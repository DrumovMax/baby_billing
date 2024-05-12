package com.nexign.brt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object (DTO) representing a tariff.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TariffDTO {

    private Long id;
    private String nameTariff;

}
