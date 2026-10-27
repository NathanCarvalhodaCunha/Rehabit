// Rehabit — goniômetro RESERVA (só ESP32 + MPU6050 + powerbank)
// Firmware 2.4-reserva
//
// Para o dia em que o aparelho principal não funcionar. Este precisa só de:
//   * uma placa ESP32 DevKit;
//   * um MPU6050 com 4 fios (serve clone: MPU6500, MPU9250...);
//   * um powerbank no USB da placa.
// Sem bateria 18650, sem TP4056, sem MT3608, sem LED externo, sem solda.
//
// Ele faz duas coisas ao mesmo tempo:
//
//   1. MODO LOCAL, sempre ligado: cria um Wi-Fi próprio, "Rehabit-Reserva-XXXX"
//      (senha rehabit123), com uma página em http://192.168.4.1 que mostra o
//      ângulo ao vivo, zera, mede a amplitude (mínimo, máximo e a diferença) e
//      desenha o movimento dos últimos 10 s. Não precisa de internet, de
//      servidor, de conta nem do site.
//
//   2. SITE REHABIT, quando houver Wi-Fi com internet: na mesma página, em
//      "Conectar ao site Rehabit", escolha a rede da clínica e digite o código
//      de 6 dígitos da tela Dispositivo. Daí em diante ele fala com o site do
//      mesmo jeito que o principal — ângulo ao vivo, tara, identificar,
//      captura e reinício pela tela Dispositivo — e o modo local continua
//      funcionando junto.
//
// Para esquecer o Wi-Fi e o pareamento: botão na página, ou segure o BOOT por
// 5 segundos. A tara não se perde.
//
// NENHUMA BIBLIOTECA PARA INSTALAR: só o pacote da placa ESP32. O sensor é
// lido direto nos registradores (a biblioteca da Adafruit recusa os clones, e
// muito MPU6050 vendido por aí é clone), e a página de configuração é feita
// aqui mesmo, sem o WiFiManager.
//
// ================== LIGAÇÕES ==================
//   MPU6050 VCC -> 3V3
//   MPU6050 GND -> GND
//   MPU6050 SDA -> GPIO21
//   MPU6050 SCL -> GPIO22
//   Powerbank   -> USB da placa (o mesmo cabo de gravar serve)
//   Luz         -> o LED azul que já vem soldado na placa (GPIO2)
//   Botão       -> o BOOT, que já vem na placa (GPIO0)
//
// A medida é a mesma do principal: o ângulo entre a gravidade de agora e a
// gravidade na hora da tara, de 0 a 180. Zere com o braço PENDURADO ao lado do
// tronco; na horizontal dá 90°, acima da cabeça ~180°. Vale com o paciente de
// pé ou sentado ereto (detalhes no guia do firmware principal, Parte 3).
//
// Passo a passo de gravar e usar: goniometro-reserva-GUIA.md, ao lado.

#include <WiFi.h>
#include <WebServer.h>
#include <DNSServer.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <Wire.h>
#include <Preferences.h>
#include <esp_mac.h>

// ------------------------------------------------------------------
// Configuração
// ------------------------------------------------------------------

// Onde o Rehabit está publicado — o mesmo endereço do firmware principal.
const char *BASE_URL = "https://rehabit-api-4tex.onrender.com/api";

// Aparece no campo "Firmware" da tela Dispositivo: dá para saber de longe que
// quem está mandando é o aparelho reserva.
const char *VERSAO_FIRMWARE = "2.4-reserva";

// O nome ganha o fim do número de série, para dois aparelhos reserva na mesma
// sala não terem o mesmo Wi-Fi. A senha está publicada aqui e no guia: ela só
// evita que um celular qualquer entre por acaso (veja o guia, "Segurança").
const char *PREFIXO_REDE_PROPRIA = "Rehabit-Reserva-";
const char *SENHA_REDE_PROPRIA = "rehabit123";
const IPAddress IP_PROPRIO(192, 168, 4, 1);
const IPAddress MASCARA_PROPRIA(255, 255, 255, 0);

const int PINO_SDA = 21;
const int PINO_SCL = 22;
// O LED que já vem soldado na maioria das DevKit. Numa placa em que ele fica
// em outro pino, ou que acende com o pino em LOW, ajuste as duas linhas.
const int PINO_LED = 2;
const bool LED_ACENDE_EM_HIGH = true;
const int PINO_BOTAO = 0;  // BOOT

// Espaço próprio na memória interna: gravar este firmware por cima do principal
// (ou o contrário) não mistura token, rede nem tara de um com os do outro.
const char *MEMORIA = "reserva";

const unsigned long SEGURAR_PARA_ESQUECER_MS = 5000;

// Peso do giroscópio no filtro complementar — o mesmo do principal.
const float PESO_GIRO = 0.98f;
const unsigned long PERIODO_AMOSTRA_US = 10000;  // 100 Hz de leitura do sensor

// 100 kHz, e não os 400 do principal: aqui o sensor costuma ir na protoboard,
// com fio comprido, e o barramento lento erra bem menos. 100 leituras de 14
// bytes por segundo ocupam uns 16% dele, sobra folga.
const uint32_t FREQUENCIA_I2C = 100000;
const int FALHAS_PARA_PERDER_SENSOR = 50;  // 0,5 s seguidos sem leitura boa
const unsigned long REPROCURAR_SENSOR_MS = 2000;

const int AMOSTRAS_CALIBRACAO = 400;
const unsigned long PERIODO_CALIBRACAO_US = 5000;  // 400 x 5 ms = 2 s parado
// Desvio padrão do giroscópio acima disto durante a calibração = alguém mexeu.
// Parado na mesa dá uns 0,002 rad/s; na mão, passa fácil de 0,02.
const float TREMOR_MAXIMO_RAD_S = 0.02f;
const int TENTATIVAS_CALIBRACAO = 3;

// Cada tentativa de entrar no Wi-Fi da clínica, e a espera até a próxima. A
// espera não é só educação: enquanto procura a rede, o rádio sai do canal da
// rede própria e a página no celular engasga.
const unsigned long CONEXAO_MS = 15000;
const unsigned long ESPERA_ENTRE_TENTATIVAS_MS = 15000;

const unsigned long TIMEOUT_HTTP_MS = 4000;
// O pareamento acontece uma vez só e o servidor gratuito pode estar acordando.
const unsigned long TIMEOUT_PAREAMENTO_MS = 15000;
const unsigned long REPAREAR_MS = 5000;
const unsigned long INTERVALO_INICIAL_MS = 2000;
// Token recusado (aparelho revogado): não adianta insistir a cada 400 ms.
const unsigned long INTERVALO_RECUSADO_MS = 10000;
// Os dois limites do principal (veja lá o porquê de serem iguais).
const unsigned long SERVIDOR_CALADO_MS = 10000;
const unsigned long CAPTURA_SEM_CONTATO_MS = 10000;

// Uma busca de redes vale por este tempo; abrir a página de novo logo depois
// mostra a mesma lista, sem derrubar a conexão do celular para procurar outra
// vez.
const unsigned long REDES_VALEM_MS = 15000;

extern const char RAIZES_TLS[];
extern const char PAGINA[];

// ------------------------------------------------------------------
// Estados (constantes uint8_t, e não enum: veja "Erros de compilação
// conhecidos" no guia do principal)
// ------------------------------------------------------------------

// Wi-Fi da clínica
const uint8_t REDE_SEM_CONFIG = 0;  // nenhuma rede salva: só o modo local
const uint8_t REDE_CONECTANDO = 1;
const uint8_t REDE_CONECTADA = 2;
const uint8_t REDE_FALHOU = 3;  // não entrou; tenta de novo sozinho

// Pareamento com o site (o que a página conta sobre o último código digitado)
const uint8_t PAR_NADA = 0;
const uint8_t PAR_PAREANDO = 1;
const uint8_t PAR_OK = 2;
const uint8_t PAR_RECUSADO = 3;      // código vencido, já usado ou errado
const uint8_t PAR_SEM_RESPOSTA = 4;  // servidor calado; tenta de novo

// Registradores do MPU6050 (os mesmos nos clones MPU6500/9250)
const uint8_t REG_SMPLRT_DIV = 0x19;
const uint8_t REG_CONFIG = 0x1A;
const uint8_t REG_GYRO_CONFIG = 0x1B;
const uint8_t REG_ACCEL_CONFIG = 0x1C;
const uint8_t REG_ACCEL_XOUT_H = 0x3B;
const uint8_t REG_PWR_MGMT_1 = 0x6B;
const uint8_t REG_WHO_AM_I = 0x75;

// Mesmas escalas do principal: ±4 g e ±500 °/s.
const float LSB_POR_G = 8192.0f;
const float LSB_POR_GRAU_S = 65.5f;
const float GRAVIDADE = 9.80665f;
const float GRAU_PARA_RAD = 0.01745329252f;

// ------------------------------------------------------------------
// Estado
// ------------------------------------------------------------------
//
// Três tarefas dividem o trabalho:
//   * loop(): sensor a 100 Hz, filtro, tara, medida de amplitude e a página
//     (servidor web e DNS). Nunca fica parada esperando a rede.
//   * tarefaDaRede: Wi-Fi da clínica, pareamento e telemetria para o site.
//     Um envio sem resposta prende só ela, por até 8 s, e a página segue viva.
//   * tarefaDaLuz: o LED, no ritmo dele.
// O que passa de uma para outra são variáveis simples de até 32 bits
// (volatile, como no principal) ou textos protegidos por travaTextos.

WebServer servidor(80);
DNSServer dns;
Preferences memoria;  // só a loop() usa esta; a tarefa da rede abre a sua

String numeroSerie;
String redeSalva;  // nome da rede da clínica, para a página; só a loop() escreve

// --- só da loop() ---
uint8_t enderecoMpu = 0;
bool barramentoAberto = false;
bool sensorPronto = false;  // achou o chip (calibrando ou medindo)
bool biasMedido = false;    // já calibrou uma vez desde que ligou
int falhasSeguidas = 0;
unsigned long ultimaProcuraSensor = 0;
int16_t ultimoBruto[6] = {0, 0, 0, 0, 0, 0};
int leiturasIguais = 0;

int amostrasCalibracao = 0;
int tentativaCalibracao = 0;
double somaGiro[3] = {0, 0, 0};
double quadradoGiro[3] = {0, 0, 0};

/* Gravidade estimada no referencial da placa, a pose de zero e o bias do
   giroscópio: exatamente como no principal. */
float gx = 0.0f, gy = 0.0f, gz = 1.0f;
float refX = 0.0f, refY = 0.0f, refZ = 0.0f;
bool referenciaDefinida = false;
float biasGiroX = 0.0f, biasGiroY = 0.0f, biasGiroZ = 0.0f;
float acelBrutoX = 0.0f, acelBrutoY = 0.0f, acelBrutoZ = 0.0f;
unsigned long ultimaAmostraUs = 0;

// Medida de amplitude da página (não tem nada a ver com a captura do site).
float minimoLocal = 0.0f, maximoLocal = 0.0f;
unsigned long inicioGravacaoLocal = 0;
unsigned long duracaoGravacaoMs = 0;
bool temResultado = false;

// --- compartilhado entre as tarefas ---
volatile bool sensorAusente = false;
volatile bool calibrando = true;  // do ligar até o fim da calibração
volatile bool calibradoParado = true;
volatile bool gravandoLocal = false;
volatile float anguloPublicado = 0.0f;
volatile float anguloBrutoPublicado = 0.0f;

volatile uint8_t estadoRede = REDE_SEM_CONFIG;
volatile uint16_t motivoDaQueda = 0;  // código do Wi-Fi para a última recusa
volatile bool temToken = false;
volatile bool pareamentoRecusado = false;  // a API respondeu 401 ou 403
volatile uint8_t estadoPareamento = PAR_NADA;
volatile bool capturando = false;
volatile uint32_t ultimoEnvioOk = 0;  // millis() da última telemetria aceita
volatile uint32_t silencioDesde = 0;  // último 200 ou última conexão ao Wi-Fi

volatile uint8_t pedidosIdentificacao = 0;  // IDENTIFICAR do site
volatile uint8_t pedidosConfirmacao = 0;    // tara feita, configuração esquecida
volatile uint8_t pedidosTara = 0;           // TARAR do site, aplicado pela loop()

volatile bool configMudou = false;  // a página salvou ou esqueceu a rede
volatile bool pedidoBusca = false;
volatile bool buscandoRedes = false;
volatile uint32_t ultimaBuscaMs = 0;

SemaphoreHandle_t travaTextos;
String redesJson = "[]";  // resultado da última busca de redes
String nomeClinica;
String ipNaRede;  // IP no Wi-Fi da clínica, quando conectado

