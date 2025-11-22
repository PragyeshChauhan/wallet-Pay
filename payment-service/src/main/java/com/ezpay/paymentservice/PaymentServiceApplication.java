package com.ezpay.paymentservice;

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(PaymentServiceApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}

}
