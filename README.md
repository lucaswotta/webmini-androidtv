# WebMini

Navegador Android de uma aba baseado no Android System WebView.

## Requisitos

- Android 6.0 ou superior com Android System WebView instalado, em celular ou TV.
- JDK 17 e Android SDK 36 para compilar. O projeto inclui Gradle Wrapper 8.11.1.

## Instalação

Configure `ANDROID_HOME` para o Android SDK e execute `./gradlew :app:assembleDebug`.
O APK instalável é gerado em `app/build/outputs/apk/debug/app-debug.apk`.
Para distribuição, execute `./gradlew :app:assembleRelease` e assine o APK de release.

## Uso

A tela inicial tem pesquisa Google e favoritos salvos localmente. A barra do
navegador contém, nesta ordem, início, voltar, endereço, recarregar, favorito
e opções. Pesquisas na barra de endereço e na tela inicial abrem resultados do
Google. Tocar na estrela salva ou remove a página atual dos favoritos.

O menu `⋮` oferece avançar, bloqueio de pop-ups, bloqueio de anúncios e Sobre.
As escolhas de bloqueio são salvas. O item Sobre abre
`https://github.com/lucaswotta` na mesma WebView. Links HTTP e HTTPS de outros
aplicativos podem ser abertos no navegador.

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

## Limitações

O app não oferece downloads, seletor de arquivos ou permissões de câmera e
microfone. O filtro de domínios não remove espaços de anúncios, não cobre todos
os redirecionamentos ou requisições de Service Workers e não bloqueia anúncios
servidos pelo mesmo domínio do conteúdo.