// --- só da tarefaDaRede ---
WiFiClientSecure clienteSeguro;
String redeAlvo, senhaAlvo, codigoPendente, tokenDispositivo;
bool tentandoConectar = false;
uint32_t inicioTentativa = 0;
uint32_t esperarAte = 0;
uint32_t proximoPareamento = 0;
unsigned long proximoEnvio = 0;
unsigned long intervaloEnvioMs = INTERVALO_INICIAL_MS;
bool capturaLargadaPorSilencio = false;

// ------------------------------------------------------------------
// Apoio
// ------------------------------------------------------------------

/** Texto pronto para ir entre aspas num JSON. */
String escaparJson(const String &texto) {
  String saida;
  saida.reserve(texto.length() + 8);
  for (size_t i = 0; i < texto.length(); i++) {
    char c = texto.charAt(i);
    if (c == '"' || c == '\\') {
      saida += '\\';
      saida += c;
    } else if ((uint8_t)c < 0x20) {
      char codigo[8];
      snprintf(codigo, sizeof(codigo), "\\u%04x", (uint8_t)c);
      saida += codigo;
    } else {
      saida += c;
    }
  }
  return saida;
}

const char *textoBool(bool valor) {
  return valor ? "true" : "false";
}

// Os leitores de JSON do principal, iguais: as respostas da API são pequenas e
// de formato conhecido, e não vale puxar uma biblioteca para isso.

