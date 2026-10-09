# Guia: gravar e conectar o goniômetro (ESP32 + MPU6050)

O aparelho lê o ângulo da articulação e manda para o Rehabit várias vezes por
segundo. A tela **Dispositivo** mostra esse ângulo ao vivo, e o formulário de
sessão consegue preencher a amplitude sozinho a partir de uma gravação do
movimento.

São duas etapas bem diferentes: gravar o firmware é coisa de quem monta o
aparelho e acontece uma vez só; conectar à clínica é feito pelo celular, por
qualquer pessoa, sem computador e sem mexer em código.

---

# Parte 1 — Gravar o firmware (só uma vez, feito por você)

## 1. Instalar suporte à placa ESP32 na Arduino IDE

1. **Arquivo → Preferências**.
2. Em "URLs adicionais para Gerenciadores de Placas", cole:
   `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
3. **Ferramentas → Placa → Gerenciador de Placas**, busque "esp32" (autor
   Espressif Systems) e instale.
4. **Ferramentas → Placa**, escolha o modelo da sua placa ("ESP32 Dev Module"
   se você não souber o nome exato).

## 2. Instalar as bibliotecas

**Ferramentas → Gerenciar Bibliotecas**, e instale as duas:

- **Adafruit MPU6050** — vai pedir para instalar junto "Adafruit Unified
  Sensor" e "Adafruit BusIO"; aceite, são dependências.
- **WiFiManager** (de *tzapu*) — é ela que cria o portal de configuração no
  celular.

Nada além disso: o firmware lê as respostas JSON da API com duas funções
próprias, de propósito, para não depender de mais bibliotecas.

## 3. Montar o hardware

| MPU6050 / componente | ESP32 |
| --- | --- |
| VCC | 3V3 |
| GND | GND |
| SDA | GPIO21 |
| SCL | GPIO22 |
| LED de status | GPIO19 → resistor 220 Ω → LED azul → GND |
| Botão de reconfiguração | GPIO0 — é o BOOT, já existe na placa |
| Bateria (opcional) | divisor 100 kΩ / 100 kΩ → GPIO34 |

E a alimentação, pela bateria:

| De | Para |
| --- | --- |
| Bateria 18650 + / − | TP4056 B+ / B− |
| TP4056 OUT+ | chave liga/desliga → MT3608 VIN+ |
| TP4056 OUT− | MT3608 VIN− |
| MT3608 VOUT+ (regulado em **5 V**) | ESP32 VIN |
| MT3608 VOUT− | ESP32 GND |

SDA/SCL são os pinos padrão de I2C na maioria das DevKit; confira a serigrafia
da sua se for diferente.

**Acabou de soldar?** Antes de gravar este firmware, siga o
[`teste-hardware-GUIA.md`](teste-hardware-GUIA.md): ele confere as ligações
com o multímetro, regula o MT3608 sem queimar o ESP32 e roda um sketch de
teste que diz qual peça falhou.

**Sobre a bateria:** o firmware vem com `const int PINO_BATERIA = -1;`, porque
o diagrama de montagem não tem como medir a bateria — e, com o pino solto, o
Rehabit mostraria um número inventado em vez de simplesmente não mostrar
bateria. Para medir, solde o divisor: o pino GPIO34 não aguenta os 4,2 V de
uma LiPo, por isso os dois resistores de 100 kΩ, que entregam metade da
tensão ao pino. Depois ponha `const int PINO_BATERIA = 34;`. O
[`teste-hardware-GUIA.md`](teste-hardware-GUIA.md) mostra onde ligar.

**Onde fixar o sensor:** no segmento **móvel** da articulação — no braço, para
medir o ombro; na perna, para o joelho. **Não importa a orientação** em que
você amarra a placa: o zero não vem de um eixo escolhido no código, vem da
pose que você marca com o botão Zerar (veja a Parte 3).

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

Entre como **clínica**, abra a tela **Dispositivo** e clique em conectar um
goniômetro. Aparece um código de 6 dígitos com contagem regressiva: ele vale
10 minutos, serve uma vez só, e pedir outro invalida o anterior.

## 2. Configurar o goniômetro

1. Ligue o aparelho. Sem configuração, ele cria um Wi-Fi chamado
   **Rehabit-Goniometro** (senha `rehabit123`).
2. Conecte o celular nesse Wi-Fi.
3. O portal costuma abrir sozinho. **Se não abrir, digite `http://192.168.4.1`
   no navegador** — com o `http://` na frente, senão o navegador procura no
   Google em vez de abrir a página.
