// Rehabit — goniômetro digital (ESP32 + MPU6050)
// Firmware 2.3
//
// NÃO É PRECISO EDITAR NADA AQUI. Wi-Fi e pareamento são configurados pelo
// celular, na primeira vez que o aparelho liga:
//
//   1. Grave este código uma vez (veja o guia ao lado).
//   2. Ao ligar sem configuração, o goniômetro cria um Wi-Fi chamado
//      "Rehabit-Goniometro" (senha: rehabit123).
//   3. Conecte o celular nesse Wi-Fi. O portal costuma abrir sozinho; se não
//      abrir, digite http://192.168.4.1 no navegador. (O aviso de "sem
//      internet" é esperado: essa rede serve só para configurar.)
//   4. Escolha a rede da clínica, digite a senha dela e o código de 6
//      dígitos que aparece na tela Dispositivo do Rehabit.
//   5. O aparelho grava tudo na memória e já se conecta.
//
// Trocou só a rede ou a senha do Wi-Fi: desligue e ligue — sem conseguir
// entrar na rede salva, ele abre o portal sozinho; deixe o código em branco e
// o pareamento e a tara ficam. Mudou de clínica: segure o botão BOOT por 5
// segundos com o aparelho ligado e conectado. Ele apaga a configuração e
// volta ao passo 2.
//
// O que este firmware faz depois de pareado:
//   * lê o MPU6050 a 100 Hz e estima a gravidade com um filtro complementar
//     (acelerômetro + giroscópio), que não treme como o acelerômetro sozinho
//     nem escorrega como o giroscópio sozinho;
//   * calibra o giroscópio no boot e guarda a pose de zero na memória, então
//     ela sobrevive a desligar e ligar;
//   * manda telemetria (ângulo, bateria, sinal, série, firmware) e obedece
//     aos comandos que voltam na resposta — tarar, identificar, iniciar e
//     parar captura, reiniciar;
//   * acelera para 10 amostras por segundo quando alguém está com a tela
//     aberta ou gravando, e desacelera quando ninguém está olhando.
//
// A luz de status tem ritmo próprio: roda numa tarefa à parte, então não
// trava nem muda de ritmo quando a rede demora. O que cada padrão quer dizer
// está no bloco "LED de status", mais abaixo, e no guia.
//
// ================== LIGAÇÕES ==================
//   MPU6050 VCC  -> 3V3
//   MPU6050 GND  -> GND
//   MPU6050 SDA  -> GPIO21
//   MPU6050 SCL  -> GPIO22
//   LED de status-> GPIO19 -> resistor 220 ohm -> LED azul -> GND
//   Botão BOOT   -> GPIO0 (já existe na placa; segurar 5 s reconfigura)
//   Bateria      -> divisor 100k/100k -> GPIO34   (opcional; veja PINO_BATERIA)
//
// Antes de gravar este firmware numa placa recém-soldada, rode o
// teste-hardware/teste-hardware.ino: ele confere cada ligação e diz qual
// falhou (passo a passo em teste-hardware-GUIA.md).
//
// ================== COMO O ÂNGULO É MEDIDO ==================
// O aparelho vai no segmento MÓVEL da articulação — no braço, para medir o
// ombro. NÃO importa em que orientação ele é amarrado: o zero não vem de um
// eixo escolhido no código, vem da pose que você marcar.
//
//   1. Prenda o aparelho no braço do paciente.
//   2. Com o braço PENDURADO ao lado do tronco, clique em "Zerar (tara)" na
//      tela Dispositivo. Aquela posição vira 0°.
//   3. A partir daí o número é o quanto o braço se afastou dali:
//        braço pendurado ................  0°
//        braço na horizontal ............ 90°
//        braço acima da cabeça ......... ~180°
//
// A conta é o ângulo entre a gravidade de agora e a gravidade na pose de
// zero. Por sair de um produto escalar, ela é sempre positiva (0 a 180) —
// mede o quanto o braço abriu, não para que lado. Uma hiperextensão de 10°
// aparece como 10°, igual a uma flexão de 10°.
//
// A medida é em relação à GRAVIDADE, não ao tronco: vale enquanto o paciente
// estiver de pé ou sentado ereto. Se ele se inclinar, o tronco sai da
// vertical e o número deixa de corresponder ao ângulo da articulação.
//
// BIBLIOTECAS (Gerenciar Bibliotecas): "Adafruit MPU6050" e "WiFiManager"
// (de tzapu).

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <WiFiManager.h>
#include <Preferences.h>

// ------------------------------------------------------------------
// Configuração
// ------------------------------------------------------------------

// Onde o Rehabit está publicado. Só precisa mudar se você republicar a API
// em outro endereço.
const char *BASE_URL = "https://rehabit-api-4tex.onrender.com/api";

const char *VERSAO_FIRMWARE = "2.3";

const char *AP_NOME = "Rehabit-Goniometro";
const char *AP_SENHA = "rehabit123";

const int PINO_BOTAO_RESET = 0;  // BOOT na maioria das placas DevKit
// LED azul externo, o do diagrama de montagem. Numa placa sem ele, ponha 2:
// é o LED que já vem soldado na maioria das DevKit.
const int PINO_LED = 19;
// Pino do divisor de tensão da bateria. Fica -1 porque o diagrama de
// montagem não tem o divisor: com o pino solto, a leitura seria ruído, e o
// Rehabit mostraria um número inventado em vez de simplesmente não mostrar
// bateria. Soldou o divisor (veja o guia)? Ponha 34.
const int PINO_BATERIA = -1;

