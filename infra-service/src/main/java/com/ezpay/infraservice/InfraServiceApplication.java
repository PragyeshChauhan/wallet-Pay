package com.ezpay.infraservice;

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InfraServiceApplication {
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(InfraServiceApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}

}
