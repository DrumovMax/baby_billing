package com.nexign.brt.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Data Transfer Object (DTO) representing a bill for a subscriber.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillDTO {

    private Long phoneNumber;
    private BigDecimal toPay;
}
