package com.ichi2.anki

interface SnackbarForwarder {
    fun forwardSnackbar(message: String, action: (() -> Unit)? = null)
}

data class SnackbarMessageEvent(val message: String, val action: (() -> Unit)? = null)
