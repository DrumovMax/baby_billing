package com.nexign.cdr.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CDR {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "call_id")
    private Long id;

    @Column(name = "call_type")
    private CallType callType;

    @Column(name = "caller_number")
    private Long callerNumber;

    @Column(name = "callee_number")
    private Long calleeNumber;

    @Column(name = "start_time")
    private Long startTime;

    @Column(name = "end_time")
    private Long endTime;

}
