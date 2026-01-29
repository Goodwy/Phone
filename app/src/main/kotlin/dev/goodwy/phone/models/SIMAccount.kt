package dev.goodwy.phone.models

import android.telecom.PhoneAccountHandle

data class SIMAccount(
    val id: Int,
    val handle: PhoneAccountHandle,
    val label: String,
    val phoneNumber: String,
    val color: Int,
)
