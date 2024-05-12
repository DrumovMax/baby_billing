package com.nexign.brt.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nexign.brt.cache.TariffCache;
import com.nexign.brt.dto.BillDTO;
import com.nexign.brt.dto.CallDTO;
import com.nexign.brt.dto.TariffDTO;
import com.nexign.brt.model.CallType;
import com.nexign.brt.model.Client;
import com.nexign.brt.repository.ClientRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Service class for performing Billing Real Time (BRT) operations.
 */
@Slf4j
@Service
public class BRTService {

    @Resource
    private ClientRepository clientRepository;

    @Resource
    private TariffCache tariffCache;

    @Resource
    private ClientService clientService;

    @Value("${gateway.host}")
    private String HOST;
    private static final String PORT = "8765";
    private static final String BASE = "/api";
    private static final String MS = "/hrs";
    private static final String PAYMENT = "/payment";
    private static final String MONTHLY_PAYMENT = "/monthly-payment";
    private static final String CHECK_TARIFF = "/check-tariff";
    private static final String UPDATE_CACHE = "/update-cache";
    private static final String PARAM_START_MONTH = "start-month";
    private static final String CLIENT_PARAM = "client-param";
    private static final String PARAM_END_MONTH = "end-month";
    private static final String CALL_PARAM = "call-param";
    private static final Long MONTH_TARIFF = 12L;

    private Integer lastMonthProcessed = -1;

    private final Gson gson;

    /**
     * Constructor for the class, initializes a Gson object for JSON serialization/deserialization.
     */
    public BRTService() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    /**
     * Serializes an object into JSON format.
     *
     * @param input the object to be serialized
     * @return a string representing the serialized object in JSON format
     */
    public <T> String toJson (T input) {
        return gson.toJson(input);
    }

    /**
     * Deserializes JSON string into an object of the specified type.
     *
     * @param json      the JSON string to deserialize
     * @param typeToken a TypeToken object defining the type of the returned object
     * @return the deserialized object
     */
    public <T> T decodeJson (String json, TypeToken<T> typeToken) {
        return gson.fromJson(json, typeToken.getType());
    }

    /**
     * Checks if the given MSISDN number is valid.
     *
     * @param msisdn the MSISDN number to check
     * @return true if the number is valid, otherwise false
     */
    public boolean checkMsisdn(long msisdn) {
        String msisdnStr = String.valueOf(msisdn);
        return msisdnStr.length() == 11 && msisdnStr.startsWith("7");
    }

    /**
     * Checks the existence of the specified tariff.
     *
     * @param tariffId the tariff ID to check
     * @return true if the tariff exists, otherwise false
     */
    public boolean checkTariff (Long tariffId) {
        TariffDTO tariffDTO = tariffCache.getDataFromCache(tariffId);
        if (tariffDTO == null) {
            String url = String.format("http://%s:%s%s%s%s/%s",
                    HOST,
                    PORT,
                    MS,
                    BASE,
                    CHECK_TARIFF,
                    tariffId);

            HttpRequest getRequestTest =  buildHttpRequest(url);

            CompletableFuture<HttpResponse<String>> future = newHttpClient()
                    .sendAsync(getRequestTest, HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> response = future.join();

            if (response.statusCode() == 404) {
                log.error("Тарифа с номером {} не существует.", tariffId);
                return false;
            } else {
                tariffCache.putDataIntoCache(tariffId, decodeJson(response.body(), new TypeToken<>() {}));
            }
        }
        return true;
    }

    /**
     * Performs monthly billing based on a list of bills.
     *
     * @param bills the list of bills to process
     */
    public void monthlyPayment (List<BillDTO> bills) {
        for (BillDTO billDTO : bills) {
            clientService.newBalance(billDTO);
        }
    }

    /**
     * Decodes a Base64-encoded message.
     *
     * @param message the Base64-encoded message
     * @return the decoded message
     */
    public String decodeData (String message) {
        return new String(Base64.getDecoder().decode(message), StandardCharsets.UTF_8);
    }

    /**
     * Checks the client corresponding to the caller number from a list of clients.
     *
     * @param callerNumber the caller's phone number
     * @param clients      the list of clients to search
     * @return the client matching the caller's phone number, or null if not found
     */
    private Client checkClient (Long callerNumber, List<Client> clients) {
        return clients.stream()
                .filter(c -> c.getMsisdn().equals(callerNumber))
                .findFirst()
                .orElse(null);
    }

    /**
     * Converts a string representation of call type to a CallType enum.
     *
     * @param callTypeString the string representation of the call type ("01" for incoming, "02" for outgoing)
     * @return the corresponding CallType enum value
     */
    private CallType stringToCallType (String callTypeString) {
        return callTypeString.equals("01") ? CallType.INCOMING : CallType.OUTCOMING;
    }

    /**
     * Constructs a new HttpClient instance with a configured connection timeout.
     *
     * @return a new HttpClient instance
     */
    private HttpClient newHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * Constructs an HttpRequest object for the specified URL.
     *
     * @param url the URL to construct the request for
     * @return the HttpRequest object
     */
    private HttpRequest buildHttpRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
    }

