package com.example.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AlertLogEntity
import com.example.data.AppDatabase
import com.example.model.AlertType
import com.example.model.RecurrenceCalculator
import com.example.model.RecurrenceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processIntentSuspend(appContext, intent)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private val SYSTEM_RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )

        /**
         * Processes broadcast intents in a coroutine-safe manner.
         * Can also be invoked directly in Robolectric tests for deterministic verification.
         */
        suspend fun processIntentSuspend(
            context: Context,
            intent: Intent,
            nowMillis: Long = System.currentTimeMillis()
        ) {
            val action = intent.action ?: return

            if (action in SYSTEM_RESCHEDULE_ACTIONS) {
                AlarmScheduler.rescheduleAllAndRecoverMissed(context, nowMillis)
                return
            }

            val reminderId = intent.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, -1L)
            if (reminderId <= 0L) return

            val dao = AppDatabase.getInstance(context).reminderDao()
            val reminder = dao.getReminderById(reminderId) ?: return

            when (action) {
                NotificationHelper.ACTION_TRIGGER_REMINDER -> {
                    if (!reminder.isEnabled || reminder.isCompleted || reminder.isArchived) return

                    val isPreAlert = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_PRE_ALERT, false)
                    val preAlertOffset = intent.getIntExtra(NotificationHelper.EXTRA_PRE_ALERT_OFFSET, 0)
                    val alertTypeStr = intent.getStringExtra(NotificationHelper.EXTRA_ALERT_TYPE) ?: reminder.alertType
                    val triggerAlertType = runCatching { AlertType.valueOf(alertTypeStr) }
                        .getOrDefault(reminder.parsedAlertType)

                    if (!isPreAlert) {
                        // Duplicate guard: prevent firing the exact same main schedule within 3 seconds
                        val lastTrig = reminder.lastTriggeredMillis
                        if (lastTrig != null && (nowMillis - lastTrig) in 0..3_000L) {
                            return
                        }
                    }

                    // Log the alert execution
                    dao.insertAlertLog(
                        AlertLogEntity(
                            reminderId = reminder.id,
                            reminderTitle = reminder.title,
                            triggeredAtMillis = nowMillis,
                            alertType = triggerAlertType.name,
                            actionTaken = if (isPreAlert) "PRE_ALERT_TRIGGERED" else "TRIGGERED",
                            note = if (isPreAlert) "تنبيه مسبق قبل $preAlertOffset دقيقة" else "تنفيذ الموعد الأساسي"
                        )
                    )

                    // Execute Notification or Strong Alarm
                    if (triggerAlertType == AlertType.ALARM) {
                        AlarmPlaybackService.startAlarm(context, reminder.id)
                    } else {
                        NotificationHelper.showStandardReminderNotification(
                            context = context,
                            reminder = reminder,
                            isPreAlert = isPreAlert,
                            preAlertOffsetMinutes = preAlertOffset
                        )
                        SoundController.playNotificationSoundAndVibrate(
                            context = context,
                            soundOption = reminder.parsedSoundOption,
                            customUriString = reminder.customSoundUri,
                            vibrate = reminder.vibrationEnabled
                        )
                    }

                    // Update reminder state & schedule next recurrence if this was the main alert
                    if (!isPreAlert) {
                        if (reminder.parsedRecurrenceType != RecurrenceType.NONE) {
                            val nextOccurrence = RecurrenceCalculator.computeNextOccurrence(
                                baseScheduledMillis = reminder.originalTimeMillis,
                                afterTimeMillis = maxOf(nowMillis, reminder.originalTimeMillis),
                                recurrenceType = reminder.parsedRecurrenceType,
                                recurrenceDaysOfWeek = RecurrenceCalculator.parseDaysOfWeek(reminder.recurrenceDaysOfWeek),
                                recurrenceInterval = reminder.recurrenceInterval,
                                customUnit = reminder.parsedCustomUnit,
                                endDateMillis = reminder.recurrenceEndDateMillis
                            )
                            if (nextOccurrence != null) {
                                val updated = reminder.copy(
                                    scheduledTimeMillis = nextOccurrence,
                                    originalTimeMillis = nextOccurrence,
                                    snoozeUntilMillis = null,
                                    lastTriggeredMillis = nowMillis
                                )
                                dao.updateReminder(updated)
                                AlarmScheduler.scheduleReminder(context, updated, nowMillis)
                            } else {
                                val updated = reminder.copy(
                                    snoozeUntilMillis = null,
                                    lastTriggeredMillis = nowMillis
                                )
                                dao.updateReminder(updated)
                            }
                        } else {
                            val updated = reminder.copy(
                                snoozeUntilMillis = null,
                                lastTriggeredMillis = nowMillis
                            )
                            dao.updateReminder(updated)
                        }
                    }
                }

                NotificationHelper.ACTION_DISMISS_ALARM -> {
                    AlarmPlaybackService.stopAlarm(context)
                    NotificationHelper.cancelReminderNotifications(context, reminder.id)
                    dao.insertAlertLog(
                        AlertLogEntity(
                            reminderId = reminder.id,
                            reminderTitle = reminder.title,
                            triggeredAtMillis = nowMillis,
                            alertType = reminder.alertType,
                            actionTaken = "DISMISSED",
                            note = "تم إيقاف المنبّه بواسطة المستخدم"
                        )
                    )
                }

                NotificationHelper.ACTION_SNOOZE_ALARM -> {
                    val snoozeMinutes = intent.getIntExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, 10)
                        .coerceAtLeast(1)
                    AlarmPlaybackService.stopAlarm(context)
                    NotificationHelper.cancelReminderNotifications(context, reminder.id)

                    val snoozeTargetMillis = nowMillis + (snoozeMinutes * 60_000L)
                    // Notice: originalTimeMillis is untouched so recurring schedule stays intact!
                    val updated = reminder.copy(
                        snoozeUntilMillis = snoozeTargetMillis,
                        isCompleted = false,
                        isEnabled = true
                    )
                    dao.updateReminder(updated)
                    AlarmScheduler.scheduleReminder(context, updated, nowMillis)

                    dao.insertAlertLog(
                        AlertLogEntity(
                            reminderId = reminder.id,
                            reminderTitle = reminder.title,
                            triggeredAtMillis = nowMillis,
                            alertType = reminder.alertType,
                            actionTaken = "SNOOZED",
                            note = "تم تأجيل المنبّه لمدة $snoozeMinutes دقيقة"
                        )
                    )
                }

                NotificationHelper.ACTION_MARK_COMPLETED -> {
                    AlarmPlaybackService.stopAlarm(context)
                    NotificationHelper.cancelReminderNotifications(context, reminder.id)
                    if (reminder.parsedRecurrenceType == RecurrenceType.NONE) {
                        val updated = reminder.copy(
                            isCompleted = true,
                            snoozeUntilMillis = null
                        )
                        dao.updateReminder(updated)
                        AlarmScheduler.cancelReminderAlarms(context, reminder.id)
                    } else {
                        // For a recurring reminder, clear any active snooze and keep next occurrence scheduled
                        val updated = reminder.copy(snoozeUntilMillis = null)
                        dao.updateReminder(updated)
                        AlarmScheduler.scheduleReminder(context, updated, nowMillis)
                    }
                    dao.insertAlertLog(
                        AlertLogEntity(
                            reminderId = reminder.id,
                            reminderTitle = reminder.title,
                            triggeredAtMillis = nowMillis,
                            alertType = reminder.alertType,
                            actionTaken = "COMPLETED",
                            note = "تم تحديد المذكرة كمكتملة"
                        )
                    )
                }
            }
        }
    }
}
