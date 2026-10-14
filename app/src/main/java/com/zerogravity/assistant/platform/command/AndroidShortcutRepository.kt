package com.zerogravity.assistant.platform.command

import android.content.Context
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.LocalShortcut
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.port.ShortcutRepository
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AndroidShortcutRepository(context: Context) : ShortcutRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(readStoredShortcuts())
    private val mutex = Mutex()
    override val shortcuts: Flow<List<LocalShortcut>> = state

    override suspend fun save(shortcut: LocalShortcut): ServiceResult<Unit> = mutex.withLock {
        val name = shortcut.name.trim()
        if (!NAME_PATTERN.matches(name) || shortcut.command.isBlank() ||
            shortcut.command.length > MAX_COMMAND_LENGTH
        ) {
            return@withLock ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        val existing = state.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing == null && state.value.size >= MAX_SHORTCUTS) {
            return@withLock ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        val updated = state.value.filterNot { it.name.equals(name, ignoreCase = true) } +
            shortcut.copy(name = name)
        if (!persist(updated)) return@withLock ServiceResult.Failure(FailureReason.IO_ERROR)
        state.value = updated
        ServiceResult.Success(Unit)
    }

    override suspend fun find(name: String): LocalShortcut? =
        state.value.firstOrNull { it.name.lowercase(Locale.ROOT) == name.lowercase(Locale.ROOT) }

    override suspend fun delete(name: String): ServiceResult<Unit> = mutex.withLock {
        val updated = state.value.filterNot { it.name.equals(name, ignoreCase = true) }
        if (updated.size == state.value.size) {
            return@withLock ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        if (!persist(updated)) return@withLock ServiceResult.Failure(FailureReason.IO_ERROR)
        state.value = updated
        ServiceResult.Success(Unit)
    }

    private suspend fun persist(shortcuts: List<LocalShortcut>): Boolean = withContext(Dispatchers.IO) {
        val json = JSONArray()
        shortcuts.forEach { shortcut ->
            json.put(JSONObject().put("name", shortcut.name).put("command", shortcut.command))
        }
        preferences.edit().putString(KEY_SHORTCUTS, json.toString()).commit()
    }

    private fun readStoredShortcuts(): List<LocalShortcut> {
        return try {
            val value = preferences.getString(KEY_SHORTCUTS, null) ?: return emptyList()
            val json = JSONArray(value)
            require(json.length() <= MAX_SHORTCUTS) { "Too many stored shortcuts" }
            val shortcuts = List(json.length()) { index ->
                val item = json.getJSONObject(index)
                LocalShortcut(item.getString("name"), item.getString("command")).also { shortcut ->
                    require(NAME_PATTERN.matches(shortcut.name))
                    require(shortcut.command.isNotBlank() && shortcut.command.length <= MAX_COMMAND_LENGTH)
                }
            }
            require(shortcuts.map { it.name.lowercase(Locale.ROOT) }.distinct().size == shortcuts.size)
            shortcuts
        } catch (_: JSONException) {
            android.util.Log.e(TAG, "Stored shortcut data is malformed; shortcuts were not loaded.")
            emptyList()
        } catch (_: IllegalArgumentException) {
            android.util.Log.e(TAG, "Stored shortcut data failed validation; shortcuts were not loaded.")
            emptyList()
        } catch (_: ClassCastException) {
            android.util.Log.e(TAG, "Stored shortcut data has an invalid type; shortcuts were not loaded.")
            emptyList()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "local_shortcuts"
        const val KEY_SHORTCUTS = "shortcuts"
        const val TAG = "ZeroGravity"
        const val MAX_SHORTCUTS = 100
        const val MAX_COMMAND_LENGTH = 200
        val NAME_PATTERN = Regex("[\\p{L}][\\p{L}\\p{N}_ -]{0,31}")
    }
}
