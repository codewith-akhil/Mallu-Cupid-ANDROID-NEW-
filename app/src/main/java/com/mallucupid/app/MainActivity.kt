package com.mallucupid.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.remote.SessionManager
import com.mallucupid.app.data.remote.SupabaseAuth
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.ui.screens.*
import com.mallucupid.app.ui.theme.AccentPink
import com.mallucupid.app.ui.theme.MalluCupidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialise the persistent session store + restore any saved access token.
        SessionManager.init(applicationContext)

        enableEdgeToEdge()
        setContent {
            MalluCupidTheme {
                val scope = rememberCoroutineScope()
                // If a session already exists, skip straight to home.
                val initialScreen = if (SessionManager.isLoggedIn()) "home" else "splash"

                var currentScreen by remember { mutableStateOf(initialScreen) }
                var userEmail by remember { mutableStateOf(SessionManager.current()?.email ?: "") }
                var userName by remember { mutableStateOf("") }
                var userPassword by remember { mutableStateOf("") } // needed for signup→signin flow
                var userDraft by remember { mutableStateOf(OnboardingDraft()) }
                var loadingState by remember { mutableStateOf(false) }
                var otpContext by remember { mutableStateOf("signup") }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        "splash" -> SplashScreen(onFinished = { currentScreen = "welcome" })

                        "welcome" -> WelcomeScreen(onGetStarted = { currentScreen = "signin" })

                        "signin" -> SignInScreen(
                            onSignIn = { email, password ->
                                scope.launch {
                                    loadingState = true
                                    try {
                                        val (token, err) = SupabaseAuth.signInWithPassword(email, password)
                                        loadingState = false
                                        if (token != null) {
                                            Toast.makeText(this@MainActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                                            currentScreen = "home"
                                        } else {
                                            Toast.makeText(this@MainActivity, err ?: "Could not sign in", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        loadingState = false
                                        Toast.makeText(this@MainActivity, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onGoToSignUp = { currentScreen = "signup" },
                            onForgotPassword = { currentScreen = "reset" },
                            loading = loadingState
                        )

                        "signup" -> SignUpScreen(
                            onContinue = { name, email, password ->
                                userEmail = email
                                userName = name
                                userPassword = password
                                scope.launch {
                                    loadingState = true
                                    try {
                                        // 1. Create the auth user
                                        val signUpErr = SupabaseAuth.signUp(email, password)
                                        if (signUpErr != null) {
                                            loadingState = false
                                            Toast.makeText(this@MainActivity, signUpErr, Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                        // 2. Send OTP
                                        val otpErr = SupabaseAuth.sendOtp(email)
                                        loadingState = false
                                        if (otpErr != null) {
                                            Toast.makeText(this@MainActivity, otpErr, Toast.LENGTH_LONG).show()
                                        } else {
                                            // If a dev_code was returned (testing mode), surface it
                                            val devCode = SupabaseAuth.lastDevCode
                                            if (devCode != null) {
                                                Toast.makeText(this@MainActivity, "Dev code: $devCode (Resend not configured)", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "We sent a 6-digit code to $email", Toast.LENGTH_SHORT).show()
                                            }
                                            otpContext = "signup"
                                            currentScreen = "otp"
                                        }
                                    } catch (e: Exception) {
                                        loadingState = false
                                        Toast.makeText(this@MainActivity, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onGoToSignIn = { currentScreen = "signin" },
                            loading = loadingState
                        )

                        "reset" -> ResetPasswordScreen(
                            onSendOtp = { email ->
                                userEmail = email
                                scope.launch {
                                    loadingState = true
                                    try {
                                        val err = SupabaseAuth.sendOtp(email)
                                        loadingState = false
                                        if (err != null) {
                                            Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                        } else {
                                            val devCode = SupabaseAuth.lastDevCode
                                            if (devCode != null) {
                                                Toast.makeText(this@MainActivity, "Dev code: $devCode (Resend not configured)", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "We sent a 6-digit code to $email", Toast.LENGTH_SHORT).show()
                                            }
                                            otpContext = "reset"
                                            currentScreen = "otp"
                                        }
                                    } catch (e: Exception) {
                                        loadingState = false
                                        Toast.makeText(this@MainActivity, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onBackToSignIn = { currentScreen = "signin" },
                            loading = loadingState
                        )

                        "otp" -> OtpVerificationScreen(
                            email = userEmail.ifEmpty { "your email" },
                            otpContext = otpContext,
                            onVerified = { code ->
                                // Verify the OTP code against the server, THEN proceed.
                                scope.launch {
                                    loadingState = true
                                    try {
                                        val verifyErr = SupabaseAuth.verifyOtp(userEmail, code)
                                        if (verifyErr != null) {
                                            loadingState = false
                                            Toast.makeText(this@MainActivity, verifyErr, Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                        // OTP verified — proceed based on context
                                        if (otpContext == "signup") {
                                            // Sign in with password → onboarding
                                            val (token, err) = SupabaseAuth.signInWithPassword(userEmail, userPassword)
                                            loadingState = false
                                            if (token != null) {
                                                userDraft = userDraft.copy(name = userName)
                                                currentScreen = "onboarding"
                                            } else {
                                                Toast.makeText(this@MainActivity, err ?: "Could not sign in. Please try again.", Toast.LENGTH_LONG).show()
                                                currentScreen = "signin"
                                            }
                                        } else {
                                            // reset flow: go to new password screen
                                            loadingState = false
                                            currentScreen = "newpassword"
                                        }
                                    } catch (e: Exception) {
                                        loadingState = false
                                        Toast.makeText(this@MainActivity, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onBack = { currentScreen = "signin" },
                            loading = loadingState
                        )

                        "newpassword" -> NewPasswordScreen(
                            email = userEmail,
                            onSubmit = { email, newPassword ->
                                scope.launch {
                                    loadingState = true
                                    try {
                                        val err = SupabaseAuth.resetPassword(email, newPassword)
                                        loadingState = false
                                        if (err != null) {
                                            Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(this@MainActivity, "Password updated! Please sign in.", Toast.LENGTH_SHORT).show()
                                            currentScreen = "signin"
                                        }
                                    } catch (e: Exception) {
                                        loadingState = false
                                        Toast.makeText(this@MainActivity, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onBack = { currentScreen = "signin" },
                            loading = loadingState
                        )

                        "onboarding" -> OnboardingScreen(
                            initialDraft = userDraft.copy(name = userName),
                            onComplete = { completedDraft ->
                                scope.launch {
                                    val session = SessionManager.current()
                                    val userId = session?.userId
                                    if (userId != null) {
                                        loadingState = true
                                        val ok = withContext(Dispatchers.IO) {
                                            SupabaseRepository.saveProfile(userId, session.email.orEmpty(), completedDraft)
                                        }
                                        loadingState = false
                                        if (!ok) {
                                            Toast.makeText(this@MainActivity, "Profile saved locally", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    userDraft = completedDraft
                                    currentScreen = "home"
                                }
                            },
                            onBack = { currentScreen = "signin" }
                        )

                        "home" -> DashboardScreen(
                            userDraft = userDraft,
                            onOpenOnboarding = { currentScreen = "onboarding" },
                            onSignOut = {
                                SessionManager.signOut()
                                currentScreen = "welcome"
                            }
                        )
                    }

                    if (loadingState) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentPink)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Please wait...", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
