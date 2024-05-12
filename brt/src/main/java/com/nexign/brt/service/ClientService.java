package com.nexign.brt.service;

import com.nexign.brt.dto.BillDTO;
import com.nexign.brt.dto.ClientDTO;
import com.nexign.brt.model.Client;
import com.nexign.brt.repository.ClientRepository;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Service class responsible for handling client-related operations.
 */
@Service
public class ClientService {

    @Resource
    private ClientRepository clientRepository;

    @Value("${gateway.host}")
    private String HOST;
    private static final String PORT = "8765";
    private static final String BASE = "/api";
    private static final String NEW_CLIENT = "/new-client";

    /**
     * Creates a new client based on the provided ClientDTO and saves it to the repository.
     * Sends a request to add the new user to the CDR system.
     *
     * @param clientDTO the DTO containing client information
     */
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

    /**
     * Updates the client's balance based on a bill DTO.
     * Deducts the bill amount from the client's balance.
     *
     * @param billDTO the DTO containing billing information
     */
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

    /**
     * Adds the specified deposit amount to the client's balance.
     *
     * @param client  the client whose balance is to be topped up
     * @param deposit the amount to be added to the balance
     */
    @Transactional
    public void topUpBalance (Client client, BigDecimal deposit) {
        if (deposit.compareTo(BigDecimal.ZERO) > 0) {
            client.setBalance(client.getBalance().add(deposit));
            clientRepository.save(client);
        }
    }

    /**
     * Changes the tariff of the specified client and saves the updated client to the repository.
     *
     * @param client       the client whose tariff is to be changed
     * @param tariffNumber the new tariff ID to set for the client
     * @return the updated client object
     */
    @Transactional
    public Client changeTariff (Client client, Long tariffNumber) {
        client.setTariffId(tariffNumber);
        clientRepository.save(client);
        return client;
    }

    /**
     * Sends an HTTP request to add a new user to the Call Detail Record (CDR) system.
     *
     * @param client the client whose addition to CDR is requested
     */
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

    /**
     * Retrieves a client from the repository based on the given MSISDN (phone number).
     *
     * @param msisdn the MSISDN of the client to retrieve
     * @return the client object corresponding to the MSISDN, or null if not found
     */
    public Client getClient (Long msisdn) {
        return clientRepository.findClientByMsisdn(msisdn);
    }

    /**
     * Maps a Client object to a ClientDTO object for data transfer purposes.
     *
     * @param client the client object to map to DTO
     * @return the corresponding ClientDTO object
     */
    public ClientDTO mappingClientToDTO(Client client) {
        return ClientDTO.builder()
                .msisdn(client.getMsisdn())
                .tariffId(client.getTariffId())
                .build();
    }

}