const unsigned long SEGURAR_PARA_RESETAR_MS = 5000;
const unsigned long AVISO_SEM_PAREAMENTO_MS = 30000;

// Peso do giroscópio no filtro complementar. Perto de 1 = mais suave e mais
// sujeito a deriva; perto de 0 = mais fiel à gravidade e mais trêmulo.
const float PESO_GIRO = 0.98f;

const unsigned long PERIODO_AMOSTRA_US = 10000;  // 100 Hz de leitura do sensor
const unsigned long TIMEOUT_HTTP_MS = 4000;
const unsigned long INTERVALO_INICIAL_MS = 2000;

// Sem nenhuma telemetria aceita por este tempo, a luz avisa que o servidor
// não responde. Parado, o aparelho manda um pacote a cada 2 s: dá folga para
// alguns envios perdidos antes de acusar. É o mesmo limite da captura (abaixo):
// com valores diferentes, uma captura abandonada passava uns segundos em
// "tudo certo" entre deixar de dizer "gravando" e acusar o servidor.
const unsigned long SERVIDOR_CALADO_MS = 10000;
// Uma captura sem contato por este tempo é encerrada pelo próprio aparelho.
// O servidor fecha a dele entre 8 e 10 s de silêncio (timeout de 8 s, conferido
// a cada 2 s), mas não tem como mandar o PARAR_CAPTURA para quem está fora do
// ar — sem isto, a luz ficaria dizendo "gravando" para sempre.
const unsigned long CAPTURA_SEM_CONTATO_MS = 10000;

// ------------------------------------------------------------------
// Estado
// ------------------------------------------------------------------

WiFiClientSecure clienteSeguro;
WiFiClient clienteInseguro;

Adafruit_MPU6050 mpu;
Preferences memoria;

String tokenDispositivo = "";
String numeroSerie;

/* Gravidade estimada no referencial da placa. É a partir dela que sai o
   ângulo: a inclinação é a diferença entre para onde a gravidade aponta agora
   e para onde apontava na pose de zero. */
float gx = 0.0f, gy = 0.0f, gz = 1.0f;

/* A pose que vale 0°: braço pendurado ao lado do tronco. Guardada como o
   vetor de gravidade daquele momento — e não como um número —, porque assim o
   zero não depende de como a placa foi amarrada no braço. */
float refX = 0.0f, refY = 0.0f, refZ = 0.0f;
bool referenciaDefinida = false;

float biasGiroX = 0.0f, biasGiroY = 0.0f, biasGiroZ = 0.0f;
bool calibrado = false;

unsigned long ultimaAmostraUs = 0;
unsigned long proximoEnvio = 0;
unsigned long intervaloEnvioMs = INTERVALO_INICIAL_MS;
unsigned long ultimoAvisoPareamento = 0;
unsigned long botaoPressionadoDesde = 0;
// A captura foi largada pelo limite de 10 s sem resposta? Se o servidor
// voltar dizendo que ela continua aberta (as respostas se perderam, mas os
// pacotes chegaram), o aparelho volta a gravar. Só a loopTask usa.
bool capturaLargadaPorSilencio = false;

/* Tudo o que a luz de status consulta. Ela roda em outra tarefa (veja "LED
   de status"), então só lê variáveis simples de até 32 bits — cada escrita
   chega inteira, coisa que uma String, ponteiro mais tamanho, não garante. O
   volatile obriga a reler da memória a cada consulta, em vez de reaproveitar
   um valor velho guardado num registrador. */
volatile bool sensorAusente = false;
volatile bool calibrando = true;            // do ligar até o fim da calibração
volatile bool portalAberto = false;
volatile bool wifiConectado = false;
volatile bool temToken = false;
volatile bool pareamentoRecusado = false;   // a API respondeu 401 ou 403
volatile bool capturando = false;
volatile uint8_t pedidosIdentificacao = 0;  // cada IDENTIFICAR soma um
volatile uint32_t ultimoEnvioOk = 0;        // millis() da última telemetria aceita (200)
// De quando conta o "servidor calado": o último 200 ou a última (re)conexão
// ao Wi-Fi, o que vier depois. Fica separado de ultimoEnvioOk porque a
// reconexão dá tolerância ao servidor, mas não pode esticar uma captura sem
// contato — aquela conta é só com respostas de verdade.
volatile uint32_t silencioDesde = 0;

WiFiClient &clienteParaUrl(const char *url) {
  return String(url).startsWith("https://") ? (WiFiClient &)clienteSeguro : clienteInseguro;
}

// ------------------------------------------------------------------
// Memória interna (NVS)
// ------------------------------------------------------------------
//
// Guarda o token e a tara entre reinícios, para não precisar parear nem
// rezerar o aparelho toda vez que faltar luz.

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

void salvarReferencia(float x, float y, float z) {
  memoria.begin("rehabit", false);
  memoria.putFloat("refx", x);
  memoria.putFloat("refy", y);
  memoria.putFloat("refz", z);
  memoria.end();
}

