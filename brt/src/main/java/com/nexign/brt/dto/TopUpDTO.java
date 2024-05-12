package com.nexign.brt.dto;

import lombok.*;

import java.math.BigDecimal;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopUpDTO {

    private BigDecimal money;

}
