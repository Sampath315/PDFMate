package com.example.pdfmate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.pdfmate.R

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold)
)

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Jakarta,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em
)

val Typography = Typography(
    displayLarge = style(52, FontWeight.ExtraBold, 58, -0.02),
    displayMedium = style(42, FontWeight.ExtraBold, 48, -0.02),
    displaySmall = style(34, FontWeight.ExtraBold, 40, -0.015),
    headlineLarge = style(30, FontWeight.ExtraBold, 36, -0.01),
    headlineMedium = style(26, FontWeight.Bold, 32, -0.01),
    headlineSmall = style(22, FontWeight.Bold, 28, -0.005),
    titleLarge = style(20, FontWeight.Bold, 26),
    titleMedium = style(16, FontWeight.Bold, 22),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Medium, 16),
    labelLarge = style(14, FontWeight.SemiBold, 20),
    labelMedium = style(12, FontWeight.SemiBold, 16),
    labelSmall = style(11, FontWeight.SemiBold, 14, 0.02)
)
