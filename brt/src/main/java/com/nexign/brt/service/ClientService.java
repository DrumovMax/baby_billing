package com.nexign.brt.service;

import com.nexign.brt.dto.BillDTO;
import com.nexign.brt.dto.ClientDTO;
import com.nexign.brt.model.Client;
import com.nexign.brt.repository.ClientRepository;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Service
public class ClientService {

    @Resource
    private ClientRepository clientRepository;

    @Transactional
    public void newBalance (BillDTO billDTO) {
        if (billDTO.getToPay().compareTo(BigDecimal.ZERO) != 0) {
            Client client = clientRepository.findClientByClientNumber(billDTO.getPhoneNumber());
            client.setBalance(client.getBalance().subtract(billDTO.getToPay()));
            clientRepository.save(client);
        }
    }

    @Transactional
    public void topUpBalance (Client client, BigDecimal deposit) {
        client.setBalance(client.getBalance().add(deposit));
        clientRepository.save(client);
    }

    @Transactional
    public Client changeTariff (Client client, Long tariffNumber) {
        client.setTariffNumber(tariffNumber);
        clientRepository.save(client);
        return client;
    }

    public ClientDTO mappingClientToDTO(Client client) {
        return ClientDTO.builder()
                .clientNumber(client.getClientNumber())
                .tariffNumber(client.getTariffNumber())
                .build();
    }

}
