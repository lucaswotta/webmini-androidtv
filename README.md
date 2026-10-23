# Navegador Leve

Navegador Android com WebView do sistema e página inicial vazia.

## Requisitos

- Android 6.0 ou superior com Android System WebView instalado.
- JDK 17 e Android SDK 36 para compilar. O projeto inclui Gradle Wrapper 8.11.1.

## Instalação

Configure `ANDROID_HOME` para o Android SDK e execute `./gradlew :app:assembleDebug`.
O APK instalável é gerado em `app/build/outputs/apk/debug/app-debug.apk`.
Para distribuição, execute `./gradlew :app:assembleRelease` e assine o APK de release.

## Uso

Digite uma URL ou busca na barra e use a tecla **Ir** do teclado. O botão de abas abre,
alterna e fecha abas. Toque e segure um link para abri-lo em outra aba.
O menu oferece avançar, recarregar, página inicial vazia e acesso ao YouTube.
Links HTTP e HTTPS de outros aplicativos podem ser abertos no navegador.
O menu também liga ou desliga o bloqueio de anúncios. A escolha é salva e a
página é recarregada para aplicar a mudança. Janelas automáticas são bloqueadas;
links podem ser abertos em nova aba pelo toque longo.

## Configuração

`MainActivity.HOME` define a página inicial. O app usa o provedor WebView do aparelho.
Cada aba inativa guarda URL e histórico; somente a aba visível mantém uma WebView.
O limite é de oito abas. A página de uma aba inativa é carregada ao voltar a ela.
Interface, menus e abas usam Views do Android, sem biblioteca de interface,
fontes próprias, imagens de fundo ou miniaturas de abas. O tema claro ou escuro
segue a configuração do aparelho.
O bloqueio de anúncios começa desligado e filtra requisições para sete domínios
de anúncios conhecidos, sem extensão, serviço externo ou lista carregada em RAM.

## Medição

Use o mesmo aparelho, versão da WebView, URL e rede para comparar navegadores.
Meça o PSS do processo do app e do renderizador da WebView após abrir a página,
após iniciar um vídeo e após alternar abas. O comando
`adb shell dumpsys activity processes br.com.lucaswotta.navegadorleve`
mostra o processo renderizador associado; `adb shell dumpsys meminfo PID` mede
cada processo. Repita cada cenário três vezes e registre a mediana. O tamanho do
APK deve ser medido separadamente da RAM.

## Limitações

O consumo de RAM depende do WebView, da página e do dispositivo. O processo de
renderização da WebView deve ser incluído na medição. Não há downloads,
favoritos, seletor de arquivos ou permissões para câmera e microfone.
O filtro de domínios não remove espaços visuais de anúncios, não cobre todos os
redirecionamentos ou requisições de Service Workers e não bloqueia anúncios
servidos pelo mesmo domínio do conteúdo, como pode ocorrer no YouTube.
