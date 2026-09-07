package ui.fragments

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.libopenmw.openmw.R
import permission.PermissionHelper
import java.io.File

class StoragePermissionFragment : Fragment() {

    private var onPermissionGrantedListener: (() -> Unit)? = null

    private lateinit var overallStatusCard: MaterialCardView
    private lateinit var overallStatusIcon: ImageView
    private lateinit var overallStatusTitle: TextView
    private lateinit var overallStatusDesc: TextView
    private lateinit var overallStatusBadge: TextView

    private lateinit var allFilesCard: MaterialCardView
    private lateinit var allFilesBadge: TextView
    private lateinit var btnGrantAllFiles: MaterialButton

    private lateinit var legacyStorageCard: MaterialCardView
    private lateinit var legacyStorageBadge: TextView
    private lateinit var btnGrantLegacyStorage: MaterialButton

    private lateinit var gameDataCheckBadge: TextView
    private lateinit var gameDataCheckPath: TextView
    private lateinit var gameDataCheckDetail: TextView

    private lateinit var btnRefreshStatus: MaterialButton
    private lateinit var btnContinue: MaterialButton

    private lateinit var prefs: SharedPreferences

    fun setOnPermissionGrantedListener(listener: () -> Unit) {
        this.onPermissionGrantedListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_storage_permission, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Setup Toolbar if present in container
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.permission_toolbar)
        toolbar?.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        // Overall status
        overallStatusCard = view.findViewById(R.id.card_overall_status)
        overallStatusIcon = view.findViewById(R.id.overall_status_icon)
        overallStatusTitle = view.findViewById(R.id.overall_status_title)
        overallStatusDesc = view.findViewById(R.id.overall_status_desc)
        overallStatusBadge = view.findViewById(R.id.overall_status_badge)

        // All Files Access
        allFilesCard = view.findViewById(R.id.card_all_files)
        allFilesBadge = view.findViewById(R.id.all_files_badge)
        btnGrantAllFiles = view.findViewById(R.id.btn_grant_all_files)

        // Legacy Storage
        legacyStorageCard = view.findViewById(R.id.card_legacy_storage)
        legacyStorageBadge = view.findViewById(R.id.legacy_storage_badge)
        btnGrantLegacyStorage = view.findViewById(R.id.btn_grant_legacy_storage)

        // Game Data Check
        gameDataCheckBadge = view.findViewById(R.id.game_data_check_badge)
        gameDataCheckPath = view.findViewById(R.id.game_data_check_path)
        gameDataCheckDetail = view.findViewById(R.id.game_data_check_detail)

        // Bottom action buttons
        btnRefreshStatus = view.findViewById(R.id.btn_refresh_status)
        btnContinue = view.findViewById(R.id.btn_continue)

