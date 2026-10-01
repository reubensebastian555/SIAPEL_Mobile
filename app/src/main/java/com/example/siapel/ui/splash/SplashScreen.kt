package com.example.siapel.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siapel.ui.theme.PrimaryNavy
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Simple and elegant fade-in animation
        logoAlpha.animateTo(1f, animationSpec = tween(1000))
        textAlpha.animateTo(1f, animationSpec = tween(800))
        delay(1000) // Total around 2 seconds
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // High-quality responsive vector emblem of Kota Malang
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .alpha(logoAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                MalangEmblem()
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SIAPEL",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}

@Composable
fun MalangEmblem() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val cx = width / 2
        val cy = height / 2

        // Outer pentagon path
        val outerPath = Path().apply {
            moveTo(cx, height * 0.05f)
            lineTo(width * 0.95f, height * 0.42f)
            lineTo(width * 0.78f, height * 0.92f)
            lineTo(width * 0.22f, height * 0.92f)
            lineTo(width * 0.05f, height * 0.42f)
            close()
        }

        // Inner pentagon (Green Fill)
        val innerPath = Path().apply {
            moveTo(cx, height * 0.08f)
            lineTo(width * 0.91f, height * 0.43f)
            lineTo(width * 0.75f, height * 0.90f)
            lineTo(width * 0.25f, height * 0.90f)
            lineTo(width * 0.09f, height * 0.43f)
            close()
        }

        // Draw red-white outer border
        drawPath(path = outerPath, color = Color(0xFFD32F2F))
        drawPath(path = outerPath, color = Color.White, style = Stroke(width = 4f))

        // Draw green background
        drawPath(path = innerPath, color = Color(0xFF2E7D32))

        // Draw the golden star at top center
        val starPath = Path().apply {
            val sx = cx
            val sy = height * 0.32f
            val rOuter = width * 0.22f
            val rInner = width * 0.09f
            for (i in 0 until 5) {
                val angleOuter = Math.toRadians((i * 72 - 90).toDouble())
                val angleInner = Math.toRadians((i * 72 - 90 + 36).toDouble())
                if (i == 0) {
                    moveTo(
                        (sx + rOuter * Math.cos(angleOuter)).toFloat(),
                        (sy + rOuter * Math.sin(angleOuter)).toFloat()
                    )
                } else {
                    lineTo(
                        (sx + rOuter * Math.cos(angleOuter)).toFloat(),
                        (sy + rOuter * Math.sin(angleOuter)).toFloat()
                    )
                }
                lineTo(
                    (sx + rInner * Math.cos(angleInner)).toFloat(),
                    (sy + rInner * Math.sin(angleInner)).toFloat()
                )
            }
            close()
        }
        drawPath(path = starPath, color = Color(0xFFFFD54F))

        // Draw the Tugu/Monument in the middle
        val tuguPath = Path().apply {
            // Base steps
            moveTo(width * 0.32f, height * 0.78f)
            lineTo(width * 0.68f, height * 0.78f)
            lineTo(width * 0.65f, height * 0.74f)
            lineTo(width * 0.35f, height * 0.74f)
            close()
            
            moveTo(width * 0.36f, height * 0.74f)
            lineTo(width * 0.64f, height * 0.74f)
            lineTo(width * 0.61f, height * 0.68f)
            lineTo(width * 0.39f, height * 0.68f)
            close()

            // Main pillar
            moveTo(width * 0.44f, height * 0.68f)
            lineTo(width * 0.56f, height * 0.68f)
            lineTo(width * 0.53f, height * 0.40f)
            lineTo(width * 0.47f, height * 0.40f)
            close()

            // Top crown
            moveTo(width * 0.45f, height * 0.40f)
            lineTo(width * 0.55f, height * 0.40f)
            lineTo(width * 0.50f, height * 0.35f)
            close()
        }
        drawPath(path = tuguPath, color = Color(0xFF0288D1))
        drawPath(path = tuguPath, color = Color.White, style = Stroke(width = 2f))

        // Draw the ribbon banner at the bottom
        val bannerPath = Path().apply {
            moveTo(width * 0.20f, height * 0.84f)
            lineTo(width * 0.80f, height * 0.84f)
            lineTo(width * 0.75f, height * 0.76f)
            lineTo(width * 0.25f, height * 0.76f)
            close()
        }
        drawPath(path = bannerPath, color = Color.White)
        drawPath(path = bannerPath, color = Color(0xFF0B2D4D), style = Stroke(width = 3f))

        // Tiny representation line for text banner
        drawLine(
            color = Color(0xFF111111),
            start = Offset(width * 0.30f, height * 0.80f),
            end = Offset(width * 0.70f, height * 0.80f),
            strokeWidth = 4f
        )
    }
}
