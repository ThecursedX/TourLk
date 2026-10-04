package com.tourlk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TourlkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TourlkApplication.class, args);
    }

}
