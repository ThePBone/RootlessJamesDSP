package me.timschneeberger.rootlessjamesdsp.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import me.timschneeberger.rootlessjamesdsp.BuildConfig
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.interop.JamesDspLocalEngine
import me.timschneeberger.rootlessjamesdsp.interop.ProcessorMessageHandler
import me.timschneeberger.rootlessjamesdsp.model.preference.AudioEncoding
import me.timschneeberger.rootlessjamesdsp.model.room.AppBlocklistDatabase
import me.timschneeberger.rootlessjamesdsp.model.room.AppBlocklistRepository
import me.timschneeberger.rootlessjamesdsp.model.room.BlockedApp
import me.timschneeberger.rootlessjamesdsp.model.rootless.AudioSessionEntry
import me.timschneeberger.rootlessjamesdsp.model.rootless.MutedSessionEntry
import me.timschneeberger.rootlessjamesdsp.model.rootless.SessionRecordingPolicyEntry
import me.timschneeberger.rootlessjamesdsp.session.rootless.AudioSessionManager
import me.timschneeberger.rootlessjamesdsp.session.rootless.MutedSessionManager
import me.timschneeberger.rootlessjamesdsp.session.rootless.SessionRecordingPolicyManager
import me.timschneeberger.rootlessjamesdsp.utils.*
import me.timschneeberger.rootlessjamesdsp.utils.Constants.ACTION_PREFERENCES_UPDATED
import me.timschneeberger.rootlessjamesdsp.utils.Constants.ACTION_SAMPLE_RATE_UPDATED
import me.timschneeberger.rootlessjamesdsp.utils.Constants.ACTION_SERVICE_HARD_REBOOT_CORE
import me.timschneeberger.rootlessjamesdsp.utils.Constants.ACTION_SERVICE_RELOAD_LIVEPROG
import me.timschneeberger.rootlessjamesdsp.utils.Constants.ACTION_SERVICE_SOFT_REBOOT_CORE
import me.timschneeberger.rootlessjamesdsp.utils.Constants.CHANNEL_ID_APP_INCOMPATIBILITY
import me.timschneeberger.rootlessjamesdsp.utils.Constants.CHANNEL_ID_SERVICE
import me.timschneeberger.rootlessjamesdsp.utils.Constants.CHANNEL_ID_SESSION_LOSS
import me.timschneeberger.rootlessjamesdsp.utils.Constants.NOTIFICATION_ID_APP_INCOMPATIBILITY
import me.timschneeberger.rootlessjamesdsp.utils.Constants.NOTIFICATION_ID_PERMISSION_PROMPT
import me.timschneeberger.rootlessjamesdsp.utils.Constants.NOTIFICATION_ID_SERVICE
import me.timschneeberger.rootlessjamesdsp.utils.Constants.NOTIFICATION_ID_SESSION_LOSS
import me.timschneeberger.rootlessjamesdsp.utils.ContextExtensions.registerLocalReceiver
import me.timschneeberger.rootlessjamesdsp.utils.ContextExtensions.sendLocalBroadcast
import me.timschneeberger.rootlessjamesdsp.utils.ContextExtensions.unregisterLocalReceiver
import timber.log.Timber
import java.io.IOException


@RequiresApi(Build.VERSION_CODES.Q)
class RootlessAudioProcessorService : BaseAudioProcessorService() {
    // System services
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager

    // Media projection token
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionStartIntent: Intent? = null

    // Processing
    private var recreateRecorderRequested = false
    private var recorderThread: Thread? = null
    private lateinit var engine: JamesDspLocalEngine
    private val isRunning: Boolean
        get() = recorderThread != null

    // Session management
    private lateinit var audioSessionManager: AudioSessionManager
    private var sessionLossRetryCount = 0

    // Idle detection
    private var isProcessorIdle = false
    private var suspendOnIdle = false

    // Exclude restricted apps flag
    private var excludeRestrictedSessions = false

