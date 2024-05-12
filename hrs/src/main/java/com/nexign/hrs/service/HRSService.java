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
import org.springframework.beans.factory.annotation.Value;
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

/**
 * Service class providing business logic for handling HTTP requests and calculations related to HRS services.
 */
@Slf4j
@Service
public class HRSService {

    @Resource
    private ClientCache clientCache;

    @Resource
    private TariffRepository tariffRepository;

    @Value("${gateway.host}")
    private String HOST;
    private static final String PORT = "8765";
    private static final String BASE = "/api/brt";
    private static final String MS = "/brt";
    private static final String CHECK_MSISDN = "/check-msisdn";
    private static final Long CLASSIC_TARIFF = 11L;
    private static final Long MONTH_TARIFF = 12L;
    private static final String MINIMAL_STEP = "0.1";

    /**
     * Converts a list of objects to JSON format.
     *
     * @param tariffs The list of objects to convert.
     * @param <T>     The type of the objects in the list.
     * @return A JSON representation of the list.
     */
    public <T> String listToJson (List<T> tariffs) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.toJson(tariffs);
    }

    /**
     * Converts JSON string to a specified object type.
     *
     * @param json      The JSON string to parse.
     * @param typeToken Type token for the target object type.
     * @param <T>       The target object type.
     * @return The parsed object of type T.
     */
    public <T> T fromJson (String json, TypeToken<T> typeToken) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        return gson.fromJson(json, typeToken.getType());
    }

    /**
     * Decodes URL-encoded string.
     *
     * @param urlParam The URL-encoded string to decode.
     * @return The decoded string.
     */
    public String decodeUrl(String urlParam) {
        return URLDecoder.decode(urlParam, StandardCharsets.UTF_8);
    }

    /**
     * Updates the client cache with a list of client DTOs.
     *
     * @param json The JSON string representing a list of client DTOs.
     */
    public void updateClientCacheByListClient (String json) {
        clientCache.parseAndCacheJsonArray(json);
    }

    /**
     * Checks if the client's tariff is classic or if remaining minutes are zero.
     *
     * @param cachedClient The client data to check.
     * @return True if the client's tariff is classic or remaining minutes are zero; false otherwise.
     */
    private boolean checkTariff (ClientDTO cachedClient) {
        return cachedClient.getTariffId().equals(CLASSIC_TARIFF) || cachedClient.getRemainingMinutes() == 0;
    }

    /**
     * Calculates bills for clients based on monthly rates.
     *
     * @param clients List of client data.
     * @return JSON representation of the bill details.
     */
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

    /**
     * Checks and processes new month billing for a specified range of months.
     *
     * @param startMonth Start month of the billing period.
     * @param endMonth   End month of the billing period.
     * @return JSON representation of the monthly bills.
     */
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

    /**
     * Rounds the given amount to the nearest minimal step.
     *
     * @param toPay Amount to be rounded.
     * @return Rounded amount.
     */
    private BigDecimal roundNumberBill (BigDecimal toPay) {
        BigDecimal divisor = new BigDecimal(MINIMAL_STEP);
        return  toPay.divide(divisor, 0, RoundingMode.CEILING).multiply(divisor);
    }

    /**
     * Counts the bill for a monthly duration based on client and call details.
     *
     * @param durationMinutes Monthly duration in minutes.
     * @param clientDTO       Client details.
     * @param callDTO         Call details.
     * @return Calculated bill amount.
     */
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

    /**
     * Counts the bill amount for a specified call duration.
     *
     * @param durationMinutes Duration of the call in minutes.
     * @param callDTO         Call details.
     * @return Calculated bill amount.
     */
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

    /**
     * Retrieves and checks a client's data from the cache or external service.
     *
     * @param msisdn Client's phone number.
     * @return Client data retrieved from cache or external service.
     */
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

    /**
     * Calculates the bill for a call based on call details and client data.
     *
     * @param callDTO Call details.
     * @return BillDTO containing the calculated bill amount.
     */
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
