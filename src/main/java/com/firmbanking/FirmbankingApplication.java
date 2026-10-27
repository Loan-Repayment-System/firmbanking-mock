package com.firmbanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FirmbankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(FirmbankingApplication.class, args);
    }
}
