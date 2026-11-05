package com.focusfish.app.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 加密存储
 *
 * 研究结论明确指出：API Key 绝不能明文存 SharedPreferences，
 * 应入 Android Keystore 加密。这里用 AES-GCM + Keystore 派生密钥实现。
 *
 * 设计：
 * - 密钥存于 Android Keystore（不可导出），只在设备上解密
 * - 密文存 SharedPreferences（Base64 编码）
 * - Keystore 在部分 ROM 上不可用时降级为明文存储，保证功能可用
 */
object SecureKeyStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "focus_fish_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_LENGTH = 12

    /** 标记密文前缀，用于识别是否加密过 */
    private const val ENC_PREFIX = "enc:"
    private const val PLAIN_PREFIX = "plain:"

    /**
     * 加密后存储，返回可直接写入 SharedPreferences 的字符串
     */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            ENC_PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Keystore 不可用时降级：加前缀标识为明文，读取时识别
            PLAIN_PREFIX + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    /**
     * 解密读取
     */
    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        return try {
            when {
                stored.startsWith(ENC_PREFIX) -> {
                    val payload = stored.removePrefix(ENC_PREFIX)
                    val parts = payload.split(":")
                    if (parts.size != 2) return ""
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val data = Base64.decode(parts[1], Base64.NO_WRAP)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
                    String(cipher.doFinal(data), Charsets.UTF_8)
                }

                stored.startsWith(PLAIN_PREFIX) -> {
                    String(
                        Base64.decode(stored.removePrefix(PLAIN_PREFIX), Base64.NO_WRAP),
                        Charsets.UTF_8
                    )
                }

                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
