package com.nexign.hrs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexign.hrs.cache.ClientCache;
import com.nexign.hrs.dto.BillDTO;
import com.nexign.hrs.dto.CallDTO;
import com.nexign.hrs.dto.ClientDTO;
import com.nexign.hrs.model.*;
import com.nexign.hrs.repository.TariffRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class HRSService {

    @Resource
    private ClientCache clientCache;

    @Resource
    private TariffRepository tariffRepository;

    private static final Long CLASSIC_TARIFF = 11L;
    private static final Long MONTH_TARIFF = 12L;
    private static final String MINIMAL_STEP = "0.1";

    public void cachedData (String jsonData) {
        clientCache.parseAndCacheJsonFiles(jsonData);
    }

    public <T> String listToJson (List<T> tariffs) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.toJson(tariffs);
    }

    public String decodeUrl(String urlParam) {
        return URLDecoder.decode(urlParam, StandardCharsets.UTF_8);
    }

    public CallDTO getCallDataFromString(String jsonString) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(jsonString, new TypeReference<>(){});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    private boolean checkTariff (ClientDTO cachedClient) {
        return cachedClient.getTariffNumber().equals(CLASSIC_TARIFF) || cachedClient.getRemainingMinutes() == 0;
    }

    public String clientForMonthBill (List<ClientDTO> clients) {
        List<BillDTO> bills = new ArrayList<>();
        for (ClientDTO clientDTO : clients) {
            Tariff tariff = tariffRepository.findById(clientDTO.getTariffNumber()).orElse(null);
            if (tariff != null) {
                BillDTO bill = BillDTO.builder()
                        .phoneNumber(clientDTO.getClientNumber())
                        .toPay(tariff.getMonthlyRate())
                        .build();
                bills.add(bill);
            }
        }
        return listToJson(bills);

    }

    public String checkNewMonth(int startMonth, int endMonth) {
        List<BillDTO> bills = new ArrayList<>();
        for (int month = startMonth; month <= endMonth; month++) {
            if (month != startMonth) {
                List<ClientDTO> clients = clientCache.getAllByTariffId(MONTH_TARIFF);
                return clientForMonthBill(clients);
            }
        }
        return listToJson(bills);
    }

    private BigDecimal roundNumberBill (BigDecimal toPay) {
        BigDecimal divisor = new BigDecimal(MINIMAL_STEP);
        return  toPay.divide(divisor, 0, RoundingMode.CEILING).multiply(divisor);
    }

    private BigDecimal countMonthlyBill (Integer durationMinutes, ClientDTO clientDTO, CallDTO callDTO) {
        int newRemainingMinutes = clientDTO.getRemainingMinutes();

        while (durationMinutes > 0 && newRemainingMinutes > 0) {
            newRemainingMinutes--;
            durationMinutes--;
        }

        if (clientDTO.getRemainingMinutes() != 0) {
            clientDTO.setRemainingMinutes(newRemainingMinutes);
            clientCache.putDataIntoCache(clientDTO.getClientNumber(), clientDTO);
        }

        return durationMinutes > 0 ? countBill(durationMinutes, callDTO) : new BigDecimal(0);
    }


    private BigDecimal countBill (Integer durationMinutes, CallDTO callDTO) {
        ClientDTO caller = clientCache.getDataFromCache(callDTO.getCalleeNumber());
        Tariff tariff = tariffRepository.findById(callDTO.getTariffId()).orElse(null);
        BigDecimal minutes = new BigDecimal(durationMinutes);
        if (tariff != null) {
            BigDecimal costPerMinute;

            if (callDTO.getCallType().equals(CallType.INCOMING)) {
                costPerMinute = (caller != null) ? tariff.getCostPerMinuteInNet() : tariff.getCostPerMinuteInOther();
            } else {
                costPerMinute = (caller != null) ? tariff.getCostPerMinuteOutNet() : tariff.getCostPerMinuteOutOther();
            }

            return roundNumberBill(costPerMinute.multiply(minutes));
        }

        return new BigDecimal(0);
    }

    public BillDTO callCalculation (CallDTO callDTO) {
        ClientDTO cachedClient = clientCache.getDataFromCache(callDTO.getCallerNumber());
        long duration = callDTO.getEndTime() - callDTO.getStartTime();
        Integer roundedDuration = Math.toIntExact(TimeUnit.SECONDS.toMinutes(duration - (duration % 60) + 60));

        BigDecimal bill;
        if (duration == 0) {
            bill = new BigDecimal(0);
        } else if (checkTariff(cachedClient)) {
            bill = countBill(roundedDuration, callDTO);
        } else {
            bill = countMonthlyBill(roundedDuration, cachedClient, callDTO);
        }

        return BillDTO.builder()
                .phoneNumber(callDTO.getCallerNumber())
                .toPay(bill)
                .build();
    }
}
