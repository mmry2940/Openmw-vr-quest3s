package file

import android.util.Log
import java.io.File

class GameInstaller(private val basePath: String) {

    companion object {
        const val DEFAULT_CHARSET_PREF = "win1252"
        private const val TAG = "GameInstaller"
    }

    fun check(): Boolean {
        if (basePath.isEmpty()) return false
        val dir = File(basePath)
        if (!dir.exists() || !dir.isDirectory) return false

        // Check directly in basePath or in Data Files subfolder
        val dataDir = findDataFiles() ?: return false
        val files = dataDir.listFiles() ?: return false
        return files.any { it.name.endsWith(".esm", ignoreCase = true) || it.name.endsWith(".omwgame", ignoreCase = true) }
    }

    fun findDataFiles(): File? {
        val root = File(basePath)
        if (!root.exists() || !root.isDirectory) return null

        val subData = File(root, "Data Files")
        if (subData.exists() && subData.isDirectory) {
            return subData
        }

        val directFiles = root.listFiles() ?: return null
        if (directFiles.any { it.name.endsWith(".esm", ignoreCase = true) }) {
            return root
        }

        return root
    }

    fun setNomedia() {
        try {
            val dataDir = findDataFiles() ?: return
            val noMedia = File(dataDir, ".nomedia")
            if (!noMedia.exists()) {
                noMedia.createNewFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set .nomedia", e)
        }
    }

    fun convertIni(charset: String) {
        Log.d(TAG, "convertIni called for charset $charset")
    }
}
