// Rehabit — goniômetro digital (ESP32 + MPU6050)
// Firmware 2.0
//
// O que este firmware faz:
//   * lê o MPU6050 a 100 Hz e calcula o ângulo da articulação com um filtro
//     complementar (acelerômetro + giroscópio), que não treme como o
//     acelerômetro sozinho nem escorrega como o giroscópio sozinho;
//   * calibra o giroscópio no boot e guarda a tara (o "zero" do aparelho) na
//     memória não volátil, então ela sobrevive a desligar e ligar;
//   * manda telemetria (ângulo, bateria, sinal, série, firmware) para um ou
//     dois servidores Rehabit e obedece aos comandos que voltam na resposta
//     — tarar, identificar, iniciar/parar captura, reiniciar;
//   * acelera para 10 amostras por segundo quando alguém está com a tela
//     aberta ou gravando, e desacelera quando ninguém está olhando.
//
// ================== ANTES DE GRAVAR ==================
//   1. Preencha WIFI_SSID / WIFI_SENHA.
//   2. Preencha e-mail e senha de uma conta de CLÍNICA já cadastrada no
//      Rehabit em cada ALVO que você for usar.
//   3. Confira o IP do alvo "local" — precisa ser o IP do PC que roda o
//      backend, na mesma rede Wi-Fi do ESP32.
//   4. Se for usar só a nuvem (ou só o local), ponha `false` no campo
//      `ativo` do alvo que não vai usar.
//   5. Monte o hardware conforme a tabela abaixo.
//
// ================== LIGAÇÕES ==================
//   MPU6050 VCC  -> 3V3
//   MPU6050 GND  -> GND
//   MPU6050 SDA  -> GPIO21
//   MPU6050 SCL  -> GPIO22
//   LED de status-> GPIO2 (o LED azul já soldado na maioria das DevKit)
//   Bateria      -> divisor 100k/100k -> GPIO34   (opcional; veja PINO_BATERIA)
//
// O MPU6050 deve ficar no segmento MÓVEL da articulação, com o eixo X do
// chip apontando ao longo do osso. Se o ângulo crescer no sentido errado,
// inverta EIXO_INVERTIDO abaixo em vez de remontar o sensor.

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Preferences.h>

// ------------------------------------------------------------------
// Configuração
// ------------------------------------------------------------------

const char *WIFI_SSID = "SEU_WIFI_AQUI";
const char *WIFI_SENHA = "SUA_SENHA_WIFI_AQUI";

const char *VERSAO_FIRMWARE = "2.0";

struct Alvo {
  const char *nome;
  const char *baseUrl;
  const char *email;
  const char *senha;
  bool ativo;

  // Preenchidos em tempo de execução — não mexa.
  String token;
  int idClinica;
  bool logado;
  unsigned long proximaTentativaLogin;
  unsigned long proximoEnvio;
  unsigned long intervaloMs;
  int falhasSeguidas;
};

Alvo ALVOS[] = {
  {"local", "http://192.168.1.10:8080/api", "SEU_EMAIL_AQUI", "SUA_SENHA_AQUI", true, "", 0, false, 0, 0, 2000, 0},
  {"nuvem", "https://rehabit-api-4tex.onrender.com/api", "SEU_EMAIL_AQUI", "SUA_SENHA_AQUI", true, "", 0, false, 0, 0, 2000, 0},
};
const int QTD_ALVOS = sizeof(ALVOS) / sizeof(ALVOS[0]);

// Pino do divisor de tensão da bateria. Ponha -1 se a placa é alimentada só
// por USB — aí o Rehabit simplesmente não mostra bateria, em vez de mostrar
// um número inventado.
const int PINO_BATERIA = 34;
const int PINO_LED = 2;

// Inverta se o ângulo aumentar quando deveria diminuir.
const bool EIXO_INVERTIDO = false;

// Peso do giroscópio no filtro complementar. Perto de 1 = mais suave e mais
// sujeito a deriva; perto de 0 = mais fiel à gravidade e mais trêmulo.
const float PESO_GIRO = 0.98f;

const unsigned long PERIODO_AMOSTRA_US = 10000;   // 100 Hz de leitura do sensor
const unsigned long ESPERA_LOGIN_MS = 15000;
const unsigned long TIMEOUT_HTTP_MS = 4000;
const int MAX_FALHAS_ANTES_DO_CASTIGO = 3;
const unsigned long CASTIGO_MS = 20000;           // alvo que só dá erro descansa

// ------------------------------------------------------------------
// Estado
// ------------------------------------------------------------------

