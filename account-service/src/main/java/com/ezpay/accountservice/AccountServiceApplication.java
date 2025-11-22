package com.ezpay.accountservice;

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AccountServiceApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(AccountServiceApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}

}
