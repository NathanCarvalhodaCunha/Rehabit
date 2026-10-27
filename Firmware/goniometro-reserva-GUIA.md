# Guia: goniômetro reserva (ESP32 + MPU6050 + powerbank)

É o plano B do aparelho. Ele serve quando o goniômetro principal não liga (a
bateria 18650, o TP4056, o MT3608 ou uma solda deu problema) e também quando
a rede não colabora no dia (Wi-Fi da escola bloqueado, servidor dormindo, site
fora do ar).

Ele precisa só de três coisas: uma placa **ESP32 DevKit**, um **MPU6050** com
4 fios e um **powerbank** no USB. Não tem solda nem biblioteca para instalar.

Ele funciona de dois jeitos ao mesmo tempo:

- **Modo local, sempre ligado.** O aparelho cria um Wi-Fi próprio com uma
  página que mostra o ângulo ao vivo, zera, mede a amplitude e desenha o
  movimento. Não precisa de internet, de servidor, de conta nem do site.
- **Site Rehabit, quando houver internet.** Pela mesma página você escolhe o
  Wi-Fi da clínica e digita o código de pareamento. A partir daí a tela
  **Dispositivo** recebe o ângulo exatamente como recebe do principal.

| | Principal | Reserva |
| --- | --- | --- |
| Alimentação | 18650 + TP4056 + MT3608 + chave | powerbank no USB |
| Luz de status | LED externo no GPIO19 | LED que já vem na placa (GPIO2) |
| Bibliotecas | Adafruit MPU6050 e WiFiManager | nenhuma |
| Sensor | só MPU6050 legítimo (WHO_AM_I 0x68) | MPU6050 ou clone (MPU6500, MPU9250...) |
| Sem internet | não mede | mede pela página do aparelho |
| Configurar o Wi-Fi | portal que abre com o botão | página do aparelho, a qualquer hora |
| Bateria no site | não aparece (sem divisor) | não aparece (powerbank) |
| Firmware no site | `2.4` | `2.4-reserva` |

