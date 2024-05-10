package com.nexign.cdr.controller;

import com.nexign.cdr.service.CDRService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/api")
public class CDRController {

    @Resource
    private CDRService cdrService;

    @PostMapping
    public void start() {
        cdrService.startEmulate();
    }

    @PostMapping("/iterate")
    public void next() {
        cdrService.nextIteration();
    }

    @GetMapping("/test")
    public String test () {
        return "test";
    }

}
