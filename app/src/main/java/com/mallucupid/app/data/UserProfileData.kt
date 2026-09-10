package com.mallucupid.app.data

data class PromptItem(
    val question: String,
    val answer: String
)

data class OnboardingDraft(
    val name: String = "Akhil P",
    val isVerified: Boolean = true,
    val gender: String = "Man",
    val sexualOrientation: String = "Straight",
    val lookingFor: String = "Long-term partner",
    val relationshipType: String = "Open to exploring",
    val birthDay: String = "15",
    val birthMonth: String = "08",
    val birthYear: String = "1998",
    val city: String = "Kochi, Kerala, India",
    val distance: Int = 25,
    val pronouns: String = "He",
    val height: String = "165 cm",
    val languages: List<String> = listOf("English", "Malayalam"),
    val photos: List<String> = listOf(
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=800&q=80"
    ),
    val smartPhotos: Boolean = true,
    val bio: String = "Back in Kochi again 👋 This time looking for something genuine and meaningful. Tech founder, filter coffee enthusiast, and weekend explorer.",
    val interests: List<String> = listOf("Entrepreneurship", "Exchange programme", "Foodie", "Travel", "Music", "Art"),
    val goal: String = "Long-term partner",
    val prompts: List<PromptItem> = listOf(
        PromptItem("My hidden talent is:", "Saying 'last reel' and watching 47 more"),
        PromptItem("If I'm not home, you can find me:", "Nearest park or quiet specialty cafe with good espresso"),
        PromptItem("A surprising thing about me is:", "Talk a lot once I'm comfortable 😄")
    ),
    val zodiac: String = "Leo",
    val education: String = "On a graduate programme",
    val familyPlans: String = "Not sure yet",
    val communicationStyle: String = "Better in person",
    val loveStyle: String = "Time together",
    val pets: String = "Pet-free",
    val drinking: String = "Not for me",
    val smoking: String = "Non-smoker",
    val workout: String = "Sometimes",
    val socialMedia: String = "Passive scroller",
    val college: String = "Govt Polytechnic College",
    val courseName: String = "B.Tech Computer Science",
    val jobTitle: String = "Founder",
    val company: String = "METRIC FLUX SOLUTIONS PVT LTD",
    val maritalStatus: String = "Single",
    val username: String = "akhil_p",
    val anthem: String = "Janice STFU · Drake",
    val dontShowAge: Boolean = false,
    val dontShowDistance: Boolean = false,
    val superLikesCount: Int = 0,
    val myBoostsCount: Int = 0,
    val ageMin: Int = 21,
    val ageMax: Int = 33,
    val dealBreakers: List<String> = listOf("Smoking"),
    // Account & Discovery Settings
    val registeredEmail: String = "akhiakmtr12@gmail.com",
    val interestedIn: List<String> = listOf("Women"), // Women, Men, Transmen, Transwomen, Couples, Anyone
    val maxDistanceKm: Int = 50,
    val photoVerifiedOnlyChat: Boolean = true,
    val isOnline: Boolean = true,
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
    val blockedUsers: List<BlockedUser> = listOf(
        BlockedUser("blk_1", "Deepak Nair", "Kochi", "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=400&q=80"),
        BlockedUser("blk_2", "Sneha Menon", "Thrissur", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80"),
        BlockedUser("blk_3", "Rahul Rajan", "Trivandrum", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80")
    )
) {
    val calculatedAge: Int
        get() {
            val y = birthYear.toIntOrNull() ?: 1998
            return (2026 - y).coerceIn(18, 99)
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
    val isVerified: Boolean = true,
    val location: String,
    val distanceKm: Int,
    val bio: String,
    val photos: List<String>,
    val profession: String,
    val company: String = "",
    val college: String = "",
    val lookingFor: String = "Long-term partner",
    val essentialsGender: String = "Woman",
    val astrologyStar: String = "Libra",
    val musicAnthem: String = "Darshana · Hridayam",
    val communicationStyle: String = "Better in person",
    val loveStyle: String = "Presents",
    val education: String = "Bachelor degree",
    val drinking: String = "Not for me",
    val smoking: String = "Non-smoker",
    val workout: String = "Sometimes",
    val pets: String = "Don't have, but love",
    val prompts: List<PromptItem> = emptyList(),
    val interests: List<String> = emptyList()
)

object SampleProfiles {
    val samplePhotoPool = listOf(
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&w=800&q=80",
        "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=800&q=80"
    )

    val list = listOf(
        DatingProfile(
            id = "0",
            name = "Remy",
            age = 39,
            isVerified = true,
            location = "Kochi · 6 km away",
            distanceKm = 6,
            bio = "I'm a simple person with a slightly complicated brain. I overthink everything, laugh at the most random things, and ask way too many questions. I love good conversations, good food, spontaneous plans, and people who can keep up with my thoughts.",
            photos = listOf(
                "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1508214751196-bcfd4ca60f91?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1502685104226-ee32379fefbe?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e?auto=format&fit=crop&w=900&q=85"
            ),
            profession = "Product Consultant",
            lookingFor = "Long-term partner",
            essentialsGender = "Woman",
            astrologyStar = "Scorpio",
            musicAnthem = "Pavizha Mazha · Athiran",
            communicationStyle = "Better in person",
            loveStyle = "Quality time",
            education = "Master's degree",
            drinking = "Socially",
            smoking = "Non-smoker",
            workout = "Sometimes",
            pets = "Dog lover",
            prompts = listOf(
                PromptItem("A surprising thing about me is:", "I overthink everything and ask way too many questions 😄"),
                PromptItem("My simple pleasures in life:", "Filter coffee, slow rains, and spontaneous late drives.")
            ),
            interests = listOf("Travel", "Filter Coffee", "Music", "Conversations", "Foodie", "Books")
        ),
        DatingProfile(
            id = "1",
            name = "Misba",
            age = 22,
            isVerified = false, // Set to false to showcase Unverified badge in First Impression!
            location = "Kochi · 6 km away",
            distanceKm = 6,
            bio = "Art student who loves experimenting with watercolors, indie acoustic tracks, and late night chai discussions.",
            photos = listOf(
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=900&q=85"
            ),
            profession = "Fine Arts & Design",
            lookingFor = "Long-term partner",
            essentialsGender = "Woman",
            astrologyStar = "Libra",
            musicAnthem = "Golden Hour · JVKE",
            communicationStyle = "Better in person",
            loveStyle = "Presents",
            education = "Bachelor degree",
            drinking = "Not for me",
            smoking = "Non-smoker",
            workout = "Sometimes",
            pets = "Don't have, but love",
            prompts = listOf(
                PromptItem("A surprising thing about me is:", "Talk a lot once I'm comfortable 😄"),
                PromptItem("My simple pleasures in life:", "Sunset at Fort Kochi beach and freshly brewed sulaimani.")
            ),
            interests = listOf("Art", "Painting", "Drawing", "Foodie", "Travel", "Museums")
        ),
        DatingProfile(
            id = "2",
            name = "Resh",
            age = 32,
            isVerified = true,
            location = "Calicut · 146 km away",
            distanceKm = 146,
            bio = "Looking for fun moments, spontaneous weekend trips, and someone who appreciates good banter.",
            photos = listOf(
                "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=85"
            ),
            profession = "Marketing Strategist",
            lookingFor = "Short-term fun",
            essentialsGender = "Woman",
            astrologyStar = "Leo",
            musicAnthem = "Aalroopam · Thallumaala",
            communicationStyle = "Replies quickly",
            loveStyle = "Quality time",
            education = "Master's degree",
            drinking = "Socially",
            smoking = "Non-smoker",
            workout = "Regularly",
            pets = "Dog lover",
            prompts = listOf(
                PromptItem("My ideal first date:", "Rooftop lounge, tapas, and good live music.")
            ),
            interests = listOf("Travel", "Cocktails", "Music", "Road trips", "Photography")
        ),
        DatingProfile(
            id = "3",
            name = "HINASH",
            age = 23,
            isVerified = true,
            location = "Kozhikode · 24h left",
            distanceKm = 18,
            bio = "Fashion graduate & modest stylist. Tea over coffee, always.",
            photos = listOf(
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85"
            ),
            profession = "Fashion Stylist",
            lookingFor = "Long-term partner",
            essentialsGender = "Woman",
            astrologyStar = "Taurus",
            musicAnthem = "Pavizha Mazha · Athiran",
            communicationStyle = "Better in person",
            loveStyle = "Acts of service",
            education = "Degree in Fashion",
            drinking = "Not for me",
            smoking = "Non-smoker",
            workout = "Active",
            pets = "Cat lover",
            prompts = listOf(
                PromptItem("The quickest way to my heart is:", "Remembering the little details.")
            ),
            interests = listOf("Design", "Fashion", "Coffee", "Travel")
        ),
        DatingProfile(
            id = "4",
            name = "Zainab",
            age = 30,
            isVerified = true,
            location = "Ernakulam · 24h left",
            distanceKm = 12,
            bio = "Curator & architect. Love minimalist aesthetics and cozy bookstores.",
            photos = listOf(
                "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=900&q=85",
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85"
            ),
            profession = "Architectural Designer",
            lookingFor = "Serious commitment",
            essentialsGender = "Woman",
            astrologyStar = "Virgo",
            musicAnthem = "Darshana · Hridayam",
            communicationStyle = "Thoughtful texter",
            loveStyle = "Deep talks",
            education = "Bachelor of Architecture",
            drinking = "Not for me",
            smoking = "Non-smoker",
            workout = "Pilates",
            pets = "Pet-free",
            prompts = listOf(
                PromptItem("A non-negotiable for me is:", "Kindness and emotional maturity.")
            ),
            interests = listOf("Architecture", "Coffee", "Books", "Art")
        ),
        DatingProfile(
            id = "5",
            name = "Aparna",
            age = 30,
            isVerified = true,
            location = "Trivandrum · 12h left",
            distanceKm = 24,
            bio = "Doctor by training, classical dancer by heart. Finding calm in life's rhythm.",
            photos = listOf(
                "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=900&q=85"
            ),
            profession = "Physician",
            lookingFor = "Long-term partner",
            essentialsGender = "Woman",
            astrologyStar = "Cancer",
            musicAnthem = "Nee Himamazhayayi",
            communicationStyle = "Calls over texts",
            loveStyle = "Affection",
            education = "MBBS, MD",
            drinking = "Not for me",
            smoking = "Non-smoker",
            workout = "Yoga daily",
            pets = "Has a Golden Retriever",
            prompts = listOf(
                PromptItem("I'm looking for someone who:", "Can hold a deep conversation and make me laugh.")
            ),
            interests = listOf("Classical Dance", "Yoga", "Medicine", "Nature")
        ),
        DatingProfile(
            id = "6",
            name = "Unnati",
            age = 22,
            isVerified = true,
            location = "Kochi · 12h left",
            distanceKm = 5,
            bio = "Psychology student & podcaster. Forever chasing sunsets and good conversations.",
            photos = listOf(
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=85"
            ),
            profession = "Student / Creator",
            lookingFor = "Coffee date",
            essentialsGender = "Woman",
            astrologyStar = "Gemini",
            musicAnthem = "Levitating · Dua Lipa",
            communicationStyle = "Voice notes",
            loveStyle = "Words of affirmation",
            education = "Undergraduate",
            drinking = "Socially",
            smoking = "Non-smoker",
            workout = "Gym 3x/week",
            pets = "Love pets",
            prompts = listOf(
                PromptItem("A surprising fact:", "I've recorded over 50 podcast episodes!")
            ),
            interests = listOf("Podcasts", "Psychology", "Beach days", "Baking")
        ),
        DatingProfile(
            id = "7",
            name = "Zappy",
            age = 25,
            isVerified = true,
            location = "Kochi",
            distanceKm = 8,
            bio = "Content creator & traveler. Always with a camera in hand.",
            photos = listOf(
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=900&q=85"
            ),
            profession = "Photographer",
            lookingFor = "Free tonight",
            essentialsGender = "Man",
            astrologyStar = "Sagittarius",
            musicAnthem = "Blinding Lights · The Weeknd",
            communicationStyle = "Casual & fun",
            loveStyle = "Adventures",
            education = "Media Studies",
            drinking = "Socially",
            smoking = "Non-smoker",
            workout = "Skateboarding",
            pets = "Dog lover",
            prompts = emptyList(),
            interests = listOf("Photography", "Street Food", "Skate", "Film")
        )
    )
}