Adafruit_MPU6050 mpu;
Preferences memoria;

WiFiClientSecure clienteSeguro;
WiFiClient clienteSimples;

float anguloFiltrado = 0.0f;
float offsetTara = 0.0f;
float biasGiroX = 0.0f, biasGiroY = 0.0f, biasGiroZ = 0.0f;
bool calibrado = false;
bool capturando = false;

unsigned long ultimaAmostraUs = 0;
unsigned long fimIdentificacao = 0;
unsigned long ultimoPiscaLed = 0;
bool ledAceso = false;

String numeroSerie;

// ------------------------------------------------------------------
// Apoio: JSON na unha
// ------------------------------------------------------------------
//
// As respostas da API são pequenas e de formato conhecido, então vale mais
// um leitor de 20 linhas do que puxar uma biblioteca de JSON inteira para
// dentro do firmware. Os dois leitores procuram a chave e leem o valor até
// o delimitador — sem depender da ORDEM dos campos nem de haver uma vírgula
// depois deles (era exatamente aí que a versão anterior quebrava).

String extrairTexto(const String &json, const char *chave) {
  String alvo = String("\"") + chave + "\":\"";
  int inicio = json.indexOf(alvo);
  if (inicio < 0) return "";
  inicio += alvo.length();
  String valor = "";
  for (int i = inicio; i < (int)json.length(); i++) {
    char c = json.charAt(i);
    if (c == '\\' && i + 1 < (int)json.length()) {
      valor += json.charAt(++i);  // escape: leva o próximo caractere como está
      continue;
    }
    if (c == '"') break;
    valor += c;
  }
  return valor;
}

long extrairNumero(const String &json, const char *chave, long padrao) {
  String alvo = String("\"") + chave + "\":";
  int inicio = json.indexOf(alvo);
  if (inicio < 0) return padrao;
  inicio += alvo.length();
  while (inicio < (int)json.length() && json.charAt(inicio) == ' ') inicio++;
  int fim = inicio;
  if (fim < (int)json.length() && json.charAt(fim) == '-') fim++;
  while (fim < (int)json.length() && isDigit(json.charAt(fim))) fim++;
  if (fim == inicio) return padrao;
  return json.substring(inicio, fim).toInt();
}

WiFiClient &clienteParaUrl(const char *url) {
  return String(url).startsWith("https://") ? (WiFiClient &)clienteSeguro : clienteSimples;
}

// ------------------------------------------------------------------
// Identidade e hardware
// ------------------------------------------------------------------

// Número de série derivado do MAC: é único por placa, não precisa ser
// cadastrado à mão e continua o mesmo depois de regravar o firmware.
String montarNumeroSerie() {
  uint8_t mac[6];
  WiFi.macAddress(mac);
  char buffer[10];
  snprintf(buffer, sizeof(buffer), "%02X%02X-%02X%02X", mac[2], mac[3], mac[4], mac[5]);
  return String(buffer);
}

int lerBateria() {
  if (PINO_BATERIA < 0) return -1;
  // Divisor 100k/100k: o pino vê metade da tensão da bateria.
  long soma = 0;
  for (int i = 0; i < 8; i++) soma += analogReadMilliVolts(PINO_BATERIA);
  float tensao = (soma / 8.0f) * 2.0f / 1000.0f;

  // Curva grosseira de LiPo de célula única. Não serve para medir carga com
  // precisão, serve para o profissional saber se dá para atender a tarde toda.
  if (tensao >= 4.15f) return 100;
  if (tensao <= 3.30f) return 0;
  if (tensao >= 3.85f) return (int)(80 + (tensao - 3.85f) / (4.15f - 3.85f) * 20);
  if (tensao >= 3.70f) return (int)(45 + (tensao - 3.70f) / (3.85f - 3.70f) * 35);
  return (int)((tensao - 3.30f) / (3.70f - 3.30f) * 45);
}

// ------------------------------------------------------------------
// Ângulo
// ------------------------------------------------------------------

void calibrarGiroscopio() {
  Serial.println("Calibrando o giroscopio — mantenha o aparelho PARADO...");
  const int amostras = 400;
  float somaX = 0, somaY = 0, somaZ = 0;
  for (int i = 0; i < amostras; i++) {
    sensors_event_t accel, gyro, temp;
    mpu.getEvent(&accel, &gyro, &temp);
    somaX += gyro.gyro.x;
    somaY += gyro.gyro.y;
    somaZ += gyro.gyro.z;
    delay(5);
  }
  biasGiroX = somaX / amostras;
  biasGiroY = somaY / amostras;
  biasGiroZ = somaZ / amostras;
  calibrado = true;
  Serial.printf("Giroscopio calibrado (bias X=%.4f Y=%.4f Z=%.4f rad/s)\n", biasGiroX, biasGiroY, biasGiroZ);
}

