package com.rehabit.controller;

import com.rehabit.dto.GoniometroDTO;
import com.rehabit.dto.GoniometroLeituraDTO;
import com.rehabit.dto.GoniometroLeituraRespostaDTO;
import com.rehabit.dto.GoniometroSincronizarDTO;
import com.rehabit.security.AuthContext;
import com.rehabit.service.DispositivoService;
import com.rehabit.service.GoniometroService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goniometro")
@CrossOrigin(origins = "*")
public class GoniometroController {

    private final GoniometroService goniometroService;
    private final DispositivoService dispositivoService;

    public GoniometroController(GoniometroService goniometroService,
                                  DispositivoService dispositivoService) {
        this.goniometroService = goniometroService;
        this.dispositivoService = dispositivoService;
    }

    @GetMapping
    public ResponseEntity<GoniometroDTO> buscar(@RequestParam Integer idClinica, HttpServletRequest request) {
        return ResponseEntity.ok(goniometroService.buscarUltimo(
                idClinica, AuthContext.id(request), AuthContext.tipo(request)));
    }

    @PostMapping("/sincronizar")
    public ResponseEntity<GoniometroDTO> sincronizar(@Valid @RequestBody GoniometroSincronizarDTO dados,
                                                        HttpServletRequest request) {
        return ResponseEntity.ok(goniometroService.sincronizar(
                dados.getIdClinica(), AuthContext.id(request), AuthContext.tipo(request)));
    }

    /**
     * Aceita tanto um goniômetro pareado quanto a clínica logada. Vindo do
     * aparelho, a clínica sai do próprio token — o corpo não manda idClinica,
     * então um dispositivo não consegue escrever na clínica de outro.
     */
    @PostMapping("/leitura")
    public ResponseEntity<Void> registrarLeitura(@Valid @RequestBody GoniometroLeituraDTO dados,
                                                    HttpServletRequest request) {
        Integer idClinicaDoToken = AuthContext.idClinicaDoDispositivo(request);

        if (idClinicaDoToken != null) {
            Integer idClinicaAtiva = dispositivoService.exigirDispositivoAtivo(AuthContext.id(request));
            goniometroService.registrarLeituraDeDispositivo(idClinicaAtiva, dados.getAngulo());
            return ResponseEntity.ok().build();
        }

        if (dados.getIdClinica() == null) {
            throw new com.rehabit.exception.AuthException("A clínica é obrigatória.",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        goniometroService.registrarLeitura(
                dados.getIdClinica(), AuthContext.id(request), AuthContext.tipo(request), dados.getAngulo());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/leitura")
    public ResponseEntity<GoniometroLeituraRespostaDTO> buscarLeitura(@RequestParam Integer idClinica,
                                                                          HttpServletRequest request) {
        var angulo = goniometroService.buscarLeituraAtual(idClinica, AuthContext.id(request), AuthContext.tipo(request));
        return ResponseEntity.ok(new GoniometroLeituraRespostaDTO(angulo));
    }
}
