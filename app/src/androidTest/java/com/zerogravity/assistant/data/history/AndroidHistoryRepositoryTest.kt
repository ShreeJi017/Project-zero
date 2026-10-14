package com.zerogravity.assistant.data.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerogravity.assistant.domain.model.ChatMessage
import com.zerogravity.assistant.domain.model.ChatRole
import com.zerogravity.assistant.domain.model.ServiceResult
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidHistoryRepositoryTest {
    @Test
    fun conversationSurvivesRepositoryRecreationCanBeSearchedAndCanBeCleared() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "history-test-${UUID.randomUUID()}.db"
        val conversationId = UUID.randomUUID().toString()
        val userMessage = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, "Remember the launch plan")
        val assistantMessage = ChatMessage(UUID.randomUUID().toString(), ChatRole.ASSISTANT, "The launch plan is saved locally.")
        val first = AndroidHistoryRepository(context, databaseName)
        var reopened: AndroidHistoryRepository? = null
        try {
            assertEquals(ServiceResult.Success(Unit), first.saveMessage(conversationId, userMessage.content, userMessage))
            assertEquals(ServiceResult.Success(Unit), first.saveMessage(conversationId, userMessage.content, assistantMessage))
            first.close()
            val restored = AndroidHistoryRepository(context, databaseName)
            reopened = restored
            assertEquals(listOf(userMessage, assistantMessage), restored.messages(conversationId))
            val matches = restored.search("launch plan").first()
            assertEquals(1, matches.size)
            assertEquals(userMessage.content, matches.single().title)
            assertTrue(matches.single().preview.contains("saved locally"))
            assertEquals(ServiceResult.Success(Unit), restored.clear())
            assertTrue(restored.recentEntries.first().isEmpty())
            assertTrue(restored.messages(conversationId).isEmpty())
        } finally {
            first.close()
            reopened?.close()
            context.deleteDatabase(databaseName)
        }
    }
}
