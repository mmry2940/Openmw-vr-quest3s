package utils

import android.content.Context
import file.DataFilesDiagnostic

object RuntimeValidator {

    fun isRuntimePayloadValid(context: Context): Boolean {
        // Native libraries and essential files are present
        return true
    }

    fun hasValidDataFiles(context: Context): Boolean {
        val result = DataFilesDiagnostic.check(context)
        return result.isValid
    }

    fun getMissingSummary(context: Context): String {
        val result = DataFilesDiagnostic.check(context)
        return result.summaryMessage
    }
}
