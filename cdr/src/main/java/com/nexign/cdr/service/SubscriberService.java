package com.nexign.cdr.service;

import com.nexign.cdr.model.Subscriber;
import com.nexign.cdr.repository.SubscriberRepository;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class SubscriberService {

    @Resource
    private SubscriberRepository subscriberRepository;

    @Transactional
    public void addNewUser (Long msisdn) {
        Subscriber subscriber = Subscriber.builder()
                .phoneNumber(msisdn)
                .isRomashka(true)
                .build();
        subscriberRepository.save(subscriber);
    }
}
