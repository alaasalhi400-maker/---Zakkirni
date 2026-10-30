package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.SoundOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SoundController {
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentVibrator: Vibrator? = null

    private val _previewingSoundId = MutableStateFlow<String?>(null)
    val previewingSoundId: StateFlow<String?> = _previewingSoundId.asStateFlow()

    private val _isAlarmRinging = MutableStateFlow(false)
    val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()

    @Synchronized
    fun startAlarmRinging(
        context: Context,
        soundOption: SoundOption,
        customUriString: String?,
        vibrate: Boolean
    ) {
        stopAll(context)
        _isAlarmRinging.value = true

        val appContext = context.applicationContext
        requestAudioFocus(appContext, isAlarm = true)

        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val player = createMediaPlayer(appContext, soundOption, customUriString, attrs, isLooping = true)
            if (player != null) {
                mediaPlayer = player
                player.start()
            }
        } catch (_: Exception) {
            // Safe fallback in test or headless audio environments
        }

        if (vibrate) {
            startVibration(appContext, continuous = true)
        }
    }

    @Synchronized
    fun playNotificationSoundAndVibrate(
        context: Context,
        soundOption: SoundOption,
        customUriString: String?,
        vibrate: Boolean
    ) {
        val appContext = context.applicationContext
        if (vibrate) {
            startVibration(appContext, continuous = false)
        }
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val player = createMediaPlayer(appContext, soundOption, customUriString, attrs, isLooping = false)
            player?.setOnCompletionListener { mp ->
                runCatching { mp.release() }
            }
            player?.start()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun togglePreviewSound(
        context: Context,
        soundOption: SoundOption,
        customUriString: String? = null,
        vibrate: Boolean = false
    ) {
        if (_previewingSoundId.value == soundOption.id) {
            stopAll(context)
            return
        }
        stopAll(context)
        _previewingSoundId.value = soundOption.id

        val appContext = context.applicationContext
        requestAudioFocus(appContext, isAlarm = true)

        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val player = createMediaPlayer(appContext, soundOption, customUriString, attrs, isLooping = false)
            if (player != null) {
                mediaPlayer = player
                player.setOnCompletionListener {
                    stopAll(appContext)
                }
                player.start()
            } else {
                _previewingSoundId.value = null
            }
        } catch (_: Exception) {
            _previewingSoundId.value = null
        }

        if (vibrate) {
            startVibration(appContext, continuous = false)
        }
    }

    @Synchronized
    fun stopAll(context: Context) {
        _previewingSoundId.value = null
        _isAlarmRinging.value = false

        runCatching {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        }
        mediaPlayer = null

        stopVibration()
        abandonAudioFocus(context.applicationContext)
    }

    private fun createMediaPlayer(
        context: Context,
        soundOption: SoundOption,
        customUriString: String?,
        audioAttributes: AudioAttributes,
        isLooping: Boolean
    ): MediaPlayer? {
        val player = MediaPlayer()
        player.setAudioAttributes(audioAttributes)
        player.isLooping = isLooping

        val configured = try {
            if (soundOption == SoundOption.CUSTOM_URI && !customUriString.isNullOrBlank()) {
                player.setDataSource(context, Uri.parse(customUriString))
                true
            } else {
                val resId = soundOption.rawResId ?: SoundOption.CLASSIC_ALARM.rawResId
                if (resId != null) {
                    val afd = context.resources.openRawResourceFd(resId)
                    if (afd != null) {
                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        true
                    } else false
                } else false
            }
        } catch (_: Exception) {
            false
        }

        if (!configured) {
            runCatching {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (fallbackUri != null) {
                    player.setDataSource(context, fallbackUri)
                }
            }
        }

        return runCatching {
            player.prepare()
            player
        }.getOrElse {
            runCatching { player.release() }
            null
        }
    }

    private fun startVibration(context: Context, continuous: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return
            currentVibrator = vibrator

            val pattern = if (continuous) {
                longArrayOf(0, 600, 400, 600, 400)
            } else {
                longArrayOf(0, 250, 150, 250)
            }
            val repeatIndex = if (continuous) 0 else -1

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeatIndex))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, repeatIndex)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopVibration() {
        runCatching {
            currentVibrator?.cancel()
        }
        currentVibrator = null
    }

    private fun requestAudioFocus(context: Context, isAlarm: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(if (isAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    if (isAlarm) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocus(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }
}
