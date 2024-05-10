package com.nexign.hrs.dto;

import com.nexign.hrs.model.CallType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CallDTO {

    private CallType callType;
    private Long callerNumber;
    private Long calleeNumber;
    private Long startTime;
    private Long endTime;
    private Long tariffId;

}
