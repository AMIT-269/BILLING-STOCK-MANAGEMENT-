package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2500, easing = LinearEasing)
        )
        delay(100)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B0B0E),
                        Color(0xFF060608),
                        Color(0xFF000000)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSplashFinished
            )
    ) {
        // Ambient golden lighting from top-right corner
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33FFD700), Color(0x10D4AF37), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.12f),
                    radius = size.width * 0.7f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.7f))

            // 1. Top Logo Emblem: Document, Growth Chart, Gold Coins & Ingots
            Image(
                painter = painterResource(id = R.drawable.ic_billing_stock_logo),
                contentDescription = "Billing and Stock Management Logo",
                modifier = Modifier
                    .size(130.dp)
                    .padding(4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Title: BILLING & STOCK
            Text(
                text = "BILLING & STOCK",
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 2.sp,
                color = Color(0xFFF3BD36),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Subtitle: —— MANAGEMENT ——
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(1.5.dp)
                        .background(Color(0xFFE5B02C))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MANAGEMENT",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = Color(0xFFE5B02C)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(1.5.dp)
                        .background(Color(0xFFE5B02C))
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Tagline: Manage Your Jewellery Business Easily & Smartly
            Text(
                text = "Manage Your Jewellery Business\nEasily & Smartly",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp,
                color = Color(0xFFE2E8F0),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.4f))

            // 5. Middle Artistic Display: Gold Jewellery, Bullions & Flowing Gold Waves
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                contentAlignment = Alignment.Center
            ) {
                SplashJewelleryArt()
            }

            Spacer(modifier = Modifier.weight(0.8f))

            // 6. Bottom Progress & Loading Indicator
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF26262E))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFD4AF37), Color(0xFFFFD700), Color(0xFFFFF4B8))
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Loading...",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "2.5 sec",
                fontSize = 11.5.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

/**
 * Custom artistic rendering of Traditional Indian Gold Necklace, Jhumka Earrings,
 * Gold & Silver bullion bars, and flowing dual golden waves matching the right-side photo.
 */
