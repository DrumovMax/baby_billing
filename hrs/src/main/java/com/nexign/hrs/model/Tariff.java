package com.nexign.hrs.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tariff")
public class Tariff {

    @Id
    @Column(name = "tariff_id")
    private Long id;

    @Column(name = "name_tariff")
    private String nameTariff;
    // цена за минуту входящего звонка для нашего оператора
    @Column(name = "cost_per_minute_in_net")
    private BigDecimal costPerMinuteInNet;
    // цена за минуту исходящего звонка для нашего оператора
    @Column(name = "cost_per_minute_out_net")
    private BigDecimal costPerMinuteOutNet;
    // цена за минуту входящего звонка для другого оператора
    @Column(name = "cost_per_minute_in_other")
    private BigDecimal costPerMinuteInOther;
    // цена за минуту исходящего звонка для другого оператора
    @Column(name = "cost_per_minute_out_other")
    private BigDecimal costPerMinuteOutOther;

    @Column(name = "monthly_limit_minutes")
    private Integer monthlyLimitMinutes;

    @Column(name = "monthly_rate")
    private BigDecimal monthlyRate;
}
