package com.nexign.hrs.service;

import com.nexign.hrs.dto.TariffDTO;
import com.nexign.hrs.model.Tariff;
import com.nexign.hrs.repository.TariffRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class TariffService {

    @Resource
    private TariffRepository tariffRepository;

    public Tariff checkTariff (Long tariffId) {
        return tariffRepository.findById(tariffId).orElse(null);
    }

    public TariffDTO mappingTariffToDTO (Tariff tariff) {
        return TariffDTO.builder()
                .id(tariff.getId())
                .nameTariff(tariff.getNameTariff())
                .build();
    }

}
