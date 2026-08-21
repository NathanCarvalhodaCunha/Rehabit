# Dados Reais Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace hardcoded/mock content across all `Software/*.html` screens with real data from MySQL, by building the missing backend (Paciente, Sessão, Medição, Goniômetro) and wiring every screen to it.

**Architecture:** New JPA entities map 1:1 onto the existing `tb03`–`tb06` tables (no schema changes). New Spring controllers expose them as `permitAll` REST endpoints, consistent with the existing `/api/auth`, `/api/fisioterapeutas`, `/api/uploads` pattern (no JWT — decided with the user). Each `Software/*.html` page gets a small dedicated `Software/pages/<screen>.js` file that fetches its data and renders it into the existing markup; `Software/script.js` gains three tiny shared HTTP helpers used by all of them.

**Tech Stack:** Spring Boot 3.3 / Java, Spring Data JPA, MySQL (MariaDB locally), vanilla JS front-end, no build step.

**Spec:** [docs/superpowers/specs/2026-08-20-dados-reais-design.md](../specs/2026-08-20-dados-reais-design.md)

## Global Constraints

- No schema changes — every new entity maps to a column that already exists in `Rehabit.sql`.
- No JWT / real auth — every new endpoint is added to `SecurityConfig`'s `permitAll()` list, same as existing endpoints.
- No automated test suite exists in this project (`rehabit-api/rehabit-api/src/test` is empty) and adding one is out of scope — verification is: `mvnw.cmd -q -o compile` (or a full app boot, which also validates every JPA mapping against the live schema via `spring.jpa.hibernate.ddl-auto=validate`) for backend tasks, and a real browser pass against the running backend for frontend tasks.
- Follow existing code style exactly: DTOs are plain classes with a no-arg constructor + all-args constructor + getters/setters (see `AuthResponseDTO.java`, `FisioterapeutaCreateDTO.java`); services throw `AuthException(message, HttpStatus)` for user-facing errors, caught by the existing `GlobalExceptionHandler`; JS uses `var`-free `const`/`async`/`await`, Portuguese identifiers, and the existing `alert()`-based error UX (see `Software/script.js`).
- Money/measurement values: `amplitudeMedia` is a `BigDecimal` end-to-end (matches `decimal(6,2)` column); dates are `java.time.LocalDate` / `LocalTime`, serialized by Spring Boot's built-in Jackson JSR-310 support as ISO-8601 strings (`"yyyy-MM-dd"`), which is exactly what `<input type="date">` produces — no manual date parsing needed anywhere.

---

## Backend

### Task 1: Data layer — Paciente, Sessão, Medição, Goniômetro entities and repositories

**Files:**
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Paciente.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Sessao.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Medicao.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Goniometro.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/PacienteRepository.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/SessaoRepository.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/MedicaoRepository.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/GoniometroRepository.java`
- Modify: `rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/FisioterapeutaRepository.java`
- Modify: `rehabit-api/rehabit-api/src/main/java/com/rehabit/config/SecurityConfig.java`

**Interfaces:**
- Produces (used by every later backend task): `Paciente`/`Sessao`/`Medicao`/`Goniometro` entities with getters/setters as listed below; `PacienteRepository.findByIdFisioterapeutaOrderByNomeAsc(Integer)`, `.existsByCpf(String)`, `.countByIdFisioterapeutaAndStatus(Integer, String)`, `.countByIdClinica(Integer)`; `SessaoRepository.findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(Integer)`, `.findByIdFisioterapeuta(Integer)`, `.findByIdFisioterapeutaIn(List<Integer>)`; `MedicaoRepository.findByIdSessao(Integer)`; `GoniometroRepository.findFirstByIdClinicaOrderByIdDesc(Integer)`; `FisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(Integer)`, `.countByIdClinica(Integer)`.

This is the foundational task — every other backend task depends on it. All the `SecurityConfig` route patterns for the whole plan are added here too, even though their controllers don't exist yet, to avoid every later task touching the same shared line.

- [ ] **Step 1: Create the four entities**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Paciente.java`:

```java
package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "tb03_paciente")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb03_id_paciente")
    private Integer id;

    @Column(name = "tb03_nome_paciente", nullable = false, length = 150)
    private String nome;

    @Column(name = "tb03_CPF", nullable = false, unique = true, length = 14)
    private String cpf;

    @Column(name = "tb03_telefone_paciente", length = 20)
    private String telefone;

    @Column(name = "tb03_email_paciente", length = 150)
    private String email;

    @Column(name = "tb03_data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "tb03_sexo", length = 20)
    private String sexo;

    @Column(name = "tb03_data_inicio_tratamento")
    private LocalDate dataInicioTratamento;

    @Column(name = "tb03_situacao", length = 50)
    private String situacao;

    @Column(name = "tb03_status", length = 50)
    private String status;

    @Column(name = "tb03_id_clinica", nullable = false)
    private Integer idClinica;

    @Column(name = "tb03_id_fisioterapeuta", nullable = false)
    private Integer idFisioterapeuta;

    public Paciente() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public String getSexo() {
        return sexo;
    }

    public void setSexo(String sexo) {
        this.sexo = sexo;
    }

    public LocalDate getDataInicioTratamento() {
        return dataInicioTratamento;
    }

    public void setDataInicioTratamento(LocalDate dataInicioTratamento) {
        this.dataInicioTratamento = dataInicioTratamento;
    }

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Sessao.java`:

```java
package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "tb05_sessoes")
public class Sessao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb05_id_sessoes")
    private Integer id;

    @Column(name = "tb05_duracao")
    private Integer duracao;

    @Column(name = "tb05_data_sessoes")
    private LocalDate dataSessao;

    @Column(name = "tb05_hora_sessoes")
    private LocalTime horaSessao;

    @Column(name = "tb05_id_fisioterapeuta", nullable = false)
    private Integer idFisioterapeuta;

    @Column(name = "tb05_id_paciente", nullable = false)
    private Integer idPaciente;

    public Sessao() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDuracao() {
        return duracao;
    }

    public void setDuracao(Integer duracao) {
        this.duracao = duracao;
    }

    public LocalDate getDataSessao() {
        return dataSessao;
    }

    public void setDataSessao(LocalDate dataSessao) {
        this.dataSessao = dataSessao;
    }

    public LocalTime getHoraSessao() {
        return horaSessao;
    }

    public void setHoraSessao(LocalTime horaSessao) {
        this.horaSessao = horaSessao;
    }

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }

    public Integer getIdPaciente() {
        return idPaciente;
    }

    public void setIdPaciente(Integer idPaciente) {
        this.idPaciente = idPaciente;
    }
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Medicao.java`:

```java
package com.rehabit.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "tb06_medicao")
public class Medicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb06_id_medicao")
    private Integer id;

    @Column(name = "tb06_amplitude_media", precision = 6, scale = 2)
    private BigDecimal amplitudeMedia;

    @Column(name = "tb06_data_medicao")
    private LocalDate dataMedicao;

    @Column(name = "tb06_hora_medicao")
    private LocalTime horaMedicao;

    @Column(name = "tb06_id_sessoes", nullable = false)
    private Integer idSessao;

    public Medicao() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public BigDecimal getAmplitudeMedia() {
        return amplitudeMedia;
    }

    public void setAmplitudeMedia(BigDecimal amplitudeMedia) {
        this.amplitudeMedia = amplitudeMedia;
    }

    public LocalDate getDataMedicao() {
        return dataMedicao;
    }

    public void setDataMedicao(LocalDate dataMedicao) {
        this.dataMedicao = dataMedicao;
    }

    public LocalTime getHoraMedicao() {
        return horaMedicao;
    }

    public void setHoraMedicao(LocalTime horaMedicao) {
        this.horaMedicao = horaMedicao;
    }

    public Integer getIdSessao() {
        return idSessao;
    }

    public void setIdSessao(Integer idSessao) {
        this.idSessao = idSessao;
    }
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Goniometro.java`:

```java
package com.rehabit.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "tb04_goniometro")
public class Goniometro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tb04_id_goniometro")
    private Integer id;

    @Column(name = "tb04_bateria")
    private Integer bateria;

    @Column(name = "tb04_data_sincronizacao")
    private LocalDate dataSincronizacao;

    @Column(name = "tb04_hora_sincronizacao")
    private LocalTime horaSincronizacao;

    @Column(name = "tb04_id_clinica", nullable = false)
    private Integer idClinica;

    public Goniometro() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getBateria() {
        return bateria;
    }

    public void setBateria(Integer bateria) {
        this.bateria = bateria;
    }

    public LocalDate getDataSincronizacao() {
        return dataSincronizacao;
    }

    public void setDataSincronizacao(LocalDate dataSincronizacao) {
        this.dataSincronizacao = dataSincronizacao;
    }

    public LocalTime getHoraSincronizacao() {
        return horaSincronizacao;
    }

    public void setHoraSincronizacao(LocalTime horaSincronizacao) {
        this.horaSincronizacao = horaSincronizacao;
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }
}
```

- [ ] **Step 2: Create the four repositories**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/PacienteRepository.java`:

```java
package com.rehabit.repository;

