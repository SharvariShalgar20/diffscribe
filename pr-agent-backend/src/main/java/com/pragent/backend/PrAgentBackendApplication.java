package com.pragent.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PrAgentBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrAgentBackendApplication.class, args);
	}

}
