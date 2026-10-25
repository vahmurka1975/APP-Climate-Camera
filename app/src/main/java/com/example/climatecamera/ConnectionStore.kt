package com.example.climatecamera

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class SavedConnection(val address: String, val username: String, val password: String, val rememberPassword: Boolean)

/** Device-local encrypted connection, excluded from backup. Never stores the API token. */
internal class ConnectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cc_connection", Context.MODE_PRIVATE)
    private val alias = "cc_connection_v105"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): SavedConnection? {
        val blob = prefs.getString("blob", null) ?: return null
        val parts = blob.split(":")
        require(parts.size == 2) { "Повреждено сохранённое подключение" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        val remember = json.optBoolean("rememberPassword", false)
        return SavedConnection(json.getString("address"), json.getString("username"), if (remember) json.optString("password") else "", remember)
    }
    fun save(settings: ConnectionSettings, rememberPassword: Boolean) {
        val json = JSONObject().put("address", settings.address).put("username", settings.username)
            .put("rememberPassword", rememberPassword).put("password", if (rememberPassword) settings.password else "")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("blob", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) { "Не удалось сохранить подключение" }
    }
    fun clear() {
        check(prefs.edit().clear().commit()) { "Не удалось удалить подключение" }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }
}
