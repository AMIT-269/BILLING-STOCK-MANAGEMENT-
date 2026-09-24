package com.example.data.sync

object LicenceValidator {
    // Single Owner Licence Code for new registration activation
    const val OWNER_LICENCE_CODE = "M30P23"

    /**
     * Validates owner activation licence code for NEW REGISTRATION.
     * The valid Owner Licence Code is strictly "M30P23".
     * Secret credential - never shown or hinted anywhere in the app UI.
     */
    fun isValidLicence(rawCode: String): Boolean {
        val code = rawCode.trim()
        return code == OWNER_LICENCE_CODE
    }
}