/** Preenche refX/Y/Z do que estiver guardado; devolve false se nunca houve tara. */
bool carregarReferencia() {
  memoria.begin("rehabit", true);
  refX = memoria.getFloat("refx", 0.0f);
  refY = memoria.getFloat("refy", 0.0f);
  refZ = memoria.getFloat("refz", 0.0f);
  memoria.end();
  // Vetor de gravidade tem módulo ~9,8; perto de zero significa "não gravado".
  return sqrtf(refX * refX + refY * refY + refZ * refZ) > 0.5f;
}

void apagarConfiguracao() {
  memoria.begin("rehabit", false);
  memoria.clear();  // leva junto a tara: o aparelho está indo para outro lugar
  memoria.end();

  WiFiManager wm;
  wm.resetSettings();  // apaga também a rede Wi-Fi salva
}

// ------------------------------------------------------------------
// Apoio: JSON na unha
// ------------------------------------------------------------------
//
// As respostas da API são pequenas e de formato conhecido, então vale mais
// um leitor de 20 linhas do que puxar uma biblioteca de JSON inteira para
// dentro do firmware. Os dois leitores procuram a chave e leem o valor até
// o delimitador — sem depender da ORDEM dos campos nem de haver uma vírgula
// depois deles.

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

/** 1 para true, 0 para false e -1 se a chave não veio (servidor antigo). */
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
  // Os três eixos, e não só um: o filtro agora gira um vetor no espaço, então
  // uma deriva em qualquer eixo entortaria a estimativa.
  biasGiroX = somaX / amostras;
  biasGiroY = somaY / amostras;
  biasGiroZ = somaZ / amostras;
  calibrado = true;
  calibrando = false;  // a luz deixa de pedir "não mexa"
  Serial.printf("Giroscopio calibrado (bias X=%.4f Y=%.4f Z=%.4f rad/s)\n",
                biasGiroX, biasGiroY, biasGiroZ);
}

/**
 * Ângulo entre dois vetores, em graus (0 a 180).
 *
 * É o coração da medida: com o braço na pose de zero os dois vetores
 * coincidem e dá 0°; com o braço na horizontal a gravidade girou um quarto de
 * volta em relação à pose de zero e dá 90°. Como sai de um produto escalar,
 * não depende de qual eixo do chip está apontando para onde — só de quanto a
 * placa girou desde a tara.
 */
float anguloEntre(float ax, float ay, float az, float bx, float by, float bz) {
  float modA = sqrtf(ax * ax + ay * ay + az * az);
  float modB = sqrtf(bx * bx + by * by + bz * bz);
  if (modA < 0.001f || modB < 0.001f) {
    return 0.0f;
  }
  float cosseno = (ax * bx + ay * by + az * bz) / (modA * modB);
  // Arredondamento pode empurrar o cosseno para fora de [-1, 1] e acosf
  // devolveria NaN, que contaminaria tudo dali para a frente.
  if (cosseno > 1.0f) cosseno = 1.0f;
  if (cosseno < -1.0f) cosseno = -1.0f;
  return acosf(cosseno) * 180.0f / PI;
}

/* Guarda a última leitura crua do acelerômetro, para o pacote de telemetria
   poder mandar o ângulo sem filtro junto do filtrado. */
float acelBrutoX = 0.0f, acelBrutoY = 0.0f, acelBrutoZ = 0.0f;

/**
 * Filtro complementar sobre o VETOR de gravidade.
 *
 * O giroscópio diz quanto a placa girou desde a amostra anterior, e girar a
 * placa é o mesmo que girar a gravidade para o lado contrário dentro do
 * referencial dela — daí o produto vetorial com sinal negativo. Esse palpite
 * é suave mas escorrega com o tempo, então o acelerômetro puxa a estimativa
 * de volta para a gravidade de verdade a cada amostra.
 */
void atualizarAngulo() {
  unsigned long agoraUs = micros();
  if (ultimaAmostraUs != 0 && (agoraUs - ultimaAmostraUs) < PERIODO_AMOSTRA_US) {
    return;
  }
  float dt = ultimaAmostraUs == 0 ? 0.0f : (agoraUs - ultimaAmostraUs) / 1000000.0f;
  ultimaAmostraUs = agoraUs;

  sensors_event_t accel, gyro, temp;
  mpu.getEvent(&accel, &gyro, &temp);
  acelBrutoX = accel.acceleration.x;
  acelBrutoY = accel.acceleration.y;
  acelBrutoZ = accel.acceleration.z;

  // Um envio HTTP lento pode segurar o loop por segundos. Integrar o
  // giroscópio por um buraco desses só produz lixo — nesse caso a estimativa
  // é ressincronizada direto pela gravidade medida.
  if (dt <= 0.0f || dt > 0.2f) {
    gx = acelBrutoX;
    gy = acelBrutoY;
    gz = acelBrutoZ;
    return;
  }

  float wx = gyro.gyro.x - biasGiroX;
  float wy = gyro.gyro.y - biasGiroY;
  float wz = gyro.gyro.z - biasGiroZ;

  // g_previsto = g - (omega x g) * dt
  float previstoX = gx - (wy * gz - wz * gy) * dt;
  float previstoY = gy - (wz * gx - wx * gz) * dt;
  float previstoZ = gz - (wx * gy - wy * gx) * dt;

  gx = PESO_GIRO * previstoX + (1.0f - PESO_GIRO) * acelBrutoX;
  gy = PESO_GIRO * previstoY + (1.0f - PESO_GIRO) * acelBrutoY;
  gz = PESO_GIRO * previstoZ + (1.0f - PESO_GIRO) * acelBrutoZ;
}