// Ângulo que a gravidade indica agora. É a referência absoluta do filtro:
// não escorrega com o tempo, mas balança a cada tranco do movimento.
float anguloPelaGravidade(const sensors_event_t &accel) {
  float graus = atan2(accel.acceleration.y, accel.acceleration.z) * 180.0f / PI;
  return EIXO_INVERTIDO ? -graus : graus;
}

void atualizarAngulo() {
  unsigned long agoraUs = micros();
  if (ultimaAmostraUs != 0 && (agoraUs - ultimaAmostraUs) < PERIODO_AMOSTRA_US) {
    return;
  }
  float dt = ultimaAmostraUs == 0 ? 0.0f : (agoraUs - ultimaAmostraUs) / 1000000.0f;
  ultimaAmostraUs = agoraUs;

  sensors_event_t accel, gyro, temp;
  mpu.getEvent(&accel, &gyro, &temp);
  float pelaGravidade = anguloPelaGravidade(accel);

  // Um envio HTTP lento pode segurar o loop por segundos. Integrar o
  // giroscópio por um buraco desses só produz lixo — nesse caso o ângulo é
  // ressincronizado direto pela gravidade.
  if (dt <= 0.0f || dt > 0.2f) {
    anguloFiltrado = pelaGravidade;
    return;
  }

  float velocidade = gyro.gyro.x - biasGiroX;  // rad/s no eixo do movimento
  if (EIXO_INVERTIDO) velocidade = -velocidade;
  float pelaRotacao = anguloFiltrado + velocidade * 180.0f / PI * dt;

  anguloFiltrado = PESO_GIRO * pelaRotacao + (1.0f - PESO_GIRO) * pelaGravidade;
}

float anguloAtual() {
  return anguloFiltrado - offsetTara;
}

void aplicarTara() {
  offsetTara = anguloFiltrado;
  memoria.putFloat("tara", offsetTara);
  Serial.printf("Tara aplicada: %.2f graus viraram o novo zero.\n", offsetTara);
}

// ------------------------------------------------------------------
// LED de status
// ------------------------------------------------------------------
//
// Sem tela no aparelho, o LED é a única forma de saber o que está havendo
// olhando para ele: piscando rápido = "sou eu" (comando Identificar),
// aceso = gravando captura, pisca curto = tudo certo, pisca duplo = sem
// Wi-Fi ou sem login.

bool algumAlvoLogado() {
  for (int i = 0; i < QTD_ALVOS; i++) {
    if (ALVOS[i].ativo && ALVOS[i].logado) return true;
  }
  return false;
}

void atualizarLed() {
  unsigned long agora = millis();

  if (agora < fimIdentificacao) {
    if (agora - ultimoPiscaLed > 80) {
      ledAceso = !ledAceso;
      digitalWrite(PINO_LED, ledAceso);
      ultimoPiscaLed = agora;
    }
    return;
  }

  if (capturando) {
    digitalWrite(PINO_LED, HIGH);
    return;
  }

  bool saudavel = WiFi.status() == WL_CONNECTED && algumAlvoLogado();
  unsigned long aceso = saudavel ? 60 : 500;
  unsigned long apagado = saudavel ? 1940 : 500;
  unsigned long limite = ledAceso ? aceso : apagado;
  if (agora - ultimoPiscaLed > limite) {
    ledAceso = !ledAceso;
    digitalWrite(PINO_LED, ledAceso);
    ultimoPiscaLed = agora;
  }
}

// ------------------------------------------------------------------
// Rede
// ------------------------------------------------------------------

void conectarWifi() {
  Serial.printf("Conectando ao Wi-Fi %s", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);  // o modem dormindo atrasa os POSTs e engasga o tempo real
  WiFi.begin(WIFI_SSID, WIFI_SENHA);

  unsigned long limite = millis() + 20000;
  while (WiFi.status() != WL_CONNECTED && millis() < limite) {
    delay(300);
    Serial.print(".");
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("Wi-Fi conectado, IP: ");
    Serial.println(WiFi.localIP());
  } else {
    // Não trava aqui: o loop tenta de novo sozinho, e o aparelho continua
    // lendo o ângulo (útil para conferir a montagem pelo Monitor Serial).
    Serial.println("Wi-Fi nao conectou agora; vou continuar tentando em segundo plano.");
  }
}