A medida é a mesma do principal, com o mesmo filtro e a mesma conta. O que o
número significa e o que ele não significa está na
[Parte 3 do guia principal](goniometro-esp32-GUIA.md#parte-3--zerar-o-aparelho-antes-de-medir).

---

## 1. Montar (4 fios)

| MPU6050 | ESP32 |
| --- | --- |
| VCC | 3V3 |
| GND | GND |
| SDA | GPIO21 |
| SCL | GPIO22 |

O powerbank vai no USB da placa; o mesmo cabo de gravar serve. Dá para montar
na protoboard ou com jumpers fêmea-fêmea direto entre as duas placas.

Ligue o VCC do sensor no **3V3**, e não no 5V nem no VIN.

## 2. Gravar

1. A Arduino IDE precisa do pacote da placa ESP32. Se ainda não tiver, siga o
   passo 1 da Parte 1 do [guia principal](goniometro-esp32-GUIA.md). Nenhuma
   biblioteca além dele.
2. Abra `Firmware/goniometro-reserva/goniometro-reserva.ino`. A pasta já tem o
   nome que a IDE exige.
3. **Ferramentas → Placa → ESP32 Dev Module**, escolha a porta e clique em
   **Carregar**. Não segure o BOOT enquanto grava.

No **Monitor Serial** (115200) deve aparecer algo assim:

```
Rehabit — goniometro RESERVA, firmware 2.4-reserva
Numero de serie: A1B2-C3D4
Rede propria no ar: "Rehabit-Reserva-C3D4" (senha rehabit123). Pagina: http://192.168.4.1
Sensor encontrado no endereco 0x68 (WHO_AM_I 0x68, MPU6050).
Calibrando o giroscopio: mantenha o aparelho PARADO por 2 s...
Giroscopio calibrado (bias X=... Y=... Z=... rad/s)
Sem tara guardada: usando a posicao de agora como zero provisorio.
Medindo.
Angulo:  12.40 graus (sem filtro  12.10) | so modo local
```

O nome do Wi-Fi termina com os 4 últimos dígitos do número de série. Anote:
é por ele que você acha o aparelho no celular.

## 3. Medir sem internet (modo local)

1. Ligue a placa no powerbank e deixe **parada uns 2 segundos**. A luz fica
   fraca e parada enquanto calibra.
2. No celular, entre no Wi-Fi **Rehabit-Reserva-XXXX**, senha `rehabit123`.
3. A página costuma abrir sozinha. Se não abrir, digite **`http://192.168.4.1`**
   no navegador, com o `http://`.
4. O celular avisa que a rede não tem internet. Toque em **Manter conexão**,
   ou desligue os dados móveis enquanto mede. Se não fizer isso, o celular
   pode largar a rede do aparelho e a página para de atualizar.
5. Prenda o aparelho no braço. Com o braço **pendurado ao lado do tronco**,
   toque em **Zerar (tara)**. A luz dá três piscadas rápidas.
6. Toque em **Medir amplitude**, leve a articulação até o limite e toque em
   **Parar**. A página mostra o mínimo, o máximo e a amplitude, e guarda a
   lista das medidas da vez.

A lista de medidas fica só na página: recarregou, ela some. Anote os números
antes de fechar. A tara não some: fica guardada no aparelho, inclusive depois
de desligar.

## 4. Mandar para o site (quando houver internet)

1. No site, entre como clínica ou profissional, abra a tela **Dispositivo** e
   clique em **Parear novo dispositivo**. Aparece um código de 6 dígitos, que
   vale 10 minutos.
2. Na página do aparelho, abra **Conectar ao site Rehabit**. Escolha o Wi-Fi da
   clínica, digite a senha dele e o código, e toque em **Salvar e conectar**.
3. Acompanhe pela própria página. Ela passa por "Conectando…" e "Pareando com o
   código…" até chegar em "o ângulo está indo para o site".
4. Na tela Dispositivo, o ângulo aparece ao vivo, com o firmware
   `2.4-reserva`. Zerar, Identificar, Iniciar/Parar captura e Reiniciar
   funcionam como no principal.

O modo local continua no ar enquanto isso. Também dá para abrir a página
pela rede da clínica, no endereço que ela mostra (algo como
`http://192.168.1.55`). Esse endereço também aparece no campo IP da tela
Dispositivo.

O Wi-Fi da escola não deixa o aparelho entrar? Isso acontece com redes que
pedem login numa página ou usuário e senha. Use o roteador do celular (o
hotspot, em 2,4 GHz) como rede da clínica.

Para trocar de rede, é só salvar outra pelo mesmo formulário. Para só parear
de novo, deixe a rede como está, a senha em branco e digite o código novo.

## O que a luz está dizendo

| Luz | Significado |
| --- | --- |
| Fraca e parada, logo ao ligar | Calibrando: não mexa |
| "Respirando" (acende e apaga suave) | Modo local: nenhum Wi-Fi da clínica salvo. Normal sem internet |
| Piscada curtinha a cada 2 s | Tudo certo: mandando para o site |
| Pisca devagar, meio a meio | Tem Wi-Fi salvo, mas não entrou nele, ou falta parear |
| Piscada dupla a cada 2 s | Conectado, mas o site não responde |
| Acesa fixa | Medindo amplitude na página, ou gravando captura pelo site |
| Três piscadas rápidas | Confirmação: zerou, ou esqueceu a rede |
| Pisca muito rápido por 4 s | Alguém clicou em "Identificar aparelho" no site |
| Pisca rápido sem parar | Sensor não encontrado: confira os 4 fios |

Com o sensor desconectado, o aparelho procura de novo a cada 2 segundos. Não
precisa reiniciar: encaixou o fio, ele volta a medir sozinho.

## O botão BOOT

Segurado por **5 segundos** com o aparelho ligado, ele esquece o Wi-Fi da
clínica e o pareamento, igual ao botão **Esquecer** da página. A tara não se
perde. O aparelho continua no modo local.

## Problemas comuns

| O que acontece | O que fazer |
| --- | --- |
| O powerbank desliga sozinho depois de um tempo | Ele achou que não tem nada ligado, porque a placa puxa pouca corrente. Muitos powerbanks têm um "modo baixa corrente" (às vezes ligado com dois cliques no botão; veja o manual). Se o seu não tiver, use outro powerbank. |
| A placa reinicia sozinha ao conectar no Wi-Fi, e o Monitor Serial mostra `Brownout detector was triggered` | É o cabo USB: fino ou comprido demais, ele não segura o pico de corrente do Wi-Fi. Troque por um cabo curto e bom, ou use outra saída do powerbank. |
| Luz piscando rápido, página dizendo "Sensor não encontrado" | Confira os 4 fios, principalmente o VCC no **3V3**. O aparelho procura de novo sozinho. |
| `WHO_AM_I 0x70` (ou outro valor) no Monitor Serial | É um clone do MPU6050. Funciona normalmente com este firmware. |
| A página não abre | Digite `http://192.168.4.1` com o `http://`. Confira se o celular continua no Wi-Fi do aparelho e desligue os dados móveis. |
| A página mostra "Sem contato" | O celular saiu do Wi-Fi do aparelho (de novo, a história do "sem internet"), ou o aparelho desligou. Confira o powerbank. |
| "Não achei a rede" | O ESP32 só enxerga Wi-Fi de **2,4 GHz**. Redes só de 5 GHz não aparecem. Use outra rede ou o hotspot do celular. |
| "A senha parece errada" | Confira maiúsculas e minúsculas e salve de novo. |
| "O código foi recusado" | O código vence em 10 minutos e vale uma vez só. Gere outro na tela Dispositivo. |
| "Esperando o site responder" | O servidor gratuito dorme quando ninguém usa e leva até uns 2 minutos para acordar. Espere. |
| "Calibrou mexendo" | Alguém mexeu no aparelho durante os 2 s do começo. Desligue e ligue com ele parado sobre a mesa. |
| O ângulo não bate com a posição do braço | Falta zerar com o braço pendurado. |
| Na tela Dispositivo, "Dispositivo offline" com o aparelho ligado | Sem sensor, o aparelho não manda nada ao site. Antes de tudo, veja na página do aparelho se o sensor está ok. |

## Segurança

A senha do Wi-Fi do aparelho está publicada neste guia e no código. Quem
estiver perto e souber a senha consegue abrir a página, ver o ângulo, zerar e
trocar a rede ou o pareamento. Para um aparelho reserva, usado numa
apresentação ou numa emergência, esse risco é aceitável.

Para uso contínuo na clínica, use o aparelho principal: lá o portal só abre
com o botão, por 3 minutos. Se um aparelho reserva sumir, clique em
**Revogar** na tela Dispositivo.

---

## Para quem for mexer no código

- **Três tarefas.** A `loop()` cuida do sensor a 100 Hz, do filtro, da tara,
  da medida de amplitude e da página (servidor web + DNS do portal). A
  `tarefaDaRede`, no núcleo 0, cuida do Wi-Fi da clínica, do pareamento e da
  telemetria: um envio sem resposta prende só ela, e a página segue viva. A
  `tarefaDaLuz` é a mesma do principal, com dois ritmos a mais.
- **Sensor direto nos registradores**, a 100 kHz. O fio da protoboard costuma
  ser comprido, e o barramento mais lento erra menos. As escalas são as do
  principal (±4 g, ±500 °/s, filtro de ~21 Hz). O aparelho detecta o sensor
  sumindo (50 leituras falhas seguidas ou 100 leituras idênticas) e procura
  de novo.
- **Calibração sem travar a loop()**: uma amostra a cada 5 ms. Ela confere o
  tremor e repete até 3 vezes se o aparelho mexeu.
- **Memória separada** (namespace `reserva`): gravar um firmware por cima do
  outro não mistura token, rede nem tara.
- **Mesmo contrato com a API do principal**: `POST /dispositivos/parear` e
  `POST /goniometro/telemetria`, mesmos campos, mesmos comandos. Duas
  diferenças: não manda `bateria`, e manda `calibrado` sempre `true`, porque o
  site lê `false` como "calibrando, mantenha parado".
- **Rotas da página**: `GET /` (a página, embutida no `.ino`), `GET /dados`,
  `POST /tarar`, `POST /gravar?acao=iniciar|parar`, `GET /redes`,
  `POST /configurar` (rede, senha, codigo) e `POST /esquecer`. Qualquer outro
  endereço redireciona para a página, e é isso que faz o portal abrir sozinho.

Mudou o contrato com a API, o filtro ou as raízes do HTTPS no principal?
Mude aqui também: as raízes do fim do arquivo são cópia exata das de lá.
