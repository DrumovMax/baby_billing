package com.nexign.hrs.service;

import jakarta.annotation.Resource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.stereotype.Service;


@Service
public class KafkaHRSConsumer {

    @Resource
    private HRSService hrsService;

    private static final String BRT_CACHE_TOPIC = "brt-cache-topic";
    private static final String GROUP = "hrsGroup";

    @KafkaListener(topics = BRT_CACHE_TOPIC, groupId = GROUP, topicPartitions = {
            @TopicPartition(topic = BRT_CACHE_TOPIC, partitions = "0")
    })
    public void cacheClients (String message) {
        hrsService.cachedData(message);
    }
}