/** Quanto o braço se afastou da pose de zero: 0° pendurado, 90° na horizontal. */
float anguloAtual() {
  if (!referenciaDefinida) {
    return 0.0f;
  }
  return anguloEntre(gx, gy, gz, refX, refY, refZ);
}

/** O mesmo ângulo, mas direto do acelerômetro — mostra o quanto o filtro suavizou. */
float anguloSemFiltro() {
  if (!referenciaDefinida) {
    return 0.0f;
  }
  return anguloEntre(acelBrutoX, acelBrutoY, acelBrutoZ, refX, refY, refZ);
}

/**
 * Fixa a pose atual como 0°. O profissional aperta isto com o braço do
 * paciente pendurado ao lado do tronco; a partir daí, levantar o braço até a
 * horizontal marca 90°, e até acima da cabeça, perto de 180°.
 */
void aplicarTara() {
  refX = gx;
  refY = gy;
  refZ = gz;
  referenciaDefinida = true;
  salvarReferencia(refX, refY, refZ);
  Serial.printf("Tara aplicada: a posicao atual virou 0 grau (g = %.2f, %.2f, %.2f).\n", refX, refY, refZ);
}

// ------------------------------------------------------------------
// LED de status
// ------------------------------------------------------------------
//
// Sem tela no aparelho, o LED é a única forma de saber o que está havendo
// olhando para ele. Do mais forte para o mais fraco — valendo duas coisas ao
// mesmo tempo, aparece a de cima:
//
//   sensor não encontrado ..... pisca rápido sem parar (150 aceso / 150 apagado)
//   identificar ............... pisca muito rápido por 4 s (80 / 80)
//   gravando captura .......... acesa fixa
//   portal de configuração .... "respirando": acende e apaga suave, ciclo de 3 s
//   calibrando, logo ao ligar . fraca e parada: não mexa no aparelho
//   sem Wi-Fi ................. pisca devagar (500 / 500)
//   sem pareamento ............ pisca devagar (500 / 500), igual ao de cima
//   servidor não responde ..... piscada dupla a cada 2 s
//   tudo certo ................ piscada curtinha a cada 2 s (60 ms)
//
// A luz roda numa tarefa própria do FreeRTOS, e não no loop(): o loop fica
// parado segundos num envio sem resposta (até 4 s para conectar e 4 s para
// ler, e o DNS pode segurar uns 15 s), no portal do WiFiManager e na
// calibração. Presa a ele, a luz mudava de ritmo conforme a rede — e era
// justamente a rede que ela precisava mostrar. A tarefa acorda a cada 10 ms,
// lê o estado, escreve no LED e volta a dormir; é a única que mexe no pino.

// Modos da luz, na ordem de prioridade. Constantes uint8_t, e não um enum: a
// Arduino IDE gera sozinha os protótipos das funções e os põe lá em cima,
// antes de um tipo declarado aqui embaixo, e uma função que recebesse o enum
// não compilaria (veja "Erros de compilação conhecidos" no guia).
const uint8_t LUZ_SEM_SENSOR = 0;
const uint8_t LUZ_IDENTIFICANDO = 1;
const uint8_t LUZ_GRAVANDO = 2;
const uint8_t LUZ_PORTAL = 3;
const uint8_t LUZ_CALIBRANDO = 4;
const uint8_t LUZ_SEM_WIFI = 5;
const uint8_t LUZ_SEM_PAREAMENTO = 6;
const uint8_t LUZ_SEM_SERVIDOR = 7;
const uint8_t LUZ_TUDO_CERTO = 8;

const uint32_t PERIODO_LUZ_MS = 10;
const uint32_t DURACAO_IDENTIFICACAO_MS = 4000;
const uint32_t CICLO_RESPIRAR_MS = 3000;

// PWM de 12 bits a 5 kHz: rápido demais para o olho perceber o liga-desliga,
// e com degraus finos o bastante para o "respirando" não andar aos saltos
// perto do apagado, onde o olho mais nota diferença.
const uint32_t LUZ_PWM_HZ = 5000;
const uint8_t LUZ_PWM_BITS = 12;
const uint32_t LUZ_DUTY_MAXIMO = 4095;  // (1 << 12) - 1

// Brilhos na escala do olho, de 0 a 255 (veja dutyDoBrilho).
const uint8_t BRILHO_TOTAL = 255;
const uint8_t BRILHO_CALIBRANDO = 38;       // ~15%: acesa, mas claramente fraca
const uint8_t BRILHO_RESPIRAR_MINIMO = 10;  // fundo do "respirando": quase apagada

/** Acesa nos primeiros `acesoMs` de cada ciclo de `cicloMs`, apagada no resto. */
uint8_t piscar(uint32_t tempoMs, uint32_t acesoMs, uint32_t cicloMs) {
  return tempoMs % cicloMs < acesoMs ? BRILHO_TOTAL : 0;
}