    // Termination flags
    private var isProcessorDisposing = false
    private var isServiceDisposing = false

    // Shared preferences
    private lateinit var sharedPreferences: SharedPreferences

    // Room databases
    private val applicationScope = CoroutineScope(SupervisorJob())
    private val blockedAppDatabase by lazy { AppBlocklistDatabase.getDatabase(this, applicationScope) }
    private val blockedAppRepository by lazy { AppBlocklistRepository(blockedAppDatabase.appBlocklistDao()) }
    private val blockedApps by lazy { blockedAppRepository.blocklist.asLiveData() }
    private val blockedAppObserver = Observer<List<BlockedApp>?> {
        Timber.d("blockedAppObserver: Database changed; ignored=${!isRunning}")
        if(isRunning)
            recreateRecorderRequested = true
    }

    override fun onCreate() {
        super.onCreate()

        // Get reference to system services
        audioManager = SystemServices.get(this, AudioManager::class.java)
        mediaProjectionManager = SystemServices.get(this, MediaProjectionManager::class.java)
        notificationManager = SystemServices.get(this, NotificationManager::class.java)

        // Setup session manager
        audioSessionManager = AudioSessionManager(this)
        audioSessionManager.sessionDatabase.setOnSessionLossListener(onSessionLossListener)
        audioSessionManager.sessionDatabase.setOnAppProblemListener(onAppProblemListener)
        audioSessionManager.sessionDatabase.registerOnSessionChangeListener(onSessionChangeListener)
        audioSessionManager.sessionPolicyDatabase.registerOnRestrictedSessionChangeListener(onSessionPolicyChangeListener)

        // Setup core engine
        engine = JamesDspLocalEngine(this, ProcessorMessageHandler())
        engine.syncWithPreferences()

        // Setup general-purpose broadcast receiver
        val filter = IntentFilter()
        filter.addAction(ACTION_PREFERENCES_UPDATED)
        filter.addAction(ACTION_SAMPLE_RATE_UPDATED)
        filter.addAction(ACTION_SERVICE_RELOAD_LIVEPROG)
        filter.addAction(ACTION_SERVICE_HARD_REBOOT_CORE)
        filter.addAction(ACTION_SERVICE_SOFT_REBOOT_CORE)
        registerLocalReceiver(broadcastReceiver, filter)

        // Setup shared preferences
        sharedPreferences = getSharedPreferences(Constants.PREF_APP, Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        loadFromPreferences(getString(R.string.key_powersave_suspend))
        loadFromPreferences(getString(R.string.key_session_exclude_restricted))

        // Setup database observer
        blockedApps.observeForever(blockedAppObserver)

        // Register notification channels
        val channel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_NONE
        )
        val channelSessionLoss = NotificationChannel(
            CHANNEL_ID_SESSION_LOSS,
            getString(R.string.notification_channel_session_loss_alert),
            NotificationManager.IMPORTANCE_HIGH
        )
        val channelAppCompatIssue = NotificationChannel(
            CHANNEL_ID_APP_INCOMPATIBILITY,
            getString(R.string.notification_channel_app_compat_alert),
            NotificationManager.IMPORTANCE_HIGH
        )

        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(channelSessionLoss)
        notificationManager.createNotificationChannel(channelAppCompatIssue)
        notificationManager.cancel(NOTIFICATION_ID_PERMISSION_PROMPT)

        // No need to recreate in this stage
        recreateRecorderRequested = false

        // Launch foreground service
        startForeground(
            NOTIFICATION_ID_SERVICE,
            ServiceNotificationHelper.createServiceNotification(this, null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        Timber.d("onStartCommand")

        // Handle intent action
        when (intent.action) {
            null -> {
                Timber.wtf("onStartCommand: intent.action is null")
            }
            ACTION_START -> {
                Timber.d("Starting service")
            }
            ACTION_STOP -> {
                Timber.d("Stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (isRunning) {
            return START_NOT_STICKY
        }

        // Cancel outdated notifications
        notificationManager.cancel(NOTIFICATION_ID_SESSION_LOSS)
        notificationManager.cancel(NOTIFICATION_ID_APP_INCOMPATIBILITY)

        // Setup media projection
        mediaProjectionStartIntent = intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA)

        mediaProjection = try {
            mediaProjectionManager.getMediaProjection(
                Activity.RESULT_OK,
                mediaProjectionStartIntent!!
            )
        }
        catch (ex: Exception) {
            Timber.e("Failed to acquire media projection")
            sendLocalBroadcast(Intent(Constants.ACTION_DISCARD_AUTHORIZATION))
            Timber.e(ex)
            null
        }

        mediaProjection?.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        if (mediaProjection != null) {
            startRecording()
        } else {
            Timber.w("Failed to capture audio")
            stopSelf()
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        isServiceDisposing = true

        // Stop recording and release engine
        stopRecording()
        engine.close()

        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Notify app about service termination
        sendLocalBroadcast(Intent(Constants.ACTION_SERVICE_STOPPED))

        // Unregister database observer
        blockedApps.removeObserver(blockedAppObserver)

        // Unregister receivers and release resources
        unregisterLocalReceiver(broadcastReceiver)
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection = null

        audioSessionManager.sessionPolicyDatabase.unregisterOnRestrictedSessionChangeListener(onSessionPolicyChangeListener)
        audioSessionManager.sessionDatabase.unregisterOnSessionChangeListener(onSessionChangeListener)
        audioSessionManager.destroy()

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferencesListener)
        notificationManager.cancel(NOTIFICATION_ID_SERVICE)

        stopSelf()
        super.onDestroy()
    }

    // Preferences listener
    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener {
            _, key ->
        loadFromPreferences(key)
    }

    // Projection termination callback
    private val projectionCallback = object: MediaProjection.Callback() {
        override fun onStop() {
            if(isServiceDisposing) {
                // Planned shutdown
                return
            }

            if(getSharedPreferences(Constants.PREF_VAR, Context.MODE_PRIVATE)
                    .getBoolean(getString(R.string.key_is_activity_active), false)) {
                // Activity in foreground, toast too disruptive
                return
            }

            Timber.w("Capture permission revoked. Stopping service.")

            sendLocalBroadcast(Intent(Constants.ACTION_DISCARD_AUTHORIZATION))

            Toast.makeText(this@RootlessAudioProcessorService,
                getString(R.string.capture_permission_revoked_toast), Toast.LENGTH_LONG).show()
            notificationManager.cancel(NOTIFICATION_ID_SERVICE)
            stopSelf()
        }
    }

    // General purpose broadcast receiver
    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SAMPLE_RATE_UPDATED -> engine.syncWithPreferences(arrayOf(Constants.PREF_CONVOLVER))
                ACTION_PREFERENCES_UPDATED -> engine.syncWithPreferences()
                ACTION_SERVICE_RELOAD_LIVEPROG -> engine.syncWithPreferences(arrayOf(Constants.PREF_LIVEPROG))
                ACTION_SERVICE_HARD_REBOOT_CORE -> restartRecording()
                ACTION_SERVICE_SOFT_REBOOT_CORE -> requestAudioRecordRecreation()
            }
        }
    }

