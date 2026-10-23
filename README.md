# Navegador Leve

Navegador Android de uma aba com WebView do sistema e página inicial do YouTube móvel.

## Requisitos

- Android 6.0 ou superior com Android System WebView instalado.
- JDK 17, Android SDK 34 e Gradle 8.9 para compilar.

## Instalação

Configure `ANDROID_HOME` para o Android SDK e execute `gradle :app:assembleDebug`.
O APK instalável é gerado em `app/build/outputs/apk/debug/app-debug.apk`.
Para distribuição, execute `gradle :app:assembleRelease` e assine o APK de release.

## Uso

Digite uma URL na barra e toque em **Ir**. As setas navegam no histórico;
o botão circular recarrega a página.
A página inicial é `https://m.youtube.com/`.

## Configuração

`MainActivity.HOME` define a página inicial. O app usa o provedor WebView do aparelho.

## Limitações

O consumo de RAM depende do WebView, da página e do dispositivo. O processo de
renderização da WebView deve ser incluído na medição. Esta versão tem uma aba e
não inclui downloads, favoritos nem seletor de arquivos.
