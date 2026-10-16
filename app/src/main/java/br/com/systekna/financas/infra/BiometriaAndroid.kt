/* Infraestrutura: biometria como alternativa ao PIN (porta Biometria).
   Uma chave AES-GCM 256 do Android Keystore que só funciona depois de uma digital/rosto forte (BIOMETRIC_STRONG),
   a cada uso. Ela cifra a chave de dados; o resultado fica em noBackupFilesDir/biometria.bin.
   Cadastrar uma digital nova no aparelho invalida a chave (setInvalidatedByBiometricEnrollment): aí o app pede o PIN.
   O leitor (BiometricPrompt) precisa da tela aberta: a MainActivity se registra em `tela`. */
package br.com.systekna.financas.infra

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import br.com.systekna.financas.aplicacao.Biometria
import br.com.systekna.financas.aplicacao.BiometriaInvalidada
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.lang.ref.WeakReference
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BiometriaAndroid(private val context: Context) : Biometria {
    private val ALIAS = "financas-kotlin-biometria-v1"
    private val arq = File(context.noBackupFilesDir, "biometria.bin")
    private val arqRecusa = File(context.noBackupFilesDir, "biometria-recusada")
    private var telaRef = WeakReference<FragmentActivity>(null)
    var tela: FragmentActivity?
        get() = telaRef.get()
        set(v) { telaRef = WeakReference(v) }

    private fun ks() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun disponivel() = BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    override fun ativa() = arq.exists() && ks().containsAlias(ALIAS)
    override fun recusada() = arqRecusa.exists()
    override fun recusar() { arqRecusa.writeText("1") }
    override fun desativar() {
        arq.delete()
        runCatching { ks().deleteEntry(ALIAS) }
    }

    private fun novaChave(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        // Uma autenticação biométrica forte por uso da chave (sem janela de validade).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        init(spec.build())
    }.generateKey()

    override suspend fun ativar(dek: String): Boolean {
        desativar()
        val cifra = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, novaChave()) }
        val ok = autenticar(cifra, "Ativar biometria", "Confirme com a digital ou o rosto para abrir o app sem o PIN.") ?: return false.also { desativar() }
        val ct = ok.doFinal(Base64.getDecoder().decode(dek))
        val tmp = File(arq.parentFile, arq.name + ".tmp")
        tmp.writeBytes(ok.iv + ct)
        if (!tmp.renameTo(arq)) { arq.delete(); check(tmp.renameTo(arq)) }
        arqRecusa.delete()
        return true
    }

    override suspend fun abrir(): String? {
        val b = arq.readBytes()
        val cifra = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            val chave = ks().getKey(ALIAS, null) as? SecretKey ?: throw BiometriaInvalidada()
            cifra.init(Cipher.DECRYPT_MODE, chave, GCMParameterSpec(128, b, 0, 12))
        } catch (e: KeyPermanentlyInvalidatedException) { throw BiometriaInvalidada() }
        val ok = autenticar(cifra, "Abrir Finanças da Casa", "Use a digital ou o rosto.") ?: return null
        val raw = ok.doFinal(b, 12, b.size - 12)
        try { return Base64.getEncoder().encodeToString(raw) } finally { raw.fill(0) }
    }

    // Mostra o leitor com a cifra presa à autenticação. Devolve a cifra liberada, ou null se a pessoa cancelou.
    private suspend fun autenticar(cifra: Cipher, titulo: String, subtitulo: String): Cipher? {
        val atividade = tela ?: throw IllegalStateException("Tela do app fechada.")
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(atividade, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                    if (!cont.isActive) return
                    val c = r.cryptoObject?.cipher
                    if (c != null) cont.resume(c) else cont.resumeWithException(IllegalStateException("Biometria sem chave."))
                }
                override fun onAuthenticationError(codigo: Int, msg: CharSequence) {
                    if (!cont.isActive) return
                    if (codigo in CANCELOU) cont.resume(null) else cont.resumeWithException(IllegalStateException(msg.toString()))
                }
                // onAuthenticationFailed (digital não reconhecida): o próprio leitor deixa tentar de novo.
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(titulo).setSubtitle(subtitulo)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setNegativeButtonText("Usar PIN")
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cifra))
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    private companion object {
        val CANCELOU = setOf(BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED)
    }
}
