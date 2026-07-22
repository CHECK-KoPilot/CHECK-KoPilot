package com.koscom.kopilot.checkapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties(prefix = "checkapi")
public record CheckApiProperties(String baseUrl, String custId, String apiKey, Map<String, String> paths) {}