String extrairTexto(const String &json, const char *chave) {
  String alvo = String("\"") + chave + "\":\"";
  int inicio = json.indexOf(alvo);
  if (inicio < 0) return "";
  inicio += alvo.length();
  String valor = "";
  for (int i = inicio; i < (int)json.length(); i++) {
    char c = json.charAt(i);
    if (c == '\\' && i + 1 < (int)json.length()) {
      valor += json.charAt(++i);
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

/** 1 para true, 0 para false e -1 se a chave não veio. */
int extrairBooleano(const String &json, const char *chave) {
  String alvo = String("\"") + chave + "\":";
  int inicio = json.indexOf(alvo);
  if (inicio < 0) return -1;
  inicio += alvo.length();
  while (inicio < (int)json.length() && json.charAt(inicio) == ' ') inicio++;
  if (json.startsWith("true", inicio)) return 1;
  if (json.startsWith("false", inicio)) return 0;
  return -1;
}

// Número de série do MAC, como no principal: na mesma placa, os dois firmwares
// aparecem no site com o mesmo número.
String montarNumeroSerie() {
  uint8_t mac[6];
  esp_read_mac(mac, ESP_MAC_WIFI_STA);
  char buffer[10];
  snprintf(buffer, sizeof(buffer), "%02X%02X-%02X%02X", mac[2], mac[3], mac[4], mac[5]);
  return String(buffer);
}

// ------------------------------------------------------------------
// Memória interna (NVS) — lado da loop()
// ------------------------------------------------------------------

String lerDaMemoria(const char *chave) {
  memoria.begin(MEMORIA, true);
  String valor = memoria.getString(chave, "");
  memoria.end();
  return valor;
}

void salvarReferencia(float x, float y, float z) {
  memoria.begin(MEMORIA, false);
  memoria.putFloat("refx", x);
  memoria.putFloat("refy", y);
  memoria.putFloat("refz", z);
  memoria.end();
}

/** Preenche refX/Y/Z do que estiver guardado; devolve false se nunca houve tara. */
bool carregarReferencia() {
  memoria.begin(MEMORIA, true);
  refX = memoria.getFloat("refx", 0.0f);
  refY = memoria.getFloat("refy", 0.0f);
  refZ = memoria.getFloat("refz", 0.0f);
  memoria.end();
  return sqrtf(refX * refX + refY * refY + refZ * refZ) > 0.5f;
}

/**
 * Apaga a rede da clínica e o pareamento — não a tara, que é do paciente e não
 * do lugar. Vem do botão da página ou dos 5 s no BOOT.
 */
void esquecerRedeEPareamento() {
  memoria.begin(MEMORIA, false);
  memoria.remove("rede");
  memoria.remove("senha");
  memoria.remove("codigo");
  memoria.remove("token");
  memoria.remove("clinica");
  memoria.end();
  redeSalva = "";
  configMudou = true;
  pedidosConfirmacao = pedidosConfirmacao + 1;
  Serial.println("Wi-Fi e pareamento esquecidos. A tara continua guardada.");
}

// ------------------------------------------------------------------
// Sensor: MPU6050 direto nos registradores
// ------------------------------------------------------------------
//
// O mesmo jeito do teste-hardware.ino: sem a biblioteca da Adafruit, que
// recusa qualquer chip cujo WHO_AM_I não seja 0x68. Aqui serve qualquer chip
// da família que responda em 0x68 ou 0x69.

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

/**
 * Acelerômetro em m/s² e giroscópio em rad/s, numa leitura só de 14 bytes —
 * as mesmas unidades que a biblioteca entregava ao principal, para o filtro
 * ser o mesmo. Devolve false se o chip não respondeu ou parece travado.
 */
bool lerSensor(float &ax, float &ay, float &az, float &wx, float &wy, float &wz) {
  Wire.beginTransmission(enderecoMpu);
  Wire.write(REG_ACCEL_XOUT_H);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(enderecoMpu, (uint8_t)14) != 14) return false;

  int16_t bruto[7];
  for (int i = 0; i < 7; i++) bruto[i] = lerPalavra();  // bruto[3] é a temperatura

  // Sensor vivo tem ruído: até parado na mesa, algum dos seis números muda de
  // uma leitura para a outra. Cem leituras idênticas seguidas (1 s) é chip
  // travado ou dormindo, e o filtro mostraria um ângulo congelado como se
  // estivesse tudo bem.
  bool igual = true;
  for (int i = 0; i < 6; i++) {
    int16_t valor = bruto[i < 3 ? i : i + 1];
    if (valor != ultimoBruto[i]) igual = false;
    ultimoBruto[i] = valor;
  }
  leiturasIguais = igual ? leiturasIguais + 1 : 0;
  if (leiturasIguais >= 100) return false;

  ax = bruto[0] / LSB_POR_G * GRAVIDADE;
  ay = bruto[1] / LSB_POR_G * GRAVIDADE;
  az = bruto[2] / LSB_POR_G * GRAVIDADE;
  wx = bruto[4] / LSB_POR_GRAU_S * GRAU_PARA_RAD;
  wy = bruto[5] / LSB_POR_GRAU_S * GRAU_PARA_RAD;
  wz = bruto[6] / LSB_POR_GRAU_S * GRAU_PARA_RAD;
  return true;
}

/** Procura o chip, acorda e configura. Devolve true se ele está medindo. */
bool iniciarSensor() {
  // Recomeçar o barramento do zero também solta um SDA preso por um fio que
  // mexeu no meio de uma leitura.
  if (barramentoAberto) Wire.end();
  barramentoAberto = Wire.begin(PINO_SDA, PINO_SCL, FREQUENCIA_I2C);
  if (!barramentoAberto) return false;

  enderecoMpu = 0;
  const uint8_t enderecos[] = {0x68, 0x69};  // 0x69 quando o AD0 está em 3V3
  for (uint8_t endereco : enderecos) {
    Wire.beginTransmission(endereco);
    if (Wire.endTransmission() == 0) {
      enderecoMpu = endereco;
      break;
    }
  }
  if (enderecoMpu == 0) return false;

  int quemSou = lerRegistrador(REG_WHO_AM_I);
  if (quemSou < 0) return false;

  bool configurado = escreverRegistrador(REG_PWR_MGMT_1, 0x80);  // reset
  delay(100);
  configurado = configurado && escreverRegistrador(REG_PWR_MGMT_1, 0x01);    // acorda, relógio do giro X
  configurado = configurado && escreverRegistrador(REG_SMPLRT_DIV, 9);       // 100 Hz
  configurado = configurado && escreverRegistrador(REG_CONFIG, 0x04);        // filtro ~21 Hz, como no principal
  configurado = configurado && escreverRegistrador(REG_GYRO_CONFIG, 0x08);   // ±500 °/s
  configurado = configurado && escreverRegistrador(REG_ACCEL_CONFIG, 0x08);  // ±4 g
  delay(50);
  if (!configurado) return false;

  // Primeira leitura: confere que o chip mede uma gravidade com sentido (um
  // clone defeituoso responde ao endereço e devolve só zeros).
  leiturasIguais = 0;
  float ax, ay, az, wx, wy, wz;
  if (!lerSensor(ax, ay, az, wx, wy, wz)) return false;
  float modulo = sqrtf(ax * ax + ay * ay + az * az);
  if (modulo < 4.0f || modulo > 20.0f) {
    Serial.printf("O sensor respondeu, mas a gravidade lida (%.1f m/s2) nao faz sentido.\n", modulo);
    return false;
  }
  acelBrutoX = ax;
  acelBrutoY = ay;
  acelBrutoZ = az;

  Serial.printf("Sensor encontrado no endereco 0x%02X (WHO_AM_I 0x%02X%s).\n", enderecoMpu, quemSou,
                quemSou == 0x68 ? ", MPU6050" : ", clone ou variante: funciona igual");
  return true;
}

void encerrarGravacaoLocal() {
  if (!gravandoLocal) return;
  gravandoLocal = false;
  duracaoGravacaoMs = millis() - inicioGravacaoLocal;
  temResultado = true;
}

/** Sem leitura boa por 0,5 s: fio solto, ou o chip travou. */
void perderSensor() {
  sensorPronto = false;
  sensorAusente = true;
  calibrando = false;
  encerrarGravacaoLocal();
  ultimaProcuraSensor = millis();
  Serial.println("O sensor parou de responder (fio solto?). Procurando de novo a cada 2 s...");
}

// ------------------------------------------------------------------
// Ângulo (a mesma conta do principal)
// ------------------------------------------------------------------

/** Ângulo entre dois vetores, em graus (0 a 180). */
float anguloEntre(float ax, float ay, float az, float bx, float by, float bz) {
  float modA = sqrtf(ax * ax + ay * ay + az * az);
  float modB = sqrtf(bx * bx + by * by + bz * bz);
  if (modA < 0.001f || modB < 0.001f) {
    return 0.0f;
  }
  float cosseno = (ax * bx + ay * by + az * bz) / (modA * modB);
  if (cosseno > 1.0f) cosseno = 1.0f;
  if (cosseno < -1.0f) cosseno = -1.0f;
  return acosf(cosseno) * 180.0f / PI;
}

float anguloAtual() {
  if (!referenciaDefinida) return 0.0f;
  return anguloEntre(gx, gy, gz, refX, refY, refZ);
}

float anguloSemFiltro() {
  if (!referenciaDefinida) return 0.0f;
  return anguloEntre(acelBrutoX, acelBrutoY, acelBrutoZ, refX, refY, refZ);
}

bool medindo() {
  return sensorPronto && !calibrando;
}

/** Deixa o ângulo de agora à vista das outras tarefas e da medida de amplitude. */
void publicarAngulo() {
  float angulo = anguloAtual();
  anguloPublicado = angulo;
  anguloBrutoPublicado = anguloSemFiltro();
  if (gravandoLocal) {
    if (angulo < minimoLocal) minimoLocal = angulo;
    if (angulo > maximoLocal) maximoLocal = angulo;
  }
}

/** Calibração pronta (ou já feita antes): o filtro parte da gravidade medida. */
void comecarAMedir() {
  gx = acelBrutoX;
  gy = acelBrutoY;
  gz = acelBrutoZ;
  ultimaAmostraUs = 0;  // a próxima amostra não integra o giroscópio
  falhasSeguidas = 0;
  if (!referenciaDefinida) {
    // Nunca houve tara: a pose de agora vira o zero provisório, como no
    // principal. Zere de verdade com o braço pendurado.
    refX = gx;
    refY = gy;
    refZ = gz;
    referenciaDefinida = true;
    Serial.println("Sem tara guardada: usando a posicao de agora como zero provisorio.");
  }
  calibrando = false;
  publicarAngulo();
  Serial.println("Medindo.");
}

void comecarCalibracao() {
  amostrasCalibracao = 0;
  tentativaCalibracao = 0;
  for (int i = 0; i < 3; i++) somaGiro[i] = quadradoGiro[i] = 0.0;
  ultimaAmostraUs = 0;
  falhasSeguidas = 0;
  calibrando = true;
  Serial.println("Calibrando o giroscopio: mantenha o aparelho PARADO por 2 s...");
}

/**
 * Uma amostra da calibração por vez, a cada 5 ms, sem travar a loop(): a
 * página continua respondendo e mostra "Calibrando: não mexa".
 *
 * Diferente do principal, ela confere se o aparelho ficou parado. Se alguém
 * mexeu, o bias sai torto; ela repete até 3 vezes e, se não der, segue assim
 * mesmo e avisa na página.
 */
void avancarCalibracao() {
  unsigned long agoraUs = micros();
  if (ultimaAmostraUs != 0 && agoraUs - ultimaAmostraUs < PERIODO_CALIBRACAO_US) return;
  ultimaAmostraUs = agoraUs;

  float ax, ay, az, wx, wy, wz;
  if (!lerSensor(ax, ay, az, wx, wy, wz)) {
    if (++falhasSeguidas >= FALHAS_PARA_PERDER_SENSOR) perderSensor();
    return;
  }
  falhasSeguidas = 0;
  acelBrutoX = ax;
  acelBrutoY = ay;
  acelBrutoZ = az;
  const float giro[3] = {wx, wy, wz};
  for (int i = 0; i < 3; i++) {
    somaGiro[i] += giro[i];
    quadradoGiro[i] += (double)giro[i] * giro[i];
  }
  if (++amostrasCalibracao < AMOSTRAS_CALIBRACAO) return;

  float media[3];
  float tremor = 0.0f;
  for (int i = 0; i < 3; i++) {
    media[i] = somaGiro[i] / AMOSTRAS_CALIBRACAO;
    double variancia = quadradoGiro[i] / AMOSTRAS_CALIBRACAO - (double)media[i] * media[i];
    float desvio = variancia > 0.0 ? sqrtf((float)variancia) : 0.0f;
    if (desvio > tremor) tremor = desvio;
  }
  bool parado = tremor < TREMOR_MAXIMO_RAD_S;
  tentativaCalibracao++;
  if (!parado && tentativaCalibracao < TENTATIVAS_CALIBRACAO) {
    Serial.printf("Mexeu durante a calibracao (tremor %.3f rad/s). De novo (%d de %d)...\n", tremor,
                  tentativaCalibracao + 1, TENTATIVAS_CALIBRACAO);
    amostrasCalibracao = 0;
    for (int i = 0; i < 3; i++) somaGiro[i] = quadradoGiro[i] = 0.0;
    return;
  }

  biasGiroX = media[0];
  biasGiroY = media[1];
  biasGiroZ = media[2];
  biasMedido = true;
  calibradoParado = parado;
  Serial.printf("Giroscopio calibrado (bias X=%.4f Y=%.4f Z=%.4f rad/s)%s\n", biasGiroX, biasGiroY, biasGiroZ,
                parado ? "" : " — mas o aparelho mexeu: para refazer, desligue e ligue com ele parado.");
  comecarAMedir();
}

/** Filtro complementar sobre o vetor de gravidade — o mesmo do principal. */
void atualizarAngulo() {
  unsigned long agoraUs = micros();
  if (ultimaAmostraUs != 0 && (agoraUs - ultimaAmostraUs) < PERIODO_AMOSTRA_US) {
    return;
  }
  float dt = ultimaAmostraUs == 0 ? 0.0f : (agoraUs - ultimaAmostraUs) / 1000000.0f;
  ultimaAmostraUs = agoraUs;

  float ax, ay, az, wx, wy, wz;
  if (!lerSensor(ax, ay, az, wx, wy, wz)) {
    if (++falhasSeguidas >= FALHAS_PARA_PERDER_SENSOR) perderSensor();
    return;
  }
  falhasSeguidas = 0;
  acelBrutoX = ax;
  acelBrutoY = ay;
  acelBrutoZ = az;

  // Um buraco grande (sensor voltando, loop() presa um instante): integrar o
  // giroscópio por ele só daria lixo, então a estimativa volta direto para a
  // gravidade medida.
  if (dt <= 0.0f || dt > 0.2f) {
    gx = ax;
    gy = ay;
    gz = az;
  } else {
    wx -= biasGiroX;
    wy -= biasGiroY;
    wz -= biasGiroZ;

    // g_previsto = g - (omega x g) * dt
    float previstoX = gx - (wy * gz - wz * gy) * dt;
    float previstoY = gy - (wz * gx - wx * gz) * dt;
    float previstoZ = gz - (wx * gy - wy * gx) * dt;

    gx = PESO_GIRO * previstoX + (1.0f - PESO_GIRO) * ax;
    gy = PESO_GIRO * previstoY + (1.0f - PESO_GIRO) * ay;
    gz = PESO_GIRO * previstoZ + (1.0f - PESO_GIRO) * az;
  }
  publicarAngulo();
}

/** Fixa a pose atual como 0°. */
void aplicarTara() {
  refX = gx;
  refY = gy;
  refZ = gz;
  referenciaDefinida = true;
  salvarReferencia(refX, refY, refZ);
  publicarAngulo();
  pedidosConfirmacao = pedidosConfirmacao + 1;
  Serial.printf("Tara aplicada: a posicao atual virou 0 grau (g = %.2f, %.2f, %.2f).\n", refX, refY, refZ);
}

/** Procura o sensor de tempos em tempos, calibra, mede. Chamada pela loop(). */
void cuidarDoSensor() {
  if (sensorPronto) {
    if (calibrando) {
      avancarCalibracao();
    } else {
      atualizarAngulo();
    }
    return;
  }

  unsigned long agora = millis();
  if (ultimaProcuraSensor != 0 && agora - ultimaProcuraSensor < REPROCURAR_SENSOR_MS) return;
  ultimaProcuraSensor = agora == 0 ? 1 : agora;

  if (iniciarSensor()) {
    sensorPronto = true;
    sensorAusente = false;
    // Voltou depois de um fio solto: o bias de antes ainda vale, e pedir 2 s
    // parado no meio de uma medida só atrapalharia.
    if (biasMedido) {
      comecarAMedir();
    } else {
      comecarCalibracao();
    }
    return;
  }
  if (!sensorAusente) {
    Serial.println("MPU6050 nao encontrado! Confira os 4 fios: VCC->3V3, GND->GND, SDA->GPIO21, SCL->GPIO22.");
    Serial.println("Procurando de novo a cada 2 s; a pagina do aparelho continua no ar.");
  }
  sensorAusente = true;
  calibrando = false;
}

/** TARAR que chegou do site: a tarefa da rede só avisa, quem mexe no filtro é a loop(). */
void aplicarPedidosDoSite() {
  static uint8_t tarasVistas = 0;
  uint8_t pedidos = pedidosTara;
  if (pedidos == tarasVistas) return;
  tarasVistas = pedidos;
  if (medindo()) aplicarTara();
}

// ------------------------------------------------------------------
// LED de status
// ------------------------------------------------------------------
//
// Do mais forte para o mais fraco — valendo duas coisas ao mesmo tempo,
// aparece a de cima. Os ritmos são os do principal, mais dois:
//
//   sensor não encontrado ..... pisca rápido sem parar (150 aceso / 150 apagado)
//   identificar ............... pisca muito rápido por 4 s (80 / 80)
//   confirmação ............... três piscadas rápidas: zerou, ou esqueceu a rede
//   gravando .................. acesa fixa (captura do site ou medida da página)
//   calibrando, logo ao ligar . fraca e parada: não mexa no aparelho
//   modo local ................ "respirando": nenhuma rede da clínica salva
//   sem Wi-Fi ................. pisca devagar (500 / 500)
//   sem pareamento ............ pisca devagar (500 / 500), igual ao de cima
//   servidor não responde ..... piscada dupla a cada 2 s
//   tudo certo ................ piscada curtinha a cada 2 s (60 ms)
//
// Roda numa tarefa própria, como no principal: nem a calibração nem a rede
// mudam o ritmo dela.

const uint8_t LUZ_SEM_SENSOR = 0;
const uint8_t LUZ_IDENTIFICANDO = 1;
const uint8_t LUZ_CONFIRMANDO = 2;
const uint8_t LUZ_GRAVANDO = 3;
const uint8_t LUZ_CALIBRANDO = 4;
const uint8_t LUZ_MODO_LOCAL = 5;
const uint8_t LUZ_SEM_WIFI = 6;
const uint8_t LUZ_SEM_PAREAMENTO = 7;
const uint8_t LUZ_SEM_SERVIDOR = 8;
const uint8_t LUZ_TUDO_CERTO = 9;

const uint32_t PERIODO_LUZ_MS = 10;
const uint32_t DURACAO_IDENTIFICACAO_MS = 4000;
const uint32_t DURACAO_CONFIRMACAO_MS = 600;
const uint32_t CICLO_RESPIRAR_MS = 3000;

const uint32_t LUZ_PWM_HZ = 5000;
const uint8_t LUZ_PWM_BITS = 12;
const uint32_t LUZ_DUTY_MAXIMO = 4095;

const uint8_t BRILHO_TOTAL = 255;
const uint8_t BRILHO_CALIBRANDO = 38;
const uint8_t BRILHO_RESPIRAR_MINIMO = 10;

uint8_t piscar(uint32_t tempoMs, uint32_t acesoMs, uint32_t cicloMs) {
  return tempoMs % cicloMs < acesoMs ? BRILHO_TOTAL : 0;
}

uint8_t brilhoDoModo(uint8_t modo, uint32_t agoraMs, uint32_t inicioModoMs) {
  uint32_t t = agoraMs - inicioModoMs;
  switch (modo) {
    case LUZ_SEM_SENSOR:
      return piscar(t, 150, 300);
    case LUZ_IDENTIFICANDO:
      return piscar(t, 80, 160);
    case LUZ_CONFIRMANDO:
      return piscar(t, 100, 200);
    case LUZ_GRAVANDO:
      return BRILHO_TOTAL;
    case LUZ_CALIBRANDO:
      return BRILHO_CALIBRANDO;
    case LUZ_MODO_LOCAL: {
      float onda = 0.5f - 0.5f * cosf(6.2831853f * (t % CICLO_RESPIRAR_MS) / CICLO_RESPIRAR_MS);
      return (uint8_t)(BRILHO_RESPIRAR_MINIMO + (BRILHO_TOTAL - BRILHO_RESPIRAR_MINIMO) * onda + 0.5f);
    }
    case LUZ_SEM_WIFI:
    case LUZ_SEM_PAREAMENTO:
      return piscar(t, 500, 1000);
    case LUZ_SEM_SERVIDOR: {
      uint32_t fase = t % 2000;
      return (fase < 100 || (fase >= 250 && fase < 350)) ? BRILHO_TOTAL : 0;
    }
    default:  // LUZ_TUDO_CERTO
      return piscar(t, 60, 2000);
  }
}

/** Correção de gama 2, como no principal: o olho não é linear. */
uint32_t dutyDoBrilho(uint8_t brilho) {
  return ((uint32_t)brilho * brilho * LUZ_DUTY_MAXIMO + 255 * 255 / 2) / (255 * 255);
}

uint8_t modoDaLuz(bool identificando, bool confirmando, uint8_t rede, uint32_t semEnvioOkMs, uint32_t silencioMs) {
  if (sensorAusente) return LUZ_SEM_SENSOR;
  if (identificando) return LUZ_IDENTIFICANDO;
  if (confirmando) return LUZ_CONFIRMANDO;
  if (gravandoLocal || (capturando && semEnvioOkMs <= CAPTURA_SEM_CONTATO_MS)) return LUZ_GRAVANDO;
  if (calibrando) return LUZ_CALIBRANDO;
  if (rede == REDE_SEM_CONFIG) return LUZ_MODO_LOCAL;
  if (rede != REDE_CONECTADA) return LUZ_SEM_WIFI;
  if (!temToken || pareamentoRecusado) return LUZ_SEM_PAREAMENTO;
  if (silencioMs > SERVIDOR_CALADO_MS) return LUZ_SEM_SERVIDOR;
  return LUZ_TUDO_CERTO;
}

void escreverNoLed(uint32_t duty) {
  if (!LED_ACENDE_EM_HIGH) duty = LUZ_DUTY_MAXIMO - duty;
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(PINO_LED, duty);
#else
  ledcWrite(0, duty);
#endif
}

void tarefaDaLuz(void *) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(PINO_LED, LUZ_PWM_HZ, LUZ_PWM_BITS);
#else
  ledcSetup(0, LUZ_PWM_HZ, LUZ_PWM_BITS);
  ledcAttachPin(PINO_LED, 0);
#endif

  uint8_t modoAtual = 0xFF;
  uint32_t inicioModo = 0;
  uint32_t dutyAtual = 0xFFFFFFFF;
  uint8_t identificacoesVistas = pedidosIdentificacao;
  uint8_t confirmacoesVistas = pedidosConfirmacao;
  uint32_t inicioIdentificacao = 0, inicioConfirmacao = 0;
  bool identificando = false, confirmando = false;

  for (;;) {
    // A ordem das leituras importa, como no principal: a tarefa da rede acerta
    // silencioDesde ANTES de marcar a rede como conectada, e o relógio vem por
    // último, para "agora" nunca ficar antes das marcas de tempo.
    uint8_t rede = estadoRede;
    uint32_t ultimoOk = ultimoEnvioOk;
    uint32_t silencio = silencioDesde;
    uint32_t agora = millis();

    uint8_t pedidos = pedidosIdentificacao;
    if (pedidos != identificacoesVistas) {
      identificacoesVistas = pedidos;
      inicioIdentificacao = agora;
      identificando = true;
      modoAtual = 0xFF;
    }
    if (identificando && agora - inicioIdentificacao >= DURACAO_IDENTIFICACAO_MS) identificando = false;

    pedidos = pedidosConfirmacao;
    if (pedidos != confirmacoesVistas) {
      confirmacoesVistas = pedidos;
      inicioConfirmacao = agora;
      confirmando = true;
      modoAtual = 0xFF;
    }
    if (confirmando && agora - inicioConfirmacao >= DURACAO_CONFIRMACAO_MS) confirmando = false;

    uint8_t modo = modoDaLuz(identificando, confirmando, rede, agora - ultimoOk, agora - silencio);
    if (modo != modoAtual) {
      modoAtual = modo;
      inicioModo = agora;
    }

    uint32_t duty = dutyDoBrilho(brilhoDoModo(modo, agora, inicioModo));
    if (duty != dutyAtual) {
      dutyAtual = duty;
      escreverNoLed(duty);
    }
    vTaskDelay(pdMS_TO_TICKS(PERIODO_LUZ_MS));
  }
}

void iniciarTarefaDaLuz() {
  if (xTaskCreateUniversal(tarefaDaLuz, "luz", 3072, NULL, 2, NULL, ARDUINO_RUNNING_CORE) != pdPASS) {
    Serial.println("Nao consegui criar a tarefa da luz: o aparelho funciona, mas sem LED.");
  }
}

// ------------------------------------------------------------------
// Wi-Fi da clínica, pareamento e telemetria (tarefaDaRede)
// ------------------------------------------------------------------

void carregarConfiguracaoDaRede() {
  Preferences p;
  p.begin(MEMORIA, true);
  redeAlvo = p.getString("rede", "");
  senhaAlvo = p.getString("senha", "");
  codigoPendente = p.getString("codigo", "");
  tokenDispositivo = p.getString("token", "");
  String clinica = p.getString("clinica", "");
  p.end();

  if (xSemaphoreTake(travaTextos, portMAX_DELAY) == pdTRUE) {
    nomeClinica = clinica;
    xSemaphoreGive(travaTextos);
  }
  temToken = tokenDispositivo.length() > 0;
  pareamentoRecusado = false;
  estadoPareamento = codigoPendente.length() > 0 ? PAR_PAREANDO : PAR_NADA;
  proximoPareamento = 0;
}

/** Apaga o código salvo, se ainda for o mesmo (a página pode ter salvado outro). */
void descartarCodigo(const String &codigoUsado) {
  Preferences p;
  p.begin(MEMORIA, false);
  if (p.getString("codigo", "") == codigoUsado) p.remove("codigo");
  p.end();
  codigoPendente = "";
}

/**
 * Procura redes para a lista da página. Bloqueia uns 2 a 4 s, mas só esta
 * tarefa: a página e o sensor seguem.
 */
void buscarRedes() {
  pedidoBusca = false;
  buscandoRedes = true;
  if (tentandoConectar) {
    // Não dá para procurar redes no meio de uma tentativa de conexão: larga
    // esta e tenta de novo logo depois da busca.
    WiFi.disconnect();
    tentandoConectar = false;
    esperarAte = millis();
  }

  int total = WiFi.scanNetworks();
  const int MAXIMO = 40;
  int indices[MAXIMO];
  int quantos = total > 0 ? (total < MAXIMO ? total : MAXIMO) : 0;
  for (int i = 0; i < quantos; i++) indices[i] = i;
  // Do sinal mais forte para o mais fraco (são poucas: ordenação simples).
  for (int i = 1; i < quantos; i++) {
    int atual = indices[i];
    int j = i - 1;
    while (j >= 0 && WiFi.RSSI(indices[j]) < WiFi.RSSI(atual)) {
      indices[j + 1] = indices[j];
      j--;
    }
    indices[j + 1] = atual;
  }

  String json = "[";
  int incluidas = 0;
  for (int k = 0; k < quantos && incluidas < 15; k++) {
    String nome = WiFi.SSID(indices[k]);
    if (nome.length() == 0) continue;  // rede oculta
    // A mesma rede em vários roteadores aparece uma vez só, com o melhor sinal.
    bool repetida = false;
    for (int j = 0; j < k && !repetida; j++) repetida = WiFi.SSID(indices[j]) == nome;
    if (repetida) continue;
    if (incluidas > 0) json += ",";
    json += "{\"nome\":\"";
    json += escaparJson(nome);
    json += "\",\"sinal\":";
    json += String(WiFi.RSSI(indices[k]));
    json += ",\"aberta\":";
    json += textoBool(WiFi.encryptionType(indices[k]) == WIFI_AUTH_OPEN);
    json += "}";
    incluidas++;
  }
  json += "]";
  WiFi.scanDelete();

  if (xSemaphoreTake(travaTextos, portMAX_DELAY) == pdTRUE) {
    redesJson = json;
    xSemaphoreGive(travaTextos);
  }
  ultimaBuscaMs = millis();
  buscandoRedes = false;
  Serial.printf("Busca de redes: %d encontradas.\n", incluidas);
}

/** Troca o código de 6 dígitos por um token próprio do aparelho. */
void tentarParear() {
  uint32_t agora = millis();
  if ((int32_t)(agora - proximoPareamento) < 0) return;

  String codigoUsado = codigoPendente;
  estadoPareamento = PAR_PAREANDO;
  Serial.println("Tentando parear com o codigo informado...");

  HTTPClient http;
  http.setConnectTimeout(TIMEOUT_PAREAMENTO_MS);
  http.setTimeout(TIMEOUT_PAREAMENTO_MS);
  http.begin(clienteSeguro, String(BASE_URL) + "/dispositivos/parear");
  http.addHeader("Content-Type", "application/json");
  int status = http.POST(String("{\"codigo\":\"") + codigoUsado + "\"}");
  String resposta = http.getString();
  http.end();

  String token = status == 200 ? extrairTexto(resposta, "token") : "";
  if (token.length() > 0) {
    String clinica = extrairTexto(resposta, "nomeClinica");
    Preferences p;
    p.begin(MEMORIA, false);
    p.putString("token", token);
    p.putString("clinica", clinica);
    p.end();
    descartarCodigo(codigoUsado);
    if (xSemaphoreTake(travaTextos, portMAX_DELAY) == pdTRUE) {
      nomeClinica = clinica;
      xSemaphoreGive(travaTextos);
    }
    tokenDispositivo = token;
    temToken = true;
    pareamentoRecusado = false;
    estadoPareamento = PAR_OK;
    proximoEnvio = 0;
    silencioDesde = millis();
    Serial.printf("Pareado com a clinica \"%s\". Token guardado.\n", clinica.c_str());
    return;
  }

  if (status >= 400 && status < 500 && status != 408 && status != 429) {
    // Código vencido (vale 10 min), já usado ou errado: repetir não adianta.
    descartarCodigo(codigoUsado);
    estadoPareamento = PAR_RECUSADO;
    Serial.printf("Codigo recusado (status=%d): %s\n", status, resposta.c_str());
    return;
  }

  // Sem resposta, 5xx ou "devagar": o servidor gratuito pode estar acordando.
  // O código continua valendo até vencer, então tenta de novo.
  estadoPareamento = PAR_SEM_RESPOSTA;
  proximoPareamento = millis() + REPAREAR_MS;
  Serial.printf("Pareamento sem resposta (status=%d). Tento de novo em %lu s.\n", status, REPAREAR_MS / 1000);
}

void tratarComando(const String &comando) {
  if (comando.length() == 0 || comando == "NENHUM") return;

  Serial.printf("Comando recebido: %s\n", comando.c_str());
  if (comando == "TARAR") {
    pedidosTara = pedidosTara + 1;
  } else if (comando == "IDENTIFICAR") {
    pedidosIdentificacao = pedidosIdentificacao + 1;
  } else if (comando == "INICIAR_CAPTURA") {
    capturando = true;
    capturaLargadaPorSilencio = false;
  } else if (comando == "PARAR_CAPTURA") {
    capturando = false;
    capturaLargadaPorSilencio = false;
  } else if (comando == "REINICIAR") {
    Serial.println("Reiniciando a pedido do servidor...");
    delay(200);
    ESP.restart();
  }
}

/** Igual ao principal: acerta a captura pelo que o servidor diz. */
void acertarCapturaComServidor(int capturandoNoServidor) {
  if (capturandoNoServidor == 0) {
    capturaLargadaPorSilencio = false;
    if (capturando) {
      capturando = false;
      Serial.println("Captura encerrada: o servidor ja tinha fechado a gravacao.");
    }
  } else if (capturandoNoServidor == 1 && capturaLargadaPorSilencio) {
    capturaLargadaPorSilencio = false;
    capturando = true;
    Serial.println("Captura retomada: o servidor continuava gravando.");
  }
}

void enviarTelemetria() {
  HTTPClient http;
  http.setConnectTimeout(TIMEOUT_HTTP_MS);
  http.setTimeout(TIMEOUT_HTTP_MS);
  http.setReuse(true);
  if (!http.begin(clienteSeguro, String(BASE_URL) + "/goniometro/telemetria")) {
    Serial.println("Nao consegui abrir a conexao de telemetria.");
    return;
  }
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + tokenDispositivo);

  String ip;
  if (xSemaphoreTake(travaTextos, portMAX_DELAY) == pdTRUE) {
    ip = ipNaRede;
    xSemaphoreGive(travaTextos);
  }

  // Sem "bateria": no powerbank não há como medir, e o site simplesmente não
  // mostra o campo. "calibrado" vai sempre true daqui: o site lê false como
  // "calibrando, mantenha parado", e quem só chega aqui já calibrou.
  String corpo = "{";
  corpo += "\"angulo\":" + String(anguloPublicado, 2);
  corpo += ",\"anguloBruto\":" + String(anguloBrutoPublicado, 2);
  corpo += ",\"rssi\":" + String(WiFi.RSSI());
  corpo += ",\"numeroSerie\":\"" + numeroSerie + "\"";
  corpo += ",\"firmware\":\"" + String(VERSAO_FIRMWARE) + "\"";
  corpo += ",\"ip\":\"" + ip + "\"";
  corpo += ",\"calibrado\":true";
  corpo += "}";

  int status = http.POST(corpo);

  if (status == 200) {
    uint32_t agoraOk = millis();
    ultimoEnvioOk = agoraOk;
    silencioDesde = agoraOk;
    pareamentoRecusado = false;
    String resposta = http.getString();
    long intervalo = extrairNumero(resposta, "intervaloMs", (long)intervaloEnvioMs);
    if (intervalo >= 50 && intervalo <= 60000) {
      intervaloEnvioMs = (unsigned long)intervalo;
    }
    tratarComando(extrairTexto(resposta, "comando"));
    acertarCapturaComServidor(extrairBooleano(resposta, "capturando"));
  } else if (status == 401 || status == 403) {
    pareamentoRecusado = true;
    Serial.println("O site recusou este aparelho (token vencido ou revogado). Pareie de novo pela pagina.");
  } else {
    Serial.printf("Envio falhou, status=%d\n", status);
  }
  http.end();
}

