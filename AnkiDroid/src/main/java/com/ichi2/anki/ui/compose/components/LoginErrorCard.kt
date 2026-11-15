// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.LoginError
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * A prominent error card for displaying authentication errors.
 * Features an icon, descriptive error message, and an optional action button.
 */
@Composable
fun LoginErrorCard(
    error: LoginError,
    onResetPasswordClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                ).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.error_24px),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.login_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text =
                        when (error) {
                            is LoginError.StringResource -> stringResource(error.resId)
                            is LoginError.DynamicString -> error.text
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        // Show reset password button for authentication errors
        if (error.requiresPasswordReset) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onResetPasswordClick) {
                    Text(
                        text = stringResource(R.string.reset_password),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun LoginErrorCardAuthPreview() {
    AnkiDroidTheme {
        LoginErrorCard(
            error =
                LoginError.StringResource(
                    resId = R.string.login_error_authentication_failed,
                    requiresPasswordReset = true,
                ),
            onResetPasswordClick = {},
        )
    }
}

@Preview
@Composable
private fun LoginErrorCardNetworkPreview() {
    AnkiDroidTheme {
        LoginErrorCard(
            error = LoginError.StringResource(R.string.login_error_network),
            onResetPasswordClick = {},
        )
    }
}

@Preview
@Composable
private fun LoginErrorCardDynamicPreview() {
    AnkiDroidTheme {
        LoginErrorCard(
            error = LoginError.DynamicString("Server responded with an unexpected error code: 500"),
            onResetPasswordClick = {},
        )
    }
}
