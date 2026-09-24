package com.example.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class AppLanguage(val code: String, val label: String, val englishName: String) {
    ENGLISH("en", "English", "English"),
    GUJARATI("gu", "ગુજરાતી", "Gujarati"),
    HINDI("hi", "हिन्दी", "Hindi"),
    MARATHI("mr", "मराठी", "Marathi"),
    BENGALI("bn", "বাংলা", "Bengali"),
    TAMIL("ta", "தமிழ்", "Tamil"),
    TELUGU("te", "తెలుగు", "Telugu"),
    KANNADA("kn", "ಕನ್ನಡ", "Kannada"),
    MALAYALAM("ml", "മലയാളം", "Malayalam"),
    PUNJABI("pa", "ਪੰਜਾਬੀ", "Punjabi"),
    ODIA("or", "ଓଡ଼ିଆ", "Odia"),
    ASSAMESE("as", "অসমীয়া", "Assamese"),
    URDU("ur", "اردو", "Urdu");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

class LanguageManager(initialLanguage: AppLanguage = AppLanguage.ENGLISH) {
    private val _currentLanguage = mutableStateOf(initialLanguage)
    var currentLanguage: AppLanguage
        get() = _currentLanguage.value
        set(value) {
            _currentLanguage.value = value
            globalLanguage = value
        }

    init {
        globalLanguage = initialLanguage
    }

    fun setLanguage(language: AppLanguage) {
        currentLanguage = language
    }

    companion object {
        var globalLanguage by mutableStateOf(AppLanguage.ENGLISH)

        fun locString(en: String, gu: String): String {
            return translate(en, gu)
        }

        fun translate(en: String, gu: String): String {
            return when (globalLanguage) {
                AppLanguage.ENGLISH -> en
                AppLanguage.GUJARATI -> gu
                else -> {
                    val custom = Translations.get(globalLanguage, en)
                    custom ?: en
                }
            }
        }

        fun isGujarati(): Boolean = globalLanguage == AppLanguage.GUJARATI

        // FORMATTERS: All numbers MUST ALWAYS use English digits (0-9)
        private val symbols = DecimalFormatSymbols(Locale.US)

        fun formatDouble(value: Double, decimals: Int = 2): String {
            return String.format(Locale.US, "%.${decimals}f", value)
        }

        fun formatCurrency(value: Double): String {
            val df = DecimalFormat("#,##,##0.00", symbols)
            return "₹ ${df.format(value)}"
        }

        fun formatCurrencyNoDecimals(value: Double): String {
            val df = DecimalFormat("#,##,##0", symbols)
            return "₹ ${df.format(value)}"
        }

        fun formatWeight(value: Double, isGu: Boolean = isGujarati()): String {
            val num = String.format(Locale.US, "%.3f", value)
            val unit = if (isGu) "ગ્રામ" else "g"
            return "$num $unit"
        }

        fun formatWeightWithLabel(labelEn: String, labelGu: String, value: Double, isGu: Boolean = isGujarati()): String {
            val label = if (isGu) labelGu else labelEn
            val num = String.format(Locale.US, "%.3f", value)
            val unit = if (isGu) "ગ્રામ" else "g"
            return "$label: $num $unit"
        }

        fun formatPercent(value: Double): String {
            return "${String.format(Locale.US, "%.1f", value)}%"
        }

        fun formatUnit(unit: String, isGu: Boolean = isGujarati()): String {
            return when (unit.lowercase().trim()) {
                "g", "gram", "grams", "ગ્રામ" -> if (isGu) "ગ્રામ" else "g"
                "kg", "કિલો", "કિલોગ્રામ" -> if (isGu) "કિલોગ્રામ" else "kg"
                "₹", "rs", "inr" -> "₹"
                "%" -> "%"
                else -> unit
            }
        }
    }
}

val LocalLanguageManager = compositionLocalOf { LanguageManager(AppLanguage.ENGLISH) }

/**
 * Returns Gujarati or English string depending on selected language.
 * Tracks Compose state so calling this inside any Composable triggers recomposition on language change.
 * Can also be safely called inside callbacks, remember blocks, and event handlers.
 */
fun loc(en: String, gu: String): String {
    return LanguageManager.translate(en, gu)
}

fun isAppGujarati(): Boolean {
    return LanguageManager.isGujarati()
}

fun formatLocWeight(value: Double): String {
    val isGu = isAppGujarati()
    return LanguageManager.formatWeight(value, isGu)
}

fun formatLocUnit(unit: String): String {
    val isGu = isAppGujarati()
    return LanguageManager.formatUnit(unit, isGu)
}