/** Uma volta da tarefa da rede: rede, pareamento, telemetria. Nunca espera muito. */
void cuidarDaRede() {
  if (configMudou) {
    configMudou = false;
    String redeAntes = redeAlvo;
    String senhaAntes = senhaAlvo;
    carregarConfiguracaoDaRede();
    // Só o código mudou: não precisa sair da rede para parear.
    if (redeAlvo != redeAntes || senhaAlvo != senhaAntes) {
      WiFi.disconnect();
      // O status leva um instante para sair de "conectado"; sem a espera, a
      // volta seguinte tomaria a rede antiga pela nova.
      vTaskDelay(pdMS_TO_TICKS(300));
      tentandoConectar = false;
      esperarAte = millis();
      motivoDaQueda = 0;
      estadoRede = redeAlvo.length() > 0 ? REDE_CONECTANDO : REDE_SEM_CONFIG;
      if (xSemaphoreTake(travaTextos, portMAX_DELAY) == pdTRUE) {
        ipNaRede = "";
        xSemaphoreGive(travaTextos);
      }
    }
  }
  if (pedidoBusca) buscarRedes();

  if (redeAlvo.length() == 0) {
    estadoRede = REDE_SEM_CONFIG;
    return;
  }

  uint32_t agora = millis();
  if (WiFi.status() != WL_CONNECTED) {
    if (estadoRede == REDE_CONECTADA) {
      Serial.println("Wi-Fi caiu. Tentando voltar...");
      estadoRede = REDE_CONECTANDO;
      tentandoConectar = false;
      esperarAte = agora;
    }
    if (!tentandoConectar) {
      if ((int32_t)(agora - esperarAte) < 0) return;
      Serial.printf("Conectando a \"%s\"...\n", redeAlvo.c_str());
      WiFi.begin(redeAlvo.c_str(), senhaAlvo.length() > 0 ? senhaAlvo.c_str() : NULL);
      tentandoConectar = true;
      inicioTentativa = agora;
      // Depois de uma recusa, a página segue dizendo por que falhou (e que
      // está tentando de novo) em vez de voltar a um "conectando" genérico.
      if (estadoRede != REDE_FALHOU) estadoRede = REDE_CONECTANDO;
      return;
    }
    // Senha errada ou rede que não existe: o Wi-Fi já sabe em 1 ou 2 s, não
    // precisa esperar os 15. (Os 2 s de folga são para não ler o estado da
    // tentativa anterior.)
    wl_status_t situacao = WiFi.status();
    bool recusou = agora - inicioTentativa > 2000 && (situacao == WL_NO_SSID_AVAIL || situacao == WL_CONNECT_FAILED);
    if (recusou || agora - inicioTentativa > CONEXAO_MS) {
      WiFi.disconnect();
      tentandoConectar = false;
      esperarAte = agora + ESPERA_ENTRE_TENTATIVAS_MS;
      estadoRede = REDE_FALHOU;
      Serial.printf("Nao consegui entrar em \"%s\" (motivo %u). Tento de novo em %lu s.\n", redeAlvo.c_str(),
                    (unsigned)motivoDaQueda, ESPERA_ENTRE_TENTATIVAS_MS / 1000);
    }
    return;
  }

  if (estadoRede != REDE_CONECTADA) {
    tentandoConectar = false;
    String ip = WiFi.localIP().toString();
    if (xSemaphoreTake(travaTextos, portMAX_DELAY) == pdTRUE) {
      ipNaRede = ip;
      xSemaphoreGive(travaTextos);
    }
    silencioDesde = millis();  // antes de marcar conectada: veja a tarefa da luz
    motivoDaQueda = 0;
    estadoRede = REDE_CONECTADA;
    Serial.printf("Wi-Fi conectado a \"%s\", IP %s. Nesta rede, a pagina abre em http://%s\n", redeAlvo.c_str(),
                  ip.c_str(), ip.c_str());
  }

  if (codigoPendente.length() > 0) tentarParear();
  if (!temToken) return;

  // Captura presa: a conexão caiu no meio da gravação (veja o principal).
  uint32_t ultimoOk = ultimoEnvioOk;
  agora = millis();
  if (capturando && agora - ultimoOk > CAPTURA_SEM_CONTATO_MS) {
    capturando = false;
    capturaLargadaPorSilencio = true;
    Serial.println("Captura encerrada: 10 s sem resposta do servidor (que fecha a dele entre 8 e 10 s).");
  }

  // Sem sensor ou calibrando, não há ângulo de verdade para mandar: o site
  // mostra "parou de enviar leituras" em vez de um zero inventado.
  if (sensorAusente || calibrando) return;

  if (agora >= proximoEnvio) {
    // Marca o próximo envio ANTES de enviar, como no principal.
    proximoEnvio = agora + (pareamentoRecusado ? INTERVALO_RECUSADO_MS : intervaloEnvioMs);
    enviarTelemetria();
  }
}