    // Session loss listener
    private val onSessionLossListener = object: MutedSessionManager.OnSessionLossListener {
        override fun onSessionLost(sid: Int) {
            // Push notification if enabled
            val ignore = sharedPreferences.getBoolean(getString(R.string.key_session_loss_ignore), false)
            if(!ignore) {
                // Check if retry count exceeded
                if(sessionLossRetryCount < SESSION_LOSS_MAX_RETRIES) {
                    // Retry
                    sessionLossRetryCount++
                    Timber.d("Session lost. Retry count: $sessionLossRetryCount/$SESSION_LOSS_MAX_RETRIES")
                    audioSessionManager.pollOnce(false)
                    restartRecording()
                    return
                }
                else {
                    sessionLossRetryCount = 0
                    Timber.d("Giving up on saving session. User interaction required.")
                }

                // Request users attention
                notificationManager.cancel(NOTIFICATION_ID_SERVICE)
                ServiceNotificationHelper.pushSessionLossNotification(this@RootlessAudioProcessorService, mediaProjectionStartIntent)
                Toast.makeText(this@RootlessAudioProcessorService, getString(R.string.session_control_loss_toast), Toast.LENGTH_SHORT).show()
                Timber.w("Terminating service due to session loss")
                stopSelf()
            }
        }
    }

