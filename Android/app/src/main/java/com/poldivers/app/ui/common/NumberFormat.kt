package com.poldivers.app.ui.common

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val polishNumbers: NumberFormat = NumberFormat.getIntegerInstance(Locale("pl", "PL"))

/** 1234567 -> "1 234 567" */
fun formatNumber(value: Long): String = polishNumbers.format(value)

/** 1234567 -> "1,2 mln", 12345 -> "12,3 tys." -- for tight spots like map labels. */
fun formatCompact(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1f mld".format(Locale("pl", "PL"), value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1f mln".format(Locale("pl", "PL"), value / 1_000_000.0)
    value >= 10_000 -> "%.1f tys.".format(Locale("pl", "PL"), value / 1_000.0)
    else -> formatNumber(value)
}

/** 12.3456 -> "12,35" */
fun formatPercent(value: Double, decimals: Int = 2): String =
    "%.${decimals}f".format(Locale("pl", "PL"), value)

fun Double.roundToOneDecimal(): Double = (this * 10.0).roundToInt() / 10.0
