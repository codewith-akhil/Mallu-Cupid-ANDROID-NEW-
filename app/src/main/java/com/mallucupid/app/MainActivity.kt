package com.mallucupid.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
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
        SessionManager.init(applicationContext)

        enableEdgeToEdge()
        setContent {
            MalluCupidTheme {
                val scope = rememberCoroutineScope()
                val initialScreen = if (SessionManager.isLoggedIn()) "home" else "splash"

                var currentScreen by remember { mutableStateOf(initialScreen) }
                // Back-stack: track the previous screen so BackHandler can navigate back
                val backStack = remember { mutableStateListOf<String>() }

                var userEmail by remember { mutableStateOf(SessionManager.current()?.email ?: "") }
                var userName by remember { mutableStateOf("") }
                var userPassword by remember { mutableStateOf("") }
                var userDraft by remember { mutableStateOf(OnboardingDraft()) }
                var loadingState by remember { mutableStateOf(false) }
                var otpContext by remember { mutableStateOf("signup") }

                // Helper: navigate to a screen + push the current one onto the back stack
                fun navigateTo(target: String) {
                    if (target != currentScreen) {
                        backStack.add(0, currentScreen)
                        currentScreen = target
                    }
                }

                // Helper: go back — pop the back stack, or show "press again to exit" on home
                var backPressedOnce by remember { mutableStateOf(false) }
                fun goBack(): Boolean {
                    return if (backStack.isNotEmpty()) {
                        currentScreen = backStack.removeAt(0)
                        true
                    } else if (currentScreen == "home") {
                        if (backPressedOnce) {
                            false // let the system handle it (exit app)
                        } else {
                            backPressedOnce = true
                            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
                            // Reset after 2 seconds
                            scope.launch {
                                kotlinx.coroutines.delay(2000)
                                backPressedOnce = false
                            }
                            true
                        }
                    } else {
                        false // entry screens (splash, welcome) — let the system exit
                    }
                }

                // App-wide BackHandler — intercepts hardware back on EVERY screen
                BackHandler(enabled = true) {
                    if (!goBack()) {
                        finish()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        "splash" -> SplashScreen(onFinished = { navigateTo("welcome") })

                        "welcome" -> WelcomeScreen(onGetStarted = { navigateTo("signin") })

                        "signin" -> SignInScreen(
                            onSignIn = { email, password ->
                                scope.launch {
                                    loadingState = true
                                    try {
                                        val (token, err) = SupabaseAuth.signInWithPassword(email, password)
                                        loadingState = false
                                        if (token != null) {
                                            Toast.makeText(this@MainActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                                            backStack.clear()
                                            currentScreen = "home"
                                        } else {
                                            Toast.makeText(this@MainActivity, err ?: "Wrong email or password.", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        loadingState = false
                                        Toast.makeText(this@MainActivity, "No internet. Check your connection and try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onGoToSignUp = { navigateTo("signup") },
                            onForgotPassword = { navigateTo("reset") },
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
                                        val signUpErr = SupabaseAuth.signUp(email, password)
                                        if (signUpErr != null) {
                                            loadingState = false
                                            Toast.makeText(this@MainActivity, signUpErr, Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                        val otpErr = SupabaseAuth.sendOtp(email)
                                        loadingState = false
                                        if (otpErr != null) {
                                            Toast.makeText(this@MainActivity, "Couldn't send the code. Please try again.", Toast.LENGTH_LONG).show()
                                        } else {
                                            // Log dev_code in debug only (never show to users)
                                            val devCode = SupabaseAuth.lastDevCode
                                            if (devCode != null) {
                                                Log.d("OTP", "dev_code for $email: $devCode")
                                            }
                                            Toast.makeText(this@MainActivity, "Code sent to $email", Toast.LENGTH_LONG).show()
                                            otpContext = "signup"
                                            navigateTo("otp")
                                        }
                                    } catch (e: Exception) {
                                        loadingState = false
                                        Toast.makeText(this@MainActivity, "No internet. Check your connection and try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onGoToSignIn = { navigateTo("signin") },
                            loading = loadingState
                        )

                        "reset" -> ResetPasswordScreen(
                            onSendOtp = { email ->
                                userEmail = email
                                scope.launch {
                                    loadingState = true
                                    try {
                                        // Check if the email is registered first
                                        val exists = SupabaseRepository.emailExists(email)
                                        if (!exists) {
                                            loadingState = false
                                            Toast.makeText(this@MainActivity, "No account found with this email. Please sign up first.", Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                        val err = SupabaseAuth.sendOtp(email)
                                        loadingState = false
                                        if (err != null) {
                                            Toast.makeText(this@MainActivity, "Couldn't send the code. Please try again.", Toast.LENGTH_LONG).show()
                                        } else {
                                            val devCode = SupabaseAuth.lastDevCode
                                            if (devCode != null) {
                                                Log.d("OTP", "dev_code for $email: $devCode")
                                            }
                                            Toast.makeText(this@MainActivity, "Code sent to $email", Toast.LENGTH_LONG).show()
                                            otpContext = "reset"
                                            navigateTo("otp")
                                        }
                                    } catch (e: Exception) {
                                        loadingState = false
                                        Toast.makeText(this@MainActivity, "No internet. Check your connection and try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onBackToSignIn = { navigateTo("signin") },
                            loading = loadingState
                        )

                        "otp" -> OtpVerificationScreen(
                            email = userEmail.ifEmpty { "your email" },
                            otpContext = otpContext,
                            onVerified = { code ->
                                scope.launch {
                                    loadingState = true
                                    try {
                                        val verifyErr = SupabaseAuth.verifyOtp(userEmail, code)
                                        if (verifyErr != null) {
                                            loadingState = false
                                            Toast.makeText(this@MainActivity, verifyErr, Toast.LENGTH_LONG).show()
                                            return@launch
                                        }
                                        if (otpContext == "signup") {
                                            val (token, err) = SupabaseAuth.signInWithPassword(userEmail, userPassword)
                                            loadingState = false
                                            if (token != null) {
                                                userDraft = userDraft.copy(name = userName)
                                                backStack.clear()
                                                currentScreen = "onboarding"
                                            } else {
                                                Toast.makeText(this@MainActivity, "Wrong email or password.", Toast.LENGTH_LONG).show()
                                                backStack.clear()
                                                currentScreen = "signin"
                                            }
                                        } else {
                                            loadingState = false
                                            navigateTo("newpassword")
                                        }
                                    } catch (e: Exception) {
                                        loadingState = false
                                        Toast.makeText(this@MainActivity, "No internet. Check your connection and try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onBack = { navigateTo(if (otpContext == "signup") "signup" else "reset") },
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
                                            Toast.makeText(this@MainActivity, "Couldn't update your password. Please try again.", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(this@MainActivity, "Password updated! Please sign in.", Toast.LENGTH_LONG).show()
                                            backStack.clear()
                                            currentScreen = "signin"
                                        }
                                    } catch (e: Exception) {
                                        loadingState = false
                                        Toast.makeText(this@MainActivity, "No internet. Check your connection and try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onBack = { navigateTo("otp") },
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
                                            Toast.makeText(this@MainActivity, "Profile saved", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    userDraft = completedDraft
                                    backStack.clear()
                                    currentScreen = "home"
                                }
                            },
                            onBack = { navigateTo("signin") }
                        )

                        "home" -> DashboardScreen(
                            userDraft = userDraft,
                            onOpenOnboarding = { navigateTo("onboarding") },
                            onSignOut = {
                                SessionManager.signOut()
                                backStack.clear()
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
