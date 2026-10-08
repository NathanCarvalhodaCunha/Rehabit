// Rehabit — teste de hardware do goniômetro (ESP32 + MPU6050)
//
// Grave este sketch ANTES do firmware principal, logo depois de soldar. Ele
// não usa Wi-Fi de clínica, servidor nem biblioteca externa: só confere se
// cada peça da placa responde, e diz o que está errado quando não responde.
// O passo a passo completo (multímetro, regulagem do MT3608, bateria) está em
// Firmware/teste-hardware-GUIA.md.
//
// O que ele testa, em ordem:
//   1. LED externo (GPIO19) — pisca 3 vezes; confira com os olhos.
//   2. Barramento I2C — procura o MPU6050 em SDA=21/SCL=22 e, se não achar,
//      tenta com os fios trocados para dizer se SDA e SCL foram invertidos.
//   3. MPU6050 — identidade do chip (WHO_AM_I), leituras a 100 kHz e a
//      400 kHz (a velocidade do firmware principal), gravidade ~1 g parado,
//      giroscópio perto de zero parado e temperatura plausível.
//   4. Wi-Fi — procura redes por perto. Além de testar o rádio, é o maior
//      pico de consumo da placa: se ela reinicia aqui na bateria, a
//      alimentação não está dando conta.
//   5. Quedas de energia — conta quantas vezes a placa reiniciou por queda de
//      tensão (brownout), inclusive quando estava só na bateria, sem
//      ninguém olhando o Monitor Serial.
//
// Depois dos testes ele fica em "modo movimento", que serve para testar na
// bateria, sem computador:
//   * LED externo ACENDE quando a placa inclina mais de 30° da posição em
//     que estava no fim dos testes, e apaga quando volta;
//   * botão BOOT faz da posição atual o novo zero;
//   * LED azul da própria placa (GPIO2) dá uma piscada por segundo = o ESP32
//     está vivo;
//   * LED externo piscando rápido sem parar = MPU6050 não respondeu;
//   * as 3 piscadas do começo se repetindo sozinhas = a placa está
//     reiniciando, quase sempre por falta de energia.
//
// No Monitor Serial (115200), envie:
//   r  repete todos os testes
//   c  zera o contador de quedas de energia
//
// ================== LIGAÇÕES (as do diagrama) ==================
//   MT3608 VOUT+ -> VIN        MT3608 VOUT- -> GND
//   MPU6050 VCC  -> 3V3        MPU6050 GND  -> GND
//   MPU6050 SDA  -> GPIO21     MPU6050 SCL  -> GPIO22
//   LED azul     -> GPIO19 -> resistor 220 ohm -> LED -> GND
//   Botão BOOT   -> GPIO0 (já existe na placa)

#include <Wire.h>
#include <WiFi.h>
#include <Preferences.h>
#include <esp_system.h>

const int PINO_SDA = 21;
const int PINO_SCL = 22;
const int PINO_LED_EXTERNO = 19;
const int PINO_LED_PLACA = 2;
const int PINO_BOTAO = 0;

const float LIMITE_INCLINACAO_GRAUS = 30.0f;
const int AMOSTRAS_POR_TESTE = 200;

// Registradores do MPU6050 usados aqui (datasheet "Register Map", rev. 4.2).
const uint8_t REG_SMPLRT_DIV = 0x19;
const uint8_t REG_CONFIG = 0x1A;
const uint8_t REG_GYRO_CONFIG = 0x1B;
const uint8_t REG_ACCEL_CONFIG = 0x1C;
const uint8_t REG_ACCEL_XOUT_H = 0x3B;
const uint8_t REG_PWR_MGMT_1 = 0x6B;
const uint8_t REG_WHO_AM_I = 0x75;

// Com ±2 g e ±250 °/s, as escalas do datasheet.
const float LSB_POR_G = 16384.0f;
const float LSB_POR_GRAU_S = 131.0f;

Preferences memoria;

uint8_t enderecoMpu = 0;  // 0 = não achado
bool mpuOk = false;
int falhas = 0;
int avisos = 0;
unsigned long errosI2c = 0;