4. Escolha a rede da clínica, digite a senha dela e o código de 6 dígitos.
5. O aparelho grava tudo na memória e já se conecta.

> **"Rede sem internet" é normal.** O goniômetro não é um roteador — essa
> rede existe só para configurá-lo. Se o celular insistir em voltar para os
> dados móveis, desligue os dados enquanto configura ou toque em "Manter
> conexão". Prefira o celular ao notebook: no Windows o portal praticamente
> nunca abre sozinho.

Faltou luz? Ele volta sozinho: rede, senha e token ficam guardados.

## 3. Reconfigurar depois (trocou de Wi-Fi ou de clínica)

**Só trocou a rede ou a senha do Wi-Fi?** Desligue e ligue o aparelho: sem
conseguir entrar na rede salva, ele abre o portal sozinho. Escolha a rede
nova, digite a senha e deixe o código em branco — o pareamento e a tara
continuam guardados.

**Mudou de clínica?** Segure o botão **BOOT** por 5 segundos com o aparelho
ligado. Ele apaga a configuração — inclusive o pareamento e a tara — e volta
ao portal, onde vai o código da clínica nova. (Se o portal já abriu sozinho,
porque a rede antiga não existe ali, o BOOT não faz nada: vá direto a ele.)
Confira na lista **Goniômetros
pareados** da clínica nova que ele aparece como **Online**. Se não aparecer,
o código não pegou — e, se o portal tinha aberto sozinho, o aparelho segue com
o pareamento antigo. Como ele já está no Wi-Fi, agora o BOOT funciona: segure
5 segundos e refaça com um código novo. Depois, refaça o **Zerar (tara)**.

## 4. Se um aparelho sumir ou for roubado

Na tela Dispositivo, clique em **Revogar**. O token daquele aparelho para de
valer na hora, sem mexer na senha de ninguém.

---

# Parte 3 — Zerar o aparelho antes de medir

Este passo é o que faz o número significar alguma coisa. Sem ele, o aparelho
mostra o quanto se afastou da posição em que foi ligado — que não quer dizer
nada clinicamente.

1. Prenda o aparelho no **braço** do paciente (segmento móvel).
2. Peça para ele deixar o braço **pendurado ao lado do tronco**, relaxado.
3. Na tela **Dispositivo**, clique em **Zerar (tara)**.

Pronto. Daí em diante:

| Posição do braço | O aparelho marca |
| --- | --- |
| Pendurado ao lado do tronco | **0°** |
| A meio caminho | ~45° |
| Na horizontal, 90° com o tronco | **90°** |
| Acima da cabeça | ~180° |

O zero fica guardado na memória do aparelho: desligar e ligar não perde. Só
refaça a tara ao trocar de paciente ou se remontar o aparelho no braço.

## O que essa medida é (e o que não é)

A conta é o **ângulo entre a gravidade de agora e a gravidade na hora da
tara**. Duas consequências que valem saber:

- **O número é sempre positivo (0 a 180).** Ele diz o quanto a articulação
  abriu, não para que lado. Uma hiperextensão de 10° aparece como 10°, igual
  a uma flexão de 10°.
- **A referência é a gravidade, não o tronco.** Vale enquanto o paciente
  estiver de pé ou sentado ereto. Se ele se inclinar 15° para a frente, o
  aparelho marca 15° a mais do que o ângulo real da articulação. Vale lembrar
  o paciente de manter o tronco reto durante a medida.

