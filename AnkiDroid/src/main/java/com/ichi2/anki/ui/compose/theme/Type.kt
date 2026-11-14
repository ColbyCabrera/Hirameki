// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.ui.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ichi2.anki.R

@OptIn(ExperimentalTextApi::class)
val GoogleSansRounded =
    FontFamily(
        Font(
            R.font.google_sans_rounded_regular,
            FontWeight.W100,
            variationSettings = FontVariation.Settings(FontVariation.weight(100)),
        ),
        Font(
            R.font.google_sans_rounded_regular,
            FontWeight.W200,
            variationSettings = FontVariation.Settings(FontVariation.weight(200)),
        ),
        Font(
            R.font.google_sans_rounded_regular,
            FontWeight.W300,
            variationSettings = FontVariation.Settings(FontVariation.weight(300)),
        ),
        Font(
            R.font.google_sans_rounded_regular,
            FontWeight.W400,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            R.font.google_sans_rounded_regular,
            FontWeight.W500,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            R.font.google_sans_rounded_regular,
            FontWeight.W600,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
        Font(
            R.font.google_sans_rounded_regular,
            FontWeight.W700,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
        Font(
            R.font.google_sans_rounded_regular,
            FontWeight.W800,
            variationSettings = FontVariation.Settings(FontVariation.weight(800)),
        ),
        Font(
            R.font.google_sans_rounded_regular,
            FontWeight.W900,
            variationSettings = FontVariation.Settings(FontVariation.weight(900)),
        ),
    )

val GoogleSansFamily =
    FontFamily(
        Font(R.font.google_sans_family, FontWeight.Normal),
    )

val RobotoFlex =
    FontFamily(
        Font(R.font.roboto_flex, FontWeight.Normal),
    )

val RobotoMono =
    FontFamily(
        Font(R.font.roboto_mono, FontWeight.Normal),
    )

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexLowWidth =
    FontFamily(
        Font(
            R.font.google_sans_flex,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.width(3F),
                    FontVariation.slant(-6f),
                ),
        ),
    )

/**
 * Vazirmatn, the UI font for Arabic-script languages (see [com.ichi2.themes.ArabicScriptFont]).
 * Every weight is an instance of its variable font file.
 */
@OptIn(ExperimentalTextApi::class)
val Vazirmatn =
    FontFamily(
        (100..900 step 100).map { weight ->
            Font(
                R.font.vazirmatn,
                FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )

val AppTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = RobotoFlex,
                fontWeight = FontWeight.Bold,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayLargeEmphasized =
            TextStyle(
                fontFamily = GoogleSansFlexLowWidth,
                fontWeight = FontWeight.Thin,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.5).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = RobotoFlex,
                fontWeight = FontWeight.Bold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
        displayMediumEmphasized =
            TextStyle(
                fontFamily = GoogleSansFlexLowWidth,
                fontWeight = FontWeight.Thin,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = (-0.25).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = RobotoFlex,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        displaySmallEmphasized =
            TextStyle(
                fontFamily = GoogleSansFlexLowWidth,
                fontWeight = FontWeight.Light,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.25).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = RobotoFlex,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = RobotoFlex,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = RobotoFlex,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = RobotoFlex,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleLargeEmphasized =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = RobotoFlex,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = RobotoFlex,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )

/**
 * [AppTypography] with every style drawn in [Vazirmatn], including styles left at Material defaults (for example
 * labelLargeEmphasized), so no UI text falls back to the system's Naskh. Sizes, weights, and spacing are unchanged.
 */
val ArabicScriptTypography = AppTypography.inFamily(Vazirmatn)

private fun Typography.inFamily(family: FontFamily): Typography {
    fun TextStyle.withFamily() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.withFamily(),
        displayMedium = displayMedium.withFamily(),
        displaySmall = displaySmall.withFamily(),
        headlineLarge = headlineLarge.withFamily(),
        headlineMedium = headlineMedium.withFamily(),
        headlineSmall = headlineSmall.withFamily(),
        titleLarge = titleLarge.withFamily(),
        titleMedium = titleMedium.withFamily(),
        titleSmall = titleSmall.withFamily(),
        bodyLarge = bodyLarge.withFamily(),
        bodyMedium = bodyMedium.withFamily(),
        bodySmall = bodySmall.withFamily(),
        labelLarge = labelLarge.withFamily(),
        labelMedium = labelMedium.withFamily(),
        labelSmall = labelSmall.withFamily(),
        displayLargeEmphasized = displayLargeEmphasized.withFamily(),
        displayMediumEmphasized = displayMediumEmphasized.withFamily(),
        displaySmallEmphasized = displaySmallEmphasized.withFamily(),
        headlineLargeEmphasized = headlineLargeEmphasized.withFamily(),
        headlineMediumEmphasized = headlineMediumEmphasized.withFamily(),
        headlineSmallEmphasized = headlineSmallEmphasized.withFamily(),
        titleLargeEmphasized = titleLargeEmphasized.withFamily(),
        titleMediumEmphasized = titleMediumEmphasized.withFamily(),
        titleSmallEmphasized = titleSmallEmphasized.withFamily(),
        bodyLargeEmphasized = bodyLargeEmphasized.withFamily(),
        bodyMediumEmphasized = bodyMediumEmphasized.withFamily(),
        bodySmallEmphasized = bodySmallEmphasized.withFamily(),
        labelLargeEmphasized = labelLargeEmphasized.withFamily(),
        labelMediumEmphasized = labelMediumEmphasized.withFamily(),
        labelSmallEmphasized = labelSmallEmphasized.withFamily(),
    )
}
