# Finanças da Casa · piloto Kotlin

Piloto de migração nativa do PWA `../stk-desp-firebase-google` (v2.6.3). Escopo: **domínio + criptografia + 1 tela**.

## Baixar o APK no celular

**[Baixar a última versão](https://github.com/CelsoAlmeidaLF/stk-financas-kotlin/releases/latest/download/financas-casa-kotlin.apk)**: abra este link no navegador do Android, baixe e toque no arquivo. Na primeira vez, o Android pede para permitir a instalação de apps de fontes desconhecidas. Versões anteriores ficam em [Releases](https://github.com/CelsoAlmeidaLF/stk-financas-kotlin/releases).

O APK é de teste (build debug), assinado com a chave de debug da máquina de build: as atualizações instalam por cima enquanto forem geradas na mesma máquina.

| Módulo | Camada | O que tem |
| --- | --- | --- |
| `dominio/` | Domain (Kotlin puro, JVM) | `Modelo.kt` (porta do `modelo.js`), `Validacao.kt` (`limpar`/`ESQUEMAS`), `CriptoCasa.kt` (porta do `cripto-casa.js`), `Registros.kt`, `Json.kt`. Lista BIP39 em `resources/bip39-pt.txt` (sha256 `2685e9c1…cd3f`, igual à oficial). |
| `app/` · `aplicacao/` | Application | Porta `RepositorioLancamentos` e `LancamentosViewModel` (valida com as regras do PWA). |
| `app/` · `infra/` | Infrastructure | Room (SQLite) como adaptador da porta. |
| `app/` · `ui/` | Interface | Jetpack Compose: tela de lançamentos do mês, tokens do `aero-fin.css`, Space Grotesk + IBM Plex Mono (TTF gerados dos woff2 do PWA), ícones Lucide em `ImageVector`. |

## Testes

```bash
gradle :dominio:test          # 22 testes, não precisa de Android SDK
node scripts/conferir-interop.mjs   # depois do teste: o PWA abre o que o Kotlin cifrou
node scripts/gerar-interop.mjs      # regenera o fixture a partir do cripto-casa.js real
```

- 17 testes portados de `test/modelo.test.js` e `test/cripto-casa.test.js`, com os mesmos casos e números.
- 1 teste novo: trava de 2 dias (a regra do `firestore.rules` virou regra do domínio).
- 4 testes de interoperabilidade (`InteropTest`): o Kotlin abre a casa criada pelo `cripto-casa.js` real (palavras, hash, código, certificado com senha acentuada) e decifra e valida um lançamento gravado pelo PWA. Na volta, o PWA abre o que o Kotlin cifrou.
- O teste "ordem trocada" foi corrigido: no PWA ele falha por acaso ~1 em 16 vezes; aqui aceita os dois resultados válidos.

## Desenvolvimento e build (local)

Tudo roda na máquina local; o repositório guarda o código e publica o APK em Releases.

```bash
scripts/build-apk.sh               # testa o domínio e gera dist/financas-casa-kotlin.apk
scripts/publicar-apk.sh v0.1.0     # cria a Release com o APK (precisa do gh logado)
```

Requisitos: JDK 17, Gradle 8.9+ (o script acha o do cache `~/.gradle` se não houver no PATH) e Android SDK em `~/Android/Sdk` (ou `ANDROID_HOME`), com `platforms;android-35`. No Android Studio: abrir esta pasta.

## O que o piloto não cobre (próximas etapas)

- Firebase (Auth + Firestore) e as telas de PIN, palavras, cartão, dívidas, investimentos e metas.
- Cofre local: a DEK guardada no Android Keystore com biometria (`BiometricPrompt`), no lugar do WebAuthn PRF.
- Room cifrado (SQLCipher) e desfoque real do vidro (Haze ou `Modifier.blur`).

## Licença

Proprietária, todos os direitos reservados. Veja [LICENSE](LICENSE). O repositório é público só para consulta. As fontes Space Grotesk e IBM Plex Mono seguem a SIL OFL 1.1 ([LICENSES/](LICENSES/)).
