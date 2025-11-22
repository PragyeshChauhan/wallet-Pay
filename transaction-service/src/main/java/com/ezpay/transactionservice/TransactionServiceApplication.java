package com.ezpay.transactionservice;

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TransactionServiceApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(TransactionServiceApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}

}
