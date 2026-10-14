package com.zerogravity.assistant.platform.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zerogravity.assistant.R
import com.zerogravity.assistant.domain.model.AppPermission
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.Reminder
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.port.PermissionManager
import com.zerogravity.assistant.domain.port.ReminderRepository
import com.zerogravity.assistant.domain.port.ReminderWorkScheduler
import com.zerogravity.assistant.core.permission.AndroidPermissionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AndroidReminderScheduler(context: Context) : ReminderWorkScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override suspend fun schedule(reminder: Reminder): ServiceResult<Unit> {
        if (reminder.scheduledAtEpochMillis <= System.currentTimeMillis()) {
            return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        val operation = pendingIntent(reminder.id, reminder.title)
        return try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.scheduledAtEpochMillis,
                operation,
            )
            ServiceResult.Success(Unit)
        } catch (exception: SecurityException) {
            ServiceResult.Failure(FailureReason.PERMISSION_DENIED, exception)
        } catch (exception: IllegalArgumentException) {
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        }
    }

    override suspend fun cancel(reminderId: String): ServiceResult<Unit> {
        return try {
            alarmManager.cancel(pendingIntent(reminderId, null))
            ServiceResult.Success(Unit)
        } catch (exception: SecurityException) {
            ServiceResult.Failure(FailureReason.PERMISSION_DENIED, exception)
        } catch (exception: IllegalArgumentException) {
            ServiceResult.Failure(FailureReason.IO_ERROR, exception)
        }
    }

    private fun pendingIntent(id: String, title: String?): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java)
            .putExtra(EXTRA_REMINDER_ID, id)
            .setData(Uri.parse("zerogravity://reminder/$id"))
        if (title != null) intent.putExtra(EXTRA_REMINDER_TITLE, title)
        return PendingIntent.getBroadcast(
            appContext,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
    }
}