---

# Verificar que está funcionando

Com o cabo USB ligado, abra o **Monitor Serial** (Ferramentas → Monitor
Serial), velocidade **115200**. Você deve ver, em ordem:

```
Rehabit — goniometro digital, firmware 2.2
MPU6050 encontrado.
Sem tara guardada: usando a posicao do boot como zero provisorio.
Calibrando o giroscopio — mantenha o aparelho PARADO...
Giroscopio calibrado (bias X=...)
Numero de serie: A1B2-C3D4
Sem token: use o portal para parear.      (ou: Token encontrado na memoria.)
Wi-Fi conectado, IP: 192.168.1.55
Pareado com a clinica "..."               (só na primeira vez)
Angulo:  87.40 graus (sem filtro  87.40) | bateria sem medidor | RSSI -54 dBm
```

**Importante:** deixe o aparelho parado durante a calibração do giroscópio
(uns 2 segundos no boot). Se ele se mexer nessa hora, o ângulo fica alguns
graus torto — nesse caso, basta reiniciar a placa.

Depois abra a tela **Dispositivo** logado como a clínica: o selo deve virar
"Conectado" e o ângulo deve acompanhar o movimento do sensor.

## O que o LED está dizendo

| LED | Significado |
| --- | --- |
| Pisca curtinho a cada 2 s | Tudo certo: Wi-Fi ligado e aparelho pareado |
| Pisca devagar, meio a meio | Sem Wi-Fi ou sem pareamento |
| Pisca muito rápido por 4 s | Alguém clicou em "Identificar aparelho" no site |
| Aceso fixo | Gravando uma captura |
| Pisca sem parar, bem rápido, desde o boot | MPU6050 não foi encontrado — confira a fiação |
| Apagado nos primeiros segundos após ligar | Normal: calibrando o giroscópio (deixe parado) e conectando ao Wi-Fi |
| Apagado o tempo todo | Portal de configuração aberto — o Wi-Fi "Rehabit-Goniometro" aparece no celular, em até 1 minuto (aparelho sem configuração, depois do BOOT 5 s, rede ou senha do Wi-Fi trocada, aparelho vindo de outra clínica, ou a rede salva não estava no ar quando ele ligou). Ou sem energia: bateria descarregada ou chave desligada |
| Acende e apaga bem devagar, uns 4 s ou mais de cada | Wi-Fi e pareamento ok, mas sem resposta do servidor (internet fora ou servidor fora do ar): cada envio espera 4 s ou mais (conectar e ler têm 4 s cada, e o DNS pode segurar uns 15 s) e a luz só troca entre um envio e outro |

A luz só sabe do Wi-Fi e do pareamento: ela pisca curtinho mesmo que o
servidor recuse o aparelho (revogado, token recusado). Se ela diz que está
tudo certo e o site mostra "Desconectado", veja
no Monitor Serial com qual status os envios falham (tabela de Problemas
comuns, abaixo). A mesma tabela, em linguagem para a equipe da clínica, está
no site: tela Dispositivo → Ver tutorial → "O que a luz do aparelho está
dizendo".

## O que o site pode mandar para o aparelho

O ESP32 não abre porta nem fica escutando: ele lê a resposta do próprio POST
de telemetria que acabou de fazer. É por aí que chegam os comandos da tela
Dispositivo:

| Botão no site | Comando | O que acontece |
| --- | --- | --- |
| Zerar (tara) | `TARAR` | O ângulo atual vira o novo zero. Fica guardado na memória do aparelho e sobrevive a desligar e ligar. |
| Identificar aparelho | `IDENTIFICAR` | O LED pisca por 4 s — serve para confirmar que o aparelho está recebendo os comandos do site. (A fila de comandos é da clínica: com mais de um aparelho ligado, quem recebe é o primeiro que enviar telemetria.) |
| Iniciar/Parar captura | `INICIAR_CAPTURA` / `PARAR_CAPTURA` | Liga o LED fixo e faz o aparelho amostrar a 10 Hz enquanto grava. |
| Reiniciar aparelho | `REINICIAR` | Reinício remoto — inclusive refaz a calibração do giroscópio. |

