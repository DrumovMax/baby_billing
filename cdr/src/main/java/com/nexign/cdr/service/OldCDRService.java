package com.nexign.cdr.service;

import com.nexign.cdr.model.CDR;
import com.nexign.cdr.model.CallType;
import com.nexign.cdr.model.Subscriber;
import com.nexign.cdr.repository.CDRRepository;
import com.nexign.cdr.repository.SubscriberRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static org.apache.tomcat.util.http.fileupload.FileUtils.deleteDirectory;

@Slf4j
@Service
public class CDRService {

    @Resource
    private SubscriberRepository subscriberRepository;
    @Resource
    private CDRRepository cdrRepository;

    public void emulateWork () throws InterruptedException {
        System.out.println("Emulating work");
        String dirName = "cdr/cdr_files";
        System.out.println("Start time: " + LocalDateTime.now());
        PriorityBlockingQueue<CDR> queue = new PriorityBlockingQueue<>(10, Comparator.comparingLong(CDR::getStartTime));

        List<Subscriber> subscribers = subscriberRepository.findAll();
        int numberOfThreads = 1; // Количество потоков
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

        Thread[] threads = new Thread[numberOfThreads];

        Producer producer = new Producer(queue, 1704067200L, phaser, subscribers);
        Consumer consumer = new Consumer(queue, dirName, phaser, cdrRepository);

        for (int i = 0; i < numberOfThreads; i++) {
            phaser.register();
            threads[i] = new Thread(producer);
            threads[i].start();
        }
        Thread consumerThread = new Thread(consumer);

        consumerThread.start();

        while (phaser.getRegisteredParties() > 1) {
            phaser.arriveAndAwaitAdvance();
        }

        phaser.forceTermination();
        System.out.println("End time: " + LocalDateTime.now());
    }
}

@Slf4j
class Producer implements Runnable {
    private final PriorityBlockingQueue<CDR> queue;
    private final long maxCount;
    private final List<Subscriber> subscribers;
    private final Phaser phaser;

    public Producer(PriorityBlockingQueue<CDR> queue, long maxCount, Phaser phaser, List<Subscriber> subscribers) {
        this.queue = queue;
        this.maxCount = maxCount;
        this.phaser = phaser;
        this.subscribers = subscribers;
    }

    private CallType randCallType () {
        Random random = new Random();
        if (0 == random.nextInt(2)) {
            return CallType.INCOMING;
        } else {
            return CallType.OUTCOMING;
        }
    }

    private CallType mirrorCallType (CallType callType) {
        if (callType == CallType.OUTCOMING) {
            return CallType.INCOMING;
        } else {
            return CallType.OUTCOMING;
        }
    }

    private long randNextCall (long prevCallUnixTime, long limitMonthUnixTime) {
        int min = 57600, max = 115200;
        Random random = new Random();
        long newCallUnixTime = prevCallUnixTime + random.nextInt(max - min) + min;

        return Math.min(newCallUnixTime, limitMonthUnixTime);
    }

    private long randCallTime (long startCallUnixTime) {
        Random random = new Random();
        return startCallUnixTime + random.nextInt(1800);
    }

    private long countLimitEpochTime (long currentUnixTime) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(currentUnixTime), ZoneOffset.UTC);
        LocalDateTime newDateTime = dateTime.plusMonths(12);

        return newDateTime.toEpochSecond(ZoneOffset.UTC);
    }

    @Override
    public void run() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse("01/01/2023 00:00:00", formatter);
        dateTime = dateTime.atOffset(ZoneOffset.UTC).toLocalDateTime();
        Random random = new Random();

        long currentUnixTime = dateTime.toEpochSecond(ZoneOffset.UTC);
        long limitEpochTime = countLimitEpochTime(currentUnixTime);

        while (currentUnixTime < limitEpochTime) {
            try {
                phaser.arriveAndAwaitAdvance();

                Subscriber caller = subscribers.get(random.nextInt(subscribers.size()));
                int generateCalleeIndex;
                do {
                    generateCalleeIndex = random.nextInt(subscribers.size());
                } while (generateCalleeIndex == caller.getId());
                Subscriber callee = subscribers.get(generateCalleeIndex);
                CallType callType = randCallType();
                Long endTime = randCallTime(currentUnixTime);

                CDR cdr = CDR.builder()
                        .callType(callType)
                        .callerNumber(caller.getPhoneNumber())
                        .calleeNumber(callee.getPhoneNumber())
                        .startTime(currentUnixTime)
                        .endTime(endTime)
                        .build();


                produce(cdr);
                if (caller.getIsRomashka() && callee.getIsRomashka()) {
                    CDR newCdr = CDR.builder()
                            .callType(mirrorCallType(callType))
                            .callerNumber(callee.getPhoneNumber())
                            .calleeNumber(caller.getPhoneNumber())
                            .startTime(currentUnixTime)
                            .endTime(endTime)
                            .build();

                    produce(newCdr);
                }

                currentUnixTime = randNextCall(currentUnixTime, maxCount);

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        while (!queue.isEmpty()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        phaser.arriveAndDeregister();
    }

    private synchronized void produce(CDR item) throws InterruptedException {
        queue.put(item);
    }
}


@Slf4j
class Consumer implements Runnable {
    private final PriorityBlockingQueue<CDR> queue;
    private int currentMonth;
    private final String dirName;
    @Resource
    private CDRRepository cdrRepository;
    private final List<CDR> cdrList;
    private volatile boolean running = true;
    private final Phaser phaser;

    public Consumer(PriorityBlockingQueue<CDR> queue, String dirName, Phaser phaser, CDRRepository cdrRepository) {
        this.queue = queue;
        this.phaser = phaser;
        this.currentMonth = 1;
        this.dirName = dirName;
        this.cdrRepository = cdrRepository;
        this.cdrList = new ArrayList<>();
    }

    private int callTypeToInt (CallType callType) {
        return CallType.INCOMING.equals(callType) ? 1 : 2;
    }

    private LocalDateTime epochToLocalDateTime (long epoch) {
        return  LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
    }

    synchronized public void consume () {
        try {
            CDR newCdr = queue.take();
            cdrRepository.save(newCdr);
            cdrList.add(newCdr);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (running) {
            if (cdrList.size() == 10 || (queue.isEmpty() && phaser.isTerminated() && !cdrList.isEmpty())) {
                cdrList.sort(Comparator.comparingLong(CDR::getStartTime));

                LocalDateTime startLocalDateTime = epochToLocalDateTime(cdrList.get(0).getStartTime());
                LocalDateTime endLocalDateTime = epochToLocalDateTime(cdrList.get(cdrList.size() - 1).getStartTime());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");

                if (endLocalDateTime.getMonthValue() > currentMonth) {
                    currentMonth = endLocalDateTime.getMonthValue();
                }

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
                    for (CDR cdr : cdrList) {
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
                cdrList.clear();
            }

            if (queue.isEmpty() || (phaser.isTerminated() && !cdrList.isEmpty())) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else if (queue.isEmpty() && phaser.isTerminated() && cdrList.isEmpty()) {
                running = false;
                break;
            } else {
                consume();
            }
        }

    }
}
