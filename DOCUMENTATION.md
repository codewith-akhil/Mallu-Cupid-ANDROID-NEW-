# Mallu Cupid — Production Architecture & End-to-End Documentation

<p align="center">
  <b>The Premier Dating Application for Kerala Singles</b><br>
  Built with 100% Kotlin, Jetpack Compose, Material 3, and Enterprise-Grade Android Privacy Controls
</p>

---

## 1. Executive Summary & Brand Identity

**Mallu Cupid** is a bespoke, culturally tailored dating and matchmaking application designed for Malayalis worldwide. The platform pairs modern dating mechanics (card-swiping discovery, real-time messaging, photo verification) with Kerala-specific lifestyle cultural touchpoints (native Malayalam interests, Kerala district home towns, filterable relationship intentions, and date night ideas in Kochi, Trivandrum, Kozhikode, and Thrissur).

### 🎨 Design System & Custom Color Palette
Mallu Cupid implements a signature **Dark Luxury Kerala Palette** crafted with warm chocolate, earthy terracotta, and soft cream tones to deliver an inviting, premium aesthetic:

| Color Token | Hex Code | Purpose |
|---|---|---|
| `DashboardBg` | `#201B18` | Deep luxury canvas background |
| `DashboardCard` | `#342823` | Elevated card surfaces, containers, and dialogs |
| `DashboardTerracotta` | `#B24B39` | Primary accent, action buttons, sent message bubbles, active switches |
| `DashboardPeach` | `#E1AA98` | Secondary accent, badges, icons, highlights |
| `DashboardCream` | `#FFFFFAF5` | Primary high-contrast typography and titles |
| `DashboardNavMuted` | `#A89B93` | Secondary labels, descriptions, and subtitle text |
| `DashboardMutedBeige` | `#D5CBC4` | Body text and readable long-form descriptions |
| `SuperBlue` | `#38BDF8` | Photo verified security badges and checkmarks |
| `NopeCoral` | `#F87171` | Danger actions, unverified badges, and account deletion buttons |

---

## 2. Comprehensive Screen & Feature Matrix

### 2.1. Discovery & Swipe Engine
- **Swipe Cards**: Fluid gesture swiping (drag to Like / Nope) with spring animations.
- **Action Toolbar**: Rewind last swipe, Nope (red circle), Super Like (blue star), Like (terracotta heart), and Boost.
- **First Impression Modal**: Quick-response opening prompts allowing users to reply directly to a match's prompt before matching.
- **Category Filter Chips**: Instant filtering by Kerala regions (*"Kochi Creatives"*, *"Techies in Infopark"*, *"Foodies & Cafe Lovers"*, *"Tradition & Roots"*).

### 2.2. Comprehensive Profile Editor (`EditProfileScreen`)
- **6-Slot Photo Manager**: Drag, reorder, delete, and add profile photos with thumbnail badges.
- **Malayalam & English Bios**: Rich text bio editor with live character counter.
- **Relationship Intentions**: Filterable relationship types (Long-term partner, Casual dating, Matrimony-ready, Cafe buddies).
- **Kerala Cultural Interests**: Lifestyle tags, music anthems, Kerala cuisine preferences, and zodiac signs.
- **Privacy Controls**: Toggles to hide age or distance if desired.

### 2.3. Account Settings Hub (`AccountSettingsScreen`)
- **Discovery Preferences**:
  - Interactive distance slider (2 km to 160 km) with live readout.
  - Interested in gender tags (*Women, Men, Transmen, Transwomen, Couples, Anyone*).
  - Age group range slider (18 to 65+ years).
- **Chat & Message Privacy**:
  - **Photo Verified Chat Only**: When active, only users who completed selfie photo verification can message you.
- **Activity Status Hub**:
  - Live Online / Offline toggle with real-time green/gray status indicator.
  - One-tap navigation to the granular **Active Status Settings** screen.
- **Notifications & Email Hub**:
  - Direct navigation to **Push Notifications** and **Email Subscriptions** management screens.
- **Account Credentials**:
  - Displays registered email, calculated age, date of birth (`DD/MM/YYYY`), gender, and verification badge.
- **Danger Zone**:
  - **Delete My Account**: Prominently styled navigation to the deletion workflow.

### 2.4. Active Status Screen (`ActiveStatusScreen`)
Faithfully implemented from Android reference specifications:
- **Show Active Status Toggle**: Displays whether you were active in Mallu Cupid within the last 2 hours.
- **Show Recently Active Status Toggle**: Displays whether you were active within the last 24 hours.
- Styled in Mallu Cupid dark luxury card layouts with real-time preference persistence.

### 2.5. Email Settings Screen (`EmailSettingsScreen`)
Faithfully implemented from Android reference specifications:
- **Email Address Card**:
  - Displays registered account address with verification badge.
  - Interactive **"Send verification email"** button with automated cooldown and user feedback.
- **Email Subscriptions Card**:
  - Independent toggles for **New matches**, **New messages**, and **Promotions** ("I want to receive news, updates and offers from Mallu Cupid").
- **Unsubscribe From All**:
  - One-tap global opt-out button resetting all subscriptions with feedback toast.

