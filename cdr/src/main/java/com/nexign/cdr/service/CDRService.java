package com.nexign.cdr.service;

import com.nexign.cdr.producers.GeneratorByPeriod;
import com.nexign.cdr.model.CDR;
import com.nexign.cdr.model.CallType;
import com.nexign.cdr.model.Subscriber;
import com.nexign.cdr.producers.KafkaCDRProducer;
import com.nexign.cdr.repository.CDRRepository;
import com.nexign.cdr.repository.SubscriberRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Phaser;

import static org.apache.tomcat.util.http.fileupload.FileUtils.deleteDirectory;

/**
 * CDRService manages the generation and processing of Call Detail Records (CDRs).
 */
@Slf4j
@Service
public class CDRService {

    private final BlockingQueue<List<CDR>> queue = new ArrayBlockingQueue<>(10);

    @Resource
    private CDRRepository cdrRepository;

    @Resource
    private SubscriberRepository subscriberRepository;

    @Resource
    private KafkaCDRProducer kafkaCDRProducer;

    private final Phaser phaser = new Phaser(1);

    @Value("${directory.cdr.name}")
    private String CDR_FILES;
    private static final String CDR_TOPIC = "cdr-topic";

    /**
     * Advances the Phaser to the next phase, allowing waiting threads to proceed.
     */
    public void nextIteration () {
        phaser.arriveAndAwaitAdvance();
    }

    /**
     * Registers a new party to the Phaser, returning the current count of registered parties.
     *
     * @return The current count of registered parties after registration.
     */
    public int register () {
        phaser.register();
        return phaser.getRegisteredParties() - 1;
    }

    /**
     * Deregisters a party from the Phaser if more than one party is registered.
     *
     * @return The current count of registered parties after deregistration.
     */
    public int deregister () {
        if (phaser.getRegisteredParties() > 1) {
            phaser.arriveAndDeregister();
        }

        return phaser.getRegisteredParties() - 1;
    }

    /**
     * Retrieves the start time of the billing period.
     *
     * @return The start time of the billing period in epoch seconds.
     */
    private long getStartBillingPeriod () {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse("01/01/2023 00:00:00", formatter);
        dateTime = dateTime.atOffset(ZoneOffset.UTC).toLocalDateTime();
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    /**
     * Computes the epoch time for the start of the next month.
     *
     * @param currentUnixTime The current epoch time.
     * @return The epoch time for the start of the next month.
     */
    private long countNextUnixTimeMonth (long currentUnixTime) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(currentUnixTime), ZoneOffset.UTC);
        LocalDateTime newDateTime = dateTime.plusMonths(1);

