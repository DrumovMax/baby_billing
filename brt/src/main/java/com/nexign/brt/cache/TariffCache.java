package com.nexign.brt.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexign.brt.dto.TariffDTO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Component class for managing caching of TariffDTO objects.
 */
@Component
public class TariffCache {
    private final Cache<Long, TariffDTO> cache = Caffeine.newBuilder()
            .expireAfterWrite(12, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    /**
     * Retrieves data from cache based on the specified ID.
     *
     * @param id The ID of the TariffDTO object to retrieve from cache.
     * @return The TariffDTO object associated with the given ID from cache, or null if not present.
     */
    @Cacheable("TariffCache")
    public TariffDTO getDataFromCache(Long id) {
        return cache.getIfPresent(id);
    }

    /**
     * Retrieves all keys (IDs) currently present in the cache.
     *
     * @return A set of all keys (IDs) present in the cache.
     */
    public Set<Long> getAllKeysFromCache () {
        return cache.asMap().keySet();
    }

    /**
     * Puts the specified data into the cache with the given ID.
     *
     * @param id   The ID under which to store the data in the cache.
     * @param data The TariffDTO object to store in the cache.
     */
    public void putDataIntoCache(Long id, TariffDTO data) {
        cache.put(id, data);
    }

    /**
     * Removes the data associated with the specified ID from the cache.
     *
     * @param id The ID of the data to remove from the cache.
     */
    public void removeFromCache(Long id) {
        cache.invalidate(id);
    }
}
