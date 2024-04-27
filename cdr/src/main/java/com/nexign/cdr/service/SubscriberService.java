package com.nexign.cdr.service;

import com.nexign.cdr.model.Subscriber;
import com.nexign.cdr.repository.SubscriberRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SubscriberService {

   /* @Resource
    private SubscriberRepository subscriberRepository;

    public void initSubs () {
        List<Long> phoneNumbers = new ArrayList<>() {};
        for (int i = 0; i < 10; i++) {
            Long newPhoneNumber = generatePhoneNumber();
            Subscriber sub = new Subscriber();
            sub.setPhoneNumber(newPhoneNumber);
            subscriberRepository.save(sub);
        }
    }*/
}
