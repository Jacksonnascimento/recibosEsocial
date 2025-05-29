package com.recibos.recibos_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.recibos.recibos_service.util.LimpadorDePasta;



@SpringBootApplication
public class RecibosServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecibosServiceApplication.class, args);
		new LimpadorDePasta("scripts_gerados");
	}

}
