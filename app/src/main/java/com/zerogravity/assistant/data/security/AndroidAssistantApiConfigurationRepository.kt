package com.zerogravity.assistant.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.zerogravity.assistant.domain.model.AssistantApiConfiguration
import com.zerogravity.assistant.domain.model.AssistantApiEndpoint
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.port.AssistantApiConfigurationRepository
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidAssistantApiConfigurationRepository(context: Context) :
    AssistantApiConfigurationRepository {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val currentConfiguration = MutableStateFlow(preferences.readConfiguration())
    override val configuration: Flow<AssistantApiConfiguration> =
        currentConfiguration.asStateFlow()

    override suspend fun save(
        endpoint: String,
        model: String,
        apiKey: String,
    ): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        val normalizedEndpoint = AssistantApiEndpoint.normalizeBaseUrl(endpoint)
            ?: return@withContext ServiceResult.Failure(FailureReason.INVALID_INPUT)
        val normalizedModel = model.trim()
        val normalizedApiKey = apiKey.trim()
        if (!AssistantApiEndpoint.isValidModel(normalizedModel) ||
            !AssistantApiEndpoint.isValidApiKey(normalizedApiKey)
        ) {
            return@withContext ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }

        try {
            val editor = preferences.edit()
                .putString(KEY_ENDPOINT, normalizedEndpoint)
                .putString(KEY_MODEL, normalizedModel)
            if (normalizedApiKey.isNotEmpty()) {
                editor.putString(KEY_ENCRYPTED_API_KEY, encrypt(normalizedApiKey))
            }
            if (!editor.commit()) {
                return@withContext ServiceResult.Failure(FailureReason.IO_ERROR)
            }
            currentConfiguration.value = preferences.readConfiguration()
            ServiceResult.Success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        }
    }

    override suspend fun clear(): ServiceResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!preferences.edit().clear().commit()) {
                    ServiceResult.Failure(FailureReason.IO_ERROR)
                } else {
                    currentConfiguration.value = preferences.readConfiguration()
                    ServiceResult.Success(Unit)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                ServiceResult.Failure(FailureReason.IO_ERROR, exception)
            }
        }

    override suspend fun readApiKey(): ServiceResult<String> = withContext(Dispatchers.IO) {
        val encrypted = preferences.getString(KEY_ENCRYPTED_API_KEY, null)
            ?: return@withContext ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        try {
            ServiceResult.Success(decrypt(encrypted))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH_BYTES)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun SharedPreferences.readConfiguration() = AssistantApiConfiguration(
        endpoint = getString(KEY_ENDPOINT, null) ?: AssistantApiConfiguration.DEFAULT_ENDPOINT,
        model = getString(KEY_MODEL, null).orEmpty(),
        hasApiKey = contains(KEY_ENCRYPTED_API_KEY),
    )

    private companion object {
        const val PREFERENCES_NAME = "assistant_api_configuration"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_MODEL = "model"
        const val KEY_ENCRYPTED_API_KEY = "encrypted_api_key"
        const val KEY_ALIAS = "zerogravity_api_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
