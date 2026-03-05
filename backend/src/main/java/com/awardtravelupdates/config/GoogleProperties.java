package com.awardtravelupdates.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google")
public record GoogleProperties(String clientId, String clientSecret, String gmailRefreshToken) {}
