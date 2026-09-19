package com.mallucupid.app.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.*
import com.mallucupid.app.data.remote.SessionManager
import com.mallucupid.app.data.remote.SupabaseRepository
import com.mallucupid.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Premium subscription flow backed by Google Play Billing.
 *
 * 3 plans (weekly / monthly / yearly), each unlocking the same 4 perks.
 * Flow:
 *   1. OFFER      — pick a plan, tap "Continue to Payment"
 *   2. PROCESSING — Google Play Billing sheet is launched; verify the returned
 *                   Purchase via SupabaseRepository.verifyPurchase()
 *   3. SUCCESS    — Pro activated; show expiry + unlocked features
 *
 * No Razorpay, no fake delays, no hardcoded order IDs. The Google Play purchase
 * token + orderId are forwarded to the `verify-purchase` edge function, which
 * persists the subscription row and returns `{ ok: true }` on success.
 */
enum class PremiumFlowStep {
    OFFER,
    PROCESSING,
    SUCCESS,
}

private data class PremiumPlan(
    val productId: String,
    val name: String,
    val priceLabel: String,
    val durationLabel: String,
    val durationDays: Int,
    val isBestValue: Boolean,
)

private val PremiumPlans: List<PremiumPlan> = listOf(
    PremiumPlan("weekly_pro", "Weekly Pro", "₹49", "7 days", 7, isBestValue = false),
    PremiumPlan("monthly_pro", "Monthly Pro", "₹99", "30 days", 30, isBestValue = false),
    PremiumPlan("yearly_pro", "Yearly Pro", "₹799", "365 days", 365, isBestValue = true),
)

private data class PremiumPerk(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val description: String,
)

private val PremiumPerks: List<PremiumPerk> = listOf(
    PremiumPerk(Icons.Default.Favorite, NopeCoral, "Unlimited Likes", "Swipe on as many nearby profiles as you like with zero daily limits."),
    PremiumPerk(Icons.Default.Visibility, TinderGold, "See Who Likes You", "Unblur every incoming like and match instantly without waiting."),
    PremiumPerk(Icons.Default.ChatBubble, SuperBlue, "Unlimited Chat", "Message and share photos freely with any match, anytime."),
    PremiumPerk(Icons.Default.Replay, DashboardPeach, "Unlimited Rewinds", "Accidentally swiped left? Undo your last swipe with one tap."),
)

