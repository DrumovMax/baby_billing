package com.nexign.brt.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexign.brt.model.CallType;
import lombok.*;

/**
 * Data Transfer Object (DTO) representing a call event.
 */
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallDTO {

    private CallType callType;
    private Long callerNumber;
    private Long calleeNumber;
    private Long startTime;
    private Long endTime;
    private Long tariffId;

    /**
     * Convert the CallDTO object to a JSON string.
     *
     * @return JSON representation of the CallDTO object.
     */
    public String toJson () {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.toJson(this);
    }
}
