package com.ezpay.loanservice;

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LoanServiceApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(LoanServiceApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}

}
