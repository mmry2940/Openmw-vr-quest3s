package ui.activity

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val textView = TextView(this).apply {
            text = "OpenMW VR Engine Running..."
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 32, 32, 32)
        }
        setContentView(textView)
    }
}
