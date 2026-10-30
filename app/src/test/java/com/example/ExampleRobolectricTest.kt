package com.example

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.AlarmPlaybackService
import com.example.alarm.AlarmScheduler
import com.example.alarm.NotificationHelper
import com.example.alarm.ReminderBroadcastReceiver
import com.example.data.AppDatabase
import com.example.data.ReminderEntity
import com.example.data.ReminderRepository
import com.example.model.AlertType
import com.example.model.PreAlertSpec
import com.example.model.RecurrenceCalculator
import com.example.model.RecurrenceType
import com.example.model.SoundOption
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ReminderRepository
    private lateinit var alarmManager: AlarmManager
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setTestInstance(db)
        repository = ReminderRepository(context, db.reminderDao())
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        AlarmPlaybackService.stopAlarm(context)
        db.close()
    }

    @Test
    fun test01_and_02_createNotificationAndAlarmAfterOneMinute_andChangeAlertType() = runBlocking {
        val now = System.currentTimeMillis()
        val oneMinLater = now + 60_000L

        // 1. Create Notification reminder in 1 minute
        val notifId = repository.saveReminder(
            ReminderEntity(
                title = "مراجعة الرياضيات",
                details = "حل تمارين التفاضل",
                category = "الدراسة",
                scheduledTimeMillis = oneMinLater,
                alertType = AlertType.NOTIFICATION.name
            )
        )
        assertTrue(notifId > 0L)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

        // 2. Create Strong Alarm reminder in 1 minute
        val alarmId = repository.saveReminder(
            ReminderEntity(
                title = "موعد الطبيب",
                details = "العيادة الاستشارية",
                category = "المواعيد",
                scheduledTimeMillis = oneMinLater + 5_000L,
                alertType = AlertType.ALARM.name
            )
        )
        assertTrue(alarmId > 0L)
        assertEquals(2, shadowAlarmManager.scheduledAlarms.size)

        // 3. Change alert method from Notification to Strong Alarm without creating a new reminder
        val savedNotif = db.reminderDao().getReminderById(notifId)!!
        val toggled = repository.toggleReminderAlertType(savedNotif)
        assertEquals(AlertType.ALARM, toggled.parsedAlertType)
        assertEquals(notifId, toggled.id)
    }

    @Test
    fun test03_04_05_06_triggerNotificationAndAlarm_snoozeAndDismiss() = runBlocking {
        val now = System.currentTimeMillis()
        val originalSchedule = now + 60_000L

        val id = repository.saveReminder(
            ReminderEntity(
                title = "تسديد فاتورة الكهرباء",
                details = "قبل انتهاء المهلة",
                scheduledTimeMillis = originalSchedule,
                originalTimeMillis = originalSchedule,
                alertType = AlertType.ALARM.name,
                recurrenceType = RecurrenceType.DAILY.name,
                soundId = SoundOption.STRONG_SIREN.id,
                vibrationEnabled = true
            )
        )

        // Trigger the alarm via BroadcastReceiver (simulates background / lockscreen trigger)
        val triggerIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = NotificationHelper.ACTION_TRIGGER_REMINDER
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, id)
            putExtra(NotificationHelper.EXTRA_ALERT_TYPE, AlertType.ALARM.name)
        }
        ReminderBroadcastReceiver.processIntentSuspend(context, triggerIntent, originalSchedule)

        // Verify log recorded TRIGGERED and recurring next day was scheduled
        val logsAfterTrigger = db.reminderDao().getAllAlertLogsImmediate()
        assertTrue(logsAfterTrigger.any { it.reminderId == id && it.actionTaken == "TRIGGERED" })

        val updatedAfterTrigger = db.reminderDao().getReminderById(id)!!
        assertTrue(updatedAfterTrigger.scheduledTimeMillis > originalSchedule)

        // Test Smart Snooze (10 minutes): must schedule snooze without losing recurring reminder
        val snoozeNow = originalSchedule + 10_000L
        val snoozeIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = NotificationHelper.ACTION_SNOOZE_ALARM
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, id)
            putExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, 10)
        }
        ReminderBroadcastReceiver.processIntentSuspend(context, snoozeIntent, snoozeNow)

        val afterSnooze = db.reminderDao().getReminderById(id)!!
        assertEquals(snoozeNow + 10 * 60_000L, afterSnooze.snoozeUntilMillis)
        assertEquals(updatedAfterTrigger.originalTimeMillis, afterSnooze.originalTimeMillis)

        // Test Dismiss Alarm
        val dismissIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = NotificationHelper.ACTION_DISMISS_ALARM
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, id)
        }
        ReminderBroadcastReceiver.processIntentSuspend(context, dismissIntent, snoozeNow + 20_000L)
        val logsAfterDismiss = db.reminderDao().getAllAlertLogsImmediate()
        assertTrue(logsAfterDismiss.any { it.reminderId == id && it.actionTaken == "DISMISSED" })
        assertFalse( com.example.alarm.SoundController.isAlarmRinging.value )
    }

    @Test
    fun test07_recurringRemindersSpecificDaysOfWeek() {
        // Sunday (1), Tuesday (3), Thursday (5) at 08:30
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 1, 8, 30, 0) // Sunday Nov 1, 2026
            set(Calendar.MILLISECOND, 0)
        }
        val baseMillis = cal.timeInMillis
        val days = setOf(Calendar.SUNDAY, Calendar.TUESDAY, Calendar.THURSDAY)

        val nextOccurrence = RecurrenceCalculator.computeNextOccurrence(
            baseScheduledMillis = baseMillis,
            afterTimeMillis = baseMillis + 1_000L,
            recurrenceType = RecurrenceType.SPECIFIC_DAYS_OF_WEEK,
            recurrenceDaysOfWeek = days,
            recurrenceInterval = 1
        )
        assertNotNull(nextOccurrence)
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextOccurrence!! }
        assertEquals(Calendar.TUESDAY, nextCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, nextCal.get(Calendar.MINUTE))
    }

    @Test
    fun test08_09_10_multipleRemindersSameHour_editAndDeleteCancelsOldAlarm() = runBlocking {
        val now = System.currentTimeMillis()
        val preAlerts = PreAlertSpec.serializeList(
            listOf(PreAlertSpec(30, AlertType.NOTIFICATION))
        )

        val id1 = repository.saveReminder(
            ReminderEntity(
                title = "شرب الماء",
                scheduledTimeMillis = now + 45 * 60_000L,
                alertType = AlertType.NOTIFICATION.name,
                preAlertsSerialized = preAlerts
            )
        )
        val id2 = repository.saveReminder(
            ReminderEntity(
                title = "اجتماع الفريق",
                scheduledTimeMillis = now + 50 * 60_000L,
                alertType = AlertType.ALARM.name,
                preAlertsSerialized = preAlerts
            )
        )

        // 2 reminders * (1 main + 1 pre-alert) = 4 distinct scheduled alarms without collision
        assertEquals(4, shadowAlarmManager.scheduledAlarms.size)

        // Edit reminder 1 time -> old alarms replaced cleanly
        val r1 = db.reminderDao().getReminderById(id1)!!
        repository.saveReminder(
            r1.copy(
                scheduledTimeMillis = now + 90 * 60_000L,
                preAlertsSerialized = "" // removed pre-alert
            )
        )
        assertEquals(3, shadowAlarmManager.scheduledAlarms.size)

        // Delete reminder 2 -> its alarms are cancelled
        repository.deleteReminder(id2)
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        assertNull(db.reminderDao().getReminderById(id2))
    }

    @Test
    fun test11_12_bootCompletedRecoversMissed_andExactAlarmFallback() = runBlocking {
        val now = System.currentTimeMillis()
        // Insert a reminder whose time passed while phone was off
        val missedId = db.reminderDao().insertReminder(
            ReminderEntity(
                title = "موعد فات أثناء إغلاق الهاتف",
                scheduledTimeMillis = now - 120_000L,
                originalTimeMillis = now - 120_000L,
                alertType = AlertType.NOTIFICATION.name
            )
        )
        // Insert a future reminder
        val futureId = db.reminderDao().insertReminder(
            ReminderEntity(
                title = "موعد قادم بعد إعادة التشغيل",
                scheduledTimeMillis = now + 3_600_000L,
                originalTimeMillis = now + 3_600_000L,
                alertType = AlertType.ALARM.name
            )
        )

        // Simulate revoking exact alarm permission and rebooting phone
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(NotificationHelper.canScheduleExactAlarms(context))

        val bootIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = Intent.ACTION_BOOT_COMPLETED
        }
        ReminderBroadcastReceiver.processIntentSuspend(context, bootIntent, now)

        // Missed reminder was logged as MISSED_RECOVERED
        val logs = db.reminderDao().getAllAlertLogsImmediate()
        assertTrue(logs.any { it.reminderId == missedId && it.actionTaken == "MISSED_RECOVERED" })

        // Future reminder was still scheduled using fallback alarm API
        assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())
        assertNotNull(db.reminderDao().getReminderById(futureId))
    }

    @Test
    fun test15_backupExportAndImport() = runBlocking {
        repository.saveReminder(
            ReminderEntity(
                title = "مذكرة النسخ الاحتياطي",
                details = "اختبار التصدير والاستيراد",
                category = "العمل",
                scheduledTimeMillis = System.currentTimeMillis() + 120_000L,
                alertType = AlertType.ALARM.name
            )
        )
        val exportedJson = repository.exportBackupJson()
        assertTrue(exportedJson.contains("مذكرة النسخ الاحتياطي"))

        val importedCount = repository.importBackupJson(exportedJson)
        assertEquals(1, importedCount)
    }
}