    // Session change listener
    private val onSessionChangeListener = object : MutedSessionManager.OnSessionChangeListener {
        override fun onSessionChanged(sessionList: HashMap<Int, MutedSessionEntry>) {
            isProcessorIdle = sessionList.size == 0
            Timber.d("onSessionChanged: isProcessorIdle=$isProcessorIdle")

            ServiceNotificationHelper.pushServiceNotification(
                this@RootlessAudioProcessorService,
                sessionList.map { it.value.audioSession }.toTypedArray()
            )
        }
    }

    // App problem listener
    private val onAppProblemListener = object : MutedSessionManager.OnAppProblemListener {
        override fun onAppProblemDetected(data: AudioSessionEntry) {
            // Push notification if enabled
            val ignore = sharedPreferences.getBoolean(getString(R.string.key_session_app_problem_ignore), false)
            if(!ignore) {
                // Request users attention
                notificationManager.cancel(NOTIFICATION_ID_SERVICE)

                // Determine if we should redirect instantly, or push a non-intrusive notification
                val prefsVar = this@RootlessAudioProcessorService.getSharedPreferences(Constants.PREF_VAR, Context.MODE_PRIVATE)
                if(prefsVar.getBoolean(getString(R.string.key_is_activity_active), false) ||
                    prefsVar.getBoolean(getString(R.string.key_is_app_compat_activity_active), false)) {
                    startActivity(
                        ServiceNotificationHelper.createAppTroubleshootIntent(
                            this@RootlessAudioProcessorService,
                            mediaProjectionStartIntent,
                            data,
                            directLaunch = true
                        )
                    )
                    notificationManager.cancel(NOTIFICATION_ID_APP_INCOMPATIBILITY)
                }
                else
                    ServiceNotificationHelper.pushAppIssueNotification(this@RootlessAudioProcessorService, mediaProjectionStartIntent, data)

                Toast.makeText(this@RootlessAudioProcessorService, getString(R.string.session_app_compat_toast), Toast.LENGTH_SHORT).show()
                Timber.w("Terminating service due to app incompatibility; redirect user to troubleshooting options")
                stopSelf()
            }
        }
    }

    // Session policy change listener
    private val onSessionPolicyChangeListener = object : SessionRecordingPolicyManager.OnSessionRecordingPolicyChangeListener {
        override fun onSessionRecordingPolicyChanged(sessionList: HashMap<String, SessionRecordingPolicyEntry>, isMinorUpdate: Boolean) {
            if(!this@RootlessAudioProcessorService.excludeRestrictedSessions) {
                Timber.d("onRestrictedSessionChanged: blocked; excludeRestrictedSessions disabled")
                return
            }

            if(!isMinorUpdate) {
                Timber.d("onRestrictedSessionChanged: major update detected; requesting soft-reboot")
                requestAudioRecordRecreation()
            }
            else {
                Timber.d("onRestrictedSessionChanged: minor update detected")
            }
        }
    }

