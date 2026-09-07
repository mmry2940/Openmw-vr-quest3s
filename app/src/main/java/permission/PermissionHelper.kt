/*
    Copyright (C) 2016 sandstranger

    This file is part of OpenMW-Android.

    OpenMW-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenMW-Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenMW-Android.  If not, see <https://www.gnu.org/licenses/>.
*/

package permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    const val REQUEST_CODE_STORAGE = 23
    const val REQUEST_CODE_MANAGE_STORAGE = 1001

    /**
     * Legacy permission request method maintained for backward compatibility.
     */
    fun getWriteExternalStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 30) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        REQUEST_CODE_STORAGE
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= 30) {
            if (!hasAllFilesAccess()) {
                requestAllFilesAccess(activity)
            }
        }
    }

    /**
     * Checks if MANAGE_EXTERNAL_STORAGE (All files access) is granted.
     * Always returns true on pre-Android 11 where this permission didn't exist.
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Checks if standard read/write external storage permissions are granted.
     */
    fun hasLegacyStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    /**
     * Checks if all required storage permissions are granted for OpenMW
     * to locate, read, and write game assets and configs.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasAllFilesAccess()
        } else {
            hasLegacyStoragePermission(context)
        }
    }

    /**
     * Requests All Files Access on Android 11+ (API 30+) / Meta Quest.
     */
    fun requestAllFilesAccess(activity: Activity, requestCode: Int = REQUEST_CODE_MANAGE_STORAGE): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val uri = Uri.parse("package:${activity.packageName}")
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                activity.startActivityForResult(intent, requestCode)
                true
            } catch (_: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivityForResult(fallbackIntent, requestCode)
                    true
                } catch (_: Exception) {
                    openAppSettings(activity)
                    false
                }
            }
        }
        return false
    }

    /**
     * Requests runtime READ & WRITE storage permissions.
     */
    fun requestLegacyStoragePermission(activity: Activity, requestCode: Int = REQUEST_CODE_STORAGE) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            requestCode
        )
    }

    /**
     * Opens Application Details Settings screen in OS settings.
     */
    fun openAppSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
