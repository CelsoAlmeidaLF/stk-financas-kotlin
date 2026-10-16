/* Infraestrutura: guarda o cofre do PIN (porta ArmazemCofre) num arquivo privado, cifrado por uma chave do
   Android Keystore (AES-GCM 256, não exportável, no hardware quando o aparelho tem). Assim o arquivo copiado do
   aparelho não serve para testar PINs em outro computador: só abre aqui dentro.
   Fica em noBackupFilesDir: não vai para o backup do Google nem para a transferência entre aparelhos. */
package br.com.systekna.financas.infra

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import br.com.systekna.financas.aplicacao.ArmazemCofre
import br.com.systekna.financas.aplicacao.Erros
import br.com.systekna.financas.dominio.Cofre
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CofreKeystore(context: Context) : ArmazemCofre {
    private val dir = context.noBackupFilesDir
    private val arqCofre = File(dir, "cofre.bin")
    private val arqErros = File(dir, "erros.txt")
    private val ALIAS = "financas-kotlin-cofre-v1"

    private fun chave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    // Grava num temporário e troca: o cofre nunca fica pela metade.
    private fun gravarAtomico(f: File, bytes: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { f.delete(); check(tmp.renameTo(f)) { "Não gravei ${f.name}" } }
    }

    override fun ler(): Cofre? {
        if (!arqCofre.exists()) return null
        val b = arqCofre.readBytes()
        val claro = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, b, 0, 12)); doFinal(b, 12, b.size - 12)
        }
        return Json.decodeFromString<Cofre>(claro.toString(Charsets.UTF_8))
    }

    override fun gravar(c: Cofre) {
        val cifra = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, chave()) }
        val ct = cifra.doFinal(Json.encodeToString(c).toByteArray(Charsets.UTF_8))
        gravarAtomico(arqCofre, cifra.iv + ct)
    }

    override fun erros(): Erros = runCatching {
        val (n, em) = arqErros.readText().trim().split(" ").map(String::toLong)
        Erros(n.toInt(), em)
    }.getOrDefault(Erros())

    override fun gravarErros(e: Erros) = gravarAtomico(arqErros, "${e.seguidos} ${e.ultimoEm}".toByteArray())
}