/**
 * Brilho de um modo num dado instante, de 0 a 255 na escala do olho.
 *
 * Só depende dos argumentos, para dar para conferir a linha do tempo de cada
 * padrão no computador. O padrão conta a partir de quando o modo começou:
 * toda troca já abre com a luz acesa, sem esperar o fim de um ciclo antigo.
 */
uint8_t brilhoDoModo(uint8_t modo, uint32_t agoraMs, uint32_t inicioModoMs) {
  uint32_t t = agoraMs - inicioModoMs;
  switch (modo) {
    case LUZ_SEM_SENSOR:
      return piscar(t, 150, 300);
    case LUZ_IDENTIFICANDO:
      return piscar(t, 80, 160);
    case LUZ_GRAVANDO:
      return BRILHO_TOTAL;
    case LUZ_PORTAL: {
      // Cosseno: sai do fundo, sobe devagar, demora no alto e desce do mesmo
      // jeito — sem os cantos de uma rampa reta, que parecem um tranco.
      float onda = 0.5f - 0.5f * cosf(6.2831853f * (t % CICLO_RESPIRAR_MS) / CICLO_RESPIRAR_MS);
      return (uint8_t)(BRILHO_RESPIRAR_MINIMO + (BRILHO_TOTAL - BRILHO_RESPIRAR_MINIMO) * onda + 0.5f);
    }
    case LUZ_CALIBRANDO:
      return BRILHO_CALIBRANDO;
    case LUZ_SEM_WIFI:
    case LUZ_SEM_PAREAMENTO:
      return piscar(t, 500, 1000);
    case LUZ_SEM_SERVIDOR: {
      uint32_t fase = t % 2000;  // 100 aceso, 150 apagado, 100 aceso, 1650 apagado
      return (fase < 100 || (fase >= 250 && fase < 350)) ? BRILHO_TOTAL : 0;
    }
    default:  // LUZ_TUDO_CERTO
      return piscar(t, 60, 2000);
  }
}

/**
 * Converte o brilho da escala do olho para o duty do PWM.
 *
 * O olho não é linear: com metade do duty o LED já parece quase no máximo, e
 * as diferenças que a gente enxerga estão todas perto do apagado. Elevar ao
 * quadrado (uma correção de gama 2) compensa — sem isso, o "respirando"
 * pareceria aceso o ciclo quase inteiro, e a luz fraca da calibração
 * pareceria média.
 */
uint32_t dutyDoBrilho(uint8_t brilho) {
  return ((uint32_t)brilho * brilho * LUZ_DUTY_MAXIMO + 255 * 255 / 2) / (255 * 255);
}

/**
 * Qual padrão mostrar agora: o primeiro que valer, de cima para baixo.
 * semEnvioOkMs conta desde a última resposta 200; silencioMs, desde ela ou
 * desde a última reconexão ao Wi-Fi (veja silencioDesde).
 */
uint8_t modoDaLuz(bool identificando, bool comWifi, uint32_t semEnvioOkMs, uint32_t silencioMs) {
  if (sensorAusente) return LUZ_SEM_SENSOR;
  if (identificando) return LUZ_IDENTIFICANDO;
  // Com 10 s sem contato a captura provavelmente já acabou no servidor: a luz
  // para de dizer "gravando" na hora, mesmo que o loop ainda esteja preso
  // num envio.
  if (capturando && semEnvioOkMs <= CAPTURA_SEM_CONTATO_MS) return LUZ_GRAVANDO;
  if (portalAberto) return LUZ_PORTAL;
  if (calibrando) return LUZ_CALIBRANDO;
  if (!comWifi) return LUZ_SEM_WIFI;
  if (!temToken || pareamentoRecusado) return LUZ_SEM_PAREAMENTO;
  if (silencioMs > SERVIDOR_CALADO_MS) return LUZ_SEM_SERVIDOR;
  return LUZ_TUDO_CERTO;
}

void tarefaDaLuz(void *) {
  // O LEDC mudou no core 3.x: o canal sumiu e o pino virou o identificador.
  // A versão do core já vem no Arduino.h.
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(PINO_LED, LUZ_PWM_HZ, LUZ_PWM_BITS);
#else
  const uint8_t canal = 0;
  ledcSetup(canal, LUZ_PWM_HZ, LUZ_PWM_BITS);
  ledcAttachPin(PINO_LED, canal);
#endif

  uint8_t modoAtual = 0xFF;  // nenhum: o primeiro modo marca o próprio início
  uint32_t inicioModo = 0;
  uint32_t dutyAtual = 0xFFFFFFFF;
  // Os 4 s do Identificar são contados aqui: o loop só avisa que chegou um
  // pedido. Assim um envio demorado não encurta nem estica a piscada.
  uint8_t pedidosVistos = pedidosIdentificacao;
  uint32_t inicioIdentificacao = 0;
  bool identificando = false;

  for (;;) {
    // A ordem das leituras importa. O evento de Wi-Fi, que pode interromper
    // esta tarefa, acerta silencioDesde ANTES de marcar wifiConectado; lendo
    // na ordem contrária, a luz nunca junta um "conectado" novo com uma hora
    // velha. E o relógio vem por último, para "agora" nunca ficar antes das
    // marcas de tempo — a conta daria negativa, um número enorme.
    bool comWifi = wifiConectado;
    uint32_t ultimoOk = ultimoEnvioOk;
    uint32_t silencio = silencioDesde;
    uint32_t agora = millis();

    uint8_t pedidos = pedidosIdentificacao;
    if (pedidos != pedidosVistos) {
      pedidosVistos = pedidos;
      inicioIdentificacao = agora;
      identificando = true;
      modoAtual = 0xFF;  // pedido novo recomeça a piscada do zero
    }
    if (identificando && agora - inicioIdentificacao >= DURACAO_IDENTIFICACAO_MS) {
      identificando = false;
    }

    uint8_t modo = modoDaLuz(identificando, comWifi, agora - ultimoOk, agora - silencio);
    if (modo != modoAtual) {
      modoAtual = modo;
      inicioModo = agora;
    }

    uint32_t duty = dutyDoBrilho(brilhoDoModo(modo, agora, inicioModo));
    if (duty != dutyAtual) {
      dutyAtual = duty;
#if ESP_ARDUINO_VERSION_MAJOR >= 3
      ledcWrite(PINO_LED, duty);
#else
      ledcWrite(canal, duty);
#endif
    }
    vTaskDelay(pdMS_TO_TICKS(PERIODO_LUZ_MS));
  }
}