void cuidarDoWifi() {
  static unsigned long proximaTentativa = 0;
  if (WiFi.status() == WL_CONNECTED) return;
  if (millis() < proximaTentativa) return;
  proximaTentativa = millis() + 10000;
  Serial.println("Wi-Fi caiu — reconectando...");
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_SENHA);
}

void fazerLogin(Alvo &alvo) {
  alvo.proximaTentativaLogin = millis() + ESPERA_LOGIN_MS;
  Serial.printf("[%s] tentando login...\n", alvo.nome);

  HTTPClient http;
  http.setConnectTimeout(TIMEOUT_HTTP_MS);
  http.setTimeout(TIMEOUT_HTTP_MS);
  if (!http.begin(clienteParaUrl(alvo.baseUrl), String(alvo.baseUrl) + "/auth/login")) {
    Serial.printf("[%s] nao consegui abrir a conexao de login\n", alvo.nome);
    return;
  }
  http.addHeader("Content-Type", "application/json");

  String corpo = String("{\"email\":\"") + alvo.email + "\",\"senha\":\"" + alvo.senha + "\"}";
  int status = http.POST(corpo);

  if (status == 200) {
    String resposta = http.getString();
    String token = extrairTexto(resposta, "token");
    long id = extrairNumero(resposta, "id", -1);
    if (token.length() > 0 && id >= 0) {
      alvo.token = token;
      alvo.idClinica = (int)id;
      alvo.logado = true;
      alvo.falhasSeguidas = 0;
      Serial.printf("[%s] login OK, idClinica=%d\n", alvo.nome, alvo.idClinica);
    } else {
      Serial.printf("[%s] login respondeu 200 mas sem token/id: %s\n", alvo.nome, resposta.c_str());
      alvo.logado = false;
    }
  } else if (status == 401 || status == 403) {
    Serial.printf("[%s] login recusado (status=%d) — confira e-mail e senha da clinica.\n", alvo.nome, status);
    alvo.logado = false;
  } else {
    Serial.printf("[%s] login falhou, status=%d\n", alvo.nome, status);
    alvo.logado = false;
  }
  http.end();
}

void tratarComando(const String &comando) {
  if (comando.length() == 0 || comando == "NENHUM") return;

  Serial.printf("Comando recebido: %s\n", comando.c_str());
  if (comando == "TARAR") {
    aplicarTara();
  } else if (comando == "IDENTIFICAR") {
    fimIdentificacao = millis() + 4000;
  } else if (comando == "INICIAR_CAPTURA") {
    capturando = true;
  } else if (comando == "PARAR_CAPTURA") {
    capturando = false;
  } else if (comando == "REINICIAR") {
    Serial.println("Reiniciando a pedido do servidor...");
    delay(200);
    ESP.restart();
  }
}

void enviarTelemetria(Alvo &alvo) {
  HTTPClient http;
  http.setConnectTimeout(TIMEOUT_HTTP_MS);
  http.setTimeout(TIMEOUT_HTTP_MS);
  http.setReuse(true);
  if (!http.begin(clienteParaUrl(alvo.baseUrl), String(alvo.baseUrl) + "/goniometro/telemetria")) {
    alvo.falhasSeguidas++;
    return;
  }
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + alvo.token);

  int bateria = lerBateria();
  String corpo = "{";
  corpo += "\"idClinica\":" + String(alvo.idClinica);
  corpo += ",\"angulo\":" + String(anguloAtual(), 2);
  corpo += ",\"anguloBruto\":" + String(anguloFiltrado, 2);
  if (bateria >= 0) corpo += ",\"bateria\":" + String(bateria);
  corpo += ",\"rssi\":" + String(WiFi.RSSI());
  corpo += ",\"numeroSerie\":\"" + numeroSerie + "\"";
  corpo += ",\"firmware\":\"" + String(VERSAO_FIRMWARE) + "\"";
  corpo += ",\"ip\":\"" + WiFi.localIP().toString() + "\"";
  corpo += ",\"calibrado\":" + String(calibrado ? "true" : "false");
  corpo += "}";

  int status = http.POST(corpo);

  if (status == 200) {
    String resposta = http.getString();
    alvo.falhasSeguidas = 0;
    // O servidor manda o ritmo: rápido enquanto alguém olha ou grava, lento
    // quando ninguém está usando (é a bateria do aparelho em jogo).
    long intervalo = extrairNumero(resposta, "intervaloMs", (long)alvo.intervaloMs);
    if (intervalo >= 50 && intervalo <= 60000) {
      alvo.intervaloMs = (unsigned long)intervalo;
    }
    tratarComando(extrairTexto(resposta, "comando"));
  } else if (status == 401) {
    Serial.printf("[%s] token expirou — vou logar de novo\n", alvo.nome);
    alvo.logado = false;
    alvo.proximaTentativaLogin = 0;  // sem espera: reautentica no próximo loop
  } else {
    alvo.falhasSeguidas++;
    Serial.printf("[%s] envio falhou, status=%d (falha %d)\n", alvo.nome, status, alvo.falhasSeguidas);
  }
  http.end();

  // Um alvo fora do ar não pode segurar o outro: depois de algumas falhas
  // seguidas ele fica de castigo por um tempo, e o alvo saudável continua
  // no ritmo normal.
  if (alvo.falhasSeguidas >= MAX_FALHAS_ANTES_DO_CASTIGO) {
    alvo.proximoEnvio = millis() + CASTIGO_MS;
    Serial.printf("[%s] muitas falhas seguidas — pausando os envios por %lus\n", alvo.nome, CASTIGO_MS / 1000);
  }
}

