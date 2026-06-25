package com.ecommint.accounthr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AccountHrApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountHrApplication.class, args);
	}

}
