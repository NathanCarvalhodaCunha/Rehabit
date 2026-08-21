package com.rehabit.controller;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.FisioterapeutaCreateDTO;
import com.rehabit.service.FisioterapeutaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fisioterapeutas")
@CrossOrigin(origins = "*") // ajuste para o(s) domínio(s) real(is) do front-end em produção
public class FisioterapeutaController {

    private final FisioterapeutaService fisioterapeutaService;

    public FisioterapeutaController(FisioterapeutaService fisioterapeutaService) {
        this.fisioterapeutaService = fisioterapeutaService;
    }

    // Endpoint usado pela instituição já logada para cadastrar um
    // fisioterapeuta vinculado a ela (tela cadastrar-profissional.html).
    @PostMapping
    public ResponseEntity<AuthResponseDTO> cadastrar(@Valid @RequestBody FisioterapeutaCreateDTO dados) {
        AuthResponseDTO resposta = fisioterapeutaService.cadastrar(dados);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }
}