    /**
     * Sends a JSON message to the HRS (High performance rating) for processing and retrieves a billing result.
     *
     * @param json the JSON message to send
     * @return the billing result as a BillDTO object
     * @throws IOException          if an I/O error occurs
     * @throws InterruptedException if the operation is interrupted
     * @throws ExecutionException   if the HTTP request execution fails
     */
    private BillDTO sendToHRS (String json) throws IOException, InterruptedException, ExecutionException {
        String callParam = URLEncoder.encode(json, StandardCharsets.UTF_8);
        String url = String.format("http://%s:%s%s%s%s?%s=%s",
                HOST,
                PORT,
                MS,
                BASE,
                PAYMENT,
                CALL_PARAM,
                callParam);

        BillDTO responseBillDTO;
        HttpRequest getRequestTest = buildHttpRequest(url);

        CompletableFuture<BillDTO> future = newHttpClient()
                .sendAsync(getRequestTest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(jsonResponse -> decodeJson(jsonResponse, new TypeToken<>() {}));

        responseBillDTO = future.join();
        return responseBillDTO;
    }

    /**
     * Sends a request to the HRS to retrieve monthly bill data for a specified time period.
     *
     * @param startMonth the starting month of the billing period
     * @param endMonth   the ending month of the billing period
     * @return a list of BillDTO objects representing the monthly bills
     */
    private List<BillDTO> sendCheckToHRS (int startMonth, int endMonth) {
        String url = String.format("http://%s:%s%s%s%s?%s=%s&%s=%s",
                HOST,
                PORT,
                MS,
                BASE,
                MONTHLY_PAYMENT,
                PARAM_START_MONTH,
                startMonth,
                PARAM_END_MONTH,
                endMonth);

        List<BillDTO> responseBillData;
        HttpRequest getRequest = buildHttpRequest(url);

        CompletableFuture<List<BillDTO>> future = newHttpClient()
                .sendAsync(getRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(jsonResponse -> {
                    System.out.println(jsonResponse);
                    return decodeJson(jsonResponse, new TypeToken<>() {});
                });

        responseBillData = future.join();
        return responseBillData;
    }

    /**
     * Sends a list of clients to the HRS for caching purposes.
     *
     * @param clients the list of clients to be cached
     */
    private void sendClientsToCacheHRS (List<Client> clients) {
        String clientParam = URLEncoder.encode(toJson(clients), StandardCharsets.UTF_8);
        String url = String.format("http://%s:%s%s%s%s?%s=%s",
                HOST,
                PORT,
                MS,
                BASE,
                UPDATE_CACHE,
                CLIENT_PARAM,
                clientParam);

        HttpRequest getRequest = buildHttpRequest(url);

        CompletableFuture<String> future = newHttpClient()
                .sendAsync(getRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);

        future.join();
    }

    /**
     * Performs monthly changes to client tariffs based on predefined rules.
     */
    private void monthlyChangeTariff () {
        List<Client> clients = clientRepository.findAll();
        Set<Long> tariffCacheKeySet = tariffCache.getAllKeysFromCache();
        List<Client> changedClients = new ArrayList<>();

        Random random = new Random();
        int countToChange = random.nextInt(3);
        Collections.shuffle(clients);

        for (int i = 0; i <= countToChange; i ++) {
            Client client = clients.get(i);

            int mapSize = tariffCacheKeySet.size();
            if (mapSize > 1) {
                Long newTariff = tariffCacheKeySet.stream()
                        .filter(key -> mapSize > 2 || !key.equals(client.getTariffId()))
                        .skip(new Random().nextInt(mapSize - 1))
                        .findFirst()
                        .orElse(null);
                Client changedClient = clientService.changeTariff(clients.get(i), newTariff);

                changedClients.add(changedClient);
            }
        }

        if (!changedClients.isEmpty()) {
            sendClientsToCacheHRS(changedClients);
        }
    }

    /**
     * Performs monthly balance top-up operations for clients based on predefined rules.
     */
    private void monthlyTopUp () {
        List<Client> clients = clientRepository.findAll();
        Random random = new Random();
        int minimalDeposit = 30;
        int maximalDeposit = 100;
        clients.forEach(client -> {
            int cash;
            if (Objects.equals(client.getTariffId(), MONTH_TARIFF)) {
                cash = random.nextInt(minimalDeposit, maximalDeposit);
            } else {
                cash = random.nextInt(minimalDeposit / 2, minimalDeposit);
            }
            BigDecimal deposit = new BigDecimal(cash);
            clientService.topUpBalance(client, deposit);
        });
    }

    /**
     * Checks if a new month has started based on call timestamps and triggers monthly operations.
     *
     * @param callDTO the CallDTO object representing a call event
     */
    private void checkNewMonth (CallDTO callDTO) {
        int startMonth = LocalDateTime.ofEpochSecond(callDTO.getStartTime(), 0, ZoneOffset.UTC).getMonthValue();
        int endMonth = LocalDateTime.ofEpochSecond(callDTO.getEndTime(), 0, ZoneOffset.UTC).getMonthValue();

        if (startMonth != lastMonthProcessed || endMonth != lastMonthProcessed) {
            if (lastMonthProcessed > startMonth && lastMonthProcessed > endMonth) {
                lastMonthProcessed = endMonth;
            }

            startMonth = Math.min (lastMonthProcessed, startMonth);
            endMonth = Math.max (lastMonthProcessed, endMonth);

            // Ежемесячное пополнение счента, для всех пользователей
            for (int i = startMonth; i <= endMonth; i++) {
                monthlyTopUp();
            }

            // Ежемесячная оплата для тех, у кого месячный тариф
            monthlyPayment(sendCheckToHRS(startMonth, endMonth));

            // Ежемесячная смена тарифов
            monthlyChangeTariff();

            lastMonthProcessed = endMonth;
        }
    }

    /**
     * Processes a list of call records and performs billing and rating operations.
     *
     * @param message the message containing call records in CSV format
     */
    void checkListCall(String message) {
        List<Client> clients = clientRepository.findAll();

        try (BufferedReader br = new BufferedReader(new StringReader(message))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                String[] items = line.split(",");

                if (items.length == 5) {
                    Client currentClientCheck = checkClient(Long.parseLong(items[1]), clients);
                    if (currentClientCheck != null) {
                        if (Long.parseLong(items[3]) < Long.parseLong(items[4])) {
                            CallDTO call = CallDTO.builder()
                                    .callType(stringToCallType(items[0]))
                                    .callerNumber(Long.parseLong(items[1]))
                                    .calleeNumber(Long.parseLong(items[2]))
                                    .startTime(Long.parseLong(items[3]))
                                    .endTime(Long.parseLong(items[4]))
                                    .tariffId(currentClientCheck.getTariffId())
                                    .build();

                            checkTariff(currentClientCheck.getTariffId());

                            checkNewMonth(call);

                            String json = call.toJson();
                            BillDTO response = sendToHRS(json);
                            clientService.newBalance(response);
                        } else {
                            log.error("Incorrect start and end of call, start: {}, end: {}", items[3], items[4]);
                        }

                    }
                } else {
                    log.error("Failure to read cdr record {}", Arrays.toString(items));
                }
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            log.error("Failure during call list check {}", e.getMessage());
        }
    }
}
