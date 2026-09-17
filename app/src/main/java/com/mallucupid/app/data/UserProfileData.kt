package com.mallucupid.app.data

data class PromptItem(
    val question: String,
    val answer: String
)

data class OnboardingDraft(
    val name: String = "",
    val isVerified: Boolean = false,
    val gender: String = "",
    val sexualOrientation: String = "",
    val lookingFor: String = "",
    val relationshipType: String = "",
    val birthDay: String = "",
    val birthMonth: String = "",
    val birthYear: String = "",
    val city: String = "",
    val country: String = "",
    val countryIsoCode: String = "",
    val countryMinAge: Int = 18,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distance: Int = 25,
    val pronouns: String = "",
    val height: String = "",
    val languages: List<String> = emptyList(),
    val photos: List<String> = emptyList(),
    val smartPhotos: Boolean = true,
    val bio: String = "",
    val interests: List<String> = emptyList(),
    val goal: String = "",
    val prompts: List<PromptItem> = listOf(
        PromptItem("", ""),
        PromptItem("", "")
    ),
    val zodiac: String = "",
    val education: String = "",
    val familyPlans: String = "",
    val communicationStyle: String = "",
    val loveStyle: String = "",
    val pets: String = "",
    val drinking: String = "",
    val smoking: String = "",
    val workout: String = "",
    val socialMedia: String = "",
    val college: String = "",
    val courseName: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val maritalStatus: String = "",
    val username: String = "",
    val anthem: String = "",
    val dontShowAge: Boolean = false,
    val dontShowDistance: Boolean = false,
    val superLikesCount: Int = 0,
    val myBoostsCount: Int = 0,
    val ageMin: Int = 18,
    val ageMax: Int = 99,
    val dealBreakers: List<String> = emptyList(),
    val registeredEmail: String = "",
    val interestedIn: List<String> = emptyList(),
    val maxDistanceKm: Int = 50,
    val photoVerifiedOnlyChat: Boolean = true,
    val isOnline: Boolean = false,
    // Active Status Settings (Screenshot 1)
    val showActiveStatus: Boolean = true,
    val showRecentlyActiveStatus: Boolean = true,
    // Email Settings (Screenshot 2)
    val emailVerified: Boolean = true,
    val emailSubMatches: Boolean = true,
    val emailSubMessages: Boolean = true,
    val emailSubPromos: Boolean = true,
    // Push Notifications (Screenshot 3)
    val pushMatches: Boolean = true,
    val pushMessages: Boolean = true,
    val pushMessageLikes: Boolean = true,
    val pushSuperLikes: Boolean = true,
    val pushPromos: Boolean = false,
    val pushLikesFrequency: String = "Every 1 new like",
    val blockedUsers: List<BlockedUser> = emptyList()
) {
    val calculatedAge: Int
        get() {
            val y = birthYear.toIntOrNull() ?: return 0
            return (java.time.LocalDate.now().year - y).coerceIn(0, 100)
        }
}

data class BlockedUser(
    val id: String,
    val name: String,
    val location: String,
    val photoUrl: String
)

data class DatingProfile(
    val id: String,
    val name: String,
    val age: Int,
    val isVerified: Boolean = false,
    val location: String,
    val distanceKm: Int,
    val bio: String,
    val photos: List<String>,
    val profession: String,
    val company: String = "",
    val college: String = "",
    val lookingFor: String = "",
    val essentialsGender: String = "",
    val astrologyStar: String = "",
    val musicAnthem: String = "",
    val communicationStyle: String = "",
    val loveStyle: String = "",
    val education: String = "",
    val drinking: String = "",
    val smoking: String = "",
    val workout: String = "",
    val pets: String = "",
    val prompts: List<PromptItem> = emptyList(),
    val interests: List<String> = emptyList()
)

object SampleProfiles {
    val list = emptyList<DatingProfile>()
}

