package com.zerogravity.assistant.domain.model

sealed interface PhoneAction {
    data class PlaceCall(val phoneNumber: String) : PhoneAction

    data class SendTextMessage(
        val phoneNumber: String,
        val message: String,
    ) : PhoneAction
}