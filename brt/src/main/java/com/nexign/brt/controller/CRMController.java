package com.nexign.brt.controller;

import com.nexign.brt.dto.ChangeTariffDTO;
import com.nexign.brt.dto.ClientDTO;
import com.nexign.brt.dto.TopUpDTO;
import com.nexign.brt.model.Client;
import com.nexign.brt.service.BRTService;
import com.nexign.brt.service.ClientService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Objects;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class CRMController {

    @Resource
    private BRTService brtService;

    @Resource
    private ClientService clientService;

    @PatchMapping("/subscriber/{msisdn}/pay")
    public ResponseEntity<String> topUpBalance (@PathVariable("msisdn") String msisdn, @RequestBody TopUpDTO topUpDTO) {
        System.out.println(msisdn);
        Client client = clientService.getClient(Long.parseLong(msisdn));
        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Абонент не является клиентом Ромашки: " + msisdn);
        }

        if (topUpDTO.getMoney().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Сумма введена некорректно " + topUpDTO.getMoney());
        } else if (topUpDTO.getMoney().compareTo(new BigDecimal ("0.1")) < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Сумма должна быть больше чем 0.1. Введенная сумма: " + topUpDTO.getMoney());
        }

        try {
            clientService.topUpBalance(client, topUpDTO.getMoney());
            return ResponseEntity.status(HttpStatus.OK)
                                 .body("Баланс клиента: " + msisdn + ", пополнен на " + topUpDTO.getMoney());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Ошибка при обработке данных: " + e.getMessage());
        }
    }

    @PatchMapping("/subscriber/{msisdn}/changeTariff")
    public ResponseEntity<String> changeTariff (@PathVariable String msisdn, @RequestBody ChangeTariffDTO changeTariffDTO) {
        Client client = clientService.getClient(Long.parseLong(msisdn));
        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Абонент не является клиентом Ромашки: " + msisdn);
        }
        if (Objects.equals(changeTariffDTO.getTariffId(), client.getTariffId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Абонент " + msisdn + " уже пользуется тарифом " + changeTariffDTO.getTariffId());
        }

        if (brtService.checkTariff(changeTariffDTO.getTariffId())) {
            try {
                clientService.changeTariff(client, changeTariffDTO.getTariffId());
                return ResponseEntity.status(HttpStatus.OK)
                        .body("Тариф клиента: " + msisdn + ", был изменен на " + changeTariffDTO.getTariffId());
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Ошибка при обработке данных: " + e.getMessage());
            }
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Тариф введен некорректно " + changeTariffDTO.getTariffId());
        }
    }

    @PostMapping("/subscriber/save")
    public ResponseEntity<String> addNewClient (@RequestBody ClientDTO clientDTO) {
        Client client = clientService.getClient(clientDTO.getMsisdn());
        if (client != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Абонент уже является клиентом Ромашки: " + clientDTO.getMsisdn());
        }

        if (!brtService.checkMsisdn(clientDTO.getMsisdn())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Номер телефона введен некорректно " + clientDTO.getTariffId());
        }

        if (!brtService.checkTariff(clientDTO.getTariffId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Тариф введен некорректно " + clientDTO.getTariffId());
        }

        try {
            clientService.newClient(clientDTO);
            return ResponseEntity.status(HttpStatus.OK)
                    .body("Добавлен новый абонент: " + clientDTO.getMsisdn()
                            + ", с тарифом " + clientDTO.getTariffId());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при обработке данных: " + e.getMessage());
        }

    }

    @GetMapping("/check-msisdn/{msisdn}")
    public ResponseEntity<String> checkClient (@PathVariable String msisdn) {
        Client client = clientService.getClient(Long.parseLong(msisdn));
        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found");
        }

        String json = clientService.mappingClientToDTO(client).toJson();
        return ResponseEntity.status(HttpStatus.OK).body(json);
    }

}
