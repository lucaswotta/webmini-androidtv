<div align="center">

<img src="docs/brand/webmini-mark.svg" alt="Logo do WebMini" width="112">

# WebMini

Navegador leve, de aba única, para Android TV, que bloqueia anúncios, rastreadores e pop-ups.

[![Versão](https://img.shields.io/github/v/release/lucaswotta/webmini-androidtv?label=vers%C3%A3o)](https://github.com/lucaswotta/webmini-androidtv/releases/latest)
[![Build](https://github.com/lucaswotta/webmini-androidtv/actions/workflows/build.yml/badge.svg)](https://github.com/lucaswotta/webmini-androidtv/actions/workflows/build.yml)
[![Android 6.0+](https://img.shields.io/badge/Android-6.0%2B-3ddc84)](#requisitos)
[![Licença: MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue)](LICENSE)

[Baixar](#instalação) · [Recursos](#recursos) · [Controle remoto](#controle-remoto) · [Privacidade](#privacidade) · [Compilar](#compilar) · [English](README.md)

<img src="docs/images/hero.png" alt="WebMini numa TV mostrando um artigo da Wikipédia com o ponteiro do controle remoto" width="900">

</div>

## Por que o WebMini

Quem abre um navegador na TV quer chegar a um site, ler ou dar play, e voltar para o sofá. A maioria das páginas foi feita para mouse e para o orçamento de anúncios do desktop: pop-ups, redirecionamentos para outros apps, anúncios que tocam sozinhos e rastreadores que o controle remoto não consegue enfrentar.

O WebMini mantém uma página aberta por vez e filtra o resto:

- Filtro de rede com o [`adblock-rust`](https://github.com/brave/adblock-rust), o motor do navegador Brave, carregado com EasyList e EasyPrivacy.
- Nenhuma janela nova. Um pop-up só abre depois da sua confirmação, na mesma aba.
- Páginas não abrem outros apps nem a Play Store (`intent://`, `market://` e qualquer esquema que não seja `http` ou `https`).
- Confirmação antes de toda página HTTP e de todo download.
- APK de 1,8 MB para TVs ARM de 32 bits, sem abas, contas, sincronização, histórico ou telemetria.

Medido num TiVo Stream 4K (Android 12, 2 GB de RAM) em 24/10/2026, carregando a página inicial do `uol.com.br` por 30 s com cache desligado, três rodadas de cada:

| | Requisições | Dados transferidos | Hosts contatados |
| --- | ---: | ---: | ---: |
| Filtro desligado | 230–265 | 2,9–3,2 MB | 64–92 |
| Filtro ligado | 115 | 1,2 MB | 19 |

Partida a frio no mesmo aparelho: 0,7–1,4 s até a tela inicial.

## Capturas de tela

| Início e favoritos | Opções |
| --- | --- |
| ![Tela inicial com pesquisa e quatro favoritos](docs/images/home.png) | ![Menu de opções com avançar, bloqueio de pop-ups, bloqueio de anúncios e sobre](docs/images/menu.png) |
| Confirmação de pop-up | Confirmação de download |
| ![Diálogo pedindo para abrir www.wikipedia.org na aba atual](docs/images/popup.png) | ![Diálogo com nome do arquivo, host e tipo antes do download](docs/images/download.png) |
| Aviso de HTTP | |
| ![Diálogo avisando que neverssl.com usa conexão sem criptografia](docs/images/http.png) | |

A interface segue o idioma do aparelho: inglês, espanhol ou português do Brasil.

## Instalação

O WebMini é distribuído em arquivos APK na [página de releases](https://github.com/lucaswotta/webmini-androidtv/releases/latest).

| Arquivo | Para quem |
| --- | --- |
| `WebMini-universal.apk` | Qualquer aparelho. Escolha este se não tiver certeza. |
| `WebMini-armeabi-v7a.apk` | ARM de 32 bits. A maioria dos sticks e boxes de TV, inclusive chips de 64 bits que rodam sistema de 32 bits. |
| `WebMini-arm64-v8a.apk` | Sistemas ARM de 64 bits. |
| `WebMini-x86_64.apk` | Emulador de Android TV e aparelhos x86. |

Para descobrir a arquitetura do aparelho, rode `adb shell getprop ro.product.cpu.abilist`. Use o primeiro valor.

### Pelo app Downloader

1. Instale o [Downloader by AFTVnews](https://play.google.com/store/apps/details?id=com.esaba.downloader) pela Play Store da TV.
2. Nas configurações da TV, permita que o Downloader instale apps desconhecidos.
3. Abra o Downloader e digite:

   ```text
   https://github.com/lucaswotta/webmini-androidtv/releases/latest/download/WebMini-universal.apk
   ```

4. Confirme a instalação. O WebMini aparece na fileira de apps do launcher.

### Pelo ADB

Ative as opções do desenvolvedor e a depuração pela rede na TV, depois:

```sh
adb connect <ip-da-tv>:5555
adb install WebMini-universal.apk
```

O WebMini se registra como navegador, então links abertos por outros apps podem abrir nele.

## Recursos

### Proteção

| Ameaça | O que o WebMini faz |
| --- | --- |
| Anúncios e rastreadores | Bloqueia as requisições de rede que casam com EasyList e EasyPrivacy, inclusive as feitas por service workers. Uma lista curta de hosts embutida cobre os segundos em que as listas carregam. |
| Pop-ups e janelas novas | Nunca abre uma segunda janela. Pop-up iniciado por clique pede confirmação e mostra o host de destino; pop-up iniciado por script é descartado. |
| Redirecionamento para outros apps | Bloqueia navegação para qualquer esquema que não seja `http` ou `https`. |
| Páginas sem criptografia | Pergunta antes de carregar qualquer página HTTP, venha ela de link, redirecionamento, barra de endereço, favorito ou outro app. Conteúdo misto nunca carrega. |
| Downloads | Mostra nome do arquivo, host e tipo antes de salvar em Downloads. Arquivos nunca abrem sozinhos. |
| Acesso ao aparelho | Nega pedidos de câmera, microfone e localização. Envio de arquivos e acesso a `file://` ou `content://` ficam bloqueados. |
| Sites maliciosos | O Safe Browsing da WebView do Android fica ligado (Android 8.0 ou superior). |
| Página travada | Recupera quando o processo da página cai, sem fechar o app. |

As listas de filtro são baixadas por HTTPS de `easylist.to`, atualizadas a cada 7 dias, limitadas a 5 MB cada, conferidas pelo cabeçalho e substituídas de forma atômica. Os dois bloqueios podem ser desligados no menu de opções.

### Navegação

- Barra de endereço que aceita URL ou pesquisa no Google.
- Voltar, avançar, recarregar e início.
- Favoritos na tela inicial.
- Vídeo em tela cheia.
- Temas claro e escuro do sistema no celular; interface preta na TV.

## Controle remoto

| Entrada | Ação |
| --- | --- |
| Direcional | Move o ponteiro. Segurar a direção acelera. |
| Ponteiro na borda de cima ou de baixo | Rola a página. |
| OK, Enter ou botão A do gamepad | Clica sob o ponteiro. |
| Voltar ou botão B do gamepad | Fecha o menu, sai da tela cheia ou volta uma página. Na tela inicial, sai do app. |
| Esquerda e direita num campo de texto | Movem o cursor do texto. |
| Analógico do gamepad | Move o ponteiro. |

O ponteiro some depois de 2,5 s sem uso e volta na próxima tecla.

## Privacidade

O WebMini não tem analytics, telemetria, conta nem histórico de navegação. A pilha de voltar dura só a sessão atual.

Fica guardado no aparelho, no armazenamento privado do app:

- favoritos e as duas opções de bloqueio;
- as cópias de EasyList e EasyPrivacy;
- cookies, cache e armazenamento dos sites, geridos pela WebView do Android. Cookies de terceiros são recusados.

Os dados do app ficam fora do backup na nuvem, da transferência entre aparelhos e do `adb backup`. Para apagar tudo, abra Configurações > Apps > WebMini > Limpar armazenamento.

Requisições feitas fora das páginas que você visita:

- `easylist.to`, no máximo uma vez a cada 7 dias, com o bloqueio de anúncios ligado;
- Google Safe Browsing, pela WebView do Android, no Android 8.0 ou superior;
- `google.com`, quando você pesquisa pela tela inicial ou pela barra de endereço.

## Limitações

- O filtro age só nas requisições de rede. Anúncios bloqueados podem deixar espaço vazio na página, e anúncios servidos pelo próprio site, inclusive os de vídeo do YouTube, não são removidos.
- Sites podem detectar que requisições foram bloqueadas.
- Não há listas de filtro regionais. Sites em outros idiomas dependem das regras genéricas da EasyList.
- O buscador é fixo no Google.
- O WebMini não torna a navegação anônima.

## Requisitos

- Android 6.0 (API 23) ou superior, em TV, celular ou tablet.
- WebView do Android, que vem instalada no Android TV.

## Aparelhos testados

| Aparelho | Sistema | Arquitetura | WebView | Resultado |
| --- | --- | --- | --- | --- |
| TiVo Stream 4K | Android TV 12 | `armeabi-v7a` | 155 | Funciona |

Usa o WebMini em outro aparelho? [Abra um relato de aparelho](https://github.com/lucaswotta/webmini-androidtv/issues/new?template=device-report.yml) com o modelo e o que funcionou, para esta tabela crescer.

## Compilar

Requisitos: JDK 17 ou superior, Android SDK Platform 36, Android NDK 27.2.12479018, Rust e [`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk).

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --locked
export ANDROID_HOME=/caminho/do/android/sdk
./gradlew :app:assembleDebug
```

O build compila o filtro em Rust para as três arquiteturas e grava um APK por arquitetura, mais um universal, em `app/build/outputs/apk/debug/`. O build debug usa o application ID `io.github.lucaswotta.webmini.debug` e instala ao lado da release.

O build release é assinado quando estas propriedades do Gradle estão definidas, por exemplo em `~/.gradle/gradle.properties`:

```properties
webminiStoreFile=/caminho/release.jks
webminiStorePassword=...
webminiKeyAlias=...
webminiKeyPassword=...
```

```sh
./gradlew :app:assembleRelease
```

Sem elas, `assembleRelease` gera APKs sem assinatura.

### Estrutura

| Caminho | Conteúdo |
| --- | --- |
| `app/src/main/java/io/github/lucaswotta/webmini/MainActivity.java` | O navegador inteiro: interface, ponteiro da TV, políticas da WebView, atualização das listas. |
| `native/adblock/src/lib.rs` | Ponte JNI para o `adblock-rust`. |
| `app/src/main/res/` | Textos em inglês, espanhol e português; ícones e banner da TV. |
| `docs/brand/webmini-mark.svg` | Fonte do logo. |

## Certificado de assinatura

Os APKs de release são assinados com este certificado. O Android recusa atualizações assinadas com outra chave.

```text
SHA-256: AB:E8:D3:13:DC:19:19:CB:B4:6A:E4:9B:DC:CF:2C:BC:35:37:66:AF:2A:B3:FD:25:5B:3B:B2:90:39:14:57:37
```

Confira um arquivo baixado com `apksigner verify --print-certs WebMini-universal.apk`.

## Contribuir

Relatos de bug, relatos de aparelho e pull requests são bem-vindos. Veja o [`SECURITY.md`](SECURITY.md) para relatar uma vulnerabilidade em privado.

## Licença

O WebMini é distribuído sob a [licença MIT](LICENSE). O componente `adblock-rust` embutido usa a licença MPL-2.0, incluída no app em `app/src/main/assets/MPL-2.0.txt`. EasyList e EasyPrivacy são baixadas durante o uso e não fazem parte deste repositório.
