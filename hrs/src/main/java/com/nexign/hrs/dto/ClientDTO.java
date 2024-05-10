package com.nexign.hrs.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {

    private Long clientNumber;
    private Long tariffNumber;
    private Integer remainingMinutes;

}
