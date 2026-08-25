// Rehabit — leitura do goniômetro (ESP32 + MPU6050)
//
// ANTES DE GRAVAR:
//   1. Preencha WIFI_SSID / WIFI_SENHA abaixo.
//   2. Preencha o e-mail e a senha de uma conta de CLÍNICA já cadastrada
//      no Rehabit em cada um dos dois ALVOS (local e nuvem) — pode ser a
//      mesma conta nos dois, mas os dois blocos {email, senha} de baixo
//      precisam estar preenchidos.
//   3. Confira o IP do alvo "local" (ALVOS[0].baseUrl) — hoje aponta pra
//      192.168.1.10:8080. Se o PC que roda o backend local mudar de IP,
//      troque aqui.
//   4. Monte o MPU6050: VCC->3.3V, GND->GND, SDA->GPIO21, SCL->GPIO22
//      (pinos padrão de I2C da maioria das placas ESP32 DevKit — confira
//      a serigrafia da sua placa se for diferente).
//
// O ângulo é calculado só com o acelerômetro (sem giroscópio), pelo eixo
// Y/Z — se o MPU6050 estiver montado de outro jeito na articulação, troque
// accel.acceleration.y / accel.acceleration.z pelos eixos certos (X/Y ou
// X/Z) na função lerAngulo().

#include <WiFi.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>

const char *WIFI_SSID = "SEU_WIFI_AQUI";
const char *WIFI_SENHA = "SUA_SENHA_WIFI_AQUI";

struct Alvo {
  const char *nome;
  const char *baseUrl;
  const char *email;
  const char *senha;
  String token;
  int idClinica;
  bool logado;
  unsigned long ultimaTentativaLogin;
};

Alvo ALVOS[2] = {
  {"local", "http://192.168.1.10:8080/api", "SEU_EMAIL_AQUI", "SUA_SENHA_AQUI", "", 0, false, 0},
  {"nuvem", "https://rehabit-api-4tex.onrender.com/api", "SEU_EMAIL_AQUI", "SUA_SENHA_AQUI", "", 0, false, 0},
};

const unsigned long INTERVALO_LEITURA_MS = 2000;
const unsigned long INTERVALO_RETRY_LOGIN_MS = 30000;

Adafruit_MPU6050 mpu;
unsigned long ultimaLeitura = 0;

void conectarWifi() {
  Serial.print("Conectando ao Wi-Fi ");
  Serial.println(WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_SENHA);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("Wi-Fi conectado, IP: ");
  Serial.println(WiFi.localIP());
}

void fazerLogin(Alvo &alvo) {
  Serial.printf("[%s] tentando login...\n", alvo.nome);
  HTTPClient http;
  http.begin(String(alvo.baseUrl) + "/auth/login");
  http.addHeader("Content-Type", "application/json");
  String corpo = String("{\"email\":\"") + alvo.email + "\",\"senha\":\"" + alvo.senha + "\"}";
  int status = http.POST(corpo);
  if (status == 200) {
    String resposta = http.getString();
    int posToken = resposta.indexOf("\"token\":\"");
    int posId = resposta.indexOf("\"id\":");
    if (posToken >= 0 && posId >= 0) {
      int inicioToken = posToken + 9;
      int fimToken = resposta.indexOf("\"", inicioToken);
      alvo.token = resposta.substring(inicioToken, fimToken);
      alvo.idClinica = resposta.substring(posId + 5, resposta.indexOf(",", posId)).toInt();
      alvo.logado = true;
      Serial.printf("[%s] login OK, idClinica=%d\n", alvo.nome, alvo.idClinica);
    } else {
      Serial.printf("[%s] login: resposta 200 mas sem token/id reconhecivel: %s\n", alvo.nome, resposta.c_str());
      alvo.logado = false;
    }
  } else {
    Serial.printf("[%s] login falhou, status=%d\n", alvo.nome, status);
    alvo.logado = false;
  }
  http.end();
  alvo.ultimaTentativaLogin = millis();
}

float lerAngulo() {
  sensors_event_t accel, gyro, temp;
  mpu.getEvent(&accel, &gyro, &temp);
  float angulo = atan2(accel.acceleration.y, accel.acceleration.z) * 180.0 / PI;
  return angulo;
}

void enviarLeitura(Alvo &alvo, float angulo) {
  HTTPClient http;
  http.begin(String(alvo.baseUrl) + "/goniometro/leitura");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + alvo.token);
  String corpo = String("{\"idClinica\":") + alvo.idClinica + ",\"angulo\":" + String(angulo, 2) + "}";
  int status = http.POST(corpo);
  if (status == 401) {
    Serial.printf("[%s] token expirado, vou logar de novo\n", alvo.nome);
    alvo.logado = false;
  } else if (status != 200) {
    Serial.printf("[%s] envio falhou, status=%d\n", alvo.nome, status);
  } else {
    Serial.printf("[%s] leitura enviada: %.2f graus\n", alvo.nome, angulo);
  }
  http.end();
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  Wire.begin();
  if (!mpu.begin()) {
    Serial.println("MPU6050 nao encontrado! Confira a fiacao (SDA/SCL/VCC/GND) e trave aqui.");
    while (true) {
      delay(1000);
    }
  }
  Serial.println("MPU6050 encontrado.");

  conectarWifi();

  for (int i = 0; i < 2; i++) {
    fazerLogin(ALVOS[i]);
  }
}

void loop() {
  unsigned long agora = millis();

  for (int i = 0; i < 2; i++) {
    if (!ALVOS[i].logado && agora - ALVOS[i].ultimaTentativaLogin > INTERVALO_RETRY_LOGIN_MS) {
      fazerLogin(ALVOS[i]);
    }
  }

  if (agora - ultimaLeitura > INTERVALO_LEITURA_MS) {
    ultimaLeitura = agora;
    float angulo = lerAngulo();
    Serial.printf("Angulo lido: %.2f graus\n", angulo);
    for (int i = 0; i < 2; i++) {
      if (ALVOS[i].logado) {
        enviarLeitura(ALVOS[i], angulo);
      }
    }
  }
}
