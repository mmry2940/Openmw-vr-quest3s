package ui.activity

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VrCalibrationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = "VR Calibration: Height 175cm, IPD 64.0mm, Stance: Standing"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 32, 32, 32)
        }
        setContentView(textView)
    }
}