// Última leitura convertida: aceleração em g, giro em °/s, temperatura em °C.
float ax = 0, ay = 0, az = 0, gxs = 0, gys = 0, gzs = 0, temperatura = 0;
// Posição que vale 0° no modo movimento.
float refX = 0, refY = 0, refZ = 1;

// ------------------------------------------------------------------
// Relatório
// ------------------------------------------------------------------

void ok(const char *texto) {
  Serial.printf("  [OK]    %s\n", texto);
}

void aviso(const char *texto) {
  avisos++;
  Serial.printf("  [AVISO] %s\n", texto);
}

void falha(const char *texto) {
  falhas++;
  Serial.printf("  [FALHA] %s\n", texto);
}

void titulo(const char *texto) {
  Serial.printf("\n--- %s ---\n", texto);
}

// ------------------------------------------------------------------
// I2C e MPU6050, direto nos registradores
// ------------------------------------------------------------------
//
// Sem a biblioteca da Adafruit de propósito: ela recusa qualquer chip cujo
// WHO_AM_I não seja 0x68, e muitos módulos vendidos como MPU6050 são clones
// que respondem outro valor. Lendo os registradores na mão, o teste consegue
// dizer "achei um clone" em vez de só "não achei nada".

bool escreverRegistrador(uint8_t reg, uint8_t valor) {
  Wire.beginTransmission(enderecoMpu);
  Wire.write(reg);
  Wire.write(valor);
  return Wire.endTransmission() == 0;
}

/** Devolve o valor do registrador, ou -1 se o chip não respondeu. */
int lerRegistrador(uint8_t reg) {
  Wire.beginTransmission(enderecoMpu);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return -1;
  if (Wire.requestFrom(enderecoMpu, (uint8_t)1) != 1) return -1;
  return Wire.read();
}

int16_t lerPalavra() {
  // Dois read() separados: em "(Wire.read() << 8) | Wire.read()" o C++ não
  // garante qual dos dois roda primeiro.
  uint8_t alto = Wire.read();
  uint8_t baixo = Wire.read();
  return (int16_t)((alto << 8) | baixo);
}

/** Lê acelerômetro, temperatura e giroscópio de uma vez (14 bytes). */
bool lerMpu() {
  Wire.beginTransmission(enderecoMpu);
  Wire.write(REG_ACCEL_XOUT_H);
  if (Wire.endTransmission(false) != 0 || Wire.requestFrom(enderecoMpu, (uint8_t)14) != 14) {
    errosI2c++;
    return false;
  }
  ax = lerPalavra() / LSB_POR_G;
  ay = lerPalavra() / LSB_POR_G;
  az = lerPalavra() / LSB_POR_G;
  temperatura = lerPalavra() / 340.0f + 36.53f;
  gxs = lerPalavra() / LSB_POR_GRAU_S;
  gys = lerPalavra() / LSB_POR_GRAU_S;
  gzs = lerPalavra() / LSB_POR_GRAU_S;
  return true;
}

/** Procura dispositivos no barramento; devolve quantos achou e guarda o MPU. */
int varrerI2c(int sda, int scl) {
  Wire.end();
  Wire.begin(sda, scl, 100000);
  int achados = 0;
  for (uint8_t endereco = 1; endereco < 127; endereco++) {
    Wire.beginTransmission(endereco);
    if (Wire.endTransmission() == 0) {
      achados++;
      Serial.printf("  dispositivo em 0x%02X%s\n", endereco,
                    (endereco == 0x68 || endereco == 0x69) ? " (e o endereco do MPU6050)" : "");
      if (endereco == 0x68 || endereco == 0x69) enderecoMpu = endereco;
    }
  }
  return achados;
}

/**
 * Faz AMOSTRAS_POR_TESTE leituras na velocidade pedida e conta as que
 * falharam. Uma solda fria costuma aparecer aqui: o chip responde às vezes.
 */
unsigned long contarErros(uint32_t frequencia) {
  Wire.setClock(frequencia);
  unsigned long antes = errosI2c;
  for (int i = 0; i < AMOSTRAS_POR_TESTE; i++) {
    lerMpu();
    delay(2);
  }
  return errosI2c - antes;
}

// ------------------------------------------------------------------
// Testes
// ------------------------------------------------------------------

