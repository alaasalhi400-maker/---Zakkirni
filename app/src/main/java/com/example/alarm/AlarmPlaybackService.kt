package com.example.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.ReminderEntity
import com.example.model.AlertType
import com.example.model.SoundOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveAlarmSession(
    val reminderId: Long,
    val title: String,
    val details: String,
    val longNote: String,
    val category: String,
    val scheduledTimeMillis: Long,
    val soundOption: SoundOption,
    val vibrationEnabled: Boolean
)

class AlarmPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SERVICE) {
            stopAlarmAndSelf()
            return START_NOT_STICKY
        }

        val reminderId = intent?.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, -1L) ?: -1L
        if (reminderId <= 0L) {
            stopAlarmAndSelf()
            return START_NOT_STICKY
        }

        acquireSafetyWakeLock()

        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            val reminder = db.reminderDao().getReminderById(reminderId) ?: ReminderEntity(
                id = reminderId,
                title = "منبّه ذكّرني",
                details = "حان موعد المنبّه المحدد",
                scheduledTimeMillis = System.currentTimeMillis(),
                alertType = AlertType.ALARM.name
            )

            val notification = NotificationHelper.buildAlarmForegroundNotification(
                context = applicationContext,
                reminder = reminder,
                defaultSnoozeMinutes = 10
            )

            launch(Dispatchers.Main) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            FOREGROUND_NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {
                    // Fallback if foreground service start is restricted in test environment
                }

                _activeAlarm.value = ActiveAlarmSession(
                    reminderId = reminder.id,
                    title = reminder.title,
                    details = reminder.details,
                    longNote = reminder.longNote,
                    category = reminder.category,
                    scheduledTimeMillis = reminder.scheduledTimeMillis,
                    soundOption = reminder.parsedSoundOption,
                    vibrationEnabled = reminder.vibrationEnabled
                )

                SoundController.startAlarmRinging(
                    context = applicationContext,
                    soundOption = reminder.parsedSoundOption,
                    customUriString = reminder.customSoundUri,
                    vibrate = reminder.vibrationEnabled
                )

                // Launch full-screen AlarmRingingActivity
                runCatching {
                    val activityIntent = Intent(applicationContext, AlarmRingingActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                        putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminder.id)
                    }
                    startActivity(activityIntent)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireSafetyWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Zakkirni::AlarmWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun stopAlarmAndSelf() {
        _activeAlarm.value = null
        SoundController.stopAll(applicationContext)
        releaseWakeLock()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        _activeAlarm.value = null
        SoundController.stopAll(applicationContext)
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 9901
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_ALARM_SERVICE"

        private val _activeAlarm = MutableStateFlow<ActiveAlarmSession?>(null)
        val activeAlarm: StateFlow<ActiveAlarmSession?> = _activeAlarm.asStateFlow()

        fun startAlarm(context: Context, reminderId: Long) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, AlarmPlaybackService::class.java).apply {
                putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminderId)
            }
            try {
                ContextCompat.startForegroundService(appContext, intent)
            } catch (_: Exception) {
                runCatching { appContext.startService(intent) }
            }
        }

        fun stopAlarm(context: Context) {
            _activeAlarm.value = null
            SoundController.stopAll(context.applicationContext)
            val intent = Intent(context.applicationContext, AlarmPlaybackService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            runCatching { context.applicationContext.stopService(intent) }
        }
    }
}
