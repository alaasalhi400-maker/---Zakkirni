package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.AlertLogEntity
import com.example.data.AppDatabase
import com.example.data.ReminderEntity
import com.example.model.AlertType
import com.example.model.RecurrenceCalculator
import com.example.model.RecurrenceType

object AlarmScheduler {
    private const val MAX_PRE_ALERTS = 15

    /**
     * Generates a unique, deterministic requestCode per (reminderId, slotIndex).
     * slotIndex = 0 is the main/snooze reminder; slotIndex = 1..15 are pre-alerts.
     */
    fun computeRequestCode(reminderId: Long, slotIndex: Int = 0): Int {
        return ((reminderId % 10_000_000L).toInt() * 100) + slotIndex
    }

    /**
     * Schedules the main reminder (or active snooze) and all future pre-alerts in Android AlarmManager.
     * Always cancels any existing PendingIntents for this reminder first so modified times never ring twice.
     */
    fun scheduleReminder(
        context: Context,
        reminder: ReminderEntity,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        cancelReminderAlarms(context, reminder.id)

        if (!reminder.isEnabled || reminder.isCompleted || reminder.isArchived) {
            return false
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        var scheduledAny = false

        // 1. Schedule pre-alerts (only if not currently in a snooze override)
        if (reminder.snoozeUntilMillis == null) {
            reminder.preAlerts.take(MAX_PRE_ALERTS).forEachIndexed { index, preAlert ->
                if (preAlert.offsetMinutes > 0) {
                    val triggerAt = reminder.scheduledTimeMillis - (preAlert.offsetMinutes * 60_000L)
                    if (triggerAt > nowMillis) {
                        val pi = buildTriggerPendingIntent(
                            context = context,
                            reminderId = reminder.id,
                            slotIndex = index + 1,
                            alertType = preAlert.alertType,
                            isPreAlert = true,
                            preAlertOffsetMinutes = preAlert.offsetMinutes
                        )
                        scheduleSingleAlarm(
                            context = context,
                            alarmManager = alarmManager,
                            triggerAtMillis = triggerAt,
                            alertType = preAlert.alertType,
                            reminderId = reminder.id,
                            operation = pi
                        )
                        scheduledAny = true
                    }
                }
            }
        }

        // 2. Schedule the primary target time (either snoozeUntilMillis or scheduledTimeMillis)
        val mainTriggerTime = reminder.effectiveTriggerTimeMillis
        if (mainTriggerTime > nowMillis - 5_000L) {
            val safeTriggerTime = maxOf(mainTriggerTime, nowMillis + 500L)
            val mainPi = buildTriggerPendingIntent(
                context = context,
                reminderId = reminder.id,
                slotIndex = 0,
                alertType = reminder.parsedAlertType,
                isPreAlert = false,
                preAlertOffsetMinutes = 0
            )
            scheduleSingleAlarm(
                context = context,
                alarmManager = alarmManager,
                triggerAtMillis = safeTriggerTime,
                alertType = reminder.parsedAlertType,
                reminderId = reminder.id,
                operation = mainPi
            )
            scheduledAny = true
        }

        return scheduledAny
    }

    private fun scheduleSingleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        alertType: AlertType,
        reminderId: Long,
        operation: PendingIntent
    ) {
        val canExact = NotificationHelper.canScheduleExactAlarms(context)

        try {
            if (canExact) {
                if (alertType == AlertType.ALARM) {
                    val showIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(NotificationHelper.EXTRA_OPEN_REMINDER_ID, reminderId)
                    }
                    val showPi = PendingIntent.getActivity(
                        context,
                        computeRequestCode(reminderId, 99),
                        showIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPi)
                    alarmManager.setAlarmClock(clockInfo, operation)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            operation
                        )
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                    }
                }
            } else {
                // Fallback when exact alarm permission is revoked by the user
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operation
                    )
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                }
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        }
    }

    fun cancelReminderAlarms(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (slot in 0..MAX_PRE_ALERTS) {
            val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                action = NotificationHelper.ACTION_TRIGGER_REMINDER
            }
            val pi = PendingIntent.getBroadcast(
                context,
                computeRequestCode(reminderId, slot),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    fun buildTriggerPendingIntent(
        context: Context,
        reminderId: Long,
        slotIndex: Int,
        alertType: AlertType,
        isPreAlert: Boolean,
        preAlertOffsetMinutes: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = NotificationHelper.ACTION_TRIGGER_REMINDER
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationHelper.EXTRA_ALERT_TYPE, alertType.name)
            putExtra(NotificationHelper.EXTRA_IS_PRE_ALERT, isPreAlert)
            putExtra(NotificationHelper.EXTRA_PRE_ALERT_OFFSET, preAlertOffsetMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            computeRequestCode(reminderId, slotIndex),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Called on BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED, and
     * SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.
     * Recovers missed reminders while phone was off and reschedules all upcoming/recurring reminders.
     */
    suspend fun rescheduleAllAndRecoverMissed(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val dao = AppDatabase.getInstance(context).reminderDao()
        val activeReminders = dao.getActiveRemindersImmediate()

        for (reminder in activeReminders) {
            val effectiveTime = reminder.effectiveTriggerTimeMillis
            if (effectiveTime <= nowMillis) {
                // This reminder's time passed while the device was off or clock was adjusted
                val alreadyTriggered = reminder.lastTriggeredMillis != null &&
                    reminder.lastTriggeredMillis >= effectiveTime

                if (!alreadyTriggered) {
                    NotificationHelper.showMissedReminderNotification(context, reminder)
                    dao.insertAlertLog(
                        AlertLogEntity(
                            reminderId = reminder.id,
                            reminderTitle = reminder.title,
                            triggeredAtMillis = nowMillis,
                            alertType = reminder.alertType,
                            actionTaken = "MISSED_RECOVERED",
                            note = "تم اكتشاف موعد فائت أثناء إيقاف الهاتف أو تعديل الوقت"
                        )
                    )
                }

                // If recurring, advance to next occurrence after nowMillis
                if (reminder.parsedRecurrenceType != RecurrenceType.NONE) {
                    val nextTime = RecurrenceCalculator.computeNextOccurrence(
                        baseScheduledMillis = reminder.originalTimeMillis,
                        afterTimeMillis = nowMillis,
                        recurrenceType = reminder.parsedRecurrenceType,
                        recurrenceDaysOfWeek = RecurrenceCalculator.parseDaysOfWeek(reminder.recurrenceDaysOfWeek),
                        recurrenceInterval = reminder.recurrenceInterval,
                        customUnit = reminder.parsedCustomUnit,
                        endDateMillis = reminder.recurrenceEndDateMillis
                    )
                    if (nextTime != null) {
                        val updated = reminder.copy(
                            scheduledTimeMillis = nextTime,
                            originalTimeMillis = nextTime,
                            snoozeUntilMillis = null,
                            lastTriggeredMillis = nowMillis
                        )
                        dao.updateReminder(updated)
                        scheduleReminder(context, updated, nowMillis)
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
            } else {
                // Future reminder: schedule it
                scheduleReminder(context, reminder, nowMillis)
            }
        }
    }
}
