package com.nexign.brt.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.*;

/**
 * Data Transfer Object (DTO) representing a client.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDTO {

    private Long msisdn;
    private Long tariffId;

    /**
     * Convert the ClientDTO object to a JSON string.
     *
     * @return JSON representation of the ClientDTO object.
     */
    public String toJson () {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.toJson(this);
    }

}
