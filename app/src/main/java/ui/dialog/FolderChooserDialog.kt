package ui.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.libopenmw.openmw.R
import file.DataFilesDiagnostic
import java.io.File

class FolderChooserDialog(
    private val context: Context,
    private val initialPath: String?,
    private val onFolderSelected: (String) -> Unit,
    private val onLaunchSafPicker: () -> Unit
) {

    private val dialog: Dialog = Dialog(context, R.style.MyTheme)
    private var currentDir: File

    private lateinit var txtCurrentPath: TextView
    private lateinit var btnFolderUp: ImageButton
    private lateinit var recyclerFolderList: RecyclerView
    private lateinit var btnSelectCurrentFolder: Button
    private lateinit var btnManualPath: Button
    private lateinit var btnSystemSafPicker: Button
    private lateinit var btnCancelPicker: Button
    private lateinit var containerQuickPaths: LinearLayout
    private lateinit var imgCurrentDirStatus: ImageView
    private lateinit var txtCurrentDirStatus: TextView

    private val folderAdapter = FolderAdapter()

    init {
        val rootPath = when {
            !initialPath.isNullOrBlank() && File(initialPath).exists() -> initialPath
            File(Environment.getExternalStorageDirectory(), "Morrowind").exists() ->
                File(Environment.getExternalStorageDirectory(), "Morrowind").absolutePath
            else -> Environment.getExternalStorageDirectory().absolutePath
        }
        currentDir = File(rootPath)

        setupDialog()
    }

    private fun setupDialog() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_folder_chooser, null)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        txtCurrentPath = view.findViewById(R.id.txt_current_path)
        btnFolderUp = view.findViewById(R.id.btn_folder_up)
        recyclerFolderList = view.findViewById(R.id.recycler_folder_list)
        btnSelectCurrentFolder = view.findViewById(R.id.btn_select_current_folder)
        btnManualPath = view.findViewById(R.id.btn_manual_path)
        btnSystemSafPicker = view.findViewById(R.id.btn_system_saf_picker)
        btnCancelPicker = view.findViewById(R.id.btn_cancel_picker)
        containerQuickPaths = view.findViewById(R.id.container_quick_paths)
        imgCurrentDirStatus = view.findViewById(R.id.img_current_dir_status)
        txtCurrentDirStatus = view.findViewById(R.id.txt_current_dir_status)

        recyclerFolderList.layoutManager = LinearLayoutManager(context)
        recyclerFolderList.adapter = folderAdapter

        btnFolderUp.setOnClickListener {
            val parent = currentDir.parentFile
            if (parent != null && parent.canRead()) {
                navigateTo(parent)
            }
        }

        btnSelectCurrentFolder.setOnClickListener {
            val selected = currentDir.absolutePath
            dialog.dismiss()
            onFolderSelected(selected)
        }

        btnManualPath.setOnClickListener {
            showManualPathDialog()
        }

        btnSystemSafPicker.setOnClickListener {
            dialog.dismiss()
            onLaunchSafPicker()
        }

        btnCancelPicker.setOnClickListener {
            dialog.dismiss()
        }

        setupQuickLocations()
        navigateTo(currentDir)
    }

    fun show() {
        dialog.show()
    }

    private fun setupQuickLocations() {
        containerQuickPaths.removeAllViews()
        val sdcard = Environment.getExternalStorageDirectory()

        val candidatePaths = listOf(
            File(sdcard, "Morrowind"),
            File(sdcard, "Morrowind/Data Files"),
            File(sdcard, "Download/Morrowind"),
            File(sdcard, "Download"),
            File(sdcard, "openmw"),
            File(sdcard, "games/Morrowind"),
            sdcard
        )

        for (candidate in candidatePaths) {
            val label = when (candidate.absolutePath) {
                sdcard.absolutePath -> "Storage Root"
                File(sdcard, "Morrowind").absolutePath -> "Morrowind"
                File(sdcard, "Morrowind/Data Files").absolutePath -> "Morrowind/Data Files"
                File(sdcard, "Download/Morrowind").absolutePath -> "Download/Morrowind"
                File(sdcard, "Download").absolutePath -> "Download"
                File(sdcard, "openmw").absolutePath -> "openmw"
                File(sdcard, "games/Morrowind").absolutePath -> "games/Morrowind"
                else -> candidate.name
            }

            val btn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                textSize = 11f
                setPadding(24, 8, 24, 8)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
                layoutParams = params
                isAllCaps = false
                setOnClickListener {
                    if (candidate.exists() && candidate.isDirectory) {
                        navigateTo(candidate)
                    } else {
                        // If doesn't exist yet, navigate to parent if possible
                        val parent = candidate.parentFile
                        if (parent != null && parent.exists()) {
                            navigateTo(parent)
                        } else {
                            navigateTo(sdcard)
                        }
                    }
                }
            }
            containerQuickPaths.addView(btn)
        }
    }

    private fun navigateTo(dir: File) {
        currentDir = dir
        txtCurrentPath.text = dir.absolutePath

        val parent = dir.parentFile
        btnFolderUp.isEnabled = parent != null && parent.canRead() && dir.absolutePath != "/"
        btnFolderUp.alpha = if (btnFolderUp.isEnabled) 1.0f else 0.4f

        // Check diagnostic status of current directory
        val diag = DataFilesDiagnostic.check(context, dir.absolutePath)
        if (diag.isValid) {
            imgCurrentDirStatus.setImageResource(R.drawable.ic_check_circle_24)
            imgCurrentDirStatus.setColorFilter(context.getColor(R.color.status_ready))
            txtCurrentDirStatus.text = "✓ Valid Morrowind installation found in this folder!"
            txtCurrentDirStatus.setTextColor(context.getColor(R.color.status_ready))
            btnSelectCurrentFolder.setBackgroundColor(context.getColor(R.color.gold_accent))
            btnSelectCurrentFolder.text = "SELECT THIS MORROWIND FOLDER"
        } else {
            imgCurrentDirStatus.setImageResource(R.drawable.ic_folder_open_24)
            imgCurrentDirStatus.setColorFilter(context.getColor(R.color.text_secondary))
            txtCurrentDirStatus.text = "Navigate to your Morrowind game folder (or tap Select Current Folder)."
            txtCurrentDirStatus.setTextColor(context.getColor(R.color.text_secondary))
            btnSelectCurrentFolder.setBackgroundColor(context.getColor(R.color.card_stroke))
            btnSelectCurrentFolder.text = "SELECT CURRENT FOLDER"
        }

        // List subdirectories
        val items = mutableListOf<FolderItem>()
        val files = try {
            dir.listFiles() ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

        val dirs = files.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }

        for (d in dirs) {
            val childCount = try {
                d.listFiles()?.size ?: 0
            } catch (_: Exception) {
                0
            }

            val hasMorrowind = try {
                val subFiles = d.listFiles() ?: emptyArray()
                subFiles.any {
                    it.name.equals("Morrowind.esm", ignoreCase = true) ||
                            (it.isDirectory && it.name.equals("Data Files", ignoreCase = true))
                }
            } catch (_: Exception) {
                false
            }

            items.add(
                FolderItem(
                    file = d,
                    name = d.name,
                    itemCount = childCount,
                    hasMorrowindData = hasMorrowind
                )
            )
        }

        folderAdapter.submitList(items)
    }

    private fun showManualPathDialog() {
        val input = android.widget.EditText(context).apply {
            setText(currentDir.absolutePath)
            setSelection(text.length)
        }

        AlertDialog.Builder(context)
            .setTitle("Enter Directory Path")
            .setView(input)
            .setPositiveButton("Navigate") { _, _ ->
                val enteredPath = input.text.toString().trim()
                if (enteredPath.isNotEmpty()) {
                    val target = File(enteredPath)
                    if (target.exists() && target.isDirectory) {
                        navigateTo(target)
                    } else {
                        // Navigate to parent or notify
                        target.mkdirs()
                        if (target.exists()) {
                            navigateTo(target)
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    data class FolderItem(
        val file: File,
        val name: String,
        val itemCount: Int,
        val hasMorrowindData: Boolean
    )

    inner class FolderAdapter : RecyclerView.Adapter<FolderViewHolder>() {
        private var list: List<FolderItem> = emptyList()

        fun submitList(newList: List<FolderItem>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_folder_entry, parent, false)
            return FolderViewHolder(v)
        }

        override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size
    }

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.folder_icon)
        private val name: TextView = itemView.findViewById(R.id.folder_name)
        private val details: TextView = itemView.findViewById(R.id.folder_details)
        private val badge: TextView = itemView.findViewById(R.id.folder_badge)

        fun bind(item: FolderItem) {
            name.text = item.name
            details.text = "${item.itemCount} items"

            if (item.hasMorrowindData) {
                badge.visibility = View.VISIBLE
                badge.text = "Morrowind files"
                icon.setColorFilter(context.getColor(R.color.gold_accent))
            } else {
                badge.visibility = View.GONE
                icon.setColorFilter(context.getColor(R.color.text_secondary))
            }

            itemView.setOnClickListener {
                navigateTo(item.file)
            }
        }
    }
}
