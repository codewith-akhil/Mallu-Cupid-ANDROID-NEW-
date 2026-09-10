package com.mallucupid.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.mallucupid.app.ui.theme.*

@Composable
fun SignInScreen(
    onSignIn: () -> Unit,
    onGoToSignUp: () -> Unit,
    onForgotPassword: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var emailErrorText by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf(false) }
    var passwordErrorText by remember { mutableStateOf<String?>(null) }

    AuthBackground {
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
                        emailError = email.isNotEmpty() && !isValidEmail(email)
                        emailErrorText = if (emailError) "Enter a valid email address" else null
                        passwordError = password.length < 6
                        passwordErrorText = if (passwordError) "Minimum 6 characters" else null
                        if (!emailError && !passwordError) onSignIn()
                    },
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
    onContinue: (String) -> Unit,
    onGoToSignIn: () -> Unit
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
                        name = it
                        if (nameError) nameError = false
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
                    showStrength = true,
                    isError = passwordError,
                    errorText = passwordErrorText
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 24.dp))

                AuthButton(
                    text = "Continue",
                    onClick = {
                        nameError = name.isBlank()
                        emailError = !isValidEmail(email)
                        emailErrorText = if (emailError) "Enter a valid email address" else null
                        passwordError = password.length < 6
                        passwordErrorText = if (passwordError) "Minimum 6 characters" else null
                        if (!nameError && !emailError && !passwordError) onContinue(email)
                    },
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
    showStrength: Boolean = false,
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

        if (showStrength && isPassword && value.isNotEmpty()) {
            val strength = passwordStrength(value)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (i < strength.segments) strength.color
                                else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }
                Text(
                    text = strength.label,
                    color = strength.color,
                    fontSize = 11.sp
                )
            }
        }

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

/**
 * Strength tier for a password string, based on character length.
 * <6 chars = Weak, 6-8 = Fair, 9-11 = Good, 12+ = Strong.
 */
private data class PasswordStrength(val segments: Int, val label: String, val color: Color)

private fun passwordStrength(password: String): PasswordStrength = when {
    password.length < 6 -> PasswordStrength(1, "Weak", NopeCoral)
    password.length <= 8 -> PasswordStrength(2, "Fair", TinderGold)
    password.length <= 11 -> PasswordStrength(3, "Good", TinderGreen)
    else -> PasswordStrength(4, "Strong", SuperBlue)
}

private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")

private fun isValidEmail(email: String): Boolean = emailRegex.matches(email)

@Composable
fun AuthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
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
