package app.brain.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用设置：API 密钥用 Android Keystore 加密后存入 SharedPreferences。
 * 密钥只保存在手机系统安全区，不写入安装包或代码。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 当前 DeepSeek API 密钥；未配置时为空字符串。 */
    val apiKey: String
        get() = decryptStored(prefs.getString(KEY_API_KEY, null)) ?: ""

    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) {
            prefs.edit().putString(KEY_MODEL, value.trim().ifEmpty { DEFAULT_MODEL }).apply()
        }

    /** 电脑管理端登录密码（Keystore 加密存储）。 */
    var adminPassword: String
        get() = decryptStored(prefs.getString(KEY_ADMIN_PASSWORD, null)) ?: ""
        set(value) {
            val clean = value.trim()
            prefs.edit().putString(KEY_ADMIN_PASSWORD, clean.ifEmpty { null }?.let { encrypt(it) }).apply()
        }

    var adminPort: Int
        get() = prefs.getInt(KEY_ADMIN_PORT, DEFAULT_ADMIN_PORT)
        set(value) {
            prefs.edit().putInt(KEY_ADMIN_PORT, value).apply()
        }

    fun saveApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString(KEY_API_KEY, clean.ifEmpty { null }?.let { encrypt(it) }).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decryptStored(stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        return try {
            val parts = stored.split(":", limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "brain_settings"
        private const val KEY_API_KEY = "api_key_enc"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_ADMIN_PASSWORD = "admin_password_enc"
        private const val KEY_ADMIN_PORT = "admin_port"
        private const val KEYSTORE_ALIAS = "brain_deepseek_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_ADMIN_PORT = 8080
    }
}
