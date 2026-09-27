package com.poldivers.app.ui.common

import com.poldivers.app.core.i18n.tr
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val polishNumbers: NumberFormat = NumberFormat.getIntegerInstance(Locale("pl", "PL"))
private val englishNumbers: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

private val numberLocale: Locale get() = if (com.poldivers.app.core.i18n.UiLang.english) Locale.US else Locale("pl", "PL")

/** 1234567 -> "1 234 567" (PL) / "1,234,567" (EN) */
fun formatNumber(value: Long): String =
    (if (com.poldivers.app.core.i18n.UiLang.english) englishNumbers else polishNumbers).format(value)

/** 1234567 -> "1,2 mln", 12345 -> "12,3 tys." -- for tight spots like map labels. */
fun formatCompact(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1f ".format(numberLocale, value / 1_000_000_000.0) + tr("mld", "B")
    value >= 1_000_000 -> "%.1f ".format(numberLocale, value / 1_000_000.0) + tr("mln", "M")
    value >= 10_000 -> "%.1f ".format(numberLocale, value / 1_000.0) + tr("tys.", "K")
    else -> formatNumber(value)
}

/** 12.3456 -> "12,35" */
fun formatPercent(value: Double, decimals: Int = 2): String =
    "%.${decimals}f".format(numberLocale, value)

fun Double.roundToOneDecimal(): Double = (this * 10.0).roundToInt() / 10.0
