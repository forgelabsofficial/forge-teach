package com.aiteacher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LearningWorld(
    val id: String,
    val title: String,
    val icon: String,
    val description: String,
    val accentColor: Color,
    val regions: List<WorldRegion>
)

data class WorldRegion(
    val id: String,
    val name: String,
    val status: String, // "unlocked", "in_progress", "locked", "boss"
    val progress: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningWorldsScreen(
    onSelectTopic: (subject: String, topic: String) -> Unit,
    onBack: () -> Unit = {}
) {
    val worlds = remember {
        listOf(
            LearningWorld(
                id = "math",
                title = "Math Kingdom",
                icon = "🏰",
                description = "Master numbers, algebra, geometry, and problem solving.",
                accentColor = Color(0xFF6C5CE7),
                regions = listOf(
                    WorldRegion("m1", "Fraction Fortress", "in_progress", 0.75f),
                    WorldRegion("m2", "Decimal Valley", "unlocked", 0.40f),
                    WorldRegion("m3", "Algebra Island", "locked", 0.0f),
                    WorldRegion("m4", "Geometry Citadel (Boss)", "boss", 0.0f)
                )
            ),
            LearningWorld(
                id = "science",
                title = "Science Lab",
                icon = "🧪",
                description = "Explore forces, ecosystems, chemical reactions, and space.",
                accentColor = Color(0xFF00CEC9),
                regions = listOf(
                    WorldRegion("s1", "Cellular Outpost", "unlocked", 0.60f),
                    WorldRegion("s2", "Force Fields & Motion", "in_progress", 0.30f),
                    WorldRegion("s3", "Chemical Reaction Zone", "locked", 0.0f),
                    WorldRegion("s4", "Cosmic Horizon (Boss)", "boss", 0.0f)
                )
            ),
            LearningWorld(
                id = "english",
                title = "English Library",
                icon = "📚",
                description = "Unlock reading comprehension, grammar mastery, and creative writing.",
                accentColor = Color(0xFFFD79A8),
                regions = listOf(
                    WorldRegion("e1", "Grammar Gardens", "unlocked", 0.85f),
                    WorldRegion("e2", "Vocabulary Vault", "in_progress", 0.50f),
                    WorldRegion("e3", "Essay Empire", "locked", 0.0f)
                )
            ),
            LearningWorld(
                id = "geography",
                title = "Geography Explorer",
                icon = "🌍",
                description = "Map the planet, climates, landforms, and human societies.",
                accentColor = Color(0xFF00B894),
                regions = listOf(
                    WorldRegion("g1", "Continent Canvas", "unlocked", 0.45f),
                    WorldRegion("g2", "Climate Zones", "locked", 0.0f)
                )
            ),
            LearningWorld(
                id = "code",
                title = "Code Factory",
                icon = "💻",
                description = "Build logic algorithms, variables, loops, and digital programs.",
                accentColor = Color(0xFF0984E3),
                regions = listOf(
                    WorldRegion("c1", "Logic Gates", "unlocked", 0.90f),
                    WorldRegion("c2", "Loop Laboratory", "in_progress", 0.20f)
                )
            ),
            LearningWorld(
                id = "art",
                title = "Creative Studio",
                icon = "🎨",
                description = "Design principles, color theory, historical art, and composition.",
                accentColor = Color(0xFFE17055),
                regions = listOf(
                    WorldRegion("a1", "Color Theory", "unlocked", 0.30f)
                )
            )
        )
    }

    ForgeBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Learning Worlds", fontWeight = FontWeight.Bold, color = forgeColors.textPrimary)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = "Choose a realm to explore. Unlock new regions as your mastery grows!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = forgeColors.textSecondary
                    )
                }

                items(worlds) { world ->
                    LearningWorldCard(world = world, onSelectTopic = onSelectTopic)
                }
            }
        }
    }
}

@Composable
fun LearningWorldCard(
    world: LearningWorld,
    onSelectTopic: (subject: String, topic: String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, world.accentColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = forgeColors.bgCard.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(world.accentColor.copy(alpha = 0.3f), Color.Transparent))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(world.icon, fontSize = 26.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(world.title, style = MaterialTheme.typography.titleMedium, color = forgeColors.textPrimary, fontWeight = FontWeight.Bold)
                    Text(world.description, style = MaterialTheme.typography.bodySmall, color = forgeColors.textSecondary)
                }
            }

            Divider(color = forgeColors.divider.copy(alpha = 0.5f))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                world.regions.forEach { region ->
                    WorldRegionRow(
                        region = region,
                        accentColor = world.accentColor,
                        onClick = {
                            if (region.status != "locked") {
                                onSelectTopic(world.id, region.name)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WorldRegionRow(
    region: WorldRegion,
    accentColor: Color,
    onClick: () -> Unit
) {
    val isLocked = region.status == "locked"
    val isBoss = region.status == "boss"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isLocked) Color.White.copy(alpha = 0.03f) else accentColor.copy(alpha = 0.08f))
            .clickable(enabled = !isLocked, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = when {
                isBoss -> "👑"
                isLocked -> "🔒"
                region.status == "in_progress" -> "⚔️"
                else -> "⭐"
            },
            fontSize = 18.sp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = region.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLocked) forgeColors.textSecondary.copy(alpha = 0.5f) else forgeColors.textPrimary,
                fontWeight = if (isLocked) FontWeight.Normal else FontWeight.SemiBold
            )
            if (!isLocked) {
                LinearProgressIndicator(
                    progress = region.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.2f)
                )
            }
        }
        Text(
            text = when {
                isLocked -> "Locked"
                isBoss -> "Boss Gate"
                else -> "${(region.progress * 100).toInt()}%"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isLocked) forgeColors.textSecondary.copy(alpha = 0.4f) else accentColor
        )
    }
}
