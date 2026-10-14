package com.zerogravity.assistant.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedButton
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
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
) {
    var retryToken by remember { mutableIntStateOf(0) }
    val historyState by produceState<HistoryState>(
        initialValue = if (repository == null) HistoryState.NotConnected else HistoryState.Loading,
        key1 = repository,
        key2 = retryToken,
    ) {
        if (repository == null) {
            value = HistoryState.NotConnected
            return@produceState
        }
        value = HistoryState.Loading
        try {
            repository.recentEntries.collect { entries ->
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
            modifier = Modifier
                .fillMaxWidth()
                .horizontalPageSwipe(onSwipeLeft, onSwipeRight),
        ) {
            PageHeading("History", "Your recent conversations, when enabled.")
        }
        when (val state = historyState) {
            HistoryState.NotConnected -> HistoryMessage(
                title = "Conversation history is planned",
                detail = "The history storage provider is not connected. This build does not save conversation transcripts.",
            )
            HistoryState.Loading -> HistoryMessage(
                title = "Loading history",
                detail = "Reading recent entries from this device…",
            )
            HistoryState.Empty -> HistoryMessage(
                title = "A fresh orbit",
                detail = "No saved conversations yet. A local history provider can be connected here in a future batch.",
            )
            HistoryState.Error -> Column {
                HistoryMessage(
                    title = "History could not be loaded",
                    detail = "The history provider reported an error. Your saved entries have not been changed.",
                )
                OutlinedButton(
                    onClick = { retryToken++ },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Retry")
                }
            }
            is HistoryState.Entries -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.entries, key = HistoryEntry::id) { entry ->
                    HistoryEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryMessage(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryEntryCard(entry: HistoryEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall)
            Text(
                DateTimeFormatter.ofPattern("MMM d · HH:mm")
                    .withZone(ZoneId.systemDefault())
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
