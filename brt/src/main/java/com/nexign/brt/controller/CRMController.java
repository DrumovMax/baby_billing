package com.nexign.brt.controller;

import com.nexign.brt.dto.ChangeTariffDTO;
import com.nexign.brt.dto.ClientDTO;
import com.nexign.brt.dto.TopUpDTO;
import com.nexign.brt.model.Client;
import com.nexign.brt.service.BRTService;
import com.nexign.brt.service.ClientService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

import java.util.Base64;
import java.util.Objects;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class CRMController {

    @Resource
    private BRTService brtService;

    @Resource
    private ClientService clientService;

    private static final String AUTHORIZATION = "Authorization";
    private static final String ADMIN = "admin";
    private static final String BASIC = "basic";

    @PatchMapping("/subscriber/{msisdn}/pay")
    public ResponseEntity<String> topUpBalance (@PathVariable("msisdn") String msisdn,
                                                @RequestBody TopUpDTO topUpDTO,
                                                @RequestHeader(AUTHORIZATION) HttpHeaders header) {
            if (isSubscriberAuthorized(header, Long.parseLong(msisdn))) {
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
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Абонент не авторизован: " + msisdn);
            }
    }

    @PatchMapping("/subscriber/{msisdn}/changeTariff")
    public ResponseEntity<String> changeTariff (@PathVariable String msisdn,
                                                @RequestBody ChangeTariffDTO changeTariffDTO,
                                                @RequestHeader(AUTHORIZATION) HttpHeaders header) {
        if (isAdminAuthorized(header)) {
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
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Менеджер не авторизован.");
        }

    }

    @PostMapping("/subscriber/save")
    public ResponseEntity<String> addNewClient (@RequestBody ClientDTO clientDTO,
                                                @RequestHeader(AUTHORIZATION) HttpHeaders header) {
        if (isAdminAuthorized(header)) {
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
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Менеджер не авторизован.");
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

    public boolean isAdminAuthorized (HttpHeaders headers) {
        String[] credentials = getBasicHeader(headers);
        if (credentials.length != 2) {
            return false;
        }
        String username = credentials[0];
        String password = credentials[1];
        return ADMIN.equals(username) && ADMIN.equals(password);
    }

    public boolean isSubscriberAuthorized (HttpHeaders headers, Long msisdn) {
        String[] credentials = getBasicHeader(headers);
        if (credentials.length != 2) {
            return false;
        }
        String username = credentials[0];
        return msisdn.equals(Long.parseLong(username));
    }

    private static String[] getBasicHeader (HttpHeaders headers) {
        if (headers.containsKey(HttpHeaders.AUTHORIZATION)) {
            String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.toLowerCase().startsWith(BASIC)) {
                String base64Credentials = authHeader.substring(BASIC.length()).trim();
                String credentials = new String(Base64.getDecoder().decode(base64Credentials));
                return credentials.split(":", 2);
            }
        }
        return new String[0];
    }

}
