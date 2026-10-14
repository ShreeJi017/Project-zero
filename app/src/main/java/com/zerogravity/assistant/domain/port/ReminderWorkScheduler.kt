package com.zerogravity.assistant.domain.port

import com.zerogravity.assistant.domain.model.Reminder
import com.zerogravity.assistant.domain.model.ServiceResult

interface ReminderWorkScheduler {
    suspend fun schedule(reminder: Reminder): ServiceResult<Unit>

    suspend fun cancel(reminderId: String): ServiceResult<Unit>
}