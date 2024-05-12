package com.nexign.brt.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Data Transfer Object (DTO) representing a request to top up a client's balance.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopUpDTO {

    private BigDecimal money;

}
