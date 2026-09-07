/*
    OpenMW VR Quest - Simple Launcher Activity
    Displays a simple button-based UI for configuring game data and launching the game
*/

package ui.activity

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.codekidlabs.storagechooser.StorageChooser
import com.libopenmw.openmw.R
import file.DataFilesDiagnostic
import file.GameInstaller
import permission.PermissionHelper

private const val TAG = "LauncherActivity"

class LauncherActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var selectDataButton: Button
    private lateinit var btnDiagnoseData: Button
    private lateinit var launchGameButton: Button
    private lateinit var manageModsButton: Button
    private lateinit var settingsButton: Button
    private lateinit var vrCalibrationButton: Button
    private lateinit var storageButton: Button
    private lateinit var cardStorageWarning: android.view.View
    private lateinit var btnOpenStoragePermissions: Button

    private lateinit var cardDataDiagnosticAlert: android.view.View
    private lateinit var diagnosticAlertTitle: TextView
    private lateinit var diagnosticAlertMessage: TextView
    private lateinit var btnFixGameData: Button
    private lateinit var btnViewDiagnosticReport: Button

    private var launchInProgress: Boolean = false
    private var lastDiagnosticResult: DataFilesDiagnostic.DiagnosticResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "LauncherActivity.onCreate: starting")
        super.onCreate(savedInstanceState)
        
        PermissionHelper.getWriteExternalStoragePermission(this)
        setContentView(R.layout.launcher)
        Log.d(TAG, "LauncherActivity: set launcher layout")
        
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Initialize view references
        selectDataButton = findViewById(R.id.select_data_button)
        btnDiagnoseData = findViewById(R.id.btn_diagnose_data)
        launchGameButton = findViewById(R.id.launch_game_button)
        manageModsButton = findViewById(R.id.manage_mods_button)
        settingsButton = findViewById(R.id.settings_button)
        storageButton = findViewById(R.id.storage_button)
        vrCalibrationButton = findViewById(R.id.btn_vr_calibration)
        cardStorageWarning = findViewById(R.id.card_storage_warning)
        btnOpenStoragePermissions = findViewById(R.id.btn_open_storage_permissions)

        cardDataDiagnosticAlert = findViewById(R.id.card_data_diagnostic_alert)
        diagnosticAlertTitle = findViewById(R.id.diagnostic_alert_title)
        diagnosticAlertMessage = findViewById(R.id.diagnostic_alert_message)
        btnFixGameData = findViewById(R.id.btn_fix_game_data)
        btnViewDiagnosticReport = findViewById(R.id.btn_view_diagnostic_report)

        // Run startup diagnostic check and update UI
        runDiagnosticCheck(showDialogIfCorrupted = false)
        updateVrCalibrationDisplay()
        updateStoragePermissionState()

        btnOpenStoragePermissions.setOnClickListener {
            startActivity(Intent(this, StoragePermissionActivity::class.java))
        }

        storageButton.setOnClickListener {
            startActivity(Intent(this, StoragePermissionActivity::class.java))
        }

        manageModsButton.setOnClickListener {
            startActivity(Intent(this, ModsActivity::class.java))
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        vrCalibrationButton.setOnClickListener {
            startActivity(Intent(this, VrCalibrationActivity::class.java))
        }

        findViewById<android.view.View>(R.id.card_vr_calibration).setOnClickListener {
            startActivity(Intent(this, VrCalibrationActivity::class.java))
        }

        findViewById<android.view.View>(R.id.card_game_data).setOnClickListener {
            lastDiagnosticResult?.let { result ->
                DataFilesDiagnostic.showDiagnosticDialog(this, result) {
                    selectGameData()
                }
            }
        }

        btnDiagnoseData.setOnClickListener {
            val result = DataFilesDiagnostic.check(this)
            lastDiagnosticResult = result
            DataFilesDiagnostic.showDiagnosticDialog(this, result) {
                selectGameData()
            }
        }

        btnFixGameData.setOnClickListener {
            selectGameData()
        }

        btnViewDiagnosticReport.setOnClickListener {
            lastDiagnosticResult?.let { result ->
                DataFilesDiagnostic.showDiagnosticDialog(this, result) {
                    selectGameData()
                }
            } ?: run {
                val result = DataFilesDiagnostic.check(this)
                lastDiagnosticResult = result
                DataFilesDiagnostic.showDiagnosticDialog(this, result) {
                    selectGameData()
                }
            }
        }

        selectDataButton.setOnClickListener {
            if (launchInProgress) {
                Log.d(TAG, "Select data ignored: launch already in progress")
                return@setOnClickListener
            }
            if (!PermissionHelper.hasStoragePermission(this)) {
                Toast.makeText(this, "Storage permission required to browse and load game files", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, StoragePermissionActivity::class.java))
                return@setOnClickListener
            }
            Log.d(TAG, "Select data button clicked")
            selectGameData()
        }
        
        launchGameButton.setOnClickListener {
            if (launchInProgress) {
                Log.d(TAG, "Launch game ignored: launch already in progress")
                return@setOnClickListener
            }
            if (!PermissionHelper.hasStoragePermission(this)) {
                Toast.makeText(this, "Storage permission required to locate and launch Morrowind VR", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, StoragePermissionActivity::class.java))
                return@setOnClickListener
            }
            launchInProgress = true
            setControlsEnabled(false)
            Log.d(TAG, "Launch game button clicked")
            checkStartGame()
        }

        Log.d(TAG, "LauncherActivity.onCreate: completed successfully")
    }

    override fun onResume() {
        super.onResume()
        runDiagnosticCheck(showDialogIfCorrupted = false)
        updateVrCalibrationDisplay()
        updateStoragePermissionState()
        setControlsEnabled(true)
        launchInProgress = false
    }

    private fun setControlsEnabled(enabled: Boolean) {
        launchGameButton.isEnabled = enabled
        selectDataButton.isEnabled = enabled
        btnDiagnoseData.isEnabled = enabled
        manageModsButton.isEnabled = enabled
        settingsButton.isEnabled = enabled
        storageButton.isEnabled = enabled
        vrCalibrationButton.isEnabled = enabled
    }

    private fun updateStoragePermissionState() {
        val hasPermission = PermissionHelper.hasStoragePermission(this)
        cardStorageWarning.visibility = if (hasPermission) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun updateVrCalibrationDisplay() {
        val heightCm = try {
            prefs.getFloat("pref_vr_height_val", prefs.getString("pref_vr_height", "175.0")?.toFloatOrNull() ?: 175f)
        } catch (e: Exception) {
            175f
        }

        val ipdMm = try {
            prefs.getFloat("pref_vr_eye_offset_val", prefs.getString("pref_vr_eye_offset", "64.0")?.toFloatOrNull() ?: 64f)
        } catch (e: Exception) {
            64f
        }

        val stance = prefs.getString("pref_vr_stance", "standing")
        val isSeated = stance.equals("seated", ignoreCase = true)

        val badge = findViewById<TextView>(R.id.vr_calibration_badge)
        val description = findViewById<TextView>(R.id.vr_calibration_description)

        val totalInches = (heightCm / 2.54f).toInt()
        val feet = totalInches / 12
        val inches = totalInches % 12
        val stanceLabel = if (isSeated) "Seated Mode" else "Standing Mode"

        badge?.text = "${heightCm.toInt()} cm • ${ipdMm.toInt()} mm"
        description?.text = "Height: ${heightCm.toInt()} cm (${feet}'${inches}\") • Eye IPD: ${String.format(java.util.Locale.ROOT, "%.1f", ipdMm)} mm • $stanceLabel"
    }

    /**
     * Performs startup and onResume diagnostic validation on Morrowind Data Files.
     */
    private fun runDiagnosticCheck(showDialogIfCorrupted: Boolean = false) {
        val result = DataFilesDiagnostic.check(this)
        lastDiagnosticResult = result

        val pathDisplay = findViewById<TextView>(R.id.game_data_path)
        val badge = findViewById<TextView>(R.id.game_data_badge)
        val statusIcon = findViewById<ImageView>(R.id.game_data_status_icon)
        val summaryDisplay = findViewById<TextView>(R.id.game_data_diagnostic_summary)
        val statusMsg = findViewById<TextView>(R.id.status_message)

        if (result.status == DataFilesDiagnostic.DiagnosticStatus.NOT_CONFIGURED) {
            pathDisplay.text = "(not configured - tap button below)"
            pathDisplay.setTextColor(getColor(R.color.text_secondary))
            badge.text = "Required"
            badge.setTextColor(getColor(R.color.status_warning))
            statusIcon.setImageResource(R.drawable.ic_folder_open_24)
            statusIcon.setColorFilter(getColor(R.color.gold_accent))
            summaryDisplay.text = "Select your Morrowind game folder containing 'Data Files'."
            cardDataDiagnosticAlert.visibility = android.view.View.GONE
            statusMsg.text = "Ready to configure"
            return
        }

        pathDisplay.text = result.configuredPath
        pathDisplay.setTextColor(getColor(R.color.text_primary))

        if (result.isValid) {
            badge.text = "Verified"
            badge.setTextColor(getColor(R.color.status_ready))
            statusIcon.setImageResource(R.drawable.ic_check_circle_24)
            statusIcon.setColorFilter(getColor(R.color.status_ready))

            val expansionsInfo = when {
                result.tribunalFound && result.bloodmoonFound -> "GOTY (Tribunal + Bloodmoon)"
                result.tribunalFound -> "+ Tribunal"
                result.bloodmoonFound -> "+ Bloodmoon"
                else -> "Base Game"
            }
            val sizeStr = DataFilesDiagnostic.formatHumanSize(result.totalSizeBytes)
            summaryDisplay.text = "Morrowind.esm (${DataFilesDiagnostic.formatHumanSize(result.morrowindEsmSize)}) • ${result.bsaFiles.size} BSA(s) • $expansionsInfo • $sizeStr"
            summaryDisplay.setTextColor(getColor(R.color.text_secondary))

            cardDataDiagnosticAlert.visibility = android.view.View.GONE
            statusMsg.text = "Ready to launch!"
        } else {
            // Data files exist or are configured, but files are missing or incomplete
            badge.text = when (result.status) {
                DataFilesDiagnostic.DiagnosticStatus.MISSING_MORROWIND_ESM -> "Missing ESM"
                DataFilesDiagnostic.DiagnosticStatus.MISSING_CORE_ARCHIVES -> "Missing BSA"
                DataFilesDiagnostic.DiagnosticStatus.EMPTY_MORROWIND_ESM -> "Corrupted"
                DataFilesDiagnostic.DiagnosticStatus.NOT_FOUND -> "Not Found"
                DataFilesDiagnostic.DiagnosticStatus.PERMISSION_DENIED -> "No Permission"
                else -> "Incomplete"
            }
            badge.setTextColor(getColor(R.color.status_error))
            statusIcon.setImageResource(R.drawable.ic_warning_amber_24)
            statusIcon.setColorFilter(getColor(R.color.status_error))

            summaryDisplay.text = "${result.summaryTitle}: ${result.missingCrucialItems.firstOrNull() ?: "Assets missing"}"
            summaryDisplay.setTextColor(getColor(R.color.status_error))

            // Show startup diagnostic alert card
            cardDataDiagnosticAlert.visibility = android.view.View.VISIBLE
            diagnosticAlertTitle.text = result.summaryTitle
            diagnosticAlertMessage.text = "${result.summaryMessage}\n\nFix: ${result.remediationAdvice}"

            statusMsg.text = "Game files need attention"

            if (showDialogIfCorrupted) {
                DataFilesDiagnostic.showDiagnosticDialog(this, result) {
                    selectGameData()
                }
            }
        }

        Log.d(TAG, "runDiagnosticCheck: status=${result.status}, valid=${result.isValid}")
    }
    
    private fun selectGameData() {
        Log.d(TAG, "selectGameData: launching file browser")
        val chooser = StorageChooser.Builder()
            .withActivity(this)
            .withFragmentManager(fragmentManager)
            .withMemoryBar(true)
            .allowCustomPath(true)
            .setType(StorageChooser.DIRECTORY_CHOOSER)
            .build()

        chooser.show()

        chooser.setOnSelectListener { path ->
            Log.d(TAG, "selectGameData: user selected path='$path'")
            setupData(path)
        }
    }
    
    private fun setupData(path: String) {
        Log.d(TAG, "setupData: path='$path'")
        val cleanPath = path.trim()
        if (cleanPath.isEmpty()) return

        // Run diagnostic directly on the newly selected path
        val diagnostic = DataFilesDiagnostic.check(this, cleanPath)
        lastDiagnosticResult = diagnostic

        if (diagnostic.isValid) {
            val inst = GameInstaller(cleanPath)
            inst.setNomedia()
            inst.convertIni(prefs.getString("pref_encoding", GameInstaller.DEFAULT_CHARSET_PREF) ?: GameInstaller.DEFAULT_CHARSET_PREF)

            with(prefs.edit()) {
                putString("game_files", cleanPath)
                apply()
            }

            Toast.makeText(
                this,
                "Game data verified and configured!\nData: ${diagnostic.resolvedDataPath}\n(${diagnostic.esmFiles.size} ESMs, ${diagnostic.bsaFiles.size} BSAs)",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Log.w(TAG, "setupData: path validation failed with status ${diagnostic.status}")
            // Still save path so user doesn't lose their place, but show rich diagnostic error
            with(prefs.edit()) {
                putString("game_files", cleanPath)
                apply()
            }

            DataFilesDiagnostic.showDiagnosticDialog(this, diagnostic) {
                selectGameData()
            }
        }

        runDiagnosticCheck(showDialogIfCorrupted = false)
    }

    private fun checkStartGame() {
        Log.d(TAG, "checkStartGame: running diagnostic validation before launch")

        // 1. Validate Morrowind Data Files
        val diagnosticResult = DataFilesDiagnostic.check(this)
        lastDiagnosticResult = diagnosticResult

        if (!diagnosticResult.isValid) {
            Log.e(TAG, "checkStartGame: DataFilesDiagnostic failed with status=${diagnosticResult.status}")
            launchInProgress = false
            setControlsEnabled(true)
            runDiagnosticCheck(showDialogIfCorrupted = false)

            DataFilesDiagnostic.showDiagnosticDialog(this, diagnosticResult) {
                selectGameData()
            }
            return
        }

        // 2. Validate Engine Native Payload (libopenmw.so and bundled assets)
        if (!utils.RuntimeValidator.isRuntimePayloadValid(this)) {
            Log.e(TAG, "checkStartGame: runtime payload validation failed")
            launchInProgress = false
            setControlsEnabled(true)

            val missingSummary = utils.RuntimeValidator.getMissingSummary(this)
            AlertDialog.Builder(this)
                .setTitle("Engine Files Missing")
                .setMessage("This APK is missing OpenMW VR engine components:\n\n$missingSummary\n\nThe game cannot launch without these native libraries and assets. Please build the native engine first using:\ncd buildscripts && ./build.sh --arch arm64\nthen rebuild the APK.")
                .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int -> }
                .show()
            return
        }

        Log.d(TAG, "checkStartGame: game data and engine payload are healthy and verified. Launching Morrowind VR!")
        startGame()
    }

    private fun startGame() {
        Log.d(TAG, "startGame: routing through VrEntryActivity for full prep")
        val intent = Intent(this, VrEntryActivity::class.java)
        intent.putExtra(VrEntryActivity.EXTRA_AUTO_START_GAME, true)
        startActivity(intent)
        finish()
    }
}

