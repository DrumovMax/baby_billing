package com.nexign.brt.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {

    private Long msisdn;
    private Long tariffId;

    public String toJson () {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.toJson(this);
    }

}
