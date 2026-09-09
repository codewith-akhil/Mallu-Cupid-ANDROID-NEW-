package com.mallucupid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.mallucupid.app.data.OnboardingDraft
import com.mallucupid.app.ui.screens.*
import com.mallucupid.app.ui.theme.MalluCupidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MalluCupidTheme {
                var currentScreen by remember { mutableStateOf("splash") }
                var userEmail by remember { mutableStateOf("") }
                var userDraft by remember { mutableStateOf(OnboardingDraft()) }

                when (currentScreen) {
                    "splash" -> SplashScreen(onFinished = { currentScreen = "welcome" })

                    "welcome" -> WelcomeScreen(onGetStarted = { currentScreen = "signin" })

                    "signin" -> SignInScreen(
                        onSignIn = { currentScreen = "home" },
                        onGoToSignUp = { currentScreen = "signup" },
                        onForgotPassword = { currentScreen = "reset" }
                    )

                    "signup" -> SignUpScreen(
                        onContinue = { email ->
                            userEmail = email
                            currentScreen = "otp"
                        },
                        onGoToSignIn = { currentScreen = "signin" }
                    )

                    "reset" -> ResetPasswordScreen(
                        onSendOtp = { email ->
                            userEmail = email
                            currentScreen = "otp"
                        },
                        onBackToSignIn = { currentScreen = "signin" }
                    )

                    "otp" -> OtpVerificationScreen(
                        email = userEmail.ifEmpty { "your email" },
                        onVerified = { currentScreen = "onboarding" },
                        onBack = { currentScreen = "signin" }
                    )

                    "onboarding" -> OnboardingScreen(
                        initialDraft = userDraft,
                        onComplete = { completedDraft ->
                            userDraft = completedDraft
                            currentScreen = "home"
                        },
                        onBack = {
                            currentScreen = "signin"
                        }
                    )

                    "home" -> DashboardScreen(
                        userDraft = userDraft,
                        onOpenOnboarding = { currentScreen = "onboarding" },
                        onSignOut = { currentScreen = "welcome" }
                    )
                }
            }
        }
    }
}
