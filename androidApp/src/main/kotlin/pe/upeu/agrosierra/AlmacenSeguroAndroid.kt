package pe.upeu.agrosierra

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import pe.upeu.agrosierra.data.local.AlmacenSeguro

class AlmacenSeguroAndroid(context: Context) : AlmacenSeguro {
    private val preferencias = context.getSharedPreferences("agro_sesion_cifrada", Context.MODE_PRIVATE)
    private val alias = "agro_token_v1"

    private fun clave(): SecretKey {
        val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (almacen.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    override fun guardarToken(token: String) {
        val cifrador = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, clave()) }
        val cifrado = cifrador.doFinal(token.toByteArray(Charsets.UTF_8))
        check(preferencias.edit()
            .putString("iv", Base64.encodeToString(cifrador.iv, Base64.NO_WRAP))
            .putString("dato", Base64.encodeToString(cifrado, Base64.NO_WRAP))
            .commit()) { "No se pudo guardar la sesión cifrada" }
    }

    override fun leerToken(): String? {
        val iv = preferencias.getString("iv", null) ?: return null
        val dato = preferencias.getString("dato", null) ?: return null
        return try {
            val cifrador = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, clave(), javax.crypto.spec.GCMParameterSpec(128,
                    Base64.decode(iv, Base64.NO_WRAP)))
            }
            String(cifrador.doFinal(Base64.decode(dato, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            borrarToken() // Clave revocada o copia de respaldo sin la clave Keystore.
            null
        }
    }

    override fun borrarToken() {
        preferencias.edit().remove("iv").remove("dato").commit()
    }
}
