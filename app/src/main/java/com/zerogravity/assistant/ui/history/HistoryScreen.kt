package com.zerogravity.assistant.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zerogravity.assistant.domain.model.HistoryEntry
import com.zerogravity.assistant.domain.port.HistoryRepository
import com.zerogravity.assistant.ui.navigation.horizontalPageSwipe
import com.zerogravity.assistant.ui.settings.PageHeading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    repository: HistoryRepository?,
    onOpenConversation: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
) {
    var retryToken by remember { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val historyState by produceState<HistoryState>(
        initialValue = if (repository == null) HistoryState.NotConnected else HistoryState.Loading,
        key1 = repository,
        key2 = retryToken,
        key3 = searchQuery,
    ) {
        if (repository == null) {
            value = HistoryState.NotConnected
            return@produceState
        }
        value = HistoryState.Loading
        try {
            repository.search(searchQuery).collect { entries ->
                value = if (entries.isEmpty()) HistoryState.Empty else HistoryState.Entries(entries)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            value = HistoryState.Error
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().horizontalPageSwipe(onSwipeLeft, onSwipeRight),
        ) {
            PageHeading("History", "App-private SQLite history; not separately encrypted.")
        }
        if (repository != null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                label = { Text("Search history") },
                placeholder = { Text("Search titles and messages") },
                singleLine = true,
            )
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 8.dp),
            ) { Text("Clear history") }
        }
        when (val state = historyState) {
            HistoryState.NotConnected -> HistoryMessage("History unavailable", "Local conversation storage is not connected in this build.")
            HistoryState.Loading -> HistoryMessage("Loading history", "Reading saved conversations from this device…")
            HistoryState.Empty -> HistoryMessage(
                title = if (searchQuery.isBlank()) "A fresh orbit" else "No matches",
                detail = if (searchQuery.isBlank()) {
                    "No saved conversations yet. Start a chat and it will appear here."
                } else {
                    "No saved conversation contains “${searchQuery.trim()}”. Try another phrase."
                },
            )
            HistoryState.Error -> Column {
                HistoryMessage("History could not be loaded", "Local storage reported an error. Your saved conversations have not been changed.")
                OutlinedButton(onClick = { retryToken++ }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Retry") }
            }
            is HistoryState.Entries -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.entries, key = HistoryEntry::id) { entry -> HistoryEntryCard(entry) { onOpenConversation(entry.id) } }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all local history?") },
            text = { Text("This removes saved conversations from this app. An Android system backup may retain a copy, depending on device settings.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearHistory() }) { Text("Clear history") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryMessage(title: String, detail: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: HistoryEntry, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall)
            if (entry.preview.isNotBlank()) {
                Text(entry.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text(
                DateTimeFormatter.ofPattern("MMM d · HH:mm").withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(entry.updatedAtEpochMillis)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface HistoryState {
    data object NotConnected : HistoryState
    data object Loading : HistoryState
    data object Empty : HistoryState
    data object Error : HistoryState
    data class Entries(val entries: List<HistoryEntry>) : HistoryState
}
