package com.example.controller;

import com.example.NetworkSnapshot;
import com.example.service.NetworkTrackKafkaProducer;
import com.example.service.NetworkTrackValidator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/track")
public class NetworkTrackController {

    private final NetworkTrackValidator validator;
    private final NetworkTrackKafkaProducer producer;

    public NetworkTrackController(NetworkTrackValidator validator, NetworkTrackKafkaProducer producer) {
        this.validator = validator;
        this.producer = producer;
    }

    @PostMapping("/save")
    private boolean saveTrack(List<NetworkSnapshot> track) {
         if(validator.isValid(track)) {
             producer.send(track);
             return true;
         }
         return false;
    }
}
