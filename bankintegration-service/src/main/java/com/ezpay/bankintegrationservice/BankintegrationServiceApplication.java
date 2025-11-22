package com.ezpay.bankintegrationservice;

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BankintegrationServiceApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(BankintegrationServiceApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}

}
