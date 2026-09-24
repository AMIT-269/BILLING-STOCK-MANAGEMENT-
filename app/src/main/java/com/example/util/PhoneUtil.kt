package com.example.util

object PhoneUtil {
    fun convertIndicToAscii(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                '૦', '०' -> sb.append('0')
                '૧', '१' -> sb.append('1')
                '૨', '२' -> sb.append('2')
                '૩', '३' -> sb.append('3')
                '૪', '४' -> sb.append('4')
                '૫', '५' -> sb.append('5')
                '૬', '६' -> sb.append('6')
                '૭', '७' -> sb.append('7')
                '૮', '८' -> sb.append('8')
                '૯', '९' -> sb.append('9')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Normalizes a phone number to standard 10-digit Indian mobile number:
     * - Converts any Gujarati or Devanagari numerals to ASCII 0-9
     * - Strips all non-digits (spaces, dashes, parens, '+', etc.)
     * - Handles +91, 91, 0 prefixes
     * - Returns exactly 10 digits when valid
     */
    fun normalizePhone(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val ascii = convertIndicToAscii(raw.trim())
        val digits = ascii.filter { it in '0'..'9' }
        return when {
            digits.length == 10 -> digits
            digits.length == 11 && digits.startsWith("0") -> digits.substring(1)
            digits.length == 12 && digits.startsWith("91") -> digits.substring(2)
            digits.length == 13 && digits.startsWith("091") -> digits.substring(3)
            digits.length > 10 -> digits.takeLast(10)
            else -> digits
        }
    }

    /**
     * Strict 10-digit normalized mobile number alias
     */
    fun normalizeMobile(raw: String?): String {
        return normalizePhone(raw)
    }

    /**
     * Normalizes GST Number:
     * - Converts any Indic numerals to ASCII
     * - Trims and converts to uppercase
     * - Strips all spaces, dashes, and non-alphanumeric characters
     */
    fun normalizeGst(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val ascii = convertIndicToAscii(raw)
        return ascii.uppercase().filter { it.isLetterOrDigit() }
    }

    /**
     * Validates whether a mobile number is exactly 10 digits
     */
    fun isValidMobile(raw: String?): Boolean {
        return normalizeMobile(raw).length == 10
    }

    /**
     * Normalizes general text: trims whitespace and collapses multiple spaces
     */
    fun normalizeText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Normalizes a 4-digit security code:
     * - Converts any Gujarati or Devanagari numerals to ASCII 0-9
     * - Filters out non-digit characters
     */
    fun normalizeCode(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val ascii = convertIndicToAscii(raw.trim())
        return ascii.filter { it in '0'..'9' }
    }

    /**
     * Sanitizes input typed or pasted into phone input field
     */
    fun sanitizePhoneInput(input: String): String {
        val ascii = convertIndicToAscii(input)
        val digitsOnly = ascii.filter { it in '0'..'9' }
        return when {
            digitsOnly.length > 10 && digitsOnly.startsWith("91") -> digitsOnly.drop(2).take(10)
            digitsOnly.length > 10 && digitsOnly.startsWith("0") -> digitsOnly.drop(1).take(10)
            digitsOnly.length > 10 -> digitsOnly.takeLast(10)
            else -> digitsOnly.take(10)
        }
    }

    /**
     * Sanitizes input typed or pasted into 4-digit code field
     */
    fun sanitizeCodeInput(input: String): String {
        val ascii = convertIndicToAscii(input)
        return ascii.filter { it in '0'..'9' }.take(4)
    }
}
