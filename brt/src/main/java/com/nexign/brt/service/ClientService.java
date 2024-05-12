package com.nexign.brt.service;

import com.nexign.brt.dto.BillDTO;
import com.nexign.brt.dto.ClientDTO;
import com.nexign.brt.model.Client;
import com.nexign.brt.repository.ClientRepository;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;


@Service
public class ClientService {

    @Resource
    private ClientRepository clientRepository;

    private static final String HOST = "localhost";
    private static final String PORT = "8765";
    private static final String BASE = "/api";
    private static final String NEW_CLIENT = "/new-client";

    @Transactional
    public void newClient(ClientDTO clientDTO) {
        Client client = Client.builder()
                .balance(new BigDecimal("100"))
                .msisdn(clientDTO.getMsisdn())
                .tariffId(clientDTO.getTariffId())
                .build();
        clientRepository.save(client);
        sendToAddNewUserInCDR(client);
    }

    @Transactional
    public void newBalance (BillDTO billDTO) {
        if (billDTO.getToPay().compareTo(BigDecimal.ZERO) > 0) {
            Client client = clientRepository.findClientByMsisdn(billDTO.getPhoneNumber());
            if (client != null) {
                client.setBalance(client.getBalance().subtract(billDTO.getToPay()));
                clientRepository.save(client);
            }
        }
    }

    @Transactional
    public void topUpBalance (Client client, BigDecimal deposit) {
        if (deposit.compareTo(BigDecimal.ZERO) > 0) {
            client.setBalance(client.getBalance().add(deposit));
            clientRepository.save(client);
        }
    }

    @Transactional
    public Client changeTariff (Client client, Long tariffNumber) {
        client.setTariffId(tariffNumber);
        clientRepository.save(client);
        return client;
    }

    private void sendToAddNewUserInCDR (Client client) {
        String url = String.format("http://%s:%s%s%s/%s",
                HOST,
                PORT,
                BASE,
                NEW_CLIENT,
                client.getMsisdn());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        CompletableFuture<String> future = httpClient
                .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);

        future.join();
    }

    public Client getClient (Long msisdn) {
        return clientRepository.findClientByMsisdn(msisdn);
    }

    public ClientDTO mappingClientToDTO(Client client) {
        return ClientDTO.builder()
                .msisdn(client.getMsisdn())
                .tariffId(client.getTariffId())
                .build();
    }

}