void tarefaDaRede(void *) {
  carregarConfiguracaoDaRede();
  for (;;) {
    cuidarDaRede();
    vTaskDelay(pdMS_TO_TICKS(20));
  }
}

/**
 * Núcleo 0, o do Wi-Fi: assim um handshake TLS (que é conta pesada) não
 * disputa com o sensor e a página, que rodam no 1. Pilha de 12 KB: o TLS
 * sozinho pede uns 6.
 */
void iniciarTarefaDaRede() {
  if (xTaskCreateUniversal(tarefaDaRede, "rede", 12288, NULL, 1, NULL, 0) != pdPASS) {
    Serial.println("Nao consegui criar a tarefa da rede: so o modo local vai funcionar.");
  }
}

/** Guarda por que o Wi-Fi recusou, para a página dizer "senha errada" e não só "falhou". */
void acompanharQuedasDoWifi() {
  WiFi.onEvent(
      [](arduino_event_id_t, arduino_event_info_t info) {
        uint16_t motivo = info.wifi_sta_disconnected.reason;
        // 8 = saímos nós (WiFi.disconnect): não apaga o motivo de verdade.
        if (motivo != WIFI_REASON_ASSOC_LEAVE) motivoDaQueda = motivo;
      },
      ARDUINO_EVENT_WIFI_STA_DISCONNECTED);
}

// ------------------------------------------------------------------
// Rede própria e página (loop)
// ------------------------------------------------------------------

void iniciarRedePropria() {
  String nome = String(PREFIXO_REDE_PROPRIA) + numeroSerie.substring(5);
  WiFi.persistent(false);  // a rede da clínica fica na nossa memória, não na do driver
  WiFi.setHostname("rehabit-reserva");
  WiFi.mode(WIFI_AP_STA);
  WiFi.setAutoReconnect(false);  // quem decide quando tentar é a tarefa da rede
  // O modem dormindo atrasa os envios — e muito powerbank desliga sozinho
  // quando a placa puxa pouca corrente. Acordado, com a rede própria no ar, o
  // ESP32 fica acima do limite da maioria deles (veja o guia se não ficar).
  WiFi.setSleep(false);
  WiFi.softAPConfig(IP_PROPRIO, IP_PROPRIO, MASCARA_PROPRIA);
  if (!WiFi.softAP(nome.c_str(), SENHA_REDE_PROPRIA, 1, 0, 4)) {
    Serial.println("Nao consegui criar a rede propria. Reiniciando...");
    delay(2000);
    ESP.restart();
  }
  // DNS que responde "sou eu" para qualquer nome: é o que faz o celular abrir
  // a página sozinho ao entrar no Wi-Fi (portal cativo).
  dns.setErrorReplyCode(DNSReplyCode::NoError);
  dns.start(53, "*", IP_PROPRIO);
  Serial.printf("Rede propria no ar: \"%s\" (senha %s). Pagina: http://192.168.4.1\n", nome.c_str(),
                SENHA_REDE_PROPRIA);
}

void responder(int status, bool ok, const String &mensagem) {
  String json = "{\"ok\":";
  json += textoBool(ok);
  json += ",\"mensagem\":\"";
  json += escaparJson(mensagem);
  json += "\"}";
  servidor.send(status, "application/json; charset=utf-8", json);
}

/**
 * O celular, ao entrar no Wi-Fi, testa a internet num endereço qualquer
 * (connectivitycheck.gstatic.com, captive.apple.com...). Quem pedir outro
 * endereço que não o nosso vai para a página: é isso que faz ela abrir sozinha.
 *
 * Só na rede própria. Quem chega pelo Wi-Fi da clínica já digitou o endereço
 * do aparelho — às vezes pelo nome que o roteador deu a ele, e não pelo IP —,
 * e de lá o 192.168.4.1 nem existe.
 */
bool mandarParaAPagina() {
  if (!(servidor.client().localIP() == IP_PROPRIO)) return false;
  String host = servidor.hostHeader();
  if (host.length() == 0 || host == IP_PROPRIO.toString()) return false;
  servidor.sendHeader("Location", "http://192.168.4.1/", true);
  servidor.send(302, "text/plain", "");
  return true;
}

void enviarDados() {
  String clinica, ip;
  if (xSemaphoreTake(travaTextos, pdMS_TO_TICKS(50)) == pdTRUE) {
    clinica = nomeClinica;
    ip = ipNaRede;
    xSemaphoreGive(travaTextos);
  }
  uint32_t ultimoOk = ultimoEnvioOk;
  uint32_t agora = millis();
  float desdeOk = ultimoOk == 0 ? -1.0f : (agora - ultimoOk) / 1000.0f;
  float duracao = (gravandoLocal ? agora - inicioGravacaoLocal : duracaoGravacaoMs) / 1000.0f;

  String j;
  j.reserve(640);
  j += "{\"angulo\":";
  j += String(anguloPublicado, 2);
  j += ",\"bruto\":";
  j += String(anguloBrutoPublicado, 2);
  j += ",\"sensor\":";
  j += textoBool(sensorPronto);
  j += ",\"calibrando\":";
  j += textoBool(calibrando);
  j += ",\"calibrado\":";
  j += textoBool(calibradoParado);
  j += ",\"gravando\":";
  j += textoBool(gravandoLocal);
  j += ",\"min\":";
  j += String(minimoLocal, 2);
  j += ",\"max\":";
  j += String(maximoLocal, 2);
  j += ",\"duracao\":";
  j += String(duracao, 1);
  j += ",\"temResultado\":";
  j += textoBool(temResultado);
  j += ",\"captura\":";
  j += textoBool(capturando && agora - ultimoOk <= CAPTURA_SEM_CONTATO_MS);
  j += ",\"wifi\":";
  j += String(estadoRede);
  j += ",\"rede\":\"";
  j += escaparJson(redeSalva);
  j += "\",\"motivo\":";
  j += String(motivoDaQueda);
  j += ",\"ip\":\"";
  j += ip;
  j += "\",\"pareado\":";
  j += textoBool(temToken);
  j += ",\"recusado\":";
  j += textoBool(pareamentoRecusado);
  j += ",\"pareamento\":";
  j += String(estadoPareamento);
  j += ",\"clinica\":\"";
  j += escaparJson(clinica);
  j += "\",\"servidor\":";
  j += String(desdeOk, 1);
  j += ",\"serie\":\"";
  j += numeroSerie;
  j += "\",\"versao\":\"";
  j += VERSAO_FIRMWARE;
  j += "\"}";
  servidor.sendHeader("Cache-Control", "no-store");
  servidor.send(200, "application/json; charset=utf-8", j);
}

/** Salva a rede da clínica e/ou um código de pareamento vindos da página. */
void configurarRede() {
  String rede = servidor.arg("rede");
  rede.trim();
  String senha = servidor.arg("senha");  // sem trim: espaço pode fazer parte da senha
  String codigo = servidor.arg("codigo");
  codigo.trim();

  if (rede.length() > 32) {
    responder(400, false, "O nome da rede tem no máximo 32 caracteres.");
    return;
  }
  if (senha.length() > 0 && (senha.length() < 8 || senha.length() > 63)) {
    responder(400, false, "Senha de Wi-Fi tem de 8 a 63 caracteres.");
    return;
  }
  if (codigo.length() > 0) {
    bool seisDigitos = codigo.length() == 6;
    for (size_t i = 0; i < codigo.length() && seisDigitos; i++) seisDigitos = isDigit(codigo.charAt(i));
    if (!seisDigitos) {
      responder(400, false, "O código de pareamento tem 6 dígitos.");
      return;
    }
  }
  if (rede.length() == 0 && redeSalva.length() == 0) {
    responder(400, false, "Escolha a rede Wi-Fi da clínica.");
    return;
  }
  if (rede.length() == 0 && codigo.length() == 0) {
    responder(400, false, "Nada para salvar: escolha a rede ou digite o código.");
    return;
  }

  memoria.begin(MEMORIA, false);
  if (rede.length() > 0) {
    // Mesma rede e senha em branco: mantém a senha salva (é o caso de quem só
    // veio digitar um código novo). Rede diferente e senha em branco: rede
    // aberta.
    bool mesmaRede = rede == redeSalva;
    memoria.putString("rede", rede);
    if (senha.length() > 0 || !mesmaRede) memoria.putString("senha", senha);
    redeSalva = rede;
  }
  if (codigo.length() > 0) memoria.putString("codigo", codigo);
  memoria.end();
  configMudou = true;

  String mensagem = "Salvo. Conectando a \"" + redeSalva + "\"";
  mensagem += codigo.length() > 0 ? " e pareando..." : "...";
  responder(200, true, mensagem);
}

void configurarPagina() {
  servidor.on("/", HTTP_GET, []() {
    if (mandarParaAPagina()) return;
    servidor.sendHeader("Cache-Control", "no-store");
    servidor.send_P(200, "text/html; charset=utf-8", PAGINA);
  });
  servidor.on("/dados", HTTP_GET, enviarDados);
  servidor.on("/tarar", HTTP_POST, []() {
    if (!medindo()) {
      responder(409, false, "O sensor ainda não está medindo.");
      return;
    }
    aplicarTara();
    responder(200, true, "Zerado.");
  });
  servidor.on("/gravar", HTTP_POST, []() {
    String acao = servidor.arg("acao");
    if (acao == "iniciar") {
      if (!medindo()) {
        responder(409, false, "O sensor ainda não está medindo.");
        return;
      }
      minimoLocal = maximoLocal = anguloPublicado;
      inicioGravacaoLocal = millis();
      temResultado = false;
      gravandoLocal = true;
      responder(200, true, "Medindo: leve a articulação até o limite e toque em Parar.");
    } else if (acao == "parar") {
      encerrarGravacaoLocal();
      responder(200, true, "Medida guardada.");
    } else {
      responder(400, false, "Ação desconhecida.");
    }
  });
  servidor.on("/redes", HTTP_GET, []() {
    if (!buscandoRedes && !pedidoBusca && (ultimaBuscaMs == 0 || millis() - ultimaBuscaMs > REDES_VALEM_MS)) {
      pedidoBusca = true;
    }
    String redes = "[]";
    if (xSemaphoreTake(travaTextos, pdMS_TO_TICKS(50)) == pdTRUE) {
      redes = redesJson;
      xSemaphoreGive(travaTextos);
    }
    String j = "{\"procurando\":";
    j += textoBool(buscandoRedes || pedidoBusca);
    j += ",\"redes\":";
    j += redes;
    j += "}";
    servidor.sendHeader("Cache-Control", "no-store");
    servidor.send(200, "application/json; charset=utf-8", j);
  });
  servidor.on("/configurar", HTTP_POST, configurarRede);
  servidor.on("/esquecer", HTTP_POST, []() {
    esquecerRedeEPareamento();
    responder(200, true, "Esqueci o Wi-Fi e o pareamento. A tara continua guardada.");
  });
  servidor.onNotFound([]() {
    if (mandarParaAPagina()) return;
    servidor.send(404, "text/plain", "Nao encontrado");
  });
  servidor.begin();
}

