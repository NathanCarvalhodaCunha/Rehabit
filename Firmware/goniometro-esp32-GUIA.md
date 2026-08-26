# Guia: montar e gravar o goniômetro (ESP32 + MPU6050)

O aparelho lê o ângulo da articulação e manda para o Rehabit várias vezes por
segundo. A tela **Dispositivo** mostra esse ângulo ao vivo, e o formulário de
sessão consegue preencher a amplitude sozinho a partir de uma gravação do
movimento.

## 1. Montar o hardware

| MPU6050 / componente | ESP32 |
| --- | --- |
| VCC | 3V3 |
| GND | GND |
| SDA | GPIO21 |
| SCL | GPIO22 |
| LED de status | GPIO2 (o LED azul que já vem soldado na maioria das DevKit) |
| Bateria (opcional) | divisor 100 kΩ / 100 kΩ → GPIO34 |

**Sobre a bateria:** o pino GPIO34 do ESP32 não aguenta os 4,2 V de uma LiPo,
por isso o divisor de tensão com dois resistores de 100 kΩ — ele entrega
metade da tensão ao pino. Se a sua placa vai ficar sempre ligada no USB, abra
o `.ino` e ponha `const int PINO_BATERIA = -1;`: o Rehabit simplesmente deixa
de mostrar bateria, em vez de mostrar um número inventado.

**Onde fixar o sensor:** no segmento **móvel** da articulação (por exemplo, na
perna, para medir o joelho), com o eixo X do chip acompanhando o osso. O outro
segmento fica parado e serve de referência.

## 2. Instalar suporte à placa ESP32 na Arduino IDE

