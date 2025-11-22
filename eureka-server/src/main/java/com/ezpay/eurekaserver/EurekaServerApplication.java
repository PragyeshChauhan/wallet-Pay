package com.ezpay.eurekaserver; // Adjust package as needed

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(EurekaServerApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}
}