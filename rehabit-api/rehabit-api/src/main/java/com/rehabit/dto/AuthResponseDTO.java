package com.rehabit.dto;

public class AuthResponseDTO {

    private Integer id;
    private String tipo;   // "CLINICA" ou "FISIOTERAPEUTA"
    private String nome;
    private String email;
    private String foto;
    private boolean tutorialVisto;
    /**
     * Nome da instituição a que a conta pertence (para a clínica, ela mesma).
     * Vai no login para que as telas possam se apresentar ao paciente em nome
     * da clínica sem precisar de uma chamada extra à API.
     */
    private String nomeClinica;
    private String token; // null na resposta de /auth/register (não faz login automático)

    public AuthResponseDTO() {
    }

    public AuthResponseDTO(Integer id, String tipo, String nome, String email, String foto, boolean tutorialVisto) {
        this.id = id;
        this.tipo = tipo;
        this.nome = nome;
        this.email = email;
        this.foto = foto;
        this.tutorialVisto = tutorialVisto;
    }

    public AuthResponseDTO(Integer id, String tipo, String nome, String email, String foto, boolean tutorialVisto,
                            String token) {
        this(id, tipo, nome, email, foto, tutorialVisto);
        this.token = token;
    }

    public String getNomeClinica() {
        return nomeClinica;
    }

    public void setNomeClinica(String nomeClinica) {
        this.nomeClinica = nomeClinica;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public boolean isTutorialVisto() {
        return tutorialVisto;
    }

    public void setTutorialVisto(boolean tutorialVisto) {
        this.tutorialVisto = tutorialVisto;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
