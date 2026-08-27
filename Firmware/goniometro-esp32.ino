// Rehabit — leitura do goniômetro (ESP32 + MPU6050)
//
// NÃO É PRECISO EDITAR NADA AQUI. Wi-Fi e pareamento são configurados pelo
// celular, na primeira vez que o aparelho liga:
//
//   1. Grave este código uma vez (veja o guia ao lado).
//   2. Ao ligar sem configuração, o goniômetro cria um Wi-Fi chamado
//      "Rehabit-Goniometro" (senha: rehabit123).
//   3. Conecte o celular nesse Wi-Fi — abre sozinho um portal.
//   4. Escolha a rede da clínica, digite a senha dela e o código de 6
//      dígitos que aparece na tela Dispositivo do Rehabit.
//   5. O aparelho grava tudo na memória e reinicia já conectado.
//
// Para reconfigurar (trocou de Wi-Fi, mudou de clínica): segure o botão BOOT
// por 5 segundos com o aparelho ligado. Ele apaga a configuração e volta ao
// passo 2.
//
// Montagem do MPU6050: VCC->3.3V, GND->GND, SDA->GPIO21, SCL->GPIO22.
//
// O ângulo é calculado só com o acelerômetro (sem giroscópio), pelo eixo
// Y/Z — se o MPU6050 estiver montado de outro jeito na articulação, troque
// accel.acceleration.y / accel.acceleration.z pelos eixos certos (X/Y ou
// X/Z) na função lerAngulo().
//
// BIBLIOTECAS (Gerenciar Bibliotecas): "Adafruit MPU6050" e
// "WiFiManager" (de tzapu).

#include <WiFi.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <WiFiClientSecure.h>
#include <WiFiManager.h>
#include <Preferences.h>

// Onde o Rehabit está publicado. Só precisa mudar se você republicar a API
// em outro endereço.
const char *BASE_URL = "https://rehabit-api-4tex.onrender.com/api";

const char *AP_NOME = "Rehabit-Goniometro";
const char *AP_SENHA = "rehabit123";

const int PINO_BOTAO_RESET = 0;  // BOOT na maioria das placas DevKit
const unsigned long SEGURAR_PARA_RESETAR_MS = 5000;

const unsigned long INTERVALO_LEITURA_MS = 2000;
const unsigned long INTERVALO_RETRY_PAREAMENTO_MS = 30000;

WiFiClientSecure clienteSeguro;
WiFiClient clienteInseguro;

Adafruit_MPU6050 mpu;
Preferences memoria;

String tokenDispositivo = "";
unsigned long ultimaLeitura = 0;
unsigned long ultimaTentativaPareamento = 0;
unsigned long botaoPressionadoDesde = 0;

WiFiClient &clienteParaUrl(const char *url) {
  if (String(url).startsWith("https://")) {
    return clienteSeguro;
  }
  return clienteInseguro;
}

// ---------------------------------------------------------------------------
// Memória interna (NVS): guarda o token entre reinícios, para não precisar
// parear de novo toda vez que faltar luz.
// ---------------------------------------------------------------------------

void salvarToken(const String &token) {
  memoria.begin("rehabit", false);
  memoria.putString("token", token);
  memoria.end();
}

String carregarToken() {
  memoria.begin("rehabit", true);
  String token = memoria.getString("token", "");
  memoria.end();
  return token;
}

void apagarConfiguracao() {
  memoria.begin("rehabit", false);
  memoria.clear();
  memoria.end();

  WiFiManager wm;
  wm.resetSettings();  // apaga também a rede Wi-Fi salva
}

// ---------------------------------------------------------------------------
// Pareamento
// ---------------------------------------------------------------------------

// Extrai um campo string simples de um JSON achatado, sem biblioteca de
// parsing — o corpo esperado tem poucos campos e formato conhecido.
String extrairTexto(const String &json, const char *chave) {
  String marcador = String("\"") + chave + "\":\"";
  int inicio = json.indexOf(marcador);
  if (inicio < 0) {
    return "";
  }
  inicio += marcador.length();
  int fim = json.indexOf("\"", inicio);
  if (fim < 0) {
    return "";
  }
  return json.substring(inicio, fim);
}

/** Troca o código de 6 dígitos por um token próprio do aparelho. */
bool parear(const String &codigo) {
  if (codigo.length() == 0) {
    return false;
  }
  Serial.println("Tentando parear com o codigo informado...");

  HTTPClient http;
  http.begin(clienteParaUrl(BASE_URL), String(BASE_URL) + "/dispositivos/parear");
  http.addHeader("Content-Type", "application/json");

  String corpo = String("{\"codigo\":\"") + codigo + "\"}";
  int status = http.POST(corpo);
  String resposta = http.getString();
  http.end();

  if (status != 200) {
    Serial.printf("Pareamento falhou (status=%d): %s\n", status, resposta.c_str());
    return false;
  }

  String token = extrairTexto(resposta, "token");
  if (token.length() == 0) {
    Serial.println("Pareamento: resposta sem token.");
    return false;
  }

  tokenDispositivo = token;
  salvarToken(token);
  Serial.printf("Pareado com a clinica \"%s\". Token guardado.\n",
                extrairTexto(resposta, "nomeClinica").c_str());
  return true;
}