        // Listeners
        btnGrantAllFiles.setOnClickListener {
            val act = activity ?: return@setOnClickListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val requested = PermissionHelper.requestAllFilesAccess(act)
                if (!requested) {
                    Toast.makeText(requireContext(), "Opening application settings...", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "All Files Access is only applicable to Android 11+", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantLegacyStorage.setOnClickListener {
            val act = activity ?: return@setOnClickListener
            PermissionHelper.requestLegacyStoragePermission(act)
        }

        btnRefreshStatus.setOnClickListener {
            updatePermissionsUI()
            Toast.makeText(requireContext(), "Storage permissions refreshed", Toast.LENGTH_SHORT).show()
        }

        btnContinue.setOnClickListener {
            if (PermissionHelper.hasStoragePermission(requireContext())) {
                onPermissionGrantedListener?.invoke() ?: activity?.finish()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Please grant required storage permissions to continue.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        updatePermissionsUI()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsUI()
    }

    fun updatePermissionsUI() {
        val context = context ?: return

        val hasAllFiles = PermissionHelper.hasAllFilesAccess()
        val hasLegacyStorage = PermissionHelper.hasLegacyStoragePermission(context)
        val hasFullStorage = PermissionHelper.hasStoragePermission(context)

        val colorReady = ContextCompat.getColor(context, R.color.status_ready)
        val colorWarning = ContextCompat.getColor(context, R.color.status_warning)
        val colorSecondary = ContextCompat.getColor(context, R.color.text_secondary)
        val colorPrimary = ContextCompat.getColor(context, R.color.colorPrimary)

        // 1. All Files Access Status (Android 11+ / Meta Quest)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasAllFiles) {
                allFilesBadge.text = getString(R.string.perm_status_granted)
                allFilesBadge.setTextColor(colorReady)
                btnGrantAllFiles.isEnabled = false
                btnGrantAllFiles.text = "All Files Access Granted"
                btnGrantAllFiles.setIconResource(R.drawable.ic_check_circle_24)
                btnGrantAllFiles.setIconTintResource(R.color.status_ready)
            } else {
                allFilesBadge.text = getString(R.string.perm_status_not_granted)
                allFilesBadge.setTextColor(colorWarning)
                btnGrantAllFiles.isEnabled = true
                btnGrantAllFiles.text = getString(R.string.perm_action_grant)
                btnGrantAllFiles.setIconResource(R.drawable.ic_open_in_new_24)
                btnGrantAllFiles.setIconTintResource(R.color.text_primary)
            }
        } else {
            allFilesBadge.text = getString(R.string.perm_status_not_needed)
            allFilesBadge.setTextColor(colorSecondary)
            btnGrantAllFiles.isEnabled = false
            btnGrantAllFiles.text = "Not Required on Android < 11"
        }

        // 2. Legacy Read/Write Storage Status
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !hasAllFiles) {
            if (hasLegacyStorage) {
                legacyStorageBadge.text = getString(R.string.perm_status_granted)
                legacyStorageBadge.setTextColor(colorReady)
                btnGrantLegacyStorage.isEnabled = false
                btnGrantLegacyStorage.text = "Storage Permission Granted"
                btnGrantLegacyStorage.setIconResource(R.drawable.ic_check_circle_24)
                btnGrantLegacyStorage.setIconTintResource(R.color.status_ready)
            } else {
                legacyStorageBadge.text = getString(R.string.perm_status_not_granted)
                legacyStorageBadge.setTextColor(colorWarning)
                btnGrantLegacyStorage.isEnabled = true
                btnGrantLegacyStorage.text = getString(R.string.perm_action_grant)
                btnGrantLegacyStorage.setIconResource(R.drawable.ic_shield_check_24)
                btnGrantLegacyStorage.setIconTintResource(R.color.text_primary)
            }
        } else {
            // Superseded by All Files Access on Android 11+
            legacyStorageBadge.text = getString(R.string.perm_status_granted)
            legacyStorageBadge.setTextColor(colorReady)
            btnGrantLegacyStorage.isEnabled = false
            btnGrantLegacyStorage.text = "Covered by All Files Access"
            btnGrantLegacyStorage.setIconResource(R.drawable.ic_check_circle_24)
            btnGrantLegacyStorage.setIconTintResource(R.color.status_ready)
        }

        // 3. Game Data Directory Status Check
        val configuredPath = prefs.getString("game_files", "") ?: ""
        if (configuredPath.isEmpty()) {
            gameDataCheckPath.text = getString(R.string.perm_game_data_not_set)
            gameDataCheckBadge.text = "Not Configured"
            gameDataCheckBadge.setTextColor(colorSecondary)
            gameDataCheckDetail.text = "Once storage access is granted, choose your Morrowind directory in the launcher."
        } else {
            gameDataCheckPath.text = configuredPath
            val dir = File(configuredPath)
            if (dir.exists() && dir.canRead()) {
                val dataFiles = File(dir, "Data Files")
                val hasDataFiles = dataFiles.exists() && dataFiles.canRead()
                val hasMorrowindEsm = File(dir, "Morrowind.esm").exists() || File(dataFiles, "Morrowind.esm").exists()

                gameDataCheckBadge.text = "Accessible"
                gameDataCheckBadge.setTextColor(colorReady)
                gameDataCheckDetail.text = when {
                    hasMorrowindEsm -> "Valid Morrowind installation found (Morrowind.esm verified)."
                    hasDataFiles -> "Data Files directory accessible."
                    else -> "Folder readable, OpenMW ready."
                }
            } else {
                gameDataCheckBadge.text = "Inaccessible"
                gameDataCheckBadge.setTextColor(colorWarning)
                gameDataCheckDetail.text = "Folder cannot be opened. Ensure storage permission is enabled."
            }
        }

        // 4. Overall Status Card
        if (hasFullStorage) {
            overallStatusIcon.setImageResource(R.drawable.ic_check_circle_24)
            overallStatusIcon.setColorFilter(colorReady)
            overallStatusTitle.text = "Storage Access Ready"
            overallStatusDesc.text = "OpenMW has full access to locate and load Morrowind game files."
            overallStatusBadge.text = "Ready"
            overallStatusBadge.setTextColor(colorReady)
            btnContinue.isEnabled = true
            btnContinue.text = "Proceed to Launcher"
        } else {
            overallStatusIcon.setImageResource(R.drawable.ic_warning_amber_24)
            overallStatusIcon.setColorFilter(colorWarning)
            overallStatusTitle.text = "Storage Permission Required"
            overallStatusDesc.text = "OpenMW requires file access to read Morrowind assets and configs."
            overallStatusBadge.text = "Required"
            overallStatusBadge.setTextColor(colorWarning)
            btnContinue.text = "Grant Permissions First"
        }
    }

    companion object {
        fun newInstance(): StoragePermissionFragment {
            return StoragePermissionFragment()
        }
    }
}