    private fun loadFromPreferences(key: String){
        when (key) {
            getString(R.string.key_powersave_suspend) -> {
                suspendOnIdle = sharedPreferences.getBoolean(key, true)
                Timber.d("Suspend on idle set to $suspendOnIdle")
            }
            getString(R.string.key_session_exclude_restricted) -> {
                excludeRestrictedSessions = sharedPreferences.getBoolean(key, true)
                Timber.d("Exclude restricted set to $excludeRestrictedSessions")

                requestAudioRecordRecreation()
            }
        }
    }

    // Request recreation of the AudioRecord object to update AudioPlaybackRecordingConfiguration
    fun requestAudioRecordRecreation() {
        if(isProcessorDisposing || isServiceDisposing) {
            Timber.e("recreateAudioRecorder: service or processor already disposing")
            return
        }

        recreateRecorderRequested = true
    }

    // Start recording thread
    @SuppressLint("BinaryOperationInTimber")
    private fun startRecording() {
        // Sanity check
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Timber.e("Record audio permission missing. Can't record")
            stopSelf()
            return
        }

        // Load preferences
        val channelMask = sharedPreferences.getString(getString(R.string.key_audioformat_channels), AudioFormat.CHANNEL_OUT_STEREO.toString())
            ?.toIntOrNull() ?: AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioEncoding.fromInt(
            sharedPreferences.getString(getString(R.string.key_audioformat_encoding), "1")
                ?.toIntOrNull() ?: 1
        )
        val bufferSize = sharedPreferences.getFloat(getString(R.string.key_audioformat_buffersize), 2048f).toInt()
        val bufferSizeBytes = when (encoding) {
            AudioEncoding.PcmFloat -> bufferSize * Float.SIZE_BYTES
            else -> bufferSize * Short.SIZE_BYTES
        }
        val encodingFormat = when (encoding) {
            AudioEncoding.PcmShort -> AudioFormat.ENCODING_PCM_16BIT
            else -> AudioFormat.ENCODING_PCM_FLOAT
        }
        val sampleRate = determineSamplingRate()
        val defaultDevice = determineDefaultAudioDevice()

        Timber.i("========= Detected default device =========")
        Timber.i("Name: ${defaultDevice.productName}; Is sink?: ${defaultDevice.isSink}")
        for(i in 0 until defaultDevice.channelMasks.size) {
            Timber.i("[$i] Channel mask: ${defaultDevice.channelMasks[i]} (${defaultDevice.channelMasks[i].toString(2)}); " +
                    "Channel count: ${defaultDevice.channelCounts.getOrNull(i) ?: '?'}")
        }
        for(i in 0 until defaultDevice.encodings.size) {
            Timber.i("[$i] Encoding: ${AudioUtils.toLogFriendlyEncoding(defaultDevice.encodings[i])}")
        }
        for(i in 0 until defaultDevice.sampleRates.size) {
            Timber.i("[$i] Sample rate: ${defaultDevice.sampleRates[i]}")
        }
        Timber.i("========= ======================= =========")

        Timber.i("Using format:\nChannel mask id: $channelMask; Channel count: ${Integer.bitCount(channelMask)}; Sample rate: $sampleRate; \n" +
                "Encoding: ${encoding.name}; Buffer size: $bufferSize; Buffer size (bytes): $bufferSizeBytes ; " +
                "HAL buffer size (bytes): ${determineBufferSize()}")

        // Create recorder and track
        var recorder = buildAudioRecord(encodingFormat, sampleRate, bufferSizeBytes)
        val track = buildAudioTrack(channelMask, encodingFormat, sampleRate, bufferSizeBytes)

        if(engine.sampleRate.toInt() != sampleRate) {
            Timber.d("Sampling rate changed to ${sampleRate}Hz")
            engine.sampleRate = sampleRate.toFloat()
        }

