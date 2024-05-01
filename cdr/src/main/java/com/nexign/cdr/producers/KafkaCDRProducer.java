package com.nexign.cdr.producers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaCDRProducer {

    private KafkaTemplate<String, String> kafkaTemplate;

    public KafkaCDRProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendTransaction(String topic, String message) {
        kafkaTemplate.send(topic, message);
    }
}
