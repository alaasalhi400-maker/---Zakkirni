package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.model.AlertType
import com.example.model.CustomIntervalUnit
import com.example.model.PreAlertSpec
import com.example.model.RecurrenceType
import com.example.model.SoundOption
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String = "",
    val longNote: String = "",
    val category: String = "المهام الشخصية",
    val colorHex: String = "#F59E0B",
    val scheduledTimeMillis: Long,
    val originalTimeMillis: Long = scheduledTimeMillis,
    val alertType: String = AlertType.NOTIFICATION.name,
    val recurrenceType: String = RecurrenceType.NONE.name,
    val recurrenceDaysOfWeek: String = "", // CSV of Calendar.SUNDAY..SATURDAY
    val recurrenceInterval: Int = 1,
    val customIntervalUnit: String = CustomIntervalUnit.DAYS.name,
    val recurrenceEndDateMillis: Long? = null,
    val preAlertsSerialized: String = "", // e.g., "30:NOTIFICATION;10:NOTIFICATION"
    val soundId: String = SoundOption.CLASSIC_ALARM.id,
    val customSoundUri: String? = null,
    val vibrationEnabled: Boolean = true,
    val isEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    val snoozeUntilMillis: Long? = null,
    val lastTriggeredMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val parsedAlertType: AlertType
        get() = runCatching { AlertType.valueOf(alertType) }.getOrDefault(AlertType.NOTIFICATION)

    val parsedRecurrenceType: RecurrenceType
        get() = runCatching { RecurrenceType.valueOf(recurrenceType) }.getOrDefault(RecurrenceType.NONE)

    val parsedCustomUnit: CustomIntervalUnit
        get() = runCatching { CustomIntervalUnit.valueOf(customIntervalUnit) }.getOrDefault(CustomIntervalUnit.DAYS)

    val parsedSoundOption: SoundOption
        get() = SoundOption.fromId(soundId)

    val preAlerts: List<PreAlertSpec>
        get() = PreAlertSpec.deserializeList(preAlertsSerialized)

    val effectiveTriggerTimeMillis: Long
        get() = snoozeUntilMillis ?: scheduledTimeMillis
}

@Entity(tableName = "alert_logs")
data class AlertLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val reminderTitle: String,
    val triggeredAtMillis: Long = System.currentTimeMillis(),
    val alertType: String,
    val actionTaken: String, // TRIGGERED, SNOOZED, DISMISSED, COMPLETED, MISSED_RECOVERED
    val note: String = ""
)

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#3B82F6"
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY scheduledTimeMillis ASC")
    fun observeAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY scheduledTimeMillis ASC")
    suspend fun getAllRemindersImmediate(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE isEnabled = 1 AND isCompleted = 0 AND isArchived = 0 ORDER BY scheduledTimeMillis ASC")
    suspend fun getActiveRemindersImmediate(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    fun observeReminderById(id: Long): Flow<ReminderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    // Alert logs
    @Query("SELECT * FROM alert_logs ORDER BY triggeredAtMillis DESC LIMIT 100")
    fun observeAlertLogs(): Flow<List<AlertLogEntity>>

    @Query("SELECT * FROM alert_logs ORDER BY triggeredAtMillis DESC")
    suspend fun getAllAlertLogsImmediate(): List<AlertLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlertLog(log: AlertLogEntity): Long

    @Query("DELETE FROM alert_logs")
    suspend fun clearAlertLogs()

    // Custom categories
    @Query("SELECT * FROM custom_categories ORDER BY id ASC")
    fun observeCustomCategories(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories ORDER BY id ASC")
    suspend fun getCustomCategoriesImmediate(): List<CustomCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomCategory(category: CustomCategoryEntity): Long

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteCustomCategoryById(id: Long)
}

@Database(
    entities = [ReminderEntity::class, AlertLogEntity::class, CustomCategoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zakkirni_database.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }

        fun setTestInstance(db: AppDatabase) {
            INSTANCE = db
        }
    }
}
