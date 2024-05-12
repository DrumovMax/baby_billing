package com.nexign.hrs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class HrsApplication {

	public static void main(String[] args) {
		SpringApplication.run(HrsApplication.class, args);
	}

}
