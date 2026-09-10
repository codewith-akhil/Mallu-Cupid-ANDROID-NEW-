# Mallu Cupid Android — New UI Sections & QA Requirements

> **Purpose of this document**
> This file lists EVERY newly created UI page/section added to the Mallu Cupid Android app
> (the originals shipped in the clone are NOT listed here). For each new section: what it is,
> why it exists, and where it lives. The second half is a strict end-to-end QA requirement
> for verifying that none of the new UI is broken by overlay/congestion with the Android
> system status bar or system navigation bar, that there are no colour-code or design
> mismatches, and that no over-gaps or broken button sizes were introduced.
>
> Any deviation from the QA contract below MUST be fixed end-to-end. No partial fixes.
> No blind fixes. Every fix must be verified against the same contract before commit.

---

## Part 1 — Newly Created UI Sections (NOT in the original clone)

### 1.1 Authentication & Onboarding

#### 1.1.1 Password Show/Hide Eye Toggle
- **File:** `app/src/main/java/com/mallucupid/app/ui/screens/AuthScreens.kt`
- **Section:** `AuthTextField` trailing icon (SignIn + SignUp password fields)
- **What:** A trailing eye icon (`Icons.Default.Visibility` / `Icons.Default.VisibilityOff`)
  toggles `PasswordVisualTransformation` on/off for every password field.
- **Why:** Prevents typos during sign-in/sign-up without forcing the user to retype;
  standard modern auth UX. Reduces failed logins.

#### 1.1.2 Password Strength Meter
- **File:** `AuthScreens.kt`
- **Section:** 4-segment coloured bar + tier label rendered below the password field
  (only on SignUp, when password is non-empty)
- **What:** 4 thin segments light up by tier — Weak (1, `NopeCoral`), Fair (2, `TinderGold`),
  Good (3, `TinderGreen`), Strong (4, `SuperBlue`). A label ("Weak"/"Fair"/"Good"/"Strong")
  sits to the right in the matching colour.
- **Why:** nudges users toward stronger passwords at creation time; reduces account-takeover risk.

#### 1.1.3 Inline Field Validation
- **File:** `AuthScreens.kt`
- **Section:** `isError`/`errorText` params on `AuthTextField` → red `NopeCoral` border + helper text
- **What:** On submit attempt, invalid fields get a `NopeCoral` border and a helper line below
  ("Enter a valid email address", "Minimum 6 characters", "Name is required").
- **Why:** Immediate, field-local feedback is faster and less hostile than a single Toast;
  errors auto-clear on next input.

#### 1.1.4 Auto-Advance 6-Box OTP Input
- **File:** `app/src/main/java/com/mallucupid/app/ui/screens/OtpAndResetScreens.kt`
- **Section:** `OtpVerificationScreen` — 6 individual digit boxes
- **What:** 6 `OutlinedTextField`s (52dp each, spacedBy 12dp, centred). On digit entry focus
  auto-jumps to the next box; on delete it jumps back. When all 6 are filled, verify auto-fires
  after 200ms.
- **Why:** removes the clunky single-field OTP entry; matches the OTP UX every modern app ships.

#### 1.1.5 OTP Resend Countdown Timer
- **File:** `OtpAndResetScreens.kt`
- **Section:** "Resend code" TextButton below the OTP boxes
- **What:** 60-second countdown ("Resend code in 0:59" → "Resend code in 0:00"). Disabled while
  counting. On reaching 0 the button enables in `AccentPink`; click restarts the countdown,
  clears the boxes, fires a Toast.
- **Why:** prevents abuse of resend; gives the user a clear, time-bounded recovery path.

#### 1.1.6 Live Mini Profile Preview Card
- **File:** `app/src/main/java/com/mallucupid/app/ui/screens/OnboardingScreen.kt`
- **Section:** `LiveProfilePreviewCard(draft)` shown on steps 4 (Photos), 5 (Basics), 6 (Interests), 8 (Prompts)
- **What:** A compact dating-card preview (DashboardCard surface, 20dp radius) that mirrors the
  in-progress `draft`: 80dp circular first photo with terracotta border, name + age, one-line bio,
  up to 4 interest chips, first prompt Q+A. Two-pane (form | preview) on wide screens (≥600dp);
  sticky-at-top on compact screens.
- **Why:** users can see how their profile will look to others WHILE building it — Tinder does
  this and it materially improves completion + bio quality.

