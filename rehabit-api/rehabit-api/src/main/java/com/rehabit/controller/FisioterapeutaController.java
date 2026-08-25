package com.rehabit.controller;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.DesempenhoDTO;
import com.rehabit.dto.FisioterapeutaCreateDTO;
import com.rehabit.dto.FisioterapeutaPerfilDTO;
import com.rehabit.dto.FisioterapeutaResumoDTO;
import com.rehabit.dto.FisioterapeutaUpdateDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.DesempenhoService;
import com.rehabit.service.FisioterapeutaService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final DesempenhoService desempenhoService;

    public FisioterapeutaController(FisioterapeutaService fisioterapeutaService,
                                     DesempenhoService desempenhoService) {
        this.fisioterapeutaService = fisioterapeutaService;
        this.desempenhoService = desempenhoService;
    }

    // Endpoint usado pela instituição já logada para cadastrar um
    // fisioterapeuta vinculado a ela (tela cadastrar-profissional.html).
    @PostMapping
    public ResponseEntity<AuthResponseDTO> cadastrar(@Valid @RequestBody FisioterapeutaCreateDTO dados,
                                                        HttpServletRequest request) {
        AuthResponseDTO resposta = fisioterapeutaService.cadastrar(
                dados, AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    @GetMapping
    public ResponseEntity<List<FisioterapeutaResumoDTO>> listar(@RequestParam Integer idClinica,
                                                                    HttpServletRequest request) {
        return ResponseEntity.ok(fisioterapeutaService.listarPorClinica(
                idClinica, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FisioterapeutaPerfilDTO> buscar(@PathVariable Integer id, HttpServletRequest request) {
        return ResponseEntity.ok(fisioterapeutaService.buscarPerfil(
                id, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FisioterapeutaPerfilDTO> atualizar(@PathVariable Integer id,
                                                               @Valid @RequestBody FisioterapeutaUpdateDTO dados,
                                                               HttpServletRequest request) {
        return ResponseEntity.ok(fisioterapeutaService.atualizar(
                id, dados, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer id, HttpServletRequest request) {
        fisioterapeutaService.excluir(id, AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.noContent().build();
    }

    /** Indicadores e evolução dos pacientes deste profissional. */
    @GetMapping("/{id}/desempenho")
    public ResponseEntity<DesempenhoDTO> desempenho(@PathVariable Integer id, HttpServletRequest request) {
        return ResponseEntity.ok(desempenhoService.doFisioterapeuta(
                id, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PutMapping("/{id}/tutorial-visto")
    public ResponseEntity<Void> marcarTutorialVisto(@PathVariable Integer id) {
        fisioterapeutaService.marcarTutorialVisto(id);
        return ResponseEntity.ok().build();
    }
}
