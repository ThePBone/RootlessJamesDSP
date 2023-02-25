package me.timschneeberger.rootlessjamesdsp

import android.app.Application
import android.content.*
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import fr.bipi.tressence.file.FileLoggerTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import me.timschneeberger.rootlessjamesdsp.model.preference.ThemeMode
import me.timschneeberger.rootlessjamesdsp.model.room.AppBlocklistDatabase
import me.timschneeberger.rootlessjamesdsp.model.room.AppBlocklistRepository
import me.timschneeberger.rootlessjamesdsp.session.dump.DumpManager
import me.timschneeberger.rootlessjamesdsp.utils.Constants
import me.timschneeberger.rootlessjamesdsp.flavor.CrashlyticsImpl
import me.timschneeberger.rootlessjamesdsp.service.RootAudioProcessorService
import me.timschneeberger.rootlessjamesdsp.session.root.EffectSessionManager
import me.timschneeberger.rootlessjamesdsp.utils.ContextExtensions.registerLocalReceiver
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber
import timber.log.Timber.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class MainApplication : Application(), SharedPreferences.OnSharedPreferenceChangeListener {
    init {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    val prefs: SharedPreferences by lazy { getSharedPreferences(Constants.PREF_APP, Context.MODE_PRIVATE) }
    val rootSessionManager by lazy { EffectSessionManager(this) }
    val isLegacyMode
        get() = prefs.getBoolean(getString(R.string.key_audioformat_legacymode), true)

    val applicationScope = CoroutineScope(SupervisorJob())
    val blockedAppDatabase by lazy { AppBlocklistDatabase.getDatabase(this, applicationScope) }
    val blockedAppRepository by lazy { AppBlocklistRepository(blockedAppDatabase.appBlocklistDao()) }

    var engineSampleRate = 0f
        private set

    private val receiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Constants.ACTION_REPORT_SAMPLE_RATE) {
                    engineSampleRate = intent.getFloatExtra(Constants.EXTRA_SAMPLE_RATE, 0f)
                }
            }
        }
    }

    override fun onCreate() {
        Timber.plant(DebugTree())
        if(!BuildConfig.FOSS_ONLY)
            Timber.plant(CrashReportingTree())
        Timber.plant(FileLoggerTree.Builder()
            .withFileName("application.log")
            .withDirName(this.cacheDir.absolutePath)
            .withMinPriority(Log.VERBOSE)
            .withSizeLimit(2 * 1000000)
            .withFileLimit(1)
            .appendToFile(false)
            .build())
        Timber.i("====> Application starting up")

        // Clean up
        val dumpFile = File(filesDir, "dump.txt")
        if(dumpFile.exists()) {
            dumpFile.delete()
        }

        if(!BuildConfig.FOSS_ONLY) {
            // Soft-disable crashlytics in debug mode by default on each launch
            if (BuildConfig.DEBUG) {
                prefs
                    .edit()
                    .putBoolean(getString(R.string.key_share_crash_reports), false)
                    .apply()
            }

            val crashlytics = prefs.getBoolean(getString(R.string.key_share_crash_reports), true) && (!BuildConfig.DEBUG || BuildConfig.PREVIEW)
            Timber.d("Crashlytics enabled? $crashlytics")
            CrashlyticsImpl.setCollectionEnabled(crashlytics)

            CrashlyticsImpl.setCustomKey("buildType", BuildConfig.BUILD_TYPE)
            CrashlyticsImpl.setCustomKey("buildCommit", BuildConfig.COMMIT_SHA)
            CrashlyticsImpl.setCustomKey("flavor", BuildConfig.FLAVOR)
        }

        /**
         * Fix for existing users: reset mode selection to AudioService dump once.
         *
         * Reason: AudioPolicyService dumping (previously the default mode) cannot distinguish
         *         between input and output streams and causes false compat alerts when attaching
         *         to a input SID.
         */
        if(!prefs.getBoolean(getString(R.string.key_reset_proc_mode_fix_applied), false)) {
            Timber.i("Applying service dump mode fix once")

            prefs.edit()
                .putString(getString(R.string.key_session_detection_method), DumpManager.Method.AudioService.value.toString())
                .putBoolean(getString(R.string.key_reset_proc_mode_fix_applied), true)
                .apply()
        }

        val initialPrefList = arrayOf(
            R.string.key_appearance_theme_mode
        )
        for (pref in initialPrefList)
            this.onSharedPreferenceChanged(prefs, getString(pref))
        prefs.registerOnSharedPreferenceChangeListener(this)

        val appModule = module {
            single { DumpManager(androidContext()) }
        }

        startKoin {
            androidLogger()
            androidContext(this@MainApplication)
            modules(appModule)
        }

        registerLocalReceiver(receiver, IntentFilter(Constants.ACTION_REPORT_SAMPLE_RATE))

        if (!BuildConfig.ROOTLESS && isLegacyMode)
            RootAudioProcessorService.updateLegacyMode(applicationContext, true)

        super.onCreate()
    }

    override fun onTerminate() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        unregisterReceiver(receiver)
        super.onTerminate()
    }

    override fun onLowMemory() {
        Timber.w("onLowMemory: Running low on memory")
        CrashlyticsImpl.setCustomKey("last_low_memory_event", SimpleDateFormat("yyyyMMdd HHmmss z", Locale.US).format(Date()))
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        if (level >= 60)
            Timber.w("onTrimMemory: Memory trim at level $level requested")
        CrashlyticsImpl.setCustomKey("last_memory_trim_event", SimpleDateFormat("yyyyMMdd HHmmss z", Locale.US).format(Date()))
        CrashlyticsImpl.setCustomKey("last_memory_trim_level", level)
        super.onTrimMemory(level)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        sharedPreferences ?: return

        if(key == getString(R.string.key_appearance_theme_mode)) {
            AppCompatDelegate.setDefaultNightMode(
                when (ThemeMode.fromInt(sharedPreferences.getString(key, "0")?.toIntOrNull() ?: 0)) {
                    ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                    ThemeMode.FollowSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }

    /** A tree which logs important information for crash reporting.  */
    private class CrashReportingTree : DebugTree() {
        private fun priorityAsString(priority: Int): String {
            return when(priority){
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "?"
            }
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val safeTag = tag ?: "Unknown"
            CrashlyticsImpl.log("[${priorityAsString(priority)}] $safeTag: $message")

            if (t != null && (priority == Log.ERROR || priority == Log.WARN || priority == Log.ASSERT)) {
                CrashlyticsImpl.recordException(t)
            }
        }
    }
}