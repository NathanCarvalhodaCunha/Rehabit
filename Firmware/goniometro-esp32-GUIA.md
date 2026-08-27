# Guia: gravar e conectar o goniômetro (ESP32 + MPU6050)

O firmware é gravado **uma única vez**. Depois disso, quem for usar o
aparelho configura tudo pelo celular — Wi-Fi e vínculo com a clínica — sem
precisar de computador nem da Arduino IDE.

---

# Parte 1 — Gravar o firmware (só uma vez, feito por você)

## 1. Instalar suporte à placa ESP32 na Arduino IDE

1. Abra a Arduino IDE → **Arquivo → Preferências**.
2. Em "URLs adicionais para Gerenciadores de Placas", cole:
   `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
3. **Ferramentas → Placa → Gerenciador de Placas**, busque "esp32" (autor
   Espressif Systems), clique em Instalar.
4. **Ferramentas → Placa**, escolha o modelo da sua placa (geralmente
   "ESP32 Dev Module" se não souber o nome exato).

## 2. Instalar as bibliotecas

**Ferramentas → Gerenciar Bibliotecas**, e instale as duas:

- **Adafruit MPU6050** — vai pedir para instalar junto "Adafruit Unified
  Sensor" e "Adafruit BusIO"; aceite, são dependências.
- **WiFiManager** (de *tzapu*) — é ela que cria o portal de configuração no
  celular.

## 3. Montar o sensor

MPU6050 → ESP32: `VCC → 3.3V`, `GND → GND`, `SDA → GPIO21`, `SCL → GPIO22`.
São os pinos padrão de I2C na maioria das placas DevKit; confira a
serigrafia da sua se for diferente.

## 4. Selecionar a porta e gravar

1. Conecte o ESP32 no PC via USB.
2. **Ferramentas → Porta**, escolha a porta COM que apareceu ao conectar o
   cabo (no Gerenciador de Dispositivos do Windows costuma aparecer como
   "Silicon Labs CP210x" ou "CH340").
3. Abra `goniometro-esp32.ino` e clique em **Carregar** (a seta →).

> **Não é preciso editar nada no código.** Não há senha de Wi-Fi nem senha
> de conta ali dentro — é justamente essa a diferença: essas informações
> entram pelo celular, na Parte 2.
>
> A Arduino IDE exige que o `.ino` fique dentro de uma pasta com o mesmo
> nome (`goniometro-esp32/goniometro-esp32.ino`). Ao abrir o arquivo, ela se
> oferece para criar essa pasta — aceite. Essa pasta de trabalho não vai
> para o GitHub (está no `.gitignore`).

---

# Parte 2 — Conectar o aparelho (qualquer pessoa, pelo celular)

## 1. Pegar o código na tela do Rehabit

1. Entre no Rehabit **com a conta da clínica**.
2. Vá em **Dispositivo** → botão **Parear novo dispositivo**.
3. Aparece um código de 6 dígitos. Ele vale **10 minutos** e serve **uma
   única vez** — pedir outro invalida o anterior.

## 2. Configurar o goniômetro

1. Ligue o goniômetro. Sem configuração, ele cria uma rede Wi-Fi chamada
   **`Rehabit-Goniometro`** (senha **`rehabit123`**).
2. No celular, conecte-se a essa rede. Um portal abre sozinho — se não
   abrir, acesse `192.168.4.1` no navegador.
3. No portal: escolha a rede Wi-Fi da clínica, digite a senha dela e o
   **código de 6 dígitos** no campo indicado.
4. Salve. O aparelho grava tudo na memória interna e reinicia já conectado.

Pronto. Na tela **Dispositivo**, o aparelho aparece como **Online** e o
ângulo passa a atualizar sozinho a cada ~2 segundos.

## 3. Reconfigurar depois (trocou de Wi-Fi ou de clínica)

Com o aparelho ligado, **segure o botão BOOT por 5 segundos**. Ele apaga a
configuração e volta a criar a rede `Rehabit-Goniometro`. Repita a Parte 2.

## 4. Se um aparelho sumir ou for roubado

Na tela Dispositivo, clique em **Revogar**. O acesso é cortado na hora, sem
precisar trocar a senha da clínica. A senha da clínica nunca fica gravada no
aparelho — ele guarda só um token próprio, que a revogação invalida.

---

# Verificar que está funcionando

Com o cabo USB ligado, abra o **Monitor Serial** (Ferramentas → Monitor
Serial), velocidade **115200**. Você deve ver, em ordem:

1. `MPU6050 encontrado.`
2. `Sem token: use o portal para parear.` (ou `Token encontrado na memoria.`)
3. `Wi-Fi conectado, IP: ...`
4. `Pareado com a clinica "..."` — só na primeira vez
5. A cada ~2s: `Angulo lido: X.XX graus` e `Leitura enviada: X.XX graus`

## Problemas comuns

| O que aparece | O que fazer |
|---|---|
| `MPU6050 nao encontrado!` | Fiação: confira VCC/GND/SDA/SCL. |
| `Pareamento falhou (status=400)` | Código expirado ou já usado — gere outro na tela Dispositivo. |
| `Este aparelho foi revogado pela clinica` | Alguém clicou em Revogar. Pareie de novo (BOOT 5s). |
| `Token recusado` (401) | Configuração antiga. Segure BOOT por 5s e refaça. |
| `Envio falhou, status=-1` | Problema de rede/TLS, não da aplicação. Confira o sinal do Wi-Fi e se o aparelho tem internet de verdade. |
| Demora na primeira leitura | Normal: o servidor gratuito hiberna e leva alguns segundos para acordar. |
| Ângulo sempre negativo | `atan2` pode dar negativo conforme a montagem do sensor. Monte o MPU do outro lado ou use valor absoluto em `lerAngulo()`. |

**Se algo não bater com o esperado, copie exatamente o que apareceu no
Monitor Serial — é a única forma de diagnosticar.**

---

# Erros de compilação conhecidos

**`variable or field 'fazerLogin' declared void`**

Erro da versão antiga do firmware. A Arduino IDE cria sozinha os protótipos
das funções e os coloca no topo do arquivo, antes das suas declarações — uma
função que recebesse um `struct` próprio gerava um protótipo citando um tipo
que ainda não existia ali.

A versão atual não tem mais esse problema: as funções recebem só tipos
nativos. Se você editar o código e voltar a passar um `struct` como
parâmetro, o erro reaparece.
