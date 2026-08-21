package com.rehabit.goniometro.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/goniometro/dados")

public class Goniometro {
	
	
	@PostMapping
	public ResponseEntity<String> receberDados(@RequestBody GoniometroDados dados) {

		System.out.println("X: " + dados.getX());
		System.out.println("Y: " + dados.getY());
		System.out.println("Z: " + dados.getZ());

		return ResponseEntity.ok("Dados Recebidos!" + "\n"
				+ "X: " + dados.getX() + "\n"
				+ "Y: " + dados.getY() + "\n"
				+ "Z: " + dados.getZ() + "");
		
    }
	
}
