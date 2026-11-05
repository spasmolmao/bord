package com.example.bord.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 1. CENTRAL MONOCHROME PALETTE
 * Change any hex code here and the entire app's aesthetic updates automatically.
 */
object BordPalette {
    val BgPureBlack = Color(0xFF050505)      // Main canvas background
    val SurfaceCard = Color(0xFF0E0E10)      // Matte black card surface
    val SurfaceElevated = Color(0xFF18181B)  // Subtle dark grey for inner pills/inputs
    val BorderHairline = Color(0xFF27272A)   // Crisp 1dp minimalist border
    val BorderActive = Color(0xFFFAFAFA)     // Stark white border for selected/active items

    val TextPrimary = Color(0xFFFAFAFA)      // Stark white headlines
    val TextSecondary = Color(0xFFA1A1AA)    // Muted zinc grey subtitles
    val TextDim = Color(0xFF52525B)          // Dim metadata

    // Minimalist Signal Accents (Only used when status demands attention)
    val SignalWhite = Color(0xFFFFFFFF)      // Primary action / progress fill
    val SignalGhost = Color(0xFF52525B)      // Ghost Bar target fill
    val SignalAlert = Color(0xFFEF4444)      // Overdue / Ghost deficit / Negative points
    val SignalDone = Color(0xFFD4D4D8)       // Completed state

    // Global Corner Radius (Set to 0.dp for sharp brutalist, 6.dp for Nothing/Linear style, 14.dp for soft)
    val CornerRadius: Dp = 6.dp
}

/**
 * 2. REUSABLE LEGO BLOCK #1: Minimalist Outlined Card
 * Usage anywhere: MonoCard { Text("Hello") }
 */
@Composable
fun MonoCard(
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    isAlert: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = when {
        isAlert -> BordPalette.SignalAlert
        isHighlighted -> BordPalette.BorderActive
        else -> BordPalette.BorderHairline
    }
    val shape = RoundedCornerShape(BordPalette.CornerRadius)

    Surface(
        color = BordPalette.SurfaceCard,
        shape = shape,
        border = BorderStroke(if (isHighlighted || isAlert) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

/**
 * 3. REUSABLE LEGO BLOCK #2: High-Contrast Monochrome Button
 * Inverted stark white button (or outlined ghost button) with monospace uppercase label.
 */
@Composable
fun MonoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inverted: Boolean = true,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(BordPalette.CornerRadius)
    if (inverted) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = BordPalette.SignalWhite,
                contentColor = BordPalette.BgPureBlack,
                disabledContainerColor = BordPalette.SurfaceElevated,
                disabledContentColor = BordPalette.TextDim
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = modifier
        ) {
            Text(
                text = text.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            border = BorderStroke(1.dp, BordPalette.BorderHairline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = BordPalette.TextPrimary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = modifier
        ) {
            Text(
                text = text.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 4. REUSABLE LEGO BLOCK #3: Minimalist Monospace Status Tag / Badge
 */
@Composable
fun MonoBadge(
    text: String,
    isAlert: Boolean = false,
    inverted: Boolean = false
) {
    val bg = when {
        isAlert -> BordPalette.SignalAlert.copy(alpha = 0.15f)
        inverted -> BordPalette.SignalWhite
        else -> BordPalette.SurfaceElevated
    }
    val fg = when {
        isAlert -> BordPalette.SignalAlert
        inverted -> BordPalette.BgPureBlack
        else -> BordPalette.TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (isAlert) BordPalette.SignalAlert else BordPalette.BorderHairline,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = fg,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 5. REUSABLE LEGO BLOCK #4: Dual Monochrome Ghost Progress Bar
 */
@Composable
fun MonoDualGhostBar(
    actualFraction: Float,
    ghostFraction: Float,
    isBehindGhost: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Ghost Pace Bar (Thin hairline track)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(BordPalette.SurfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ghostFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(BordPalette.SignalGhost)
            )
        }

        // Actual Invested Bar (Crisp white or alert red if behind ghost pace)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(BordPalette.SurfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(actualFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(if (isBehindGhost) BordPalette.SignalAlert else BordPalette.SignalWhite)
            )
        }
    }
}

/**
 * 6. REUSABLE LEGO BLOCK #5: Section Divider Label
 */
@Composable
fun MonoSectionTitle(title: String, rightText: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = BordPalette.TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        if (rightText != null) {
            Text(
                text = rightText,
                color = BordPalette.TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * LIVE DESIGN PLAYGROUND PREVIEW
 * Click "Split" at the top-right of Android Studio to see this live while tweaking colors/borders!
 */
@Preview(showBackground = true, widthDp = 380)
@Composable
private fun PreviewMinimalistDesignSystem() {
    Column(
        modifier = Modifier
            .background(BordPalette.BgPureBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MonoSectionTitle(title = "01 / HOUR BANK QUOTA", rightText = "NOV 2026")
        MonoCard(isHighlighted = true) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("DSA & PLACEMENT PREP", color = BordPalette.TextPrimary, fontWeight = FontWeight.Bold)
                MonoBadge("2.1H / DAY", inverted = true)
            }
            Spacer(Modifier.height(8.dp))
            MonoDualGhostBar(actualFraction = 0.35f, ghostFraction = 0.50f, isBehindGhost = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoButton(text = "Start Timer", onClick = {}, inverted = true)
                MonoButton(text = "Rebalance +2.4h", onClick = {}, inverted = false)
            }
        }
    }
}