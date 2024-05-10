package com.nexign.hrs.service;

import com.nexign.hrs.dto.TariffDTO;
import com.nexign.hrs.model.Tariff;
import org.springframework.stereotype.Service;

@Service
public class TariffService {

    public TariffDTO mappingTariffToDTO (Tariff tariff) {
        return TariffDTO.builder()
                .id(tariff.getId())
                .nameTariff(tariff.getNameTariff())
                .build();
    }

}
