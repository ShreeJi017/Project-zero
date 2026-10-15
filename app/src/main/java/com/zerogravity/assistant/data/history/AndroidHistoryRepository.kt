package com.zerogravity.assistant.data.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.zerogravity.assistant.domain.model.ChatMessage
import com.zerogravity.assistant.domain.model.ChatRole
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.HistoryEntry
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.port.HistoryRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AndroidHistoryRepository(
    context: Context,
    databaseName: String = DATABASE_NAME,
) : HistoryRepository {
    private val helper = HistoryDatabase(context.applicationContext, databaseName)
    private val revisions = MutableStateFlow(0L)

    override val recentEntries: Flow<List<HistoryEntry>> = revisions.map {
        withContext(Dispatchers.IO) { readEntries(null) }
    }

    override fun search(query: String): Flow<List<HistoryEntry>> = revisions.map {
        withContext(Dispatchers.IO) { readEntries(query) }
    }

    override suspend fun messages(conversationId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        require(conversationId.isNotBlank())
        val cursor = helper.readableDatabase.rawQuery(
            "SELECT id, role, content FROM messages WHERE conversation_id = ? ORDER BY created_at ASC, rowid ASC",
            arrayOf(conversationId),
        )
        cursor.use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    val role = when (rows.getString(1)) {
                        ChatRole.USER.name -> ChatRole.USER
                        ChatRole.ASSISTANT.name -> ChatRole.ASSISTANT
                        else -> continue
                    }
                    add(ChatMessage(rows.getString(0), role, rows.getString(2)))
                }
            }
        }
    }

    override suspend fun saveMessage(
        conversationId: String,
        title: String,
        message: ChatMessage,
    ): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        val safeTitle = title.trim().replace(WHITESPACE, " ").take(MAX_TITLE_LENGTH)
        if (conversationId.isBlank() || conversationId.length > MAX_ID_LENGTH ||
            message.id.isBlank() || message.content.isBlank() || message.content.length > MAX_MESSAGE_LENGTH || safeTitle.isBlank()
        ) return@withContext ServiceResult.Failure(FailureReason.INVALID_INPUT)
        try {
            val database = helper.writableDatabase
            val now = System.currentTimeMillis()
            database.beginTransaction()
            try {
                val conversation = ContentValues().apply {
                    put("id", conversationId); put("title", safeTitle); put("created_at", now)
                    put("updated_at", now); put("preview", message.content.trim().take(MAX_PREVIEW_LENGTH))
                }
                database.insertWithOnConflict(TABLE_CONVERSATIONS, null, conversation, SQLiteDatabase.CONFLICT_IGNORE)
                val savedMessage = ContentValues().apply {
                    put("id", message.id); put("conversation_id", conversationId)
                    put("role", message.role.name); put("content", message.content); put("created_at", now)
                }
                database.insertOrThrow(TABLE_MESSAGES, null, savedMessage)
                database.execSQL(
                    "UPDATE $TABLE_CONVERSATIONS SET updated_at = ?, preview = ? WHERE id = ?",
                    arrayOf<Any>(now, message.content.trim().take(MAX_PREVIEW_LENGTH), conversationId),
                )
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            revisions.value += 1
            ServiceResult.Success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        }
    }

    override suspend fun clear(): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                database.delete(TABLE_MESSAGES, null, null)
                database.delete(TABLE_CONVERSATIONS, null, null)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            revisions.value += 1
            ServiceResult.Success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        }
    }

    /** Test hook and lifecycle convenience; the application keeps this repository for its process lifetime. */
    fun close() = helper.close()

    private fun readEntries(query: String?): List<HistoryEntry> {
        val database = helper.readableDatabase
        val normalizedQuery = query?.trim()?.take(MAX_SEARCH_LENGTH).orEmpty()
        val cursor = if (normalizedQuery.isBlank()) {
            database.rawQuery(RECENT_CONVERSATIONS_SQL, null)
        } else {
            val escaped = normalizedQuery.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val pattern = "%$escaped%"
            database.rawQuery(SEARCH_CONVERSATIONS_SQL, arrayOf(pattern, pattern, pattern))
        }
        return cursor.use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    add(HistoryEntry(rows.getString(0), rows.getString(1), rows.getLong(2), rows.getString(3)))
                }
            }
        }
    }

    private class HistoryDatabase(context: Context, name: String) : SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {
        override fun onConfigure(database: SQLiteDatabase) { database.setForeignKeyConstraintsEnabled(true) }

        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """CREATE TABLE $TABLE_CONVERSATIONS (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    preview TEXT NOT NULL
                )""".trimIndent(),
            )
            database.execSQL(
                """CREATE TABLE $TABLE_MESSAGES (
                    id TEXT PRIMARY KEY NOT NULL,
                    conversation_id TEXT NOT NULL REFERENCES $TABLE_CONVERSATIONS(id) ON DELETE CASCADE,
                    role TEXT NOT NULL CHECK(role IN ('USER', 'ASSISTANT')),
                    content TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )""".trimIndent(),
            )
            database.execSQL("CREATE INDEX history_messages_conversation_time ON $TABLE_MESSAGES(conversation_id, created_at)")
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw IllegalStateException("No history database migration is defined for $oldVersion to $newVersion")
        }
    }

    private companion object {
        const val DATABASE_NAME = "zerogravity_history.db"
        const val DATABASE_VERSION = 1
        const val TABLE_CONVERSATIONS = "conversations"
        const val TABLE_MESSAGES = "messages"
        const val MAX_ID_LENGTH = 128
        const val MAX_TITLE_LENGTH = 80
        const val MAX_PREVIEW_LENGTH = 240
        const val MAX_MESSAGE_LENGTH = 8_000
        const val MAX_SEARCH_LENGTH = 100
        val WHITESPACE = Regex("\\s+")
        const val RECENT_CONVERSATIONS_SQL = "SELECT id, title, updated_at, preview FROM $TABLE_CONVERSATIONS ORDER BY updated_at DESC, rowid DESC"
        val SEARCH_CONVERSATIONS_SQL = """
            SELECT c.id, c.title, c.updated_at, c.preview
            FROM $TABLE_CONVERSATIONS c
            WHERE c.title LIKE ? ESCAPE '\'
                OR c.preview LIKE ? ESCAPE '\'
                OR EXISTS (
                    SELECT 1 FROM $TABLE_MESSAGES m
                    WHERE m.conversation_id = c.id AND m.content LIKE ? ESCAPE '\'
                )
            ORDER BY c.updated_at DESC, c.rowid DESC
        """.trimIndent()
    }
}