void piscar(int pino, int vezes, int msAceso, int msApagado) {
  for (int i = 0; i < vezes; i++) {
    digitalWrite(pino, HIGH);
    delay(msAceso);
    digitalWrite(pino, LOW);
    delay(msApagado);
  }
}

void testarLed() {
  titulo("1. LED externo (GPIO19)");
  Serial.println("  Piscando 3 vezes agora — olhe para o LED azul externo.");
  piscar(PINO_LED_EXTERNO, 3, 300, 300);
  Serial.println("  Piscou? Entao o LED e o resistor estao certos.");
  Serial.println("  Nao piscou? LED invertido (perna maior vai no lado do GPIO19),");
  Serial.println("  solda solta no resistor, ou o fio esta em outro pino que nao o D19.");
}

void testarI2c() {
  titulo("2. Barramento I2C (SDA=21, SCL=22)");
  enderecoMpu = 0;
  int achados = varrerI2c(PINO_SDA, PINO_SCL);
  if (enderecoMpu != 0) {
    char texto[60];
    snprintf(texto, sizeof(texto), "MPU6050 respondeu no endereco 0x%02X", enderecoMpu);
    ok(texto);
    return;
  }
  if (achados > 0) {
    falha("Ha algo no barramento, mas nao no endereco do MPU6050 (0x68/0x69).");
    return;
  }

  // Nada respondeu. Antes de culpar o chip, vê se os fios só foram trocados.
  Serial.println("  Ninguem respondeu. Testando com SDA e SCL trocados...");
  varrerI2c(PINO_SCL, PINO_SDA);
  bool trocados = enderecoMpu != 0;
  enderecoMpu = 0;
  Wire.end();
  Wire.begin(PINO_SDA, PINO_SCL, 100000);
  if (trocados) {
    falha("SDA e SCL estao INVERTIDOS: o MPU so responde com os fios trocados.");
    Serial.println("          Corrija a solda: SDA do MPU -> D21, SCL do MPU -> D22.");
  } else {
    falha("Nenhum dispositivo I2C encontrado.");
    Serial.println("          Confira: LED do modulo MPU aceso? 3V3 -> VCC e GND -> GND?");
    Serial.println("          SDA -> D21 e SCL -> D22 com continuidade? Solda fria nos pinos?");
  }
}

