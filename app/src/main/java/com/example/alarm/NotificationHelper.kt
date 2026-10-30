package com.example.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ReminderEntity

object NotificationHelper {
    const val CHANNEL_NOTIFICATION_ID = "zakkirni_standard_notifications"
    const val CHANNEL_ALARM_ID = "zakkirni_strong_alarms"
    const val CHANNEL_MISSED_ID = "zakkirni_missed_alerts"

    const val ACTION_TRIGGER_REMINDER = "com.example.action.TRIGGER_REMINDER"
    const val ACTION_DISMISS_ALARM = "com.example.action.DISMISS_ALARM"
    const val ACTION_SNOOZE_ALARM = "com.example.action.SNOOZE_ALARM"
    const val ACTION_MARK_COMPLETED = "com.example.action.MARK_COMPLETED"

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_ALERT_TYPE = "extra_alert_type"
    const val EXTRA_IS_PRE_ALERT = "extra_is_pre_alert"
    const val EXTRA_PRE_ALERT_OFFSET = "extra_pre_alert_offset"
    const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    const val EXTRA_OPEN_REMINDER_ID = "extra_open_reminder_id"

    fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val standardChannel = NotificationChannel(
                CHANNEL_NOTIFICATION_ID,
                "إشعارات المذكرات",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "التنبيهات العادية للمذكرات والمواعيد في تطبيق ذكّرني"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                "المنبّهات القوية",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "رنين المنبّه القوي للمواعيد المهمة مع العرض على شاشة القفل"
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // SoundController manages continuous alarm audio
            }

            val missedChannel = NotificationChannel(
                CHANNEL_MISSED_ID,
                "المذكرات الفائتة",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيهات المواعيد التي حلت أثناء إيقاف تشغيل الهاتف"
            }

            nm.createNotificationChannels(listOf(standardChannel, alarmChannel, missedChannel))
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.canUseFullScreenIntent() ?: true
        } else {
            true
        }
    }

    fun showStandardReminderNotification(
        context: Context,
        reminder: ReminderEntity,
        isPreAlert: Boolean = false,
        preAlertOffsetMinutes: Int = 0,
        defaultSnoozeMinutes: Int = 10
    ) {
        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_REMINDER_ID, reminder.id)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            (reminder.id.toInt() * 100) + 50,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_MARK_COMPLETED
            putExtra(EXTRA_REMINDER_ID, reminder.id)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminder.id.toInt() * 100) + 51,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_SNOOZE_MINUTES, defaultSnoozeMinutes)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminder.id.toInt() * 100) + 52,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefix = if (isPreAlert && preAlertOffsetMinutes > 0) {
            "تنبيه مسبق (قبل $preAlertOffsetMinutes د): "
        } else ""

        val bodyText = buildString {
            if (reminder.details.isNotBlank()) append(reminder.details)
            if (reminder.longNote.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(reminder.longNote)
            }
            if (isEmpty()) append("حان موعد التذكير في تطبيق ذكّرني")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_NOTIFICATION_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("$prefix${reminder.title}")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "تحديد كمكتملة",
                completePendingIntent
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                "تأجيل $defaultSnoozeMinutes د",
                snoozePendingIntent
            )
            .build()

        val notificationId = (reminder.id.toInt() * 10) + if (isPreAlert) 2 else 1
        nm.notify(notificationId, notification)
    }

    fun buildAlarmForegroundNotification(
        context: Context,
        reminder: ReminderEntity,
        defaultSnoozeMinutes: Int = 10
    ): Notification {
        ensureChannelsCreated(context)

        val alarmActivityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REMINDER_ID, reminder.id)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            (reminder.id.toInt() * 100) + 60,
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra(EXTRA_REMINDER_ID, reminder.id)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (reminder.id.toInt() * 100) + 61,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_SNOOZE_MINUTES, defaultSnoozeMinutes)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminder.id.toInt() * 100) + 62,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = reminder.details.ifBlank { "منبّه قوي — اضغط للإيقاف أو التأجيل" }

        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ منبّه ذكّرني: ${reminder.title}")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.stop_alarm),
                dismissPendingIntent
            )
            .addAction(
                android.R.drawable.ic_popup_reminder,
                "تأجيل $defaultSnoozeMinutes د",
                snoozePendingIntent
            )

        if (canUseFullScreenIntent(context)) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        return builder.build()
    }

    fun showMissedReminderNotification(context: Context, reminder: ReminderEntity) {
        ensureChannelsCreated(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_REMINDER_ID, reminder.id)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            (reminder.id.toInt() * 100) + 70,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("مذكرة فائتة: ${reminder.title}")
            .setContentText("حلّ موعد هذه المذكرة أثناء إيقاف الهاتف. اضغط لعرض التفاصيل.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        nm.notify((reminder.id.toInt() * 10) + 9, notification)
    }

    fun cancelReminderNotifications(context: Context, reminderId: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel((reminderId.toInt() * 10) + 1)
        nm.cancel((reminderId.toInt() * 10) + 2)
        nm.cancel((reminderId.toInt() * 10) + 9)
    }
}
