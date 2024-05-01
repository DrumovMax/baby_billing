package com.nexign.cdr.producers;

import com.nexign.cdr.model.CDR;
import com.nexign.cdr.model.CallType;
import com.nexign.cdr.model.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Phaser;

public class GeneratorByPeriod implements Runnable {
    private final BlockingQueue<List<CDR>> queue;
    private final List<CDR> cdrList;
    private final List<Subscriber> subscribers;
    private final Phaser phaser;
    private long startTime;
    private final long endTime;

    public GeneratorByPeriod(BlockingQueue<List<CDR>> queue, List<Subscriber> subscribers, Phaser phaser, long startTime, long endTime) {
        this.queue = queue;
        this.cdrList = new ArrayList<>();
        this.subscribers = subscribers;
        this.phaser = phaser;
        this.startTime = startTime;
        this.endTime = endTime;
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
        int plus = random.nextInt(max - min) + min;
        long newCallUnixTime = prevCallUnixTime + plus;

        return Math.min(newCallUnixTime, limitMonthUnixTime);
    }

    private long randCallTime (long startCallUnixTime) {
        Random random = new Random();
        return startCallUnixTime + random.nextInt(1800);
    }

    private synchronized void produce(List<CDR> cdrList) throws InterruptedException {
        queue.put(cdrList);
    }

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
