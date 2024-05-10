package com.nexign.brt.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexign.brt.dto.ClientDTO;
import com.nexign.brt.model.Client;
import com.nexign.brt.repository.ClientRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KafkaBRTProducer {

    @Resource
    private ClientRepository clientRepository;

    @Resource
    private ClientService clientService;

    private static final String BRT_CACHE_TOPIC = "brt-cache-topic";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaBRTProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void init () {
        updateClientCache(clientRepository.findAll());
    }

    public void updateClientCache (List<Client> clients) {
        List<ClientDTO> clientDTOs = clients.stream()
                .map(clientService::mappingClientToDTO)
                .collect(Collectors.toList());
        String json = listToJson(clientDTOs);
        sendTransaction(BRT_CACHE_TOPIC, "0", json);
    }

    public <T> String listToJson (List<T> tariffs) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.toJson(tariffs);
    }

    public void sendTransaction(String topic, String partition,  String message) {
        kafkaTemplate.send(topic, partition, message);
    }
}