@Composable
fun PremiumSubscriptionFlow(
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(PremiumFlowStep.OFFER) }
    var selectedPlan by remember { mutableStateOf<PremiumPlan?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var activatedPlan by remember { mutableStateOf<PremiumPlan?>(null) }
    var activatedExpiry by remember { mutableStateOf<String?>(null) }

    // Handler invoked by the (remembered, stable) PurchasesUpdatedListener.
    // It is recreated on every recomposition so it always reads the latest
    // selectedPlan; rememberUpdatedState gives us a stable State<T> slot the
    // listener can dereference at callback time.
    val handlePurchases: (BillingResult, List<Purchase>?) -> Unit = { billingResult, purchases ->
        handlePurchaseResult(
            billingResult = billingResult,
            purchases = purchases,
            selectedPlan = selectedPlan,
            scope = scope,
            onError = { message ->
                isProcessing = false
                currentStep = PremiumFlowStep.OFFER
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
            onVerified = { plan, expiry ->
                activatedPlan = plan
                activatedExpiry = expiry
                isProcessing = false
                currentStep = PremiumFlowStep.SUCCESS
            },
        )
    }
    val handlerState = rememberUpdatedState(handlePurchases)

    val purchasesListener = remember {
        PurchasesUpdatedListener { billingResult, purchases ->
            handlerState.value.invoke(billingResult, purchases)
        }
    }

    // Billing client lifecycle — connect when the screen opens, disconnect on leave.
    var billingClient by remember { mutableStateOf<BillingClient?>(null) }
    DisposableEffect(Unit) {
        val client = BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener(purchasesListener)
            .build()
        billingClient = client
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                // Connection ready. Product details are queried on demand when
                // the user taps "Continue to Payment".
            }
            override fun onBillingServiceDisconnected() {
                // Play Billing service dropped — next checkout attempt will need a
                // reconnect, which startConnection handles when retried.
            }
        })
        onDispose {
            if (client.isReady) client.endConnection()
        }
    }

    fun startCheckout(plan: PremiumPlan) {
        val client = billingClient
        if (client == null || !client.isReady) {
            Toast.makeText(context, "Billing service is unavailable. Please try again.", Toast.LENGTH_LONG).show()
            return
        }
        selectedPlan = plan
        isProcessing = true
        currentStep = PremiumFlowStep.PROCESSING
        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(plan.productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(queryParams) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || productDetailsList.isEmpty()) {
                isProcessing = false
                currentStep = PremiumFlowStep.OFFER
                Toast.makeText(context, "Unable to load plan details. Please try again.", Toast.LENGTH_LONG).show()
                return@queryProductDetailsAsync
            }
            val productDetails = productDetailsList.first()
            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
            val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
            if (!offerToken.isNullOrEmpty()) {
                productParamsBuilder.setOfferToken(offerToken)
            }
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
                .build()
            val activity = context as? Activity
            if (activity == null) {
                isProcessing = false
                currentStep = PremiumFlowStep.OFFER
                Toast.makeText(context, "Unable to launch payment flow.", Toast.LENGTH_LONG).show()
                return@queryProductDetailsAsync
            }
            val launchResult = client.launchBillingFlow(activity, flowParams)
            if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                isProcessing = false
                currentStep = PremiumFlowStep.OFFER
                Toast.makeText(context, "Unable to start payment. Please try again.", Toast.LENGTH_LONG).show()
            }
            // Otherwise: onPurchasesUpdated fires via purchasesListener with the
            // final result (success, cancellation, or failure).
        }
    }

    Scaffold(
        containerColor = DashboardBg,
        topBar = {
            if (currentStep != PremiumFlowStep.PROCESSING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        when (currentStep) {
                            PremiumFlowStep.OFFER -> onBack()
                            PremiumFlowStep.SUCCESS -> onSuccess()
                            PremiumFlowStep.PROCESSING -> { /* no-op while billing sheet is up */ }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DashboardCream
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = TinderGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MalluCupid Pro",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentStep) {
                PremiumFlowStep.OFFER -> OfferScreen(
                    selectedPlan = selectedPlan,
                    onSelectPlan = { selectedPlan = it },
                    onContinue = { selectedPlan?.let { startCheckout(it) } },
                )
                PremiumFlowStep.PROCESSING -> ProcessingScreen()
                PremiumFlowStep.SUCCESS -> SuccessScreen(
                    plan = activatedPlan,
                    expiryLabel = activatedExpiry,
                    onSuccess = onSuccess,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen 1 — Plan / Offer selection
// ---------------------------------------------------------------------------

@Composable
private fun OfferScreen(
    selectedPlan: PremiumPlan?,
    onSelectPlan: (PremiumPlan) -> Unit,
    onContinue: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // Hero header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = CircleShape,
                    color = DashboardTerracotta.copy(alpha = 0.15f),
                    border = BorderStroke(1.5.dp, DashboardTerracotta),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = TinderGold,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "MalluCupid Pro",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = DashboardCream
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Unlock the full MalluCupid experience",
                    fontSize = 13.sp,
                    color = DashboardPeach,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CHOOSE YOUR PLAN",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = DashboardNavMuted,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PremiumPlans.forEach { plan ->
                    PremiumPlanCard(
                        plan = plan,
                        isSelected = selectedPlan?.productId == plan.productId,
                        onSelect = { onSelectPlan(plan) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ALL PLANS INCLUDE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = DashboardNavMuted,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumPerks.forEach { perk ->
                    PremiumPerkCard(
                        icon = perk.icon,
                        iconColor = perk.tint,
                        title = perk.title,
                        description = perk.description,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Fixed bottom: CTA + terms
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onContinue,
                enabled = selectedPlan != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DashboardTerracotta,
                    contentColor = Color.White,
                    disabledContainerColor = DashboardTerracotta.copy(alpha = 0.35f),
                    disabledContentColor = DashboardCream.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                val plan = selectedPlan
                Text(
                    text = if (plan != null)
                        "Continue to Payment · ${plan.priceLabel}"
                    else
                        "Continue to Payment",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Payment will be charged to your Google Play account. Subscriptions auto-renew unless cancelled at least 24 hours before the end of the current period.",
                fontSize = 11.sp,
                color = DashboardNavMuted,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PremiumPlanCard(
    plan: PremiumPlan,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Box {
        Surface(
            onClick = onSelect,
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) DashboardTerracotta.copy(alpha = 0.10f) else DashboardCard,
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) DashboardTerracotta else Color(0xFF42342D)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardCream
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = plan.durationLabel,
                        fontSize = 13.sp,
                        color = DashboardPeach
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = plan.priceLabel,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = DashboardCream
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) DashboardTerracotta else Color(0xFF3A2E28),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color.Transparent else Color(0xFF4D3D35)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Selected",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "Subscribe",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DashboardCream
                                )
                            }
                        }
                    }
                }
            }
        }
        if (plan.isBestValue) {
            Surface(
                shape = RoundedCornerShape(50),
                color = TinderGold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Best Value",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardBg,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumPerkCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DashboardCard,
        border = BorderStroke(1.dp, Color(0xFF42342D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.2f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DashboardCream
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = DashboardNavMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen 2 — Processing (Google Play Billing sheet is up)
// ---------------------------------------------------------------------------

@Composable
private fun ProcessingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF261E1A),
            border = BorderStroke(2.dp, DashboardTerracotta),
            modifier = Modifier.size(110.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = DashboardTerracotta,
                    trackColor = Color(0xFF42342D),
                    strokeWidth = 4.dp
                )
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = DashboardCream,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Processing payment...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DashboardCream
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please complete the Google Play purchase prompt. Don't close this screen while the transaction is in progress.",
            fontSize = 13.sp,
            color = DashboardPeach,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Screen 3 — Success
// ---------------------------------------------------------------------------

@Composable
private fun SuccessScreen(
    plan: PremiumPlan?,
    expiryLabel: String?,
    onSuccess: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = CircleShape,
                color = TinderGreen.copy(alpha = 0.15f),
                border = BorderStroke(3.dp, TinderGreen),
                modifier = Modifier.size(110.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = TinderGreen,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "MalluCupid Pro Activated!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DashboardCream,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (plan != null) {
                Text(
                    text = "${plan.name} · ${plan.priceLabel}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DashboardPeach
                )
            }
            if (!expiryLabel.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Valid until $expiryLabel",
                    fontSize = 13.sp,
                    color = DashboardNavMuted
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DashboardCard,
                border = BorderStroke(1.dp, Color(0xFF42342D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "UNLOCKED FEATURES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DashboardNavMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PremiumPerks.forEach { perk ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = TinderGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = perk.title,
                                fontSize = 14.sp,
                                color = DashboardMutedBeige
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onSuccess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DashboardTerracotta,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "Start Exploring with Pro",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Billing result handler (reads latest selectedPlan via rememberUpdatedState)
// ---------------------------------------------------------------------------

private fun handlePurchaseResult(
    billingResult: BillingResult,
    purchases: List<Purchase>?,
    selectedPlan: PremiumPlan?,
    scope: CoroutineScope,
    onError: (String) -> Unit,
    onVerified: (PremiumPlan, String) -> Unit,
) {
    if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
        val msg = when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> "Payment cancelled. You were not charged."
            BillingClient.BillingResponseCode.NETWORK_ERROR -> "Network error. Please check your connection and try again."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "This plan is currently unavailable."
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "Payment configuration error. Please contact support."
            BillingClient.BillingResponseCode.ERROR -> "Payment failed. Please try again."
            else -> "Payment failed. Please try again."
        }
        onError(msg)
        return
    }
    val purchase = purchases?.firstOrNull()
    if (purchase == null) {
        onError("No purchase returned. Please try again.")
        return
    }
    val plan = selectedPlan
    if (plan == null) {
        onError("No plan selected. Please try again.")
        return
    }
    scope.launch {
        val userId = SessionManager.current()?.userId.orEmpty()
        val verified = runCatching {
            SupabaseRepository.verifyPurchase(
                userId = userId,
                productId = plan.productId,
                purchaseToken = purchase.purchaseToken,
                orderId = purchase.orderId,
            )
        }.getOrDefault(false)
        if (!verified) {
            onError("Payment verification failed. Please try again or contact support.")
            return@launch
        }
        // Fetch the real expiry from the DB; fall back to a local estimate
        // derived from the plan duration if the lookup fails.
        val expiry = runCatching {
            SupabaseRepository.getActiveSubscription(userId)?.expiresAt
        }.getOrNull()?.let(::formatExpiryIso) ?: formatExpiryDays(plan.durationDays)
        onVerified(plan, expiry)
    }
}

private fun formatExpiryIso(iso: String): String {
    return try {
        OffsetDateTime.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (e: Exception) {
        iso.take(10)
    }
}

private fun formatExpiryDays(days: Int): String {
    return try {
        OffsetDateTime.now().plusDays(days.toLong())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (e: Exception) {
        ""
    }
}
