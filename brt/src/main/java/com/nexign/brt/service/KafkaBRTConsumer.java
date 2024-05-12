package com.nexign.brt.service;

import com.nexign.brt.cache.TariffCache;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaBRTConsumer {

    @Resource
    private BRTService brtService;

    private static final String CDR_TOPIC = "cdr-topic";
    private static final String GROUP = "brt-group";


    @KafkaListener(topics = CDR_TOPIC, groupId = GROUP, topicPartitions = {
            @TopicPartition(topic = CDR_TOPIC, partitions = "0")
    })
    public void consume (String message) {
        String decodeMessage = String.valueOf(brtService.decodeData(message));
        brtService.checkListCall(decodeMessage);
    }

}
