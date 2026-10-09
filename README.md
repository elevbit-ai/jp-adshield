<div align="center">

# 🛡️ JP AdShield

### Bloqueador de anúncios e rastreadores para todo o Android — sem root

Cria uma **VPN local** no próprio aparelho e **filtra o DNS**, bloqueando anúncios e rastreadores
em **todos os apps e navegadores**. Inclui um **reprodutor em 2º plano** (tipo Premium).

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](#)
[![Tamanho](https://img.shields.io/badge/APK-31%20KB-1FBF66)](#)
[![Licença](https://img.shields.io/badge/licença-MIT-4DFF88)](LICENSE)
[![Sem root](https://img.shields.io/badge/sem%20root-sim-25D366)](#)

**[🌐 Site & vídeo](https://elevbit-ai.github.io/jp-adshield/)** · **[⬇ Baixar APK](https://github.com/elevbit-ai/jp-adshield/releases/latest)**

</div>

---

## ✨ O que é

O **JP AdShield** usa a API de VPN local do Android (sem root) para filtrar o DNS do aparelho. Quando um app ou site pede um domínio de anúncio ou rastreador, o pedido é bloqueado — em **todos** os apps, jogos e navegadores. É o mesmo método dos bloqueadores open-source sérios (DNS66, Blokada).

- 🛡️ Bloqueio no **sistema inteiro** (não só no navegador)
- 🔓 **Sem root** — é só ativar e confiar na VPN
- 🔒 **Privado** — o DNS vai direto ao Cloudflare (1.1.1.1); o app não coleta nada e não inspeciona o conteúdo acessado
- 📊 **Estatísticas** de bloqueios em tempo real
- 🔄 **Listas atualizáveis** (lista embutida + download da StevenBlack/hosts)
- 🪶 **31 KB**, sem dependências externas

## ▶️ Reprodutor em 2º plano (tipo Premium)

Abre vídeos da web (inclusive YouTube) num player que **continua tocando com a tela apagada ou o app minimizado**, e flutua em **Picture-in-Picture**. Usa um serviço em primeiro plano + wake lock para manter a reprodução viva e um script que impede o YouTube web de pausar ao ir para segundo plano.

> A eficácia da reprodução com a **tela apagada** pode variar por aparelho/versão do Android; o **Picture-in-Picture** é o modo mais garantido.

## ⚠️ Sobre os anúncios do YouTube (honestidade)

**Os anúncios DENTRO dos vídeos do YouTube NÃO podem ser bloqueados por DNS** — o YouTube entrega o anúncio pelo mesmo servidor (`googlevideo.com`) que entrega o vídeo; bloquear quebraria o vídeo. Qualquer app que promete isso por DNS está enganando.

Formas que **realmente** funcionam para o YouTube:
- **Navegador com bloqueador**: Firefox + uBlock Origin, ou o navegador Brave;
- **SponsorBlock**: pula trechos patrocinados;
- **YouTube Premium**: remove os anúncios oficialmente.

## ⬇ Instalação

1. Baixe o `JP-AdShield-v1.1.0.apk` em **[Releases](https://github.com/elevbit-ai/jp-adshield/releases/latest)**.
2. No Android, toque no arquivo e confirme **“Instalar mesmo assim”**.
3. Abra o app, toque em **Ativar proteção** e **confirme o pedido de VPN** do Android.

> 💡 Em **Configurações → Rede → DNS privado**, deixe desativado para o filtro funcionar melhor.

Requisitos: Android 7.0 (API 24) ou superior.

## 🔐 Permissões e por quê

| Permissão | Para quê |
|-----------|----------|
| `BIND_VPN_SERVICE` | Criar a VPN local que filtra o DNS. |
| `INTERNET` | Encaminhar o DNS ao Cloudflare e baixar listas. |
| `FOREGROUND_SERVICE` + `WAKE_LOCK` | Manter a proteção e o reprodutor ativos. |
| `POST_NOTIFICATIONS` | Mostrar a notificação de status (Android 13+). |

O app **não** usa câmera, microfone, contatos nem localização.

## 🛠️ Compilar do código-fonte

Requisitos: **JDK 17** e **Android SDK 34**.

```bash
git clone https://github.com/elevbit-ai/jp-adshield.git
cd jp-adshield
./gradlew assembleRelease     # no Windows: gradlew.bat assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`. Para assinar com sua chave, crie um `keystore.properties` na raiz (storeFile, storePassword, keyAlias, keyPassword).

## 📂 Estrutura

```
app/src/main/java/ai/elevbit/jpadshield/
  ├─ MainActivity.java      # tela: liga/desliga, stats, atualizar listas
  ├─ AdVpnService.java      # VPN local + filtro de DNS (NXDOMAIN p/ anúncios)
  ├─ Blocklist.java         # lista de domínios (cobre subdomínios)
  ├─ PlayerActivity.java    # reprodutor web em 2º plano + Picture-in-Picture
  └─ PlaybackService.java   # mantém a reprodução viva (foreground + wake lock)
app/src/main/assets/blocklist.txt   # lista embutida curada
docs/                                # site (GitHub Pages) + vídeo
```

## 👤 Autor

**Joaquim Pedro de Morais Filho** · ✉️ j360074@hotmail.com

## 📄 Licença

[MIT](LICENSE) © 2026 Joaquim Pedro de Morais Filho
