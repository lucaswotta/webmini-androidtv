# WebMini

Navegador Android de uma aba baseado no Android System WebView.

## Requisitos

- Android 6.0 ou superior com Android System WebView instalado, em celular ou TV.
- JDK 17 ou superior, Android SDK 36, NDK 27.2.12479018, Rust e `cargo-ndk`.
- O projeto usa Gradle Wrapper 9.1.0 e compila o filtro Rust para ARMv7, ARM64 e x86_64.

## Instalação

Configure `ANDROID_HOME` para o Android SDK e execute `./gradlew :app:assembleDebug`.
Instale o alvo Rust correspondente (`rustup target add aarch64-linux-android
armv7-linux-androideabi x86_64-linux-android`) e `cargo-ndk` (`cargo install
cargo-ndk --locked`) antes de compilar.
O APK instalável é gerado em `app/build/outputs/apk/debug/app-debug.apk`.
Para distribuição, execute `./gradlew :app:assembleRelease` e assine o APK de release.

## Uso

A tela inicial tem pesquisa Google e favoritos salvos localmente. A barra do
navegador contém, nesta ordem, início, voltar, endereço, recarregar, favorito
e opções. Pesquisas na barra de endereço e na tela inicial abrem resultados do
Google. Tocar na estrela salva ou remove a página atual dos favoritos.

O menu `⋮` oferece avançar, bloqueio de pop-ups, bloqueio de anúncios e Sobre.
Pop-ups com destino identificável pedem confirmação por padrão; desativar o
bloqueio permite abrir links com gesto na aba atual. Pop-ups sem destino e
pop-ups automáticos são bloqueados. O item Sobre abre
`https://github.com/lucaswotta` na mesma WebView. Links HTTP e HTTPS de outros
aplicativos podem ser abertos no navegador.

Downloads exibem confirmação com nome, origem e tipo antes de iniciar. O
arquivo vai para Downloads e não é aberto automaticamente. Sites não recebem
permissões de localização, câmera, microfone ou seletor de arquivos. O Safe
Browsing do Android WebView é ativado em versões compatíveis; navegações HTTP e
conteúdo misto HTTP/HTTPS são sinalizados ou bloqueados.

Na TV, o ponteiro fica ativo sobre a página e a interface; direcional ou
analógico esquerdo movem, OK ou botão A clicam. Ele some após 2,5 segundos sem
entrada. Mantendo o cursor nas bordas verticais, a rolagem acelera. No celular,
a navegação usa toque direto.

## Dados e privacidade

O Android System WebView gerencia os cookies dos sites e os mantém entre
sessões. O preenchimento de logins depende do suporte do site e do serviço de
preenchimento automático configurado no Android. O WebMini não mantém cofre de
senhas nem histórico de visitas. A pilha de voltar existe apenas na sessão
atual e é apagada ao encerrar a WebView. Favoritos e preferências de bloqueio
ficam nas preferências privadas do aplicativo.

## Desempenho

Há uma WebView por vez; a tela inicial nativa não cria uma WebView. A interface
usa Views Android, sem biblioteca de interface, fontes próprias ou imagens de
fundo. O build de release ativa R8 e reduz recursos. Os timers JavaScript param
em segundo plano.

O consumo de RAM depende do WebView, da página e do dispositivo. Inclua o
processo renderizador da WebView na medição. Em 23/10/2026, no TiVo Stream 4K
com Android 12 e WebView 155.0.8059.43, o build anterior com Google após uma
abertura limpa mediu 74.548 KiB PSS no app e 84.296 KiB no renderizador:
158.844 KiB no total (155,1 MiB), sem vídeo.

Em 23/10/2026, com EasyList e EasyPrivacy carregados e ainda na tela inicial,
o TiVo mediu 51.553 KiB PSS no processo do app (50,3 MiB). Esse valor não
inclui uma WebView nem o processo renderizador.

## Bloqueio de anúncios e rastreadores

O bloqueio de anúncios e rastreadores vem ativado por padrão. O WebMini usa
`adblock-rust` com EasyList e EasyPrivacy.
O motor `adblock-rust` 0.13.3 está sob MPL-2.0; a licença está em
`app/src/main/assets/MPL-2.0.txt` e o código fonte pode ser obtido em
`https://github.com/brave/adblock-rust`.
As listas são baixadas por HTTPS, guardadas no armazenamento privado do app e
atualizadas ao iniciar ou ativar o bloqueio quando têm mais de sete dias. Sem conexão ou antes
do primeiro download, ficam ativos apenas os domínios de contingência.

O filtro atua nas requisições de rede vistas pelo Android WebView e por Service
Workers a partir do Android 7; não injeta scripts nem aplica filtros
cosméticos. O callback do WebView não cobre todos os redirecionamentos, e
anúncios servidos pela mesma origem do conteúdo podem continuar. O bloqueio de
rede pode ser detectado pelo site; não existe garantia de que seja invisível.

O motor e as listas consomem memória quando ativados. O tamanho do APK universal
de depuração é maior porque contém bibliotecas para três arquiteturas. O consumo
real depende do WebView, da lista carregada, da página e do dispositivo; a meta
de 15 MiB para uma página complexa não pode ser garantida.
