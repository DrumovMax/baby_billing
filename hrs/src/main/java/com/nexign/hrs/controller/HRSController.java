package com.nexign.hrs.controller;

import com.google.gson.reflect.TypeToken;
import com.nexign.hrs.dto.BillDTO;
import com.nexign.hrs.model.Tariff;
import com.nexign.hrs.service.HRSService;
import com.nexign.hrs.dto.CallDTO;
import com.nexign.hrs.service.TariffService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin("*")
@RestController
@RequestMapping("/api")
public class HRSController {

    @Resource
    private HRSService hrsService;

    @Resource
    private TariffService tariffService;

    @GetMapping("/monthly-payment")
    public ResponseEntity<String> monthlyPayment (@RequestParam(name = "start-month") String startMonth,
                                 @RequestParam(name = "end-month") String endMonth) {
        String json = hrsService.checkNewMonth(Integer.parseInt(startMonth), Integer.parseInt(endMonth));
        return ResponseEntity.status(HttpStatus.OK).body(json);
    }

    @GetMapping("/payment")
    public ResponseEntity<String> payment (@RequestParam(name = "call-param") String callParam) {
        String inputJson = hrsService.decodeUrl(callParam);
        CallDTO callDTO = hrsService.fromJson(inputJson, new TypeToken<>() {});
        BillDTO billDTO = hrsService.callCalculation(callDTO);
        String json = billDTO.toJson();
        return ResponseEntity.status(HttpStatus.OK).body(json);
    }

    @GetMapping("/check-tariff/{tariffId}")
    public ResponseEntity<String> checkTariff (@PathVariable String tariffId) {
        Tariff tariff = tariffService.checkTariff(Long.parseLong(tariffId));
        if (tariff == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Tariff not found");
        }

        String json = tariffService.mappingTariffToDTO(tariff).toJson();
        return ResponseEntity.status(HttpStatus.OK).body(json);
    }

    @GetMapping("/update-cache")
    public ResponseEntity<String> updateCache (@RequestParam(name = "client-param") String clientParam) {
        String inputJson = hrsService.decodeUrl(clientParam);

        hrsService.updateClientCacheByListClient(inputJson);

        return ResponseEntity.status(HttpStatus.OK).body("Clients updated");
    }

}