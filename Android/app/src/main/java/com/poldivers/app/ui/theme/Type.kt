package com.poldivers.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.poldivers.app.R

/** Chakra Petch (SIL OFL) -- squared, HUD-like; close to the game's UI lettering. */
val HudFont = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/** Russo One (SIL OFL) -- heavy display face for campaign / section headlines. */
val DisplayFont = FontFamily(Font(R.font.russo_one, FontWeight.Normal))

// Headings and labels use the HUD font; running text stays on the system font for readability
// (and for scripts the HUD font lacks, e.g. Cyrillic/CJK from the API).
val PolDiversTypography = Typography(
    displaySmall = TextStyle(fontFamily = DisplayFont, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = 1.sp),
    headlineLarge = TextStyle(fontFamily = DisplayFont, fontSize = 28.sp, lineHeight = 31.sp, letterSpacing = 0.8.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontSize = 22.sp, lineHeight = 25.sp, letterSpacing = 0.6.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFont, fontSize = 18.sp, lineHeight = 21.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.3.sp),
    titleSmall = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.8.sp),
    labelMedium = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = HudFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
)
