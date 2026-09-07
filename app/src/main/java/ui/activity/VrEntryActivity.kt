package ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import utils.RuntimeValidator

private const val TAG = "VrEntryActivity"

class VrEntryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!RuntimeValidator.hasValidDataFiles(this)) {
            Log.e(TAG, "VrEntryActivity: game data files missing/invalid, redirecting to LauncherActivity")
            val launcherIntent = Intent(this, LauncherActivity::class.java)
            startActivity(launcherIntent)
            finish()
            return
        }

        val gameIntent = Intent(this, GameActivity::class.java)
        startActivity(gameIntent)
        finish()
    }
}
