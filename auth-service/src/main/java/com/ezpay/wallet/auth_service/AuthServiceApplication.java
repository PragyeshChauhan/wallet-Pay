package com.ezpay.wallet.auth_service;

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {

		SpringApplication app = new SpringApplication(AuthServiceApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}

}
