package com.mallucupid.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mallucupid.app.ui.theme.*

data class ExploreSpaceItem(
    val id: String,
    val title: String,
    val count: Int,
    val emoji: String,
    val gradientColors: List<Color>,
    val description: String
)

@Composable
fun ExploreViewContent(
    onSelectCategory: (String) -> Unit
) {
    // Categories matching screenshots 19 & 20
    val spaces = listOf(
        ExploreSpaceItem(
            id = "long_term",
            title = "Long-term partner",
            count = 104,
            emoji = "🌷",
            gradientColors = listOf(Color(0xFFFFE4E6), Color(0xFFFECDD3)),
            description = "Looking for something deep & lasting"
        ),
        ExploreSpaceItem(
            id = "serious",
            title = "Serious commitment",
            count = 40,
            emoji = "💍",
            gradientColors = listOf(Color(0xFFEDE9FE), Color(0xFFDDD6FE)),
            description = "Ready for marriage or serious future"
        ),
        ExploreSpaceItem(
            id = "coffee",
            title = "Coffee date",
            count = 29,
            emoji = "☕",
            gradientColors = listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A)),
            description = "Casual first meet at cozy cafes"
        ),
        ExploreSpaceItem(
            id = "free_tonight",
            title = "Free tonight",
            count = 41,
            emoji = "🌙",
            gradientColors = listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD)),
            description = "Spontaneous dinner or evening walk"
        ),
        ExploreSpaceItem(
            id = "love",
            title = "Looking for love",
            count = 56,
            emoji = "💖",
            gradientColors = listOf(Color(0xFFFCE7F3), Color(0xFFFBCFE8)),
            description = "True chemistry & romance"
        ),
        ExploreSpaceItem(
            id = "social",
            title = "Social vibe",
            count = 33,
            emoji = "👋",
            gradientColors = listOf(Color(0xFFFFEDD5), Color(0xFFFED7AA)),
            description = "Expand your circle & new friends"
        ),
        ExploreSpaceItem(
            id = "college",
            title = "College life",
            count = 88,
            emoji = "🎓",
            gradientColors = listOf(Color(0xFFDCFCE7), Color(0xFFBBF7D0)),
            description = "Campus singles near you"
        ),
        ExploreSpaceItem(
            id = "night_owls",
            title = "Night owls",
            count = 62,
            emoji = "🍸",
            gradientColors = listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0)),
            description = "Late chats and rooftop gatherings"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TinderBg)
            .padding(bottom = 80.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Text(
            text = "Explore",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TinderTextPrimary
        )
        Text(
            text = "Curated spaces to meet like-minded singles nearby",
            fontSize = 13.sp,
            color = TinderTextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2-Column Grid (Matches screenshots 19 & 20)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(spaces) { space ->
                Surface(
                    onClick = { onSelectCategory(space.title) },
                    shape = RoundedCornerShape(18.dp),
                    color = TinderSurface,
                    border = BorderStroke(1.dp, TinderBorder),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(space.gradientColors))
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Row: Count Badge & Emoji Icon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.White.copy(alpha = 0.85f)
                                ) {
                                    Text(
                                        text = "${space.count}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TinderTextPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Text(
                                    text = space.emoji,
                                    fontSize = 32.sp
                                )
                            }

                            // Bottom: Title & brief description
                            Column {
                                Text(
                                    text = space.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TinderTextPrimary,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = space.description,
                                    fontSize = 11.sp,
                                    color = TinderTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