void cuidarDosAlvos() {
  unsigned long agora = millis();
  for (int i = 0; i < QTD_ALVOS; i++) {
    Alvo &alvo = ALVOS[i];
    if (!alvo.ativo || WiFi.status() != WL_CONNECTED) continue;

    if (!alvo.logado) {
      if (agora >= alvo.proximaTentativaLogin) {
        fazerLogin(alvo);
      }
      continue;
    }

    if (agora >= alvo.proximoEnvio) {
      // Marca o próximo envio ANTES de enviar: o POST pode demorar, e contar
      // a partir do fim faria o intervalo real virar "intervalo + latência".
      alvo.proximoEnvio = agora + alvo.intervaloMs;
      enviarTelemetria(alvo);
    }
  }
}

// ------------------------------------------------------------------
// setup / loop
// ------------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.printf("\nRehabit — goniometro digital, firmware %s\n", VERSAO_FIRMWARE);

  pinMode(PINO_LED, OUTPUT);
  digitalWrite(PINO_LED, LOW);
  if (PINO_BATERIA >= 0) {
    analogSetPinAttenuation(PINO_BATERIA, ADC_11db);  // faixa até ~3,1 V no pino
  }

  Wire.begin();
  Wire.setClock(400000);  // I2C rápido: 100 leituras por segundo não cabem no modo padrão
  clienteSeguro.setInsecure();  // TCC/demo: sem validar certificado, simplicidade acima de TLS perfeito.

  if (!mpu.begin()) {
    Serial.println("MPU6050 nao encontrado! Confira a fiacao (SDA=21, SCL=22, VCC=3V3, GND).");
    while (true) {
      digitalWrite(PINO_LED, !digitalRead(PINO_LED));
      delay(150);  // pisca sem parar: erro de hardware, não adianta seguir
    }
  }
  mpu.setAccelerometerRange(MPU6050_RANGE_4_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
  Serial.println("MPU6050 encontrado.");

  memoria.begin("rehabit", false);
  offsetTara = memoria.getFloat("tara", 0.0f);
  Serial.printf("Tara guardada: %.2f graus\n", offsetTara);

  calibrarGiroscopio();

  // Primeira leitura da gravidade como ponto de partida do filtro — sem isso
  // o ângulo levaria alguns segundos convergindo de zero até o valor real.
  sensors_event_t accel, gyro, temp;
  mpu.getEvent(&accel, &gyro, &temp);
  anguloFiltrado = anguloPelaGravidade(accel);

  numeroSerie = montarNumeroSerie();
  Serial.printf("Numero de serie: %s\n", numeroSerie.c_str());

  conectarWifi();
}

void loop() {
  atualizarAngulo();
  cuidarDoWifi();
  cuidarDosAlvos();
  atualizarLed();

  // Eco no Monitor Serial, útil para conferir a montagem sem abrir o site.
  static unsigned long ultimoEco = 0;
  if (millis() - ultimoEco > 1000) {
    ultimoEco = millis();
    Serial.printf("Angulo: %6.2f graus (bruto %6.2f) | bateria %d%% | RSSI %d dBm%s\n",
                  anguloAtual(), anguloFiltrado, lerBateria(), WiFi.RSSI(),
                  capturando ? " | GRAVANDO" : "");
  }
}
