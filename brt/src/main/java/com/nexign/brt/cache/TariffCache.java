package com.nexign.brt.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nexign.brt.dto.TariffDTO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class TariffCache {
    private final Cache<Long, TariffDTO> cache = Caffeine.newBuilder()
            .expireAfterWrite(12, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    private final Gson gson;

    public TariffCache() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    public void parseAndCacheJsonFiles(String jsonArrayString) {
        List<TariffDTO> tariffDTOList = gson.fromJson(jsonArrayString, new TypeToken<List<TariffDTO>>() {}.getType());
        tariffDTOList.forEach(t -> putDataIntoCache (t.getId(), t));
    }

    @Cacheable("TariffCache")
    public TariffDTO getDataFromCache(Long id) {
        return cache.getIfPresent(id);
    }

    public Set<Long> getAllKeysFromCache () {
        return cache.asMap().keySet();
    }

    public void putDataIntoCache(Long id, TariffDTO data) {
        cache.put(id, data);
    }

    public void removeFromCache(Long id) {
        cache.invalidate(id);
    }
}
