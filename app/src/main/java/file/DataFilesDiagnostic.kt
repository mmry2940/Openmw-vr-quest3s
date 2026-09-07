package file

import android.app.AlertDialog
import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import permission.PermissionHelper
import java.io.File
import java.util.Locale

object DataFilesDiagnostic {

    private const val TAG = "DataFilesDiagnostic"

    enum class DiagnosticStatus {
        OK,
        NOT_CONFIGURED,
        NOT_FOUND,
        PERMISSION_DENIED,
        MISSING_MORROWIND_ESM,
        EMPTY_MORROWIND_ESM,
        MISSING_CORE_ARCHIVES,
        MISSING_ASSET_FOLDERS,
        INCOMPLETE
    }

    data class DiagnosticResult(
        val status: DiagnosticStatus,
        val configuredPath: String,
        val resolvedDataPath: String,
        val morrowindEsmFound: Boolean = false,
        val morrowindEsmSize: Long = 0L,
        val morrowindBsaFound: Boolean = false,
        val morrowindBsaSize: Long = 0L,
        val tribunalFound: Boolean = false,
        val bloodmoonFound: Boolean = false,
        val iniFound: Boolean = false,
        val esmFiles: List<String> = emptyList(),
        val espFiles: List<String> = emptyList(),
        val bsaFiles: List<String> = emptyList(),
        val missingCrucialItems: List<String> = emptyList(),
        val missingOptionalItems: List<String> = emptyList(),
        val foundAssetFolders: List<String> = emptyList(),
        val missingAssetFolders: List<String> = emptyList(),
        val totalFileCount: Int = 0,
        val totalSizeBytes: Long = 0L,
        val summaryTitle: String = "",
        val summaryMessage: String = "",
        val remediationAdvice: String = ""
    ) {
        val isValid: Boolean
            get() = status == DiagnosticStatus.OK
    }