// ------------------------------------------------------------------
// Botão e eco no Monitor Serial
// ------------------------------------------------------------------

/** Segurar o BOOT por 5 s esquece a rede e o pareamento (a tara fica). */
void verificarBotao() {
  static bool soltoDesdeOInicio = false;
  static unsigned long apertadoDesde = 0;
  static bool jaEsqueceu = false;
  bool apertado = digitalRead(PINO_BOTAO) == LOW;

  if (!apertado) {
    soltoDesdeOInicio = true;
    apertadoDesde = 0;
    jaEsqueceu = false;
    return;
  }
  if (!soltoDesdeOInicio) return;  // vinha apertado do boot: não conta
  if (apertadoDesde == 0) {
    apertadoDesde = millis();
    return;
  }
  if (!jaEsqueceu && millis() - apertadoDesde >= SEGURAR_PARA_ESQUECER_MS) {
    jaEsqueceu = true;
    esquecerRedeEPareamento();
  }
}

void ecoNoSerial() {
  static unsigned long ultimoEco = 0;
  unsigned long agora = millis();
  if (agora - ultimoEco < 1000) return;
  ultimoEco = agora;
  if (!medindo()) return;

  const char *rede = "so modo local";
  uint8_t estado = estadoRede;
  if (estado == REDE_CONECTANDO) rede = "conectando";
  if (estado == REDE_FALHOU) rede = "sem Wi-Fi";
  if (estado == REDE_CONECTADA) {
    if (!temToken || pareamentoRecusado) {
      rede = "conectado, falta parear";
    } else {
      rede = agora - silencioDesde > SERVIDOR_CALADO_MS ? "conectado, site calado" : "enviando ao site";
    }
  }
  Serial.printf("Angulo: %6.2f graus (sem filtro %6.2f) | %s%s%s\n", anguloPublicado, anguloBrutoPublicado, rede,
                capturando ? " | GRAVANDO NO SITE" : "", gravandoLocal ? " | MEDINDO AMPLITUDE" : "");
}

// ------------------------------------------------------------------
// setup / loop
// ------------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  travaTextos = xSemaphoreCreateMutex();
  iniciarTarefaDaLuz();
  delay(300);
  Serial.printf("\nRehabit — goniometro RESERVA, firmware %s\n", VERSAO_FIRMWARE);

  pinMode(PINO_BOTAO, INPUT_PULLUP);
  numeroSerie = montarNumeroSerie();
  Serial.printf("Numero de serie: %s\n", numeroSerie.c_str());

  referenciaDefinida = carregarReferencia();
  if (referenciaDefinida) Serial.printf("Tara guardada: g = %.2f, %.2f, %.2f\n", refX, refY, refZ);
  redeSalva = lerDaMemoria("rede");

  // Confere o certificado da API, como no principal (raízes no fim do arquivo).
  clienteSeguro.setCACert(RAIZES_TLS);

  // A página antes do sensor: mesmo com um fio solto, dá para abrir no celular
  // e ver o que está faltando.
  iniciarRedePropria();
  configurarPagina();
  acompanharQuedasDoWifi();
  iniciarTarefaDaRede();

  cuidarDoSensor();
}

void loop() {
  dns.processNextRequest();
  servidor.handleClient();
  cuidarDoSensor();
  aplicarPedidosDoSite();
  verificarBotao();
  ecoNoSerial();
  delay(1);  // deixa o núcleo respirar; 1 ms não atrapalha as 100 amostras por segundo
}

// ------------------------------------------------------------------
// A página (servida em http://192.168.4.1 e no IP da rede da clínica)
// ------------------------------------------------------------------
//
// Tudo num arquivo só, sem nada de fora: no modo local não há internet para
// buscar fonte, ícone ou biblioteca. Ela pergunta /dados umas 6 vezes por
// segundo e desenha o resto.

