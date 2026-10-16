/* Cofre do PIN: a chave de dados do aparelho (DEK, AES-GCM 256) fica embrulhada pelo PIN e pelo código de recuperação.
   - PIN: 6 números. PBKDF2-HMAC-SHA256 (600 mil iterações) do PIN → chave que embrulha a DEK.
   - Código de recuperação: 32 caracteres base32 (160 bits), mostrado uma vez; troca o PIN se ele for esquecido.
   - A DEK cifra cada lançamento no banco (CriptoCasa.selar). Sem o PIN ou o código, o banco é ilegível.
   No Android, o cofre ainda é cifrado pelo Keystore do aparelho (infra), para o PIN não poder ser testado fora dele. */
package br.com.systekna.financas.dominio

import kotlinx.serialization.Serializable

@Serializable
data class Cofre(val v: Int, val iter: Int, val kid: String, val pin: Caixa, val recup: Caixa)

object CofrePin {
    private const val CTX_PIN = "financas-kotlin:v1:cofre:pin"
    private const val CTX_RECUP = "financas-kotlin:v1:cofre:recup"
    private val PIN = Regex("^\\d{6}$")

    fun pinValido(pin: String) = PIN.matches(pin)
    private fun exigirPin(pin: String) { if (!pinValido(pin)) throw ErroCripto("PIN_INVALIDO", "O PIN tem 6 números.") }

    data class Novo(val dek: String, val cofre: Cofre, val codigo: String)

    fun criar(pin: String, iteracoes: Int = CriptoCasa.ITERACOES): Novo {
        exigirPin(pin)
        val raw = CriptoCasa.aleatorio(32)
        try {
            val dek = CriptoCasa.b64(raw); val codigo = CriptoCasa.novoCodigo()
            val cofre = Cofre(1, iteracoes, CriptoCasa.kidDe(dek),
                CriptoCasa.embrulhar(dek, pin, CTX_PIN, iteracoes), CriptoCasa.embrulhar(dek, codigo, CTX_RECUP, iteracoes))
            return Novo(dek, cofre, codigo)
        } finally { raw.fill(0) }
    }

    private fun conferir(c: Cofre) { if (c.v != 1 || c.iter < 1) throw ErroCripto(null, "Cofre inválido.") }
    private fun conferirKid(c: Cofre, dek: String) = dek.also { if (CriptoCasa.kidDe(it) != c.kid) throw ErroCripto(null, "Cofre corrompido.") }

    fun abrirComPin(c: Cofre, pin: String): String {
        conferir(c); exigirPin(pin)
        return conferirKid(c, CriptoCasa.desembrulhar(c.pin, pin, CTX_PIN, c.iter))
    }

    fun abrirComCodigo(c: Cofre, codigo: String): String {
        conferir(c)
        return conferirKid(c, CriptoCasa.desembrulhar(c.recup, CriptoCasa.normalizarCodigo(codigo), CTX_RECUP, c.iter))
    }

    // Troca o PIN (a DEK e o código de recuperação continuam os mesmos).
    fun trocarPin(c: Cofre, dek: String, novoPin: String): Cofre {
        exigirPin(novoPin); conferirKid(c, dek)
        return c.copy(pin = CriptoCasa.embrulhar(dek, novoPin, CTX_PIN, c.iter))
    }

    /* Tentativas: depois de 5 erros seguidos, espera 30 s, dobrando a cada erro (até 15 min). */
    const val ERROS_LIVRES = 5
    fun esperaMs(erros: Int): Long = if (erros < ERROS_LIVRES) 0 else minOf(30_000L shl minOf(erros - ERROS_LIVRES, 5), 15 * 60_000L)
}