    fun check(context: Context? = null, customPath: String? = null): DiagnosticResult {
        val rawPath = (customPath ?: (context?.let {
            PreferenceManager.getDefaultSharedPreferences(it).getString("game_files", "")
        } ?: "")).trim()

        if (rawPath.isEmpty()) {
            return DiagnosticResult(
                status = DiagnosticStatus.NOT_CONFIGURED,
                configuredPath = "",
                resolvedDataPath = "",
                summaryTitle = "Game Data Not Configured",
                summaryMessage = "No Morrowind data folder has been selected yet.",
                remediationAdvice = "Click 'Select Game Data Folder' to choose your Morrowind directory containing 'Data Files'."
            )
        }

        val baseDir = File(rawPath)
        if (!baseDir.exists() || !baseDir.isDirectory) {
            return DiagnosticResult(
                status = DiagnosticStatus.NOT_FOUND,
                configuredPath = rawPath,
                resolvedDataPath = "",
                summaryTitle = "Folder Not Found",
                summaryMessage = "The configured directory does not exist or cannot be opened:\n$rawPath",
                remediationAdvice = "Ensure your Quest storage has the Morrowind files copied and select the folder again."
            )
        }

        val hasPermission = context?.let { PermissionHelper.hasStoragePermission(it) } ?: true
        val canRead = try {
            baseDir.canRead() && baseDir.listFiles() != null
        } catch (_: Exception) {
            false
        }

        if (!hasPermission && !canRead) {
            return DiagnosticResult(
                status = DiagnosticStatus.PERMISSION_DENIED,
                configuredPath = rawPath,
                resolvedDataPath = "",
                summaryTitle = "Storage Permission Required",
                summaryMessage = "OpenMW cannot read files from:\n$rawPath\ndue to missing Android All Files Access permission.",
                remediationAdvice = "Grant Storage Permission via the prompt on screen."
            )
        }

        val resolvedDir: File = run {
            val subData = File(baseDir, "Data Files")
            if (subData.exists() && subData.isDirectory) {
                subData
            } else {
                val caseInsensitiveSub = baseDir.listFiles()?.firstOrNull {
                    it.isDirectory && it.name.equals("Data Files", ignoreCase = true)
                }
                caseInsensitiveSub ?: baseDir
            }
        }

        val entries = resolvedDir.listFiles() ?: emptyArray()

        var mwEsmFound = false
        var mwEsmSize = 0L
        var mwBsaFound = false
        var mwBsaSize = 0L
        var tribunalFound = false
        var bloodmoonFound = false

        val esmList = mutableListOf<String>()
        val espList = mutableListOf<String>()
        val bsaList = mutableListOf<String>()
        var totalBytes = 0L

        for (file in entries) {
            if (file.isFile) {
                totalBytes += file.length()
                val name = file.name
                when {
                    name.equals("Morrowind.esm", ignoreCase = true) -> {
                        mwEsmFound = true
                        mwEsmSize = file.length()
                        esmList.add(name)
                    }
                    name.equals("Morrowind.bsa", ignoreCase = true) -> {
                        mwBsaFound = true
                        mwBsaSize = file.length()
                        bsaList.add(name)
                    }
                    name.equals("Tribunal.esm", ignoreCase = true) -> {
                        tribunalFound = true
                        esmList.add(name)
                    }
                    name.equals("Bloodmoon.esm", ignoreCase = true) -> {
                        bloodmoonFound = true
                        esmList.add(name)
                    }
                    name.endsWith(".esm", ignoreCase = true) || name.endsWith(".omwgame", ignoreCase = true) -> {
                        esmList.add(name)
                    }
                    name.endsWith(".esp", ignoreCase = true) || name.endsWith(".omwaddon", ignoreCase = true) -> {
                        espList.add(name)
                    }
                    name.endsWith(".bsa", ignoreCase = true) -> {
                        bsaList.add(name)
                    }
                }
            }
        }

        val iniFound = File(baseDir, "Morrowind.ini").exists() || File(resolvedDir, "Morrowind.ini").exists()

        val crucialMissing = mutableListOf<String>()
        val optionalMissing = mutableListOf<String>()

        if (!mwEsmFound) {
            crucialMissing.add("Morrowind.esm (Main Master File)")
        } else if (mwEsmSize < 1024) {
            crucialMissing.add("Morrowind.esm (0 or corrupted bytes)")
        }

        if (!mwBsaFound && bsaList.isEmpty()) {
            crucialMissing.add("Morrowind.bsa (Core Archive)")
        }

        if (!tribunalFound) optionalMissing.add("Tribunal.esm (Tribunal Expansion)")
        if (!bloodmoonFound) optionalMissing.add("Bloodmoon.esm (Bloodmoon Expansion)")
        if (!iniFound) optionalMissing.add("Morrowind.ini (Configuration file)")

        val status = when {
            !mwEsmFound -> DiagnosticStatus.MISSING_MORROWIND_ESM
            mwEsmSize < 1024 -> DiagnosticStatus.EMPTY_MORROWIND_ESM
            !mwBsaFound && bsaList.isEmpty() -> DiagnosticStatus.MISSING_CORE_ARCHIVES
            else -> DiagnosticStatus.OK
        }

        val summaryTitle = when (status) {
            DiagnosticStatus.OK -> "Morrowind Game Data Verified"
            DiagnosticStatus.MISSING_MORROWIND_ESM -> "Missing Morrowind.esm"
            DiagnosticStatus.EMPTY_MORROWIND_ESM -> "Corrupted Morrowind.esm"
            DiagnosticStatus.MISSING_CORE_ARCHIVES -> "Missing Game Archives (.bsa)"
            else -> "Incomplete Game Data"
        }

        val summaryMessage = if (status == DiagnosticStatus.OK) {
            "Found valid Morrowind installation in:\n${resolvedDir.absolutePath}\n\nMaster Files: ${esmList.size}\nPlugins: ${espList.size}\nArchives: ${bsaList.size}"
        } else {
            "Issues found in:\n${resolvedDir.absolutePath}\n\nMissing required files:\n• " + crucialMissing.joinToString("\n• ")
        }

        val remediation = if (status == DiagnosticStatus.OK) {
            "Your game files are verified and ready to play in VR!"
        } else {
            "Copy your Morrowind installation (from Steam or GOG) to your Quest storage (e.g. /sdcard/Morrowind/Data Files)."
        }

        return DiagnosticResult(
            status = status,
            configuredPath = rawPath,
            resolvedDataPath = resolvedDir.absolutePath,
            morrowindEsmFound = mwEsmFound,
            morrowindEsmSize = mwEsmSize,
            morrowindBsaFound = mwBsaFound,
            morrowindBsaSize = mwBsaSize,
            tribunalFound = tribunalFound,
            bloodmoonFound = bloodmoonFound,
            iniFound = iniFound,
            esmFiles = esmList,
            espFiles = espList,
            bsaFiles = bsaList,
            missingCrucialItems = crucialMissing,
            missingOptionalItems = optionalMissing,
            totalFileCount = entries.size,
            totalSizeBytes = totalBytes,
            summaryTitle = summaryTitle,
            summaryMessage = summaryMessage,
            remediationAdvice = remediation
        )
    }

    fun formatHumanSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return if (digitGroups == 0) "$bytes B" else String.format(Locale.ROOT, "%.1f %s", formatted, units[digitGroups])
    }

    fun showDiagnosticDialog(context: Context, result: DiagnosticResult, onSelectFolderClicked: () -> Unit) {
        val message = buildString {
            append(result.summaryMessage)
            append("\n\n")
            append("Remediation:\n")
            append(result.remediationAdvice)
        }

        AlertDialog.Builder(context)
            .setTitle(result.summaryTitle)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Select Folder") { _, _ ->
                onSelectFolderClicked()
            }
            .show()
    }
}