@Composable
private fun SplashJewelleryArt() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // --- A. Flowing Curved Gold Ribbon Waves across the lower section ---
        val wavePath1 = Path().apply {
            moveTo(0f, h * 0.82f)
            cubicTo(
                w * 0.35f, h * 0.88f,
                w * 0.65f, h * 0.72f,
                w, h * 0.76f
            )
            lineTo(w, h * 0.88f)
            cubicTo(
                w * 0.65f, h * 0.84f,
                w * 0.35f, h * 1.0f,
                0f, h * 0.94f
            )
            close()
        }
        drawPath(
            path = wavePath1,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFDF73), Color(0xFFD4AF37), Color(0xFF8C6614), Color(0xFFFFDF73)),
                start = Offset(0f, h * 0.8f),
                end = Offset(w, h * 0.9f)
            )
        )

        val wavePath2 = Path().apply {
            moveTo(0f, h * 0.79f)
            cubicTo(
                w * 0.35f, h * 0.85f,
                w * 0.65f, h * 0.69f,
                w, h * 0.73f
            )
        }
        drawPath(
            path = wavePath2,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFF6D0), Color(0xFFFFD700), Color(0xFFFFF6D0)),
                start = Offset(0f, h * 0.8f),
                end = Offset(w, h * 0.75f)
            ),
            style = Stroke(width = 2.5f)
        )

        // --- B. Traditional Gold Kundan Necklace on Left/Center ---
        val neckCenterX = w * 0.34f
        val neckCenterY = h * 0.38f

        // Outer necklace arc
        for (i in -8..8) {
            val angle = Math.toRadians((i * 9.5).toDouble())
            val r = w * 0.32f
            val beadX = (neckCenterX + r * Math.sin(angle)).toFloat()
            val beadY = (neckCenterY - r * (1 - Math.cos(angle)) * 0.75f).toFloat()

            // Gold floral motif with ruby center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFECB3), Color(0xFFFFC107), Color(0xFFB8860B)),
                    center = Offset(beadX - 2, beadY - 2),
                    radius = 9f
                ),
                radius = 7.5f,
                center = Offset(beadX, beadY)
            )
            // Ruby gemstone in center of key beads
            if (i % 2 == 0) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF5252), Color(0xFFB71C1C)),
                        center = Offset(beadX - 1, beadY - 1),
                        radius = 4f
                    ),
                    radius = 3.5f,
                    center = Offset(beadX, beadY)
                )
            }
            // Hanging gold drop bead
            drawCircle(
                color = Color(0xFFFFD700),
                radius = 2.8f,
                center = Offset(beadX, beadY + 9f)
            )
        }

        // Inner necklace accent curve
        for (i in -6..6) {
            val angle = Math.toRadians((i * 10.0).toDouble())
            val r = w * 0.26f
            val beadX = (neckCenterX + r * Math.sin(angle)).toFloat()
            val beadY = (neckCenterY - r * (1 - Math.cos(angle)) * 0.75f - 12f).toFloat()

            drawCircle(
                color = Color(0xFFFFE082),
                radius = 4.2f,
                center = Offset(beadX, beadY)
            )
        }

        // --- C. Hanging Gold Jhumka Earrings on the Right ---
        val jhumkaX = w * 0.84f
        val jhumkaY = h * 0.40f

        // Stud flower
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF8A80), Color(0xFFC62828)),
                center = Offset(jhumkaX - 1, jhumkaY - 18f),
                radius = 5f
            ),
            radius = 4.5f,
            center = Offset(jhumkaX, jhumkaY - 18f)
        )
        // Connecting gold ring
        drawLine(
            color = Color(0xFFFFD700),
            start = Offset(jhumkaX, jhumkaY - 14f),
            end = Offset(jhumkaX, jhumkaY - 8f),
            strokeWidth = 2.5f
        )
        // Jhumka Bell (Dome)
        val domePath = Path().apply {
            moveTo(jhumkaX - 16f, jhumkaY + 4f)
            cubicTo(
                jhumkaX - 14f, jhumkaY - 10f,
                jhumkaX + 14f, jhumkaY - 10f,
                jhumkaX + 16f, jhumkaY + 4f
            )
            close()
        }
        drawPath(
            path = domePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFF2A3), Color(0xFFFFD700), Color(0xFFB8860B)),
                startY = jhumkaY - 10f,
                endY = jhumkaY + 4f
            )
        )
        // Little hanging gold bells
        for (dx in -12..12 step 6) {
            drawCircle(
                color = Color(0xFFFFE57F),
                radius = 2.2f,
                center = Offset(jhumkaX + dx, jhumkaY + 8f)
            )
        }

        // Secondary Jhumka slightly behind
        val jhumka2X = w * 0.74f
        val jhumka2Y = h * 0.45f
        val domePath2 = Path().apply {
            moveTo(jhumka2X - 12f, jhumka2Y + 2f)
            cubicTo(
                jhumka2X - 10f, jhumka2Y - 8f,
                jhumka2X + 10f, jhumka2Y - 8f,
                jhumka2X + 12f, jhumka2Y + 2f
            )
            close()
        }
        drawPath(
            path = domePath2,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFE57F), Color(0xFFC69524)),
                startY = jhumka2Y - 8f,
                endY = jhumka2Y + 2f
            )
        )

        // --- D. 3D Gold Bullion Bar in Foreground ---
        val gX = w * 0.48f
        val gY = h * 0.52f
        val gW = w * 0.24f
        val gH = 34f

        // Top Face of Gold Bar
        val goldTop = Path().apply {
            moveTo(gX, gY)
            lineTo(gX + gW * 0.7f, gY - 12f)
            lineTo(gX + gW * 0.55f, gY + 4f)
            lineTo(gX - gW * 0.15f, gY + 16f)
            close()
        }
        drawPath(
            path = goldTop,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFF8D0), Color(0xFFFFD700), Color(0xFFF5A623))
            )
        )
        // Front Face of Gold Bar
        val goldFront = Path().apply {
            moveTo(gX - gW * 0.15f, gY + 16f)
            lineTo(gX + gW * 0.55f, gY + 4f)
            lineTo(gX + gW * 0.50f, gY + 4f + gH)
            lineTo(gX - gW * 0.20f, gY + 16f + gH)
            close()
        }
        drawPath(
            path = goldFront,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF5A623), Color(0xFFB8860B), Color(0xFF7A5907)),
                startY = gY + 4f,
                endY = gY + 16f + gH
            )
        )
        // Right Edge Face of Gold Bar
        val goldRight = Path().apply {
            moveTo(gX + gW * 0.55f, gY + 4f)
            lineTo(gX + gW * 0.7f, gY - 12f)
            lineTo(gX + gW * 0.65f, gY - 12f + gH)
            lineTo(gX + gW * 0.50f, gY + 4f + gH)
            close()
        }
        drawPath(
            path = goldRight,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFC69524), Color(0xFF8C6614))
            )
        )

        // --- E. 3D Silver Bullion Bar in Foreground ---
        val sX = w * 0.66f
        val sY = h * 0.56f
        val sW = w * 0.24f
        val sH = 32f

        // Top Face of Silver Bar
        val silverTop = Path().apply {
            moveTo(sX, sY)
            lineTo(sX + sW * 0.7f, sY - 10f)
            lineTo(sX + sW * 0.55f, sY + 5f)
            lineTo(sX - sW * 0.15f, sY + 15f)
            close()
        }
        drawPath(
            path = silverTop,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0), Color(0xFFCBD5E1))
            )
        )
        // Front Face of Silver Bar
        val silverFront = Path().apply {
            moveTo(sX - sW * 0.15f, sY + 15f)
            lineTo(sX + sW * 0.55f, sY + 5f)
            lineTo(sX + sW * 0.50f, sY + 5f + sH)
            lineTo(sX - sW * 0.20f, sY + 15f + sH)
            close()
        }
        drawPath(
            path = silverFront,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8), Color(0xFF64748B)),
                startY = sY + 5f,
                endY = sY + 15f + sH
            )
        )
        // Right Edge Face of Silver Bar
        val silverRight = Path().apply {
            moveTo(sX + sW * 0.55f, sY + 5f)
            lineTo(sX + sW * 0.7f, sY - 10f)
            lineTo(sX + sW * 0.65f, sY - 10f + sH)
            lineTo(sX + sW * 0.50f, sY + 5f + sH)
            close()
        }
        drawPath(
            path = silverRight,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF94A3B8), Color(0xFF475569))
            )
        )

        // Highlights on bullion top edges
        drawLine(
            color = Color.White,
            start = Offset(gX - gW * 0.15f, gY + 16f),
            end = Offset(gX + gW * 0.55f, gY + 4f),
            strokeWidth = 1.8f
        )
        drawLine(
            color = Color.White,
            start = Offset(sX - sW * 0.15f, sY + 15f),
            end = Offset(sX + sW * 0.55f, sY + 5f),
            strokeWidth = 1.8f
        )
    }
}
