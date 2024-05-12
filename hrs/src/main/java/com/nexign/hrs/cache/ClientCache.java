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

/**
 * Component responsible for caching and managing client data.
 */
@Component
public class ClientCache {

    @Resource
    private TariffRepository tariffRepository;

    private final Cache<Long, ClientDTO> cache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .maximumSize(1000)
            .build();

    private final Gson gson;

    /**
     * Constructor initializing Gson for JSON serialization/deserialization.
     */
    public ClientCache() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    /**
     * Parses a JSON string into a ClientDTO object, enriches it with tariff details, and caches it.
     *
     * @param json JSON string representing a ClientDTO object
     * @return Parsed and cached ClientDTO object
     */
    public ClientDTO parseAndCacheJson (String json) {
        ClientDTO clientDTO = gson.fromJson(json, new TypeToken<ClientDTO>() {}.getType());

        tariffRepository.findById(clientDTO.getTariffId()).ifPresent(t -> clientDTO.setRemainingMinutes(t.getMonthlyLimitMinutes()));
        putDataIntoCache(clientDTO.getMsisdn(), clientDTO);
        return clientDTO;
    }

    /**
     * Parses a JSON array string into a list of ClientDTO objects, enriches them with tariff details,
     * and caches each of them individually.
     *
     * @param jsonArrayString JSON array string representing a list of ClientDTO objects
     */
    public void parseAndCacheJsonArray (String jsonArrayString) {
        List<ClientDTO> clientDTOList = gson.fromJson(jsonArrayString, new TypeToken<List<ClientDTO>>() {}.getType());

        Map<Long, Integer> mapOfIdToRemainingMinutes = tariffRepository.findAll().stream()
                .collect(Collectors.toMap(Tariff::getId, Tariff::getMonthlyLimitMinutes));

        for (ClientDTO clientDTO : clientDTOList) {
            clientDTO.setRemainingMinutes(mapOfIdToRemainingMinutes.get(clientDTO.getTariffId()));
            putDataIntoCache(clientDTO.getMsisdn(), clientDTO);
        }
    }

    /**
     * Retrieves all cached ClientDTO objects matching the given tariff ID.
     *
     * @param tariffId Tariff ID to filter ClientDTOs by
     * @return List of ClientDTO objects matching the tariff ID
     */
    public List<ClientDTO> getAllByTariffId (Long tariffId) {
        List<ClientDTO> matchedClients = new ArrayList<>();

        Map<Long, ClientDTO> cacheMap = cache.asMap();
        for (ClientDTO clientDTO : cacheMap.values()) {
            if (clientDTO.getTariffId().equals(tariffId)) {
                matchedClients.add(clientDTO);
            }
        }

        return matchedClients;
    }

    /**
     * Retrieves a ClientDTO object from the cache based on the phone number (key).
     *
     * @param phoneNumber Phone number used as the cache key
     * @return Cached ClientDTO object, or null if not found
     */
    @Cacheable("ClientCache")
    public ClientDTO getDataFromCache(Long phoneNumber) {
        return cache.getIfPresent(phoneNumber);
    }

    /**
     * Puts a ClientDTO object into the cache with the specified phone number as the key.
     *
     * @param phoneNumber Phone number to use as the cache key
     * @param data        ClientDTO object to cache
     */
    public void putDataIntoCache(Long phoneNumber, ClientDTO data) {
        cache.put(phoneNumber, data);
    }

    /**
     * Removes a ClientDTO object from the cache based on the phone number (key).
     *
     * @param phoneNumber Phone number used as the cache key
     */
    public void removeFromCache(Long phoneNumber) {
        cache.invalidate(phoneNumber);
    }
}
