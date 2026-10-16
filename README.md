# Finanças da Casa · piloto Kotlin

Piloto de migração nativa do PWA `../stk-desp-firebase-google` (v2.6.3). Escopo: **domínio + criptografia + tela de lançamentos**, com **acesso por PIN** e **backup cifrado**. Dados só no aparelho (SQLite), sem internet.

## Baixar o APK no celular

Página de download dos dois apps (com QR code para abrir no celular): **https://celsoalmeidalf.github.io/stk-financas-android/**

**[Baixar a última versão pelo GitHub](https://github.com/CelsoAlmeidaLF/stk-financas-kotlin/releases/latest/download/financas-casa-kotlin.apk)**: abra este link no navegador do Android, baixe e toque no arquivo. Na primeira vez, o Android pede para permitir a instalação de apps de fontes desconhecidas. Versões anteriores ficam em [Releases](https://github.com/CelsoAlmeidaLF/stk-financas-kotlin/releases).

O APK é assinado com a chave de release da Systekna (certificado `CN=Celso de Almeida Leite F, O=Systekna, C=BR`, SHA-256 `1b21a6ac…5e72de236`). As atualizações instalam por cima da versão anterior. Quem instalou um APK de debug antigo precisa desinstalá-lo uma vez antes.

| Módulo | Camada | O que tem |
| --- | --- | --- |
| `dominio/` | Domain (Kotlin puro, JVM) | `Modelo.kt` (porta do `modelo.js`), `Validacao.kt` (`limpar`/`ESQUEMAS`), `CriptoCasa.kt` (porta do `cripto-casa.js`), `CofrePin.kt` (PIN e código de recuperação), `BackupCifrado.kt`, `Registros.kt`, `Json.kt`. Lista BIP39 em `resources/bip39-pt.txt` (sha256 `2685e9c1…cd3f`, igual à oficial). |
| `app/` · `aplicacao/` | Application | Portas `RepositorioLancamentos` e `ArmazemCofre`; `AcessoViewModel` (PIN, bloqueio, recuperação) e `LancamentosViewModel` (lançar, exportar e restaurar backup). |
| `app/` · `infra/` | Infrastructure | `RepositorioCifrado` (Room/SQLite, cada lançamento cifrado) e `CofreKeystore` (cofre do PIN cifrado pelo Android Keystore). |
| `app/` · `ui/` | Interface | Jetpack Compose: telas de PIN, lançamentos do mês e backup, tokens do `aero-fin.css`, Space Grotesk + IBM Plex Mono (TTF gerados dos woff2 do PWA), ícones Lucide em `ImageVector`. |

## Segurança

**Acesso por PIN** (6 números):

- Na primeira abertura, o app cria uma chave de dados aleatória (AES-256) e pede um PIN. O PIN embrulha essa chave via PBKDF2-HMAC-SHA256 com 600 mil iterações.
- Junto sai um **código de recuperação** (160 bits), mostrado uma vez: com ele dá para criar um PIN novo se esquecer o atual.
- O cofre (chave embrulhada) fica em `noBackupFilesDir/cofre.bin`, **cifrado de novo pelo Android Keystore**: copiado para fora do aparelho, não serve para testar PINs.
- Depois de 5 PINs errados seguidos, espera 30 s, dobrando a cada erro (até 15 min).
- O app bloqueia pelo cadeado no topo ou depois de 1 minuto em segundo plano. A chave fica só na memória enquanto está aberto.
- `FLAG_SECURE`: sem print de tela e sem miniatura na lista de apps recentes.

**Banco:** `databases/financas.db` (Room). Tabela `registro`: cada lançamento cifrado (AES-GCM, AAD `local/lancamentos/<id>`); abertos só o mês e a hora de criação. Lançamentos da versão sem criptografia são cifrados e apagados no primeiro desbloqueio.

**Backup cifrado:** ícone de arquivo no topo da tela.

- *Exportar*: senha própria do backup (mínimo 8 caracteres, digitada 2 vezes) → o Android pergunta onde salvar (Downloads, Drive…). Arquivo JSON com os lançamentos cifrados (PBKDF2 600 mil + AES-GCM).
- *Restaurar*: arquivo + senha. Cada lançamento passa pela validação do domínio e substitui os deste aparelho. Funciona em outro celular, com outro PIN.
- Sem a senha o arquivo não abre, e qualquer alteração nele faz a abertura falhar.

Todo campo de senha e o teclado do PIN têm o botão de olho para mostrar ou ocultar o que foi digitado.

## Testes

```bash
gradle :dominio:test :app:testReleaseUnitTest   # 35 testes, rodam no JVM
node scripts/conferir-interop.mjs   # depois do teste: o PWA abre o que o Kotlin cifrou
node scripts/gerar-interop.mjs      # regenera o fixture a partir do cripto-casa.js real
```

- 17 testes portados de `test/modelo.test.js` e `test/cripto-casa.test.js`, com os mesmos casos e números, e 1 da trava de 2 dias.
- 4 testes de interoperabilidade (`InteropTest`): o Kotlin abre a casa criada pelo `cripto-casa.js` real e decifra um lançamento gravado pelo PWA. Na volta, o PWA abre o que o Kotlin cifrou.
- 7 testes do cofre e do backup (`CofrePinTest`): PIN certo/errado, código de recuperação, troca de PIN, cofre adulterado, espera, backup com senha certa/errada, arquivo alterado e registro inválido.
- 6 testes da aplicação e do repositório (`AcessoEBackupTest`, DAO em memória): criar PIN, bloqueio por erros, esqueci o PIN, lançamento cifrado (e texto trocado de linha recusado), migração dos antigos, exportar e restaurar em outro aparelho.
- O Android Keystore e as telas só rodam no aparelho: a validação final é no celular.

## Desenvolvimento e build (local)

Tudo roda na máquina local; o repositório guarda o código e publica o APK em Releases.

```bash
scripts/build-apk.sh               # roda os testes e gera dist/financas-casa-kotlin.apk
scripts/publicar-apk.sh v0.1.2     # cria a Release e atualiza a página de download (precisa do gh logado)
```

Requisitos: JDK 17, a chave de assinatura, Gradle 8.9+ (o script acha o do cache `~/.gradle` se não houver no PATH) e Android SDK em `~/Android/Sdk` (ou `ANDROID_HOME`), com `platforms;android-35`. No Android Studio: abrir esta pasta.

### Chave de assinatura

`scripts/build-apk.sh` compila o release e assina com `~/.android-keys/systekna-release.jks` (alias `systekna`, senha em `~/.android-keys/systekna-release.pass`). Outros caminhos: `STK_KEYSTORE` e `STK_KEYPASS_FILE`. A chave nunca entra no git. **Sem ela não dá para publicar atualizações**: os aparelhos recusam um APK assinado com outra chave.

## O que o piloto não cobre (próximas etapas)

- As telas de cartão, compras, dívidas, investimentos e metas.
- Biometria (`BiometricPrompt` + Keystore) como alternativa ao PIN.
- Desfoque real do vidro (Haze ou `Modifier.blur`).

## Licença

Proprietária, todos os direitos reservados. Veja [LICENSE](LICENSE). O repositório é público só para consulta. As fontes Space Grotesk e IBM Plex Mono seguem a SIL OFL 1.1 ([LICENSES/](LICENSES/)).
