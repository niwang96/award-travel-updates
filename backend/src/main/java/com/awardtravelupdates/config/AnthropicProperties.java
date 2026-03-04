package com.awardtravelupdates.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record AnthropicProperties(String apiKey) {}
