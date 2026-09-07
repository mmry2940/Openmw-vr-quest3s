package utils

import android.app.Application
import android.content.Context
import android.preference.PreferenceManager
import android.util.Log

class MyApp : Application() {
    companion object {
        private const val TAG = "MyApp"
        private lateinit var instance: MyApp

        fun getAppContext(): Context {
            return instance.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "MyApp initialized")
    }
}
