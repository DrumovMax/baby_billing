package com.nexign.hrs.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nexign.hrs.cache.ClientCache;
import com.nexign.hrs.dto.BillDTO;
import com.nexign.hrs.dto.CallDTO;
import com.nexign.hrs.dto.ClientDTO;
import com.nexign.hrs.model.*;
import com.nexign.hrs.repository.TariffRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class HRSService {

    @Resource
    private ClientCache clientCache;

    @Resource
    private TariffRepository tariffRepository;

    private static final String HOST = "localhost";
    private static final String PORT = "8765";
    private static final String BASE = "/api/brt";
    private static final String MS = "/brt";
    private static final String CHECK_MSISDN = "/check-msisdn";
    private static final Long CLASSIC_TARIFF = 11L;
    private static final Long MONTH_TARIFF = 12L;
    private static final String MINIMAL_STEP = "0.1";



    public <T> String listToJson (List<T> tariffs) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.toJson(tariffs);
    }

    public <T> T fromJson (String json, TypeToken<T> typeToken) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.fromJson(json, typeToken.getType());
    }

    public String decodeUrl(String urlParam) {
        return URLDecoder.decode(urlParam, StandardCharsets.UTF_8);
    }

    public void updateClientCacheByListClient (String json) {
        clientCache.parseAndCacheJsonArray(json);
    }

    private boolean checkTariff (ClientDTO cachedClient) {
        return cachedClient.getTariffId().equals(CLASSIC_TARIFF) || cachedClient.getRemainingMinutes() == 0;
    }

    public String clientForMonthBill (List<ClientDTO> clients) {
        List<BillDTO> bills = new ArrayList<>();
        for (ClientDTO clientDTO : clients) {
            Tariff tariff = tariffRepository.findById(clientDTO.getTariffId()).orElse(null);
            if (tariff != null) {
                BillDTO bill = BillDTO.builder()
                        .phoneNumber(clientDTO.getMsisdn())
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
            clientCache.putDataIntoCache(clientDTO.getMsisdn(), clientDTO);
        }

        return durationMinutes > 0 ? countBill(durationMinutes, callDTO) : new BigDecimal(0);
    }


    private BigDecimal countBill (Integer durationMinutes, CallDTO callDTO) {
        ClientDTO callee = checkClientCache(callDTO.getCalleeNumber());
        Tariff tariff = tariffRepository.findById(callDTO.getTariffId()).orElse(null);
        BigDecimal minutes = new BigDecimal(durationMinutes);
        if (tariff != null) {
            BigDecimal costPerMinute;

            if (callDTO.getCallType().equals(CallType.INCOMING)) {
                costPerMinute = (callee != null) ? tariff.getCostPerMinuteInNet() : tariff.getCostPerMinuteInOther();
            } else {
                costPerMinute = (callee != null) ? tariff.getCostPerMinuteOutNet() : tariff.getCostPerMinuteOutOther();
            }

            return roundNumberBill(costPerMinute.multiply(minutes));
        }

        return BigDecimal.ZERO;
    }

    private ClientDTO checkClientCache (Long msisdn) {
        ClientDTO cachedClient = clientCache.getDataFromCache(msisdn);

        if (cachedClient == null) {
            String url = String.format("http://%s:%s%s%s%s/%s",
                    HOST,
                    PORT,
                    MS,
                    BASE,
                    CHECK_MSISDN,
                    msisdn);

            HttpClient httpClient = HttpClient
                    .newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest getRequestTest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            CompletableFuture<HttpResponse<String>> future = httpClient
                    .sendAsync(getRequestTest, HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> response = future.join();

            if (response.statusCode() == 200) {
                return clientCache.parseAndCacheJson(response.body());
            } else {
                log.info("Абонента с номером {} не существует.", msisdn);
            }
        }
        return null;
    }

    public BillDTO callCalculation (CallDTO callDTO) {
        ClientDTO cachedClient = clientCache.getDataFromCache(callDTO.getCallerNumber());

        if (cachedClient == null) {
            Tariff tariff = tariffRepository.findById(callDTO.getTariffId()).orElse(null);
            Integer remainingMinutes = tariff != null ? tariff.getMonthlyLimitMinutes() : 0;
            cachedClient = ClientDTO.builder()
                    .msisdn(callDTO.getCallerNumber())
                    .tariffId(callDTO.getTariffId())
                    .remainingMinutes(remainingMinutes)
                    .build();

            clientCache.putDataIntoCache(cachedClient.getMsisdn(), cachedClient);
        }

        long duration = callDTO.getEndTime() - callDTO.getStartTime();
        Integer roundedDuration = Math.toIntExact(TimeUnit.SECONDS.toMinutes(duration - (duration % 60) + 60));

        BigDecimal bill;
        if (duration == 0) {
            bill = BigDecimal.ZERO;
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
