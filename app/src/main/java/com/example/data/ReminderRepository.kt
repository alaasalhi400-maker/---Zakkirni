package com.example.data

import android.content.Context
import android.content.Intent
import com.example.alarm.AlarmScheduler
import com.example.alarm.NotificationHelper
import com.example.alarm.ReminderBroadcastReceiver
import com.example.model.AlertType
import com.example.model.CategorySpec
import com.example.model.DefaultCategories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject

class ReminderRepository(
    private val context: Context,
    private val dao: ReminderDao
) {
    private val appContext = context.applicationContext

    val allReminders: Flow<List<ReminderEntity>> = dao.observeAllReminders()
    val alertLogs: Flow<List<AlertLogEntity>> = dao.observeAlertLogs()

    val allCategories: Flow<List<CategorySpec>> = dao.observeCustomCategories().combine(
        kotlinx.coroutines.flow.flowOf(DefaultCategories.list)
    ) { customList, defaults ->
        defaults + customList.map {
            CategorySpec(
                nameAr = it.name,
                colorHex = it.colorHex,
                iconKey = "custom",
                isCustom = true
            )
        }
    }

    suspend fun saveReminder(reminder: ReminderEntity): Long {
        if (reminder.id > 0L) {
            // Cancel any previously scheduled alarms before updating
            AlarmScheduler.cancelReminderAlarms(appContext, reminder.id)
            dao.updateReminder(reminder)
            if (reminder.isEnabled && !reminder.isCompleted && !reminder.isArchived) {
                AlarmScheduler.scheduleReminder(appContext, reminder)
            }
            return reminder.id
        } else {
            val newId = dao.insertReminder(reminder)
            val saved = reminder.copy(id = newId)
            if (saved.isEnabled && !saved.isCompleted && !saved.isArchived) {
                AlarmScheduler.scheduleReminder(appContext, saved)
            }
            return newId
        }
    }

    /**
     * Allows changing the alert type (Notification <-> Strong Alarm) directly on an existing reminder
     * without recreating it, immediately rescheduling the Android AlarmManager entry.
     */
    suspend fun toggleReminderAlertType(reminder: ReminderEntity): ReminderEntity {
        val newType = if (reminder.parsedAlertType == AlertType.ALARM) {
            AlertType.NOTIFICATION
        } else {
            AlertType.ALARM
        }
        val updated = reminder.copy(alertType = newType.name)
        saveReminder(updated)
        return updated
    }

    suspend fun setReminderAlertType(reminder: ReminderEntity, newType: AlertType): ReminderEntity {
        val updated = reminder.copy(alertType = newType.name)
        saveReminder(updated)
        return updated
    }

    suspend fun setReminderEnabled(reminder: ReminderEntity, enabled: Boolean) {
        val updated = reminder.copy(
            isEnabled = enabled,
            snoozeUntilMillis = if (!enabled) null else reminder.snoozeUntilMillis
        )
        dao.updateReminder(updated)
        if (enabled && !updated.isCompleted && !updated.isArchived) {
            AlarmScheduler.scheduleReminder(appContext, updated)
        } else {
            AlarmScheduler.cancelReminderAlarms(appContext, reminder.id)
            NotificationHelper.cancelReminderNotifications(appContext, reminder.id)
        }
    }

    suspend fun setReminderCompleted(reminder: ReminderEntity, completed: Boolean) {
        val updated = reminder.copy(
            isCompleted = completed,
            snoozeUntilMillis = null
        )
        dao.updateReminder(updated)
        if (completed) {
            AlarmScheduler.cancelReminderAlarms(appContext, reminder.id)
            NotificationHelper.cancelReminderNotifications(appContext, reminder.id)
        } else if (updated.isEnabled && !updated.isArchived) {
            AlarmScheduler.scheduleReminder(appContext, updated)
        }
    }

    suspend fun setReminderArchived(reminder: ReminderEntity, archived: Boolean) {
        val updated = reminder.copy(
            isArchived = archived,
            snoozeUntilMillis = null
        )
        dao.updateReminder(updated)
        if (archived) {
            AlarmScheduler.cancelReminderAlarms(appContext, reminder.id)
            NotificationHelper.cancelReminderNotifications(appContext, reminder.id)
        } else if (updated.isEnabled && !updated.isCompleted) {
            AlarmScheduler.scheduleReminder(appContext, updated)
        }
    }

    suspend fun snoozeReminder(reminderId: Long, minutes: Int) {
        val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
            action = NotificationHelper.ACTION_SNOOZE_ALARM
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, minutes)
        }
        ReminderBroadcastReceiver.processIntentSuspend(appContext, intent)
    }

    suspend fun deleteReminder(reminderId: Long) {
        AlarmScheduler.cancelReminderAlarms(appContext, reminderId)
        NotificationHelper.cancelReminderNotifications(appContext, reminderId)
        dao.deleteReminderById(reminderId)
    }

    suspend fun triggerReminderNowForTest(reminder: ReminderEntity) {
        val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
            action = NotificationHelper.ACTION_TRIGGER_REMINDER
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminder.id)
            putExtra(NotificationHelper.EXTRA_ALERT_TYPE, reminder.alertType)
            putExtra(NotificationHelper.EXTRA_IS_PRE_ALERT, false)
        }
        ReminderBroadcastReceiver.processIntentSuspend(appContext, intent)
    }

    suspend fun addCustomCategory(name: String, colorHex: String) {
        if (name.isBlank()) return
        dao.insertCustomCategory(CustomCategoryEntity(name = name.trim(), colorHex = colorHex))
    }

    suspend fun clearLogs() {
        dao.clearAlertLogs()
    }

    suspend fun exportBackupJson(): String {
        val reminders = dao.getAllRemindersImmediate()
        val categories = dao.getCustomCategoriesImmediate()

        val root = JSONObject()
        root.put("app", "ZAKKIRNI")
        root.put("version", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())

        val remindersArray = JSONArray()
        for (r in reminders) {
            val obj = JSONObject().apply {
                put("title", r.title)
                put("details", r.details)
                put("longNote", r.longNote)
                put("category", r.category)
                put("colorHex", r.colorHex)
                put("scheduledTimeMillis", r.scheduledTimeMillis)
                put("originalTimeMillis", r.originalTimeMillis)
                put("alertType", r.alertType)
                put("recurrenceType", r.recurrenceType)
                put("recurrenceDaysOfWeek", r.recurrenceDaysOfWeek)
                put("recurrenceInterval", r.recurrenceInterval)
                put("customIntervalUnit", r.customIntervalUnit)
                if (r.recurrenceEndDateMillis != null) put("recurrenceEndDateMillis", r.recurrenceEndDateMillis)
                put("preAlertsSerialized", r.preAlertsSerialized)
                put("soundId", r.soundId)
                if (r.customSoundUri != null) put("customSoundUri", r.customSoundUri)
                put("vibrationEnabled", r.vibrationEnabled)
                put("isEnabled", r.isEnabled)
                put("isCompleted", r.isCompleted)
                put("isArchived", r.isArchived)
            }
            remindersArray.put(obj)
        }
        root.put("reminders", remindersArray)

        val categoriesArray = JSONArray()
        for (c in categories) {
            val obj = JSONObject().apply {
                put("name", c.name)
                put("colorHex", c.colorHex)
            }
            categoriesArray.put(obj)
        }
        root.put("customCategories", categoriesArray)

        return root.toString(2)
    }

    suspend fun importBackupJson(jsonString: String): Int {
        val root = JSONObject(jsonString)
        val categoriesArray = root.optJSONArray("customCategories")
        if (categoriesArray != null) {
            for (i in 0 until categoriesArray.length()) {
                val obj = categoriesArray.getJSONObject(i)
                dao.insertCustomCategory(
                    CustomCategoryEntity(
                        name = obj.optString("name", "مخصص"),
                        colorHex = obj.optString("colorHex", "#3B82F6")
                    )
                )
            }
        }

        val remindersArray = root.optJSONArray("reminders") ?: return 0
        var importedCount = 0
        for (i in 0 until remindersArray.length()) {
            val obj = remindersArray.getJSONObject(i)
            val scheduled = obj.optLong("scheduledTimeMillis", System.currentTimeMillis() + 3600_000L)
            val entity = ReminderEntity(
                title = obj.optString("title", "مذكرة مستوردة"),
                details = obj.optString("details", ""),
                longNote = obj.optString("longNote", ""),
                category = obj.optString("category", "المهام الشخصية"),
                colorHex = obj.optString("colorHex", "#F59E0B"),
                scheduledTimeMillis = scheduled,
                originalTimeMillis = obj.optLong("originalTimeMillis", scheduled),
                alertType = obj.optString("alertType", AlertType.NOTIFICATION.name),
                recurrenceType = obj.optString("recurrenceType", "NONE"),
                recurrenceDaysOfWeek = obj.optString("recurrenceDaysOfWeek", ""),
                recurrenceInterval = obj.optInt("recurrenceInterval", 1),
                customIntervalUnit = obj.optString("customIntervalUnit", "DAYS"),
                recurrenceEndDateMillis = if (obj.has("recurrenceEndDateMillis")) obj.optLong("recurrenceEndDateMillis") else null,
                preAlertsSerialized = obj.optString("preAlertsSerialized", ""),
                soundId = obj.optString("soundId", "CLASSIC_ALARM"),
                customSoundUri = if (obj.has("customSoundUri")) obj.optString("customSoundUri") else null,
                vibrationEnabled = obj.optBoolean("vibrationEnabled", true),
                isEnabled = obj.optBoolean("isEnabled", true),
                isCompleted = obj.optBoolean("isCompleted", false),
                isArchived = obj.optBoolean("isArchived", false)
            )
            saveReminder(entity)
            importedCount++
        }
        return importedCount
    }
}
