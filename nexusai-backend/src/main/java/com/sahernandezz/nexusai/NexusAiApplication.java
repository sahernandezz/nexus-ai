package com.sahernandezz.nexusai;

import com.sahernandezz.nexusai.config.NativeRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@ImportRuntimeHints(NativeRuntimeHints.class)
public class NexusAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusAiApplication.class, args);
    }
}