#### 1.1.7 Photo Drag-to-Reorder Handle
- **File:** `OnboardingScreen.kt` (Step4Photos) + `EditProfileScreen.kt` (photo manager grid)
- **Section:** Long-press-drag affordance on every filled photo slot
- **What:** `detectDragGesturesAfterLongPress` on each filled slot. A drag handle icon
  (`Icons.Default.DragHandle`) sits at the top-left in a small dark circle. Dragging visually
  lifts the slot (graphicsLayer translation + shadow + alpha); on drop the photo swaps into the
  target slot and `draft.photos` is reordered.
- **Why:** previously users could only add/remove — they could not fix a wrong photo order
  without deleting and re-adding. Drag-reorder is the standard fix.

#### 1.1.8 Primary Photo Star Badge
- **File:** `OnboardingScreen.kt` + `EditProfileScreen.kt`
- **Section:** Gold star badge on slot index 0
- **What:** `Icons.Default.Star` tinted `TinderGold` in a small circle at the top-right of the
  first photo slot, with a tiny "Primary" label. The first photo is implicitly the primary
  (the one shown in swipe cards, match modal, chat avatar).
- **Why:** makes it obvious which photo is the "face" of the profile; removes the previous
  ambiguity where slot 0 had no visual distinction.

### 1.2 Discovery / Swipe

#### 1.2.1 First-Launch Drag-Hint Overlay
- **File:** `app/src/main/java/com/mallucupid/app/ui/screens/TinderSwipeableCardStack.kt`
- **Section:** Full-card overlay shown once on first launch
- **What:** Semi-transparent black layer (alpha 0.45) over the active card with an animated
  "👆" finger translating left↔right (1.2s infinite), "Swipe right to like 👉" headline,
  "Swipe left to pass" subline. Auto-dismisses after 4s or on tap. Persisted via
  `rememberSaveable` so it shows only once.
- **Why:** new users had no affordance that the card was draggable. This onboards them in 4s.

#### 1.2.2 Verified Blue Badge on Swipe Card
- **File:** `TinderSwipeableCardStack.kt`
- **Section:** Small `SuperBlue` circle with white check, beside the name on the active card
- **What:** When `profile.isVerified` is true, an 18dp `SuperBlue` circle (2dp white border)
  with a 12dp `Icons.Default.Check` renders immediately after the name/age row.
- **Why:** the data model had `isVerified` but the card never showed it. Verified badges drive
  match-rate and trust.