Na mesma resposta vem o **intervalo de amostragem**: 10 leituras por segundo
durante uma captura, 2,5 por segundo com alguém olhando a tela, e uma a cada
2 segundos quando ninguém está usando. Isso é bateria: não adianta transmitir
rápido para ninguém.

## Problemas comuns

| O que aparece | O que fazer |
|---|---|
| `MPU6050 nao encontrado!` | Fiação: confira VCC/GND/SDA/SCL. |
| `Pareamento falhou (status=400)` | Código expirado ou já usado — gere outro na tela Dispositivo. |
| `Este aparelho foi revogado pela clinica` | Alguém clicou em Revogar. Pareie de novo (BOOT 5s). |
| `Token recusado` (401) | Configuração antiga. Segure BOOT por 5s e refaça. |
| `Envio falhou, status=-1` | Problema de rede/TLS, não da aplicação. Confira o sinal do Wi-Fi e se o aparelho tem internet de verdade. |
| Demora na primeira leitura | Normal: o servidor gratuito hiberna e leva alguns segundos para acordar. |
| O ângulo não bate com a posição do braço | Falta zerar. Deixe o braço pendurado e clique em **Zerar (tara)**. |
| O braço na horizontal não marca 90° | A tara foi feita com o braço fora da posição pendurada. Refaça com o braço solto ao lado do tronco. |
| O ângulo fica alguns graus fora mesmo depois de zerar | O giroscópio foi calibrado em movimento. Reinicie a placa parada. |
| O ângulo treme demais | Abaixe `PESO_GIRO` (de `0.98` para `0.95`): o filtro passa a confiar mais na gravidade e menos na rotação. |
| O site mostra "Desconectado" com o aparelho ligado | O site considera offline quem passa 8 segundos sem mandar pacote. Veja no Monitor Serial se os envios estão falhando e com qual status. |

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
nativos — é por isso que `anguloPelaGravidade()` recebe dois `float` em vez de
um `sensors_event_t`. Se você editar o código e voltar a passar um tipo
próprio como parâmetro, o erro reaparece.

---

# O contrato com a API (para quem for mexer no firmware)

O aparelho troca o código de 6 dígitos por um token próprio em
`POST /api/dispositivos/parear` (único endpoint público, já que o aparelho
ainda não tem credencial nenhuma). Daí em diante tudo vai com
`Authorization: Bearer <token do aparelho>`.

**`POST /api/goniometro/telemetria`** — um pacote por amostra. Só o `angulo` é
obrigatório; o resto é telemetria de apoio e pode faltar. **Não** mande
`idClinica`: ela sai do token do aparelho, e é isso que impede um goniômetro
de escrever na clínica de outro.

```json
{ "angulo": 87.4, "anguloBruto": 87.4, "bateria": 82, "rssi": -54,
  "numeroSerie": "A1B2-C3D4", "firmware": "2.1",
  "ip": "192.168.1.55", "calibrado": true }
```

A resposta é o canal de volta:

```json
{ "comando": "TARAR", "intervaloMs": 400, "emUso": true }
```

`comando` vem como `"NENHUM"` quando não há nada pendente. `intervaloMs` é o
ritmo que o servidor pede a partir de agora. `emUso` diz se tem alguém com a
tela aberta.

O endpoint antigo `POST /api/goniometro/leitura` (só o ângulo) continua
existindo para não quebrar firmware gravado antes desta versão, mas ele não
recebe comandos de volta.

**Do lado do site**, para referência: `GET /api/goniometro/estado` devolve o
retrato completo, `GET /api/goniometro/stream` é o SSE (token na query, porque
o `EventSource` do navegador não deixa mandar cabeçalho), e
`POST /api/goniometro/comando`, `/captura/iniciar` e `/captura/parar`
enfileiram as ações do profissional.
