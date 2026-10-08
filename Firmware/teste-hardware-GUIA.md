# Guia: testar a placa depois de soldar

Este guia segue o diagrama de montagem (bateria 18650 → TP4056 → chave →
MT3608 → ESP32 → MPU6050 e LED). A ordem importa: primeiro tudo desligado,
depois a energia sem o ESP32, depois o ESP32 pelo USB e só no fim pela
bateria. Cada fase só começa se a anterior passou. Assim, um erro de solda
aparece no multímetro e não queima nenhuma peça.

**Material:** multímetro com continuidade (o "bip"), um cabo micro-USB **de
dados** (alguns cabos só carregam), o PC com a Arduino IDE e o suporte de
placa ESP32 já instalado (Parte 1 do
[`goniometro-esp32-GUIA.md`](goniometro-esp32-GUIA.md)).

---

## Antes de tudo: quatro regras

1. **Nunca ligue o USB do ESP32 com a chave LIGADA.** Seriam duas fontes
   empurrando o mesmo fio de 5 V, e a Espressif avisa que isso pode danificar
   a placa ou a fonte. Para gravar e usar o Monitor Serial: **chave
   desligada**.
2. **Não ligue a chave com o ESP32 conectado antes de regular o MT3608**
   (Fase 3). Ele sai de fábrica numa tensão qualquer, que pode passar de
   12 V.
3. **Confira a polaridade da bateria antes de encaixar.** Invertida, ela
   queima o TP4056 na hora.
4. **Carregue pelo USB do TP4056 com a chave desligada.** Com o aparelho
   ligado puxando corrente, o TP4056 pode não perceber que a carga terminou.

---

## Fase 1: inspeção e multímetro (sem bateria, sem USB)

Olhe as soldas com boa luz, de preferência com lupa ou com a câmera do
celular:

- **pontes de solda** entre pinos vizinhos do ESP32, principalmente D21, D19
  e D22, que ficam lado a lado com outros pinos;
- **soldas frias**: foscas, em forma de bolinha, ou com o pino "boiando" no
  meio. Uma solda boa é brilhante e tem forma de cone;
- fios com cobre exposto encostando em outro fio ou em outro pino.

Coloque o multímetro em **continuidade**.

### Tem que apitar

| Ponta 1 | Ponta 2 |
| --- | --- |
| TP4056 **B+** | **+** do suporte da bateria |
| TP4056 **B−** | **−** do suporte da bateria |
| TP4056 **OUT+** | pino lateral da chave (o que vai para o TP4056) |
| pino **do meio** da chave | MT3608 **VIN+** |
| TP4056 **OUT−** | MT3608 **VIN−** |
| MT3608 **VOUT+** | ESP32 **VIN** |
| MT3608 **VOUT−** | ESP32 **GND** (o pino ao lado do VIN) |
| ESP32 **3V3** | MPU6050 **VCC** |
| ESP32 **GND** (o pino ao lado do 3V3) | MPU6050 **GND** |
| ESP32 **D21** | MPU6050 **SDA** |
| ESP32 **D22** | MPU6050 **SCL** |
| ESP32 **D19** | uma perna do resistor de 220 Ω |
| perna **curta** do LED (lado chanfrado) | GND |

**A chave:** com ela **ligada**, TP4056 OUT+ ↔ MT3608 VIN+ apita; **desligada**,
não apita. Se apitar nas duas posições ou em nenhuma, o fio está no pino
lateral errado da chave.

### NÃO pode apitar (curto-circuito)

| Ponta 1 | Ponta 2 |
| --- | --- |
| ESP32 VIN | GND |
| ESP32 3V3 | GND |
| MT3608 VIN+ | MT3608 VIN− |
| TP4056 B+ | TP4056 B− |
| TP4056 OUT+ | TP4056 OUT− |
| MPU6050 SDA | MPU6050 SCL |
| MPU6050 SDA ou SCL | GND |
| ESP32 D21 | D19 (vizinhos) |
| ESP32 D22 | D23 e TX0 (vizinhos) |

> Um **bip curtinho que para sozinho** é capacitor carregando, e é normal
> entre VIN e GND ou entre 3V3 e GND. Curto de verdade é **bip contínuo**.

