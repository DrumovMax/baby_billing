package com.nexign.cdr.controller;

import com.nexign.cdr.service.CDRService;
import com.nexign.cdr.service.SubscriberService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class CDRController {

    @Resource
    private CDRService cdrService;

    @Resource
    private SubscriberService subscriberService;

    @PostMapping("/start")
    public ResponseEntity<String> start() {
        cdrService.startEmulate();
        return ResponseEntity.status(HttpStatus.OK).body("Эмуляция коммутатора прошла успешно.");
    }

    @PostMapping("/register")
    public ResponseEntity<String> register() {
        int registered = cdrService.register();
        return ResponseEntity.status(HttpStatus.OK).body("Зарегистрирован стоп-поинт. Сейчас зарегистрировано: " + registered);
    }

    @PostMapping("/deregister")
    public ResponseEntity<String> deregister() {
        int registered = cdrService.deregister();
        return ResponseEntity.status(HttpStatus.OK).body("Удален стоп-поинт. Сейчас зарегистрировано: " + registered);
    }

    @PostMapping("/iterate")
    public ResponseEntity<String> next() {
        cdrService.nextIteration();
        return ResponseEntity.status(HttpStatus.OK).body("Запущена следующая итерация.");
    }

    @PostMapping("/new-client/{msisdn}")
    public ResponseEntity<String> next(@PathVariable String msisdn) {
        subscriberService.addNewUser(Long.parseLong(msisdn));
        return ResponseEntity.status(HttpStatus.OK).body("Добавлен новый абонент в генератор с номером: " + msisdn);
    }

}
