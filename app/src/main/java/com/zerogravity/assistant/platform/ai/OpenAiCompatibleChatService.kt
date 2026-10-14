package com.zerogravity.assistant.platform.ai

import com.zerogravity.assistant.domain.model.AssistantApiConfiguration
import com.zerogravity.assistant.domain.model.AssistantApiEndpoint
import com.zerogravity.assistant.domain.model.ChatMessage
import com.zerogravity.assistant.domain.model.ChatRole
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.port.AssistantApiConfigurationRepository
import com.zerogravity.assistant.domain.port.AssistantChatService
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class OpenAiCompatibleChatService(
    private val configurationRepository: AssistantApiConfigurationRepository,
) : AssistantChatService {
    override suspend fun respond(messages: List<ChatMessage>): ServiceResult<String> {
        val recentMessages = messages.takeLast(MAX_CONTEXT_MESSAGES)
        if (recentMessages.isEmpty() || recentMessages.last().role != ChatRole.USER ||
            recentMessages.any { it.content.isBlank() || it.content.length > MAX_MESSAGE_LENGTH }
        ) {
            return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }

        val configuration = configurationRepository.configuration.first()
        if (!configuration.isConfigured) return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
        val endpoint = AssistantApiEndpoint.chatCompletionsUrl(configuration.endpoint)
            ?: return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        val apiKey = when (val result = configurationRepository.readApiKey()) {
            is ServiceResult.Failure -> return result
            is ServiceResult.Success -> result.value
        }
        if (apiKey.isBlank() || !AssistantApiEndpoint.isValidApiKey(apiKey)) {
            return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }

        return try {
            withContext(Dispatchers.IO) {
                executeRequest(endpoint, configuration, apiKey, recentMessages)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        } catch (exception: JSONException) {
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        } catch (exception: SecurityException) {
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        }
    }

    private fun executeRequest(
        endpoint: String,
        configuration: AssistantApiConfiguration,
        apiKey: String,
        messages: List<ChatMessage>,
    ): ServiceResult<String> {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")

            val requestBody = createRequestBody(configuration.model, messages)
                .toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(requestBody.size)
            connection.outputStream.use { it.write(requestBody) }

            val status = connection.responseCode
            if (status !in 200..299) {
                connection.errorStream?.use(::readLimited)
                return ServiceResult.Failure(
                    FailureReason.IO_ERROR,
                    IOException("AI provider returned HTTP $status"),
                )
            }
            val response = connection.inputStream.use(::readLimited)
            val content = JSONObject(response)
                .getJSONArray("choices")
                .optJSONObject(0)
                ?.getJSONObject("message")
                ?.opt("content") as? String
            return content
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= MAX_MESSAGE_LENGTH }
                ?.let(ServiceResult<String>::Success)
                ?: ServiceResult.Failure(FailureReason.IO_ERROR)
        } finally {
            connection.disconnect()
        }
    }

    private fun createRequestBody(model: String, messages: List<ChatMessage>): String {
        val providerMessages = JSONArray().put(
            JSONObject()
                .put("role", "system")
                .put("content", SYSTEM_MESSAGE),
        )
        messages.forEach { message ->
            providerMessages.put(
                JSONObject()
                    .put(
                        "role",
                        when (message.role) {
                            ChatRole.USER -> "user"
                            ChatRole.ASSISTANT -> "assistant"
                        },
                    )
                    .put("content", message.content),
            )
        }
        return JSONObject()
            .put("model", model)
            .put("messages", providerMessages)
            .toString()
    }

    private fun readLimited(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_RESPONSE_BYTES) {
                throw IOException("AI provider response exceeded the size limit")
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val MAX_RESPONSE_BYTES = 1_048_576
        const val MAX_MESSAGE_LENGTH = 8_000
        const val MAX_CONTEXT_MESSAGES = 20
        const val SYSTEM_MESSAGE =
            "You are ZeroGravity, a helpful Android assistant. You cannot inspect or control the device, " +
                "send messages, place calls, or perform actions unless the app explicitly says it did so. " +
                "Be transparent when you are unsure."
    }
}
