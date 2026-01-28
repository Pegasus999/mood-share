package com.moodshare.core

data class Profile(
    val name: String,
    val avatar: String,
    val mood: String,
    val isFocused: Boolean,
    val partnerId: String? = null,
    val partner: PartnerProfile? = null
)

data class PartnerProfile(
    val name: String,
    val avatar: String,
    val mood: String,
    val isFocused: Boolean
)