/**
 * Põe a luz para rodar. Chamada logo no começo do setup(), para ela já ter
 * ritmo próprio durante a checagem do sensor, a calibração e o portal.
 *
 * Núcleo: o mesmo do loop() (o 1, no ESP32). O núcleo 0 é do Wi-Fi, cujas
 * tarefas têm prioridade bem maior e atrasariam a luz justo no tráfego; aqui
 * ela só disputa com o loop. Prioridade 2, um degrau acima do loop (1): ela
 * interrompe até o handshake TLS, conta pesada feita dentro do loop, e não
 * atrapalha ninguém porque passa quase todo o tempo dormindo. Pilha de 3 KB:
 * as contas usam pouco; a folga é para o log de erro do LEDC, que formata
 * texto.
 */
void iniciarTarefaDaLuz() {
  if (xTaskCreateUniversal(tarefaDaLuz, "luz", 3072, NULL, 2, NULL, ARDUINO_RUNNING_CORE) != pdPASS) {
    Serial.println("Nao consegui criar a tarefa da luz: o aparelho funciona, mas sem LED.");
  }
}

/**
 * Mantém wifiConectado em dia a partir dos eventos do Wi-Fi.
 *
 * A luz não chama WiFi.status() por conta própria: no core 2.x a troca de
 * status é feita em dois passos (apaga o valor antigo, depois grava o novo),
 * e quem lê de outra tarefa bem no meio vê "desconectado" por um instante.
 * Dentro do evento o status já está completo — o core atualiza antes de
 * chamar quem se inscreveu, nos dois cores.
 */
void acompanharWifi() {
  WiFi.onEvent([](arduino_event_id_t, arduino_event_info_t) {
    bool conectado = WiFi.status() == WL_CONNECTED;
    if (conectado && !wifiConectado) {
      // Acabou de (re)conectar: a contagem de "servidor calado" recomeça
      // daqui, senão a luz culparia o servidor pelo tempo sem Wi-Fi. A da
      // captura (ultimoEnvioOk) não: reconectar não é falar com o servidor.
      silencioDesde = millis();
    }
    wifiConectado = conectado;
  });
}

// ------------------------------------------------------------------
// Pareamento e portal
// ------------------------------------------------------------------

/** Troca o código de 6 dígitos por um token próprio do aparelho. */
bool parear(const String &codigo) {
  if (codigo.length() == 0) {
    return false;
  }
  Serial.println("Tentando parear com o codigo informado...");

  HTTPClient http;
  http.setConnectTimeout(TIMEOUT_HTTP_MS);
  http.setTimeout(TIMEOUT_HTTP_MS);
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
  temToken = true;
  pareamentoRecusado = false;
  Serial.printf("Pareado com a clinica \"%s\". Token guardado.\n",
                extrairTexto(resposta, "nomeClinica").c_str());
  return true;
}