### 2.6. Push Notifications Screen (`PushNotificationsScreen`)
Faithfully implemented from Android reference specifications:
- **Granular Push Toggles**:
  - New matches (*"You just got a new match"*)
  - Messages (*"Someone sent you a new message"*)
  - Message likes (*"Someone liked your message"*)
  - Super Likes (*"You've been Super Liked! Swipe to find out by whom."*)
  - Offers & promotions (*"Receive discounts, offers, promos and other news from Mallu Cupid"*)
- **New Likes Threshold Frequency**:
  - Selectable radio options: **Every 1 new like**, **Every 10 new likes**, **Every 100 new likes**.

### 2.7. Blocked Contacts & Unblock Workflow (`BlockedUsersScreen`)
- **Live Search Field**: Real-time filtering by blocked contact's name or city.
- **Blocked Contact Cards**: Displays user avatar, name, and Kerala city.
- **Two-Step Unblock Confirmation Dialog**: Prevents accidental unblocking by clearly warning that unblocked profiles will be able to see you and match again.

### 2.8. Account Deletion Workflow (`DeleteAccountScreen`)
- **Structured Exit Survey**: Selectable reasons for leaving (*"Found someone special on Mallu Cupid"*, *"Taking a break from dating apps"*, *"Not getting quality matches in Kerala"*, *"Privacy and security concerns"*, *"Starting over with a new profile"*, or *"Other"*).
- **Optional Feedback Box**: Multi-line text field for user feedback.
- **Double Confirmation Dialog**: Permanent deletion warning before account reset and sign-out.

---

## 3. Real-Time Chat & Security Architecture

### 3.1. Main Chat Tray (`ChatViewContent`)
- **Search Matches**: Search bar with real-time match count.
- **New Matches Carousel**: Horizontal story-style tray with verified badges and quick conversation launcher.
- **Active Threads**: Thread preview cards featuring user avatars, name, verified icons, last message snippets, relative timestamps, and **"Your turn"** pill badges.

### 3.2. Media Sharing (Photos & Videos)
- **Zero-Permission Android Photo Picker**: Utilizes modern Android `ActivityResultContracts.PickVisualMedia` for picking images and recorded videos without requesting intrusive `READ_EXTERNAL_STORAGE` permissions.
- **Rich Message Cards**:
  - Photos render with rounded cards and tap-to-view fullscreen modal.
  - Videos render with video thumbnail, overlay play button, and duration indicator (`0:18`, `0:24`).
  - Quick-share presets allow instant testing of high-resolution Munnar scenery photos and Alleppey backwater video clips directly on emulators.
- **Kerala Sticker & GIF Tray**: Fast picker for culturally relevant conversation starters (*"Vanakkam! 🙏"*, *"Chaya koodan undo? ☕"*, *"Fort Kochi vibes 🌴"*, *"Kidu look! ✨"*).
- **Message Heart Reactions**: One-tap heart button beside received message bubbles.

### 3.3. Perfect Working In-Chat Screenshot Prevention (`FLAG_SECURE`)
> [!IMPORTANT]
> **Strict Lifecycle-Scoped Screenshot Prevention**
> As requested, screenshot prevention is applied **strictly inside the active conversation screen only**, leaving the rest of the application free for standard screenshots and sharing.

#### Implementation Detail:
1. When entering an active conversation (`activeChatProfile != null`), a `DisposableEffect` queries the host activity window and sets:
   ```kotlin
   activity?.window?.setFlags(
       WindowManager.LayoutParams.FLAG_SECURE,
       WindowManager.LayoutParams.FLAG_SECURE
   )
   ```
2. While `FLAG_SECURE` is active:
   - Hardware key shortcuts (Power + Volume Down) are rejected by Android OS with *"Can't take screenshot due to security policy"*.
   - Third-party screen recorders capture a blank black frame.
   - App previews in the Android Task Switcher / Recent Apps list are obfuscated.
3. Upon exiting the conversation (back press, gesture, or disposal), `onDispose` immediately clears the flag:
   ```kotlin
   onDispose {
       activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
   }
   ```
4. A prominent privacy banner inside the chat header informs the user:
   *`Screenshot Protected Chat (FLAG_SECURE Active)`*

---

## 4. Face Verification Architecture (`FaceVerificationScreen`)

Faithfully implemented following the Tinder Photo Verification standard:
- **Pose-Matching Liveness Verification**:
  - Requires the user to capture two live selfies matching dynamic pose instructions:
    1. **Pose 1**: Look directly into the camera and smile.
    2. **Pose 2**: Tilt head slightly to the right side.
- **Oval Guide Frame & Animated Scanning Reticle**:
  - Displays a dedicated rounded oval face guide frame with laser scan line animation, corner brackets, and positioning guides.
  - Leverages Android Camera Contract (`ActivityResultContracts.TakePicturePreview()`) with camera runtime permission request (`Manifest.permission.CAMERA`).
  - Includes quick-simulation affordance for emulators lacking physical camera hardware.
