package com.rehabit.service;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.FisioterapeutaCreateDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FisioterapeutaService {

    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final ClinicaRepository clinicaRepository;
    private final PasswordEncoder passwordEncoder;

    public FisioterapeutaService(FisioterapeutaRepository fisioterapeutaRepository,
                                  ClinicaRepository clinicaRepository,
                                  PasswordEncoder passwordEncoder) {
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.clinicaRepository = clinicaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponseDTO cadastrar(FisioterapeutaCreateDTO dados) {
        if (!clinicaRepository.existsById(dados.getIdClinica())) {
            throw new AuthException("Instituição não encontrada.", HttpStatus.BAD_REQUEST);
        }

        if (fisioterapeutaRepository.existsByEmail(dados.getEmail())
                || clinicaRepository.existsByEmail(dados.getEmail())) {
            throw new AuthException("Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
        }

        if (fisioterapeutaRepository.existsByCoffito(dados.getCoffito())) {
            throw new AuthException("Este COFFITO já está cadastrado.", HttpStatus.CONFLICT);
        }

        Fisioterapeuta fisioterapeuta = new Fisioterapeuta();
        fisioterapeuta.setIdClinica(dados.getIdClinica());
        fisioterapeuta.setNome(dados.getNome());
        fisioterapeuta.setCoffito(dados.getCoffito());
        fisioterapeuta.setEmail(dados.getEmail());
        fisioterapeuta.setSenha(passwordEncoder.encode(dados.getSenha()));
        fisioterapeuta.setTelefone(vazioParaNulo(dados.getTelefone()));
        fisioterapeuta.setEspecialidade(vazioParaNulo(dados.getEspecialidade()));
        fisioterapeuta.setDescricao(vazioParaNulo(dados.getDescricao()));
        fisioterapeuta.setLocalidade(vazioParaNulo(dados.getLocalidade()));
        fisioterapeuta.setFoto(vazioParaNulo(dados.getFoto()));

        Fisioterapeuta salvo = fisioterapeutaRepository.save(fisioterapeuta);
        return new AuthResponseDTO(salvo.getId(), "FISIOTERAPEUTA", salvo.getNome(), salvo.getEmail(), salvo.getFoto());
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
