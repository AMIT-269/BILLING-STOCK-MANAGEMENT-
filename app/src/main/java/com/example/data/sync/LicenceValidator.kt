package com.example.data.sync

object LicenceValidator {
    // Owner Licence Codes for new registration activation
    const val OWNER_LICENCE_CODE = "M30P23"
    const val OWNER_BYPASS_CODE = "2330"

    /**
     * Validates owner activation licence code for NEW REGISTRATION.
     * Accepts official owner codes "M30P23" and "2330".
     * Secret credential - never shown or hinted anywhere in the app UI.
     */
    fun isValidLicence(rawCode: String): Boolean {
        val code = rawCode.trim()
        return code == OWNER_LICENCE_CODE || code == OWNER_BYPASS_CODE
    }
}
