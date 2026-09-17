package com.mallucupid.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mallucupid.app.data.remote.SupabaseAuth
import com.mallucupid.app.ui.theme.*

@Composable
fun SignInScreen(
    onSignIn: (String, String) -> Unit,
    onGoToSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    loading: Boolean = false
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var emailErrorText by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf(false) }
    var passwordErrorText by remember { mutableStateOf<String?>(null) }

    AuthBackground {
        if (loading) {
            LoadingOverlay(message = "Signing in...")
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
                    text = "Welcome Back",
                    color = Color.White,
                    fontSize = if (isCompact) 22.sp else 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

                Text(
                    text = "Sign in to continue",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = if (isCompact) 13.sp else 15.sp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 14.dp else 36.dp))

                AuthTextField(
                    value = email,
                    onValueChange = {
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

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))

                AuthTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (passwordError) {
                            passwordError = false
                            passwordErrorText = null
                        }
                    },
                    placeholder = "Password",
                    isPassword = true,
                    isError = passwordError,
                    errorText = passwordErrorText
                )

                Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onForgotPassword,
                        contentPadding = if (isCompact) PaddingValues(horizontal = 4.dp, vertical = 2.dp) else ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(
                            text = "Forgot Password?",
                            color = SoftPink,
                            fontSize = if (isCompact) 13.sp else 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))

                AuthButton(
                    text = "Sign In",
                    onClick = {
                        emailError = email.isEmpty() || !isValidEmail(email)
                        emailErrorText = when {
                            email.isEmpty() -> "Email is required"
                            !isValidEmail(email) -> "Enter a valid email address"
                            else -> null
                        }
                        passwordError = password.length < 6
                        passwordErrorText = if (passwordError) "Minimum 6 characters" else null
                        if (!emailError && !passwordError) {
                            onSignIn(email, password)
                        }
                    },
                    enabled = !loading,
                    height = if (isCompact) 50.dp else 56.dp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 32.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Don't have an account? ",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = if (isCompact) 14.sp else 15.sp
                    )
                    TextButton(
                        onClick = onGoToSignUp,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Sign Up",
                            color = SoftPink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (isCompact) 14.sp else 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SignUpScreen(
    onContinue: (String, String, String) -> Unit,
    onGoToSignIn: () -> Unit,
    loading: Boolean = false
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var emailErrorText by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf(false) }
    var passwordErrorText by remember { mutableStateOf<String?>(null) }

    AuthBackground {
        if (loading) {
            LoadingOverlay(message = "Creating account...")
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
                    size = if (isCompact) 44.dp else 90.dp,
                    bottomSpacing = if (isCompact) 8.dp else 24.dp
                )

                Text(
                    text = "Create Account",
                    color = Color.White,
                    fontSize = if (isCompact) 22.sp else 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

                Text(
                    text = "Join Mallu Cupid today",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = if (isCompact) 13.sp else 15.sp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 36.dp))

                AuthTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 50) {
                            name = it
                            if (nameError) nameError = false
                        }
                    },
                    placeholder = "Full Name",
                    isError = nameError,
                    errorText = if (nameError) "Name is required" else null
                )

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))

                AuthTextField(
                    value = email,
                    onValueChange = {
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

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))

                AuthTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (passwordError) {
                            passwordError = false
                            passwordErrorText = null
                        }
                    },
                    placeholder = "Password",
                    isPassword = true,
                    isError = passwordError,
                    errorText = passwordErrorText
                )

                // Password validation checklist — shown only when password is non-empty.
                // Uses SupabaseAuth.validatePassword to drive the 4 rule rows; each row
                // turns green ✓ when its rule passes, gray otherwise.
                if (password.isNotEmpty()) {
                    val rules = SupabaseAuth.validatePassword(password).associateBy { it.label }
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
                    text = "Continue",
                    onClick = {
                        nameError = name.isBlank()
                        emailError = !isValidEmail(email)
                        emailErrorText = if (emailError) "Enter a valid email address" else null
                        val passwordValid = SupabaseAuth.isPasswordValid(password)
                        passwordError = !passwordValid
                        passwordErrorText = if (passwordError) "Please meet all password requirements" else null
                        if (!nameError && !emailError && !passwordError) {
                            onContinue(name, email, password)
                        }
                    },
                    enabled = !loading,
                    height = if (isCompact) 50.dp else 56.dp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 32.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Already have an account? ",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = if (isCompact) 14.sp else 15.sp
                    )
                    TextButton(
                        onClick = onGoToSignIn,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Sign In",
                            color = SoftPink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (isCompact) 14.sp else 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuthBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PrimaryRed, DarkMaroon)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun AuthLogo(
    size: Dp = 90.dp,
    bottomSpacing: Dp = 24.dp
) {
    AsyncImage(
        model = "https://res.cloudinary.com/wxytzoo1/image/upload/v1788918988/Mallucupidlogo.png",
        contentDescription = "Logo",
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
    Spacer(modifier = Modifier.height(bottomSpacing))
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    showStrength: Boolean = false, // kept for backward-compat with OtpAndResetScreens; no-op (strength meter replaced by per-screen checklist UI).
    isError: Boolean = false,
    errorText: String? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val borderColor = if (isError) NopeCoral else Color.Transparent

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(placeholder, color = Color.White.copy(alpha = 0.6f))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                focusedBorderColor = borderColor,
                unfocusedBorderColor = borderColor,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AccentPink
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation()
                else VisualTransformation.None,
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = Color.White
                        )
                    }
                }
            } else null
        )

        if (isError && !errorText.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorText,
                color = NopeCoral,
                fontSize = 11.sp
            )
        }
    }
}

private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")

private fun isValidEmail(email: String): Boolean = emailRegex.matches(email)

@Composable
fun AuthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentPink,
            disabledContainerColor = AccentPink.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = if (height < 52.dp) 16.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
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