class AndroidReminderRepository(
    context: Context,
    private val scheduler: ReminderWorkScheduler,
    private val permissionManager: PermissionManager,
) : ReminderRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(readStoredReminders())
    private val mutex = Mutex()
    override val reminders: Flow<List<Reminder>> = state

    override suspend fun save(reminder: Reminder): ServiceResult<Unit> = mutex.withLock {
        if (!permissionManager.isGranted(AppPermission.POST_NOTIFICATIONS)) {
            return@withLock ServiceResult.Failure(FailureReason.PERMISSION_DENIED)
        }
        if (reminder.id.isBlank() || reminder.title.isBlank() ||
            reminder.title.length > Reminder.MAX_TITLE_LENGTH ||
            reminder.scheduledAtEpochMillis <= System.currentTimeMillis()
        ) {
            return@withLock ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        val previous = state.value
        if (previous.none { it.id == reminder.id } && previous.size >= MAX_REMINDERS) {
            return@withLock ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        val updated = previous.filterNot { it.id == reminder.id } + reminder
        persistAndSchedule(updated, reminder, previous)
    }

    override suspend fun delete(reminderId: String): ServiceResult<Unit> = mutex.withLock {
        val existing = state.value.firstOrNull { it.id == reminderId }
            ?: return@withLock ServiceResult.Failure(FailureReason.INVALID_INPUT)
        when (val cancelled = scheduler.cancel(reminderId)) {
            is ServiceResult.Failure -> return@withLock cancelled
            is ServiceResult.Success -> Unit
        }
        val updated = state.value.filterNot { it.id == reminderId }
        if (!persist(updated)) {
            when (val restored = scheduler.schedule(existing)) {
                is ServiceResult.Failure -> return@withLock ServiceResult.Failure(
                    FailureReason.IO_ERROR,
                    restored.cause,
                )
                is ServiceResult.Success -> Unit
            }
            return@withLock ServiceResult.Failure(FailureReason.IO_ERROR)
        }
        state.value = updated
        ServiceResult.Success(Unit)
    }

    private suspend fun persistAndSchedule(
        updated: List<Reminder>,
        reminder: Reminder,
        previous: List<Reminder>,
    ): ServiceResult<Unit> {
        if (!persist(updated)) return ServiceResult.Failure(FailureReason.IO_ERROR)
        return when (val scheduled = scheduler.schedule(reminder)) {
            is ServiceResult.Failure -> {
                if (!persist(previous)) {
                    return ServiceResult.Failure(FailureReason.IO_ERROR, scheduled.cause)
                }
                scheduled
            }
            is ServiceResult.Success -> {
                state.value = updated
                ServiceResult.Success(Unit)
            }
        }
    }

    suspend fun restorePendingSchedules(): List<ServiceResult<Unit>> = mutex.withLock {
        state.value.filter { it.scheduledAtEpochMillis > System.currentTimeMillis() }
            .map { reminder -> scheduler.schedule(reminder) }
    }

    private suspend fun persist(reminders: List<Reminder>): Boolean = withContext(Dispatchers.IO) {
        val json = JSONArray()
        reminders.forEach { reminder ->
            json.put(
                JSONObject()
                    .put("id", reminder.id)
                    .put("title", reminder.title)
                    .put("scheduledAt", reminder.scheduledAtEpochMillis),
            )
        }
        preferences.edit().putString(KEY_REMINDERS, json.toString()).commit()
    }

    private fun readStoredReminders(): List<Reminder> {
        return try {
            val value = preferences.getString(KEY_REMINDERS, null) ?: return emptyList()
            val json = JSONArray(value)
            require(json.length() <= MAX_REMINDERS) { "Too many stored reminders" }
            val reminders = List(json.length()) { index ->
                val item = json.getJSONObject(index)
                Reminder(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    scheduledAtEpochMillis = item.getLong("scheduledAt"),
                ).also { reminder ->
                    require(reminder.id.isNotBlank())
                    require(reminder.title.isNotBlank() && reminder.title.length <= Reminder.MAX_TITLE_LENGTH)
                    require(reminder.scheduledAtEpochMillis > 0L)
                }
            }
            require(reminders.map(Reminder::id).distinct().size == reminders.size)
            reminders
        } catch (_: JSONException) {
            android.util.Log.e(TAG, "Stored reminder data is malformed; reminders were not loaded.")
            emptyList()
        } catch (_: IllegalArgumentException) {
            android.util.Log.e(TAG, "Stored reminder data failed validation; reminders were not loaded.")
            emptyList()
        } catch (_: ClassCastException) {
            android.util.Log.e(TAG, "Stored reminder data has an invalid type; reminders were not loaded.")
            emptyList()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "local_reminders"
        const val KEY_REMINDERS = "reminders"
        const val TAG = "ZeroGravity"
        const val MAX_REMINDERS = 500
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AndroidReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(AndroidReminderScheduler.EXTRA_REMINDER_TITLE) ?: return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_zero_mark)
            .setContentTitle("Reminder")
            .setContentText(title)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_zero_mark)
                    .setContentTitle("Reminder")
                    .setContentText("You have a reminder.")
                    .build(),
            )
            .setAutoCancel(true)
            .build()
        try {
            notificationManager.notify(id, NOTIFICATION_ID, notification)
        } catch (exception: SecurityException) {
            android.util.Log.w(TAG, "Reminder notification permission was revoked", exception)
        }
    }

    private companion object {
        const val CHANNEL_ID = "local_reminders"
        const val NOTIFICATION_ID = 0
        const val TAG = "ZeroGravity"
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val scheduler = AndroidReminderScheduler(appContext)
                val repository = AndroidReminderRepository(
                    appContext,
                    scheduler,
                    AndroidPermissionManager(appContext),
                )
                repository.restorePendingSchedules().forEach { result ->
                    if (result is ServiceResult.Failure) {
                        android.util.Log.e(TAG, "Unable to restore a scheduled reminder", result.cause)
                    }
                }
            } catch (exception: IllegalStateException) {
                android.util.Log.e(TAG, "Unable to restore reminders after reboot", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ZeroGravity"
    }
}
