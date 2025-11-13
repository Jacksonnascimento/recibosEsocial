package com.recibos.recibos_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// MUDANÇA: Importar EnableAsync
import org.springframework.scheduling.annotation.EnableAsync;

import com.recibos.recibos_service.util.LimpadorDePasta;

@SpringBootApplication
@EnableAsync // MUDANÇA: Habilitar processamento assíncrono
public class RecibosServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecibosServiceApplication.class, args);
		new LimpadorDePasta("scripts_gerados");
	}
}