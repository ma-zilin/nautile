package io.github.mazilin.nautile.platform.keystore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.mazilin.nautile.domain.port.SecretStore
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore AES-GCM 密钥存储（架构 §6）。
 *
 * 文件格式：`版本(1B) + IV(12B) + 密文+tag`，放在
 * `context.noBackupFilesDir/secrets/<keyName>.bin`；不写 Room/DataStore，不打印 Key、密文、IV 或 AAD。
 *
 * 接口保持同步；调用方（Key 用例、网关）必须在 IO 上下文调用，不在主线程做 Keystore/文件 IO。
 */
class AndroidKeystoreSecretStore(context: Context) : SecretStore {

    private val appContext = context.applicationContext

    override fun put(key: String, value: String) {
        val file = secretsFile(key)
        val directory = file.parentFile ?: throw IOException("密钥目录不可用")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("无法创建密钥目录")
        }
        val encrypted = encrypt(key, value)
        val temp = File(directory, "${file.name}.tmp")
        var renamed = false
        try {
            temp.writeBytes(encrypted)
            renamed = temp.renameTo(file)
            if (!renamed) throw IOException("无法替换密钥文件")
        } finally {
            if (!renamed) temp.delete()
        }
    }

    override fun get(key: String): String? {
        val file = secretsFile(key)
        if (!file.isFile) return null
        return try {
            val plain = decrypt(key, file.readBytes())
            if (plain == null) {
                // 格式或版本不合法：尽力删除损坏文件并回落“未配置”。
                file.delete()
                null
            } else {
                plain
            }
        } catch (_: Exception) {
            // 解析/解密失败（Keystore 重置、别名丢失、文件损坏）→ 删除并返回 null，不崩溃、不打印密文。
            file.delete()
            null
        }
    }

    override fun delete(key: String) {
        // 先删密文，确保之后即使别名删除失败也已无密文可用。
        secretsFile(key).delete()
        try {
            val keyStore = loadKeyStore()
            val alias = aliasOf(key)
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } catch (_: Exception) {
            // 别名删除失败不影响清除结果；不记录异常中的内部信息。
        }
    }

    private fun encrypt(keyName: String, value: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeyOrCreate(keyName))
        cipher.updateAAD(aad())
        val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        check(iv.size == GCM_IV_SIZE) { "AES-GCM IV 长度异常" }
        return ByteArray(1 + iv.size + cipherText.size).also { out ->
            out[0] = FORMAT_VERSION
            iv.copyInto(out, destinationOffset = 1)
            cipherText.copyInto(out, destinationOffset = 1 + iv.size)
        }
    }

    private fun decrypt(keyName: String, data: ByteArray): String? {
        if (data.size <= 1 + GCM_IV_SIZE) return null
        if (data[0] != FORMAT_VERSION) return null
        val secretKey = existingSecretKey(keyName) ?: return null
        val iv = data.copyOfRange(1, 1 + GCM_IV_SIZE)
        val cipherText = data.copyOfRange(1 + GCM_IV_SIZE, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(aad())
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun secretKeyOrCreate(keyName: String): SecretKey {
        val keyStore = loadKeyStore()
        val alias = aliasOf(keyName)
        try {
            (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        } catch (_: Exception) {
            // 别名可能已失效（Keystore 重置/设备恢复）：尽力删除后重建，避免保存永久失败。
            try {
                keyStore.deleteEntry(alias)
            } catch (_: Exception) {
                // 删除失败仍继续尝试生成；失败由调用方转为“保存失败”。
            }
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun existingSecretKey(keyName: String): SecretKey? =
        loadKeyStore().getKey(aliasOf(keyName), null) as? SecretKey

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun secretsFile(keyName: String): File {
        require(SAFE_KEY_NAME.matches(keyName)) { "非法密钥键名" }
        return File(File(appContext.noBackupFilesDir, SECRETS_DIR), "$keyName.bin")
    }

    private fun aliasOf(keyName: String): String {
        require(SAFE_KEY_NAME.matches(keyName)) { "非法密钥键名" }
        return "$ALIAS_PREFIX$keyName"
    }

    private fun aad(): ByteArray = FIXED_AAD.toByteArray(Charsets.UTF_8)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ALIAS_PREFIX = "nautile_secret_"
        const val SECRETS_DIR = "secrets"
        const val KEY_SIZE_BITS = 256
        const val GCM_IV_SIZE = 12
        const val GCM_TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
        const val FIXED_AAD = "nautile.ai.deepseek.key.v1"
        val SAFE_KEY_NAME = Regex("[A-Za-z0-9_.-]+")
    }
}
