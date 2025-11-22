package com.ezpay.configserver;

import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ConfigServerApplication.class);
		app.setBanner(new EzPayBanner());
		app.run(args);
	}

}
