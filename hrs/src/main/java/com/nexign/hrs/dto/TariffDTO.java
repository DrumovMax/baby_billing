package com.nexign.hrs.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TariffDTO {

    private Long id;
    private String nameTariff;

        public String toJson () {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.toJson(this);
    }

}
