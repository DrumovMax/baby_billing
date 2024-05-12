package com.nexign.hrs.service;

import com.nexign.hrs.dto.TariffDTO;
import com.nexign.hrs.model.Tariff;
import com.nexign.hrs.repository.TariffRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Service class providing operations related to tariff management.
 */
@Service
public class TariffService {

    @Resource
    private TariffRepository tariffRepository;

    /**
     * Retrieves a tariff by its ID from the repository.
     *
     * @param tariffId The ID of the tariff to retrieve.
     * @return The retrieved Tariff object, or null if not found.
     */
    public Tariff checkTariff (Long tariffId) {
        return tariffRepository.findById(tariffId).orElse(null);
    }

    /**
     * Maps a Tariff object to a TariffDTO object for data transfer.
     *
     * @param tariff The Tariff object to map.
     * @return The mapped TariffDTO object.
     */
    public TariffDTO mappingTariffToDTO (Tariff tariff) {
        return TariffDTO.builder()
                .id(tariff.getId())
                .nameTariff(tariff.getNameTariff())
                .build();
    }

}
