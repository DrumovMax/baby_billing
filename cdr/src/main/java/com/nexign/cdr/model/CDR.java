package com.nexign.cdr.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Represents a Call Detail Record (CDR) entity storing information about a call.
 */
@Entity
@Table
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
public class CDR {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "call_id")
    Long id;

    @Column(name = "call_type")
    CallType callType;

    @Column(name = "caller_number")
    Long callerNumber;

    @Column(name = "callee_number")
    Long calleeNumber;

    @Column(name = "start_time")
    Long startTime;

    @Column(name = "end_time")
    Long endTime;

}
