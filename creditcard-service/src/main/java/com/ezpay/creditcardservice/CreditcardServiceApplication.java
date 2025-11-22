package com.ezpay.creditcardservice;

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CreditcardServiceApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(CreditcardServiceApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}

}
