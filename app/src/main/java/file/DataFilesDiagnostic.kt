/*
    OpenMW VR Quest - Morrowind Data Files Diagnostic Validator
    Performs startup and runtime health checks on the Morrowind 'Data Files' folder
    and provides clear, actionable error diagnostics when assets are missing.
*/

package file

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.preference.PreferenceManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.libopenmw.openmw.R
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
        MISSING_DATA_FILES_DIR,
        MISSING_MORROWIND_ESM,
        EMPTY_MORROWIND_ESM,
        MISSING_CORE_ARCHIVES,
        EMPTY_FOLDER
    }

    data class DiagnosticResult(
        val status: DiagnosticStatus,
        val configuredPath: String,
        val resolvedDataPath: String,
        val exists: Boolean,
        val isReadable: Boolean,
        val morrowindEsmFound: Boolean,
        val morrowindEsmSize: Long,
        val morrowindBsaFound: Boolean,
        val morrowindBsaSize: Long,
        val tribunalFound: Boolean,
        val bloodmoonFound: Boolean,
        val iniFound: Boolean,
        val iniPath: String?,
        val esmFiles: List<String>,
        val espFiles: List<String>,
        val bsaFiles: List<String>,
        val assetFolders: List<String>,
        val totalFilesCount: Int,
        val totalSizeBytes: Long,
        val missingCrucialItems: List<String>,
        val warnings: List<String>,
        val summaryTitle: String,
        val summaryMessage: String,
        val remediationAdvice: String
    ) {
        val isValid: Boolean
            get() = status == DiagnosticStatus.OK

        val hasGameData: Boolean
            get() = status != DiagnosticStatus.NOT_CONFIGURED && exists
    }

    /**
     * Runs a comprehensive diagnostic check on the configured Morrowind game folder.
     */
    fun check(context: Context? = null, customPath: String? = null): DiagnosticResult {
        val rawPath = (customPath ?: (context?.let {
            PreferenceManager.getDefaultSharedPreferences(it).getString("game_files", "")
        } ?: "")).trim()

        if (rawPath.isEmpty()) {
            return DiagnosticResult(
                status = DiagnosticStatus.NOT_CONFIGURED,
                configuredPath = "",
                resolvedDataPath = "",
                exists = false,
                isReadable = false,
                morrowindEsmFound = false,
                morrowindEsmSize = 0L,
                morrowindBsaFound = false,
                morrowindBsaSize = 0L,
                tribunalFound = false,
                bloodmoonFound = false,
                iniFound = false,
                iniPath = null,
                esmFiles = emptyList(),
                espFiles = emptyList(),
                bsaFiles = emptyList(),
                assetFolders = emptyList(),
                totalFilesCount = 0,
                totalSizeBytes = 0L,
                missingCrucialItems = listOf("Morrowind 'Data Files' directory path"),
                warnings = emptyList(),
                summaryTitle = "Game Data Not Configured",
                summaryMessage = "No Morrowind game data folder has been selected yet.",
                remediationAdvice = "Tap 'Select Game Folder' to locate your Morrowind installation or 'Data Files' directory on this device."
            )
        }

        val baseDir = File(rawPath)
        if (!baseDir.exists()) {
            return DiagnosticResult(
                status = DiagnosticStatus.NOT_FOUND,
                configuredPath = rawPath,
                resolvedDataPath = rawPath,
                exists = false,
                isReadable = false,
                morrowindEsmFound = false,
                morrowindEsmSize = 0L,
                morrowindBsaFound = false,
                morrowindBsaSize = 0L,
                tribunalFound = false,
                bloodmoonFound = false,
                iniFound = false,
                iniPath = null,
                esmFiles = emptyList(),
                espFiles = emptyList(),
                bsaFiles = emptyList(),
                assetFolders = emptyList(),
                totalFilesCount = 0,
                totalSizeBytes = 0L,
                missingCrucialItems = listOf("Directory does not exist: $rawPath"),
                warnings = emptyList(),
                summaryTitle = "Game Folder Not Found",
                summaryMessage = "The configured directory could not be found:\n$rawPath\n\nThe folder may have been moved, renamed, or deleted.",
                remediationAdvice = "Please re-select your Morrowind game folder using the file chooser."
            )
        }

        // Check storage permission & read access
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
                resolvedDataPath = rawPath,
                exists = true,
                isReadable = false,
                morrowindEsmFound = false,
                morrowindEsmSize = 0L,
                morrowindBsaFound = false,
                morrowindBsaSize = 0L,
                tribunalFound = false,
                bloodmoonFound = false,
                iniFound = false,
                iniPath = null,
                esmFiles = emptyList(),
                espFiles = emptyList(),
                bsaFiles = emptyList(),
                assetFolders = emptyList(),
                totalFilesCount = 0,
                totalSizeBytes = 0L,
                missingCrucialItems = listOf("Storage read permission denied"),
                warnings = emptyList(),
                summaryTitle = "Storage Permission Required",
                summaryMessage = "OpenMW does not have permission to read files in:\n$rawPath",
                remediationAdvice = "Open Storage Settings in the launcher and grant 'All files access' or Storage permissions."
            )
        }

        val installer = GameInstaller(rawPath)
        val resolvedDir = installer.resolveDataFilesDir()
        val resolvedPath = resolvedDir.absolutePath
        val dataDirExists = resolvedDir.exists() && resolvedDir.isDirectory

        if (!dataDirExists) {
            return DiagnosticResult(
                status = DiagnosticStatus.MISSING_DATA_FILES_DIR,
                configuredPath = rawPath,
                resolvedDataPath = resolvedPath,
                exists = true,
                isReadable = canRead,
                morrowindEsmFound = false,
                morrowindEsmSize = 0L,
                morrowindBsaFound = false,
                morrowindBsaSize = 0L,
                tribunalFound = false,
                bloodmoonFound = false,
                iniFound = false,
                iniPath = null,
                esmFiles = emptyList(),
                espFiles = emptyList(),
                bsaFiles = emptyList(),
                assetFolders = emptyList(),
                totalFilesCount = 0,
                totalSizeBytes = 0L,
                missingCrucialItems = listOf("Morrowind 'Data Files' folder"),
                warnings = emptyList(),
                summaryTitle = "Data Files Folder Missing",
                summaryMessage = "Could not locate a 'Data Files' folder or game assets in:\n$rawPath\n\nA standard Morrowind installation contains a 'Data Files' subfolder.",
                remediationAdvice = "Ensure you copied the entire Morrowind game folder (including the 'Data Files' directory) from your PC (Steam / GOG / CD)."
            )
        }

        // Scan files inside resolved Data Files directory
        val filesInDir = try {
            resolvedDir.listFiles() ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

        if (filesInDir.isEmpty()) {
            return DiagnosticResult(
                status = DiagnosticStatus.EMPTY_FOLDER,
                configuredPath = rawPath,
                resolvedDataPath = resolvedPath,
                exists = true,
                isReadable = canRead,
                morrowindEsmFound = false,
                morrowindEsmSize = 0L,
                morrowindBsaFound = false,
                morrowindBsaSize = 0L,
                tribunalFound = false,
                bloodmoonFound = false,
                iniFound = false,
                iniPath = null,
                esmFiles = emptyList(),
                espFiles = emptyList(),
                bsaFiles = emptyList(),
                assetFolders = emptyList(),
                totalFilesCount = 0,
                totalSizeBytes = 0L,
                missingCrucialItems = listOf("Directory is completely empty"),
                warnings = emptyList(),
                summaryTitle = "Data Files Folder Is Empty",
                summaryMessage = "The 'Data Files' folder at:\n$resolvedPath\nis completely empty (0 files found).",
                remediationAdvice = "Copy your Morrowind master (.esm) and asset (.bsa) files into this folder."
            )
        }

        val esmList = mutableListOf<String>()
        val espList = mutableListOf<String>()
        val bsaList = mutableListOf<String>()
        val assetFolderList = mutableListOf<String>()
        var totalSize = 0L
        var totalCount = 0

        var morrowindEsmFile: File? = null
        var morrowindBsaFile: File? = null
        var tribunalEsmFound = false
        var bloodmoonEsmFound = false

        val knownAssetFolderNames = setOf(
            "meshes", "textures", "sound", "music", "bookart", "icons", "fonts", "video", "splash"
        )

        for (file in filesInDir) {
            totalCount++
            totalSize += file.length()
            val name = file.name
            val nameLower = name.lowercase(Locale.ROOT)

            if (file.isDirectory) {
                if (knownAssetFolderNames.contains(nameLower)) {
                    assetFolderList.add(name)
                }
            } else {
                if (nameLower.endsWith(".esm") || nameLower.endsWith(".omwgame")) {
                    esmList.add(name)
                    if (nameLower == "morrowind.esm" || nameLower.startsWith("morrowind.")) {
                        morrowindEsmFile = file
                    }
                    if (nameLower.contains("tribunal")) tribunalEsmFound = true
                    if (nameLower.contains("bloodmoon")) bloodmoonEsmFound = true
                } else if (nameLower.endsWith(".esp") || nameLower.endsWith(".omwaddon")) {
                    espList.add(name)
                } else if (nameLower.endsWith(".bsa")) {
                    bsaList.add(name)
                    if (nameLower == "morrowind.bsa" || nameLower.startsWith("morrowind.")) {
                        morrowindBsaFile = file
                    }
                }
            }
        }

        val iniFile = installer.findIniFile()
        val missingCrucial = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Check Morrowind.esm
        val esmFound = morrowindEsmFile != null || esmList.isNotEmpty()
        val esmSize = morrowindEsmFile?.length() ?: 0L

        if (!esmFound) {
            missingCrucial.add("Primary Master: Morrowind.esm (or .omwgame)")
        } else if (esmSize < 50000L && morrowindEsmFile != null) {
            missingCrucial.add("Morrowind.esm is empty or corrupted (${formatHumanSize(esmSize)})")
        }

        // 2. Check Morrowind.bsa or loose meshes/textures
        val bsaFound = morrowindBsaFile != null || bsaList.isNotEmpty()
        val bsaSize = morrowindBsaFile?.length() ?: 0L
        val hasLooseMeshesOrTextures = assetFolderList.any {
            it.equals("meshes", ignoreCase = true) || it.equals("textures", ignoreCase = true)
        }

        if (!bsaFound && !hasLooseMeshesOrTextures) {
            missingCrucial.add("Core Archive: Morrowind.bsa (or Meshes/Textures folders)")
        }

        // Check expansions / optional assets
        if (!tribunalEsmFound) {
            warnings.add("Tribunal expansion (.esm) not detected (optional)")
        }
        if (!bloodmoonEsmFound) {
            warnings.add("Bloodmoon expansion (.esm) not detected (optional)")
        }
        if (iniFile == null) {
            warnings.add("Morrowind.ini not found (OpenMW default fallback config will be used)")
        }

        // Determine Final Status
        val status: DiagnosticStatus = when {
            !esmFound -> DiagnosticStatus.MISSING_MORROWIND_ESM
            esmSize < 50000L && morrowindEsmFile != null -> DiagnosticStatus.EMPTY_MORROWIND_ESM
            !bsaFound && !hasLooseMeshesOrTextures -> DiagnosticStatus.MISSING_CORE_ARCHIVES
            else -> DiagnosticStatus.OK
        }

        val summaryTitle = when (status) {
            DiagnosticStatus.OK -> "Morrowind Data Files Verified"
            DiagnosticStatus.MISSING_MORROWIND_ESM -> "Missing Morrowind.esm"
            DiagnosticStatus.EMPTY_MORROWIND_ESM -> "Corrupted Morrowind.esm"
            DiagnosticStatus.MISSING_CORE_ARCHIVES -> "Missing Game Archives (Morrowind.bsa)"
            else -> "Data Files Incomplete"
        }

        val summaryMessage = when (status) {
            DiagnosticStatus.OK -> "Morrowind Data Files folder is correctly populated with master files and game assets."
            DiagnosticStatus.MISSING_MORROWIND_ESM -> "The 'Data Files' folder at:\n$resolvedPath\ndoes not contain 'Morrowind.esm'."
            DiagnosticStatus.EMPTY_MORROWIND_ESM -> "Found 'Morrowind.esm' at:\n$resolvedPath\nbut the file is 0 bytes or damaged (${formatHumanSize(esmSize)})."
            DiagnosticStatus.MISSING_CORE_ARCHIVES -> "Found 'Morrowind.esm', but the main archive 'Morrowind.bsa' and loose asset folders are missing in:\n$resolvedPath."
            else -> "Missing game data in $resolvedPath."
        }

        val remediationAdvice = when (status) {
            DiagnosticStatus.OK -> "All core game files are in place. You are ready to launch Morrowind VR!"
            DiagnosticStatus.MISSING_MORROWIND_ESM -> "Copy 'Morrowind.esm' from your PC installation (e.g., GOG: 'C:\\GOG Games\\Morrowind\\Data Files\\' or Steam: 'steamapps\\common\\Morrowind\\Data Files\\') into this folder on your Quest/Android device."
            DiagnosticStatus.EMPTY_MORROWIND_ESM -> "Re-copy a complete, uncorrupted 'Morrowind.esm' (standard GOTY file size is ~79 MB) to this folder."
            DiagnosticStatus.MISSING_CORE_ARCHIVES -> "Copy 'Morrowind.bsa' (and optionally 'Tribunal.bsa', 'Bloodmoon.bsa') from your PC installation's Data Files folder so models, sounds, and textures can load."
            else -> "Verify your game data files and ensure all files are copied completely."
        }

        Log.i(TAG, "Diagnostic result: status=$status, esmFound=$esmFound, bsaCount=${bsaList.size}, totalFiles=$totalCount, size=$totalSize")

        return DiagnosticResult(
            status = status,
            configuredPath = rawPath,
            resolvedDataPath = resolvedPath,
            exists = true,
            isReadable = canRead,
            morrowindEsmFound = esmFound,
            morrowindEsmSize = esmSize,
            morrowindBsaFound = bsaFound,
            morrowindBsaSize = bsaSize,
            tribunalFound = tribunalEsmFound,
            bloodmoonFound = bloodmoonEsmFound,
            iniFound = iniFile != null,
            iniPath = iniFile?.absolutePath,
            esmFiles = esmList,
            espFiles = espList,
            bsaFiles = bsaList,
            assetFolders = assetFolderList,
            totalFilesCount = totalCount,
            totalSizeBytes = totalSize,
            missingCrucialItems = missingCrucial,
            warnings = warnings,
            summaryTitle = summaryTitle,
            summaryMessage = summaryMessage,
            remediationAdvice = remediationAdvice
        )
    }

    /**
     * Formats bytes into clean human readable format (e.g. 79.2 MB, 1.15 GB)
     */
    fun formatHumanSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> String.format(Locale.ROOT, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.ROOT, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.ROOT, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Displays a rich, detailed diagnostic report dialog to the user.
     */
    fun showDiagnosticDialog(
        context: Context,
        result: DiagnosticResult,
        onSelectFolder: (() -> Unit)? = null
    ) {
        val builder = AlertDialog.Builder(context)
        val sb = SpannableStringBuilder()

        // 1. Status Overview Header
        val statusHeader = if (result.isValid) "STATUS: VERIFIED & HEALTHY\n\n" else "STATUS: ACTION REQUIRED\n\n"
        val headerStart = sb.length
        sb.append(statusHeader)
        sb.setSpan(StyleSpan(Typeface.BOLD), headerStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        sb.append("${result.summaryMessage}\n\n")

        // 2. Location Section
        if (result.resolvedDataPath.isNotEmpty()) {
            val locTitleStart = sb.length
            sb.append("DATA FILES DIRECTORY:\n")
            sb.setSpan(StyleSpan(Typeface.BOLD), locTitleStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("${result.resolvedDataPath}\n\n")
        }

        // 3. Detailed Assets Checklist
        val checkTitleStart = sb.length
        sb.append("ASSETS DIAGNOSTIC CHECKLIST:\n")
        sb.setSpan(StyleSpan(Typeface.BOLD), checkTitleStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Master file check
        if (result.morrowindEsmFound) {
            sb.append("• Morrowind.esm:  Found (${formatHumanSize(result.morrowindEsmSize)})\n")
        } else {
            sb.append("• Morrowind.esm:  MISSING (Required)\n")
        }

        // Core BSA check
        if (result.morrowindBsaFound) {
            sb.append("• Morrowind.bsa:  Found (${formatHumanSize(result.morrowindBsaSize)})\n")
        } else if (result.assetFolders.contains("Meshes") || result.assetFolders.contains("Textures")) {
            sb.append("• Morrowind.bsa:  Loose Meshes/Textures present\n")
        } else {
            sb.append("• Morrowind.bsa:  MISSING (Textures/Meshes archive)\n")
        }

        // Expansions check
        val expansions = mutableListOf<String>()
        if (result.tribunalFound) expansions.add("Tribunal")
        if (result.bloodmoonFound) expansions.add("Bloodmoon")
        if (expansions.isNotEmpty()) {
            sb.append("• Expansions:  ${expansions.joinToString(", ")}\n")
        } else {
            sb.append("• Expansions:  None (Base Morrowind only)\n")
        }

        // Asset folders
        if (result.assetFolders.isNotEmpty()) {
            sb.append("• Asset Folders:  ${result.assetFolders.joinToString(", ")}\n")
        }

        // Total content plugins & BSAs
        val totalMods = result.espFiles.size
        sb.append("• Content: ${result.esmFiles.size} Master(s), $totalMods Plugin(s), ${result.bsaFiles.size} Archive(s)\n")
        if (result.totalFilesCount > 0) {
            sb.append("• Total Size: ${formatHumanSize(result.totalSizeBytes)} across ${result.totalFilesCount} files\n")
        }

        // INI check
        if (result.iniFound) {
            sb.append("• Morrowind.ini:  Found\n")
        } else {
            sb.append("• Morrowind.ini:  Default OpenMW config generated\n")
        }

        sb.append("\n")

        // 4. Missing items & Remediation Advice
        if (result.missingCrucialItems.isNotEmpty()) {
            val missTitleStart = sb.length
            sb.append("MISSING ASSETS:\n")
            sb.setSpan(StyleSpan(Typeface.BOLD), missTitleStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.missingCrucialItems.forEach { item ->
                sb.append("  $item\n")
            }
            sb.append("\n")
        }

        val adviceTitleStart = sb.length
        sb.append("HOW TO RESOLVE:\n")
        sb.setSpan(StyleSpan(Typeface.BOLD), adviceTitleStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("${result.remediationAdvice}\n")

        builder.setTitle(result.summaryTitle)
        builder.setMessage(sb)
        builder.setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }

        if (onSelectFolder != null) {
            builder.setNeutralButton("Select Folder") { _, _ -> onSelectFolder() }
        }

        builder.show()
    }
}
