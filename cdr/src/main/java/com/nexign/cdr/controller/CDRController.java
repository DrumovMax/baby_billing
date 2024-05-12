package com.nexign.cdr.controller;

import com.nexign.cdr.service.CDRService;
import com.nexign.cdr.service.SubscriberService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Rest Controller for handling CDR (Call Detail Record) generator operations.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class CDRController {

    @Resource
    private CDRService cdrService;

    @Resource
    private SubscriberService subscriberService;

    /**
     * Start emulating the switch.
     *
     * @return ResponseEntity indicating success of the emulation.
     */
    @PostMapping("/start")
    public ResponseEntity<String> start() {
        cdrService.startEmulate();
        return ResponseEntity.status(HttpStatus.OK).body("Эмуляция коммутатора прошла успешно.");
    }
    /**
     * Register a stop-point for CDR.
     *
     * @return ResponseEntity with the number of registered stop-points.
     */
    @PostMapping("/register")
    public ResponseEntity<String> register() {
        int registered = cdrService.register();
        return ResponseEntity.status(HttpStatus.OK).body("Зарегистрирован стоп-поинт. Сейчас зарегистрировано: " + registered);
    }

    /**
     * Deregister a stop-point for CDR.
     *
     * @return ResponseEntity with the number of remaining registered stop-points.
     */
    @PostMapping("/deregister")
    public ResponseEntity<String> deregister() {
        int registered = cdrService.deregister();
        return ResponseEntity.status(HttpStatus.OK).body("Удален стоп-поинт. Сейчас зарегистрировано: " + registered);
    }

    /**
     * Proceed to the next iteration of CDR processing.
     *
     * @return ResponseEntity indicating the start of the next iteration.
     */
    @PostMapping("/iterate")
    public ResponseEntity<String> next() {
        cdrService.nextIteration();
        return ResponseEntity.status(HttpStatus.OK).body("Запущена следующая итерация.");
    }

    /**
     * Add a new client with the specified MSISDN.
     *
     * @param msisdn The MSISDN of the new subscriber.
     * @return ResponseEntity confirming the addition of the new subscriber.
     */
    @PostMapping("/new-client/{msisdn}")
    public ResponseEntity<String> next(@PathVariable String msisdn) {
        subscriberService.addNewUser(Long.parseLong(msisdn));
        return ResponseEntity.status(HttpStatus.OK).body("Добавлен новый абонент в генератор с номером: " + msisdn);
    }

}
