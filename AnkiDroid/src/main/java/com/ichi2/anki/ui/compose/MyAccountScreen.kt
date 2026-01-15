/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.compose

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.LoginError
import com.ichi2.anki.MyAccountScreenState
import com.ichi2.anki.MyAccountViewModel
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.LoginErrorCard
import com.ichi2.anki.ui.compose.components.RoundedPolygonShape
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SoftBurstShape = RoundedPolygonShape(MaterialShapes.SoftBurst)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyAccountScreen(
    viewModel: MyAccountViewModel,
    onBack: () -> Unit,
    onLoginClick: (String, String) -> Unit,
    onResetPasswordClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onLostEmailClick: () -> Unit,
    onRemoveAccountClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onBackPressedCallback: androidx.activity.OnBackPressedCallback? = null,
    showSignUp: Boolean = true,
    showNoAccountText: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Update back button callback enabled state based on screen state
    LaunchedEffect(state.screenState) {
        onBackPressedCallback?.isEnabled = state.screenState == MyAccountScreenState.REMOVE_ACCOUNT
    }


    when (state.screenState) {
        MyAccountScreenState.ACCOUNT_MANAGEMENT -> {
            Scaffold(
                topBar = {
                    TopAppBar(title = {
                        Text(
                            text = stringResource(if (state.isLoggedIn) R.string.menu_my_account else R.string.log_in),
                            style = MaterialTheme.typography.displayMediumEmphasized,
                        )
                    }, navigationIcon = {
                        FilledIconButton(
                            modifier = Modifier.padding(end = 8.dp),
                            onClick = onBack,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    })
                },
            ) { padding ->
                when (state.isLoggedIn) {
                    true -> LoggedInContent(
                        modifier = Modifier.padding(padding),
                        username = state.username ?: "",
                        onLogoutClick = onLogoutClick,
                        onRemoveAccountClick = onRemoveAccountClick,
                        onPrivacyPolicyClick = onPrivacyPolicyClick,
                    )

                    false -> {
                        var password by remember { mutableStateOf("") }
                        LoggedOutContent(
                            modifier = Modifier.padding(padding),
                            email = state.email,
                            password = password,
                            isLoading = state.isLoginLoading,
                            onEmailChanged = viewModel::onEmailChanged,
                            onPasswordChanged = { password = it },
                            onLoginClick = { onLoginClick(state.email, password) },
                            onResetPasswordClick = onResetPasswordClick,
                            onSignUpClick = onSignUpClick,
                            onPrivacyPolicyClick = onPrivacyPolicyClick,
                            onLostEmailClick = onLostEmailClick,
                            showSignUp = showSignUp,
                            showNoAccountText = showNoAccountText,
                            loginError = state.loginError,
                        )
                    }
                }
            }

            // Show login progress dialog
            if (state.isLoginLoading) {
                LoginProgressDialog(onCancel = viewModel::cancelLogin)
            }
        }

        MyAccountScreenState.REMOVE_ACCOUNT -> {
            RemoveAccountContent(
                onBack = { viewModel.setScreenState(MyAccountScreenState.ACCOUNT_MANAGEMENT) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemoveAccountContent(onBack: () -> Unit) {
    val removeAccountUrl = stringResource(R.string.remove_account_url)

    // Redirect logic from RemoveAccountFragment
    val urlsToRedirect = listOf(
        "https://ankiweb.net/account/login?afterAuth=1",
        "https://ankiweb.net/decks",
        "https://ankiweb.net/account/verify-email",
    )

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    text = stringResource(R.string.remove_account),
                    style = MaterialTheme.typography.displayMediumEmphasized,
                )
            }, navigationIcon = {
                FilledIconButton(
                    modifier = Modifier.padding(end = 8.dp),
                    onClick = onBack,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back_24px),
                        contentDescription = stringResource(R.string.back),
                    )
                }
            })
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            RemoveAccountWebView(
                removeAccountUrl,
                urlsToRedirect,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RemoveAccountWebView(
    removeAccountUrl: String,
    urlsToRedirect: List<String>,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.displayZoomControls = false
                settings.builtInZoomControls = true
                settings.setSupportZoom(true)

                // Security hardening as requested
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                removeJavascriptInterface("searchBoxJavaBridge_")
                removeJavascriptInterface("accessibility")
                removeJavascriptInterface("accessibilityTraversal")

                var redirectCount = 0

                webViewClient = object : WebViewClient() {
                    private fun isUrlAllowed(url: String?): Boolean {
                        if (url == null) return false
                        val uri = url.toUri()
                        val host = uri.host ?: return false
                        return host == "ankiweb.net" || host.endsWith(".ankiweb.net")
                    }

                    private fun maybeRedirect(url: String?): Boolean {
                        if (url == null) return false
                        if (urlsToRedirect.any { url.startsWith(it) }) {
                            redirectCount++
                            if (redirectCount <= 3) {
                                loadUrl(removeAccountUrl)
                                return true
                            }
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        if (!isUrlAllowed(request?.url?.toString())) {
                            return WebResourceResponse("text/plain", "utf-8", null)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?,
                    ) {
                        handler?.cancel()
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString()
                        if (maybeRedirect(url)) return true

                        return !isUrlAllowed(url)
                    }

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        super.onPageFinished(view, url)
                        maybeRedirect(url)
                    }
                }
                loadUrl(removeAccountUrl)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun LoggedOutContent(
    modifier: Modifier = Modifier,
    email: String,
    password: String,
    isLoading: Boolean,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
    onResetPasswordClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onLostEmailClick: () -> Unit,
    showSignUp: Boolean,
    showNoAccountText: Boolean,
    loginError: LoginError? = null,
) {
    val passwordFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loginError != null) {
                LoginErrorCard(
                    error = loginError,
                    onResetPasswordClick = onResetPasswordClick,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChanged,
                label = { Text(stringResource(R.string.username)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.mail_24px),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() },
                ),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChanged,
                label = { Text(stringResource(R.string.password)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.lock_24px),
                        contentDescription = null,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (email.isNotEmpty() && password.isNotEmpty() && !isLoading) {
                            onLoginClick()
                        }
                    },
                ),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotEmpty() && password.isNotEmpty() && !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.log_in))
                }
            }

            TextButton(onClick = onResetPasswordClick) {
                Text(stringResource(R.string.reset_password))
            }


            Spacer(modifier = Modifier.height(24.dp))

            if (showNoAccountText) {
                Text(
                    text = stringResource(R.string.sign_up_description),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.ankiweb_is_not_affiliated_with_this_app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (showSignUp) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSignUpClick) {
                    Text(stringResource(R.string.sign_up))
                }
            }

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = onPrivacyPolicyClick) {
                Text(stringResource(R.string.help_title_privacy))
            }

            TextButton(onClick = onLostEmailClick) {
                Text(stringResource(R.string.lost_mail_instructions))
            }
        }
    }
}

