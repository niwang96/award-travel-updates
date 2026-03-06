package com.awardtravelupdates.controller;

import com.awardtravelupdates.service.DiscordNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DiscordController {

    private final DiscordNotificationService discordNotificationService;

    @PostMapping("/discord/send")
    public ResponseEntity<Void> send() {
        discordNotificationService.send();
        return ResponseEntity.ok().build();
    }
}
