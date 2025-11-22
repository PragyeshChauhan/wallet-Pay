package com.ezpay.notificationservice;
import com.ezpay.notificationservice.config.TwilioConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.ezpay.infraservice.banner.EzPayBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class NotificationServiceApplication {

	public static void main(String[] args) {
					SpringApplication app = new SpringApplication(NotificationServiceApplication.class);
					app.setBanner(new EzPayBanner());
					app.run(args);
	}

}
