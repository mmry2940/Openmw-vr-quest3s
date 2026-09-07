package ui.activity

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import permission.PermissionHelper

class StoragePermissionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val tv = TextView(this).apply {
            setText("Storage Permission is needed to read Morrowind game files.")
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 24)
        }

        val btn = Button(this).apply {
            setText("Grant Permission")
            setOnClickListener {
                PermissionHelper.requestStoragePermission(this@StoragePermissionActivity)
                finish()
            }
        }

        layout.addView(tv)
        layout.addView(btn)
        setContentView(layout)
    }
}
