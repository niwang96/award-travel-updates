package com.awardtravelupdates.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "groq")
public record AnthropicProperties(String apiKey) {}
