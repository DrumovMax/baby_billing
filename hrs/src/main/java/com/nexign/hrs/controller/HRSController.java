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

/**
 * Controller class responsible for handling HTTP requests related to HRS operations.
 */
@CrossOrigin("*")
@RestController
@RequestMapping("/api")
public class HRSController {

    @Resource
    private HRSService hrsService;

    @Resource
    private TariffService tariffService;

    /**
     * Initiates the monthly payment process for a specified range of months.
     *
     * @param startMonth Start month for monthly payment
     * @param endMonth   End month for monthly payment
     * @return ResponseEntity containing JSON response with payment details
     */
    @GetMapping("/monthly-payment")
    public ResponseEntity<String> monthlyPayment (@RequestParam(name = "start-month") String startMonth,
                                 @RequestParam(name = "end-month") String endMonth) {
        String json = hrsService.checkNewMonth(Integer.parseInt(startMonth), Integer.parseInt(endMonth));
        return ResponseEntity.status(HttpStatus.OK).body(json);
    }

    /**
     * Calculates payment for a call based on the provided call parameters.
     *
     * @param callParam Encoded call parameters
     * @return ResponseEntity containing JSON response with billing details
     */
    @GetMapping("/payment")
    public ResponseEntity<String> payment (@RequestParam(name = "call-param") String callParam) {
        String inputJson = hrsService.decodeUrl(callParam);
        CallDTO callDTO = hrsService.fromJson(inputJson, new TypeToken<>() {});
        BillDTO billDTO = hrsService.callCalculation(callDTO);
        String json = billDTO.toJson();
        return ResponseEntity.status(HttpStatus.OK).body(json);
    }

    /**
     * Retrieves tariff information for the specified tariff ID.
     *
     * @param tariffId Tariff ID to check
     * @return ResponseEntity containing JSON response with tariff details
     */
    @GetMapping("/check-tariff/{tariffId}")
    public ResponseEntity<String> checkTariff (@PathVariable String tariffId) {
        Tariff tariff = tariffService.checkTariff(Long.parseLong(tariffId));
        if (tariff == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Tariff not found");
        }

        String json = tariffService.mappingTariffToDTO(tariff).toJson();
        return ResponseEntity.status(HttpStatus.OK).body(json);
    }

    /**
     * Updates the client cache based on the provided client parameters.
     *
     * @param clientParam Encoded client parameters
     * @return ResponseEntity indicating success or failure of cache update
     */
    @GetMapping("/update-cache")
    public ResponseEntity<String> updateCache (@RequestParam(name = "client-param") String clientParam) {
        String inputJson = hrsService.decodeUrl(clientParam);

        hrsService.updateClientCacheByListClient(inputJson);

        return ResponseEntity.status(HttpStatus.OK).body("Clients updated");
    }

}