@Composable
fun LoggedInContent(
    modifier: Modifier = Modifier,
    username: String,
    onLogoutClick: () -> Unit,
    onRemoveAccountClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AccountIconRotation")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
        ),
        label = "AccountIconRotationAngle",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 64.dp, bottom = 24.dp)
                    .size(124.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotation
                        }
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = SoftBurstShape,
                        ),
                )
                Image(
                    modifier = Modifier.size(60.dp),
                    painter = painterResource(R.drawable.link_24px),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.logged_as),
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = username,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.log_out))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRemoveAccountClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.remove_account))
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onPrivacyPolicyClick) {
                Text(stringResource(R.string.help_title_privacy))
            }
        }
    }
}

@Preview
@Composable
private fun LoggedOutContentPreview() {
    AnkiDroidTheme {
        LoggedOutContent(
            email = "test@example.com",
            password = "password",
            isLoading = false,
            onEmailChanged = {},
            onPasswordChanged = {},
            onLoginClick = {},
            onResetPasswordClick = {},
            onSignUpClick = {},
            onPrivacyPolicyClick = {},
            onLostEmailClick = {},
            showSignUp = true,
            showNoAccountText = true,
        )
    }
}

@Preview
@Composable
private fun LoggedOutContentLoadingPreview() {
    AnkiDroidTheme {
        LoggedOutContent(
            email = "test@example.com",
            password = "password",
            isLoading = true,
            onEmailChanged = {},
            onPasswordChanged = {},
            onLoginClick = {},
            onResetPasswordClick = {},
            onSignUpClick = {},
            onPrivacyPolicyClick = {},
            onLostEmailClick = {},
            showSignUp = true,
            showNoAccountText = true,
        )
    }
}

@Preview
@Composable
private fun LoggedInContentPreview() {
    AnkiDroidTheme {
        LoggedInContent(
            username = "test@example.com",
            onLogoutClick = {},
            onRemoveAccountClick = {},
            onPrivacyPolicyClick = { },
        )
    }
}
