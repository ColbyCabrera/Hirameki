package com.ichi2.anki

interface SnackbarForwarder {
    fun forwardSnackbar(message: String, actionLabel: String? = null, action: (() -> Unit)? = null)
}

data class SnackbarMessageEvent(
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)
