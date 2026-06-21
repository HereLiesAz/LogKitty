package com.hereliesaz.logkitty.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Secure on-device store for the GitHub Personal Access Token.
 *
 * The PAT is sensitive, so it is kept **out of [UserPreferences]** (whose `logkitty_user_prefs` file
 * is cloud-backed-up/device-transferred and exported in the settings JSON). It lives in its own
 * `logkitty_secure_prefs` file — which must be excluded in `res/xml/backup_rules.xml` and
 * `res/xml/data_extraction_rules.xml` — and is encrypted at rest with an AES/GCM key held in the
 * Android Keystore (so the stored ciphertext is useless off-device, and there is no new dependency).
 *
 * All crypto is best-effort: any failure (e.g. the Keystore key was invalidated) degrades to "no
 * token" rather than throwing, and corrupt state is cleared.
 */
class GitHubCredentials(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(load())
    /** The decrypted PAT, or null when none is set / it couldn't be decrypted. */
    val token: StateFlow<String?> = _token.asStateFlow()

    /** Stores (encrypted) or, for a null/blank value, clears the PAT. */
    fun setToken(raw: String?) {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) {
            clear()
            return
        }
        val encrypted = runCatching { encrypt(value) }.getOrNull()
        if (encrypted == null) {
            clear()
            return
        }
        prefs.edit().putString(KEY_TOKEN, encrypted).apply()
        _token.value = value
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _token.value = null
    }

    private fun load(): String? {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return null
        return runCatching { decrypt(stored) }.getOrElse {
            // Key rotated/invalidated or corrupt blob — drop it so the user can re-enter cleanly.
            prefs.edit().remove(KEY_TOKEN).apply()
            null
        }
    }

    // --- AES/GCM via the Android Keystore. Stored form: base64(iv):base64(ciphertext). ---

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split(":", limit = 2)
        require(parts.size == 2) { "malformed ciphertext" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ct = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "logkitty_secure_prefs"
        const val KEY_TOKEN = "github_pat"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "logkitty_github_pat"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
