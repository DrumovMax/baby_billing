package com.nexign.hrs.service;

import com.nexign.hrs.dto.TariffDTO;
import com.nexign.hrs.repository.TariffRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KafkaHRSProducer {

    @Resource
    private TariffRepository tariffRepository;

    @Resource
    private HRSService hrsService;

    @Resource
    private TariffService tariffService;

    private static final String HRS_CACHE_TOPIC = "hrs-cache-topic";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaHRSProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void init () {
        List<TariffDTO> tariffDTOs = tariffRepository.findAll()
                .stream()
                .map(tariffService::mappingTariffToDTO)
                .toList();

        String json = hrsService.listToJson(tariffDTOs);
        sendTransaction(HRS_CACHE_TOPIC, "0", json);
    }

    public void sendTransaction(String topic, String partition,  String message) {
        kafkaTemplate.send(topic, partition,  message);
    }

}
