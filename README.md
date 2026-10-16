# Finanças da Casa · piloto Kotlin

Piloto de migração nativa do PWA `../stk-desp-firebase-google` (v2.6.3). Escopo: **domínio + criptografia + 1 tela**.

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

## Build

Sem Android SDK na máquina: o APK sai pelo GitHub Actions (`.github/workflows/build-apk.yml`, artefato `financas-casa-kotlin-apk`). No Android Studio: abrir esta pasta (o Studio cria o wrapper do Gradle).

## O que o piloto não cobre (próximas etapas)

- Firebase (Auth + Firestore) e as telas de PIN, palavras, cartão, dívidas, investimentos e metas.
- Cofre local: a DEK guardada no Android Keystore com biometria (`BiometricPrompt`), no lugar do WebAuthn PRF.
- Room cifrado (SQLCipher) e desfoque real do vidro (Haze ou `Modifier.blur`).

## Licença

Proprietária, todos os direitos reservados. Veja [LICENSE](LICENSE). O repositório é público só para consulta. As fontes Space Grotesk e IBM Plex Mono seguem a SIL OFL 1.1 ([LICENSES/](LICENSES/)).
