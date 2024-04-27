package com.nexign.cdr.service;

import com.nexign.cdr.generators.GeneratorByPeriod;
import com.nexign.cdr.model.CDR;
import com.nexign.cdr.model.CallType;
import com.nexign.cdr.model.Subscriber;
import com.nexign.cdr.repository.CDRRepository;
import com.nexign.cdr.repository.SubscriberRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.javatuples.Pair;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Phaser;

import static org.apache.tomcat.util.http.fileupload.FileUtils.deleteDirectory;

@Slf4j
@Service
public class CDRService {

/*  Идея:
*   Каждый месяц параллельно будет генерировать записи. Месяц делится на количество потоков
*   Будет фазер, который на каждом месяце будет ждать генерации всех файлов, которые
*   собираются в какой-то список месячный. Как только собрался месяц, список который нагенерился
*   сортируется где-то в другой функции(потоке, который будет ждать пока примут все данные по этому месяцу.
*   сортированный список берется по 10 и генерирует файлы, который отправляются в BRT.
* */

    BlockingQueue<List<CDR>> queue = new ArrayBlockingQueue<>(10);

    @Resource
    private CDRRepository cdrRepository;
    @Resource
    private SubscriberRepository subscriberRepository;
    private final String dirName = "cdr/cdr_files";

    private long getStartBillingPeriod () {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse("01/01/2023 00:00:00", formatter);
        dateTime = dateTime.atOffset(ZoneOffset.UTC).toLocalDateTime();
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }

    private long getEndBillingPeriod (long currentUnixTime) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(currentUnixTime), ZoneOffset.UTC);
        LocalDateTime newDateTime = dateTime.plusMonths(12);

        return newDateTime.toEpochSecond(ZoneOffset.UTC);
    }

    private long countNextUnixTimeMonth (long currentUnixTime) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(currentUnixTime), ZoneOffset.UTC);
        LocalDateTime newDateTime = dateTime.plusMonths(1);

        return newDateTime.toEpochSecond(ZoneOffset.UTC);
    }

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

    synchronized public List<CDR> consume () {
        try {
            List<CDR> cdrList = queue.take();
            cdrRepository.saveAll(cdrList);
            return cdrList;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private int callTypeToInt (CallType callType) {
        return CallType.INCOMING.equals(callType) ? 1 : 2;
    }

    private LocalDateTime epochToLocalDateTime (long epoch) {
        return  LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
    }

    private void sendByMonthList (List<CDR> cdrList, Phaser phaser) {
        cdrList.sort(Comparator.comparingLong(CDR::getStartTime));
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

            String pathFileString = dirName + "/cdr_"
                    + startLocalDateTime.format(formatter)
                    + "_"
                    + endLocalDateTime.format(formatter)
                    + ".txt";
            Path pathFile = Paths.get(pathFileString);

            try {
                if (!Files.exists(pathFile)) Files.createFile(pathFile);
            } catch (IOException e) {
                log.error("Fail to create file: " + pathFile);
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
            cdrList.removeAll(subList);
        }

        phaser.arriveAndAwaitAdvance();
    }

    public void startEmulate () {
        System.out.println("Start time: " + LocalDateTime.now());
        List<CDR> monthCDRs = new ArrayList<>();
        long currentUnixTime = getStartBillingPeriod();
        long nextMonthUnixTime = countNextUnixTimeMonth(currentUnixTime);
        long limitTime = getEndBillingPeriod(currentUnixTime); // Время окончания тарифицируемого периода

        int numberOfThreads = 4;
        Thread[] threads = new Thread[numberOfThreads];

        List<Subscriber> subscribers = subscriberRepository.findAll();
        Phaser phaser = new Phaser(1);

        try {
            Path path = Paths.get(dirName);
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            } else {
                deleteDirectory(path.toFile());
                Files.createDirectory(path);
            }
        } catch (IOException e) {
            log.error("Fail to create directory: " + dirName);
        }

        for (int month = 1; month <= 12; month++) {
            System.out.println("Month: " + month);
            List<Pair<Long, Long>> splitMonth = splitMonth(currentUnixTime, nextMonthUnixTime, numberOfThreads);

//            Создаем потоки и даем очередь. Они потом должны по завершению положить в очередь результат своей работы
//            Пока незакончили тут после функции будет ожидание по фазеру
            for (int i = 0; i < splitMonth.size(); i++) {
                phaser.register();

                GeneratorByPeriod generator = new GeneratorByPeriod(queue, subscribers, phaser, splitMonth.get(i).getValue0(), splitMonth.get(i).getValue1());
                threads[i] = new Thread(generator);
                threads[i].start();
            }

            phaser.arriveAndAwaitAdvance();

            while (!queue.isEmpty()) {
                monthCDRs.addAll(consume());
            }

            sendByMonthList(monthCDRs, phaser);

            monthCDRs.clear();
            currentUnixTime = nextMonthUnixTime;
            nextMonthUnixTime = countNextUnixTimeMonth(currentUnixTime);
        }
        System.out.println("End of billing period: " + limitTime);
        System.out.println("End time: " + LocalDateTime.now());
    }
}