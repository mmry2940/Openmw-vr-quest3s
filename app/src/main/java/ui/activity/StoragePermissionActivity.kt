package ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.libopenmw.openmw.R
import permission.PermissionHelper
import ui.fragments.StoragePermissionFragment

class StoragePermissionActivity : AppCompatActivity() {

    private lateinit var permissionFragment: StoragePermissionFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)

        val autoContinue = intent.getBooleanExtra(EXTRA_AUTO_CONTINUE, false)
        if (autoContinue && PermissionHelper.hasStoragePermission(this)) {
            Log.d(TAG, "Storage permission already granted, continuing")
            finishWithSuccess()
            return
        }

        if (savedInstanceState == null) {
            permissionFragment = StoragePermissionFragment.newInstance()
            permissionFragment.setOnPermissionGrantedListener {
                finishWithSuccess()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, permissionFragment)
                .commit()
        } else {
            val existing = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (existing is StoragePermissionFragment) {
                permissionFragment = existing
                permissionFragment.setOnPermissionGrantedListener {
                    finishWithSuccess()
                }
            } else {
                permissionFragment = StoragePermissionFragment.newInstance()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::permissionFragment.isInitialized) {
            permissionFragment.updatePermissionsUI()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::permissionFragment.isInitialized) {
            permissionFragment.updatePermissionsUI()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::permissionFragment.isInitialized) {
            permissionFragment.updatePermissionsUI()
        }
    }

    private fun finishWithSuccess() {
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        private const val TAG = "StoragePermActivity"
        const val EXTRA_AUTO_CONTINUE = "extra_auto_continue"
    }
}