void testarMpu() {
  titulo("3. MPU6050");
  if (enderecoMpu == 0) {
    falha("Pulado: o MPU6050 nao respondeu no teste anterior.");
    mpuOk = false;
    return;
  }

  int quemSou = lerRegistrador(REG_WHO_AM_I);
  if (quemSou < 0) {
    falha("O chip nao respondeu a leitura do WHO_AM_I.");
    mpuOk = false;
    return;
  }
  char texto[140];
  if (quemSou == 0x68) {
    ok("WHO_AM_I = 0x68: MPU6050 legitimo (ou clone fiel).");
  } else {
    snprintf(texto, sizeof(texto),
             "WHO_AM_I = 0x%02X, nao 0x68: e um clone ou variante (0x70 = MPU6500, 0x71 = MPU9250).",
             quemSou);
    aviso(texto);
    Serial.println("          As leituras podem funcionar, mas a biblioteca Adafruit MPU6050 do");
    Serial.println("          firmware principal recusa esse chip e vai dizer \"MPU6050 nao");
    Serial.println("          encontrado\" mesmo com a fiacao certa.");
  }

  // Acorda o chip (ele liga dormindo) e fixa escalas conhecidas.
  bool configurado = escreverRegistrador(REG_PWR_MGMT_1, 0x80);  // reset
  delay(100);
  configurado = configurado && escreverRegistrador(REG_PWR_MGMT_1, 0x01);  // acorda, relógio do giro X
  configurado = configurado && escreverRegistrador(REG_SMPLRT_DIV, 9);     // 100 Hz
  configurado = configurado && escreverRegistrador(REG_CONFIG, 0x03);      // filtro ~44 Hz
  configurado = configurado && escreverRegistrador(REG_GYRO_CONFIG, 0x00); // ±250 °/s
  configurado = configurado && escreverRegistrador(REG_ACCEL_CONFIG, 0x00);// ±2 g
  delay(100);
  if (!configurado) {
    falha("O chip nao aceitou a configuracao (escrita I2C falhou).");
    mpuOk = false;
    return;
  }
  ok("Chip acordado e configurado.");

  Serial.println("  Deixe a placa PARADA sobre a mesa pelos proximos segundos...");
  delay(500);

  unsigned long erros100 = contarErros(100000);
  if (erros100 == 0) {
    snprintf(texto, sizeof(texto), "Leituras a 100 kHz: 0 erros em %d.", AMOSTRAS_POR_TESTE);
    ok(texto);
  } else {
    snprintf(texto, sizeof(texto), "Leituras a 100 kHz: %lu erros em %d — mau contato (solda fria?) em SDA/SCL/VCC/GND.",
             erros100, AMOSTRAS_POR_TESTE);
    falha(texto);
  }

  // Medidas em repouso, já a 400 kHz como no firmware principal.
  Wire.setClock(400000);
  unsigned long antes = errosI2c;
  float somaMod = 0, somaModQuad = 0;
  float maxGiro = 0, somaTemp = 0;
  float somaGx = 0, somaGy = 0, somaGz = 0;
  bool congelado = true;
  float primeiroAz = 0;
  int validas = 0;
  for (int i = 0; i < AMOSTRAS_POR_TESTE; i++) {
    if (lerMpu()) {
      float modulo = sqrtf(ax * ax + ay * ay + az * az);
      somaMod += modulo;
      somaModQuad += modulo * modulo;
      somaGx += gxs;
      somaGy += gys;
      somaGz += gzs;
      somaTemp += temperatura;
      if (validas == 0) primeiroAz = az;
      else if (az != primeiroAz) congelado = false;
      validas++;
    }
    delay(5);
  }
  unsigned long erros400 = errosI2c - antes;
  if (erros400 == 0) {
    snprintf(texto, sizeof(texto), "Leituras a 400 kHz: 0 erros em %d.", AMOSTRAS_POR_TESTE);
    ok(texto);
  } else {
    snprintf(texto, sizeof(texto), "Leituras a 400 kHz: %lu erros em %d — o firmware principal usa 400 kHz.",
             erros400, AMOSTRAS_POR_TESTE);
    aviso(texto);
    Serial.println("          Fios de SDA/SCL longos ou soldas ruins. Encurte os fios ou, no");
    Serial.println("          goniometro-esp32.ino, troque Wire.setClock(400000) por 100000.");
  }

  if (validas < AMOSTRAS_POR_TESTE / 2) {
    falha("Leituras demais falharam; nao da para avaliar os sensores.");
    mpuOk = false;
    return;
  }

  if (congelado) {
    falha("Os valores nao mudam nada entre leituras: o chip parece travado.");
    mpuOk = false;
    return;
  }

  float media = somaMod / validas;
  float desvio = sqrtf(fmaxf(0.0f, somaModQuad / validas - media * media));
  if (media > 0.90f && media < 1.10f) {
    snprintf(texto, sizeof(texto), "Acelerometro: gravidade medida = %.2f g (esperado ~1.00).", media);
    ok(texto);
  } else {
    snprintf(texto, sizeof(texto), "Acelerometro: gravidade medida = %.2f g, fora de 0.90-1.10.", media);
    falha(texto);
  }
  if (desvio > 0.05f) {
    aviso("A placa parece ter mexido durante a medida. Repita parada (envie r).");
  }

  float mediaGx = somaGx / validas, mediaGy = somaGy / validas, mediaGz = somaGz / validas;
  maxGiro = fmaxf(fabsf(mediaGx), fmaxf(fabsf(mediaGy), fabsf(mediaGz)));
  snprintf(texto, sizeof(texto), "Giroscopio parado: X=%.1f Y=%.1f Z=%.1f graus/s", mediaGx, mediaGy, mediaGz);
  if (maxGiro < 10.0f) {
    ok(texto);  // um desvio de poucos °/s é normal; o firmware principal calibra no boot
  } else {
    aviso(texto);
    Serial.println("          Mais de 10 graus/s parado: a placa mexeu ou o giroscopio tem defeito.");
  }

  float mediaTemp = somaTemp / validas;
  snprintf(texto, sizeof(texto), "Temperatura do chip: %.1f C", mediaTemp);
  if (mediaTemp > -10.0f && mediaTemp < 70.0f) {
    ok(texto);
  } else {
    aviso(texto);
    Serial.println("          Valor estranho (clones calculam a temperatura de outro jeito; nao afeta o angulo).");
  }

  refX = ax;
  refY = ay;
  refZ = az;
  mpuOk = true;
}