1. **Arquivo → Preferências**.
2. Em "URLs adicionais para Gerenciadores de Placas", cole:
   `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
3. **Ferramentas → Placa → Gerenciador de Placas**, busque "esp32" (autor
   Espressif Systems) e instale.
4. **Ferramentas → Placa**, escolha o modelo da sua placa ("ESP32 Dev Module"
   se você não souber o nome exato).

## 3. Instalar a biblioteca do MPU6050

**Ferramentas → Gerenciar Bibliotecas**, busque "Adafruit MPU6050" e instale.
Vai pedir para instalar junto "Adafruit Unified Sensor" e "Adafruit BusIO" —
aceite, são dependências. Nenhuma outra biblioteca é necessária: o firmware lê
as respostas JSON da API com duas funções próprias, de propósito, para não
depender de mais nada.

## 4. Preencher e gravar o firmware

1. Abra `goniometro-esp32.ino` na Arduino IDE.
2. Preencha `WIFI_SSID` e `WIFI_SENHA`.
3. Preencha e-mail e senha de uma conta de **clínica** já cadastrada no
   Rehabit, em cada `ALVO` que você for usar.
4. Confira o `baseUrl` do alvo `"local"`: precisa ser o IP do PC que roda o
   backend (`ipconfig` no Windows), e o ESP32 tem que estar na mesma rede
   Wi-Fi. Se você só vai usar a nuvem, ponha `false` no campo `ativo` do alvo
   local — e vice-versa.
5. Conecte o ESP32 no USB, escolha a porta em **Ferramentas → Porta** (no
   Gerenciador de Dispositivos do Windows ela aparece como "Silicon Labs
   CP210x" ou "CH340") e clique em **Carregar**.

## 5. Conferir que está funcionando

Abra o **Monitor Serial** a **115200**. Na ordem, você deve ver:

```
Rehabit — goniometro digital, firmware 2.0
MPU6050 encontrado.
Tara guardada: 0.00 graus
Calibrando o giroscopio — mantenha o aparelho PARADO...
Giroscopio calibrado (bias X=... Y=... Z=...)
Numero de serie: A1B2-C3D4
Conectando ao Wi-Fi SUA_REDE....
Wi-Fi conectado, IP: 192.168.1.55
[local] login OK, idClinica=1
Angulo:  87.40 graus (bruto  87.40) | bateria 82% | RSSI -54 dBm
```

**Importante:** deixe o aparelho parado durante a calibração do giroscópio
(uns 2 segundos no boot). Se ele se mexer nessa hora, o zero fica torto e o
ângulo escorrega devagar — nesse caso, basta reiniciar a placa.

Depois abra a tela **Dispositivo** do Rehabit logado como a clínica: o selo
deve virar "Conectado" e o ângulo deve acompanhar o movimento do sensor.

## 6. O que o LED está dizendo

| LED | Significado |
| --- | --- |
| Pisca curtinho a cada 2 s | Tudo certo: Wi-Fi ligado e login feito |
| Pisca devagar, meio a meio | Sem Wi-Fi ou sem login em nenhum servidor |
| Pisca muito rápido por 4 s | Alguém clicou em "Identificar aparelho" no site |
| Aceso fixo | Gravando uma captura |
| Pisca sem parar, bem rápido, desde o boot | MPU6050 não foi encontrado — confira a fiação |

## 7. O que o site pode mandar para o aparelho

O ESP32 não abre porta nem fica escutando: ele lê a resposta do próprio POST
de telemetria que acabou de fazer. É por aí que chegam os comandos da tela
Dispositivo:

| Botão no site | Comando | O que acontece |
| --- | --- | --- |
| Zerar (tara) | `TARAR` | O ângulo atual vira o novo zero. Fica guardado na memória do aparelho e sobrevive a desligar e ligar. |
| Identificar aparelho | `IDENTIFICAR` | O LED pisca por 4 s — serve para achar qual aparelho é qual, quando a clínica tem mais de um. |
| Iniciar/Parar captura | `INICIAR_CAPTURA` / `PARAR_CAPTURA` | Liga o LED fixo e faz o aparelho amostrar a 10 Hz enquanto grava. |
| Reiniciar aparelho | `REINICIAR` | Reinício remoto — inclusive refaz a calibração do giroscópio. |

Na mesma resposta vem o **intervalo de amostragem**: 10 leituras por segundo
durante uma captura, 2,5 por segundo com alguém olhando a tela, e uma a cada
2 segundos quando ninguém está usando. Isso é bateria: não adianta transmitir
rápido para ninguém.

## 8. Quando algo não bate

- **`[local] login falhou, status=-1`** — problema de rede, não da aplicação.
  Confira se o backend está rodando (`iniciar-rehabit.bat`), se o IP em
  `baseUrl` está certo e se o ESP32 está na mesma rede Wi-Fi do PC.
- **`login recusado (status=401)`** — e-mail ou senha da clínica errados no
  `.ino`. Teste as mesmas credenciais na tela de login do site.
- **A primeira leitura na nuvem demora muito** — normal: o plano gratuito do
  Render "dorme" e leva alguns segundos para acordar.
- **O ângulo aumenta quando deveria diminuir** — troque `EIXO_INVERTIDO` para
  `true` em vez de remontar o sensor.
- **O ângulo escorrega devagar mesmo com o aparelho parado** — o giroscópio
  foi calibrado em movimento. Reinicie a placa com ela parada.
- **O ângulo treme demais** — abaixe `PESO_GIRO` (de `0.98` para `0.95`, por
  exemplo): o filtro passa a confiar mais na gravidade e menos na rotação.
- **O site mostra "Desconectado" mesmo com o aparelho ligado** — o site
  considera offline quem passa 8 segundos sem mandar pacote. Veja no Monitor
  Serial se os envios estão falhando e com qual status.

**Se nada disso resolver, copie exatamente o que apareceu no Monitor Serial —
é a única forma de diagnosticar o problema.**

## 9. O contrato com a API (para quem for mexer no firmware)

Todas as chamadas vão com `Authorization: Bearer <token>`, obtido em
`POST /api/auth/login` com o e-mail e a senha da clínica.

**`POST /api/goniometro/telemetria`** — um pacote por amostra. Só `idClinica`
e `angulo` são obrigatórios; o resto é telemetria de apoio e pode faltar.

```json
{ "idClinica": 1, "angulo": 87.4, "anguloBruto": 87.4, "bateria": 82,
  "rssi": -54, "numeroSerie": "A1B2-C3D4", "firmware": "2.0",
  "ip": "192.168.1.55", "calibrado": true }
```

A resposta é o canal de volta:

```json
{ "comando": "TARAR", "intervaloMs": 400, "emUso": true }
```

`comando` vem como `"NENHUM"` quando não há nada pendente. `intervaloMs` é o
ritmo que o servidor pede a partir de agora. `emUso` diz se tem alguém com a
tela aberta.

O endpoint antigo `POST /api/goniometro/leitura` (só `idClinica` + `angulo`)
continua existindo para não quebrar firmware gravado antes desta versão, mas
ele não recebe comandos de volta.

**Do lado do site**, para referência: `GET /api/goniometro/estado` devolve o
retrato completo, `GET /api/goniometro/stream` é o SSE (token na query, porque
o `EventSource` do navegador não deixa mandar cabeçalho), e
`POST /api/goniometro/comando`, `/captura/iniciar` e `/captura/parar`
enfileiram as ações do profissional.