---

## Fase 2: bateria e TP4056 (chave DESLIGADA)

1. **Meça a bateria fora do suporte** (multímetro em tensão DC, escala 20 V):
   - entre 3,0 V e 4,2 V: ok;
   - entre 2,5 V e 3,0 V: serve, mas carregue antes de testar;
   - abaixo de 2,5 V: **não use essa célula**. Descarregada demais, ela pode
     ter estragado por dentro e esquentar ao carregar.
2. **Encaixe conferindo a polaridade.** O **+** da célula é o lado com o
   polo saliente e precisa ficar do lado do fio que vai ao **B+**.
3. Meça **B+ / B−**: deve dar a tensão da bateria.
4. Meça **OUT+ / OUT−**: deve dar praticamente a mesma tensão. Se der 0 V, a
   proteção do módulo travou. Ligue um carregador USB no TP4056 por um
   segundo e ela destrava.
5. **Carga:** ligue um carregador no USB do TP4056. O LED **vermelho** acende
   enquanto carrega, e o **azul ou verde** acende quando termina (4,2 V). O
   módulo esquenta um pouco, porque carrega a até 1 A. Se ficar quente demais
   para segurar com o dedo, desligue.

---

## Fase 3: regular o MT3608 para 5 V (a etapa que mais queima ESP32)

O MT3608 é um elevador de tensão ajustável até 28 V, e ninguém sabe em
quanto ele veio. O regulador do pino VIN do ESP32 aguenta no máximo uns 15 V.
Acima disso ele queima e pode levar o ESP32 junto. Por isso a regulagem é
feita com o ESP32 **desconectado**.

1. **Desconecte o ESP32 do VOUT+.** Se o ESP32 está em barra de pinos fêmea,
   tire a placa. Se está soldado, dessolde **só uma ponta** do fio
   VOUT+ → VIN. É uma solda só e protege a placa.
2. Ligue a **chave**. Multímetro em tensão DC entre **VOUT+** e **VOUT−**.
3. Gire o **parafusinho do cubo azul** (trimpot) devagar até marcar
   **5,0 a 5,2 V**.
   - É **multivoltas**: pode precisar de 10 a 20 voltas antes de a tensão
     começar a mudar. Isso é normal.
   - Se a tensão sobe quando você quer que desça, gire para o outro lado.
   - O MT3608 só **eleva**. Se a leitura fica parada na tensão da bateria
     (~3,7 V) e não sobe, o trimpot está num extremo. Gire bastante para o
     outro lado.
4. **Desligue a chave** e reconecte o ESP32 (ressolde o fio).
5. Confira com tudo ligado: chave **ligada**, sem USB.
   - VIN ↔ GND do ESP32: **~5 V**;
   - 3V3 ↔ GND do ESP32: **3,2 a 3,4 V**;
   - o LED vermelho do ESP32 e o LED do módulo MPU6050 acendem.

   Desligue a chave de novo antes de ir para a Fase 4.

---

## Fase 4: rodar o teste pelo USB (chave DESLIGADA)

O sketch [`teste-hardware/teste-hardware.ino`](teste-hardware/teste-hardware.ino)
não precisa de nenhuma biblioteca além das que já vêm com a placa ESP32, nem
de Wi-Fi da clínica, nem do site. Ele testa cada peça e diz qual falhou.

1. **Chave desligada.** Cabo USB do PC no **ESP32** (não no TP4056).
2. Na Arduino IDE, abra `Firmware/teste-hardware/teste-hardware.ino`.
3. **Ferramentas → Placa → ESP32 Dev Module**, **Ferramentas → Porta** (a
   que apareceu ao ligar o cabo) e clique em **Carregar**.
   - Se ficar travado em `Connecting.....____`, segure o botão **BOOT** da
     placa até a gravação começar.
4. Abra o **Monitor Serial** em **115200** e aperte o botão **EN** da placa
   para ver o relatório desde o começo.
5. Deixe a placa **parada sobre a mesa** durante os testes, que levam uns
   10 segundos.

### Como deve sair, numa placa boa