import com.rehabit.model.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PacienteRepository extends JpaRepository<Paciente, Integer> {

    List<Paciente> findByIdFisioterapeutaOrderByNomeAsc(Integer idFisioterapeuta);

    boolean existsByCpf(String cpf);

    long countByIdFisioterapeutaAndStatus(Integer idFisioterapeuta, String status);

    long countByIdClinica(Integer idClinica);
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/SessaoRepository.java`:

```java
package com.rehabit.repository;

import com.rehabit.model.Sessao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessaoRepository extends JpaRepository<Sessao, Integer> {

    List<Sessao> findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(Integer idPaciente);

    List<Sessao> findByIdFisioterapeuta(Integer idFisioterapeuta);

    List<Sessao> findByIdFisioterapeutaIn(List<Integer> idsFisioterapeuta);
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/MedicaoRepository.java`:

```java
package com.rehabit.repository;

import com.rehabit.model.Medicao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicaoRepository extends JpaRepository<Medicao, Integer> {

    Medicao findByIdSessao(Integer idSessao);
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/GoniometroRepository.java`:

```java
package com.rehabit.repository;

import com.rehabit.model.Goniometro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoniometroRepository extends JpaRepository<Goniometro, Integer> {

    Optional<Goniometro> findFirstByIdClinicaOrderByIdDesc(Integer idClinica);
}
```

- [ ] **Step 3: Add clinic-scoped lookups to `FisioterapeutaRepository`**

Modify `rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/FisioterapeutaRepository.java` — current content is:

```java
package com.rehabit.repository;

import com.rehabit.model.Fisioterapeuta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FisioterapeutaRepository extends JpaRepository<Fisioterapeuta, Integer> {

    Optional<Fisioterapeuta> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCoffito(String coffito);
}
```

Replace it with:

```java
package com.rehabit.repository;

import com.rehabit.model.Fisioterapeuta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FisioterapeutaRepository extends JpaRepository<Fisioterapeuta, Integer> {

    Optional<Fisioterapeuta> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCoffito(String coffito);

    List<Fisioterapeuta> findByIdClinicaOrderByNomeAsc(Integer idClinica);

    long countByIdClinica(Integer idClinica);
}
```

- [ ] **Step 4: Open the new routes in `SecurityConfig`**

Modify `rehabit-api/rehabit-api/src/main/java/com/rehabit/config/SecurityConfig.java` — find this line:

```java
                .requestMatchers("/api/auth/**", "/api/fisioterapeutas/**", "/api/uploads/**", "/uploads/**").permitAll()
```

Replace it with:

```java
                .requestMatchers("/api/auth/**", "/api/fisioterapeutas/**", "/api/uploads/**", "/uploads/**",
                        "/api/clinicas/**", "/api/pacientes/**", "/api/goniometro/**").permitAll()
```

- [ ] **Step 5: Verify it compiles and the schema mapping is valid**

Run (from `rehabit-api/rehabit-api`):

```bash
./mvnw.cmd -q -o compile
```

Expected: no output, exit code 0 (matches the pattern already used successfully earlier in this project for `Fisioterapeuta`/`Clinica`).

Then boot the app once to let Hibernate validate every `@Column` against the real `rehabit` MySQL schema (`spring.jpa.hibernate.ddl-auto=validate` — this fails loudly and immediately if any table/column name above is wrong):

```bash
./mvnw.cmd -q package -DskipTests
java -jar target/rehabit-api-1.0.0.jar
```

Expected: log ends with `Started RehabitApiApplication in N seconds` and no `SchemaManagementException` / `Missing column` errors. Stop it with Ctrl+C (or `Stop-Process` on the `java` PID) once confirmed — later tasks restart it themselves.

- [ ] **Step 6: Commit**

```bash
git add rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Paciente.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Sessao.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Medicao.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/model/Goniometro.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/PacienteRepository.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/SessaoRepository.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/MedicaoRepository.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/GoniometroRepository.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/repository/FisioterapeutaRepository.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/config/SecurityConfig.java
git commit -m "feat(backend): add Paciente/Sessao/Medicao/Goniometro data layer"
```

(This repo has no `.git` yet at time of writing — if `git commit` fails with "not a git repository", skip this step; the working-tree changes are enough for later tasks to build on.)

---

### Task 2: Clínica profile — `GET`/`PUT /api/clinicas/{id}`

**Files:**
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/ClinicaPerfilDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/ClinicaUpdateDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/service/ClinicaService.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/ClinicaController.java`

**Interfaces:**
- Consumes: `ClinicaRepository` (existing: `findById`, `existsByEmail`, `existsByCnpj`), `FisioterapeutaRepository.findByIdClinicaOrderByNomeAsc`/`.countByIdClinica`, `PacienteRepository.countByIdClinica`, `SessaoRepository.findByIdFisioterapeutaIn`, `MedicaoRepository.findByIdSessao` (all from Task 1), `PasswordEncoder` (existing bean).
- Produces: `GET /api/clinicas/{id}` → `ClinicaPerfilDTO` (`id, nome, cnpj, email, telefone, endereco, subtitulo, descricao, foto, profissionaisAtivos, pacientesTotais, sessoesEsteMes, amplitudeMediaGeral`); `PUT /api/clinicas/{id}` accepting `ClinicaUpdateDTO`, returns the same `ClinicaPerfilDTO`.

- [ ] **Step 1: Create the DTOs**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/ClinicaPerfilDTO.java`:

```java
package com.rehabit.dto;

public class ClinicaPerfilDTO {

    private Integer id;
    private String nome;
    private String cnpj;
    private String email;
    private String telefone;
    private String endereco;
    private String subtitulo;
    private String descricao;
    private String foto;
    private long profissionaisAtivos;
    private long pacientesTotais;
    private long sessoesEsteMes;
    private Double amplitudeMediaGeral;

    public ClinicaPerfilDTO() {
    }

    public ClinicaPerfilDTO(Integer id, String nome, String cnpj, String email, String telefone,
                             String endereco, String subtitulo, String descricao, String foto,
                             long profissionaisAtivos, long pacientesTotais, long sessoesEsteMes,
                             Double amplitudeMediaGeral) {
        this.id = id;
        this.nome = nome;
        this.cnpj = cnpj;
        this.email = email;
        this.telefone = telefone;
        this.endereco = endereco;
        this.subtitulo = subtitulo;
        this.descricao = descricao;
        this.foto = foto;
        this.profissionaisAtivos = profissionaisAtivos;
        this.pacientesTotais = pacientesTotais;
        this.sessoesEsteMes = sessoesEsteMes;
        this.amplitudeMediaGeral = amplitudeMediaGeral;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getSubtitulo() {
        return subtitulo;
    }

    public void setSubtitulo(String subtitulo) {
        this.subtitulo = subtitulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public long getProfissionaisAtivos() {
        return profissionaisAtivos;
    }

    public void setProfissionaisAtivos(long profissionaisAtivos) {
        this.profissionaisAtivos = profissionaisAtivos;
    }

    public long getPacientesTotais() {
        return pacientesTotais;
    }

    public void setPacientesTotais(long pacientesTotais) {
        this.pacientesTotais = pacientesTotais;
    }

    public long getSessoesEsteMes() {
        return sessoesEsteMes;
    }

    public void setSessoesEsteMes(long sessoesEsteMes) {
        this.sessoesEsteMes = sessoesEsteMes;
    }

    public Double getAmplitudeMediaGeral() {
        return amplitudeMediaGeral;
    }

    public void setAmplitudeMediaGeral(Double amplitudeMediaGeral) {
        this.amplitudeMediaGeral = amplitudeMediaGeral;
    }
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/ClinicaUpdateDTO.java`:

```java
package com.rehabit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClinicaUpdateDTO {

    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 150)
    private String nome;

    @NotBlank(message = "O CNPJ é obrigatório.")
    private String cnpj;

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Informe um e-mail válido.")
    private String email;

    private String telefone;
    private String endereco;
    private String subtitulo;
    private String descricao;
    private String foto;
    private String senhaAtual;
    private String novaSenha;

    public ClinicaUpdateDTO() {
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getSubtitulo() {
        return subtitulo;
    }

    public void setSubtitulo(String subtitulo) {
        this.subtitulo = subtitulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public String getSenhaAtual() {
        return senhaAtual;
    }

    public void setSenhaAtual(String senhaAtual) {
        this.senhaAtual = senhaAtual;
    }

    public String getNovaSenha() {
        return novaSenha;
    }

    public void setNovaSenha(String novaSenha) {
        this.novaSenha = novaSenha;
    }
}
```

- [ ] **Step 2: Create `ClinicaService`**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/service/ClinicaService.java`:

```java
package com.rehabit.service;

import com.rehabit.dto.ClinicaPerfilDTO;
import com.rehabit.dto.ClinicaUpdateDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Clinica;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Sessao;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ClinicaService {

    private final ClinicaRepository clinicaRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final PacienteRepository pacienteRepository;
    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;
    private final PasswordEncoder passwordEncoder;

    public ClinicaService(ClinicaRepository clinicaRepository,
                           FisioterapeutaRepository fisioterapeutaRepository,
                           PacienteRepository pacienteRepository,
                           SessaoRepository sessaoRepository,
                           MedicaoRepository medicaoRepository,
                           PasswordEncoder passwordEncoder) {
        this.clinicaRepository = clinicaRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.pacienteRepository = pacienteRepository;
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public ClinicaPerfilDTO buscarPerfil(Integer id) {
        Clinica clinica = clinicaRepository.findById(id)
                .orElseThrow(() -> new AuthException("Instituição não encontrada.", HttpStatus.NOT_FOUND));
        return paraDTO(clinica);
    }

    @Transactional
    public ClinicaPerfilDTO atualizar(Integer id, ClinicaUpdateDTO dados) {
        Clinica clinica = clinicaRepository.findById(id)
                .orElseThrow(() -> new AuthException("Instituição não encontrada.", HttpStatus.NOT_FOUND));

        if (!clinica.getEmail().equalsIgnoreCase(dados.getEmail())
                && clinicaRepository.existsByEmail(dados.getEmail())) {
            throw new AuthException("Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
        }
        if (!clinica.getCnpj().equals(dados.getCnpj())
                && clinicaRepository.existsByCnpj(dados.getCnpj())) {
            throw new AuthException("Este CNPJ já está cadastrado.", HttpStatus.CONFLICT);
        }

        if (dados.getNovaSenha() != null && !dados.getNovaSenha().isBlank()) {
            if (dados.getSenhaAtual() == null || !passwordEncoder.matches(dados.getSenhaAtual(), clinica.getSenha())) {
                throw new AuthException("Senha atual incorreta.", HttpStatus.BAD_REQUEST);
            }
            if (dados.getNovaSenha().length() < 6) {
                throw new AuthException("A nova senha deve ter ao menos 6 caracteres.", HttpStatus.BAD_REQUEST);
            }
            clinica.setSenha(passwordEncoder.encode(dados.getNovaSenha()));
        }

        clinica.setNome(dados.getNome());
        clinica.setCnpj(dados.getCnpj());
        clinica.setEmail(dados.getEmail());
        clinica.setTelefone(vazioParaNulo(dados.getTelefone()));
        clinica.setEndereco(vazioParaNulo(dados.getEndereco()));
        clinica.setSubtitulo(vazioParaNulo(dados.getSubtitulo()));
        clinica.setDescricao(vazioParaNulo(dados.getDescricao()));
        if (dados.getFoto() != null) {
            clinica.setFoto(dados.getFoto());
        }

        return paraDTO(clinicaRepository.save(clinica));
    }

    private ClinicaPerfilDTO paraDTO(Clinica clinica) {
        List<Fisioterapeuta> fisioterapeutas = fisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(clinica.getId());
        List<Integer> idsFisioterapeutas = fisioterapeutas.stream().map(Fisioterapeuta::getId).collect(Collectors.toList());
        List<Sessao> sessoes = idsFisioterapeutas.isEmpty()
                ? List.of()
                : sessaoRepository.findByIdFisioterapeutaIn(idsFisioterapeutas);

        long sessoesEsteMes = sessoes.stream()
                .filter(s -> s.getDataSessao() != null && YearMonth.from(s.getDataSessao()).equals(YearMonth.now()))
                .count();

        List<BigDecimal> amplitudes = sessoes.stream()
                .map(s -> medicaoRepository.findByIdSessao(s.getId()))
                .filter(Objects::nonNull)
                .map(Medicao::getAmplitudeMedia)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Double amplitudeMediaGeral = amplitudes.isEmpty()
                ? null
                : amplitudes.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);

        return new ClinicaPerfilDTO(clinica.getId(), clinica.getNome(), clinica.getCnpj(), clinica.getEmail(),
                clinica.getTelefone(), clinica.getEndereco(), clinica.getSubtitulo(), clinica.getDescricao(),
                clinica.getFoto(), fisioterapeutas.size(), pacienteRepository.countByIdClinica(clinica.getId()),
                sessoesEsteMes, amplitudeMediaGeral);
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
```

- [ ] **Step 3: Create `ClinicaController`**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/ClinicaController.java`:

```java
package com.rehabit.controller;

import com.rehabit.dto.ClinicaPerfilDTO;
import com.rehabit.dto.ClinicaUpdateDTO;
import com.rehabit.service.ClinicaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clinicas")
@CrossOrigin(origins = "*")
public class ClinicaController {

    private final ClinicaService clinicaService;

    public ClinicaController(ClinicaService clinicaService) {
        this.clinicaService = clinicaService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClinicaPerfilDTO> buscar(@PathVariable Integer id) {
        return ResponseEntity.ok(clinicaService.buscarPerfil(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClinicaPerfilDTO> atualizar(@PathVariable Integer id,
                                                        @Valid @RequestBody ClinicaUpdateDTO dados) {
        return ResponseEntity.ok(clinicaService.atualizar(id, dados));
    }
}
```

- [ ] **Step 4: Verify against the running backend**

Build and run (from `rehabit-api/rehabit-api`; stop any previous instance on port 8080 first):

```bash
./mvnw.cmd -q package -DskipTests
java -jar target/rehabit-api-1.0.0.jar
```

In another shell, using the seeded clinic (`id=1`, `nathan@gmail.com` in the sample data — substitute whatever real clinic id exists in your DB):

```bash
curl -s http://localhost:8080/api/clinicas/1
```

Expected: `200` with a JSON body containing `nome`, `cnpj`, `email`, and the four stat fields (all `0`/`null` if there are no fisioterapeutas/sessions yet — that's correct, not a bug).

```bash
curl -s -X PUT http://localhost:8080/api/clinicas/1 \
  -H "Content-Type: application/json" \
  -d '{"nome":"Comando Vermelho","cnpj":"66666","email":"nathan@gmail.com","telefone":"11999999999"}'
```

Expected: `200` with the updated `telefone` reflected in the response. Re-run the `GET` to confirm it persisted.

- [ ] **Step 5: Commit**

```bash
git add rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/ClinicaPerfilDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/ClinicaUpdateDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/service/ClinicaService.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/ClinicaController.java
git commit -m "feat(backend): add clinic profile GET/PUT endpoints"
```

---

### Task 3: Fisioterapeuta profile, listing and update

**Files:**
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/FisioterapeutaPerfilDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/FisioterapeutaUpdateDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/FisioterapeutaResumoDTO.java`
- Modify: `rehabit-api/rehabit-api/src/main/java/com/rehabit/service/FisioterapeutaService.java`
- Modify: `rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/FisioterapeutaController.java`

**Interfaces:**
- Consumes: `FisioterapeutaRepository.findByIdClinicaOrderByNomeAsc`, `PacienteRepository.countByIdFisioterapeutaAndStatus`, `SessaoRepository.findByIdFisioterapeuta`, `MedicaoRepository.findByIdSessao` (all Task 1).
- Produces: `GET /api/fisioterapeutas?idClinica=` → `List<FisioterapeutaResumoDTO>` (`id, nome, especialidade, foto, pacientesAtivos`); `GET /api/fisioterapeutas/{id}` → `FisioterapeutaPerfilDTO` (`id, nome, coffito, email, telefone, especialidade, localidade, descricao, foto, idClinica, pacientesAtivos, sessoesEsteMes, amplitudeMediaGeral`); `PUT /api/fisioterapeutas/{id}` accepting `FisioterapeutaUpdateDTO`, returns the same `FisioterapeutaPerfilDTO`.

- [ ] **Step 1: Create the three DTOs**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/FisioterapeutaResumoDTO.java`:

```java
package com.rehabit.dto;

public class FisioterapeutaResumoDTO {

    private Integer id;
    private String nome;
    private String especialidade;
    private String foto;
    private long pacientesAtivos;

    public FisioterapeutaResumoDTO() {
    }

    public FisioterapeutaResumoDTO(Integer id, String nome, String especialidade, String foto, long pacientesAtivos) {
        this.id = id;
        this.nome = nome;
        this.especialidade = especialidade;
        this.foto = foto;
        this.pacientesAtivos = pacientesAtivos;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
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

    public long getPacientesAtivos() {
        return pacientesAtivos;
    }

    public void setPacientesAtivos(long pacientesAtivos) {
        this.pacientesAtivos = pacientesAtivos;
    }
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/FisioterapeutaPerfilDTO.java`:

```java
package com.rehabit.dto;

public class FisioterapeutaPerfilDTO {

    private Integer id;
    private String nome;
    private String coffito;
    private String email;
    private String telefone;
    private String especialidade;
    private String localidade;
    private String descricao;
    private String foto;
    private Integer idClinica;
    private long pacientesAtivos;
    private long sessoesEsteMes;
    private Double amplitudeMediaGeral;

    public FisioterapeutaPerfilDTO() {
    }

    public FisioterapeutaPerfilDTO(Integer id, String nome, String coffito, String email, String telefone,
                                    String especialidade, String localidade, String descricao, String foto,
                                    Integer idClinica, long pacientesAtivos, long sessoesEsteMes,
                                    Double amplitudeMediaGeral) {
        this.id = id;
        this.nome = nome;
        this.coffito = coffito;
        this.email = email;
        this.telefone = telefone;
        this.especialidade = especialidade;
        this.localidade = localidade;
        this.descricao = descricao;
        this.foto = foto;
        this.idClinica = idClinica;
        this.pacientesAtivos = pacientesAtivos;
        this.sessoesEsteMes = sessoesEsteMes;
        this.amplitudeMediaGeral = amplitudeMediaGeral;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCoffito() {
        return coffito;
    }

    public void setCoffito(String coffito) {
        this.coffito = coffito;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
    }

    public String getLocalidade() {
        return localidade;
    }

    public void setLocalidade(String localidade) {
        this.localidade = localidade;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }

    public long getPacientesAtivos() {
        return pacientesAtivos;
    }

    public void setPacientesAtivos(long pacientesAtivos) {
        this.pacientesAtivos = pacientesAtivos;
    }

    public long getSessoesEsteMes() {
        return sessoesEsteMes;
    }

    public void setSessoesEsteMes(long sessoesEsteMes) {
        this.sessoesEsteMes = sessoesEsteMes;
    }

    public Double getAmplitudeMediaGeral() {
        return amplitudeMediaGeral;
    }

    public void setAmplitudeMediaGeral(Double amplitudeMediaGeral) {
        this.amplitudeMediaGeral = amplitudeMediaGeral;
    }
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/FisioterapeutaUpdateDTO.java`:

```java
package com.rehabit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FisioterapeutaUpdateDTO {

    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 150)
    private String nome;

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Informe um e-mail válido.")
    private String email;

    private String telefone;
    private String especialidade;
    private String localidade;
    private String descricao;
    private String foto;
    private String senhaAtual;
    private String novaSenha;

    public FisioterapeutaUpdateDTO() {
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

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
    }

    public String getLocalidade() {
        return localidade;
    }

    public void setLocalidade(String localidade) {
        this.localidade = localidade;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public String getSenhaAtual() {
        return senhaAtual;
    }

    public void setSenhaAtual(String senhaAtual) {
        this.senhaAtual = senhaAtual;
    }

    public String getNovaSenha() {
        return novaSenha;
    }

    public void setNovaSenha(String novaSenha) {
        this.novaSenha = novaSenha;
    }
}
```

- [ ] **Step 2: Add perfil/listar/atualizar to `FisioterapeutaService`**

Current file `rehabit-api/rehabit-api/src/main/java/com/rehabit/service/FisioterapeutaService.java` starts with:

```java
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
```

Replace that whole block (imports through the constructor) with:

```java
package com.rehabit.service;

import com.rehabit.dto.AuthResponseDTO;
import com.rehabit.dto.FisioterapeutaCreateDTO;
import com.rehabit.dto.FisioterapeutaPerfilDTO;
import com.rehabit.dto.FisioterapeutaResumoDTO;
import com.rehabit.dto.FisioterapeutaUpdateDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Sessao;
import com.rehabit.repository.ClinicaRepository;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FisioterapeutaService {

    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final ClinicaRepository clinicaRepository;
    private final PacienteRepository pacienteRepository;
    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;
    private final PasswordEncoder passwordEncoder;

    public FisioterapeutaService(FisioterapeutaRepository fisioterapeutaRepository,
                                  ClinicaRepository clinicaRepository,
                                  PacienteRepository pacienteRepository,
                                  SessaoRepository sessaoRepository,
                                  MedicaoRepository medicaoRepository,
                                  PasswordEncoder passwordEncoder) {
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.clinicaRepository = clinicaRepository;
        this.pacienteRepository = pacienteRepository;
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
        this.passwordEncoder = passwordEncoder;
    }
```

Then, right after the closing brace of the existing `private String vazioParaNulo(String valor) { ... }` method (the last method in the file), add these new methods before the final closing `}` of the class:

```java

    public List<FisioterapeutaResumoDTO> listarPorClinica(Integer idClinica) {
        return fisioterapeutaRepository.findByIdClinicaOrderByNomeAsc(idClinica).stream()
                .map(f -> new FisioterapeutaResumoDTO(f.getId(), f.getNome(), f.getEspecialidade(), f.getFoto(),
                        pacienteRepository.countByIdFisioterapeutaAndStatus(f.getId(), "Ativo")))
                .collect(Collectors.toList());
    }

    public FisioterapeutaPerfilDTO buscarPerfil(Integer id) {
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(id)
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.NOT_FOUND));
        return paraPerfilDTO(fisioterapeuta);
    }

    @Transactional
    public FisioterapeutaPerfilDTO atualizar(Integer id, FisioterapeutaUpdateDTO dados) {
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(id)
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.NOT_FOUND));

        if (!fisioterapeuta.getEmail().equalsIgnoreCase(dados.getEmail())
                && (fisioterapeutaRepository.existsByEmail(dados.getEmail())
                        || clinicaRepository.existsByEmail(dados.getEmail()))) {
            throw new AuthException("Este e-mail já está cadastrado.", HttpStatus.CONFLICT);
        }

        if (dados.getNovaSenha() != null && !dados.getNovaSenha().isBlank()) {
            if (dados.getSenhaAtual() == null
                    || !passwordEncoder.matches(dados.getSenhaAtual(), fisioterapeuta.getSenha())) {
                throw new AuthException("Senha atual incorreta.", HttpStatus.BAD_REQUEST);
            }
            if (dados.getNovaSenha().length() < 6) {
                throw new AuthException("A nova senha deve ter ao menos 6 caracteres.", HttpStatus.BAD_REQUEST);
            }
            fisioterapeuta.setSenha(passwordEncoder.encode(dados.getNovaSenha()));
        }

        fisioterapeuta.setNome(dados.getNome());
        fisioterapeuta.setEmail(dados.getEmail());
        fisioterapeuta.setTelefone(vazioParaNulo(dados.getTelefone()));
        fisioterapeuta.setEspecialidade(vazioParaNulo(dados.getEspecialidade()));
        fisioterapeuta.setLocalidade(vazioParaNulo(dados.getLocalidade()));
        fisioterapeuta.setDescricao(vazioParaNulo(dados.getDescricao()));
        if (dados.getFoto() != null) {
            fisioterapeuta.setFoto(dados.getFoto());
        }

        return paraPerfilDTO(fisioterapeutaRepository.save(fisioterapeuta));
    }

    private FisioterapeutaPerfilDTO paraPerfilDTO(Fisioterapeuta f) {
        List<Sessao> sessoes = sessaoRepository.findByIdFisioterapeuta(f.getId());

        long sessoesEsteMes = sessoes.stream()
                .filter(s -> s.getDataSessao() != null && YearMonth.from(s.getDataSessao()).equals(YearMonth.now()))
                .count();

        List<BigDecimal> amplitudes = sessoes.stream()
                .map(s -> medicaoRepository.findByIdSessao(s.getId()))
                .filter(Objects::nonNull)
                .map(Medicao::getAmplitudeMedia)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Double amplitudeMediaGeral = amplitudes.isEmpty()
                ? null
                : amplitudes.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);

        long pacientesAtivos = pacienteRepository.countByIdFisioterapeutaAndStatus(f.getId(), "Ativo");

        return new FisioterapeutaPerfilDTO(f.getId(), f.getNome(), f.getCoffito(), f.getEmail(), f.getTelefone(),
                f.getEspecialidade(), f.getLocalidade(), f.getDescricao(), f.getFoto(), f.getIdClinica(),
                pacientesAtivos, sessoesEsteMes, amplitudeMediaGeral);
    }
```

- [ ] **Step 3: Add the three routes to `FisioterapeutaController`**

Current file:

```java
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
```

Replace it with:

```java
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
}
```

- [ ] **Step 4: Verify against the running backend**

```bash
./mvnw.cmd -q package -DskipTests
java -jar target/rehabit-api-1.0.0.jar
```

```bash
curl -s "http://localhost:8080/api/fisioterapeutas?idClinica=1"
```

Expected: `200` with a JSON array (empty `[]` if that clinic has no fisioterapeutas yet — register one through `cadastrar-profissional.html` first if you want a non-empty response, or via `curl -X POST http://localhost:8080/api/fisioterapeutas ...` matching the existing DTO fields).

```bash
curl -s http://localhost:8080/api/fisioterapeutas/<id-de-um-fisioterapeuta-real>
```

Expected: `200` with `pacientesAtivos`, `sessoesEsteMes`, `amplitudeMediaGeral` present (zero/null is fine with no data yet).

- [ ] **Step 5: Commit**

```bash
git add rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/FisioterapeutaPerfilDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/FisioterapeutaUpdateDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/FisioterapeutaResumoDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/service/FisioterapeutaService.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/FisioterapeutaController.java
git commit -m "feat(backend): add fisioterapeuta profile, listing and update endpoints"
```

---

### Task 4: Paciente CRUD — `POST/GET /api/pacientes`, `GET /api/pacientes/{id}`

**Files:**
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/PacienteCreateDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/PacienteResumoDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/PacienteDetalheDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/service/PacienteService.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/PacienteController.java`

**Interfaces:**
- Consumes: `PacienteRepository`, `FisioterapeutaRepository`, `SessaoRepository.findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc`, `MedicaoRepository.findByIdSessao` (Task 1).
- Produces: `POST /api/pacientes` (body `PacienteCreateDTO`) → `201` `PacienteDetalheDTO`; `GET /api/pacientes?idFisioterapeuta=` → `List<PacienteResumoDTO>` (`id, nome, situacao, ultimaSessao, selo` — `selo` is `"Evoluindo" | "Estavel" | "Instavel" | null`); `GET /api/pacientes/{id}` → `PacienteDetalheDTO` (`id, nome, cpf, telefone, email, dataNascimento, idade, sexo, dataInicioTratamento, situacao, status, idFisioterapeuta, nomeFisioterapeuta`). `PacienteController` also owns the `/api/pacientes/{id}/sessoes` routes, implemented in Task 5, so this task creates the controller class that Task 5 extends — do not treat the controller as "done" until Task 5 lands too.

- [ ] **Step 1: Create the DTOs**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/PacienteCreateDTO.java`:

```java
package com.rehabit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class PacienteCreateDTO {

    @NotBlank(message = "O nome é obrigatório.")
    private String nome;

    @NotBlank(message = "O CPF é obrigatório.")
    private String cpf;

    private String telefone;
    private String email;
    private LocalDate dataNascimento;
    private String sexo;
    private LocalDate dataInicioTratamento;
    private String situacao;

    @NotNull(message = "O profissional responsável é obrigatório.")
    private Integer idFisioterapeuta;

    public PacienteCreateDTO() {
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public String getSexo() {
        return sexo;
    }

    public void setSexo(String sexo) {
        this.sexo = sexo;
    }

    public LocalDate getDataInicioTratamento() {
        return dataInicioTratamento;
    }

    public void setDataInicioTratamento(LocalDate dataInicioTratamento) {
        this.dataInicioTratamento = dataInicioTratamento;
    }

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/PacienteResumoDTO.java`:

```java
package com.rehabit.dto;

public class PacienteResumoDTO {

    private Integer id;
    private String nome;
    private String situacao;
    private String ultimaSessao;
    private String selo;

    public PacienteResumoDTO() {
    }

    public PacienteResumoDTO(Integer id, String nome, String situacao, String ultimaSessao, String selo) {
        this.id = id;
        this.nome = nome;
        this.situacao = situacao;
        this.ultimaSessao = ultimaSessao;
        this.selo = selo;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public String getUltimaSessao() {
        return ultimaSessao;
    }

    public void setUltimaSessao(String ultimaSessao) {
        this.ultimaSessao = ultimaSessao;
    }

    public String getSelo() {
        return selo;
    }

    public void setSelo(String selo) {
        this.selo = selo;
    }
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/PacienteDetalheDTO.java`:

```java
package com.rehabit.dto;

import java.time.LocalDate;

public class PacienteDetalheDTO {

    private Integer id;
    private String nome;
    private String cpf;
    private String telefone;
    private String email;
    private LocalDate dataNascimento;
    private Integer idade;
    private String sexo;
    private LocalDate dataInicioTratamento;
    private String situacao;
    private String status;
    private Integer idFisioterapeuta;
    private String nomeFisioterapeuta;

    public PacienteDetalheDTO() {
    }

    public PacienteDetalheDTO(Integer id, String nome, String cpf, String telefone, String email,
                               LocalDate dataNascimento, Integer idade, String sexo,
                               LocalDate dataInicioTratamento, String situacao, String status,
                               Integer idFisioterapeuta, String nomeFisioterapeuta) {
        this.id = id;
        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
        this.email = email;
        this.dataNascimento = dataNascimento;
        this.idade = idade;
        this.sexo = sexo;
        this.dataInicioTratamento = dataInicioTratamento;
        this.situacao = situacao;
        this.status = status;
        this.idFisioterapeuta = idFisioterapeuta;
        this.nomeFisioterapeuta = nomeFisioterapeuta;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public Integer getIdade() {
        return idade;
    }

    public void setIdade(Integer idade) {
        this.idade = idade;
    }

    public String getSexo() {
        return sexo;
    }

    public void setSexo(String sexo) {
        this.sexo = sexo;
    }

    public LocalDate getDataInicioTratamento() {
        return dataInicioTratamento;
    }

    public void setDataInicioTratamento(LocalDate dataInicioTratamento) {
        this.dataInicioTratamento = dataInicioTratamento;
    }

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
```

- [ ] **Step 2: Create `PacienteService`**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/service/PacienteService.java`:

```java
package com.rehabit.service;

import com.rehabit.dto.PacienteCreateDTO;
import com.rehabit.dto.PacienteDetalheDTO;
import com.rehabit.dto.PacienteResumoDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Fisioterapeuta;
import com.rehabit.model.Medicao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.FisioterapeutaRepository;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PacienteService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PacienteRepository pacienteRepository;
    private final FisioterapeutaRepository fisioterapeutaRepository;
    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;

    public PacienteService(PacienteRepository pacienteRepository,
                            FisioterapeutaRepository fisioterapeutaRepository,
                            SessaoRepository sessaoRepository,
                            MedicaoRepository medicaoRepository) {
        this.pacienteRepository = pacienteRepository;
        this.fisioterapeutaRepository = fisioterapeutaRepository;
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
    }

    @Transactional
    public PacienteDetalheDTO cadastrar(PacienteCreateDTO dados) {
        Fisioterapeuta fisioterapeuta = fisioterapeutaRepository.findById(dados.getIdFisioterapeuta())
                .orElseThrow(() -> new AuthException("Profissional não encontrado.", HttpStatus.BAD_REQUEST));

        if (pacienteRepository.existsByCpf(dados.getCpf())) {
            throw new AuthException("Este CPF já está cadastrado.", HttpStatus.CONFLICT);
        }

        Paciente paciente = new Paciente();
        paciente.setNome(dados.getNome());
        paciente.setCpf(dados.getCpf());
        paciente.setTelefone(vazioParaNulo(dados.getTelefone()));
        paciente.setEmail(vazioParaNulo(dados.getEmail()));
        paciente.setDataNascimento(dados.getDataNascimento());
        paciente.setSexo(vazioParaNulo(dados.getSexo()));
        paciente.setDataInicioTratamento(
                dados.getDataInicioTratamento() != null ? dados.getDataInicioTratamento() : LocalDate.now());
        paciente.setSituacao(vazioParaNulo(dados.getSituacao()));
        paciente.setStatus("Ativo");
        paciente.setIdClinica(fisioterapeuta.getIdClinica());
        paciente.setIdFisioterapeuta(fisioterapeuta.getId());

        Paciente salvo = pacienteRepository.save(paciente);
        return paraDetalheDTO(salvo, fisioterapeuta.getNome());
    }

    public List<PacienteResumoDTO> listarPorFisioterapeuta(Integer idFisioterapeuta) {
        return pacienteRepository.findByIdFisioterapeutaOrderByNomeAsc(idFisioterapeuta).stream()
                .map(this::paraResumoDTO)
                .collect(Collectors.toList());
    }

    public PacienteDetalheDTO buscar(Integer id) {
        Paciente paciente = pacienteRepository.findById(id)
                .orElseThrow(() -> new AuthException("Paciente não encontrado.", HttpStatus.NOT_FOUND));
        String nomeFisioterapeuta = fisioterapeutaRepository.findById(paciente.getIdFisioterapeuta())
                .map(Fisioterapeuta::getNome)
                .orElse(null);
        return paraDetalheDTO(paciente, nomeFisioterapeuta);
    }

    private PacienteResumoDTO paraResumoDTO(Paciente paciente) {
        List<Sessao> sessoes = sessaoRepository.findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(paciente.getId());
        String ultimaSessao = sessoes.isEmpty() ? null : sessoes.get(0).getDataSessao().format(FORMATO_DATA);
        return new PacienteResumoDTO(paciente.getId(), paciente.getNome(), paciente.getSituacao(),
                ultimaSessao, calcularSelo(sessoes));
    }

    private String calcularSelo(List<Sessao> sessoesRecentesPrimeiro) {
        if (sessoesRecentesPrimeiro.isEmpty()) {
            return null;
        }
        if (sessoesRecentesPrimeiro.size() < 2) {
            return "Estavel";
        }
        Medicao ultima = medicaoRepository.findByIdSessao(sessoesRecentesPrimeiro.get(0).getId());
        Medicao anterior = medicaoRepository.findByIdSessao(sessoesRecentesPrimeiro.get(1).getId());
        if (ultima == null || anterior == null
                || ultima.getAmplitudeMedia() == null || anterior.getAmplitudeMedia() == null) {
            return "Estavel";
        }
        int comparacao = ultima.getAmplitudeMedia().compareTo(anterior.getAmplitudeMedia());
        if (comparacao > 0) {
            return "Evoluindo";
        }
        if (comparacao < 0) {
            return "Instavel";
        }
        return "Estavel";
    }

    private PacienteDetalheDTO paraDetalheDTO(Paciente p, String nomeFisioterapeuta) {
        Integer idade = p.getDataNascimento() != null
                ? Period.between(p.getDataNascimento(), LocalDate.now()).getYears()
                : null;
        return new PacienteDetalheDTO(p.getId(), p.getNome(), p.getCpf(), p.getTelefone(), p.getEmail(),
                p.getDataNascimento(), idade, p.getSexo(), p.getDataInicioTratamento(), p.getSituacao(),
                p.getStatus(), p.getIdFisioterapeuta(), nomeFisioterapeuta);
    }

    private String vazioParaNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
```

- [ ] **Step 3: Create `PacienteController`**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/PacienteController.java`:

```java
package com.rehabit.controller;

import com.rehabit.dto.PacienteCreateDTO;
import com.rehabit.dto.PacienteDetalheDTO;
import com.rehabit.dto.PacienteResumoDTO;
import com.rehabit.service.PacienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
@CrossOrigin(origins = "*")
public class PacienteController {

    private final PacienteService pacienteService;

    public PacienteController(PacienteService pacienteService) {
        this.pacienteService = pacienteService;
    }

    @PostMapping
    public ResponseEntity<PacienteDetalheDTO> cadastrar(@Valid @RequestBody PacienteCreateDTO dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pacienteService.cadastrar(dados));
    }

    @GetMapping
    public ResponseEntity<List<PacienteResumoDTO>> listar(@RequestParam Integer idFisioterapeuta) {
        return ResponseEntity.ok(pacienteService.listarPorFisioterapeuta(idFisioterapeuta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PacienteDetalheDTO> buscar(@PathVariable Integer id) {
        return ResponseEntity.ok(pacienteService.buscar(id));
    }
}
```

(Task 5 adds two more `@PostMapping("/{id}/sessoes")`/`@GetMapping("/{id}/sessoes")` methods to this same class — don't consider `PacienteController` finished until that task lands.)

- [ ] **Step 4: Verify against the running backend**

```bash
./mvnw.cmd -q package -DskipTests
java -jar target/rehabit-api-1.0.0.jar
```

Using a real `idFisioterapeuta` from your DB:

```bash
curl -s -X POST http://localhost:8080/api/pacientes \
  -H "Content-Type: application/json" \
  -d '{"nome":"Paciente Teste","cpf":"11122233344","dataNascimento":"1990-05-20","sexo":"Feminino","situacao":"Lesão de teste","idFisioterapeuta":1}'
```

Expected: `201` with `idade` computed (`~35`, given today's date) and `nomeFisioterapeuta` filled in.

```bash
curl -s "http://localhost:8080/api/pacientes?idFisioterapeuta=1"
```

Expected: `200` with an array containing the patient just created, `ultimaSessao` and `selo` both `null` (no sessions yet — correct).

```bash
curl -s -X POST http://localhost:8080/api/pacientes \
  -H "Content-Type: application/json" \
  -d '{"nome":"Duplicado","cpf":"11122233344","idFisioterapeuta":1}'
```

Expected: `409` "Este CPF já está cadastrado." — delete the test patient row from MySQL afterward (`DELETE FROM tb03_paciente WHERE tb03_CPF='11122233344'`).

- [ ] **Step 5: Commit**

```bash
git add rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/PacienteCreateDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/PacienteResumoDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/PacienteDetalheDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/service/PacienteService.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/PacienteController.java
git commit -m "feat(backend): add paciente create/list/detail endpoints"
```

---

### Task 5: Sessão + Medição — `POST/GET /api/pacientes/{id}/sessoes`

**Files:**
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/SessaoCreateDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/SessaoDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/service/SessaoService.java`
- Modify: `rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/PacienteController.java` (from Task 4)

**Interfaces:**
- Consumes: `PacienteRepository.findById`, `SessaoRepository`, `MedicaoRepository` (Task 1); `PacienteController` class body (Task 4).
- Produces: `POST /api/pacientes/{id}/sessoes` (body `SessaoCreateDTO`: `data, duracao, amplitudeMedia, idFisioterapeuta`) → `201` `SessaoDTO` (`id, data, duracao, amplitudeMedia`); `GET /api/pacientes/{id}/sessoes` → `List<SessaoDTO>`, most recent first. One `Sessao` row and one `Medicao` row are created together per `POST` call.

- [ ] **Step 1: Create the DTOs**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/SessaoCreateDTO.java`:

```java
package com.rehabit.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SessaoCreateDTO {

    @NotNull(message = "A data é obrigatória.")
    private LocalDate data;

    @NotNull(message = "A duração é obrigatória.")
    private Integer duracao;

    private BigDecimal amplitudeMedia;

    @NotNull(message = "O fisioterapeuta responsável é obrigatório.")
    private Integer idFisioterapeuta;

    public SessaoCreateDTO() {
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public Integer getDuracao() {
        return duracao;
    }

    public void setDuracao(Integer duracao) {
        this.duracao = duracao;
    }

    public BigDecimal getAmplitudeMedia() {
        return amplitudeMedia;
    }

    public void setAmplitudeMedia(BigDecimal amplitudeMedia) {
        this.amplitudeMedia = amplitudeMedia;
    }

    public Integer getIdFisioterapeuta() {
        return idFisioterapeuta;
    }

    public void setIdFisioterapeuta(Integer idFisioterapeuta) {
        this.idFisioterapeuta = idFisioterapeuta;
    }
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/SessaoDTO.java`:

```java
package com.rehabit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SessaoDTO {

    private Integer id;
    private LocalDate data;
    private Integer duracao;
    private BigDecimal amplitudeMedia;

    public SessaoDTO() {
    }

    public SessaoDTO(Integer id, LocalDate data, Integer duracao, BigDecimal amplitudeMedia) {
        this.id = id;
        this.data = data;
        this.duracao = duracao;
        this.amplitudeMedia = amplitudeMedia;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public Integer getDuracao() {
        return duracao;
    }

    public void setDuracao(Integer duracao) {
        this.duracao = duracao;
    }

    public BigDecimal getAmplitudeMedia() {
        return amplitudeMedia;
    }

    public void setAmplitudeMedia(BigDecimal amplitudeMedia) {
        this.amplitudeMedia = amplitudeMedia;
    }
}
```

- [ ] **Step 2: Create `SessaoService`**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/service/SessaoService.java`:

```java
package com.rehabit.service;

import com.rehabit.dto.SessaoCreateDTO;
import com.rehabit.dto.SessaoDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Medicao;
import com.rehabit.model.Paciente;
import com.rehabit.model.Sessao;
import com.rehabit.repository.MedicaoRepository;
import com.rehabit.repository.PacienteRepository;
import com.rehabit.repository.SessaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SessaoService {

    private final SessaoRepository sessaoRepository;
    private final MedicaoRepository medicaoRepository;
    private final PacienteRepository pacienteRepository;

    public SessaoService(SessaoRepository sessaoRepository, MedicaoRepository medicaoRepository,
                          PacienteRepository pacienteRepository) {
        this.sessaoRepository = sessaoRepository;
        this.medicaoRepository = medicaoRepository;
        this.pacienteRepository = pacienteRepository;
    }

    @Transactional
    public SessaoDTO cadastrar(Integer idPaciente, SessaoCreateDTO dados) {
        Paciente paciente = pacienteRepository.findById(idPaciente)
                .orElseThrow(() -> new AuthException("Paciente não encontrado.", HttpStatus.NOT_FOUND));

        Sessao sessao = new Sessao();
        sessao.setDataSessao(dados.getData());
        sessao.setHoraSessao(LocalTime.now().withNano(0));
        sessao.setDuracao(dados.getDuracao());
        sessao.setIdFisioterapeuta(dados.getIdFisioterapeuta());
        sessao.setIdPaciente(paciente.getId());
        Sessao sessaoSalva = sessaoRepository.save(sessao);

        Medicao medicao = new Medicao();
        medicao.setAmplitudeMedia(dados.getAmplitudeMedia());
        medicao.setDataMedicao(dados.getData());
        medicao.setHoraMedicao(LocalTime.now().withNano(0));
        medicao.setIdSessao(sessaoSalva.getId());
        Medicao medicaoSalva = medicaoRepository.save(medicao);

        return new SessaoDTO(sessaoSalva.getId(), sessaoSalva.getDataSessao(), sessaoSalva.getDuracao(),
                medicaoSalva.getAmplitudeMedia());
    }

    public List<SessaoDTO> listarPorPaciente(Integer idPaciente) {
        return sessaoRepository.findByIdPacienteOrderByDataSessaoDescHoraSessaoDesc(idPaciente).stream()
                .map(s -> {
                    Medicao medicao = medicaoRepository.findByIdSessao(s.getId());
                    return new SessaoDTO(s.getId(), s.getDataSessao(), s.getDuracao(),
                            medicao != null ? medicao.getAmplitudeMedia() : null);
                })
                .collect(Collectors.toList());
    }
}
```

- [ ] **Step 3: Add the two routes to `PacienteController`**

Modify `rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/PacienteController.java` (created in Task 4). Change the import block and constructor, then add two methods.

Replace:

```java
import com.rehabit.dto.PacienteCreateDTO;
import com.rehabit.dto.PacienteDetalheDTO;
import com.rehabit.dto.PacienteResumoDTO;
import com.rehabit.service.PacienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
@CrossOrigin(origins = "*")
public class PacienteController {

    private final PacienteService pacienteService;

    public PacienteController(PacienteService pacienteService) {
        this.pacienteService = pacienteService;
    }
```

with:

```java
import com.rehabit.dto.PacienteCreateDTO;
import com.rehabit.dto.PacienteDetalheDTO;
import com.rehabit.dto.PacienteResumoDTO;
import com.rehabit.dto.SessaoCreateDTO;
import com.rehabit.dto.SessaoDTO;
import com.rehabit.service.PacienteService;
import com.rehabit.service.SessaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
@CrossOrigin(origins = "*")
public class PacienteController {

    private final PacienteService pacienteService;
    private final SessaoService sessaoService;

    public PacienteController(PacienteService pacienteService, SessaoService sessaoService) {
        this.pacienteService = pacienteService;
        this.sessaoService = sessaoService;
    }
```

Then add these two methods right after `buscar(...)`, before the class's closing `}`:

```java

    @PostMapping("/{id}/sessoes")
    public ResponseEntity<SessaoDTO> cadastrarSessao(@PathVariable Integer id,
                                                        @Valid @RequestBody SessaoCreateDTO dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessaoService.cadastrar(id, dados));
    }

    @GetMapping("/{id}/sessoes")
    public ResponseEntity<List<SessaoDTO>> listarSessoes(@PathVariable Integer id) {
        return ResponseEntity.ok(sessaoService.listarPorPaciente(id));
    }
```

- [ ] **Step 4: Verify against the running backend**

```bash
./mvnw.cmd -q package -DskipTests
java -jar target/rehabit-api-1.0.0.jar
```

Using the patient id created while testing Task 4 (or any real patient id):

```bash
curl -s -X POST http://localhost:8080/api/pacientes/<id>/sessoes \
  -H "Content-Type: application/json" \
  -d '{"data":"2026-08-20","duracao":45,"amplitudeMedia":120.5,"idFisioterapeuta":1}'
```

Expected: `201` with the same values echoed back plus a generated `id`.

```bash
curl -s http://localhost:8080/api/pacientes/<id>/sessoes
```

Expected: `200` with a one-item array matching what was just posted. `POST` a second session with a higher `amplitudeMedia` and re-run `GET /api/pacientes?idFisioterapeuta=1` — the patient's `selo` should now read `"Evoluindo"`.

- [ ] **Step 5: Commit**

```bash
git add rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/SessaoCreateDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/SessaoDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/service/SessaoService.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/PacienteController.java
git commit -m "feat(backend): add session+measurement create/list endpoints"
```

---

### Task 6: Goniômetro — `GET/POST /api/goniometro`

**Files:**
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/GoniometroDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/GoniometroSincronizarDTO.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/service/GoniometroService.java`
- Create: `rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/GoniometroController.java`

**Interfaces:**
- Consumes: `GoniometroRepository.findFirstByIdClinicaOrderByIdDesc` / `.save` (Task 1).
- Produces: `GET /api/goniometro?idClinica=` → `GoniometroDTO` (`id, bateria, dataSincronizacao, horaSincronizacao, idClinica`), `404` if the clinic never synced; `POST /api/goniometro/sincronizar` (body `{idClinica}`) → `200` `GoniometroDTO` with a freshly generated reading (random battery 60–100, current timestamp).

- [ ] **Step 1: Create the DTOs**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/GoniometroDTO.java`:

```java
package com.rehabit.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public class GoniometroDTO {

    private Integer id;
    private Integer bateria;
    private LocalDate dataSincronizacao;
    private LocalTime horaSincronizacao;
    private Integer idClinica;

    public GoniometroDTO() {
    }

    public GoniometroDTO(Integer id, Integer bateria, LocalDate dataSincronizacao, LocalTime horaSincronizacao,
                          Integer idClinica) {
        this.id = id;
        this.bateria = bateria;
        this.dataSincronizacao = dataSincronizacao;
        this.horaSincronizacao = horaSincronizacao;
        this.idClinica = idClinica;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getBateria() {
        return bateria;
    }

    public void setBateria(Integer bateria) {
        this.bateria = bateria;
    }

    public LocalDate getDataSincronizacao() {
        return dataSincronizacao;
    }

    public void setDataSincronizacao(LocalDate dataSincronizacao) {
        this.dataSincronizacao = dataSincronizacao;
    }

    public LocalTime getHoraSincronizacao() {
        return horaSincronizacao;
    }

    public void setHoraSincronizacao(LocalTime horaSincronizacao) {
        this.horaSincronizacao = horaSincronizacao;
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }
}
```

`rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/GoniometroSincronizarDTO.java`:

```java
package com.rehabit.dto;

import jakarta.validation.constraints.NotNull;

public class GoniometroSincronizarDTO {

    @NotNull(message = "A clínica é obrigatória.")
    private Integer idClinica;

    public GoniometroSincronizarDTO() {
    }

    public Integer getIdClinica() {
        return idClinica;
    }

    public void setIdClinica(Integer idClinica) {
        this.idClinica = idClinica;
    }
}
```

- [ ] **Step 2: Create `GoniometroService`**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/service/GoniometroService.java`:

```java
package com.rehabit.service;

import com.rehabit.dto.GoniometroDTO;
import com.rehabit.exception.AuthException;
import com.rehabit.model.Goniometro;
import com.rehabit.repository.GoniometroRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GoniometroService {

    private final GoniometroRepository goniometroRepository;

    public GoniometroService(GoniometroRepository goniometroRepository) {
        this.goniometroRepository = goniometroRepository;
    }

    public GoniometroDTO buscarUltimo(Integer idClinica) {
        Goniometro goniometro = goniometroRepository.findFirstByIdClinicaOrderByIdDesc(idClinica)
                .orElseThrow(() -> new AuthException("Nenhum dispositivo sincronizado ainda.", HttpStatus.NOT_FOUND));
        return paraDTO(goniometro);
    }

    public GoniometroDTO sincronizar(Integer idClinica) {
        Goniometro goniometro = new Goniometro();
        goniometro.setIdClinica(idClinica);
        goniometro.setBateria(ThreadLocalRandom.current().nextInt(60, 101));
        goniometro.setDataSincronizacao(LocalDate.now());
        goniometro.setHoraSincronizacao(LocalTime.now().withNano(0));
        return paraDTO(goniometroRepository.save(goniometro));
    }

    private GoniometroDTO paraDTO(Goniometro g) {
        return new GoniometroDTO(g.getId(), g.getBateria(), g.getDataSincronizacao(), g.getHoraSincronizacao(),
                g.getIdClinica());
    }
}
```

- [ ] **Step 3: Create `GoniometroController`**

`rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/GoniometroController.java`:

```java
package com.rehabit.controller;

import com.rehabit.dto.GoniometroDTO;
import com.rehabit.dto.GoniometroSincronizarDTO;
import com.rehabit.service.GoniometroService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goniometro")
@CrossOrigin(origins = "*")
public class GoniometroController {

    private final GoniometroService goniometroService;

    public GoniometroController(GoniometroService goniometroService) {
        this.goniometroService = goniometroService;
    }

    @GetMapping
    public ResponseEntity<GoniometroDTO> buscar(@RequestParam Integer idClinica) {
        return ResponseEntity.ok(goniometroService.buscarUltimo(idClinica));
    }

    @PostMapping("/sincronizar")
    public ResponseEntity<GoniometroDTO> sincronizar(@Valid @RequestBody GoniometroSincronizarDTO dados) {
        return ResponseEntity.ok(goniometroService.sincronizar(dados.getIdClinica()));
    }
}
```

- [ ] **Step 4: Verify against the running backend**

```bash
./mvnw.cmd -q package -DskipTests
java -jar target/rehabit-api-1.0.0.jar
```

```bash
curl -s "http://localhost:8080/api/goniometro?idClinica=1"
```

Expected: `404` "Nenhum dispositivo sincronizado ainda." (correct — nothing synced yet).

```bash
curl -s -X POST http://localhost:8080/api/goniometro/sincronizar \
  -H "Content-Type: application/json" -d '{"idClinica":1}'
```

Expected: `200` with a `bateria` between 60–100 and today's date/time.

```bash
curl -s "http://localhost:8080/api/goniometro?idClinica=1"
```

Expected: `200`, now returning the row just created.

- [ ] **Step 5: Commit**

```bash
git add rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/GoniometroDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/dto/GoniometroSincronizarDTO.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/service/GoniometroService.java \
        rehabit-api/rehabit-api/src/main/java/com/rehabit/controller/GoniometroController.java
git commit -m "feat(backend): add goniometro read/sync endpoints"
```

---

## Frontend

Every page's own script lives in a new `Software/pages/` directory (one small file per screen, per the design-for-isolation guidance — `Software/script.js` stays focused on cross-page concerns: session guard, logout, avatar-from-session, the shared `data-action` clicks, and (from this task on) the three HTTP helpers every page script uses). Each `<light>.html`/`<light>-escuro.html` pair shares the exact same `pages/<screen>.js` file (they have identical element structure/classes — only the CSS theme differs), so every frontend task edits three files: the light HTML, the dark HTML, and one shared JS file.

### Task 7: Shared API helpers in `Software/script.js`

**Files:**
- Modify: `Software/script.js`

**Interfaces:**
- Produces (used by every task below): `async function apiGet(caminho)`, `async function apiPost(caminho, corpo)`, `async function apiPut(caminho, corpo)` — each resolves to the parsed JSON body on success, and `throw`s a plain `Error` whose `.message` is the backend's `mensagem` field (or a generic fallback) on failure. `caminho` starts with `/` and is appended to `API_BASE_URL` (e.g. `apiGet("/clinicas/1")`).

- [ ] **Step 1: Add the three helpers**

Modify `Software/script.js` — insert this right after the existing `getSessao()` function (before the `protegerPagina` IIFE):

```javascript

async function apiGet(caminho) {
  const resposta = await fetch(`${API_BASE_URL}${caminho}`);
  const dados = await resposta.json().catch(() => ({}));
  if (!resposta.ok) {
    throw new Error(dados.mensagem || "Não foi possível carregar os dados.");
  }
  return dados;
}

async function apiPost(caminho, corpo) {
  const resposta = await fetch(`${API_BASE_URL}${caminho}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  const dados = await resposta.json().catch(() => ({}));
  if (!resposta.ok) {
    throw new Error(dados.mensagem || "Não foi possível salvar os dados.");
  }
  return dados;
}

async function apiPut(caminho, corpo) {
  const resposta = await fetch(`${API_BASE_URL}${caminho}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  const dados = await resposta.json().catch(() => ({}));
  if (!resposta.ok) {
    throw new Error(dados.mensagem || "Não foi possível salvar os dados.");
  }
  return dados;
}

function urlFoto(caminhoFoto) {
  if (!caminhoFoto) return null;
  return API_BASE_URL.replace(/\/api$/, "") + caminhoFoto;
}
```

- [ ] **Step 2: Add the two new sidebar actions used by upcoming tasks**

In the same file, find the existing `switch (action) { ... }` block inside the `document.addEventListener("click", ...)` handler. Add two new `case`s right before the `default:` line:

```javascript
    case "add-patient":
      window.location.href = "./cadastrar-paciente.html";
      break;
    case "add-session":
      window.location.href = "./cadastrar-sessao.html";
      break;
```

- [ ] **Step 3: Verify no syntax errors**

Open any `Software/*.html` page directly in a real desktop browser (not needed to be served — `file://` works fine for plain scripts; only `fetch()` calls to `localhost:8080` need the backend running) and check the browser console: it should load with no red errors. A quick way to check syntax without a browser:

```bash
node --check "Software/script.js"
```

Expected: no output (Node isn't installed in every environment used on this project — if the command itself is missing, skip this check and rely on the browser console instead).

- [ ] **Step 4: Commit**

```bash
git add Software/script.js
git commit -m "feat(frontend): add shared fetch helpers to script.js"
```

---

### Task 8: `instituicao.html` — real fisioterapeuta list

**Files:**
- Modify: `Software/instituicao.html`
- Modify: `Software/instituicao-escuro.html`
- Create: `Software/pages/instituicao.js`

**Interfaces:**
- Consumes: `getSessao()`, `apiGet()`, `urlFoto()` (Task 7); `GET /api/fisioterapeutas?idClinica=` (Task 3).

- [ ] **Step 1: Empty out the hardcoded list in both HTML files**

In `Software/instituicao.html`, find (it repeats 5 times inside `<ul class="fisio-list">`):

```html
        <ul class="fisio-list">
          <li class="fisio-item">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="fisio-name">Dr. Marcelo da Silva</div>
            <div class="fisio-spec">Fisioterapeuta<br/>Ortopedista</div>
            <div class="fisio-count"><span class="num">7</span><span class="lbl mobile-only"> pacientes ativos</span></div>
          </li>
          <li class="fisio-item">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="fisio-name">Dr. Marcelo da Silva</div>
            <div class="fisio-spec">Fisioterapeuta<br/>Ortopedista</div>
            <div class="fisio-count"><span class="num">7</span><span class="lbl mobile-only"> pacientes ativos</span></div>
          </li>
          <li class="fisio-item">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="fisio-name">Dr. Marcelo da Silva</div>
            <div class="fisio-spec">Fisioterapeuta<br/>Ortopedista</div>
            <div class="fisio-count"><span class="num">7</span><span class="lbl mobile-only"> pacientes ativos</span></div>
          </li>
          <li class="fisio-item">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="fisio-name">Dr. Marcelo da Silva</div>
            <div class="fisio-spec">Fisioterapeuta<br/>Ortopedista</div>
            <div class="fisio-count"><span class="num">7</span><span class="lbl mobile-only"> pacientes ativos</span></div>
          </li>
          <li class="fisio-item">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="fisio-name">Dr. Marcelo da Silva</div>
            <div class="fisio-spec">Fisioterapeuta<br/>Ortopedista</div>
            <div class="fisio-count"><span class="num">7</span><span class="lbl mobile-only"> pacientes ativos</span></div>
          </li>
        </ul>
```

Replace it with an empty list (JS fills it in on load):

```html
        <ul class="fisio-list"></ul>
```

Do the exact same replacement in `Software/instituicao-escuro.html` — the `<ul class="fisio-list">` block there is byte-identical (this repo keeps light/dark HTML in lockstep; the only differences between the two files are `<link>`/`<body class="dark">`/logo asset/`-escuro.html` href suffixes, none of which are inside this block).

Then, right before `</body>` in both files, find:

```html
  <script src="./script.js"></script>
</body>
```

and add the page script after it:

```html
  <script src="./script.js"></script>
  <script src="./pages/instituicao.js"></script>
</body>
```

- [ ] **Step 2: Create `Software/pages/instituicao.js`**

```javascript
(function carregarInstituicao() {
  const listaEl = document.querySelector(".fisio-list");
  if (!listaEl) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "CLINICA") return;

  document.querySelectorAll(".inst-greeting h1").forEach((el) => {
    el.textContent = `Olá, ${sessao.nome}`;
  });
  document.querySelectorAll(".inst-user-meta .name").forEach((el) => {
    el.textContent = sessao.nome;
  });
  const fotoSessao = urlFoto(sessao.foto);
  if (fotoSessao) {
    document.querySelectorAll(".inst-avatar, .mobile-avatar").forEach((el) => {
      el.style.backgroundImage = `url("${fotoSessao}")`;
      el.style.backgroundSize = "cover";
      el.style.backgroundPosition = "center";
    });
  }

  apiGet(`/fisioterapeutas?idClinica=${sessao.id}`)
    .then((fisioterapeutas) => {
      if (fisioterapeutas.length === 0) {
        listaEl.innerHTML =
          '<li style="padding:16px;color:var(--ink-muted);">Nenhum profissional cadastrado ainda.</li>';
        return;
      }
      listaEl.innerHTML = fisioterapeutas
        .map((f) => {
          const foto = urlFoto(f.foto);
          const estiloAvatar = foto
            ? ` style="background-image:url('${foto}');background-size:cover;background-position:center;"`
            : "";
          return `
          <li class="fisio-item">
            <div class="avatar-sm"${estiloAvatar} aria-hidden="true"></div>
            <div class="fisio-name">${f.nome}</div>
            <div class="fisio-spec">${f.especialidade || "Fisioterapeuta"}</div>
            <div class="fisio-count"><span class="num">${f.pacientesAtivos}</span><span class="lbl mobile-only"> pacientes ativos</span></div>
          </li>`;
        })
        .join("");
    })
    .catch((err) => {
      listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${err.message}</li>`;
    });
})();
```

- [ ] **Step 3: Verify in a real browser**

Start the backend (`java -jar target/rehabit-api-1.0.0.jar` from `rehabit-api/rehabit-api`, after `./mvnw.cmd -q package -DskipTests`). Open `Software/instituicao.html` directly in a desktop browser (not the sandboxed preview tool used earlier in this project — a real Chrome/Edge window runs `file://` scripts fine). Log in first through `Login/login.html` with a clinic account so `localStorage.rehabit_usuario` is set, then navigate to `Software/instituicao.html`.

Expected: the greeting shows the real clinic name; the list shows either "Nenhum profissional cadastrado ainda." (no fisioterapeutas yet) or real rows with real names/specialties/counts — not "Dr. Marcelo da Silva" ×5. Register a professional via `cadastrar-profissional.html` first if you want to see a populated list. Repeat on `instituicao-escuro.html`.

- [ ] **Step 4: Commit**

```bash
git add Software/instituicao.html Software/instituicao-escuro.html Software/pages/instituicao.js
git commit -m "feat(frontend): wire instituicao.html to real fisioterapeuta list"
```

---

### Task 9: `profissional.html` — real paciente list with derived status badge

**Files:**
- Modify: `Software/profissional.html`
- Modify: `Software/profissional-escuro.html`
- Create: `Software/pages/profissional.js`

**Interfaces:**
- Consumes: `getSessao()`, `apiGet()`, `urlFoto()` (Task 7); `GET /api/pacientes?idFisioterapeuta=` (Task 4); the `add-patient` sidebar action (Task 7).
- Produces: each `.pat-item` carries `data-id="<pacienteId>"` and a click handler that navigates to `./paciente.html?id=<pacienteId>` — Task 15 depends on this being the only way patients are reached from this list.

- [ ] **Step 1: Empty out the hardcoded list in both HTML files**

In `Software/profissional.html`, find the whole `<ul class="pat-list">...</ul>` block (six `<li class="pat-item">` entries, the last one additionally carrying `mobile-only`):

```html
        <ul class="pat-list">
          <li class="pat-item">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="pat-name">Ana Carolina Silva</div>
            <div class="pat-meta-m mobile-only">Última sessão<br/>07/05/2026</div>
            <div class="pat-date desktop-only">07/05/2026</div>
            <div class="pat-status"><span class="badge evoluindo">Evoluindo</span></div>
          </li>
          <li class="pat-item">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="pat-name">Ana Carolina Silva</div>
            <div class="pat-meta-m mobile-only">Última sessão<br/>07/05/2026</div>
            <div class="pat-date desktop-only">07/05/2026</div>
            <div class="pat-status"><span class="badge estavel">Estável</span></div>
          </li>
          <li class="pat-item">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="pat-name">Ana Carolina Silva</div>
            <div class="pat-meta-m mobile-only">Última sessão<br/>07/05/2026</div>
            <div class="pat-date desktop-only">07/05/2026</div>
            <div class="pat-status"><span class="badge instavel">Instável</span></div>
          </li>
          <li class="pat-item">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="pat-name">Ana Carolina Silva</div>
            <div class="pat-meta-m mobile-only">Última sessão<br/>07/05/2026</div>
            <div class="pat-date desktop-only">07/05/2026</div>
            <div class="pat-status"><span class="badge evoluindo">Evoluindo</span></div>
          </li>
          <li class="pat-item">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="pat-name">Ana Carolina Silva</div>
            <div class="pat-meta-m mobile-only">Última sessão<br/>07/05/2026</div>
            <div class="pat-date desktop-only">07/05/2026</div>
            <div class="pat-status"><span class="badge evoluindo">Evoluindo</span></div>
          </li>
          <li class="pat-item mobile-only">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="pat-name">Ana Carolina Silva</div>
            <div class="pat-meta-m">Última sessão<br/>07/05/2026</div>
            <div class="pat-status"><span class="badge evoluindo">Evoluindo</span></div>
          </li>
        </ul>
```

Replace it with:

```html
        <ul class="pat-list"></ul>
```

Do the same replacement in `Software/profissional-escuro.html`. Then add the page script before `</body>` in both files, same pattern as Task 8:

```html
  <script src="./script.js"></script>
  <script src="./pages/profissional.js"></script>
</body>
```

- [ ] **Step 2: Create `Software/pages/profissional.js`**

```javascript
(function carregarProfissional() {
  const listaEl = document.querySelector(".pat-list");
  if (!listaEl) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  const SELO_INFO = {
    Evoluindo: { classe: "evoluindo", texto: "Evoluindo" },
    Estavel: { classe: "estavel", texto: "Estável" },
    Instavel: { classe: "instavel", texto: "Instável" },
  };

  const hora = new Date().getHours();
  const saudacao = hora < 12 ? "Bom dia" : hora < 18 ? "Boa tarde" : "Boa noite";
  document.querySelectorAll(".pro-greeting h1").forEach((el) => {
    el.textContent = `${saudacao}, ${sessao.nome}!`;
  });
  document.querySelectorAll(".inst-user-meta .name").forEach((el) => {
    el.textContent = sessao.nome;
  });
  const fotoSessao = urlFoto(sessao.foto);
  if (fotoSessao) {
    document.querySelectorAll(".inst-avatar, .mobile-avatar").forEach((el) => {
      el.style.backgroundImage = `url("${fotoSessao}")`;
      el.style.backgroundSize = "cover";
      el.style.backgroundPosition = "center";
    });
  }

  listaEl.addEventListener("click", (e) => {
    const item = e.target.closest(".pat-item[data-id]");
    if (item) window.location.href = `./paciente.html?id=${item.dataset.id}`;
  });

  apiGet(`/pacientes?idFisioterapeuta=${sessao.id}`)
    .then((pacientes) => {
      if (pacientes.length === 0) {
        listaEl.innerHTML =
          '<li style="padding:16px;color:var(--ink-muted);">Nenhum paciente cadastrado ainda.</li>';
        return;
      }
      listaEl.innerHTML = pacientes
        .map((p) => {
          const info = SELO_INFO[p.selo];
          const badge = info
            ? `<span class="badge ${info.classe}">${info.texto}</span>`
            : `<span class="badge estavel">Sem sessões</span>`;
          const ultimaSessao = p.ultimaSessao || "-";
          return `
          <li class="pat-item" data-id="${p.id}" style="cursor:pointer;">
            <div class="avatar-sm" aria-hidden="true"></div>
            <div class="pat-name">${p.nome}</div>
            <div class="pat-meta-m mobile-only">Última sessão<br/>${ultimaSessao}</div>
            <div class="pat-date desktop-only">${ultimaSessao}</div>
            <div class="pat-status">${badge}</div>
          </li>`;
        })
        .join("");
    })
    .catch((err) => {
      listaEl.innerHTML = `<li style="padding:16px;color:var(--ink-muted);">${err.message}</li>`;
    });
})();
```

- [ ] **Step 3: Verify in a real browser**

With the backend running, log in as a fisioterapeuta (register one via `cadastrar-profissional.html` first if needed, and register a patient or two for them via `cadastrar-paciente.html` so the list isn't empty). Open `Software/profissional.html`.

Expected: greeting shows the real name and time-of-day-appropriate salutation; list shows real patients with "Sem sessões" badges (no sessions recorded yet); clicking a row navigates to `paciente.html?id=<realId>` (confirm via the address bar — Task 15 builds that page next). Repeat on `profissional-escuro.html`.

- [ ] **Step 4: Commit**

```bash
git add Software/profissional.html Software/profissional-escuro.html Software/pages/profissional.js
git commit -m "feat(frontend): wire profissional.html to real paciente list"
```

---

### Task 10: `perfil-instituicao.html` — real profile + stats, and fix the "go-list" shortcut

**Files:**
- Modify: `Software/perfil-instituicao.html`
- Modify: `Software/perfil-instituicao-escuro.html`
- Modify: `Software/script.js` (one-line behavior fix, shared)
- Create: `Software/pages/perfil-instituicao.js`

**Interfaces:**
- Consumes: `getSessao()`, `apiGet()`, `urlFoto()` (Task 7); `GET /api/clinicas/{id}` (Task 2).

- [ ] **Step 1: Make the shared "go-list" action route by role**

`Software/script.js` currently has this in the click-delegation `switch`:

```javascript
    case "go-list":
      alert("Ir para a lista");
      break;
```

Replace it with:

```javascript
    case "go-list": {
      const sessaoAtual = getSessao();
      window.location.href =
        sessaoAtual && sessaoAtual.tipo === "CLINICA" ? "./instituicao.html" : "./profissional.html";
      break;
    }
```

(Both `perfil-instituicao.html`'s "Ir para lista de profissionais" and `perfil-profissional.html`'s "Ir para lista de pacientes" buttons already use `data-action="go-list"` — this one shared fix makes both work correctly, since each role's own "home" page already *is* its list, built in Tasks 8/9.)

- [ ] **Step 2: Add the page script tag to both HTML files**

In `Software/perfil-instituicao.html` and `Software/perfil-instituicao-escuro.html`, find:

```html
  <script src="./script.js"></script>
</body>
```

Replace with:

```html
  <script src="./script.js"></script>
  <script src="./pages/perfil-instituicao.js"></script>
</body>
```

No other HTML changes are needed on this screen — every dynamic value is filled in by JS at runtime (see Step 3), including removing the `+16 este mês`-style `.delta` lines, so the static markup can stay as-is.

- [ ] **Step 3: Create `Software/pages/perfil-instituicao.js`**

```javascript
(function carregarPerfilInstituicao() {
  const header = document.querySelector(".profile-header");
  const statsEl = document.querySelector(".stats");
  if (!header || !statsEl || !document.querySelector(".access-card")) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "CLINICA") return;

  function definirTextoAposSvg(row, texto) {
    const textNode = Array.from(row.childNodes).find(
      (n) => n.nodeType === Node.TEXT_NODE && n.textContent.trim() !== ""
    );
    if (textNode) {
      textNode.textContent = ` ${texto}`;
    } else {
      row.appendChild(document.createTextNode(` ${texto}`));
    }
  }

  apiGet(`/clinicas/${sessao.id}`)
    .then((clinica) => {
      header.querySelector("h1").textContent = clinica.nome;
      header.querySelector(".role").textContent = clinica.subtitulo || "";

      const rows = header.querySelectorAll(".contact .row");
      if (rows[0]) definirTextoAposSvg(rows[0], clinica.email || "-");
      if (rows[1]) definirTextoAposSvg(rows[1], clinica.telefone || "Não informado");
      if (rows[2]) definirTextoAposSvg(rows[2], clinica.endereco || "Não informado");

      const foto = urlFoto(clinica.foto);
      if (foto) {
        const avatarEl = header.querySelector(".avatar");
        avatarEl.style.backgroundImage = `url("${foto}")`;
        avatarEl.style.backgroundSize = "cover";
        avatarEl.style.backgroundPosition = "center";
      }

      const descEl = document.querySelector(".description-card p");
      if (descEl) descEl.textContent = clinica.descricao || "Sem descrição cadastrada.";

      const mobileVs = document.querySelectorAll(".contact-card-mobile .v");
      if (mobileVs[0]) mobileVs[0].textContent = clinica.email || "-";
      if (mobileVs[1]) mobileVs[1].textContent = clinica.telefone || "Não informado";
      if (mobileVs[2]) mobileVs[2].textContent = clinica.endereco || "Não informado";

      const valores = [
        { valor: String(clinica.profissionaisAtivos), rotulo: "Profissionais ativos" },
        { valor: String(clinica.pacientesTotais), rotulo: "Pacientes totais" },
        { valor: String(clinica.sessoesEsteMes), rotulo: "Sessões este mês" },
        {
          valor: clinica.amplitudeMediaGeral != null ? `${clinica.amplitudeMediaGeral.toFixed(0)}°` : "-",
          rotulo: "Amplitude média geral",
        },
      ];
      statsEl.querySelectorAll(".stat").forEach((card, i) => {
        const delta = card.querySelector(".delta");
        if (delta) delta.remove();
        const valorEl = card.querySelector(".value");
        const labelEl = card.querySelector(".label");
        if (valorEl && valores[i]) valorEl.textContent = valores[i].valor;
        if (labelEl && valores[i]) labelEl.textContent = valores[i].rotulo;
      });
    })
    .catch((err) => alert(err.message));
})();
```

- [ ] **Step 4: Verify in a real browser**

With the backend running and logged in as a clinic, open `Software/perfil-instituicao.html`.

Expected: name/subtitle/email/phone/address/description all match what was entered at registration (or `editar-perfil-instituicao.html` once Task 12 lands); the 4 stat cards show real numbers with no "+X este mês" lines; "Ir para lista de profissionais" now navigates to `instituicao.html` instead of alerting. Repeat on `perfil-instituicao-escuro.html`.

- [ ] **Step 5: Commit**

```bash
git add Software/perfil-instituicao.html Software/perfil-instituicao-escuro.html \
        Software/script.js Software/pages/perfil-instituicao.js
git commit -m "feat(frontend): wire perfil-instituicao.html to real profile data"
```

---

### Task 11: `perfil-profissional.html` — real profile + stats

**Files:**
- Modify: `Software/perfil-profissional.html`
- Modify: `Software/perfil-profissional-escuro.html`
- Create: `Software/pages/perfil-profissional.js`

**Interfaces:**
- Consumes: `getSessao()`, `apiGet()`, `urlFoto()` (Task 7); `GET /api/fisioterapeutas/{id}` (Task 3); the `go-list` fix (Task 10).

- [ ] **Step 1: Add the page script tag to both HTML files**

In `Software/perfil-profissional.html` and `Software/perfil-profissional-escuro.html`, find:

```html
  <script src="./script.js"></script>
</body>
```

Replace with:

```html
  <script src="./script.js"></script>
  <script src="./pages/perfil-profissional.js"></script>
</body>
```

- [ ] **Step 2: Create `Software/pages/perfil-profissional.js`**

```javascript
(function carregarPerfilProfissional() {
  const header = document.querySelector(".profile-header");
  const statsEl = document.querySelector(".stats.cols-3");
  if (!header || !statsEl) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  function definirTextoAposSvg(row, texto) {
    const textNode = Array.from(row.childNodes).find(
      (n) => n.nodeType === Node.TEXT_NODE && n.textContent.trim() !== ""
    );
    if (textNode) {
      textNode.textContent = ` ${texto}`;
    } else {
      row.appendChild(document.createTextNode(` ${texto}`));
    }
  }

  apiGet(`/fisioterapeutas/${sessao.id}`)
    .then((f) => {
      header.querySelector("h1").textContent = f.nome;
      header.querySelector(".role").textContent = f.especialidade || "Fisioterapeuta";
      const idEl = header.querySelector(".id");
      if (idEl) idEl.textContent = `COFFITO ${f.coffito}`;

      const rows = header.querySelectorAll(".contact .row");
      if (rows[0]) definirTextoAposSvg(rows[0], f.email || "-");
      if (rows[1]) definirTextoAposSvg(rows[1], f.telefone || "Não informado");
      if (rows[2]) definirTextoAposSvg(rows[2], f.localidade || "Não informado");

      const foto = urlFoto(f.foto);
      if (foto) {
        const avatarEl = header.querySelector(".avatar");
        avatarEl.style.backgroundImage = `url("${foto}")`;
        avatarEl.style.backgroundSize = "cover";
        avatarEl.style.backgroundPosition = "center";
      }

      const descEl = document.querySelector(".description-card p");
      if (descEl) descEl.textContent = f.descricao || "Sem descrição cadastrada.";

      const mobileVs = document.querySelectorAll(".contact-card-mobile .v");
      if (mobileVs[0]) mobileVs[0].textContent = f.email || "-";
      if (mobileVs[1]) mobileVs[1].textContent = f.telefone || "Não informado";
      if (mobileVs[2]) mobileVs[2].textContent = f.localidade || "Não informado";

      const valores = [
        { valor: String(f.pacientesAtivos), rotulo: "Pacientes ativos" },
        { valor: String(f.sessoesEsteMes), rotulo: "Sessões este mês" },
        {
          valor: f.amplitudeMediaGeral != null ? `${f.amplitudeMediaGeral.toFixed(0)}°` : "-",
          rotulo: "Amplitude média geral",
        },
      ];
      statsEl.querySelectorAll(".stat").forEach((card, i) => {
        const delta = card.querySelector(".delta");
        if (delta) delta.remove();
        const valorEl = card.querySelector(".value");
        const labelEl = card.querySelector(".label");
        if (valorEl && valores[i]) valorEl.textContent = valores[i].valor;
        if (labelEl && valores[i]) labelEl.textContent = valores[i].rotulo;
      });
    })
    .catch((err) => alert(err.message));
})();
```

- [ ] **Step 3: Verify in a real browser**

Logged in as a fisioterapeuta, open `Software/perfil-profissional.html`.

Expected: name/specialty/COFFITO/email/phone/localidade/description all real; 3 stat cards show real numbers, no "+X este mês" lines; "Ir para lista de pacientes" navigates to `profissional.html`. Repeat on `perfil-profissional-escuro.html`.

- [ ] **Step 4: Commit**

```bash
git add Software/perfil-profissional.html Software/perfil-profissional-escuro.html \
        Software/pages/perfil-profissional.js
git commit -m "feat(frontend): wire perfil-profissional.html to real profile data"
```

---

### Task 12: `editar-perfil-instituicao.html` — real prefill, real save, password change

**Files:**
- Modify: `Software/editar-perfil-instituicao.html`
- Modify: `Software/editar-perfil-instituicao-escuro.html`
- Create: `Software/pages/editar-perfil-instituicao.js`

**Interfaces:**
- Consumes: `getSessao()`, `apiGet()`, `apiPut()`, `urlFoto()` (Task 7); `GET`/`PUT /api/clinicas/{id}` (Task 2).
- Produces: on successful save, overwrites `localStorage.rehabit_usuario` with the fresh `nome`/`email`/`foto` so the header greeting on other pages stays correct without re-login.

- [ ] **Step 1: Strip the hardcoded values and add password fields**

In both `Software/editar-perfil-instituicao.html` and `Software/editar-perfil-instituicao-escuro.html`, find:

```html
        <section class="card edit-avatar-card">
          <div class="avatar-lg" aria-hidden="true"></div>
          <button type="button" class="btn-outline">Alterar foto</button>
          <h2 class="edit-name">CER Atibaia</h2>
          <p class="edit-role">Excelência em reabilitação</p>
          <p class="edit-id">ID-XXXX</p>
        </section>

        <section class="card edit-info-card">
          <h3>Informações</h3>
          <div class="edit-section">
            <div class="field">
              <label for="i-cnpj">CNPJ</label>
              <input id="i-cnpj" type="text" value="" />
            </div>
            <div class="field">
              <label for="i-nome">Nome</label>
              <input id="i-nome" type="text" value="CER Atibaia" />
            </div>
            <div class="two-col">
              <div class="field">
                <label for="i-email">E-mail</label>
                <input id="i-email" type="email" value="ceratibaia.contato@gmail.com" />
              </div>
              <div class="field">
                <label for="i-tel">Telefone</label>
                <input id="i-tel" type="tel" value="(11) 3333-3333" />
              </div>
            </div>
          </div>
          <hr class="edit-sep" />
          <div class="edit-section">
            <div class="two-col">
              <div class="field">
                <label for="i-sub">Subtítulo</label>
                <input id="i-sub" type="text" value="Excelência em reabilitação" />
              </div>
              <div class="field">
                <label for="i-loc">Localidade</label>
                <input id="i-loc" type="text" value="São Paulo, SP" />
              </div>
            </div>
            <div class="field">
              <label for="i-desc">Descrição</label>
              <textarea id="i-desc" rows="4">A CER Atibaia é referência em atendimento fisioterapêutico, com uma equipe especializada em diversas áreas de reabilitação. Nosso objetivo é proporcionar tratamento humanizado e baseado em evidências.</textarea>
            </div>
          </div>
          <div class="edit-actions">
            <button type="submit" class="btn-primary">Salvar alterações</button>
          </div>
        </section>
```

Replace with:

```html
        <section class="card edit-avatar-card">
          <div class="avatar-lg" aria-hidden="true"></div>
          <button type="button" class="btn-outline">Alterar foto</button>
          <h2 class="edit-name"></h2>
          <p class="edit-role"></p>
          <p class="edit-id"></p>
        </section>

        <section class="card edit-info-card">
          <h3>Informações</h3>
          <div class="edit-section">
            <div class="field">
              <label for="i-cnpj">CNPJ</label>
              <input id="i-cnpj" type="text" value="" />
            </div>
            <div class="field">
              <label for="i-nome">Nome</label>
              <input id="i-nome" type="text" value="" />
            </div>
            <div class="two-col">
              <div class="field">
                <label for="i-email">E-mail</label>
                <input id="i-email" type="email" value="" />
              </div>
              <div class="field">
                <label for="i-tel">Telefone</label>
                <input id="i-tel" type="tel" value="" />
              </div>
            </div>
          </div>
          <hr class="edit-sep" />
          <div class="edit-section">
            <div class="two-col">
              <div class="field">
                <label for="i-sub">Subtítulo</label>
                <input id="i-sub" type="text" value="" />
              </div>
              <div class="field">
                <label for="i-loc">Localidade</label>
                <input id="i-loc" type="text" value="" />
              </div>
            </div>
            <div class="field">
              <label for="i-desc">Descrição</label>
              <textarea id="i-desc" rows="4"></textarea>
            </div>
          </div>
          <hr class="edit-sep" />
          <div class="edit-section">
            <p class="caption">Deixe em branco para manter a senha atual</p>
            <div class="two-col">
              <div class="field">
                <label for="i-senha-atual">Senha atual</label>
                <input id="i-senha-atual" type="password" autocomplete="current-password" />
              </div>
              <div class="field">
                <label for="i-senha-nova">Nova senha</label>
                <input id="i-senha-nova" type="password" autocomplete="new-password" />
              </div>
            </div>
          </div>
          <div class="edit-actions">
            <button type="submit" class="btn-primary">Salvar alterações</button>
          </div>
        </section>
```

Then add the page script before `</body>` in both files:

```html
  <script src="./script.js"></script>
  <script src="./pages/editar-perfil-instituicao.js"></script>
</body>
```

- [ ] **Step 2: Create `Software/pages/editar-perfil-instituicao.js`**

```javascript
(function editarPerfilInstituicao() {
  const form = document.querySelector(".edit-grid");
  if (!form || !document.getElementById("i-cnpj")) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "CLINICA") return;

  let fotoAtual = null;

  apiGet(`/clinicas/${sessao.id}`)
    .then((clinica) => {
      fotoAtual = clinica.foto;
      document.querySelector(".edit-name").textContent = clinica.nome;
      document.querySelector(".edit-role").textContent = clinica.subtitulo || "";
      document.querySelector(".edit-id").textContent = `ID-${String(clinica.id).padStart(4, "0")}`;
      document.getElementById("i-cnpj").value = clinica.cnpj || "";
      document.getElementById("i-nome").value = clinica.nome || "";
      document.getElementById("i-email").value = clinica.email || "";
      document.getElementById("i-tel").value = clinica.telefone || "";
      document.getElementById("i-sub").value = clinica.subtitulo || "";
      document.getElementById("i-loc").value = clinica.endereco || "";
      document.getElementById("i-desc").value = clinica.descricao || "";

      const foto = urlFoto(clinica.foto);
      if (foto) {
        const avatarEl = document.querySelector(".edit-avatar-card .avatar-lg");
        avatarEl.style.backgroundImage = `url("${foto}")`;
        avatarEl.style.backgroundSize = "cover";
        avatarEl.style.backgroundPosition = "center";
      }
    })
    .catch((err) => alert(err.message));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const senhaAtual = document.getElementById("i-senha-atual").value;
    const novaSenha = document.getElementById("i-senha-nova").value;
    if (novaSenha && novaSenha.length < 6) {
      alert("A nova senha deve ter ao menos 6 caracteres.");
      return;
    }
    if (novaSenha && !senhaAtual) {
      alert("Informe a senha atual para trocar de senha.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";

    try {
      const atualizado = await apiPut(`/clinicas/${sessao.id}`, {
        nome: document.getElementById("i-nome").value.trim(),
        cnpj: document.getElementById("i-cnpj").value.trim(),
        email: document.getElementById("i-email").value.trim(),
        telefone: document.getElementById("i-tel").value.trim() || null,
        endereco: document.getElementById("i-loc").value.trim() || null,
        subtitulo: document.getElementById("i-sub").value.trim() || null,
        descricao: document.getElementById("i-desc").value.trim() || null,
        foto: fotoAtual,
        senhaAtual: senhaAtual || null,
        novaSenha: novaSenha || null,
      });

      localStorage.setItem(
        "rehabit_usuario",
        JSON.stringify(Object.assign({}, sessao, {
          nome: atualizado.nome,
          email: atualizado.email,
          foto: atualizado.foto,
        }))
      );

      alert("Perfil atualizado com sucesso.");
      window.location.href = "./perfil-instituicao.html";
    } catch (err) {
      alert(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
```

- [ ] **Step 3: Verify in a real browser**

Logged in as a clinic, open `Software/editar-perfil-instituicao.html`. Expected: every field pre-filled with real values (not "CER Atibaia"). Change the phone number and save with both password fields blank — expect success and redirect to `perfil-instituicao.html` showing the new phone number. Go back in, this time fill "Nova senha" with `12345` (5 chars) — expect the client-side "ao menos 6 caracteres" alert, no request sent. Fill nova senha `senha456` but leave "Senha atual" blank — expect the client-side "Informe a senha atual" alert. Fill both correctly but with a wrong current password — expect a `400` "Senha atual incorreta." from the server. Fill both correctly with the real current password — expect success, then log out and log back in with the new password to confirm it took effect. Repeat the pre-fill check on `editar-perfil-instituicao-escuro.html`.

- [ ] **Step 4: Commit**

```bash
git add Software/editar-perfil-instituicao.html Software/editar-perfil-instituicao-escuro.html \
        Software/pages/editar-perfil-instituicao.js
git commit -m "feat(frontend): wire editar-perfil-instituicao.html to real save + password change"
```

---

### Task 13: `editar-perfil-profissional.html` — real prefill, real save, password change

**Files:**
- Modify: `Software/editar-perfil-profissional.html`
- Modify: `Software/editar-perfil-profissional-escuro.html`
- Create: `Software/pages/editar-perfil-profissional.js`

**Interfaces:**
- Consumes: `getSessao()`, `apiGet()`, `apiPut()`, `urlFoto()` (Task 7); `GET`/`PUT /api/fisioterapeutas/{id}` (Task 3).

- [ ] **Step 1: Strip the hardcoded values and add password fields**

In both `Software/editar-perfil-profissional.html` and `Software/editar-perfil-profissional-escuro.html`, find:

```html
        <section class="card edit-avatar-card">
          <div class="avatar-lg" aria-hidden="true"></div>
          <button type="button" class="btn-outline">Alterar foto</button>
          <h2 class="edit-name">Dr. Marcelo da Silva</h2>
          <p class="edit-role">Fisioterapeuta Ortopédico</p>
          <p class="edit-id">ID-XXXX</p>
        </section>

        <section class="card edit-info-card">
          <h3>Informações</h3>
          <div class="edit-section">
            <div class="field">
              <label for="p-coffito">COFFITO</label>
              <input id="p-coffito" type="text" value="" />
            </div>
            <div class="field">
              <label for="p-nome">Nome completo</label>
              <input id="p-nome" type="text" value="Dr. Marcelo da Silva" />
            </div>
            <div class="two-col">
              <div class="field">
                <label for="p-email">E-mail</label>
                <input id="p-email" type="email" value="marcelo.silva@gmail.com" />
              </div>
              <div class="field">
                <label for="p-tel">Telefone</label>
                <input id="p-tel" type="tel" value="(11) 99999-9999" />
              </div>
            </div>
          </div>
          <hr class="edit-sep" />
          <div class="edit-section">
            <div class="two-col">
              <div class="field">
                <label for="p-esp">Especialidade</label>
                <input id="p-esp" type="text" value="Fisioterapeuta Ortopédico" />
              </div>
              <div class="field">
                <label for="p-loc">Localidade</label>
                <input id="p-loc" type="text" value="São Paulo, SP" />
              </div>
            </div>
            <div class="field">
              <label for="p-desc">Descrição</label>
              <textarea id="p-desc" rows="4">Fisioterapeuta com 8 anos de experiência em reabilitação ortopédica e esportiva. Especialista em recuperação pós-cirúrgica de joelho e ombro.</textarea>
            </div>
          </div>
          <div class="edit-actions">
            <button type="submit" class="btn-primary">Salvar alterações</button>
          </div>
        </section>
```

Replace with:

```html
        <section class="card edit-avatar-card">
          <div class="avatar-lg" aria-hidden="true"></div>
          <button type="button" class="btn-outline">Alterar foto</button>
          <h2 class="edit-name"></h2>
          <p class="edit-role"></p>
          <p class="edit-id"></p>
        </section>

        <section class="card edit-info-card">
          <h3>Informações</h3>
          <div class="edit-section">
            <div class="field">
              <label for="p-coffito">COFFITO</label>
              <input id="p-coffito" type="text" value="" disabled />
            </div>
            <div class="field">
              <label for="p-nome">Nome completo</label>
              <input id="p-nome" type="text" value="" />
            </div>
            <div class="two-col">
              <div class="field">
                <label for="p-email">E-mail</label>
                <input id="p-email" type="email" value="" />
              </div>
              <div class="field">
                <label for="p-tel">Telefone</label>
                <input id="p-tel" type="tel" value="" />
              </div>
            </div>
          </div>
          <hr class="edit-sep" />
          <div class="edit-section">
            <div class="two-col">
              <div class="field">
                <label for="p-esp">Especialidade</label>
                <input id="p-esp" type="text" value="" />
              </div>
              <div class="field">
                <label for="p-loc">Localidade</label>
                <input id="p-loc" type="text" value="" />
              </div>
            </div>
            <div class="field">
              <label for="p-desc">Descrição</label>
              <textarea id="p-desc" rows="4"></textarea>
            </div>
          </div>
          <hr class="edit-sep" />
          <div class="edit-section">
            <p class="caption">Deixe em branco para manter a senha atual</p>
            <div class="two-col">
              <div class="field">
                <label for="p-senha-atual">Senha atual</label>
                <input id="p-senha-atual" type="password" autocomplete="current-password" />
              </div>
              <div class="field">
                <label for="p-senha-nova">Nova senha</label>
                <input id="p-senha-nova" type="password" autocomplete="new-password" />
              </div>
            </div>
          </div>
          <div class="edit-actions">
            <button type="submit" class="btn-primary">Salvar alterações</button>
          </div>
        </section>
```

(`p-coffito` is marked `disabled` — COFFITO has no `PUT` field in `FisioterapeutaUpdateDTO`/backend on purpose: it is the professional's official registration number, not something this screen is meant to let people silently rewrite. It's still shown, just read-only.)

Then add the page script before `</body>` in both files:

```html
  <script src="./script.js"></script>
  <script src="./pages/editar-perfil-profissional.js"></script>
</body>
```

- [ ] **Step 2: Create `Software/pages/editar-perfil-profissional.js`**

```javascript
(function editarPerfilProfissional() {
  const form = document.querySelector(".edit-grid");
  if (!form || !document.getElementById("p-coffito")) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  let fotoAtual = null;

  apiGet(`/fisioterapeutas/${sessao.id}`)
    .then((f) => {
      fotoAtual = f.foto;
      document.querySelector(".edit-name").textContent = f.nome;
      document.querySelector(".edit-role").textContent = f.especialidade || "";
      document.querySelector(".edit-id").textContent = `ID-${String(f.id).padStart(4, "0")}`;
      document.getElementById("p-coffito").value = f.coffito || "";
      document.getElementById("p-nome").value = f.nome || "";
      document.getElementById("p-email").value = f.email || "";
      document.getElementById("p-tel").value = f.telefone || "";
      document.getElementById("p-esp").value = f.especialidade || "";
      document.getElementById("p-loc").value = f.localidade || "";
      document.getElementById("p-desc").value = f.descricao || "";

      const foto = urlFoto(f.foto);
      if (foto) {
        const avatarEl = document.querySelector(".edit-avatar-card .avatar-lg");
        avatarEl.style.backgroundImage = `url("${foto}")`;
        avatarEl.style.backgroundSize = "cover";
        avatarEl.style.backgroundPosition = "center";
      }
    })
    .catch((err) => alert(err.message));

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const senhaAtual = document.getElementById("p-senha-atual").value;
    const novaSenha = document.getElementById("p-senha-nova").value;
    if (novaSenha && novaSenha.length < 6) {
      alert("A nova senha deve ter ao menos 6 caracteres.");
      return;
    }
    if (novaSenha && !senhaAtual) {
      alert("Informe a senha atual para trocar de senha.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";

    try {
      const atualizado = await apiPut(`/fisioterapeutas/${sessao.id}`, {
        nome: document.getElementById("p-nome").value.trim(),
        email: document.getElementById("p-email").value.trim(),
        telefone: document.getElementById("p-tel").value.trim() || null,
        especialidade: document.getElementById("p-esp").value.trim() || null,
        localidade: document.getElementById("p-loc").value.trim() || null,
        descricao: document.getElementById("p-desc").value.trim() || null,
        foto: fotoAtual,
        senhaAtual: senhaAtual || null,
        novaSenha: novaSenha || null,
      });

      localStorage.setItem(
        "rehabit_usuario",
        JSON.stringify(Object.assign({}, sessao, {
          nome: atualizado.nome,
          email: atualizado.email,
          foto: atualizado.foto,
        }))
      );

      alert("Perfil atualizado com sucesso.");
      window.location.href = "./perfil-profissional.html";
    } catch (err) {
      alert(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
```

- [ ] **Step 3: Verify in a real browser**

Same checklist as Task 12 Step 3, but logged in as a fisioterapeuta on `editar-perfil-profissional.html` / `-escuro.html`, confirming `p-coffito` renders the real COFFITO and is disabled (not editable).

- [ ] **Step 4: Commit**

```bash
git add Software/editar-perfil-profissional.html Software/editar-perfil-profissional-escuro.html \
        Software/pages/editar-perfil-profissional.js
git commit -m "feat(frontend): wire editar-perfil-profissional.html to real save + password change"
```

---

### Task 14: `cadastrar-paciente.html` — add CPF field, real submit

**Files:**
- Modify: `Software/cadastrar-paciente.html`
- Modify: `Software/cadastrar-paciente-escuro.html`
- Create: `Software/pages/cadastrar-paciente.js`

**Interfaces:**
- Consumes: `getSessao()`, `apiPost()` (Task 7); `POST /api/pacientes` (Task 4).
- Produces: on success, navigates to `./paciente.html?id=<novoId>` — the entry point Task 15's page depends on existing.

`tb03_paciente` has no photo column (confirmed against `Rehabit.sql`), so the existing "Alterar foto" buttons on this screen are left exactly as they are today (decorative, no click handler) — wiring them would have nowhere to persist to.

- [ ] **Step 1: Add a CPF field, switch the birth-date input to `type="date"`, give the form an id**

In both `Software/cadastrar-paciente.html` and `Software/cadastrar-paciente-escuro.html`, find:

```html
      <form class="card form-card" onsubmit="event.preventDefault();">
        <h3>Informações</h3>
        <div class="form-split">
          <div class="form-avatar-col">
            <div class="form-avatar" aria-hidden="true"></div>
            <button type="button" class="btn-outline-brand">Alterar foto</button>
          </div>
          <div>
            <div class="two-col">
              <div class="field">
                <label for="nome">Nome</label>
                <input id="nome" type="text" placeholder="Ex: Ana Carolina Silva" />
              </div>
              <div class="field">
                <label for="nasc">Data de nascimento</label>
                <input id="nasc" type="text" placeholder="dd/mm/aaaa" />
              </div>
            </div>
            <div class="two-col">
              <div class="field">
                <label for="sexo">Sexo</label>
                <select id="sexo">
                  <option value="" selected>Selecione</option>
                  <option>Feminino</option>
                  <option>Masculino</option>
                  <option>Outro</option>
                </select>
              </div>
              <div class="field">
                <label for="situacao">Situação</label>
                <input id="situacao" type="text" placeholder="Ex: Lesão de manguito rotador" />
              </div>
            </div>
          </div>
        </div>
        <hr class="form-sep" />
        <div class="form-actions">
          <button type="submit" class="btn-primary">Cadastrar Paciente</button>
        </div>
      </form>
```

Replace with:

```html
      <form id="cadastrarPacienteForm" class="card form-card">
        <h3>Informações</h3>
        <div class="form-split">
          <div class="form-avatar-col">
            <div class="form-avatar" aria-hidden="true"></div>
            <button type="button" class="btn-outline-brand">Alterar foto</button>
          </div>
          <div>
            <div class="field field-id">
              <label for="cpf">CPF</label>
              <input id="cpf" type="text" required />
            </div>
            <hr class="form-sep" />
            <div class="two-col">
              <div class="field">
                <label for="nome">Nome</label>
                <input id="nome" type="text" placeholder="Ex: Ana Carolina Silva" required />
              </div>
              <div class="field">
                <label for="nasc">Data de nascimento</label>
                <input id="nasc" type="date" />
              </div>
            </div>
            <div class="two-col">
              <div class="field">
                <label for="sexo">Sexo</label>
                <select id="sexo">
                  <option value="" selected>Selecione</option>
                  <option>Feminino</option>
                  <option>Masculino</option>
                  <option>Outro</option>
                </select>
              </div>
              <div class="field">
                <label for="situacao">Situação</label>
                <input id="situacao" type="text" placeholder="Ex: Lesão de manguito rotador" />
              </div>
            </div>
          </div>
        </div>
        <hr class="form-sep" />
        <div class="form-actions">
          <button type="submit" class="btn-primary">Cadastrar Paciente</button>
        </div>
      </form>
```

Then add the page script before `</body>`:

```html
  <script src="./script.js"></script>
  <script src="./pages/cadastrar-paciente.js"></script>
</body>
```

- [ ] **Step 2: Create `Software/pages/cadastrar-paciente.js`**

```javascript
(function cadastrarPaciente() {
  const form = document.getElementById("cadastrarPacienteForm");
  if (!form) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") {
    alert("Apenas um profissional logado pode cadastrar pacientes.");
    return;
  }

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const nome = document.getElementById("nome").value.trim();
    const cpf = document.getElementById("cpf").value.trim();
    const dataNascimento = document.getElementById("nasc").value || null;
    const sexo = document.getElementById("sexo").value || null;
    const situacao = document.getElementById("situacao").value.trim() || null;

    if (!nome || !cpf) {
      alert("Preencha nome e CPF.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Cadastrando...";

    try {
      const paciente = await apiPost("/pacientes", {
        nome,
        cpf,
        dataNascimento,
        sexo,
        situacao,
        idFisioterapeuta: sessao.id,
      });
      alert("Paciente cadastrado com sucesso.");
      window.location.href = `./paciente.html?id=${paciente.id}`;
    } catch (err) {
      alert(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
```

- [ ] **Step 3: Verify in a real browser**

Logged in as a fisioterapeuta, open `Software/cadastrar-paciente.html`, fill nome + CPF (+ optionally the rest), submit. Expected: redirects to `paciente.html?id=<newId>` (Task 15 makes that page render correctly; for now just confirm the URL and the `201` in devtools). Try submitting the same CPF twice — expect the server's "Este CPF já está cadastrado." alert. Repeat on `cadastrar-paciente-escuro.html`.

- [ ] **Step 4: Commit**

```bash
git add Software/cadastrar-paciente.html Software/cadastrar-paciente-escuro.html \
        Software/pages/cadastrar-paciente.js
git commit -m "feat(frontend): add CPF field and wire cadastrar-paciente.html to the API"
```

---

### Task 15: `paciente.html` — real detail, real charts, real session history

**Files:**
- Modify: `Software/paciente.html`
- Modify: `Software/paciente-escuro.html`
- Create: `Software/pages/paciente.js`

**Interfaces:**
- Consumes: `getSessao()`, `apiGet()` (Task 7); `GET /api/pacientes/{id}`, `GET /api/pacientes/{id}/sessoes` (Tasks 4–5); arrives via `?id=` from Task 9's patient list or Task 14's post-create redirect.
- Produces: `construirGraficoLinha(pontos)` / `construirGraficoBarras(pontos)` (each `pontos` is `[{rotulo, valor}]`) — small self-contained SVG-string builders, not reused elsewhere, so they live directly in this file rather than in the shared `script.js`.

- [ ] **Step 1: Clear hardcoded text, fix the "Sessões" tab, drop the leftover inline script**

In both `Software/paciente.html` and `Software/paciente-escuro.html`, find:

```html
      <header class="patient-header">
        <div class="avatar-lg" aria-hidden="true"></div>
        <div>
          <h1>Ana Carolina Silva</h1>
          <p class="patient-meta desktop-only">
            28 anos – Feminino – Lesão de Manguito Rotador<br/>
            Início do tratamento: <strong>15/03/2026</strong> – Fisioterapia <strong>Dr. Marcelo Silva</strong>
          </p>
          <p class="patient-meta mobile-only">
            28 anos – Feminino<br/>Lesão de Manguito Rotador
          </p>
        </div>
      </header>

      <div class="tabs">
        <button class="tab active" data-tab="overview">Visão Geral</button>
        <button class="tab" data-tab="sessions">Sessões</button>
      </div>

      <!-- Mobile info strip -->
      <div class="info-strip mobile-only">
        <div>
          <div class="k">Início do tratamento</div>
          <div class="v">15/03/2026</div>
        </div>
        <div>
          <div class="k">Profissional</div>
          <div class="v">Dr. Marcelo Silva</div>
        </div>
      </div>
```

Replace with:

```html
      <header class="patient-header">
        <div class="avatar-lg" aria-hidden="true"></div>
        <div>
          <h1></h1>
          <p class="patient-meta desktop-only"></p>
          <p class="patient-meta mobile-only"></p>
        </div>
      </header>

      <div class="tabs">
        <button class="tab active">Visão Geral</button>
        <a class="tab" href="./cadastrar-sessao.html">Sessões</a>
      </div>

      <!-- Mobile info strip -->
      <div class="info-strip mobile-only">
        <div>
          <div class="k">Início do tratamento</div>
          <div class="v"></div>
        </div>
        <div>
          <div class="k">Profissional</div>
          <div class="v"></div>
        </div>
      </div>
```

Next, find the two chart cards (this is the big block with the hand-drawn SVGs):

```html
      <section class="two-col">
        <!-- ROM chart -->
        <div class="card chart-card">
          <h3>Amplitude de Movimento</h3>
          <p class="sub">Evolução do ombro</p>
          <div class="big-num">135°</div>
          <div class="delta">+10° desde a Última Sessão</div>
          <svg class="chart-svg" viewBox="0 0 320 190" preserveAspectRatio="none" aria-hidden="true">
```

...continuing through the matching closing `</svg>\n        </div>` for the first card, then the second `<div class="card chart-card">` for "Sessões anteriores" through its own closing `</svg>\n        </div>\n      </section>`.

This block is long — rather than hand-editing the SVG internals, replace the *entire* `<section class="two-col">...</section>` element (everything from `<section class="two-col">` through its matching `</section>`, i.e. both chart cards and their SVGs) with:

```html
      <section class="two-col">
        <!-- ROM chart -->
        <div class="card chart-card">
          <h3>Amplitude de Movimento</h3>
          <p class="sub">Evolução do ombro</p>
          <div class="big-num">-</div>
          <div class="delta">Carregando…</div>
        </div>

        <!-- Duration chart -->
        <div class="card chart-card">
          <h3>Duração das sessões</h3>
          <p class="sub">Minutos por sessão</p>
          <div class="big-num">-</div>
          <div class="delta">Carregando…</div>
        </div>
      </section>
```

(`Software/pages/paciente.js`, Step 2, injects the real `<svg>` into each card via `insertAdjacentHTML` once the data has loaded — that's simpler and less error-prone than hand-splicing dynamic values into a huge static SVG.)

Finally, find the sessions table:

```html
      <h2 class="section-title desktop-only">Últimas sessões</h2>
      <section class="bottom-grid desktop-only">
        <div class="card sessions-table">
          <table>
            <thead>
              <tr>
                <th>Data</th>
                <th>Duração</th>
                <th>Amplitude média</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>07/05/2026</td>
                <td>45 min</td>
                <td>135°</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="card add-card">
          <h3>Adicionar dados manualmente</h3>
          <p>Registre os dados de uma sessão</p>
          <button class="btn-primary" data-action="add-session" type="button">Cadastrar sessão</button>
        </div>
      </section>
```

Replace with:

```html
      <h2 class="section-title desktop-only">Últimas sessões</h2>
      <section class="bottom-grid desktop-only">
        <div class="card sessions-table">
          <table>
            <thead>
              <tr>
                <th>Data</th>
                <th>Duração</th>
                <th>Amplitude média</th>
              </tr>
            </thead>
            <tbody></tbody>
          </table>
        </div>
        <div class="card add-card">
          <h3>Adicionar dados manualmente</h3>
          <p>Registre os dados de uma sessão</p>
          <button class="btn-primary" data-action="add-session" type="button">Cadastrar sessão</button>
        </div>
      </section>
```

Lastly, find the closing script block:

```html
  <script src="./script.js"></script>
  <script>
    document.querySelectorAll('.tab').forEach(t => t.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach(x => x.classList.remove('active'));
      t.classList.add('active');
    }));
  </script>
</body>
</html>
```

Replace with:

```html
  <script src="./script.js"></script>
  <script src="./pages/paciente.js"></script>
</body>
</html>
```

- [ ] **Step 2: Create `Software/pages/paciente.js`**

```javascript
function construirGraficoLinha(pontos) {
  if (pontos.length === 0) {
    return '<p style="color:var(--ink-muted);font-size:13px;margin-top:8px;">Ainda não há sessões registradas.</p>';
  }
  const valores = pontos.map((p) => p.valor);
  const max = Math.max(...valores);
  const min = Math.min(...valores);
  const amplitude = max - min || 1;
  const esquerda = 50, direita = 300, topo = 14, base = 162;
  const passoX = pontos.length > 1 ? (direita - esquerda) / (pontos.length - 1) : 0;

  const coords = pontos.map((p, i) => ({
    x: esquerda + passoX * i,
    y: base - ((p.valor - min) / amplitude) * (base - topo),
    rotulo: p.rotulo,
  }));

  const linha = coords.map((c) => `${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(" ");
  const pontosSvg = coords.map((c) => `<circle cx="${c.x.toFixed(1)}" cy="${c.y.toFixed(1)}" r="3.5"/>`).join("");
  const labels = coords
    .map((c) => `<text x="${c.x.toFixed(1)}" y="180" text-anchor="middle">${c.rotulo}</text>`)
    .join("");

  return `
    <svg class="chart-svg" viewBox="0 0 320 190" preserveAspectRatio="none" aria-hidden="true">
      <polyline points="${linha}" fill="none" stroke="#1565D8" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
      <g fill="#1565D8">${pontosSvg}</g>
      <g font-family="Inter, sans-serif" font-size="10" fill="#5F6C7B" text-anchor="middle">${labels}</g>
    </svg>`;
}

function construirGraficoBarras(pontos) {
  if (pontos.length === 0) {
    return '<p style="color:var(--ink-muted);font-size:13px;margin-top:8px;">Ainda não há sessões registradas.</p>';
  }
  const max = Math.max(...pontos.map((p) => p.valor), 1);
  const topo = 20, base = 162, larguraBarra = 36, espaco = 50, inicioX = 52;

  const barras = pontos
    .map((p, i) => {
      const x = inicioX + i * espaco;
      const alturaBarra = (p.valor / max) * (base - topo);
      const y = base - alturaBarra;
      return `<rect x="${x}" y="${y.toFixed(1)}" width="${larguraBarra}" height="${alturaBarra.toFixed(1)}" rx="4" fill="#1565D8"/>`;
    })
    .join("");
  const labels = pontos
    .map((p, i) => {
      const x = inicioX + i * espaco + larguraBarra / 2;
      return `<text x="${x}" y="180" text-anchor="middle">${p.rotulo}</text>`;
    })
    .join("");

  return `
    <svg class="chart-svg" viewBox="0 0 320 190" preserveAspectRatio="none" aria-hidden="true">
      <g>${barras}</g>
      <g font-family="Inter, sans-serif" font-size="10" fill="#5F6C7B" text-anchor="middle">${labels}</g>
    </svg>`;
}

function formatarDataCurta(dataIso) {
  const [, mes, dia] = dataIso.split("-");
  return `${dia}/${mes}`;
}

function formatarDataLonga(dataIso) {
  const [ano, mes, dia] = dataIso.split("-");
  return `${dia}/${mes}/${ano}`;
}

(function carregarPaciente() {
  const header = document.querySelector(".patient-header");
  if (!header) return;

  const sessao = getSessao();
  if (!sessao) return;

  const params = new URLSearchParams(window.location.search);
  const idPaciente = params.get("id");
  if (!idPaciente) {
    header.querySelector("h1").textContent = "Paciente não informado.";
    return;
  }

  document.querySelectorAll('.tabs a.tab[href^="./cadastrar-sessao.html"]').forEach((a) => {
    a.href = `./cadastrar-sessao.html?id=${idPaciente}`;
  });
  const addSessionBtn = document.querySelector('[data-action="add-session"]');
  if (addSessionBtn) {
    addSessionBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      window.location.href = `./cadastrar-sessao.html?id=${idPaciente}`;
    });
  }

  Promise.all([apiGet(`/pacientes/${idPaciente}`), apiGet(`/pacientes/${idPaciente}/sessoes`)])
    .then(([paciente, sessoes]) => {
      const idadeTexto = paciente.idade != null ? `${paciente.idade} anos` : "Idade não informada";
      const sexoTexto = paciente.sexo || "Não informado";
      const situacaoTexto = paciente.situacao || "Sem situação registrada";
      const inicioTexto = paciente.dataInicioTratamento ? formatarDataLonga(paciente.dataInicioTratamento) : "-";
      const fisioTexto = paciente.nomeFisioterapeuta || "-";

      header.querySelector("h1").textContent = paciente.nome;
      header.querySelector(".patient-meta.desktop-only").innerHTML =
        `${idadeTexto} – ${sexoTexto} – ${situacaoTexto}<br/>` +
        `Início do tratamento: <strong>${inicioTexto}</strong> – Fisioterapia <strong>${fisioTexto}</strong>`;
      header.querySelector(".patient-meta.mobile-only").innerHTML = `${idadeTexto} – ${sexoTexto}<br/>${situacaoTexto}`;

      const infoValores = document.querySelectorAll(".info-strip .v");
      if (infoValores[0]) infoValores[0].textContent = inicioTexto;
      if (infoValores[1]) infoValores[1].textContent = fisioTexto;

      const cronologicas = sessoes.slice(0, 5).reverse();
      const pontosAmplitude = cronologicas
        .filter((s) => s.amplitudeMedia != null)
        .map((s) => ({ rotulo: formatarDataCurta(s.data), valor: Number(s.amplitudeMedia) }));
      const pontosDuracao = cronologicas
        .filter((s) => s.duracao != null)
        .map((s) => ({ rotulo: formatarDataCurta(s.data), valor: s.duracao }));

      const cartoes = document.querySelectorAll(".chart-card");
      if (cartoes[0]) {
        const ultima = pontosAmplitude.length ? pontosAmplitude[pontosAmplitude.length - 1].valor : null;
        const anterior = pontosAmplitude.length > 1 ? pontosAmplitude[pontosAmplitude.length - 2].valor : null;
        cartoes[0].querySelector(".big-num").textContent = ultima != null ? `${ultima}°` : "-";
        cartoes[0].querySelector(".delta").textContent =
          ultima != null && anterior != null
            ? `${ultima - anterior >= 0 ? "+" : ""}${(ultima - anterior).toFixed(0)}° desde a última sessão`
            : "Sem histórico suficiente";
        cartoes[0].insertAdjacentHTML("beforeend", construirGraficoLinha(pontosAmplitude));
      }
      if (cartoes[1]) {
        const ultima = pontosDuracao.length ? pontosDuracao[pontosDuracao.length - 1].valor : null;
        cartoes[1].querySelector(".big-num").textContent = ultima != null ? `${ultima} min` : "-";
        cartoes[1].querySelector(".delta").textContent = pontosDuracao.length
          ? `${pontosDuracao.length} sessões recentes`
          : "Sem histórico ainda";
        cartoes[1].insertAdjacentHTML("beforeend", construirGraficoBarras(pontosDuracao));
      }

      const tbody = document.querySelector(".sessions-table tbody");
      if (tbody) {
        tbody.innerHTML = sessoes.length
          ? sessoes
              .map(
                (s) => `
            <tr>
              <td>${formatarDataLonga(s.data)}</td>
              <td>${s.duracao != null ? s.duracao + " min" : "-"}</td>
              <td>${s.amplitudeMedia != null ? s.amplitudeMedia + "°" : "-"}</td>
            </tr>`
              )
              .join("")
          : '<tr><td colspan="3">Ainda não há sessões registradas.</td></tr>';
      }
    })
    .catch((err) => {
      header.querySelector("h1").textContent = err.message;
    });
})();
```

- [ ] **Step 3: Verify in a real browser**

From `profissional.html`, click into a patient with no sessions yet. Expected: real name/age/sex/situação/início/fisioterapeuta in the header; both chart cards show "Ainda não há sessões registradas." instead of a broken graph; the sessions table shows the same message; "Sessões" tab and "Cadastrar sessão" button both link to `cadastrar-sessao.html?id=<mesmoId>` (check the address bar after clicking). Register 2–3 sessions for this patient via Task 16's screen, then reload `paciente.html?id=...` — expect both charts to render real polylines/bars and the table to list them, most recent first. Repeat on `paciente-escuro.html`.

- [ ] **Step 4: Commit**

```bash
git add Software/paciente.html Software/paciente-escuro.html Software/pages/paciente.js
git commit -m "feat(frontend): wire paciente.html to real detail, charts and session history"
```

---

### Task 16: `cadastrar-sessao.html` — real submit, real session history, computed "Avanço"

**Files:**
- Modify: `Software/cadastrar-sessao.html`
- Modify: `Software/cadastrar-sessao-escuro.html`
- Create: `Software/pages/cadastrar-sessao.js`

**Interfaces:**
- Consumes: `getSessao()`, `apiGet()`, `apiPost()` (Task 7); `POST`/`GET /api/pacientes/{id}/sessoes`, `GET /api/pacientes/{id}` (Task 5); arrives via `?id=` (Task 15's tab link).

- [ ] **Step 1: Clear hardcoded text, empty the table, switch inputs to `date`/`number`**

In both `Software/cadastrar-sessao.html` and `Software/cadastrar-sessao-escuro.html`, find:

```html
      <header class="patient-header">
        <div class="avatar-lg" aria-hidden="true"></div>
        <div>
          <h1>Ana Carolina Silva</h1>
          <p class="patient-meta desktop-only">
            28 anos – Feminino – Lesão de Manguito Rotador<br/>
            Início do tratamento: <strong>15/03/2026</strong> – Fisioterapia <strong>Dr. Marcelo Silva</strong>
          </p>
          <p class="patient-meta mobile-only">
            28 anos – Feminino<br/>Lesão de Manguito Rotador
          </p>
        </div>
      </header>

      <div class="tabs">
        <a class="tab" href="./paciente.html">Visão Geral</a>
        <button class="tab active">Sessões</button>
      </div>

      <div class="info-strip mobile-only">
        <div>
          <div class="k">Início do tratamento</div>
          <div class="v">15/03/2026</div>
        </div>
        <div>
          <div class="k">Profissional</div>
          <div class="v">Dr. Marcelo Silva</div>
        </div>
      </div>

      <section class="session-grid">
        <div class="card sessions-table">
          <table>
            <thead>
              <tr>
                <th>Data</th>
                <th>Duração</th>
                <th><span class="desktop-only">Amplitude média</span><span class="mobile-only">Amplitude</span></th>
                <th>Avanço</th>
              </tr>
            </thead>
            <tbody>
                <tr>
                  <td><span class="desktop-only">07/05/2026</span><span class="mobile-only">07/05</span></td>
                  <td>45 min</td>
                  <td>135°</td>
                  <td class="pos">+10°</td>
                </tr>
                <tr>
                  <td><span class="desktop-only">30/04/2026</span><span class="mobile-only">30/04</span></td>
                  <td>40 min</td>
                  <td>125°</td>
                  <td class="pos">+13°</td>
                </tr>
                <tr>
                  <td><span class="desktop-only">23/04/2026</span><span class="mobile-only">23/04</span></td>
                  <td>42 min</td>
                  <td>112°</td>
                  <td class="pos">+16°</td>
                </tr>
                <tr>
                  <td><span class="desktop-only">16/04/2026</span><span class="mobile-only">16/04</span></td>
                  <td>35 min</td>
                  <td>96°</td>
                  <td class="pos">+6°</td>
                </tr>
                <tr>
                  <td><span class="desktop-only">09/04/2026</span><span class="mobile-only">09/04</span></td>
                  <td>40 min</td>
                  <td>90°</td>
                  <td class="">0°</td>
                </tr>
                <tr class="empty desktop-only"><td></td><td></td><td></td><td></td></tr>
                <tr class="empty desktop-only"><td></td><td></td><td></td><td></td></tr>
                <tr class="empty desktop-only"><td></td><td></td><td></td><td></td></tr>
                <tr class="empty desktop-only"><td></td><td></td><td></td><td></td></tr>
                <tr class="empty desktop-only"><td></td><td></td><td></td><td></td></tr>
                <tr class="empty desktop-only"><td></td><td></td><td></td><td></td></tr>
            </tbody>
          </table>
        </div>

        <form class="card add-manual-card" onsubmit="event.preventDefault();">
          <h3>Adicionar dados manualmente</h3>
          <p class="sub">Registre os dados de uma sessão</p>
          <div class="field">
            <label for="s-data">Data</label>
            <input id="s-data" type="text" />
          </div>
          <div class="field">
            <label for="s-dur">Duração</label>
            <input id="s-dur" type="text" />
          </div>
          <div class="field">
            <label for="s-amp">Amplitude média</label>
            <input id="s-amp" type="text" />
          </div>
          <div class="form-actions">
            <button type="submit" class="btn-primary">Cadastrar sessão</button>
          </div>
        </form>
      </section>
```

Replace with:

```html
      <header class="patient-header">
        <div class="avatar-lg" aria-hidden="true"></div>
        <div>
          <h1></h1>
          <p class="patient-meta desktop-only"></p>
          <p class="patient-meta mobile-only"></p>
        </div>
      </header>

      <div class="tabs">
        <a class="tab" href="./paciente.html">Visão Geral</a>
        <button class="tab active">Sessões</button>
      </div>

      <div class="info-strip mobile-only">
        <div>
          <div class="k">Início do tratamento</div>
          <div class="v"></div>
        </div>
        <div>
          <div class="k">Profissional</div>
          <div class="v"></div>
        </div>
      </div>

      <section class="session-grid">
        <div class="card sessions-table">
          <table>
            <thead>
              <tr>
                <th>Data</th>
                <th>Duração</th>
                <th><span class="desktop-only">Amplitude média</span><span class="mobile-only">Amplitude</span></th>
                <th>Avanço</th>
              </tr>
            </thead>
            <tbody></tbody>
          </table>
        </div>

        <form id="cadastrarSessaoForm" class="card add-manual-card">
          <h3>Adicionar dados manualmente</h3>
          <p class="sub">Registre os dados de uma sessão</p>
          <div class="field">
            <label for="s-data">Data</label>
            <input id="s-data" type="date" required />
          </div>
          <div class="field">
            <label for="s-dur">Duração (minutos)</label>
            <input id="s-dur" type="number" min="1" required />
          </div>
          <div class="field">
            <label for="s-amp">Amplitude média (graus)</label>
            <input id="s-amp" type="number" step="0.01" />
          </div>
          <div class="form-actions">
            <button type="submit" class="btn-primary">Cadastrar sessão</button>
          </div>
        </form>
      </section>
```

Then add the page script before `</body>`:

```html
  <script src="./script.js"></script>
  <script src="./pages/cadastrar-sessao.js"></script>
</body>
```

- [ ] **Step 2: Create `Software/pages/cadastrar-sessao.js`**

```javascript
(function carregarCadastrarSessao() {
  const header = document.querySelector(".patient-header");
  if (!header) return;

  const sessao = getSessao();
  if (!sessao || sessao.tipo !== "FISIOTERAPEUTA") return;

  const params = new URLSearchParams(window.location.search);
  const idPaciente = params.get("id");
  if (!idPaciente) {
    header.querySelector("h1").textContent = "Paciente não informado.";
    return;
  }

  document.querySelectorAll('.tabs a.tab[href^="./paciente.html"]').forEach((a) => {
    a.href = `./paciente.html?id=${idPaciente}`;
  });

  function formatarDataLonga(dataIso) {
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }
  function formatarDataCurta(dataIso) {
    const [, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}`;
  }

  function carregarPacienteEHistorico() {
    return Promise.all([apiGet(`/pacientes/${idPaciente}`), apiGet(`/pacientes/${idPaciente}/sessoes`)]).then(
      ([paciente, sessoes]) => {
        const idadeTexto = paciente.idade != null ? `${paciente.idade} anos` : "Idade não informada";
        const sexoTexto = paciente.sexo || "Não informado";
        const situacaoTexto = paciente.situacao || "Sem situação registrada";
        const inicioTexto = paciente.dataInicioTratamento ? formatarDataLonga(paciente.dataInicioTratamento) : "-";
        const fisioTexto = paciente.nomeFisioterapeuta || "-";

        header.querySelector("h1").textContent = paciente.nome;
        header.querySelector(".patient-meta.desktop-only").innerHTML =
          `${idadeTexto} – ${sexoTexto} – ${situacaoTexto}<br/>` +
          `Início do tratamento: <strong>${inicioTexto}</strong> – Fisioterapia <strong>${fisioTexto}</strong>`;
        header.querySelector(".patient-meta.mobile-only").innerHTML = `${idadeTexto} – ${sexoTexto}<br/>${situacaoTexto}`;

        const infoValores = document.querySelectorAll(".info-strip .v");
        if (infoValores[0]) infoValores[0].textContent = inicioTexto;
        if (infoValores[1]) infoValores[1].textContent = fisioTexto;

        const tbody = document.querySelector(".sessions-table tbody");
        if (!tbody) return;
        if (sessoes.length === 0) {
          tbody.innerHTML = '<tr><td colspan="4">Ainda não há sessões registradas.</td></tr>';
          return;
        }
        tbody.innerHTML = sessoes
          .map((s, i) => {
            const anterior = sessoes[i + 1];
            let avancoTexto = "0°";
            let avancoClasse = "";
            if (anterior && anterior.amplitudeMedia != null && s.amplitudeMedia != null) {
              const diferenca = Number(s.amplitudeMedia) - Number(anterior.amplitudeMedia);
              avancoTexto = `${diferenca >= 0 ? "+" : ""}${diferenca.toFixed(0)}°`;
              if (diferenca > 0) avancoClasse = "pos";
            }
            return `
              <tr>
                <td><span class="desktop-only">${formatarDataLonga(s.data)}</span><span class="mobile-only">${formatarDataCurta(s.data)}</span></td>
                <td>${s.duracao != null ? s.duracao + " min" : "-"}</td>
                <td>${s.amplitudeMedia != null ? s.amplitudeMedia + "°" : "-"}</td>
                <td class="${avancoClasse}">${avancoTexto}</td>
              </tr>`;
          })
          .join("");
      }
    );
  }

  carregarPacienteEHistorico().catch((err) => {
    header.querySelector("h1").textContent = err.message;
  });

  const form = document.getElementById("cadastrarSessaoForm");
  if (!form) return;
  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const data = document.getElementById("s-data").value;
    const duracao = document.getElementById("s-dur").value;
    const amplitude = document.getElementById("s-amp").value;

    if (!data || !duracao) {
      alert("Preencha ao menos a data e a duração.");
      return;
    }

    const submitBtn = form.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Salvando...";

    try {
      await apiPost(`/pacientes/${idPaciente}/sessoes`, {
        data,
        duracao: Number(duracao),
        amplitudeMedia: amplitude ? Number(amplitude) : null,
        idFisioterapeuta: sessao.id,
      });
      form.reset();
      await carregarPacienteEHistorico();
      alert("Sessão cadastrada com sucesso.");
    } catch (err) {
      alert(err.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
})();
```

- [ ] **Step 3: Verify in a real browser**

From a patient's `paciente.html`, click "Sessões" (or "Cadastrar sessão"). Expected: header/tabs carry the same patient; table starts with "Ainda não há sessões registradas." if empty. Fill Data/Duração/Amplitude and submit — expect a success alert, the form to clear, and the new row to appear in the table immediately (no reload). Add a second session with a different amplitude — expect the "Avanço" column on the newer row to show a signed `°` delta vs. the row before it. Navigate back to `paciente.html?id=...` — expect the charts (Task 15) to now render real points. Repeat on `cadastrar-sessao-escuro.html`.

- [ ] **Step 4: Commit**

```bash
git add Software/cadastrar-sessao.html Software/cadastrar-sessao-escuro.html \
        Software/pages/cadastrar-sessao.js
git commit -m "feat(frontend): wire cadastrar-sessao.html to real submit and history"
```

---

### Task 17: `dispositivo.html` — real goniômetro reading, simulated sync

**Files:**
- Modify: `Software/dispositivo.html`
- Modify: `Software/dispositivo-escuro.html`
- Create: `Software/pages/dispositivo.js`

**Interfaces:**
- Consumes: `getSessao()`, `apiGet()`, `apiPost()` (Task 7); `GET`/`POST /api/goniometro` (Task 6); `GET /api/fisioterapeutas/{id}` (Task 3, to resolve a fisioterapeuta's `idClinica` — the session object only carries it directly for `CLINICA` logins).

Serial number ("XXXX-XXXX") stays as static placeholder text — `tb04_goniometro` has no serial-number column in the schema, so there's nothing real to put there. Only battery and last-sync time come from the database; "Desconectar" stays a no-op (no real device to disconnect from), as decided with the user.

- [ ] **Step 1: Add the page script tag to both HTML files**

In `Software/dispositivo.html` and `Software/dispositivo-escuro.html`, find:

```html
  <script src="./script.js"></script>
</body>
```

Replace with:

```html
  <script src="./script.js"></script>
  <script src="./pages/dispositivo.js"></script>
</body>
```

No other HTML edits are needed — every dynamic value (`.battery-num`, `.battery-fill`, the "Última sincronização"/"Última Sincronização" rows, `.device-name`) is located and updated by class/label at runtime in Step 2, so the static markup (including its placeholder `78%`/`XXXX-XXXX`/date, which are overwritten immediately on load) can stay as-is.

- [ ] **Step 2: Create `Software/pages/dispositivo.js`**

```javascript
(function carregarDispositivo() {
  const temCartaoDispositivo = document.querySelector(".device-card") || document.querySelector(".device-card-mobile");
  if (!temCartaoDispositivo) return;

  const sessao = getSessao();
  if (!sessao) return;

  function formatarData(dataIso) {
    const [ano, mes, dia] = dataIso.split("-");
    return `${dia}/${mes}/${ano}`;
  }

  function definirValorPorRotulo(rotulo, valor) {
    document.querySelectorAll(".info-row").forEach((row) => {
      const k = row.querySelector(".k");
      if (k && k.textContent.trim().toLowerCase().startsWith(rotulo)) {
        const v = row.querySelector(".v");
        if (v && !v.classList.contains("battery")) v.textContent = valor;
      }
    });
  }

  function aplicar(goniometro) {
    const bateria = goniometro.bateria != null ? goniometro.bateria : 0;
    const sincronizacao =
      goniometro.dataSincronizacao && goniometro.horaSincronizacao
        ? `${formatarData(goniometro.dataSincronizacao)} ${goniometro.horaSincronizacao}`
        : "-";
    document.querySelectorAll(".battery-num").forEach((el) => (el.textContent = `${bateria}%`));
    document.querySelectorAll(".battery-fill").forEach((el) => (el.style.width = `${bateria}%`));
    definirValorPorRotulo("última sincronização", sincronizacao);
    document.querySelectorAll(".device-name").forEach((el) => (el.textContent = "Dispositivo conectado"));
  }

  function semDados() {
    document.querySelectorAll(".device-name").forEach(
      (el) => (el.textContent = "Nenhum dispositivo sincronizado ainda")
    );
    document.querySelectorAll(".battery-num").forEach((el) => (el.textContent = "-"));
    document.querySelectorAll(".battery-fill").forEach((el) => (el.style.width = "0%"));
    definirValorPorRotulo("última sincronização", "-");
  }

  const idClinicaPromise =
    sessao.tipo === "CLINICA"
      ? Promise.resolve(sessao.id)
      : apiGet(`/fisioterapeutas/${sessao.id}`).then((f) => f.idClinica);

  idClinicaPromise.then((idClinica) => {
    apiGet(`/goniometro?idClinica=${idClinica}`).then(aplicar).catch(semDados);

    document.querySelectorAll('[data-action="sync"]').forEach((btn) => {
      btn.addEventListener("click", async () => {
        btn.disabled = true;
        try {
          const goniometro = await apiPost("/goniometro/sincronizar", { idClinica });
          aplicar(goniometro);
        } catch (err) {
          alert(err.message);
        } finally {
          btn.disabled = false;
        }
      });
    });
  });
})();
```

- [ ] **Step 3: Verify in a real browser**

Logged in as a clinic with no goniômetro row yet, open `Software/dispositivo.html`. Expected: "Nenhum dispositivo sincronizado ainda", battery shows "-". Click "Sincronizar" — expect the battery percentage and "Última sincronização" to update immediately to a random 60–100% and the current date/time, and "Dispositivo conectado" to appear. Reload the page — expect it to show that same synced reading (not reset). Log in as a fisioterapeuta belonging to that clinic and open the same page — expect it to show the same clinic-wide reading. Repeat on `dispositivo-escuro.html`.

- [ ] **Step 4: Commit**

```bash
git add Software/dispositivo.html Software/dispositivo-escuro.html Software/pages/dispositivo.js
git commit -m "feat(frontend): wire dispositivo.html to real goniometro data"
```

---

## Self-Review

**Spec coverage:** every bullet in the design doc's "Telas e o que muda" section maps to a task — `instituicao.html` → Task 8, `profissional.html` → Task 9, `cadastrar-paciente.html` → Task 14, `paciente.html` → Task 15, `cadastrar-sessao.html` → Task 16, `perfil-instituicao.html`/`perfil-profissional.html` → Tasks 10–11 (including the mid-plan addition of the institution's own stat cards), `editar-perfil-*` → Tasks 12–13, `dispositivo.html` → Task 17. The "Identificação de paciente na URL" requirement is covered across Tasks 9, 15, 16 (list produces the `id`, both patient-scoped screens consume and re-propagate it). `configuracoes.html` and `loader.html` are explicitly out of scope per the spec and untouched by this plan.

**Placeholder scan:** no task contains "TBD"/"add error handling"/"similar to Task N" — every step has literal, complete code. The one recurring exception by design is the DTO getter/setter boilerplate, which is mechanical and identical to the existing `Clinica`/`Fisioterapeuta` pattern already in the codebase.

**Type/name consistency check performed:** `PacienteController` is created in Task 4 and modified (not recreated) in Task 5 — confirmed the Task 5 diff matches Task 4's exact produced class shape. `FisioterapeutaService`'s constructor signature changes in Task 3; confirmed no other task references the old 3-argument constructor. `SessaoRepository`/`MedicaoRepository`/`PacienteRepository` method names introduced in Task 1 (`findByIdFisioterapeutaIn`, `findByIdFisioterapeuta`, `countByIdClinica`, `countByIdFisioterapeutaAndStatus`, `findByIdSessao`) are each consumed later with matching names and argument types in Tasks 2–6. Frontend: `apiGet`/`apiPost`/`apiPut`/`urlFoto` (Task 7) are called with matching signatures in every subsequent frontend task; every page script guards on a page-specific selector (e.g. `.fisio-list`, `#cadastrarPacienteForm`) before doing anything, so accidentally loading the wrong page script is inert rather than throwing.

## Execution Handoff

Two backend tracks can run in parallel after Task 1 lands (Task 2 and Task 3 touch different files; Task 4 must land before Task 5 since Task 5 modifies the controller Task 4 creates; Task 6 is fully independent of 2–5). All frontend tasks (8–17) depend on Task 7 and their own specific backend task(s), but are otherwise independent of each other and safe to parallelize once their backend dependency is done — except Task 16 depends on Task 15 having established the `.tabs`/`?id=` convention (read Task 15 first even if implementing Task 16 in a separate session).


---
