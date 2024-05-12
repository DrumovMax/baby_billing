package com.nexign.hrs.dto;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientDTO {

    private Long msisdn;
    private Long tariffId;
    private Integer remainingMinutes;

}
