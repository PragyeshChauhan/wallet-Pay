package com.ezpay.frauddetectionservice;

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FrauddetectionServiceApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(FrauddetectionServiceApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}

}
