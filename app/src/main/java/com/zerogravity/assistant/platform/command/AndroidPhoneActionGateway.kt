package com.zerogravity.assistant.platform.command

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.zerogravity.assistant.domain.model.FailureReason
import com.zerogravity.assistant.domain.model.PhoneAction
import com.zerogravity.assistant.domain.model.ServiceResult
import com.zerogravity.assistant.domain.port.PhoneActionGateway

class AndroidPhoneActionGateway(context: Context) : PhoneActionGateway {
    private val appContext = context.applicationContext

    override suspend fun execute(action: PhoneAction): ServiceResult<String> {
        val phoneNumber = when (action) {
            is PhoneAction.PlaceCall -> action.phoneNumber
            is PhoneAction.SendTextMessage -> action.phoneNumber
        }
        if (!NORMALIZED_PHONE_NUMBER.matches(phoneNumber)) {
            return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        if (action is PhoneAction.SendTextMessage &&
            (action.message.isBlank() || action.message.length > MAX_MESSAGE_LENGTH)
        ) {
            return ServiceResult.Failure(FailureReason.INVALID_INPUT)
        }
        val intent = when (action) {
            is PhoneAction.PlaceCall -> Intent(
                Intent.ACTION_DIAL,
                Uri.fromParts("tel", action.phoneNumber, null),
            )
            is PhoneAction.SendTextMessage -> Intent(
                Intent.ACTION_SENDTO,
                Uri.fromParts("smsto", action.phoneNumber, null),
            ).putExtra("sms_body", action.message)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            if (intent.resolveActivity(appContext.packageManager) == null) {
                return ServiceResult.Failure(FailureReason.NOT_AVAILABLE)
            }
            appContext.startActivity(intent)
            ServiceResult.Success(
                when (action) {
                    is PhoneAction.PlaceCall ->
                        "Dialer opened. Review the number and tap Call to confirm; no call was placed by ZeroGravity."
                    is PhoneAction.SendTextMessage ->
                        "SMS composer opened. Review the message and tap Send to confirm; no message was sent by ZeroGravity."
                },
            )
        } catch (exception: SecurityException) {
            ServiceResult.Failure(FailureReason.PERMISSION_DENIED, exception)
        } catch (exception: ActivityNotFoundException) {
            ServiceResult.Failure(FailureReason.NOT_AVAILABLE, exception)
        }
    }

    private companion object {
        val NORMALIZED_PHONE_NUMBER = Regex("\\+?[0-9]{3,20}")
        const val MAX_MESSAGE_LENGTH = 2_000
    }
}
