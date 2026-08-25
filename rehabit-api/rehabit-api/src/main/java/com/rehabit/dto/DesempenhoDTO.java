package com.rehabit.dto;

import java.util.List;

/** Desempenho de um profissional: totais, série mensal e evolução por paciente. */
public class DesempenhoDTO {

    private Integer idFisioterapeuta;
    private String nomeFisioterapeuta;
    private String especialidade;
    private String foto;

    private long totalPacientes;
    private long pacientesAtivos;
    private long pacientesAlta;
    private long totalSessoes;
    private long sessoesUltimos30Dias;

    /** Ganho médio de amplitude entre a primeira e a última medição de cada paciente. */
    private Double ganhoMedioGraus;

    private List<PontoMensalDTO> sessoesPorMes;
    private List<EvolucaoPacienteDTO> pacientes;

    public DesempenhoDTO() {
    }

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }

    public String getNomeFisioterapeuta() {
        return nomeFisioterapeuta;
    }

    public void setNomeFisioterapeuta(String nomeFisioterapeuta) {
        this.nomeFisioterapeuta = nomeFisioterapeuta;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public long getTotalPacientes() {
        return totalPacientes;
    }

    public void setTotalPacientes(long totalPacientes) {
        this.totalPacientes = totalPacientes;
    }

    public long getPacientesAtivos() {
        return pacientesAtivos;
    }

    public void setPacientesAtivos(long pacientesAtivos) {
        this.pacientesAtivos = pacientesAtivos;
    }

    public long getPacientesAlta() {
        return pacientesAlta;
    }

    public void setPacientesAlta(long pacientesAlta) {
        this.pacientesAlta = pacientesAlta;
    }

    public long getTotalSessoes() {
        return totalSessoes;
    }

    public void setTotalSessoes(long totalSessoes) {
        this.totalSessoes = totalSessoes;
    }

    public long getSessoesUltimos30Dias() {
        return sessoesUltimos30Dias;
    }

    public void setSessoesUltimos30Dias(long sessoesUltimos30Dias) {
        this.sessoesUltimos30Dias = sessoesUltimos30Dias;
    }

    public Double getGanhoMedioGraus() {
        return ganhoMedioGraus;
    }

    public void setGanhoMedioGraus(Double ganhoMedioGraus) {
        this.ganhoMedioGraus = ganhoMedioGraus;
    }

    public List<PontoMensalDTO> getSessoesPorMes() {
        return sessoesPorMes;
    }

    public void setSessoesPorMes(List<PontoMensalDTO> sessoesPorMes) {
        this.sessoesPorMes = sessoesPorMes;
    }

    public List<EvolucaoPacienteDTO> getPacientes() {
        return pacientes;
    }

    public void setPacientes(List<EvolucaoPacienteDTO> pacientes) {
        this.pacientes = pacientes;
    }

    /** Um mês da série histórica. */
    public static class PontoMensalDTO {
        private String rotulo;
        private long sessoes;

        public PontoMensalDTO() {
        }

        public PontoMensalDTO(String rotulo, long sessoes) {
            this.rotulo = rotulo;
            this.sessoes = sessoes;
        }

        public String getRotulo() {
            return rotulo;
        }

        public void setRotulo(String rotulo) {
            this.rotulo = rotulo;
        }

        public long getSessoes() {
            return sessoes;
        }

        public void setSessoes(long sessoes) {
            this.sessoes = sessoes;
        }
    }

    /** Quanto um paciente evoluiu sob os cuidados deste profissional. */
    public static class EvolucaoPacienteDTO {
        private Integer idPaciente;
        private String nome;
        private String status;
        private long sessoes;
        private Double amplitudeInicial;
        private Double amplitudeAtual;
        private Double ganho;

        public EvolucaoPacienteDTO() {
        }

        public Integer getIdPaciente() {
            return idPaciente;
        }

        public void setIdPaciente(Integer idPaciente) {
            this.idPaciente = idPaciente;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public long getSessoes() {
            return sessoes;
        }

        public void setSessoes(long sessoes) {
            this.sessoes = sessoes;
        }

        public Double getAmplitudeInicial() {
            return amplitudeInicial;
        }

        public void setAmplitudeInicial(Double amplitudeInicial) {
            this.amplitudeInicial = amplitudeInicial;
        }

        public Double getAmplitudeAtual() {
            return amplitudeAtual;
        }

        public void setAmplitudeAtual(Double amplitudeAtual) {
            this.amplitudeAtual = amplitudeAtual;
        }

        public Double getGanho() {
            return ganho;
        }

        public void setGanho(Double ganho) {
            this.ganho = ganho;
        }
    }
}
