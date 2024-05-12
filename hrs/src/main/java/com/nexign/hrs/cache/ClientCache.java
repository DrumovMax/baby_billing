package com.nexign.hrs.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nexign.hrs.dto.ClientDTO;
import com.nexign.hrs.model.Tariff;
import com.nexign.hrs.repository.TariffRepository;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class ClientCache {

    @Resource
    private TariffRepository tariffRepository;

    private final Cache<Long, ClientDTO> cache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .maximumSize(1000)
            .build();

    private final Gson gson;

    public ClientCache() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    public ClientDTO parseAndCacheJson (String json) {
        ClientDTO clientDTO = gson.fromJson(json, new TypeToken<ClientDTO>() {}.getType());

        tariffRepository.findById(clientDTO.getTariffId()).ifPresent(t -> clientDTO.setRemainingMinutes(t.getMonthlyLimitMinutes()));
        putDataIntoCache(clientDTO.getMsisdn(), clientDTO);
        return clientDTO;
    }

    public void parseAndCacheJsonArray (String jsonArrayString) {
        List<ClientDTO> clientDTOList = gson.fromJson(jsonArrayString, new TypeToken<List<ClientDTO>>() {}.getType());

        Map<Long, Integer> mapOfIdToRemainingMinutes = tariffRepository.findAll().stream()
                .collect(Collectors.toMap(Tariff::getId, Tariff::getMonthlyLimitMinutes));

        for (ClientDTO clientDTO : clientDTOList) {
            clientDTO.setRemainingMinutes(mapOfIdToRemainingMinutes.get(clientDTO.getTariffId()));
            putDataIntoCache(clientDTO.getMsisdn(), clientDTO);
        }
    }

    public List<ClientDTO> getAllByTariffId (Long TariffId) {
        List<ClientDTO> matchedClients = new ArrayList<>();

        Map<Long, ClientDTO> cacheMap = cache.asMap();
        for (ClientDTO clientDTO : cacheMap.values()) {
            if (clientDTO.getTariffId().equals(TariffId)) {
                matchedClients.add(clientDTO);
            }
        }

        return matchedClients;
    }

    @Cacheable("ClientCache")
    public ClientDTO getDataFromCache(Long phoneNumber) {
        return cache.getIfPresent(phoneNumber);
    }

    public void putDataIntoCache(Long phoneNumber, ClientDTO data) {
        cache.put(phoneNumber, data);
    }

    public void removeFromCache(Long phoneNumber) {
        cache.invalidate(phoneNumber);
    }
}
