/* Cofre do PIN e backup cifrado. Iterações baixas nos testes (o app usa 600 mil). */
package br.com.systekna.financas.dominio

import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.test.*

class CofrePinTest {
    private val ITER = 1_000
    private inline fun code(bloco: () -> Unit): String? = assertFailsWith<ErroCripto> { bloco() }.code

    @Test fun `PIN abre o cofre e devolve a mesma chave - PIN errado e PIN mal formado são recusados`() {
        val n = CofrePin.criar("482915", ITER)
        assertEquals(n.dek, CofrePin.abrirComPin(n.cofre, "482915"))
        assertEquals("SEGREDO_INCORRETO", code { CofrePin.abrirComPin(n.cofre, "482916") })
        assertEquals("PIN_INVALIDO", code { CofrePin.abrirComPin(n.cofre, "12345") })
        assertEquals("PIN_INVALIDO", code { CofrePin.criar("12a456", ITER) })
        assertEquals(32, java.util.Base64.getDecoder().decode(n.dek).size)
    }

    @Test fun `código de recuperação abre o cofre (com ou sem traços, minúsculo) e troca o PIN`() {
        val n = CofrePin.criar("000000", ITER)
        assertEquals(n.dek, CofrePin.abrirComCodigo(n.cofre, n.codigo.lowercase().replace("-", " ")))
        assertEquals("SEGREDO_INCORRETO", code { CofrePin.abrirComCodigo(n.cofre, CriptoCasa.novoCodigo()) })
        val novo = CofrePin.trocarPin(n.cofre, n.dek, "135790")
        assertEquals(n.dek, CofrePin.abrirComPin(novo, "135790"))
        assertEquals("SEGREDO_INCORRETO", code { CofrePin.abrirComPin(novo, "000000") })
        assertEquals(n.dek, CofrePin.abrirComCodigo(novo, n.codigo)) // o código continua valendo
    }

    @Test fun `cofre adulterado não abre`() {
        val n = CofrePin.criar("111111", ITER)
        val ct = java.util.Base64.getDecoder().decode(n.cofre.pin.ct).also { it[0] = (it[0].toInt() xor 1).toByte() }
        val ruim = n.cofre.copy(pin = n.cofre.pin.copy(ct = java.util.Base64.getEncoder().encodeToString(ct)))
        assertEquals("SEGREDO_INCORRETO", code { CofrePin.abrirComPin(ruim, "111111") })
        assertFailsWith<ErroCripto> { CofrePin.abrirComPin(n.cofre.copy(kid = "00000000"), "111111") }
    }

    @Test fun `espera cresce depois de 5 erros e para em 15 minutos`() {
        assertEquals(0, CofrePin.esperaMs(4))
        assertEquals(30_000, CofrePin.esperaMs(5))
        assertEquals(60_000, CofrePin.esperaMs(6))
        assertEquals(15 * 60_000L, CofrePin.esperaMs(40))
    }

    private val itens = listOf(
        BackupCifrado.Item(Lancamento("despesa", "Pão de açúcar", 4590, "2026-10-07", "Mercado", obs = "ção"), 1_791_000_000_000),
        BackupCifrado.Item(Lancamento("entrada", "Salário", 500_000, "2026-10-05", "Salário"), 1_791_100_000_000),
    )

    @Test fun `backup - ida e volta com a senha, sem texto aberto no arquivo`() {
        val arq = BackupCifrado.gerar(itens, "senha-forte-ção", ITER, Instant.parse("2026-10-16T12:00:00Z"))
        assertFalse(arq.contains("Pão") || arq.contains("Mercado") || arq.contains("4590"))
        assertEquals(2, Json.parseToJsonElement(arq).jsonObject["registros"]!!.jsonPrimitive.int)
        assertEquals(itens, BackupCifrado.abrir(arq, "senha-forte-ção"))
    }

    @Test fun `backup - senha errada, senha curta, arquivo alterado e arquivo estranho`() {
        val arq = BackupCifrado.gerar(itens, "12345678", ITER)
        assertEquals("SEGREDO_INCORRETO", code { BackupCifrado.abrir(arq, "12345679") })
        assertEquals("SENHA_FRACA", code { BackupCifrado.gerar(itens, "1234567", ITER) })
        val o = Json.parseToJsonElement(arq).jsonObject
        val caixa = o["caixa"]!!.jsonObject
        val ct = java.util.Base64.getDecoder().decode(caixa["ct"]!!.jsonPrimitive.content).also { it[3] = (it[3].toInt() xor 1).toByte() }
        val alterado = JsonObject(o + ("caixa" to JsonObject(caixa + ("ct" to JsonPrimitive(java.util.Base64.getEncoder().encodeToString(ct)))))).toString()
        assertEquals("SEGREDO_INCORRETO", code { BackupCifrado.abrir(alterado, "12345678") })
        assertEquals("BACKUP_INVALIDO", code { BackupCifrado.abrir("{\"formato\":\"outro\"}", "12345678") })
        assertEquals("BACKUP_INVALIDO", code { BackupCifrado.abrir("não é json", "12345678") })
        val futuro = JsonObject(o + ("versao" to JsonPrimitive(99))).toString()
        assertEquals("BACKUP_INVALIDO", code { BackupCifrado.abrir(futuro, "12345678") })
    }

    @Test fun `backup - registro inválido dentro do arquivo é recusado pela validação do domínio`() {
        val ruim = listOf(BackupCifrado.Item(Lancamento("roubo", "x", 1, "2026-10-07", "Outros"), 1))
        val arq = BackupCifrado.gerar(ruim, "12345678", ITER)
        assertContains(assertFailsWith<ErroCripto> { BackupCifrado.abrir(arq, "12345678") }.message!!, "Registro 1")
    }
}
