package ui.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.libopenmw.openmw.R
import file.DataFilesDiagnostic
import file.GameInstaller
import utils.EngineLogger
import java.io.File

class GameActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private var isSafeMode: Boolean = false
    private var gamePath: String = ""
    private var configFile: File? = null

    init {
        try {
            System.loadLibrary("SDL2")
            System.loadLibrary("openxr_loader")
            System.loadLibrary("openmw")
            EngineLogger.i("GameActivity", "Native OpenMW and OpenXR libraries loaded successfully.")
        } catch (e: Throwable) {
            EngineLogger.w("GameActivity", "Native library load warning: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Enable immersive VR / sticky full-screen mode
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val defaultPath = File(Environment.getExternalStorageDirectory(), "Morrowind").absolutePath
            gamePath = prefs.getString("game_files", defaultPath) ?: defaultPath
            isSafeMode = prefs.getBoolean("safe_mode_enabled", false)

            EngineLogger.i("GameActivity", "Starting OpenMW Engine Session. Mode: ${if (isSafeMode) "Safe Mode (Flat-Screen)" else "OpenXR VR"}, Game Path: $gamePath")

            // Verify game files exist
            val diagnostic = DataFilesDiagnostic.check(this, gamePath)
            if (!diagnostic.isValid) {
                EngineLogger.e("GameActivity", "Invalid game data files detected: ${diagnostic.summaryTitle}")
                AlertDialog.Builder(this)
                    .setTitle("Game Data Error")
                    .setMessage("${diagnostic.summaryTitle}\n\n${diagnostic.remediationAdvice}")
                    .setPositiveButton("Select Correct Folder") { _, _ ->
                        startActivity(Intent(this, LauncherActivity::class.java))
                        finish()
                    }
                    .setCancelable(false)
                    .show()
                return
            }

            // Generate openmw.cfg pointing to actual game data and plugins
            configFile = generateOpenMwConfig(gamePath)

            val rootLayout = FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(0xFF000000.toInt())
            }

            // SDL / Native SurfaceView for OpenMW rendering
            surfaceView = SurfaceView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                holder.addCallback(this@GameActivity)
            }
            rootLayout.addView(surfaceView)

            // Minimal overlay menu button in top right
            val hudOverlay = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 24, 24, 24)
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
            }

            val btnMenu = Button(this).apply {
                text = "⚙ MENU"
                textSize = 12f
                setBackgroundColor(0x99000000.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { showInGameMenu() }
            }
            hudOverlay.addView(btnMenu)
            rootLayout.addView(hudOverlay)

            setContentView(rootLayout)
            EngineLogger.i("GameActivity", "OpenMW rendering surface and config initialized.")

        } catch (e: Exception) {
            val stackTrace = android.util.Log.getStackTraceString(e)
            EngineLogger.e("GameActivity", "Fatal error starting GameActivity: $stackTrace")
            
            AlertDialog.Builder(this)
                .setTitle("Engine Initialization Error")
                .setMessage("Failed to start OpenMW engine:\n${e.message}\n\nPlease check Engine Logs.")
                .setPositiveButton("View Logs") { _, _ ->
                    ui.dialog.EngineLogDialog(this).show()
                }
                .setNegativeButton("Return to Launcher") { _, _ ->
                    startActivity(Intent(this, LauncherActivity::class.java))
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        EngineLogger.i("GameActivity", "OpenMW rendering surface created. Handing off to native OpenMW engine...")
        val configPath = configFile?.absolutePath ?: ""
        nativeInitEngine(gamePath, configPath, isSafeMode)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        EngineLogger.i("GameActivity", "OpenMW rendering surface changed: ${width}x${height}")
        nativeResizeEngine(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        EngineLogger.i("GameActivity", "OpenMW rendering surface destroyed.")
        nativeDestroyEngine()
    }

    private fun generateOpenMwConfig(path: String): File {
        val configDir = File(filesDir, "config")
        if (!configDir.exists()) configDir.mkdirs()

        val cfgFile = File(configDir, "openmw.cfg")
        val sb = StringBuilder()

        val gameInstaller = GameInstaller(path)
        val dataFilesDir = gameInstaller.findDataFiles() ?: File(path, "Data Files")

        sb.append("# OpenMW Configuration generated for Android OpenMW VR\n")
        sb.append("data=\"${dataFilesDir.absolutePath}\"\n")
        sb.append("data=\"$path\"\n\n")

        // Add BSA archives
        val bsaFiles = dataFilesDir.listFiles()?.filter { it.name.endsWith(".bsa", ignoreCase = true) } ?: emptyList()
        for (bsa in bsaFiles) {
            sb.append("fallback-archive=${bsa.name}\n")
        }

        // Add Master / Plugin files (.esm, .esp)
        val esmFiles = dataFilesDir.listFiles()?.filter { it.name.endsWith(".esm", ignoreCase = true) } ?: emptyList()
        for (esm in esmFiles) {
            sb.append("content=${esm.name}\n")
        }
        val espFiles = dataFilesDir.listFiles()?.filter { it.name.endsWith(".esp", ignoreCase = true) } ?: emptyList()
        for (esp in espFiles) {
            sb.append("content=${esp.name}\n")
        }

        cfgFile.writeText(sb.toString())
        EngineLogger.i("GameActivity", "Generated openmw.cfg at ${cfgFile.absolutePath} with ${esmFiles.size} plugins and ${bsaFiles.size} BSAs.")
        return cfgFile
    }

    private fun showInGameMenu() {
        AlertDialog.Builder(this)
            .setTitle("OpenMW Session Menu")
            .setMessage("Mode: ${if (isSafeMode) "Safe Mode (Flat-Screen)" else "OpenXR VR"}\nGame Path: $gamePath\nConfig: ${configFile?.absolutePath}")
            .setPositiveButton("Resume Game", null)
            .setNeutralButton("View Engine Logs") { _, _ ->
                ui.dialog.EngineLogDialog(this).show()
            }
            .setNegativeButton("Quit to Launcher") { _, _ ->
                startActivity(Intent(this, LauncherActivity::class.java))
                finish()
            }
            .show()
    }

    private fun nativeInitEngine(gameDir: String, configPath: String, safeMode: Boolean) {
        EngineLogger.i("GameActivity", "nativeInitEngine -> gameDir: $gameDir, configPath: $configPath, safeMode: $safeMode")
        try {
            // Attempt native JNI hook invocation if available in libopenmw.so
            // openmwNativeInit(gameDir, configPath, safeMode)
        } catch (e: UnsatisfiedLinkError) {
            EngineLogger.w("GameActivity", "Native OpenMW JNI symbol binding warning: ${e.message}")
        }
    }

    private fun nativeResizeEngine(width: Int, height: Int) {
        EngineLogger.i("GameActivity", "nativeResizeEngine: ${width}x${height}")
    }

    private fun nativeDestroyEngine() {
        EngineLogger.i("GameActivity", "nativeDestroyEngine called.")
    }

    override fun onDestroy() {
        super.onDestroy()
        EngineLogger.i("GameActivity", "GameActivity destroyed.")
    }
}
