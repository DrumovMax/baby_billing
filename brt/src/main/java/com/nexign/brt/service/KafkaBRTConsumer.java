package com.nexign.brt.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer responsible for consuming messages from the CDR (Call Detail Record) topic.
 * This consumer processes incoming messages by decoding and checking call details.
 */
@Slf4j
@Service
public class KafkaBRTConsumer {

    @Resource
    private BRTService brtService;

    private static final String CDR_TOPIC = "cdr-topic";
    private static final String GROUP = "brt-group";

    /**
     * Listens to the CDR_TOPIC for incoming messages as part of the brt-group consumer group.
     * Invokes the `checkListCall` method of the `brtService` to process the consumed message.
     *
     * @param message the incoming message from the CDR_TOPIC
     */
    @KafkaListener(topics = CDR_TOPIC, groupId = GROUP, topicPartitions = {
            @TopicPartition(topic = CDR_TOPIC, partitions = "0")
    })
    public void consume (String message) {
        String decodeMessage = String.valueOf(brtService.decodeData(message));
        brtService.checkListCall(decodeMessage);
    }

}
