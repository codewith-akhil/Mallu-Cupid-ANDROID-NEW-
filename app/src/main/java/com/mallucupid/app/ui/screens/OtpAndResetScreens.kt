package com.mallucupid.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mallucupid.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ResetPasswordScreen(
    onSendOtp: (String) -> Unit,
    onBackToSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    AuthBackground {
        if (loading) {
            LoadingOverlay(message = "Sending OTP...")
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxHeight < 620.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = 28.dp,
                        vertical = if (isCompact) 12.dp else 24.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AuthLogo(
                    size = if (isCompact) 48.dp else 90.dp,
                    bottomSpacing = if (isCompact) 8.dp else 24.dp
                )

                Text(
                    text = "Reset Password",
                    color = Color.White,
                    fontSize = if (isCompact) 22.sp else 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

                Text(
                    text = "Enter your email to receive OTP",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = if (isCompact) 13.sp else 15.sp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 36.dp))

                AuthTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Email address",
                    keyboardType = KeyboardType.Email
                )

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 24.dp))

                AuthButton(
                    text = "Send OTP",
                    onClick = {
                        if (email.isNotBlank()) {
                            loading = true
                        }
                    },
                    height = if (isCompact) 50.dp else 56.dp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 32.dp))

                TextButton(onClick = onBackToSignIn) {
                    Text(
                        text = "Back to Sign In",
                        color = SoftPink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (isCompact) 14.sp else 15.sp
                    )
                }
            }
        }
    }

    // Simulate success after delay
    LaunchedEffect(loading) {
        if (loading) {
            delay(1500)
            loading = false
            onSendOtp(email)
        }
    }
}

@Composable
fun OtpVerificationScreen(
    email: String,
    onVerified: () -> Unit,
    onBack: () -> Unit
) {
    var otp by remember { mutableStateOf(List(6) { "" }) }
    var timer by remember { mutableStateOf(60) }
    var loading by remember { mutableStateOf(false) }
    val focusRequesters = List(6) { remember { FocusRequester() } }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        while (timer > 0) {
            delay(1000)
            timer--
        }
    }

    AuthBackground {
        if (loading) {
            LoadingOverlay(message = "Verifying OTP...")
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxHeight < 620.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = 24.dp,
                        vertical = if (isCompact) 12.dp else 24.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AuthLogo(
                    size = if (isCompact) 48.dp else 90.dp,
                    bottomSpacing = if (isCompact) 8.dp else 24.dp
                )

                Text(
                    text = "Verify Email",
                    color = Color.White,
                    fontSize = if (isCompact) 22.sp else 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

                Text(
                    text = "Enter the 6-digit code sent to\n$email",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = if (isCompact) 13.sp else 15.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 36.dp))

                // OTP boxes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    otp.forEachIndexed { index, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newValue ->
                                if (newValue.length <= 1 && newValue.all { it.isDigit() }) {
                                    val newOtp = otp.toMutableList()
                                    newOtp[index] = newValue
                                    otp = newOtp

                                    if (newValue.isNotEmpty() && index < 5) {
                                        focusRequesters[index + 1].requestFocus()
                                    }
                                    if (newValue.isEmpty() && index > 0) {
                                        focusRequesters[index - 1].requestFocus()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(if (isCompact) 50.dp else 56.dp)
                                .focusRequester(focusRequesters[index]),
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Center,
                                fontSize = if (isCompact) 18.sp else 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                                focusedBorderColor = AccentPink,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = AccentPink
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 28.dp))

                AuthButton(
                    text = "Verify OTP",
                    onClick = {
                        if (otp.joinToString("").length == 6) {
                            loading = true
                        }
                    },
                    height = if (isCompact) 50.dp else 56.dp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 24.dp))

                if (timer > 0) {
                    Text(
                        text = "Resend OTP in ${timer}s",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = if (isCompact) 13.sp else 14.sp
                    )
                } else {
                    TextButton(
                        onClick = { timer = 60 },
                        contentPadding = if (isCompact) PaddingValues(horizontal = 8.dp, vertical = 2.dp) else ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(
                            text = "Resend OTP",
                            color = SoftPink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (isCompact) 13.sp else 14.sp
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(loading) {
        if (loading) {
            delay(1500)
            loading = false
            onVerified()
        }
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentPink)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, color = Color.White)
        }
    }
}
