package com.mallucupid.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.data.remote.SessionManager
import com.mallucupid.app.data.remote.SupabaseAuth
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.ui.screens.*
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
                var userDraft by remember { mutableStateOf(OnboardingDraft()) }
                var loadingState by remember { mutableStateOf(false) }

                when (currentScreen) {
                    "splash" -> SplashScreen(onFinished = { currentScreen = "welcome" })

                    "welcome" -> WelcomeScreen(onGetStarted = { currentScreen = "signin" })

                    "signin" -> SignInScreen(
                        onSignIn = { email ->
                            // Sign-in flow: send OTP to the entered email, then go to OTP screen.
                            userEmail = email
                            scope.launch {
                                loadingState = true
                                val err = withContext(Dispatchers.IO) {
                                    SupabaseAuth.sendOtp(email)
                                }
                                loadingState = false
                                if (err != null) {
                                    Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                                } else {
                                    currentScreen = "otp"
                                }
                            }
                        },
                        onGoToSignUp = { currentScreen = "signup" },
                        onForgotPassword = { currentScreen = "reset" }
                    )

                    "signup" -> SignUpScreen(
                        onContinue = { email ->
                            userEmail = email
                            scope.launch {
                                loadingState = true
                                val err = withContext(Dispatchers.IO) {
                                    SupabaseAuth.sendOtp(email)
                                }
                                loadingState = false
                                if (err != null) {
                                    Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                                } else {
                                    currentScreen = "otp"
                                }
                            }
                        },
                        onGoToSignIn = { currentScreen = "signin" }
                    )

                    "reset" -> ResetPasswordScreen(
                        onSendOtp = { email ->
                            userEmail = email
                            scope.launch {
                                val err = withContext(Dispatchers.IO) { SupabaseAuth.sendOtp(email) }
                                if (err != null) {
                                    Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                                } else {
                                    currentScreen = "otp"
                                }
                            }
                        },
                        onBackToSignIn = { currentScreen = "signin" }
                    )

                    "otp" -> OtpVerificationScreen(
                        email = userEmail.ifEmpty { "your email" },
                        onVerified = {
                            // OTP verified → session established. If the user has a
                            // completed profile, go straight to home; else onboard.
                            currentScreen = "home"
                        },
                        onBack = { currentScreen = "signin" }
                    )

                    "onboarding" -> OnboardingScreen(
                        initialDraft = userDraft,
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
            }
        }
    }
}
