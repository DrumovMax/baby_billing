package com.nexign.cdr.producers;

import com.nexign.cdr.model.CDR;
import com.nexign.cdr.model.CallType;
import com.nexign.cdr.model.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Phaser;

/**
 * Runnable implementation for generating Call Detail Records (CDRs) over a specified period.
 */
public class GeneratorByPeriod implements Runnable {
    private final BlockingQueue<List<CDR>> queue;
    private final List<CDR> cdrList;
    private final List<Subscriber> subscribers;
    private final Phaser phaser;
    private long startTime;
    private final long endTime;

    /**
     * Constructor for GeneratorByPeriod.
     *
     * @param queue       The blocking queue to which generated CDR batches are added.
     * @param subscribers The list of subscribers to generate calls between.
     * @param phaser      The phaser to synchronize with other threads.
     * @param startTime   The start time of the period for CDR generation.
     * @param endTime     The end time of the period for CDR generation.
     */
    public GeneratorByPeriod(BlockingQueue<List<CDR>> queue, List<Subscriber> subscribers, Phaser phaser, long startTime, long endTime) {
        this.queue = queue;
        this.cdrList = new ArrayList<>();
        this.subscribers = subscribers;
        this.phaser = phaser;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Generates a random call type (INCOMING or OUTCOMING).
     *
     * @return The randomly generated call type.
     */
    private CallType randCallType () {
        Random random = new Random();
        if (0 == random.nextInt(2)) {
            return CallType.INCOMING;
        } else {
            return CallType.OUTCOMING;
        }
    }

    /**
     * Returns the opposite call type for the given call type.
     *
     * @param callType The call type to mirror.
     * @return The mirrored call type.
     */
    private CallType mirrorCallType (CallType callType) {
        if (callType == CallType.OUTCOMING) {
            return CallType.INCOMING;
        } else {
            return CallType.OUTCOMING;
        }
    }

    /**
     * Generates the next call time based on the previous call time and the limit time for the month.
     *
     * @param prevCallUnixTime   The previous call time in Unix timestamp format.
     * @param limitMonthUnixTime The limit time for the month in Unix timestamp format.
     * @return The Unix timestamp for the next call time.
     */
    private long randNextCall (long prevCallUnixTime, long limitMonthUnixTime) {
        int min = 57600, max = 115200;
        Random random = new Random();
        int plus = random.nextInt(max - min) + min;
        long newCallUnixTime = prevCallUnixTime + plus;

        return Math.min(newCallUnixTime, limitMonthUnixTime);
    }

    /**
     * Generates a random call time based on the start call Unix time.
     *
     * @param startCallUnixTime The starting Unix time of the call.
     * @return The Unix timestamp for the call time.
     */
    private long randCallTime (long startCallUnixTime) {
        Random random = new Random();
        return startCallUnixTime + random.nextInt(1800);
    }

    /**
     * Produces the generated CDR list into the blocking queue.
     *
     * @param cdrList The list of generated CDRs to be added to the queue.
     * @throws InterruptedException if interrupted while adding to the queue.
     */
    private synchronized void produce(List<CDR> cdrList) throws InterruptedException {
        queue.put(cdrList);
    }

    /**
     * Implements the CDR generation logic over the specified period.
     */
    @Override
    public void run() {
        Random random = new Random();
        while (startTime < endTime) {
            Subscriber caller = subscribers.get(random.nextInt(subscribers.size()));
            int generateCalleeIndex;
            do {
                generateCalleeIndex = random.nextInt(subscribers.size());
            } while (generateCalleeIndex == caller.getId());
            Subscriber callee = subscribers.get(generateCalleeIndex);
            CallType callType = randCallType();
            Long endTime = randCallTime(startTime);

            CDR cdr = CDR.builder()
                    .callType(callType)
                    .callerNumber(caller.getPhoneNumber())
                    .calleeNumber(callee.getPhoneNumber())
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();


            cdrList.add(cdr);
            if (caller.getIsRomashka() && callee.getIsRomashka()) {
                CDR newCdr = CDR.builder()
                        .callType(mirrorCallType(callType))
                        .callerNumber(callee.getPhoneNumber())
                        .calleeNumber(caller.getPhoneNumber())
                        .startTime(startTime)
                        .endTime(endTime)
                        .build();

                cdrList.add(newCdr);
            }

            startTime = randNextCall(startTime, this.endTime);
        }

        try {
            produce(cdrList);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        phaser.arriveAndDeregister();
    }
}
