package com.nexign.brt.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nexign.brt.cache.TariffCache;
import com.nexign.brt.dto.BillDTO;
import com.nexign.brt.dto.CallDTO;
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

@Slf4j
@Service
public class BRTService {

    @Resource
    private ClientRepository clientRepository;

    @Resource
    private TariffCache tariffCache;

    @Resource
    private ClientService clientService;

    @Resource
    private KafkaBRTProducer kafkaProducer;

    @Value("${hrs.host}")
    private String HOST;
    private static final String PORT = "8082";
    private static final String BASE = "/api/hrs";
    private static final String PAYMENT = "/payment";
    private static final String MONTHLY_PAYMENT = "/monthly-payment";
    private static final String PARAM_START_MONTH = "start-month";
    private static final String PARAM_END_MONTH = "end-month";
    private static final String CALL_PARAM = "call-param";
    private static final Long MONTH_TARIFF = 12L;

    private Integer lastMonthProcessed = -1;

    private final Gson gson;

    public BRTService() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    public BillDTO decodeJsonToBillData (String json) {
        return gson.fromJson(json, new TypeToken<BillDTO>() {}.getType());
    }

    public List<BillDTO> decodeJsonToListBillDTO(String json) {
        return gson.fromJson(json, new TypeToken<List<BillDTO>>() {}.getType());
    }



    public void monthlyPayment (List<BillDTO> bills) {
        for (BillDTO billDTO : bills) {
            clientService.newBalance(billDTO);
        }
    }

    public String decodeData (String message) {
        return new String(Base64.getDecoder().decode(message), StandardCharsets.UTF_8);
    }


    private Client checkClient (Long callerNumber, List<Client> clients) {
        return clients.stream()
                .filter(c -> c.getClientNumber().equals(callerNumber))
                .findFirst()
                .orElse(null);
    }

    private CallType stringToCallType (String callTypeString) {
        return callTypeString.equals("01") ? CallType.INCOMING : CallType.OUTCOMING;
    }

    private BillDTO sendToHRS (String json) throws IOException, InterruptedException, ExecutionException {
        String callParam = URLEncoder.encode(json, StandardCharsets.UTF_8);
        String url = String.format("http://%s:%s%s%s?%s=%s",
                HOST,
                PORT,
                BASE,
                PAYMENT,
                CALL_PARAM,
                callParam);

        BillDTO responseBillDTO;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        HttpRequest getRequestTest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        CompletableFuture<BillDTO> future = httpClient
                .sendAsync(getRequestTest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(this::decodeJsonToBillData);

        responseBillDTO = future.join();
        return responseBillDTO;
    }

    private List<BillDTO> sendCheckToHRS (int startMonth, int endMonth) {
        String url = String.format("http://%s:%s%s%s?%s=%s&%s=%s",
                HOST,
                PORT,
                BASE,
                MONTHLY_PAYMENT,
                PARAM_START_MONTH,
                startMonth,
                PARAM_END_MONTH,
                endMonth);

        List<BillDTO> responseBillData;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        CompletableFuture<List<BillDTO>> future = httpClient
                .sendAsync(getRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(this::decodeJsonToListBillDTO);

        responseBillData = future.join();
        return responseBillData;

    }

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
            Long newTariff = tariffCacheKeySet.stream()
                    .filter(key -> mapSize > 2 || !key.equals(client.getTariffNumber()))
                    .skip(new Random().nextInt(mapSize - 1))
                    .findFirst()
                    .orElse(null);
            Client changedClient = clientService.changeTariff(clients.get(i), newTariff);

            changedClients.add(changedClient);
        }

        kafkaProducer.updateClientCache(changedClients);
    }

    private void monthlyTopUp () {
        List<Client> clients = clientRepository.findAll();
        Random random = new Random();
        int minimalDeposit = 30;
        int maximalDeposit = 100;
        clients.forEach(client -> {
            int cash;
            if (Objects.equals(client.getTariffNumber(), MONTH_TARIFF)) {
                cash = random.nextInt(minimalDeposit, maximalDeposit);
            } else {
                cash = random.nextInt(minimalDeposit / 2, minimalDeposit);
            }
            BigDecimal deposit = new BigDecimal(cash);
            clientService.topUpBalance(client, deposit);
        });
    }

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

    void checkListCall(String message) {
        List<Client> clients = clientRepository.findAll();

        try (BufferedReader br = new BufferedReader(new StringReader(message))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                String[] items = line.split(",");
                System.out.println(Arrays.toString(items));

                Client currentClientCheck = checkClient(Long.parseLong(items[1]), clients);
                if (currentClientCheck != null) {
                    CallDTO call = CallDTO.builder()
                            .callType(stringToCallType(items[0]))
                            .callerNumber(Long.parseLong(items[1]))
                            .calleeNumber(Long.parseLong(items[2]))
                            .startTime(Long.parseLong(items[3]))
                            .endTime(Long.parseLong(items[4]))
                            .tariffId(currentClientCheck.getTariffNumber())
                            .build();

                    checkNewMonth(call);

                    String json = call.toJson();
                    BillDTO response = sendToHRS(json);
                    clientService.newBalance(response);
                }
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            log.error("Failure during call list check {}", e.getMessage());
        }
    }
}
