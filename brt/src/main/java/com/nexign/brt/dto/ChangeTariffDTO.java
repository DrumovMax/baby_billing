package com.nexign.brt.dto;

import lombok.*;

/**
 * Data Transfer Object (DTO) representing a request to change the tariff for a client.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChangeTariffDTO {

    private Long tariffId;

}