const char PAGINA[] PROGMEM = R"PAGINA(<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Goniômetro Rehabit</title>
<style>
:root{color-scheme:light;--fundo:#f3f6fb;--cartao:#fff;--texto:#14213d;--suave:#5b6578;--linha:#dfe5ef;--chip:#eef2f8;--marca:#1565d8;--ok:#1b7f3b;--alerta:#9a5b00;--erro:#c62828}
@media (prefers-color-scheme:dark){:root{color-scheme:dark;--fundo:#0d131f;--cartao:#161f31;--texto:#e7ecf5;--suave:#9ba7bc;--linha:#28344b;--chip:#1f2a40;--marca:#5b9bf5;--ok:#5fd07f;--alerta:#f2b350;--erro:#f07474}}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--fundo);color:var(--texto);font:16px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:520px;margin:0 auto;padding:16px 16px 32px}
header{display:flex;justify-content:space-between;align-items:baseline;gap:8px;margin:4px 2px 12px}
h1{font-size:17px;margin:0}
h1 span{color:var(--suave);font-weight:500}
header small{color:var(--suave);font-variant-numeric:tabular-nums}
.cartao{background:var(--cartao);border:1px solid var(--linha);border-radius:16px;padding:16px;margin-bottom:12px}
.mostrador{text-align:center;padding:22px 16px 18px}
#ang{font-size:min(30vw,132px);font-weight:700;line-height:1;letter-spacing:-.03em;font-variant-numeric:tabular-nums}
#ang sup{font-size:.45em;font-weight:600;vertical-align:.9em;margin-left:2px}
#bruto{color:var(--suave);font-size:14px;margin-top:8px;font-variant-numeric:tabular-nums;min-height:1.45em}
.chips{display:flex;flex-wrap:wrap;justify-content:center;gap:6px;margin-top:14px}
.chip{font-size:13px;font-weight:600;padding:5px 11px;border-radius:999px;background:var(--chip);color:var(--suave)}
.chip.ok{color:var(--ok)}.chip.alerta{color:var(--alerta)}.chip.erro{color:var(--erro)}
.botoes{display:grid;grid-template-columns:1fr 1fr;gap:10px}
button{font:inherit;font-weight:650;border-radius:12px;min-height:52px;padding:12px;cursor:pointer;border:2px solid var(--marca);background:transparent;color:var(--marca);touch-action:manipulation}
button.cheio{background:var(--marca);color:#fff}
button.parar{background:var(--erro);border-color:var(--erro);color:#fff}
button:disabled{opacity:.45;cursor:default}
.dica{color:var(--suave);font-size:14px;margin:12px 0 0}
.numeros{display:grid;grid-template-columns:repeat(3,1fr);text-align:center;gap:8px}
.numeros b{display:block;font-size:30px;line-height:1.15;font-variant-numeric:tabular-nums}
.numeros span{font-size:13px;color:var(--suave)}
.numeros .destaque b{color:var(--marca)}
#historico{margin:14px 0 0;padding:0;list-style:none;font-size:14px;color:var(--suave)}
#historico li{padding:8px 0;border-top:1px solid var(--linha);font-variant-numeric:tabular-nums}
#historico:empty{display:none}
canvas{display:block;width:100%;height:130px}
summary{font-weight:650;cursor:pointer;padding:4px 0}
label{display:block;font-size:14px;color:var(--suave);margin:14px 0 6px}
input,select{display:block;width:100%;font:inherit;color:var(--texto);background:var(--fundo);border:1px solid var(--linha);border-radius:10px;padding:12px;min-height:48px}
select+input{margin-top:8px}
form button{width:100%;margin-top:16px}
#esquecer{width:100%;margin-top:10px;border-color:var(--linha);color:var(--suave)}
</style>
</head>
<body>
<main>
<header><h1>Rehabit <span>· goniômetro reserva</span></h1><small id="serie"></small></header>

<section class="cartao mostrador">
<div id="ang">--<sup>°</sup></div>
<div id="bruto"></div>
<div class="chips" id="chips"></div>
</section>

<section class="cartao">
<div class="botoes">
<button id="tara" type="button" disabled>Zerar (tara)</button>
<button id="gravar" type="button" class="cheio" disabled>Medir amplitude</button>
</div>
<p class="dica">Zere com o braço pendurado ao lado do tronco. Em “Medir amplitude”, leve a articulação até o limite e toque em “Parar”.</p>
</section>

<section class="cartao" id="resultado" hidden>
<div class="numeros">
<div><b id="min">--</b><span>mínimo</span></div>
<div><b id="max">--</b><span>máximo</span></div>
<div class="destaque"><b id="amp">--</b><span>amplitude</span></div>
</div>
<ol id="historico"></ol>
</section>

<section class="cartao"><canvas id="grafico" role="img" aria-label="Ângulo nos últimos 10 segundos"></canvas></section>

<section class="cartao">
<details id="config">
<summary>Conectar ao site Rehabit</summary>
<p class="dica" id="estadoRede"></p>
<form id="form" autocomplete="off">
<label for="lista">Wi-Fi da clínica (2,4 GHz, com internet)</label>
<select id="lista"><option value="">Procurando redes…</option></select>
<input id="rede" name="rede" maxlength="32" placeholder="ou digite o nome da rede" autocapitalize="none" spellcheck="false">
<label for="senha">Senha do Wi-Fi</label>
<input id="senha" name="senha" type="password" maxlength="63">
<label for="codigo">Código de pareamento (tela Dispositivo do site)</label>
<input id="codigo" name="codigo" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" placeholder="6 dígitos">
<button type="submit" class="cheio">Salvar e conectar</button>
<p class="dica" id="mensagem" role="status"></p>
</form>
<button id="esquecer" type="button">Esquecer Wi-Fi e pareamento</button>
</details>
</section>
</main>
<script>
const $=id=>document.getElementById(id);
const grau=v=>Math.round(v)+'°';
let ultimo=null,falhas=0,eraGravando=false,serie=[],medidas=[],procurando=false;

function chip(texto,tipo){const s=document.createElement('span');s.className='chip'+(tipo?' '+tipo:'');s.textContent=texto;return s}
function trocarChips(...lista){const c=$('chips');c.textContent='';lista.forEach(x=>c.append(x))}

function motivo(m){
  if([15,202,204].includes(m))return 'a senha parece errada';
  if([201,210,211,212].includes(m))return 'não achei a rede (o aparelho só enxerga Wi-Fi de 2,4 GHz)';
  return m?'a rede recusou (código '+m+')':'a rede não respondeu';
}

function chipSite(d){
  if(d.wifi===0)return chip('Modo local · sem site');
  if(d.wifi===1)return chip('Conectando ao Wi-Fi…','alerta');
  if(d.wifi===3)return chip('Sem Wi-Fi da clínica','alerta');
  if(!d.pareado||d.recusado)return chip('Falta parear com o site','alerta');
  if(d.servidor<0||d.servidor>10)return chip('Site não responde','alerta');
  return chip('Enviando ao site','ok');
}

function textoRede(d){
  const rede='“'+d.rede+'”',clinica=d.clinica?' com '+d.clinica:'';
  if(d.wifi===0)return 'Sem Wi-Fi da clínica: o aparelho está só no modo local, e tudo nesta página funciona assim. Para o ângulo aparecer também no site, escolha a rede e digite o código da tela Dispositivo.';
  if(d.wifi===1)return 'Conectando a '+rede+'…';
  if(d.wifi===3)return 'Não consegui entrar em '+rede+': '+motivo(d.motivo)+'. Tento de novo sozinho.';
  let t='Conectado a '+rede+(d.ip?' (nesta rede, a página também abre em http://'+d.ip+')':'')+'. ';
  if(d.pareamento===1)return t+'Pareando com o código…';
  if(d.pareamento===4)return t+'O site não respondeu ao pareamento; tentando de novo (o servidor gratuito leva até 2 min para acordar).';
  if(d.pareamento===3)return t+'O código foi recusado (vencido ou já usado): gere outro na tela Dispositivo.';
  if(!d.pareado)return t+'Falta parear: digite o código da tela Dispositivo.';
  if(d.recusado)return t+'O site recusou este aparelho (foi revogado?). Gere um código novo e salve aqui.';
  if(d.servidor<0||d.servidor>10)return t+'Pareado'+clinica+'; esperando o site responder (o servidor gratuito leva até 2 min para acordar).';
  return t+'Pareado'+clinica+': o ângulo está indo para o site.';
}

function mostrar(d){
  ultimo=d;
  const medindo=d.sensor&&!d.calibrando;
  $('ang').firstChild.nodeValue=medindo?Math.round(d.angulo):'--';
  $('bruto').textContent=medindo?'sem filtro '+d.bruto.toFixed(1).replace('.',',')+'°':d.sensor?'calibrando: deixe o aparelho parado':'sem leitura do sensor';
  trocarChips(
    !d.sensor?chip('Sensor não encontrado: confira os 4 fios','erro'):
    d.calibrando?chip('Calibrando: não mexa','alerta'):
    !d.calibrado?chip('Calibrou mexendo: religue parado','alerta'):chip('Sensor ok','ok'),
    chipSite(d));
  if(d.captura)$('chips').append(chip('Gravando no site','erro'));

  const g=$('gravar');
  g.textContent=d.gravando?'Parar':'Medir amplitude';
  g.className=d.gravando?'parar':'cheio';
  g.disabled=!medindo&&!d.gravando;
  $('tara').disabled=!medindo;
  if(d.gravando||d.temResultado){
    $('resultado').hidden=false;
    $('min').textContent=grau(d.min);
    $('max').textContent=grau(d.max);
    $('amp').textContent=(Math.round(d.max)-Math.round(d.min))+'°';
  }
  if(eraGravando&&!d.gravando&&d.temResultado){medidas.unshift(d);desenharHistorico()}
  eraGravando=d.gravando;

  if(medindo)serie.push([performance.now(),d.angulo]);
  $('serie').textContent=d.serie;
  $('estadoRede').textContent=textoRede(d);
}

function desenharHistorico(){
  const ol=$('historico');ol.textContent='';
  medidas.slice(0,10).forEach((m,i)=>{
    const li=document.createElement('li');
    li.textContent='Medida '+(medidas.length-i)+': de '+grau(m.min)+' a '+grau(m.max)+' · amplitude '+(Math.round(m.max)-Math.round(m.min))+'° · '+Math.round(m.duracao)+' s';
    ol.append(li);
  });
}

function desenhar(){
  const c=$('grafico'),dpr=window.devicePixelRatio||1,w=Math.round(c.clientWidth*dpr),h=Math.round(c.clientHeight*dpr);
  if(!w||!h)return;
  if(c.width!==w||c.height!==h){c.width=w;c.height=h}
  const g=c.getContext('2d'),cs=getComputedStyle(document.documentElement),cor=n=>cs.getPropertyValue(n).trim();
  const agora=performance.now(),janela=10000,borda=8*dpr,esquerda=34*dpr;
  while(serie.length&&agora-serie[0][0]>janela)serie.shift();
  const y=v=>h-borda-Math.min(180,Math.max(0,v))/180*(h-2*borda);
  g.clearRect(0,0,w,h);
  g.lineWidth=dpr;g.strokeStyle=cor('--linha');g.fillStyle=cor('--suave');
  g.font=(11*dpr)+'px system-ui,sans-serif';g.textBaseline='middle';
  [0,90,180].forEach(v=>{g.beginPath();g.moveTo(esquerda,y(v));g.lineTo(w,y(v));g.stroke();g.fillText(v+'°',0,y(v))});
  if(serie.length<2)return;
  g.lineWidth=2.5*dpr;g.lineJoin='round';g.strokeStyle=cor('--marca');g.beginPath();
  serie.forEach((p,i)=>{const x=esquerda+(1-(agora-p[0])/janela)*(w-esquerda);i?g.lineTo(x,y(p[1])):g.moveTo(x,y(p[1]))});
  g.stroke();
}

async function atualizar(){
  if(document.hidden){setTimeout(atualizar,1000);return}
  const controle=new AbortController(),limite=setTimeout(()=>controle.abort(),2500);
  try{
    const r=await fetch('/dados',{cache:'no-store',signal:controle.signal});
    mostrar(await r.json());falhas=0;
  }catch(e){
    if(++falhas>=3){
      $('ang').firstChild.nodeValue='--';
      $('bruto').textContent='sem contato com o aparelho';
      trocarChips(chip('Sem contato: o celular ainda está no Wi-Fi do aparelho?','erro'));
    }
  }
  clearTimeout(limite);desenhar();setTimeout(atualizar,150);
}

async function enviar(url,corpo){
  const r=await fetch(url,{method:'POST',body:corpo});
  let d={};try{d=await r.json()}catch(e){}
  return{ok:r.ok,mensagem:d.mensagem||''};
}

$('tara').onclick=async()=>{
  const b=$('tara');
  try{const r=await enviar('/tarar');b.textContent=r.ok?'Zerado ✓':r.mensagem}catch(e){b.textContent='Sem contato'}
  setTimeout(()=>b.textContent='Zerar (tara)',1500);
};
$('gravar').onclick=()=>enviar('/gravar?acao='+(ultimo&&ultimo.gravando?'parar':'iniciar')).catch(()=>{});

function preencherRedes(d){
  const s=$('lista'),antes=s.value;s.textContent='';
  s.add(new Option(d.redes.length?'Escolha a rede':d.procurando?'Procurando redes…':'Nenhuma rede encontrada',''));
  d.redes.forEach(r=>{
    const forca=r.sinal>-60?'forte':r.sinal>-75?'médio':'fraco';
    s.add(new Option(r.nome+' · sinal '+forca+(r.aberta?' · aberta':''),r.nome));
  });
  if([...s.options].some(o=>o.value===antes))s.value=antes;
}
async function procurarRedes(){
  if(procurando)return;procurando=true;
  for(let i=0;i<25;i++){
    try{const d=await(await fetch('/redes',{cache:'no-store'})).json();preencherRedes(d);if(!d.procurando)break}catch(e){}
    await new Promise(r=>setTimeout(r,1000));
  }
  procurando=false;
}
function dicaSenha(){
  const mesma=ultimo&&ultimo.rede&&$('rede').value.trim()===ultimo.rede;
  $('senha').placeholder=mesma?'em branco = manter a senha salva':'';
}
$('config').addEventListener('toggle',()=>{
  if(!$('config').open)return;
  if(!$('rede').value&&ultimo&&ultimo.rede)$('rede').value=ultimo.rede;
  dicaSenha();procurarRedes();
});
$('lista').onchange=()=>{if($('lista').value){$('rede').value=$('lista').value;dicaSenha();$('senha').focus()}};
$('rede').oninput=dicaSenha;
$('form').onsubmit=async e=>{
  e.preventDefault();
  const m=$('mensagem');m.textContent='Salvando…';
  try{
    const r=await enviar('/configurar',new URLSearchParams(new FormData($('form'))));
    m.textContent=r.mensagem;
    if(r.ok){$('senha').value='';$('codigo').value='';dicaSenha()}
  }catch(_){m.textContent='Não consegui falar com o aparelho.'}
};
$('esquecer').onclick=async()=>{
  if(!confirm('Esquecer o Wi-Fi e o pareamento? A tara continua guardada.'))return;
  try{const r=await enviar('/esquecer');$('mensagem').textContent=r.mensagem;$('rede').value=''}catch(_){}
};
addEventListener('resize',desenhar);
atualizar();
</script>
</body>
</html>
)PAGINA";

// ------------------------------------------------------------------
// Raízes de confiança do HTTPS (veja setup)
// ------------------------------------------------------------------
//
// A API no Render usa certificados da Let's Encrypt ou da Google Trust
// Services, conforme a renovação; ficam as raízes das duas (RSA e ECDSA), e
// as da DigiCert e da Amazon para o caso de a API ir para trás de outro
// provedor (Cloudflare, AWS). São raízes, não o certificado do site: duram
// até 2035-2040 e não mudam a cada renovação. Tiradas do repositório de
// certificados do sistema (Mozilla), conferidas pela impressão digital SHA-256.

const char RAIZES_TLS[] = R"PEM(
// ISRG Root X1
-----BEGIN CERTIFICATE-----
MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw
TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTUwNjA0MTEwNDM4
WhcNMzUwNjA0MTEwNDM4WjBPMQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJu
ZXQgU2VjdXJpdHkgUmVzZWFyY2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBY
MTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK3oJHP0FDfzm54rVygc
h77ct984kIxuPOZXoHj3dcKi/vVqbvYATyjb3miGbESTtrFj/RQSa78f0uoxmyF+
0TM8ukj13Xnfs7j/EvEhmkvBioZxaUpmZmyPfjxwv60pIgbz5MDmgK7iS4+3mX6U
A5/TR5d8mUgjU+g4rk8Kb4Mu0UlXjIB0ttov0DiNewNwIRt18jA8+o+u3dpjq+sW
T8KOEUt+zwvo/7V3LvSye0rgTBIlDHCNAymg4VMk7BPZ7hm/ELNKjD+Jo2FR3qyH
B5T0Y3HsLuJvW5iB4YlcNHlsdu87kGJ55tukmi8mxdAQ4Q7e2RCOFvu396j3x+UC
B5iPNgiV5+I3lg02dZ77DnKxHZu8A/lJBdiB3QW0KtZB6awBdpUKD9jf1b0SHzUv
KBds0pjBqAlkd25HN7rOrFleaJ1/ctaJxQZBKT5ZPt0m9STJEadao0xAH0ahmbWn
OlFuhjuefXKnEgV4We0+UXgVCwOPjdAvBbI+e0ocS3MFEvzG6uBQE3xDk3SzynTn
jh8BCNAw1FtxNrQHusEwMFxIt4I7mKZ9YIqioymCzLq9gwQbooMDQaHWBfEbwrbw
qHyGO0aoSCqI3Haadr8faqU9GY/rOPNk3sgrDQoo//fb4hVC1CLQJ13hef4Y53CI
rU7m2Ys6xt0nUW7/vGT1M0NPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNV
HRMBAf8EBTADAQH/MB0GA1UdDgQWBBR5tFnme7bl5AFzgAiIyBpY9umbbjANBgkq
hkiG9w0BAQsFAAOCAgEAVR9YqbyyqFDQDLHYGmkgJykIrGF1XIpu+ILlaS/V9lZL
ubhzEFnTIZd+50xx+7LSYK05qAvqFyFWhfFQDlnrzuBZ6brJFe+GnY+EgPbk6ZGQ
3BebYhtF8GaV0nxvwuo77x/Py9auJ/GpsMiu/X1+mvoiBOv/2X/qkSsisRcOj/KK
NFtY2PwByVS5uCbMiogziUwthDyC3+6WVwW6LLv3xLfHTjuCvjHIInNzktHCgKQ5
ORAzI4JMPJ+GslWYHb4phowim57iaztXOoJwTdwJx4nLCgdNbOhdjsnvzqvHu7Ur
TkXWStAmzOVyyghqpZXjFaH3pO3JLF+l+/+sKAIuvtd7u+Nxe5AW0wdeRlN8NwdC
jNPElpzVmbUq4JUagEiuTDkHzsxHpFKVK7q4+63SM1N95R1NbdWhscdCb+ZAJzVc
oyi3B43njTOQ5yOf+1CceWxG1bQVs5ZufpsMljq4Ui0/1lvh+wjChP4kqKOJ2qxq
4RgqsahDYVvTH9w7jXbyLeiNdd8XM2w9U/t7y0Ff/9yi0GE44Za4rF2LN9d11TPA
mRGunUHBcnWEvgJBQl9nJEiU0Zsnvgc/ubhPgXRR4Xq37Z0j4r7g1SgEEzwxA57d
emyPxgcYxn/eR44/KJ4EBs+lVDR3veyJm+kXQ99b21/+jh5Xos1AnX5iItreGCc=
-----END CERTIFICATE-----
// ISRG Root X2
-----BEGIN CERTIFICATE-----
MIICGzCCAaGgAwIBAgIQQdKd0XLq7qeAwSxs6S+HUjAKBggqhkjOPQQDAzBPMQsw
CQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJuZXQgU2VjdXJpdHkgUmVzZWFyY2gg
R3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBYMjAeFw0yMDA5MDQwMDAwMDBaFw00
MDA5MTcxNjAwMDBaME8xCzAJBgNVBAYTAlVTMSkwJwYDVQQKEyBJbnRlcm5ldCBT
ZWN1cml0eSBSZXNlYXJjaCBHcm91cDEVMBMGA1UEAxMMSVNSRyBSb290IFgyMHYw
EAYHKoZIzj0CAQYFK4EEACIDYgAEzZvVn4CDCuwJSvMWSj5cz3es3mcFDR0HttwW
+1qLFNvicWDEukWVEYmO6gbf9yoWHKS5xcUy4APgHoIYOIvXRdgKam7mAHf7AlF9
ItgKbppbd9/w+kHsOdx1ymgHDB/qo0IwQDAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0T
AQH/BAUwAwEB/zAdBgNVHQ4EFgQUfEKWrt5LSDv6kviejM9ti6lyN5UwCgYIKoZI
zj0EAwMDaAAwZQIwe3lORlCEwkSHRhtFcP9Ymd70/aTSVaYgLXTWNLxBo1BfASdW
tL4ndQavEi51mI38AjEAi/V3bNTIZargCyzuFJ0nN6T5U6VR5CmD1/iQMVtCnwr1
/q4AaOeMSQ+2b1tbFfLn
-----END CERTIFICATE-----
// GTS Root R1
-----BEGIN CERTIFICATE-----
MIIFVzCCAz+gAwIBAgINAgPlk28xsBNJiGuiFzANBgkqhkiG9w0BAQwFADBHMQsw
CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEU
MBIGA1UEAxMLR1RTIFJvb3QgUjEwHhcNMTYwNjIyMDAwMDAwWhcNMzYwNjIyMDAw
MDAwWjBHMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZp
Y2VzIExMQzEUMBIGA1UEAxMLR1RTIFJvb3QgUjEwggIiMA0GCSqGSIb3DQEBAQUA
A4ICDwAwggIKAoICAQC2EQKLHuOhd5s73L+UPreVp0A8of2C+X0yBoJx9vaMf/vo
27xqLpeXo4xL+Sv2sfnOhB2x+cWX3u+58qPpvBKJXqeqUqv4IyfLpLGcY9vXmX7w
Cl7raKb0xlpHDU0QM+NOsROjyBhsS+z8CZDfnWQpJSMHobTSPS5g4M/SCYe7zUjw
TcLCeoiKu7rPWRnWr4+wB7CeMfGCwcDfLqZtbBkOtdh+JhpFAz2weaSUKK0Pfybl
qAj+lug8aJRT7oM6iCsVlgmy4HqMLnXWnOunVmSPlk9orj2XwoSPwLxAwAtcvfaH
szVsrBhQf4TgTM2S0yDpM7xSma8ytSmzJSq0SPly4cpk9+aCEI3oncKKiPo4Zor8
Y/kB+Xj9e1x3+naH+uzfsQ55lVe0vSbv1gHR6xYKu44LtcXFilWr06zqkUspzBmk
MiVOKvFlRNACzqrOSbTqn3yDsEB750Orp2yjj32JgfpMpf/VjsPOS+C12LOORc92
wO1AK/1TD7Cn1TsNsYqiA94xrcx36m97PtbfkSIS5r762DL8EGMUUXLeXdYWk70p
aDPvOmbsB4om3xPXV2V4J95eSRQAogB/mqghtqmxlbCluQ0WEdrHbEg8QOB+DVrN
VjzRlwW5y0vtOUucxD/SVRNuJLDWcfr0wbrM7Rv1/oFB2ACYPTrIrnqYNxgFlQID
AQABo0IwQDAOBgNVHQ8BAf8EBAMCAYYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4E
FgQU5K8rJnEaK0gnhS9SZizv8IkTcT4wDQYJKoZIhvcNAQEMBQADggIBAJ+qQibb
C5u+/x6Wki4+omVKapi6Ist9wTrYggoGxval3sBOh2Z5ofmmWJyq+bXmYOfg6LEe
QkEzCzc9zolwFcq1JKjPa7XSQCGYzyI0zzvFIoTgxQ6KfF2I5DUkzps+GlQebtuy
h6f88/qBVRRiClmpIgUxPoLW7ttXNLwzldMXG+gnoot7TiYaelpkttGsN/H9oPM4
7HLwEXWdyzRSjeZ2axfG34arJ45JK3VmgRAhpuo+9K4l/3wV3s6MJT/KYnAK9y8J
ZgfIPxz88NtFMN9iiMG1D53Dn0reWVlHxYciNuaCp+0KueIHoI17eko8cdLiA6Ef
MgfdG+RCzgwARWGAtQsgWSl4vflVy2PFPEz0tv/bal8xa5meLMFrUKTX5hgUvYU/
Z6tGn6D/Qqc6f1zLXbBwHSs09dR2CQzreExZBfMzQsNhFRAbd03OIozUhfJFfbdT
6u9AWpQKXCBfTkBdYiJ23//OYb2MI3jSNwLgjt7RETeJ9r/tSQdirpLsQBqvFAnZ
0E6yove+7u7Y/9waLd64NnHi/Hm3lCXRSHNboTXns5lndcEZOitHTtNCjv0xyBZm
2tIMPNuzjsmhDYAPexZ3FL//2wmUspO8IFgV6dtxQ/PeEMMA3KgqlbbC1j+Qa3bb
bP6MvPJwNQzcmRk13NfIRmPVNnGuV/u3gm3c
-----END CERTIFICATE-----
// GTS Root R3
-----BEGIN CERTIFICATE-----
MIICCTCCAY6gAwIBAgINAgPluILrIPglJ209ZjAKBggqhkjOPQQDAzBHMQswCQYD
VQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEUMBIG
A1UEAxMLR1RTIFJvb3QgUjMwHhcNMTYwNjIyMDAwMDAwWhcNMzYwNjIyMDAwMDAw
WjBHMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2Vz
IExMQzEUMBIGA1UEAxMLR1RTIFJvb3QgUjMwdjAQBgcqhkjOPQIBBgUrgQQAIgNi
AAQfTzOHMymKoYTey8chWEGJ6ladK0uFxh1MJ7x/JlFyb+Kf1qPKzEUURout736G
jOyxfi//qXGdGIRFBEFVbivqJn+7kAHjSxm65FSWRQmx1WyRRK2EE46ajA2ADDL2
4CejQjBAMA4GA1UdDwEB/wQEAwIBhjAPBgNVHRMBAf8EBTADAQH/MB0GA1UdDgQW
BBTB8Sa6oC2uhYHP0/EqEr24Cmf9vDAKBggqhkjOPQQDAwNpADBmAjEA9uEglRR7
VKOQFhG/hMjqb2sXnh5GmCCbn9MN2azTL818+FsuVbu/3ZL3pAzcMeGiAjEA/Jdm
ZuVDFhOD3cffL74UOO0BzrEXGhF16b0DjyZ+hOXJYKaV11RZt+cRLInUue4X
-----END CERTIFICATE-----
// GTS Root R4
-----BEGIN CERTIFICATE-----
MIICCTCCAY6gAwIBAgINAgPlwGjvYxqccpBQUjAKBggqhkjOPQQDAzBHMQswCQYD
VQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEUMBIG
A1UEAxMLR1RTIFJvb3QgUjQwHhcNMTYwNjIyMDAwMDAwWhcNMzYwNjIyMDAwMDAw
WjBHMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2Vz
IExMQzEUMBIGA1UEAxMLR1RTIFJvb3QgUjQwdjAQBgcqhkjOPQIBBgUrgQQAIgNi
AATzdHOnaItgrkO4NcWBMHtLSZ37wWHO5t5GvWvVYRg1rkDdc/eJkTBa6zzuhXyi
QHY7qca4R9gq55KRanPpsXI5nymfopjTX15YhmUPoYRlBtHci8nHc8iMai/lxKvR
HYqjQjBAMA4GA1UdDwEB/wQEAwIBhjAPBgNVHRMBAf8EBTADAQH/MB0GA1UdDgQW
BBSATNbrdP9JNqPV2Py1PsVq8JQdjDAKBggqhkjOPQQDAwNpADBmAjEA6ED/g94D
9J+uHXqnLrmvT/aDHQ4thQEd0dlq7A/Cr8deVl5c1RxYIigL9zC2L7F8AjEA8GE8
p/SgguMh1YQdc4acLa/KNJvxn7kjNuK8YAOdgLOaVsjh4rsUecrNIdSUtUlD
-----END CERTIFICATE-----
// DigiCert Global Root G2
-----BEGIN CERTIFICATE-----
MIIDjjCCAnagAwIBAgIQAzrx5qcRqaC7KGSxHQn65TANBgkqhkiG9w0BAQsFADBh
MQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3
d3cuZGlnaWNlcnQuY29tMSAwHgYDVQQDExdEaWdpQ2VydCBHbG9iYWwgUm9vdCBH
MjAeFw0xMzA4MDExMjAwMDBaFw0zODAxMTUxMjAwMDBaMGExCzAJBgNVBAYTAlVT
MRUwEwYDVQQKEwxEaWdpQ2VydCBJbmMxGTAXBgNVBAsTEHd3dy5kaWdpY2VydC5j
b20xIDAeBgNVBAMTF0RpZ2lDZXJ0IEdsb2JhbCBSb290IEcyMIIBIjANBgkqhkiG
9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuzfNNNx7a8myaJCtSnX/RrohCgiN9RlUyfuI
2/Ou8jqJkTx65qsGGmvPrC3oXgkkRLpimn7Wo6h+4FR1IAWsULecYxpsMNzaHxmx
1x7e/dfgy5SDN67sH0NO3Xss0r0upS/kqbitOtSZpLYl6ZtrAGCSYP9PIUkY92eQ
q2EGnI/yuum06ZIya7XzV+hdG82MHauVBJVJ8zUtluNJbd134/tJS7SsVQepj5Wz
tCO7TG1F8PapspUwtP1MVYwnSlcUfIKdzXOS0xZKBgyMUNGPHgm+F6HmIcr9g+UQ
vIOlCsRnKPZzFBQ9RnbDhxSJITRNrw9FDKZJobq7nMWxM4MphQIDAQABo0IwQDAP
BgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBhjAdBgNVHQ4EFgQUTiJUIBiV
5uNu5g/6+rkS7QYXjzkwDQYJKoZIhvcNAQELBQADggEBAGBnKJRvDkhj6zHd6mcY
1Yl9PMWLSn/pvtsrF9+wX3N3KjITOYFnQoQj8kVnNeyIv/iPsGEMNKSuIEyExtv4
NeF22d+mQrvHRAiGfzZ0JFrabA0UWTW98kndth/Jsw1HKj2ZL7tcu7XUIOGZX1NG
Fdtom/DzMNU+MeKNhJ7jitralj41E6Vf8PlwUHBHQRFXGU7Aj64GxJUTFy8bJZ91
8rGOmaFvE7FBcf6IKshPECBV1/MUReXgRPTqh5Uykw7+U0b6LJ3/iyK5S9kJRaTe
pLiaWN0bfVKfjllDiIGknibVb63dDcY3fe0Dkhvld1927jyNxF1WW6LZZm6zNTfl
MrY=
-----END CERTIFICATE-----
// Amazon Root CA 1
-----BEGIN CERTIFICATE-----
MIIDQTCCAimgAwIBAgITBmyfz5m/jAo54vB4ikPmljZbyjANBgkqhkiG9w0BAQsF
ADA5MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6
b24gUm9vdCBDQSAxMB4XDTE1MDUyNjAwMDAwMFoXDTM4MDExNzAwMDAwMFowOTEL
MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEZMBcGA1UEAxMQQW1hem9uIFJv
b3QgQ0EgMTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALJ4gHHKeNXj
ca9HgFB0fW7Y14h29Jlo91ghYPl0hAEvrAIthtOgQ3pOsqTQNroBvo3bSMgHFzZM
9O6II8c+6zf1tRn4SWiw3te5djgdYZ6k/oI2peVKVuRF4fn9tBb6dNqcmzU5L/qw
IFAGbHrQgLKm+a/sRxmPUDgH3KKHOVj4utWp+UhnMJbulHheb4mjUcAwhmahRWa6
VOujw5H5SNz/0egwLX0tdHA114gk957EWW67c4cX8jJGKLhD+rcdqsq08p8kDi1L
93FcXmn/6pUCyziKrlA4b9v7LWIbxcceVOF34GfID5yHI9Y/QCB/IIDEgEw+OyQm
jgSubJrIqg0CAwEAAaNCMEAwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMC
AYYwHQYDVR0OBBYEFIQYzIU07LwMlJQuCFmcx7IQTgoIMA0GCSqGSIb3DQEBCwUA
A4IBAQCY8jdaQZChGsV2USggNiMOruYou6r4lK5IpDB/G/wkjUu0yKGX9rbxenDI
U5PMCCjjmCXPI6T53iHTfIUJrU6adTrCC2qJeHZERxhlbI1Bjjt/msv0tadQ1wUs
N+gDS63pYaACbvXy8MWy7Vu33PqUXHeeE6V/Uq2V8viTO96LXFvKWlJbYK8U90vv
o/ufQJVtMVT8QtPHRh8jrdkPSHCa2XV4cdFyQzR1bldZwgJcJmApzyMZFo6IQ6XU
5MsI+yMRQ+hDKXJioaldXgjUkK642M4UwtBV8ob2xJNDd2ZhwLnoQdeXeGADbkpy
rqXRfboQnoZsG4q5WTP468SQvvG5
-----END CERTIFICATE-----
)PEM";
