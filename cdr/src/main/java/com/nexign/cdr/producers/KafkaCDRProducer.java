package com.nexign.cdr.producers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * KafkaCDRProducer is a utility class responsible for producing Kafka messages related to Call Detail Records (CDRs).
 */
@Slf4j
@Service
public class KafkaCDRProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Constructor for KafkaCDRProducer.
     *
     * @param kafkaTemplate The KafkaTemplate used for producing messages to Kafka.
     */
    public KafkaCDRProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends a transaction message to the specified Kafka topic and partition.
     *
     * @param topic     The Kafka topic to which the message will be sent.
     * @param partition The Kafka partition to which the message will be sent.
     * @param message   The message content to be sent to Kafka.
     */
    public void sendTransaction(String topic, String partition,  String message) {
        kafkaTemplate.send(topic, partition,  message);
    }
}
