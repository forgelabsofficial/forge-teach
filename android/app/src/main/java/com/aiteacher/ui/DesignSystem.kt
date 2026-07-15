package com.aiteacher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.math.cos

// ─── Animated Background Blob: a single drifting Forge Orange glow ───────────

@Composable
fun ForgeBackgroundBlob(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blob")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blobAngle"
    )
    val radius by infiniteTransition.animateFloat(
        initialValue = 100f,
        targetValue = 160f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blobRadius"
    )
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blobDrift"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2 + cos(Math.toRadians(angle.toDouble())).toFloat() * size.width * 0.28f
            val centerY = size.height / 2 + sin(Math.toRadians(drift.toDouble())).toFloat() * size.height * 0.25f
            drawCircle(color = ForgeBrand.Orange.copy(alpha = 0.04f), radius = radius * 2.2f, center = Offset(centerX, centerY))
            drawCircle(color = ForgeBrand.Orange.copy(alpha = 0.06f), radius = radius * 1.5f, center = Offset(centerX, centerY))
            drawCircle(color = ForgeBrand.Orange.copy(alpha = 0.10f), radius = radius * 0.8f, center = Offset(centerX, centerY))
        }
        content()
    }
}

// ─── Glass card ─────────────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val fc = forgeColors
    Card(
        modifier = modifier.border(1.dp, fc.glassBorder, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = fc.glassFill),
        elevation = CardDefaults.cardElevation(0.dp),
        content = content
    )
}

// ─── Solid brand card ───────────────────────────────────────────────────────

@Composable
fun ForgeCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val fc = forgeColors
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = fc.bgCard),
        elevation = CardDefaults.cardElevation(0.dp),
        content = content
    )
}

// ─── Pill button (Forge Orange) ─────────────────────────────────────────────

@Composable
fun ForgeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    shape: Shape = RoundedCornerShape(28.dp)
) {
    val fc = forgeColors
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "btnScale"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val innerEndY = with(density) { (height * 0.4f).toPx() }

    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(ForgeBrand.Orange, ForgeBrand.OrangeDark))
                else Brush.horizontalGradient(listOf(fc.textMuted.copy(alpha = 0.3f), fc.textMuted.copy(alpha = 0.3f)))
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { pressed = true; onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        startY = 0f,
                        endY = innerEndY
                    ),
                    shape = shape
                )
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (enabled) Color.White else fc.textMuted
        )
    }
}

// ─── Ghost button ───────────────────────────────────────────────────────────

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    shape: Shape = RoundedCornerShape(28.dp)
) {
    val fc = forgeColors
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "ghostScale"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .clip(shape)
            .border(1.dp, fc.glassBorder, shape)
            .background(fc.glassFill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = { pressed = true; onClick() }),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = fc.textSecondary)
    }
}

// ─── Section label ──────────────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = TextUnit(1.2f, TextUnitType.Sp)
        ),
        color = forgeColors.textMuted,
        modifier = modifier
    )
}

// ─── Divider ────────────────────────────────────────────────────────────────

@Composable
fun ForgeDivider(modifier: Modifier = Modifier) {
    Divider(modifier = modifier, thickness = 1.dp, color = forgeColors.divider)
}

// ─── Background scaffold ────────────────────────────────────────────────────

@Composable
fun ForgeBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(forgeColors.bgBase),
        content = content
    )
}

// ─── Full-screen background with animated blob ─────────────────────────────

@Composable
fun ForgeBackgroundWithBlob(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    ForgeBackgroundBlob(modifier = modifier, content = content)
}

// ─── Themed OutlinedTextField colours ───────────────────────────────────────

@Composable
fun forgeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor         = forgeColors.textPrimary,
    unfocusedTextColor       = forgeColors.textPrimary,
    cursorColor              = ForgeBrand.Orange,
    focusedBorderColor       = ForgeBrand.Orange,
    unfocusedBorderColor     = forgeColors.glassBorder,
    focusedLabelColor        = ForgeBrand.OrangeLight,
    unfocusedLabelColor      = forgeColors.textMuted,
    focusedContainerColor    = forgeColors.glassFill,
    unfocusedContainerColor  = forgeColors.glassFill,
    focusedPlaceholderColor  = forgeColors.textMuted,
    unfocusedPlaceholderColor= forgeColors.textMuted
)

// ─── Themed OutlinedButton border ───────────────────────────────────────────

@Composable
fun forgeOutlinedBorder() = ButtonDefaults.outlinedButtonBorder.copy(
    brush = Brush.horizontalGradient(listOf(forgeColors.glassBorder, forgeColors.glassBorder))
)

// ─── Orange-accented step pill ─────────────────────────────────────────────

@Composable
fun StepPill(
    number: Int,
    isActive: Boolean,
    isDone: Boolean,
    modifier: Modifier = Modifier
) {
    val size = 32.dp
    val fc = forgeColors
    val bg = when {
        isDone  -> ForgeBrand.Success
        isActive -> ForgeBrand.Orange
        else    -> fc.glassFill
    }
    val borderColor = when {
        isActive -> ForgeBrand.Orange
        isDone   -> ForgeBrand.Success
        else     -> fc.glassBorder
    }
    val label = when { isDone  -> "✓"; else -> "$number" }
    val labelColor = when { isDone || isActive -> Color.White; else -> fc.textMuted }

    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(50)).background(bg)
            .border(2.dp, borderColor.copy(0.5f), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = labelColor)
    }
}

// ─── Shimmer loading placeholder ────────────────────────────────────────────

@Composable
fun ShimmerBlock(
    width: Int,
    height: Int,
    shape: Shape = RoundedCornerShape(16.dp),
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .width(width.dp)
            .height(height.dp)
            .clip(shape)
            .background(forgeColors.glassFill.copy(alpha = alpha))
    )
}

// ─── Orange-accented section header bar ─────────────────────────────────────

@Composable
fun OrangeAccentHeader(
    title: String,
    modifier: Modifier = Modifier,
    emoji: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(ForgeBrand.Orange))
        if (emoji != null) Text(emoji, style = MaterialTheme.typography.titleSmall)
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = forgeColors.textPrimary)
    }
}