void testarWifi() {
  titulo("4. Wi-Fi (radio e pico de consumo)");
  Serial.println("  Procurando redes... (e aqui que a placa mais puxa corrente)");
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  delay(100);
  Serial.printf("  MAC: %s\n", WiFi.macAddress().c_str());
  int redes = WiFi.scanNetworks();
  if (redes > 0) {
    char texto[60];
    snprintf(texto, sizeof(texto), "%d rede(s) encontrada(s).", redes);
    ok(texto);
    for (int i = 0; i < redes && i < 8; i++) {
      Serial.printf("          %-32s %4d dBm\n", WiFi.SSID(i).c_str(), (int)WiFi.RSSI(i));
    }
  } else if (redes == 0) {
    aviso("Nenhuma rede encontrada. Se ha Wi-Fi por perto, a antena ou o radio tem problema.");
  } else {
    falha("A busca de redes falhou.");
  }
  WiFi.scanDelete();
  WiFi.mode(WIFI_OFF);
}

const char *nomeDoReset(esp_reset_reason_t motivo) {
  switch (motivo) {
    // No ESP32 o botão EN (e o reset automático da gravação) também conta
    // como "ligou", não como pino externo.
    case ESP_RST_POWERON: return "ligou a alimentacao ou apertou EN";
    case ESP_RST_SW: return "reinicio pelo programa";
    case ESP_RST_PANIC: return "travamento (panic)";
    case ESP_RST_INT_WDT:
    case ESP_RST_TASK_WDT:
    case ESP_RST_WDT: return "watchdog";
    case ESP_RST_DEEPSLEEP: return "saiu do deep sleep";
    case ESP_RST_BROWNOUT: return "QUEDA DE TENSAO (brownout)";
    default: return "outro";
  }
}

void testarEnergia() {
  titulo("5. Alimentacao");
  esp_reset_reason_t motivo = esp_reset_reason();
  Serial.printf("  Motivo deste boot: %s\n", nomeDoReset(motivo));

  memoria.begin("teste-hw", false);  // false: cria o espaço se ainda não existe
  unsigned int quedas = memoria.getUInt("quedas", 0);
  memoria.end();

  if (quedas == 0) {
    ok("Nenhuma queda de tensao registrada.");
  } else {
    char texto[140];
    snprintf(texto, sizeof(texto), "%u reinicio(s) por queda de tensao registrado(s) (envie c para zerar).", quedas);
    if (quedas == 1) {
      aviso(texto);
      Serial.println("          Um so pode acontecer ao ligar a chave. Varios = a bateria ou o MT3608");
      Serial.println("          nao seguram o pico do Wi-Fi (bateria fraca, MT3608 abaixo de 5 V,");
      Serial.println("          fio fino ou solda ruim no caminho da energia).");
    } else {
      falha(texto);
      Serial.println("          A bateria ou o MT3608 nao seguram o pico do Wi-Fi. Confira: bateria");
      Serial.println("          acima de 3,6 V, MT3608 regulado em 5,0-5,2 V, soldas da chave e do VIN.");
    }
  }
  Serial.println("  (A tensao da bateria nao e medida: este circuito nao tem divisor no GPIO34.)");
}

/** Conta o boot atual se ele veio de uma queda de tensão. */
void registrarQuedaDeTensao() {
  if (esp_reset_reason() != ESP_RST_BROWNOUT) return;
  memoria.begin("teste-hw", false);
  memoria.putUInt("quedas", memoria.getUInt("quedas", 0) + 1);
  memoria.end();
}

