package com.rehabit.goniometro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.rehabit.goniometro.controller"})
public class GoniometroApplication {

	public static void main(String[] args) {
		SpringApplication.run(GoniometroApplication.class, args);
	}

}