```
==============================================
 Rehabit — teste de hardware do goniometro
==============================================
Chip ESP32-D0WD-V3 rev 3, 2 nucleos, flash 4 MB

--- 1. LED externo (GPIO19) ---
  Piscando 3 vezes agora — olhe para o LED azul externo.
  ...

--- 2. Barramento I2C (SDA=21, SCL=22) ---
  dispositivo em 0x68 (e o endereco do MPU6050)
  [OK]    MPU6050 respondeu no endereco 0x68

--- 3. MPU6050 ---
  [OK]    WHO_AM_I = 0x68: MPU6050 legitimo (ou clone fiel).
  [OK]    Chip acordado e configurado.
  Deixe a placa PARADA sobre a mesa pelos proximos segundos...
  [OK]    Leituras a 100 kHz: 0 erros em 200.
  [OK]    Leituras a 400 kHz: 0 erros em 200.
  [OK]    Acelerometro: gravidade medida = 1.00 g (esperado ~1.00).
  [OK]    Giroscopio parado: X=-1.2 Y=0.8 Z=0.3 graus/s
  [OK]    Temperatura do chip: 27.4 C

--- 4. Wi-Fi (radio e pico de consumo) ---
  ...
  [OK]    5 rede(s) encontrada(s).

--- 5. Alimentacao ---
  Motivo deste boot: ligou a alimentacao ou apertou EN
  [OK]    Nenhuma queda de tensao registrada.

==============================================
 RESULTADO: tudo certo. Pode gravar o goniometro-esp32.ino.
==============================================
```

Depois do relatório ele entra em **modo movimento** e imprime 4 linhas por
segundo:

```
acel(g) +0.01 -0.02 +1.00 |1.00| giro(graus/s)   -1.2   +0.8   +0.3 | 27.4 C | incl   0.4 | erros I2C 0
```

Aproveite para fazer mais três testes:

- **Eixos:** deitada na mesa, um dos eixos de `acel` marca ~±1 e os outros
  ~0. Vire a placa de lado e outro eixo passa a marcar ~±1. Um eixo que
  nunca sai do zero está com defeito.
- **Inclinação:** incline a placa. `incl` acompanha o movimento e, passando
  de 30°, o LED externo acende. O botão **BOOT** faz da posição atual o novo
  zero.
- **Teste do chacoalho:** com a placa ligada, aperte e mexa de leve cada fio
  e cada solda do MPU6050 (VCC, GND, SDA, SCL), olhando o `erros I2C`. Ele
  tem que ficar parado. **Se o número sobe quando você encosta num fio, a
  solda ruim é ali.** Esse teste acha a solda fria que funciona "às vezes".

Para repetir os testes, envie `r` no Monitor Serial ou aperte **EN**.

### O que cada falha quer dizer

| O que apareceu | Causa provável | O que fazer |
| --- | --- | --- |
| LED externo não piscou | LED invertido, solda solta no resistor, ou fio fora do D19 | A perna **longa** do LED vai para o lado do resistor/D19, e a curta para o GND. Confira a continuidade da Fase 1. |
| LED piscou, mas fraquinho | Normal | LED azul com 220 Ω em 3,3 V passa pouca corrente. Se quiser mais brilho, troque por 100 Ω. |
| `Nenhum dispositivo I2C encontrado` | MPU sem energia, ou SDA/SCL abertos | O LED do módulo MPU está aceso? Se não, falta 3V3 ou GND. Se está, confira D21↔SDA e D22↔SCL. |
| `SDA e SCL estao INVERTIDOS` | Fios trocados | Ressolde: SDA → D21, SCL → D22. |
| `WHO_AM_I = 0x70` (ou 0x72, 0x98...) | Módulo com chip clone | O sensor funciona, mas a biblioteca do firmware principal recusa esse chip. Mande o print do Monitor Serial: dá para adaptar o firmware. |
| Erros a **100 kHz** | Mau contato | Solda fria em SDA, SCL, VCC ou GND. Faça o teste do chacoalho para achar qual. |
| Erros só a **400 kHz** (aviso) | Fios de SDA/SCL longos | Encurte os fios ou, no `goniometro-esp32.ino`, troque `Wire.setClock(400000)` por `Wire.setClock(100000)`. |
| `gravidade medida` fora de 0.90–1.10 | A placa mexeu, ou o sensor está danificado | Repita parado (`r`). Se continuar, o módulo pode ter se danificado com calor na solda. |
| Giroscópio acima de 10 graus/s parado | A placa mexeu, ou o giroscópio está com defeito | Repita parado. Poucos graus/s é normal, porque o firmware principal calibra isso no boot. |
| `Nenhuma rede encontrada` | Rádio ou antena | Só é problema se há Wi-Fi por perto. Não deixe metal nem fios sobre a antena (a ponta da placa com o desenho em zigue-zague). |
| `queda de tensao` | Alimentação fraca | Veja a Fase 5. |