        return newDateTime.toEpochSecond(ZoneOffset.UTC);
    }

    /**
     * Splits a month into multiple parts based on the specified number of parts.
     *
     * @param startMonth The start of the month (epoch time).
     * @param endMonth   The end of the month (epoch time).
     * @param numParts   The number of parts to split the month into.
     * @return A list of pairs representing the start and end times for each part.
     */
    private List<Pair<Long, Long>> splitMonth (long startMonth, long endMonth, int numParts) {
        List<Pair<Long, Long>> result = new ArrayList<>();

        long timeForOnePart = (endMonth - startMonth) / numParts;
        long currentUnixTime = startMonth;
        for (int i = 0; i < numParts; i++) {
            long endOfPart = (currentUnixTime) + timeForOnePart;
            Pair<Long, Long> pair = Pair.with(currentUnixTime + 1, endOfPart);
            result.add(pair);
            currentUnixTime = endOfPart;
        }
        return result;
    }

    /**
     * Consumes CDRs from the blocking queue and saves them to the database.
     *
     * @return The list of consumed CDRs.
     */
    synchronized public List<CDR> consume () {
        try {
            List<CDR> cdrList = queue.take();
            cdrRepository.saveAll(cdrList);
            return cdrList;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts a call type to an integer representation.
     *
     * @param callType The call type to convert.
     * @return An integer representation of the call type (1 for INCOMING, 2 for OUTGOING).
     */
    private int callTypeToInt (CallType callType) {
        return CallType.INCOMING.equals(callType) ? 1 : 2;
    }

    /**
     * Converts epoch time to LocalDateTime.
     *
     * @param epoch The epoch time to convert.
     * @return The LocalDateTime equivalent of the epoch time.
     */
    private LocalDateTime epochToLocalDateTime (long epoch) {
        return  LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
    }

    /**
     * Sends CDR data to BRT by encoding it as Base64 and producing it to Kafka.
     *
     * @param path The path to the CDR data file.
     */
    private void sendDataToBRT (Path path) {
        try {
            String str = Base64.getEncoder().encodeToString(Files.readAllBytes(path));
            kafkaCDRProducer.sendTransaction(CDR_TOPIC, "0", str);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Processes and sends CDRs grouped by month to BRT.
     *
     * @param cdrList The list of CDRs to process and send.
     * @param phaser  The phaser used to coordinate threads.
     */
    private void sendByMonthList (List<CDR> cdrList, Phaser phaser) {
        cdrList.sort(Comparator.comparingLong(CDR::getEndTime));
        List<CDR> subList;
        while (!cdrList.isEmpty()) {
            if (cdrList.size() > 10) {
                subList = cdrList.subList(0, 10);
            }
            else {
                subList = cdrList;
            }

            LocalDateTime startLocalDateTime = epochToLocalDateTime(subList.get(0).getStartTime());
            LocalDateTime endLocalDateTime = epochToLocalDateTime(subList.get(subList.size() - 1).getStartTime());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");

            String pathFileString = CDR_FILES + "/cdr_"
                    + startLocalDateTime.format(formatter)
                    + "_"
                    + endLocalDateTime.format(formatter)
                    + ".txt";
            Path pathFile = Paths.get(pathFileString);

            try {
                if (!Files.exists(pathFile)) Files.createFile(pathFile);
            } catch (IOException e) {
                log.error("Fail to create file: {}", pathFile);
            }

            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(pathFileString, true)))) {
                for (CDR cdr : subList) {
                    out.println("""
                            0%d,%d,%d,%d,%d
                            """.formatted(
                            callTypeToInt(cdr.getCallType()),
                            cdr.getCallerNumber(),
                            cdr.getCalleeNumber(),
                            cdr.getStartTime(),
                            cdr.getEndTime()));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            sendDataToBRT(pathFile);

            try {
                Files.deleteIfExists(pathFile);
            } catch (IOException e) {
                log.error("Fail to delete file: {}", pathFile);
            }

            cdrList.removeAll(subList);
        }

        phaser.arriveAndAwaitAdvance();
    }

    /**
     * Starts the emulation process by generating CDRs for a billing period.
     */
    public void startEmulate () {
        System.out.println("Start emulation");
        List<CDR> monthCDRs = new ArrayList<>();
        long currentUnixTime = getStartBillingPeriod();
        long nextMonthUnixTime = countNextUnixTimeMonth(currentUnixTime);

        int numberOfThreads = 4;
        Thread[] threads = new Thread[numberOfThreads];

        try {
            Path path = Paths.get(CDR_FILES);
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            } else {
                deleteDirectory(path.toFile());
                Files.createDirectory(path);
            }
        } catch (IOException e) {
            log.error("Fail to create directory: " + CDR_FILES);
        }

        for (int month = 1; month <= 12; month++) {
            System.out.println("Month: " + month);
            List<Pair<Long, Long>> splitMonth = splitMonth(currentUnixTime, nextMonthUnixTime, numberOfThreads);

            for (int i = 0; i < splitMonth.size(); i++) {
                phaser.register();

                List<Subscriber> subscribers = subscriberRepository.findAll();
                GeneratorByPeriod generator = new GeneratorByPeriod(queue, subscribers, phaser, splitMonth.get(i).getValue0(), splitMonth.get(i).getValue1());
                threads[i] = new Thread(generator);
                threads[i].start();
            }

            while (!queue.isEmpty()) {
                monthCDRs.addAll(consume());
            }

            sendByMonthList(monthCDRs, phaser);

            monthCDRs.clear();
            currentUnixTime = nextMonthUnixTime;
            nextMonthUnixTime = countNextUnixTimeMonth(currentUnixTime);
        }
        System.out.println("End of billing period");
    }

    /**
     * Sends a CDR file encoded as Base64 to BRT using Kafka.
     *
     * @param base64CDRFile The CDR file encoded as a Base64 string.
     */
    public void sendCDRToBRT (String base64CDRFile) {
        kafkaCDRProducer.sendTransaction(CDR_TOPIC, "0", base64CDRFile);
    }
}