void conectarOuAbrirPortal() {
  WiFiManager wm;
  wm.setConfigPortalTimeout(0);  // fica no portal até alguém configurar

  // Campo extra no portal, além de rede e senha: é onde entra o código que a
  // clínica vê na tela Dispositivo.
  WiFiManagerParameter campoCodigo(
      "codigo", "Codigo de pareamento (6 digitos)", "", 6,
      "pattern=\"[0-9]{6}\" inputmode=\"numeric\"");
  wm.addParameter(&campoCodigo);

  // Daqui até a configuração terminar, o setup() fica parado dentro do
  // autoConnect — a luz respira sozinha enquanto isso.
  wm.setAPCallback([](WiFiManager *) { portalAberto = true; });

  Serial.printf("Se nao conectar, abra o Wi-Fi \"%s\" (senha %s) no celular.\n",
                AP_NOME, AP_SENHA);
  // O portal quase nunca abre sozinho (o Windows praticamente nunca detecta
  // captive portal), e sem este endereco a pessoa fica sem saber o que fazer.
  Serial.println("O celular vai avisar que a rede nao tem internet — e normal, ela e so para configurar.");
  Serial.println("Se a pagina nao abrir sozinha, digite no navegador: http://192.168.4.1");

  // autoConnect: tenta a rede salva; não tendo, sobe o ponto de acesso e
  // bloqueia aqui até a pessoa terminar a configuração.
  if (!wm.autoConnect(AP_NOME, AP_SENHA)) {
    Serial.println("Falhou ao configurar. Reiniciando...");
    delay(2000);
    ESP.restart();
  }
  portalAberto = false;

  WiFi.setSleep(false);  // o modem dormindo atrasa os POSTs e engasga o tempo real
  Serial.print("Wi-Fi conectado, IP: ");
  Serial.println(WiFi.localIP());

  String codigo = String(campoCodigo.getValue());
  codigo.trim();
  if (codigo.length() > 0) {
    // Veio do portal agora: vale mais que o token antigo, porque a pessoa
    // pode estar movendo o aparelho para outra clínica.
    parear(codigo);
  }

  // A tolerância de "servidor calado" conta daqui, quando a telemetria
  // começa de fato: o pareamento acima pode ter levado alguns segundos.
  silencioDesde = millis();
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

// ------------------------------------------------------------------
// Telemetria
// ------------------------------------------------------------------

void tratarComando(const String &comando) {
  if (comando.length() == 0 || comando == "NENHUM") return;

  Serial.printf("Comando recebido: %s\n", comando.c_str());
  if (comando == "TARAR") {
    aplicarTara();
  } else if (comando == "IDENTIFICAR") {
    // Só avisa a luz, que cronometra os 4 s ela mesma. (Sem ++: no C++20 do
    // core 3.x, ++ numa volatile gera aviso.)
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

/**
 * Acerta a captura pelo que o servidor diz (1 aberta, 0 fechada, -1 servidor
 * antigo, sem o campo). Fechada lá e aberta aqui: a conexão caiu no meio da
 * gravação e o PARAR_CAPTURA não teve como chegar. Aberta lá e largada aqui
 * pelo limite de 10 s: as respostas se perderam, mas os pacotes chegaram, e a
 * gravação continua. Fora esse caso, uma captura aberta lá não acende a luz
 * aqui — com dois aparelhos na mesma clínica, ela pode ser do outro.
 */
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
  if (!http.begin(clienteParaUrl(BASE_URL), String(BASE_URL) + "/goniometro/telemetria")) {
    Serial.println("Nao consegui abrir a conexao de telemetria.");
    return;
  }
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + tokenDispositivo);

  int bateria = lerBateria();
  // Sem idClinica: o servidor tira do token deste aparelho, e é isso que
  // impede um goniômetro de escrever na clínica de outro.
  String corpo = "{";
  corpo += "\"angulo\":" + String(anguloAtual(), 2);
  corpo += ",\"anguloBruto\":" + String(anguloSemFiltro(), 2);
  if (bateria >= 0) corpo += ",\"bateria\":" + String(bateria);
  corpo += ",\"rssi\":" + String(WiFi.RSSI());
  corpo += ",\"numeroSerie\":\"" + numeroSerie + "\"";
  corpo += ",\"firmware\":\"" + String(VERSAO_FIRMWARE) + "\"";
  corpo += ",\"ip\":\"" + WiFi.localIP().toString() + "\"";
  corpo += ",\"calibrado\":" + String(calibrado ? "true" : "false");
  corpo += "}";

  int status = http.POST(corpo);

  if (status == 200) {
    uint32_t agoraOk = millis();
    ultimoEnvioOk = agoraOk;
    silencioDesde = agoraOk;
    pareamentoRecusado = false;
    String resposta = http.getString();
    // O servidor manda o ritmo: rápido enquanto alguém olha ou grava, lento
    // quando ninguém está usando (é a bateria do aparelho em jogo).
    long intervalo = extrairNumero(resposta, "intervaloMs", (long)intervaloEnvioMs);
    if (intervalo >= 50 && intervalo <= 60000) {
      intervaloEnvioMs = (unsigned long)intervalo;
    }
    tratarComando(extrairTexto(resposta, "comando"));
    // Depois do comando: um PARAR_CAPTURA normal já chega com o campo em
    // false e não deve passar por "captura que o servidor fechou".
    acertarCapturaComServidor(extrairBooleano(resposta, "capturando"));
  } else if (status == 401) {
    pareamentoRecusado = true;
    Serial.println("Token recusado. Reconfigure segurando o botao BOOT por 5s.");
  } else if (status == 403) {
    pareamentoRecusado = true;
    Serial.println("Este aparelho foi revogado pela clinica. Pareie de novo.");
  } else {
    Serial.printf("Envio falhou, status=%d\n", status);
  }
  http.end();
}

// ------------------------------------------------------------------
// setup / loop
// ------------------------------------------------------------------

void setup() {
  Serial.begin(115200);
  // A luz antes de tudo: daqui em diante ela tem ritmo próprio, e nem o
  // sensor faltando, nem a calibração, nem o portal a deixam apagada.
  iniciarTarefaDaLuz();
  delay(500);
  Serial.printf("\nRehabit — goniometro digital, firmware %s\n", VERSAO_FIRMWARE);

  pinMode(PINO_BOTAO_RESET, INPUT_PULLUP);
  if (PINO_BATERIA >= 0) {
    analogSetPinAttenuation(PINO_BATERIA, ADC_11db);  // faixa até ~3,1 V no pino
  }

  Wire.begin();
  Wire.setClock(400000);  // I2C rápido: 100 leituras por segundo não cabem no modo padrão
  clienteSeguro.setInsecure();  // TCC/demo: sem verificação de certificado.

  if (!mpu.begin()) {
    Serial.println("MPU6050 nao encontrado! Confira a fiacao (SDA=21, SCL=22, VCC=3V3, GND).");
    // Erro de hardware, não adianta seguir. A tarefa da luz pisca rápido sem
    // parar; aqui só se espera.
    sensorAusente = true;
    while (true) {
      delay(1000);
    }
  }
  mpu.setAccelerometerRange(MPU6050_RANGE_4_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
  Serial.println("MPU6050 encontrado.");

  referenciaDefinida = carregarReferencia();

  calibrarGiroscopio();

  // Primeira leitura da gravidade como ponto de partida do filtro — sem isso
  // a estimativa levaria alguns segundos convergindo até a gravidade real.
  sensors_event_t accel, gyro, temp;
  mpu.getEvent(&accel, &gyro, &temp);
  gx = accel.acceleration.x;
  gy = accel.acceleration.y;
  gz = accel.acceleration.z;
  acelBrutoX = gx;
  acelBrutoY = gy;
  acelBrutoZ = gz;

  if (referenciaDefinida) {
    Serial.printf("Tara guardada: g = %.2f, %.2f, %.2f\n", refX, refY, refZ);
  } else {
    // Nunca houve tara: adota a pose do boot como zero provisório, para o
    // número na tela ser algo em vez de nada. O profissional refaz a tara com
    // o braço pendurado, e aí o zero passa a valer de verdade.
    refX = gx;
    refY = gy;
    refZ = gz;
    referenciaDefinida = true;
    Serial.println("Sem tara guardada: usando a posicao do boot como zero provisorio.");
    Serial.println("Prenda o aparelho no braco, deixe o braco pendurado e use \"Zerar (tara)\" no site.");
  }

  numeroSerie = montarNumeroSerie();
  Serial.printf("Numero de serie: %s\n", numeroSerie.c_str());

  tokenDispositivo = carregarToken();
  temToken = tokenDispositivo.length() > 0;
  if (temToken) {
    Serial.println("Token encontrado na memoria.");
  } else {
    Serial.println("Sem token: use o portal para parear.");
  }

  acompanharWifi();
  conectarOuAbrirPortal();
}

void loop() {
  atualizarAngulo();
  verificarBotaoDeReset();

  // A hora do último envio aceito vem antes do relógio, como na tarefa da
  // luz, para "agora" nunca ficar antes dela.
  uint32_t ultimoOk = ultimoEnvioOk;
  unsigned long agora = millis();

  // Captura presa: a conexão caiu no meio da gravação e o PARAR_CAPTURA não
  // tem como chegar. O servidor fecha a dele entre 8 e 10 s; o aparelho
  // desiste aos 10, e a luz deixa de dizer "gravando". (Se a conexão voltar
  // antes disso, a primeira resposta já traz "capturando":false e encerra.)
  if (capturando && agora - ultimoOk > CAPTURA_SEM_CONTATO_MS) {
    capturando = false;
    capturaLargadaPorSilencio = true;
    Serial.println("Captura encerrada: 10 s sem resposta do servidor (que fecha a dele entre 8 e 10 s).");
  }

  // Eco no Monitor Serial, útil para conferir a montagem sem abrir o site.
  static unsigned long ultimoEco = 0;
  if (agora - ultimoEco > 1000) {
    ultimoEco = agora;
    int bateria = lerBateria();
    char textoBateria[16];
    if (bateria >= 0) {
      snprintf(textoBateria, sizeof(textoBateria), "%d%%", bateria);
    } else {
      snprintf(textoBateria, sizeof(textoBateria), "sem medidor");
    }
    Serial.printf("Angulo: %6.2f graus (sem filtro %6.2f) | bateria %s | RSSI %d dBm%s\n",
                  anguloAtual(), anguloSemFiltro(), textoBateria, WiFi.RSSI(),
                  capturando ? " | GRAVANDO" : "");
  }

  if (tokenDispositivo.length() == 0) {
    // Sem pareamento não há para onde enviar; avisa de vez em quando para
    // não encher o monitor serial.
    if (agora - ultimoAvisoPareamento > AVISO_SEM_PAREAMENTO_MS) {
      ultimoAvisoPareamento = agora;
      Serial.println("Aparelho ainda nao pareado. Segure BOOT por 5s para abrir o portal.");
    }
    return;
  }

  if (WiFi.status() != WL_CONNECTED) {
    static unsigned long proximaReconexao = 0;
    if (agora >= proximaReconexao) {
      proximaReconexao = agora + 10000;
      Serial.println("Wi-Fi caiu, tentando reconectar...");
      WiFi.reconnect();
    }
    return;
  }

  if (agora >= proximoEnvio) {
    // Marca o próximo envio ANTES de enviar: o POST pode demorar, e contar a
    // partir do fim faria o intervalo real virar "intervalo + latência".
    proximoEnvio = agora + intervaloEnvioMs;
    enviarTelemetria();
  }
}
