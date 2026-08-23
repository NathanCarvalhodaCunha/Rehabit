package com.rehabit.controller;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.FisioterapeutaCreateDTO;
import com.rehabit.dto.FisioterapeutaPerfilDTO;
import com.rehabit.dto.FisioterapeutaResumoDTO;
import com.rehabit.dto.FisioterapeutaUpdateDTO;
import com.rehabit.service.FisioterapeutaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping
    public ResponseEntity<List<FisioterapeutaResumoDTO>> listar(@RequestParam Integer idClinica) {
        return ResponseEntity.ok(fisioterapeutaService.listarPorClinica(idClinica));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FisioterapeutaPerfilDTO> buscar(@PathVariable Integer id) {
        return ResponseEntity.ok(fisioterapeutaService.buscarPerfil(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FisioterapeutaPerfilDTO> atualizar(@PathVariable Integer id,
                                                               @Valid @RequestBody FisioterapeutaUpdateDTO dados) {
        return ResponseEntity.ok(fisioterapeutaService.atualizar(id, dados));
    }

    @PutMapping("/{id}/tutorial-visto")
    public ResponseEntity<Void> marcarTutorialVisto(@PathVariable Integer id) {
        fisioterapeutaService.marcarTutorialVisto(id);
        return ResponseEntity.ok().build();
    }
}
