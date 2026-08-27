# Guia: gravar o firmware do goniômetro no ESP32

## 1. Instalar suporte à placa ESP32 na Arduino IDE

1. Abra a Arduino IDE → **Arquivo → Preferências**.
2. Em "URLs adicionais para Gerenciadores de Placas", cole:
   `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
3. **Ferramentas → Placa → Gerenciador de Placas**, busque "esp32" (autor
   Espressif Systems), clique em Instalar.
4. **Ferramentas → Placa**, escolha o modelo da sua placa (geralmente
   "ESP32 Dev Module" se não souber o nome exato).

## 2. Instalar a biblioteca do MPU6050

1. **Ferramentas → Gerenciar Bibliotecas** (ou Sketch → Incluir Biblioteca
   → Gerenciar Bibliotecas).
2. Busque "Adafruit MPU6050", instale (vai pedir pra instalar também
   "Adafruit Unified Sensor" e "Adafruit BusIO" — aceite, são dependências).

## 3. Selecionar a porta

1. Conecte o ESP32 no PC via USB (se ainda não estiver).
2. **Ferramentas → Porta**, escolha a porta COM que apareceu quando você
   conectou o cabo (no Gerenciador de Dispositivos do Windows, procure por
   "Portas (COM e LPT)" — costuma aparecer como "Silicon Labs CP210x" ou
   "CH340").

## 4. Preencher e gravar o firmware

> **A Arduino IDE exige que o `.ino` esteja dentro de uma pasta com o mesmo
> nome.** Ou seja: `goniometro-esp32/goniometro-esp32.ino`. Ao abrir o
> arquivo, a IDE se oferece para criar essa pasta — aceite.
>
> Essa pasta de trabalho **não vai para o GitHub** (está no `.gitignore`),
> justamente porque você preenche nela a senha do seu Wi-Fi e a senha da
> conta da clínica. O arquivo versionado é o `Firmware/goniometro-esp32.ino`,
> que fica só com os campos em branco. Se precisar começar do zero, copie
> esse modelo para dentro da pasta de trabalho.

1. Abra `goniometro-esp32.ino` na Arduino IDE.
2. Preencha `WIFI_SSID`/`WIFI_SENHA` e o e-mail/senha da clínica nos dois
   `ALVOS` (local e nuvem), no topo do arquivo.
3. Confira se `ALVOS[0].baseUrl` aponta pro IP certo do PC (hoje é
   `192.168.1.10:8080` — se seu PC estiver em outro IP na sua rede
   Wi-Fi, ajuste).
4. Clique em **Carregar** (a seta →) na Arduino IDE.

## 5. Verificar que está funcionando

1. Abra o **Monitor Serial** (ícone de lupa no canto superior direito, ou
   Ferramentas → Monitor Serial), velocidade **115200**.
2. Você deve ver, em ordem: `MPU6050 encontrado`, depois
   `Wi-Fi conectado, IP: ...`, depois `[local] login OK, idClinica=...` e
   `[nuvem] login OK, idClinica=...`.
3. A cada ~2 segundos deve aparecer `Angulo lido: X.XX graus` seguido de
   `[local] leitura enviada: ...` e `[nuvem] leitura enviada: ...`.
4. Se `[local]` falhar com "login falhou" — confira se o backend local
   está rodando (`iniciar-rehabit.bat`) e se o ESP e o PC estão na mesma
   rede Wi-Fi.
5. Se `[nuvem]` demorar bastante na primeira leitura — normal, o Render
   gratuito "dorme" e leva alguns segundos pra acordar na primeira
   chamada depois de um tempo parado.
6. Se aparecer `status=-1` (em vez de um código HTTP normal como 200, 401
   etc.) em qualquer um dos dois alvos, geralmente é problema de conexão
   Wi-Fi/TLS, não um erro da aplicação — confira o sinal do Wi-Fi e, no
   caso do `[nuvem]`, se o ESP tem acesso à internet de verdade (não só à
   rede local).
7. Se o ângulo aparecer sempre negativo e isso não fizer sentido para a
   articulação medida: `atan2` pode retornar valores negativos dependendo
   de como o MPU6050 está montado, e o campo de amplitude não tem mínimo —
   experimente montar o sensor do outro lado ou tirar o valor absoluto em
   `lerAngulo()`.
8. Com tudo funcionando, abra a tela Dispositivo do Rehabit (local ou a
   versão publicada) logado como a clínica — o ângulo deve aparecer e
   atualizar sozinho a cada ~2s.

**Se algo não bater com o esperado acima, copie exatamente o que apareceu
no Monitor Serial — essa é a única forma de diagnosticar o problema.**

## Erros de compilação conhecidos

**`variable or field 'fazerLogin' declared void`**

A Arduino IDE cria sozinha os protótipos das funções e os coloca no topo do
arquivo — antes do `struct Alvo`. Se uma função recebesse `Alvo &` como
parâmetro, o protótipo gerado citaria um tipo que ainda não existe naquele
ponto, e a compilação parava aí.

Por isso `fazerLogin()` e `enviarLeitura()` recebem o **índice** do alvo
(`int`) em vez do struct, e pegam `ALVOS[indice]` na primeira linha. Não
troque essas assinaturas de volta para `Alvo &` — o erro volta.
