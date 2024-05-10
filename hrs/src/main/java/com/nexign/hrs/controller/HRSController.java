package com.nexign.hrs.controller;

import com.nexign.hrs.dto.BillDTO;
import com.nexign.hrs.service.HRSService;
import com.nexign.hrs.dto.CallDTO;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hrs")
public class HRSController {

    @Resource
    private HRSService hrsService;

    @GetMapping("/monthly-payment")
    public String monthlyPayment(@RequestParam(name = "start-month") String startMonth,
                                 @RequestParam(name = "end-month") String endMonth) {
        System.out.println(startMonth + " " + endMonth);
        return hrsService.checkNewMonth(Integer.parseInt(startMonth), Integer.parseInt(endMonth));
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/payment")
    public String payment(@RequestParam(name = "call-param") String callParam) {
        String json = hrsService.decodeUrl(callParam);
        CallDTO callDTO = hrsService.getCallDataFromString(json);
        BillDTO billDTO = hrsService.callCalculation(callDTO);

        return billDTO.toJson();
    }

}