- **Multi-Phase Geometry & Anti-Spoofing Analysis**:
  - Phase 1: Scanning 3D facial geometry.
  - Phase 2: Validating liveness and anti-spoofing algorithms.
  - Phase 3: Matching facial landmarks against the user's 6 uploaded Kerala dating photos.
- **Verified Blue Checkmark Awarded**:
  - Automatically activates `isVerified = true` in state upon successful match.
  - Adds the glowing blue verified checkmark icon to the user's avatar, discovery card, and conversation threads.

---

## 5. Mallu Cupid Premium Subscription & Payment Gateway Flow (`PremiumSubscriptionFlow`)

A complete 4-stage monetization and transaction workflow:

### 5.1. Subscription Plan: ₹49 INR Per Week
- **Special Kerala Launch Offer**: ₹49 per week with cancel-anytime flexibility.
- **Core Value Propositions**:
  - 💖 **Unlimited Likes**: Swipe without daily limits on singles across Kerala.
  - 👀 **See Who Likes You**: Instantly unblur all inbound likes and match without waiting.
  - 💬 **Unlimited Chat**: Message matches and share media without limits.
  - ⏪ **Unlimited Rewinds**: Take back accidental left swipes anytime.

### 5.2. Multi-Stage Payment Gateway Flow
1. **Offer Screen (`OFFER`)**:
   - High-contrast terracotta/gold brand card detailing the ₹49/week price tag, perk cards, and "Activate Now" CTA.
2. **Indian Payment Gateway Screen (`PAYMENT_GATEWAY`)**:
   - Order ID generation (`MC_PRM_...`) and order summary (₹49.00).
   - Selection of popular Indian payment options: **Google Pay (UPI)**, **PhonePe**, **Paytm / Any UPI ID (VPA)**, **Debit/Credit Cards (RuPay, Visa, MasterCard)**.
   - Live UPI ID input field with VPA validation.
   - 256-Bit SSL Encrypted Indian Gateway security badge.
3. **Payment Verification Screen (`PAYMENT_VERIFICATION`)**:
   - Authentic banking processing screen with spinning rupee token.
   - Step-by-step progress indicators: *"Requesting bank authorization..."* -> *"Verifying 256-bit token with NPCI..."* -> *"Authorizing ₹49.00 debit..."*.
4. **Payment Success Screen (`PAYMENT_SUCCESS`)**:
   - Green animated celebration checkmark.
   - Transaction reference number (`TXN_UPI_MC_...`).
   - Summary of unlocked premium perks with "Start Exploring with Premium" completion button.

---

## 6. System State Screens (`SystemStateScreens`)

Dedicated, reusable screens ensuring resilient user experiences across all edge cases:

- **Loading Screen (`LoadingStateScreen`)**:
  - Animated pulsing Mallu Cupid logo with smooth dual-ring circular indicator.
  - Displays culturally relevant Kerala dating tips and suggestions (*"Kochi cafes and Munnar photos get 3x more dates"*).
- **No Internet Screen (`NoInternetScreen`)**:
  - Distinctive offline indicator with Wi-Fi disconnected illustration.
  - Interactive "Try Again" button with simulated connectivity check spinner and recovery toast.
  - Optional "Continue Offline" fallback.
- **Error Screen (`ErrorStateScreen`)**:
  - Alert shield styling with clear error descriptions and one-tap recovery retry buttons.
- **Success Screen (`SuccessStateScreen`)**:
  - Celebratory confirmation screen with customizable titles, descriptions, and forward actions.
- **Diagnostics Previewer**:
  - Accessible directly in the Profile settings menu for one-tap live preview and testing of all system screens.

---

## 7. App Launcher Icon Configuration

- **Branded Launcher Icon Asset**: Configured from official production asset:
  - Source: `https://res.cloudinary.com/wxytzoo1/image/upload/v1788918988/MallucupidAppicon.png`
  - High-resolution 512x512 PNG stored in `res/drawable/mallu_cupid_app_icon.png`.
- **Adaptive Icon Layers**:
  - `res/drawable/ic_launcher_foreground.xml`: Centered within 72dp safe zone canvas.
  - `res/drawable/ic_launcher_background.xml`: Styled with Mallu Cupid signature terracotta gradient.
- **Multi-Density Mipmaps**:
  - Generated webp icons across `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, and `xxxhdpi` for both standard and round launcher icons.
  - Manifest configured with `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.

---

## 8. Technology Stack & Dependencies

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (BOM 2024.09.00)
- **Design System**: Material Design 3 (M3)
- **Image Pipeline**: Coil Compose 2.7.0
- **Database & Persistence**: Android Room (Local Cache) + StateFlow / MutableState
- **System APIs**: AndroidX Activity Compose, Photo Picker Contracts, WindowManager Secure Flags
- **Testing**: Robolectric 4.13, Roborazzi 1.26.0

---

## 5. Build, Verification & Testing Commands

To verify and run tests in the development environment:

```bash
# Compile and build debug APK
gradle assembleDebug

# Run local JVM and Robolectric unit tests
gradle :app:testDebugUnitTest

# Verify screenshot regression tests
gradle :app:verifyRoborazziDebug
```

---

*Mallu Cupid — Crafted with love for Kerala singles.*