---

## Fase 5: testar na bateria (sem USB)

Agora o teste é sem computador, e o LED é a única forma de ver o que está
acontecendo.

1. **Tire o cabo USB do ESP32.**
2. **Ligue a chave.**
3. O que deve acontecer:
   - o LED vermelho do ESP32 acende;
   - o LED externo pisca **3 vezes** (é o teste começando);
   - uns 10 segundos de testes, com a busca de Wi-Fi, que é o maior pico de
     consumo;
   - o LED azul da própria placa dá **uma piscada por segundo** (o ESP32 está
     rodando);
   - inclinando a placa mais de 30°, o **LED externo acende**.
4. Deixe ligado uns 10 minutos e toque nas peças. O MT3608 e o reguladorzinho
   do ESP32 (perto do USB) ficam **mornos**, e isso é normal. **Quente demais
   para encostar o dedo não é normal**: desligue e confira as tensões da
   Fase 3.

### Sinais de problema

| O LED faz isso | Quer dizer |
| --- | --- |
| As 3 piscadas do começo **se repetem sozinhas** a cada poucos segundos | A placa está **reiniciando** por falta de energia, quase sempre na hora do Wi-Fi. |
| LED externo pisca rápido sem parar | O MPU6050 não respondeu. Volte à Fase 4. |
| Nada acende | Não chega energia. Meça a bateria, OUT+/OUT− do TP4056 e VOUT do MT3608 com a chave ligada. |

Se a placa ficou reiniciando:

- meça a bateria. Abaixo de ~3,5 V, carregue e teste de novo;
- meça VIN ↔ GND **com a placa rodando**: tem que ficar perto de 5 V. Se
  cai muito, regule o MT3608 para 5,2 V;
- reveja as soldas do caminho da energia: chave, VIN e GND do ESP32. Fio
  muito fino ou solda ruim derruba a tensão no pico de corrente.

**Para ver quantas vezes ela reiniciou:** desligue a chave, ligue o USB e
abra o Monitor Serial. A seção `5. Alimentacao` mostra quantas quedas de
tensão aconteceram enquanto ela estava na bateria. A contagem fica guardada
na memória. Uma só pode acontecer ao ligar a chave. Várias é problema. Envie
`c` para zerar a contagem.

---

## Fase 6: gravar o firmware de verdade

Passou em tudo? Grave o [`goniometro-esp32.ino`](goniometro-esp32.ino)
seguindo o [`goniometro-esp32-GUIA.md`](goniometro-esp32-GUIA.md), sempre
com a **chave desligada** enquanto o USB estiver ligado. Ele já está
configurado para este diagrama:

- **LED de status no GPIO19** (o LED externo);
- **sem leitura de bateria** (`PINO_BATERIA = -1`), porque o diagrama não tem
  o divisor de tensão. Com o pino solto, o site mostraria um número
  inventado.

### Opcional: mostrar a bateria no site

São dois resistores de **100 kΩ**:

```
pino do meio da chave (= MT3608 VIN+) ── 100 kΩ ──┬── GPIO34 (D34)
                                                  └── 100 kΩ ── GND
```

Pegando a tensão depois da chave, o divisor só gasta bateria com o aparelho
ligado. Depois, no `goniometro-esp32.ino`, troque `PINO_BATERIA = -1` por
`PINO_BATERIA = 34`.