        // TODO Move all audio-related code to C++
        recorderThread = Thread {
            try {
                ServiceNotificationHelper.pushServiceNotification(applicationContext, arrayOf())

                val floatBuffer = FloatArray(bufferSize)
                val shortBuffer = ShortArray(bufferSize)
                while (!isProcessorDisposing) {
                    if(recreateRecorderRequested) {
                        recreateRecorderRequested = false
                        Timber.d("Recreating recorder without stopping thread...")

                        // Suspend track, release recorder
                        recorder.stop()
                        track.stop()
                        recorder.release()


                        if (mediaProjection == null) {
                            Timber.e("Media projection handle is null, stopping service")
                            stopSelf()
                            return@Thread
                        }

                        // Recreate recorder with new AudioPlaybackRecordingConfiguration
                        recorder = buildAudioRecord(encodingFormat, sampleRate, bufferSizeBytes)
                        Timber.d("Recorder recreated")
                    }

                    // Suspend core while idle
                    if(isProcessorIdle && suspendOnIdle)
                    {
                        if(recorder.state == AudioRecord.STATE_INITIALIZED &&
                            recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                            recorder.stop()
                        if(track.state == AudioTrack.STATE_INITIALIZED &&
                            track.playState != AudioTrack.PLAYSTATE_STOPPED)
                            track.stop()

                        try {
                            Thread.sleep(50)
                        }
                        catch(e: InterruptedException) {
                            break
                        }
                        continue
                    }

                    // Resume recorder if suspended
                    if(recorder.recordingState == AudioRecord.RECORDSTATE_STOPPED) {
                        recorder.startRecording()
                    }
                    // Resume track if suspended
                    if(track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }

                    // Choose encoding and process data
                    if(encoding == AudioEncoding.PcmShort) {
                        recorder.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)
                        val output = engine.processInt16(shortBuffer)
                        track.write(output, 0, output.size, AudioTrack.WRITE_BLOCKING)
                    }
                    else {
                        recorder.read(floatBuffer, 0, floatBuffer.size, AudioRecord.READ_BLOCKING)
                        val output = engine.processFloat(floatBuffer)
                        track.write(output, 0, output.size, AudioTrack.WRITE_BLOCKING)
                    }
                }
            } catch (e: IOException) {
                Timber.w(e)
                // ignore
            } catch (e: Exception) {
                Timber.e("Exception in recorderThread raised")
                Timber.e(e)
                stopSelf()
            } finally {
                // Clean up recorder and track
                if(recorder.state != AudioRecord.STATE_UNINITIALIZED) {
                    recorder.stop()
                }
                if(track.state != AudioTrack.STATE_UNINITIALIZED) {
                    track.stop()
                }

                recorder.release()
                track.release()
            }
        }
        recorderThread!!.start()
    }

    // Terminate recording thread
    fun stopRecording() {
        if (recorderThread != null) {
            isProcessorDisposing = true
            recorderThread!!.interrupt()
            recorderThread!!.join(500)
            recorderThread = null
        }
    }

    // Hard restart recording thread
    fun restartRecording() {
        if(isProcessorDisposing || isServiceDisposing) {
            Timber.e("restartRecording: service or processor already disposing")
            return
        }

        stopRecording()
        isProcessorDisposing = false
        recreateRecorderRequested = false
        startRecording()
    }

    private fun buildAudioTrack(channelMask: Int, encoding: Int, sampleRate: Int, bufferSizeBytesPerChannel: Int): AudioTrack {
        val attributesBuilder = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_UNKNOWN)
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .setFlags(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            attributesBuilder.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
        }

        val format = AudioFormat.Builder()
            .setChannelMask(channelMask)
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .build()

        val frameSizeInBytes = format.frameSizeInBytes
        Timber.i("Using frame size (bytes): $frameSizeInBytes")

        /*val frameSizeInBytes: Int = if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
            Integer.bitCount(channelMask) /* channels */ * 2 /* bytes */
        } else {
            Integer.bitCount(channelMask) /* channels */ * 4 /* bytes */
        }*/

        val bufferSizeBytes = bufferSizeBytesPerChannel * Integer.bitCount(channelMask)


        var bufferSize = if (((bufferSizeBytes % frameSizeInBytes) != 0 || bufferSizeBytes < 1)) {
            Timber.e("Invalid audio buffer size $bufferSizeBytes")
            128 * (bufferSizeBytes / 128)
        }
        else bufferSizeBytes

        val minimumBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        if(bufferSize < minimumBufferSize) {
            Timber.w("Buffer size too small. Setting to minimum.")
            bufferSize = minimumBufferSize
        }

        Timber.d("Using buffer size $bufferSize")

        return AudioTrack.Builder()
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setAudioAttributes(attributesBuilder.build())
            .setBufferSizeInBytes(bufferSize)
            .build()
    }

    private fun buildAudioRecord(encoding: Int, sampleRate: Int, bufferSizeBytes: Int): AudioRecord {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Timber.e("buildAudioRecord: RECORD_AUDIO not granted")
            throw RuntimeException("RECORD_AUDIO not granted")
        }

        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val configBuilder = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)

        var excluded = if(excludeRestrictedSessions)
            audioSessionManager.sessionPolicyDatabase.getRestrictedUids().toList()
        else {
            audioSessionManager.pollOnce(false)
            emptyList()
        }

        blockedApps.value?.map { it.uid }?.let {
            excluded = concatenate(excluded, it)
        }

        excluded.forEach { configBuilder.excludeUid(it) }
        audioSessionManager.sessionDatabase.setExcludedUids(excluded.toTypedArray())
        audioSessionManager.pollOnce(false)

        Timber.d("buildAudioRecord: Excluded UIDs: ${excluded.joinToString("; ")}")

        return AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSizeBytes)
            .setAudioPlaybackCaptureConfig(configBuilder.build())
            .build()
    }

    // Determine HAL sampling rate
    private fun determineSamplingRate(): Int {
        val sampleRateStr: String? = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return sampleRateStr?.let { str -> Integer.parseInt(str).takeUnless { it == 0 } } ?: 48000
    }

    // Determine HAL buffer size
    private fun determineBufferSize(): Int {
        val framesPerBuffer: String? = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        return framesPerBuffer?.let { str -> Integer.parseInt(str).takeUnless { it == 0 } } ?: 256
    }

    // Determine default audio device using a dummy AudioTrack
    // https://developer.android.com/training/tv/playback/audio-capabilities#right-format
    private fun determineDefaultAudioDevice(): AudioDeviceInfo {
        MediaPlayer.create(this, R.raw.silence)
        val probeTrack = AudioTrack.Builder()
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))
            .build()

        var i = 0
        val bufferSize = 512
        val buffer = ByteArray(bufferSize)
        val inputStream = resources.openRawResource(R.raw.silence)
        try {
            while (inputStream.read().also { i = it } != -1) probeTrack.write(buffer, 0, i)
            inputStream.close()
        } catch (e: IOException) {
            Timber.e("Failed to play dummy file to determine default audio device")
        }
        probeTrack.play()

        return probeTrack.routedDevice.also { probeTrack.stop(); probeTrack.release() }
    }

    companion object {
        const val SESSION_LOSS_MAX_RETRIES = 1

        const val ACTION_START = BuildConfig.APPLICATION_ID + ".rootless.service.START"
        const val ACTION_STOP = BuildConfig.APPLICATION_ID + ".rootless.service.STOP"
        const val EXTRA_MEDIA_PROJECTION_DATA = "mediaProjectionData"
        const val EXTRA_APP_UID = "uid"
        const val EXTRA_APP_COMPAT_INTERNAL_CALL = "appCompatInternalCall"

        fun start(context: Context, data: Intent?) {
            context.startForegroundService(ServiceNotificationHelper.createStartIntent(context, data))
        }

        fun stop(context: Context) {
            context.startForegroundService(ServiceNotificationHelper.createStopIntent(context))
        }
    }
}