#### 1.2.3 Online Green Dot with Pulse Animation
- **File:** `TinderSwipeableCardStack.kt`
- **Section:** "Recently active" pill on the active card
- **What:** The dot in the "Recently active" badge was terracotta; it is now `TinderGreen`
  (#20D5A3) with an `infiniteTransition`-driven outer ring scaling 0.95→1.3 with fading
  opacity behind the solid dot.
- **Why:** a pulsing green dot is the universal "online now" signal; the previous static
  terracotta dot read as a generic bullet.

#### 1.2.4 Photo x/y Counter
- **File:** `TinderSwipeableCardStack.kt`
- **Section:** Small text beside the segmented photo progress bar
- **What:** `"${photoIndex + 1} / ${profile.photos.size}"` in `DashboardCream` alpha 0.8, 11sp,
  right-aligned below the progress bar.
- **Why:** segmented bars alone don't tell users how many photos exist; the counter orients them.

#### 1.2.5 Pull-to-Refresh on Card Stack
- **File:** `TinderSwipeableCardStack.kt`
- **Section:** Vertical-drag detector on the outer Box (active when stack is empty)
- **What:** `detectVerticalDragGestures` accumulates a `pullDistance`; a terracotta
  `CircularProgressIndicator` (28dp) fades in at TopCenter (below the status bar via the parent's
  `statusBarsPadding`). On release past 220dp threshold, `onResetStack()` runs after a 650ms
  simulated refresh.
- **Why:** no refresh affordance existed; when the stack was exhausted the only path was "Start Over".
  Pull-to-refresh is the expected recovery gesture.

### 1.3 Chat

#### 1.3.1 Typing Indicator (3 Animated Dots)
- **File:** `app/src/main/java/com/mallucupid/app/ui/screens/ChatViewContent.kt`
- **Section:** Left-aligned partner bubble at the bottom of the message list
- **What:** When `partnerIsTyping` is true, a `DashboardCard` bubble (16dp radius, 12dp padding)
  renders 3 dots (6dp circles, `DashboardMutedBeige`) each scaling 0.6↔1.0 with staggered
  delays (0/200/400ms). Triggered for ~1.5s after the user sends a text, then a canned reply
  arrives.
- **Why:** a silent chat feels broken; the typing dots make it feel alive and signal an
  imminent reply.

#### 1.3.2 Read Receipts (✓ / ✓✓)
- **File:** `ChatViewContent.kt`
- **Section:** Bottom-right of sender bubbles
- **What:** `isRead` field on `ChatMessageItem`. Single `✓` in `DashboardNavMuted` (12sp) when
  not read; double `✓✓` in `TinderBlue` (11sp) when read. The most-recent sender message flips
  to read ~2s after the partner replies.
- **Why:** absent read receipts made the chat feel like a black box.

#### 1.3.3 Date Separators
- **File:** `ChatViewContent.kt`
- **Section:** Centred pills interleaved in the message list
- **What:** Messages are grouped by day (parsed from their timestamp strings). A centred pill
  (`Color.White` alpha 0.08, 50% radius, 4×8 padding) with the day label ("Today" / "Yesterday"
  / "Wednesday 28 Aug") is inserted between groups.
- **Why:** inline timestamps don't give temporal structure; date separators do.

#### 1.3.4 Scroll-to-Bottom FAB + Unread Badge
- **File:** `ChatViewContent.kt`
- **Section:** 44dp circular FAB bottom-right of the conversation (above the input bar)
- **What:** `Icons.Default.KeyboardArrowDown` in a `DashboardCard` circle with a `TinderCoral`
  unread-count badge. Visible only when `listState.firstVisibleItemIndex > 0`. Click animates
  scroll to the last message.
- **Why:** long conversations had no way back to the latest message.

#### 1.3.5 Long-Press Emoji Reaction Popover
- **File:** `ChatViewContent.kt`
- **Section:** Popover above a long-pressed message bubble
- **What:** `Modifier.combinedClickable(onLongClick=...)` on every bubble opens a small
  `DashboardCard` popover with 6 emojis (👍 ❤️ 😂 😮 😢 🙏, 24sp). Selecting one sets
  `message.reaction`; a tiny pill below the bubble shows the chosen emoji.
- **Why:** previously only a heart on received messages; reactions are core to modern chat UX.

#### 1.3.6 Reply-To / Quote
- **File:** `ChatViewContent.kt`
- **Section:** "Reply" button in the reaction popover + quoted-preview bar above the input + quoted block on sent bubble
- **What:** Tapping "Reply" sets `replyTarget`. A slim bar appears above the input (terracotta
  accent + sender name + truncated text + ✕ cancel). On send, the new message carries
  `replyToText`/`replyToSenderName`; the sent bubble shows a quoted block (3dp terracotta bar +
  `DashboardPeach` name + `DashboardMutedBeige` quoted text, maxLines 1).
- **Why:** reply-to is table stakes for any chat; without it context is lost in busy threads.

#### 1.3.7 Voice Message Recording + Waveform Bubble
- **File:** `ChatViewContent.kt`
- **Section:** Mic button (input empty) → recording bar → VOICE bubble
- **What:** When `chatMessageInput` is blank the send button becomes a 44dp mic
  (`Icons.Default.Mic`). Long-press starts a recording bar (pulsing `AccentPink` dot + timer
  "0:0X" + Cancel + Send). Release-Send pushes a `VOICE` message with `audioDuration`. VOICE
  bubbles render a play button (terracotta circle) + a 20-bar fake waveform
  (`DashboardPeach`/`DashboardTerracotta`) + duration label.
- **Why:** voice messages are expected in modern dating chat; they lower the friction of
  first-message composition. **Simulated** (coroutine timer) — `MediaRecorder` integration is
  flagged `// TODO` for the build step.

#### 1.3.8 Message Requests Tab
- **File:** `ChatViewContent.kt`
- **Section:** "Matches" | "Requests" tab row in the chat tray
- **What:** A 2-tab row below the search bar. "Matches" shows existing threads. "Requests"
  shows a separate list with "Accept" (`DashboardTerracotta` pill) and "Block" (outlined
  `NopeCoral`) buttons per item. Accept/Block removes the request with a Toast.
- **Why:** all matches previously showed equally; there was no separation of inbound requests
  from established matches, which is a safety + organisation concern.

### 1.4 Face Verification

#### 1.4.1 Camera Flip Button (Front/Back)
- **File:** `app/src/main/java/com/mallucupid/app/ui/screens/FaceVerificationScreen.kt`
- **Section:** 44dp dark circle, top-right of the camera preview
- **What:** `Icons.Default.Cameraswitch` toggles `useFrontCamera`. Shows a Toast confirming
  the switch. **Simulated** — `ActivityResultContracts.TakePicturePreview()` can't select
  camera natively; real switching needs CameraX (`// TODO` flagged).

#### 1.4.2 Lighting Check Overlay
- **File:** `FaceVerificationScreen.kt`
- **Section:** Reserved-height Box below the oval guide
- **What:** On entering each pose stage, a 1-second simulated pass runs: a 20dp
  `CircularProgressIndicator` (`DashboardTerracotta`) + "Checking lighting…" text
  (`DashboardCream`, 13sp). Capture buttons are disabled during the check. **Simulated** —
  real luminance needs CameraX `ImageAnalysis` (`// TODO` flagged).

#### 1.4.3 Retake / Use Photo Buttons
- **File:** `FaceVerificationScreen.kt`
- **Section:** Bottom action row after a pose capture
- **What:** After capturing a pose, two buttons appear: "Retake" (outlined, `DashboardCream`)
  restarts the current pose capture; "Use Photo" (`DashboardTerracotta` filled) advances to the
  next pose or the analysis phase.
- **Why:** previously a capture was committed immediately with no second chance; retake is
  essential for a verification flow users trust.

#### 1.4.4 Privacy Explainer Card
- **File:** `FaceVerificationScreen.kt`
- **Section:** Slim card at the top of the CAMERA_POSE stages
- **What:** `DashboardCard` surface, 14dp radius, 1dp `SuperBlue` alpha 0.4 border, 12dp padding.
  Row: `Icons.Default.Lock` (`SuperBlue`, 18dp) + Column with "Your selfies are never stored"
  (`DashboardCream`, 13sp Bold) + "Used only for one-time verification. Deleted immediately
  after." (`DashboardMutedBeige`, 11sp).
- **Why:** verification flows lose users at the camera step without explicit trust signals.

### 1.5 Dashboard Shell

#### 1.5.1 Haptic + Pill Animation on Bottom Nav Tab Switch
- **File:** `app/src/main/java/com/mallucupid/app/ui/screens/DashboardScreen.kt`
- **Section:** Bottom nav `forEach` loop
- **What:** Each nav item is wrapped in a `Surface` with a sliding pill background
  (`DashboardTerracotta` alpha 0.18) that animates via `animateColorAsState(spring)`. Icon and
  label colours animate via `animateColorAsState` (no more if/else snap). On click,
  `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` fires
  BEFORE the tab switch.
- **Why:** a static colour-swap nav feels dated; the animated pill + haptic makes the shell
  feel responsive and premium.

#### 1.5.2 Unread Badge on Chat Nav Item
- **File:** `DashboardScreen.kt`
- **Section:** Top-right of the 💬 nav icon
- **What:** A 14dp `TinderCoral` circle with a `DashboardBg` border showing a white count ("2",
  8sp) at the top-right of the chat icon. (Static demo count — wiring to real unread state is
  flagged `// TODO`.)
- **Why:** the nav had no unread indicator; users had no at-a-glance signal of pending chats.

---

## Part 2 — End-to-End QA Requirement

> **Contract:** Every newly created UI section above MUST pass ALL of the checks below.
> If ANY check fails, fix the root cause end-to-end (no partial fixes, no blind fixes),
> re-verify, then commit. A passing build is NOT sufficient — visual + interaction QA is
> required.

### 2.1 Status Bar & Navigation Bar Overlay / Congestion (CRITICAL)

**The single most important contract.** The app uses `enableEdgeToEdge()` in `MainActivity`,
which means the app draws UNDER the system status bar (top) and system navigation bar (bottom)
by default. Every screen is responsible for padding itself away from those regions.

**Mandatory checks for every new section:**

1. **Status bar (top) clearance:**
   - Any new top-aligned overlay (drag-hint, FAB, popover, sheet header) MUST be a child of
     a container that already applies `.statusBarsPadding()`, OR apply `.statusBarsPadding()`
     itself.
   - The DashboardScreen root `Box` (around line 247) applies
     `.statusBarsPadding().navigationBarsPadding()` — all new dashboard-level overlays must
     live INSIDE this Box and inherit its padding.
   - FaceVerificationScreen's `Scaffold` topBar applies `.statusBarsPadding()` — the camera
     flip button and privacy card must sit inside the Scaffold content with `innerPadding`.
   - AuthScreens' `AuthBackground` applies `.statusBarsPadding().navigationBarsPadding().imePadding()`
     — the password eye, strength meter, and error text must be children of that container.
   - **FAIL CONDITION:** any new element whose top edge renders at y < status bar height.

2. **Navigation bar (bottom) clearance:**
   - Any new bottom-aligned element (FAB, recording bar, reply bar, input-bar children) MUST
     apply `.navigationBarsPadding()` and, where a keyboard is involved, `.imePadding()`.
   - The ChatViewContent conversation input Surface's inner Column uses
     `.navigationBarsPadding().imePadding()` — the reply-preview bar, voice recording bar,
     mic button, and scroll-to-bottom FAB must all be children of (or inherit from) this
     inset-padded container.
   - **FAIL CONDITION:** any new element whose bottom edge renders behind the system nav bar
     or is obscured when the keyboard is open.

3. **No new `Scaffold` with `contentWindowInsets` overrides.**
   - The existing inset pattern (per-screen `statusBarsPadding`/`navigationBarsPadding`) must
     NOT be replaced by a `Scaffold` that sets `contentWindowInsets = WindowInsets(0,0,0,0)`
     or similar. Such a change would "fuck up" the whole app's alignment.

4. **No removal of existing inset modifiers.**
   - Diff every modified file's root container. If a `statusBarsPadding()` or
     `navigationBarsPadding()` call that existed in the original clone has been removed,
     that is a CRITICAL regression and must be restored.

### 2.2 Colour-Code & Design Mismatch

**The colour palette is fixed in `Color.kt`. No new colours may be invented.**

**Valid tokens (the ONLY bare-identifier colours allowed):**
```
PrimaryRed, DarkMaroon, AccentPink, SoftPink, CreamBackground, DarkText, MutedText, GlassWhite,
DashboardBg, DashboardCard, DashboardCream, DashboardTerracotta, DashboardPeach,
DashboardMutedBeige, DashboardNavMuted, RewindGold, NopeCoral, SuperBlue, LikeRose,
BoostChampagne, TinderBg, TinderSurface, TinderTextPrimary, TinderTextSecondary,
TinderTextMuted, TinderBorder, TinderCoral, TinderGold, TinderPlatinum, TinderBlue,
TinderGreen, TinderPurple
```

**Checks:**

1. **No invented tokens.** Grep every modified file for `[A-Z][a-zA-Z]+` used as a `Color`
   argument; every match MUST be in the list above. Any unknown identifier is a compile error
   OR a colour mismatch and must be replaced with the closest valid token.
2. **Hardcoded `Color(0x…)` literals are ALLOWED** — the original codebase uses them
   extensively (e.g. `Color(0xF22A211D)` for dark circle backgrounds). These are NOT
   mismatches; they match the existing design language.
3. **Token-vs-context sanity:**
   - Verified badge MUST be `SuperBlue` (per docs).
   - Online/success dot MUST be `TinderGreen`.
   - Danger/delete/block MUST be `NopeCoral`.
   - Primary action buttons MUST be `DashboardTerracotta`.
   - Body text on dark surfaces MUST be `DashboardCream` / `DashboardMutedBeige`.
   - Muted labels MUST be `DashboardNavMuted`.
   - Any deviation must be justified by the design intent of the section.

### 2.3 Over-Gap Spaces & Broken Button Sizes

**Checks:**

1. **Touch targets ≥ 44dp.** Every clickable element (IconButton, `Surface(onClick=…)`,
   `Box { .clickable { } }`, `Row { .clickable { } }`) MUST have a touch zone of at least
   44×44dp.
   - For `IconButton`: remove explicit `.size(<44.dp)` OR set to ≥44dp. IconButton's default
     is 48dp.
   - For `Surface(onClick=…)` and clickable `Box`/`Row`: set `.size(≥44.dp)` or
     `.heightIn(min = 44.dp)` + `.widthIn(min = 44.dp)`, OR bump inner padding so intrinsic
     size ≥ 44dp.
   - **Already fixed in this pass:** 12 under-sized touch targets (2 OnboardingScreen photo
     remove buttons, 1 EditProfileScreen delete button, 8 ChatViewContent buttons [FAB, reply
     cancel, voice cancel, voice send, mic, reply popover, accept, block], 1 FaceVerification
     camera flip). All now ≥ 44dp.

2. **No accidental negative / zero padding** that would collapse spacing or invert layout.
   Grep for `.padding(-` and `Spacer(Modifier.height(0.dp)` / `width(0.dp)` in new code.

3. **No `fillMaxWidth` on fixed-width children** that would overflow a Row. Any Row containing
   fixed-width children must either use `weight()` on the flexible child or wrap in
   `horizontalScroll`.

4. **Spacing rhythm matches neighbours.** New sections must use the same `spacedBy` /
   `padding` values as the surrounding composables. A new card with 4dp padding inside a
   screen of 16dp-padded cards is a mismatch.

5. **No orphaned `Spacer` with huge heights** that would create visible gaps. Grep for
   `Spacer(modifier = Modifier.height(` in new code and verify each value is reasonable
   (≤ 40dp for inter-element; the existing app uses 6–28dp typically).

### 2.4 Compile-Blocker Risks (static, since no build is available in this env)

1. **All `Icons.Default.*` names must exist** in `material-icons-extended` (declared in
   `app/build.gradle.kts`). Verified in this pass:
   - `Visibility`, `VisibilityOff`, `Check`, `Close`, `DragHandle`, `Star`, `Person`,
     `KeyboardArrowDown`, `Mic`, `PlayArrow`, `Cameraswitch`, `Lock`, `Favorite`, `Send`
     (auto-mirrored), `Reply`, `KeyboardArrowUp`, `KeyboardArrowDown`, `Edit`, `Settings`,
     `CameraAlt`, `CheckCircle`, `Star`, `Lock`, `Widgets`, `Info`.
2. **All experimental APIs are opted into:**
   - `@OptIn(ExperimentalMaterial3Api::class)` for `ModalBottomSheet`, `tabIndicatorOffset`,
     `Surface(onClick=…)` in some contexts.
   - `@OptIn(ExperimentalLayoutApi::class)` for `FlowRow`, `BoxWithConstraints`-based layout.
   - `@OptIn(ExperimentalFoundationApi::class)` for `combinedClickable`.
3. **No new dependencies were added.** All features use APIs already in the Compose BOM
   2024.09.00 + `material-icons-extended` + `coil-compose`.

### 2.5 Simulated / TODO-Flagged Features

The following are visually complete but functionally simulated (because the real
implementation needs native APIs / new dependencies that require build-time integration).
They are flagged with `// TODO:` comments in the code and MUST be wired to real APIs before
production:

| Feature | Simulation | Real API needed |
|---|---|---|
| Camera flip (FaceVerification) | Toggles state + Toast | `androidx.camera` (CameraX) — `CameraSelector` front/back |
| Lighting check (FaceVerification) | 1s simulated pass, always succeeds | CameraX `ImageAnalysis` + luminance YUV averaging |
| Voice recording (Chat) | Coroutine timer + fake waveform | `MediaRecorder` + `RECORD_AUDIO` permission |
| Pull-to-refresh (Swipe) | Manual `detectVerticalDragGestures` | `PullToRefreshBox` (Material3 1.3+) — kept manual to avoid an uncertain import |
| Unread badge count (Dashboard nav) | Static "2" | Real unread-chats state from a ChatViewModel |

These simulations do NOT violate the inset / colour / size contracts above.

---

## Part 3 — Verification Checklist (run before every commit)

For each newly created UI section, verify:

- [ ] Does not draw into the status bar (top).
- [ ] Does not draw into the navigation bar (bottom).
- [ ] Respects `.imePadding()` where a keyboard can appear.
- [ ] Uses only valid colour tokens (or hardcoded `Color(0x…)` matching the existing style).
- [ ] Every clickable element has a ≥ 44dp touch target.
- [ ] Spacing/padding matches neighbouring composables.
- [ ] No experimental API is used without the corresponding `@OptIn`.
- [ ] No new dependency was added without updating `build.gradle.kts`.
- [ ] App name "Mallu Cupid", package `com.mallucupid.app`, theme token names, logo URL, and
      the Cloudinary logo asset are unchanged.
- [ ] No Kerala / Malayalam / Mallu cultural copy remains in user-visible strings (global
      relabel complete — verified by grep returning zero matches).

If ANY check fails, fix end-to-end before commit. No partial fixes. No blind fixes.