void rodarTestes() {
  falhas = 0;
  avisos = 0;
  errosI2c = 0;

  Serial.println("\n==============================================");
  Serial.println(" Rehabit — teste de hardware do goniometro");
  Serial.println("==============================================");
  Serial.printf("Chip %s rev %d, %d nucleos, flash %u MB\n", ESP.getChipModel(),
                ESP.getChipRevision(), ESP.getChipCores(), (unsigned)(ESP.getFlashChipSize() / (1024 * 1024)));

  testarLed();
  testarI2c();
  testarMpu();
  testarWifi();
  testarEnergia();

  Serial.println("\n==============================================");
  if (falhas == 0 && avisos == 0) {
    Serial.println(" RESULTADO: tudo certo. Pode gravar o goniometro-esp32.ino.");
  } else if (falhas == 0) {
    Serial.printf(" RESULTADO: funciona, com %d aviso(s) acima para olhar.\n", avisos);
  } else {
    Serial.printf(" RESULTADO: %d falha(s) e %d aviso(s). Corrija as falhas antes do\n", falhas, avisos);
    Serial.println(" firmware principal.");
  }
  Serial.println("==============================================");
  if (mpuOk) {
    Serial.println("Modo movimento: incline a placa mais de 30 graus e o LED externo acende.");
    Serial.println("BOOT = nova posicao zero | r = repetir testes | c = zerar quedas");
  } else {
    Serial.println("MPU6050 com problema: o LED externo vai piscar rapido sem parar.");
    Serial.println("r = repetir testes | c = zerar quedas");
  }
  Serial.println();
}

// ------------------------------------------------------------------
// Modo movimento
// ------------------------------------------------------------------

float inclinacaoAtual() {
  float modA = sqrtf(ax * ax + ay * ay + az * az);
  float modR = sqrtf(refX * refX + refY * refY + refZ * refZ);
  if (modA < 0.001f || modR < 0.001f) return 0.0f;
  float cosseno = (ax * refX + ay * refY + az * refZ) / (modA * modR);
  if (cosseno > 1.0f) cosseno = 1.0f;
  if (cosseno < -1.0f) cosseno = -1.0f;
  return acosf(cosseno) * 180.0f / PI;
}

void tratarSerial() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == 'r' || c == 'R') {
      rodarTestes();
    } else if (c == 'c' || c == 'C') {
      memoria.begin("teste-hw", false);
      memoria.putUInt("quedas", 0);
      memoria.end();
      Serial.println("Contador de quedas de tensao zerado.");
    }
  }
}

void setup() {
  pinMode(PINO_LED_EXTERNO, OUTPUT);
  pinMode(PINO_LED_PLACA, OUTPUT);
  pinMode(PINO_BOTAO, INPUT_PULLUP);
  digitalWrite(PINO_LED_EXTERNO, LOW);
  digitalWrite(PINO_LED_PLACA, LOW);

  registrarQuedaDeTensao();

  Serial.begin(115200);
  delay(1500);  // dá tempo de o Monitor Serial conectar depois do reset
  rodarTestes();
}

void loop() {
  tratarSerial();
  unsigned long agora = millis();

  // Pisca de "estou vivo" no LED da placa: 50 ms a cada segundo.
  digitalWrite(PINO_LED_PLACA, (agora % 1000) < 50);

  if (!mpuOk) {
    digitalWrite(PINO_LED_EXTERNO, (agora / 100) % 2);
    return;
  }

  static unsigned long ultimaLeitura = 0;
  if (agora - ultimaLeitura < 20) return;
  ultimaLeitura = agora;

  bool leu = lerMpu();
  float inclinacao = inclinacaoAtual();
  if (leu) {
    digitalWrite(PINO_LED_EXTERNO, inclinacao > LIMITE_INCLINACAO_GRAUS);
  }

  // BOOT: a posição atual vira o zero. Uma vez por aperto, não a cada volta.
  static bool botaoAntes = false;
  bool botao = digitalRead(PINO_BOTAO) == LOW;
  if (botao && !botaoAntes && leu) {
    refX = ax;
    refY = ay;
    refZ = az;
    Serial.println("BOOT: posicao atual virou o novo zero.");
  }
  botaoAntes = botao;

  static unsigned long ultimoEco = 0;
  if (agora - ultimoEco >= 250) {
    ultimoEco = agora;
    Serial.printf("acel(g) %+5.2f %+5.2f %+5.2f |%4.2f| giro(graus/s) %+6.1f %+6.1f %+6.1f | %4.1f C | incl %5.1f | erros I2C %lu\n",
                  ax, ay, az, sqrtf(ax * ax + ay * ay + az * az), gxs, gys, gzs, temperatura,
                  inclinacao, errosI2c);
  }
}
