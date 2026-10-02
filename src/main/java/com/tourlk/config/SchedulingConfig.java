package com.tourlk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs (vehicle compliance reminders, accommodation availability sync). */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
