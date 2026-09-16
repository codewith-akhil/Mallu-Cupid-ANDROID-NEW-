package com.mallucupid.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mallucupid.app.ui.theme.*
import com.mallucupid.app.data.remote.SupabaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Email regex — mirrors the private helper in AuthScreens so ResetPasswordScreen
// can validate without depending on that file's private API.
private val resetEmailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")

private fun isValidResetEmail(email: String): Boolean = resetEmailRegex.matches(email)


@Composable
fun ResetPasswordScreen(
    onSendOtp: (String) -> Unit,
    onBackToSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var emailErrorText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    AuthBackground {
        if (loading) {
            LoadingOverlay(message = "Sending code...")
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
                    onValueChange = { if (loading) loading = false
                        email = it
                        if (emailError) {
                            emailError = false
                            emailErrorText = null
                        }
                    },
                    placeholder = "Email address",
                    keyboardType = KeyboardType.Email,
                    isError = emailError,
                    errorText = emailErrorText
                )

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 24.dp))

                AuthButton(
                    text = "Send OTP",
                    onClick = {
                        emailError = !isValidResetEmail(email)
                        emailErrorText = if (emailError) "Enter a valid email address" else null
                        if (!emailError) {
                            loading = true
                            onSendOtp(email)
                        }
                    },
                    enabled = !loading,
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
}


@Composable
fun OtpVerificationScreen(
    email: String,
    otpContext: String,
    onVerified: () -> Unit,
    onBack: () -> Unit
) {
    var otp by remember { mutableStateOf(List(6) { "" }) }
    var resendSeconds by remember { mutableStateOf(60) }
    var resendTrigger by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var resending by remember { mutableStateOf(false) }
    val focusRequesters = List(6) { remember { FocusRequester() } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 60-second resend countdown — restarts whenever resendTrigger changes.
    LaunchedEffect(resendTrigger) {
        while (resendSeconds > 0) {
            delay(1000)
            resendSeconds--
        }
    }

    // Auto-advance: when all 6 boxes are filled, trigger verify after a short 200ms delay.
    val allOtpFilled = otp.all { it.isNotEmpty() }
    LaunchedEffect(allOtpFilled) {
        if (allOtpFilled && !loading) {
            delay(200)
            // Re-check in case the user cleared a box during the delay window.
            if (otp.all { it.isNotEmpty() }) {
                loading = true
            }
        }
    }

    AuthBackground {
        if (loading) {
            LoadingOverlay(message = "Verifying...")
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

                // OTP boxes — 6 individual digit boxes, auto-advance on entry,
                // centered horizontally, each ~52.dp wide, spacedBy 12.dp.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    otp.forEachIndexed { index, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newValue ->
                                // Accept at most one digit (empty allowed for delete/backspace).
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
                                .width(52.dp)
                                .height(if (isCompact) 50.dp else 56.dp)
                                .focusRequester(focusRequesters[index]),
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Center,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = DashboardCream
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
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
                    enabled = !loading,
                    height = if (isCompact) 50.dp else 56.dp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 24.dp))

                // Resend code button — disabled while countdown > 0, while resending,
                // or while a verification is in flight.
                TextButton(
                    onClick = {
                        scope.launch {
                            resending = true
                            val err = SupabaseAuth.sendOtp(email)
                            resending = false
                            if (err != null) {
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            otp = List(6) { "" }
                            resendSeconds = 60
                            resendTrigger++
                            Toast.makeText(context, "Code resent", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = resendSeconds == 0 && !resending && !loading,
                    contentPadding = if (isCompact) PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                     else ButtonDefaults.TextButtonContentPadding
                ) {
                    Text(
                        text = if (resendSeconds == 0) "Resend code"
                               else "Resend code in 0:${resendSeconds.toString().padStart(2, '0')}",
                        color = if (resendSeconds == 0) AccentPink else DashboardNavMuted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (isCompact) 13.sp else 14.sp
                    )
                }
            }
        }
    }

    // Verify flow — SupabaseAuth.verifyOtp returns null on success, or a
    // user-friendly error message. On success we toast a context-aware message
    // (signup vs reset) and then call onVerified().
    LaunchedEffect(loading) {
        if (loading) {
            val code = otp.joinToString("")
            val err = SupabaseAuth.verifyOtp(email, code)
            loading = false
            if (err == null) {
                val msg = if (otpContext == "signup")
                    "Email verified! Setting up your profile..."
                else
                    "Email verified! Set your new password."
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onVerified()
            } else {
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                // Clear the boxes so the user can try again, and refocus the first box.
                otp = List(6) { "" }
                focusRequesters.firstOrNull()?.requestFocus()
            }
        }
    }
}


@Composable
fun NewPasswordScreen(
    email: String,
    onSubmit: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }

    AuthBackground {
        if (loading) {
            LoadingOverlay(message = "Updating password...")
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxHeight < 640.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = 28.dp,
                        vertical = if (isCompact) 10.dp else 24.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AuthLogo(
                    size = if (isCompact) 48.dp else 90.dp,
                    bottomSpacing = if (isCompact) 8.dp else 24.dp
                )

                Text(
                    text = "New Password",
                    color = Color.White,
                    fontSize = if (isCompact) 22.sp else 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

                Text(
                    text = "Choose a new password for your account",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = if (isCompact) 13.sp else 15.sp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 28.dp))

                // New password — AuthTextField handles the eye toggle (isPassword = true).
                AuthTextField(
                    value = newPassword,
                    onValueChange = { if (loading) loading = false
                        newPassword = it
                        if (mismatch) mismatch = false
                    },
                    placeholder = "New password",
                    isPassword = true,
                    keyboardType = KeyboardType.Password
                )

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))

                // Confirm password — same eye toggle. isError drives the mismatch message.
                AuthTextField(
                    value = confirmPassword,
                    onValueChange = { if (loading) loading = false
                        confirmPassword = it
                        if (mismatch) mismatch = false
                    },
                    placeholder = "Confirm new password",
                    isPassword = true,
                    isError = mismatch,
                    errorText = if (mismatch) "Passwords don't match" else null,
                    keyboardType = KeyboardType.Password
                )

                // Password validation checklist — same pattern as SignUpScreen.
                // Shown only when the new password field is non-empty; turns green
                // ✓ when each rule passes, gray otherwise.
                if (newPassword.isNotEmpty()) {
                    val rules = SupabaseAuth.validatePassword(newPassword).associateBy { it.label }
                    val orderedLabels = listOf(
                        "At least 8 characters",
                        "At least 1 uppercase letter",
                        "At least 1 number",
                        "At most 128 characters"
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        orderedLabels.forEach { label ->
                            val passed = rules[label]?.passed == true
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (passed) TinderGreen else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    color = if (passed) TinderGreen else Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 24.dp))

                AuthButton(
                    text = "Update Password",
                    onClick = {
                        val passed = SupabaseAuth.isPasswordValid(newPassword)
                        val matches = newPassword == confirmPassword && confirmPassword.isNotEmpty()
                        mismatch = !matches
                        if (passed && matches) {
                            loading = true
                            onSubmit(email, newPassword)
                        }
                    },
                    enabled = !loading,
                    height = if (isCompact) 50.dp else 56.dp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 24.dp))

                TextButton(onClick = onBack) {
                    Text(
                        text = "Back",
                        color = SoftPink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (isCompact) 14.sp else 15.sp
                    )
                }
            }
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
