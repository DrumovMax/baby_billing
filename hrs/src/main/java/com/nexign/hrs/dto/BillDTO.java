package com.nexign.hrs.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public String toJson () {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.toJson(this);
    }
}
