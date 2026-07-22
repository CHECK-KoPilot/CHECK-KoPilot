package com.koscom.kopilot.checkapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class CheckApiConfig {

    @Bean
    @Profile("!fixture")
    public CheckApiClient restCheckApiClient(CheckApiProperties props) {
        return new RestCheckApiClient(props);
    }

    @Bean
    @Profile("fixture")
    public CheckApiClient fixtureCheckApiClient() {
        return new FixtureCheckApiClient();
    }
}
