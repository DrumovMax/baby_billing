package com.nexign.brt.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillDTO {

    private Long phoneNumber;
    private BigDecimal toPay;
}