// ---------------------------------------------------------------------------
// Wi-Fi + portal de configuração
// ---------------------------------------------------------------------------

void conectarOuAbrirPortal() {
  WiFiManager wm;
  wm.setConfigPortalTimeout(0);  // fica no portal até alguém configurar

  // Campo extra no portal, além de rede e senha: é onde entra o código que a
  // clínica vê na tela Dispositivo.
  WiFiManagerParameter campoCodigo(
      "codigo", "Codigo de pareamento (6 digitos)", "", 6,
      "pattern=\"[0-9]{6}\" inputmode=\"numeric\"");
  wm.addParameter(&campoCodigo);

  Serial.printf("Se nao conectar, abra o Wi-Fi \"%s\" (senha %s) no celular.\n",
                AP_NOME, AP_SENHA);

  // autoConnect: tenta a rede salva; não tendo, sobe o ponto de acesso e
  // bloqueia aqui até a pessoa terminar a configuração.
  if (!wm.autoConnect(AP_NOME, AP_SENHA)) {
    Serial.println("Falhou ao configurar. Reiniciando...");
    delay(2000);
    ESP.restart();
  }

  Serial.print("Wi-Fi conectado, IP: ");
  Serial.println(WiFi.localIP());

  String codigo = String(campoCodigo.getValue());
  codigo.trim();
  if (codigo.length() > 0) {
    // Veio do portal agora: vale mais que o token antigo, porque a pessoa
    // pode estar movendo o aparelho para outra clínica.
    parear(codigo);
  }
}

// ---------------------------------------------------------------------------
// Leitura e envio
// ---------------------------------------------------------------------------

float lerAngulo() {
  sensors_event_t accel, gyro, temp;
  mpu.getEvent(&accel, &gyro, &temp);
  return atan2(accel.acceleration.y, accel.acceleration.z) * 180.0 / PI;
}

void enviarLeitura(float angulo) {
  HTTPClient http;
  http.begin(clienteParaUrl(BASE_URL), String(BASE_URL) + "/goniometro/leitura");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + tokenDispositivo);

  // Sem idClinica: o servidor tira do token deste aparelho.
  String corpo = String("{\"angulo\":") + String(angulo, 2) + "}";
  int status = http.POST(corpo);
  http.end();

  if (status == 200) {
    Serial.printf("Leitura enviada: %.2f graus\n", angulo);
  } else if (status == 401) {
    Serial.println("Token recusado. Reconfigure segurando o botao BOOT por 5s.");
  } else if (status == 403) {
    Serial.println("Este aparelho foi revogado pela clinica. Pareie de novo.");
  } else {
    Serial.printf("Envio falhou, status=%d\n", status);
  }
}

/** Segurar BOOT por 5s apaga a configuração e reinicia no portal. */
void verificarBotaoDeReset() {
  bool pressionado = digitalRead(PINO_BOTAO_RESET) == LOW;

  if (!pressionado) {
    botaoPressionadoDesde = 0;
    return;
  }
  if (botaoPressionadoDesde == 0) {
    botaoPressionadoDesde = millis();
    return;
  }
  if (millis() - botaoPressionadoDesde >= SEGURAR_PARA_RESETAR_MS) {
    Serial.println("Apagando configuracao... o aparelho vai reiniciar no portal.");
    apagarConfiguracao();
    delay(500);
    ESP.restart();
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  pinMode(PINO_BOTAO_RESET, INPUT_PULLUP);

  Wire.begin();
  clienteSeguro.setInsecure();  // TCC/demo: sem verificação de certificado.

  if (!mpu.begin()) {
    Serial.println("MPU6050 nao encontrado! Confira a fiacao (SDA/SCL/VCC/GND).");
    while (true) {
      delay(1000);
    }
  }
  Serial.println("MPU6050 encontrado.");

  tokenDispositivo = carregarToken();
  if (tokenDispositivo.length() > 0) {
    Serial.println("Token encontrado na memoria.");
  } else {
    Serial.println("Sem token: use o portal para parear.");
  }

  conectarOuAbrirPortal();
}

void loop() {
  verificarBotaoDeReset();

  unsigned long agora = millis();
  if (agora - ultimaLeitura < INTERVALO_LEITURA_MS) {
    return;
  }
  ultimaLeitura = agora;

  float angulo = lerAngulo();
  Serial.printf("Angulo lido: %.2f graus\n", angulo);

  if (tokenDispositivo.length() == 0) {
    // Sem pareamento não há para onde enviar; avisa de vez em quando para
    // não encher o monitor serial.
    if (agora - ultimaTentativaPareamento > INTERVALO_RETRY_PAREAMENTO_MS) {
      ultimaTentativaPareamento = agora;
      Serial.println("Aparelho ainda nao pareado. Segure BOOT por 5s para abrir o portal.");
    }
    return;
  }

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Wi-Fi caiu, tentando reconectar...");
    WiFi.reconnect();
    return;
  }

  enviarLeitura(angulo);
}
