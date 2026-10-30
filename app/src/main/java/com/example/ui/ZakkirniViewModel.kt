package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmPlaybackService
import com.example.alarm.ActiveAlarmSession
import com.example.alarm.NotificationHelper
import com.example.alarm.SoundController
import com.example.data.AlertLogEntity
import com.example.data.AppDatabase
import com.example.data.ReminderEntity
import com.example.data.ReminderRepository
import com.example.data.UserPreferencesRepository
import com.example.data.UserSettings
import com.example.model.AlertType
import com.example.model.AppColorPalette
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.CategorySpec
import com.example.model.DefaultCategories
import com.example.model.RecurrenceType
import com.example.model.ReminderSection
import com.example.model.SoundOption
import com.example.model.TimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppDestination {
    HOME,
    CALENDAR,
    CATEGORIES,
    SETTINGS,
    ADD_EDIT_REMINDER,
    REMINDER_DETAILS
}

data class PermissionStatusState(
    val hasNotificationPermission: Boolean = true,
    val canScheduleExactAlarms: Boolean = true,
    val canUseFullScreenIntent: Boolean = true,
    val notificationPermissionDeniedBanner: Boolean = false
)

class ZakkirniViewModel(
    application: Application,
    private val repository: ReminderRepository,
    private val prefsRepository: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext

    val settings: StateFlow<UserSettings> = prefsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    val allReminders: StateFlow<List<ReminderEntity>> = repository.allReminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val alertLogs: StateFlow<List<AlertLogEntity>> = repository.alertLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val categories: StateFlow<List<CategorySpec>> = repository.allCategories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DefaultCategories.list
    )

    val activeAlarmSession: StateFlow<ActiveAlarmSession?> = AlarmPlaybackService.activeAlarm
    val previewingSoundId: StateFlow<String?> = SoundController.previewingSoundId

    private val _currentDestination = MutableStateFlow(AppDestination.HOME)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _selectedSection = MutableStateFlow(ReminderSection.TODAY)
    val selectedSection: StateFlow<ReminderSection> = _selectedSection.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _selectedReminderId = MutableStateFlow<Long?>(null)
    val selectedReminderId: StateFlow<Long?> = _selectedReminderId.asStateFlow()

    private val _editingReminder = MutableStateFlow<ReminderEntity?>(null)
    val editingReminder: StateFlow<ReminderEntity?> = _editingReminder.asStateFlow()

    private val _prefilledDateMillis = MutableStateFlow<Long?>(null)
    val prefilledDateMillis: StateFlow<Long?> = _prefilledDateMillis.asStateFlow()

    private val _selectedCalendarDayMillis = MutableStateFlow(System.currentTimeMillis())
    val selectedCalendarDayMillis: StateFlow<Long> = _selectedCalendarDayMillis.asStateFlow()

    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    private val _permissionStatus = MutableStateFlow(PermissionStatusState())
    val permissionStatus: StateFlow<PermissionStatusState> = _permissionStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        NotificationHelper.ensureChannelsCreated(appContext)
        refreshPermissionsState()

        // Lightweight 30-second UI clock tick to keep remaining time strings accurate without battery drain
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                _nowMillis.value = System.currentTimeMillis()
            }
        }
    }

    val filteredRemindersForHome: StateFlow<List<ReminderEntity>> = combine(
        allReminders,
        _selectedSection,
        _searchQuery,
        _selectedCategoryFilter,
        _nowMillis
    ) { reminders, section, query, categoryFilter, now ->
        val q = query.trim().lowercase()

        reminders.filter { r ->
            val matchesQuery = if (q.isEmpty()) {
                true
            } else {
                val dateText = TimeFormatter.formatFullDate(r.scheduledTimeMillis).lowercase()
                r.title.lowercase().contains(q) ||
                    r.details.lowercase().contains(q) ||
                    r.longNote.lowercase().contains(q) ||
                    r.category.lowercase().contains(q) ||
                    dateText.contains(q)
            }

            val matchesCategory = categoryFilter == null || r.category == categoryFilter

            val matchesSection = if (q.isNotEmpty()) {
                // When searching, search across all reminders unless archived
                true
            } else {
                when (section) {
                    ReminderSection.TODAY -> !r.isCompleted && !r.isArchived &&
                        (TimeFormatter.isSameDay(r.effectiveTriggerTimeMillis, now) || r.effectiveTriggerTimeMillis < now)
                    ReminderSection.TOMORROW -> !r.isCompleted && !r.isArchived &&
                        TimeFormatter.isTomorrow(r.effectiveTriggerTimeMillis, now)
                    ReminderSection.UPCOMING -> !r.isCompleted && !r.isArchived &&
                        r.effectiveTriggerTimeMillis > now
                    ReminderSection.RECURRING -> !r.isCompleted && !r.isArchived &&
                        r.parsedRecurrenceType != RecurrenceType.NONE
                    ReminderSection.COMPLETED -> r.isCompleted && !r.isArchived
                    ReminderSection.ARCHIVED -> r.isArchived
                }
            }

            matchesQuery && matchesCategory && matchesSection
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun refreshPermissionsState(notificationExplicitlyDenied: Boolean? = null) {
        val currentDenied = notificationExplicitlyDenied ?: _permissionStatus.value.notificationPermissionDeniedBanner
        val hasNotif = NotificationHelper.hasNotificationPermission(appContext)
        _permissionStatus.value = PermissionStatusState(
            hasNotificationPermission = hasNotif,
            canScheduleExactAlarms = NotificationHelper.canScheduleExactAlarms(appContext),
            canUseFullScreenIntent = NotificationHelper.canUseFullScreenIntent(appContext),
            notificationPermissionDeniedBanner = if (hasNotif) false else currentDenied
        )
    }

    fun navigateTo(destination: AppDestination) {
        SoundController.stopAll(appContext)
        _currentDestination.value = destination
    }

    fun navigateBackToHome() {
        SoundController.stopAll(appContext)
        _editingReminder.value = null
        _prefilledDateMillis.value = null
        _currentDestination.value = AppDestination.HOME
    }

    fun openAddReminder(prefillDateMillis: Long? = null) {
        _editingReminder.value = null
        _prefilledDateMillis.value = prefillDateMillis
        _currentDestination.value = AppDestination.ADD_EDIT_REMINDER
    }

    fun openEditReminder(reminder: ReminderEntity) {
        _editingReminder.value = reminder
        _prefilledDateMillis.value = null
        _currentDestination.value = AppDestination.ADD_EDIT_REMINDER
    }

    fun openReminderDetails(reminderId: Long) {
        _selectedReminderId.value = reminderId
        _currentDestination.value = AppDestination.REMINDER_DETAILS
    }

    fun selectSection(section: ReminderSection) {
        _selectedSection.value = section
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = if (_selectedCategoryFilter.value == category) null else category
    }

    fun selectCalendarDay(millis: Long) {
        _selectedCalendarDayMillis.value = millis
    }

    fun saveReminder(reminder: ReminderEntity, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val savedId = repository.saveReminder(reminder)
            _nowMillis.value = System.currentTimeMillis()
            val modeText = reminder.parsedAlertType.labelAr
            _statusMessage.value = "تم حفظ المذكرة «${reminder.title}» بنجاح ($modeText)"
            onSaved(savedId)
            navigateBackToHome()
        }
    }

    fun quickScheduleTestReminder(inOneMinute: Boolean, alertType: AlertType) {
        viewModelScope.launch {
            val triggerTime = System.currentTimeMillis() + if (inOneMinute) 60_000L else 5_000L
            val currentSettings = settings.value
            val entity = ReminderEntity(
                title = if (alertType == AlertType.ALARM) "منبّه تجريبي سريع" else "إشعار تجريبي سريع",
                details = "تمت جدولته للتحقق الفعلي من نظام التنبيهات والمنبّهات في الخلفية",
                category = "المواعيد",
                colorHex = if (alertType == AlertType.ALARM) "#EF4444" else "#F59E0B",
                scheduledTimeMillis = triggerTime,
                originalTimeMillis = triggerTime,
                alertType = alertType.name,
                soundId = currentSettings.defaultSound.id,
                vibrationEnabled = currentSettings.defaultVibration
            )
            repository.saveReminder(entity)
            _selectedSection.value = ReminderSection.TODAY
            _statusMessage.value = if (inOneMinute) {
                "تمت جدولة ${alertType.labelAr} بعد دقيقة واحدة تماماً"
            } else {
                "تمت جدولة ${alertType.labelAr} بعد 5 ثوانٍ"
            }
        }
    }

    fun toggleAlertType(reminder: ReminderEntity) {
        viewModelScope.launch {
            val updated = repository.toggleReminderAlertType(reminder)
            _statusMessage.value = "تم تغيير طريقة التنبيه إلى: ${updated.parsedAlertType.labelAr}"
        }
    }

    fun setAlertType(reminder: ReminderEntity, newType: AlertType) {
        viewModelScope.launch {
            val updated = repository.setReminderAlertType(reminder, newType)
            _statusMessage.value = "تم تحديث طريقة التنبيه إلى: ${updated.parsedAlertType.labelAr}"
        }
    }

    fun toggleReminderEnabled(reminder: ReminderEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.setReminderEnabled(reminder, enabled)
            _statusMessage.value = if (enabled) "تم تفعيل المذكرة وإعادة جدولتها" else "تم تعطيل المذكرة مؤقتاً وإلغاء التنبيه"
        }
    }

    fun toggleReminderCompleted(reminder: ReminderEntity, completed: Boolean) {
        viewModelScope.launch {
            repository.setReminderCompleted(reminder, completed)
            _statusMessage.value = if (completed) "تم تحديد المذكرة كمكتملة ✓" else "تمت إعادة تفعيل المذكرة"
        }
    }

    fun toggleReminderArchived(reminder: ReminderEntity, archived: Boolean) {
        viewModelScope.launch {
            repository.setReminderArchived(reminder, archived)
            _statusMessage.value = if (archived) "تم نقل المذكرة إلى الأرشيف" else "تم استرجاع المذكرة من الأرشيف"
        }
    }

    fun snoozeReminder(reminderId: Long, minutes: Int) {
        viewModelScope.launch {
            repository.snoozeReminder(reminderId, minutes)
            _statusMessage.value = "تم تأجيل التذكير لمدة $minutes دقيقة"
        }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
            _statusMessage.value = "تم حذف المذكرة وإلغاء تنبيهاتها المجدولة"
            if (_currentDestination.value == AppDestination.REMINDER_DETAILS) {
                navigateBackToHome()
            }
        }
    }

    fun triggerReminderNow(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.triggerReminderNowForTest(reminder)
        }
    }

    fun addCustomCategory(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.addCustomCategory(name, colorHex)
            _statusMessage.value = "تمت إضافة التصنيف «$name»"
        }
    }

    fun clearAlertLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _statusMessage.value = "تم مسح سجل التنبيهات"
        }
    }

    fun previewSound(soundOption: SoundOption, customUri: String? = null, vibrate: Boolean = false) {
        SoundController.togglePreviewSound(appContext, soundOption, customUri, vibrate)
    }

    fun stopSoundPreview() {
        SoundController.stopAll(appContext)
    }

    fun dismissActiveAlarm() {
        AlarmPlaybackService.stopAlarm(appContext)
    }

    // Settings updates
    fun updateUserName(name: String) = viewModelScope.launch { prefsRepository.updateUserName(name) }
    fun updateLanguage(lang: AppLanguage) = viewModelScope.launch { prefsRepository.updateLanguage(lang) }
    fun updateThemeMode(mode: AppThemeMode) = viewModelScope.launch { prefsRepository.updateThemeMode(mode) }
    fun updateColorPalette(palette: AppColorPalette) = viewModelScope.launch { prefsRepository.updateColorPalette(palette) }
    fun updateIs24Hour(is24Hour: Boolean) = viewModelScope.launch { prefsRepository.updateIs24Hour(is24Hour) }
    fun updateDefaultSound(sound: SoundOption, customUri: String? = null) = viewModelScope.launch {
        prefsRepository.updateDefaultSound(sound, customUri)
    }
    fun updateDefaultVibration(enabled: Boolean) = viewModelScope.launch { prefsRepository.updateDefaultVibration(enabled) }
    fun updateDefaultSnoozeMinutes(minutes: Int) = viewModelScope.launch { prefsRepository.updateDefaultSnoozeMinutes(minutes) }
    fun updateDefaultAlertType(type: AlertType) = viewModelScope.launch { prefsRepository.updateDefaultAlertType(type) }

    fun exportBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportBackupJson()
            onResult(json)
        }
    }

    fun importBackup(json: String) {
        viewModelScope.launch {
            runCatching {
                val count = repository.importBackupJson(json)
                _statusMessage.value = "تم استيراد $count مذكرة بنجاح وجدولة مواعيدها"
            }.onFailure {
                _statusMessage.value = "تعذر استيراد النسخة الاحتياطية: تأكد من صحة ملف JSON"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getInstance(application)
            val repo = ReminderRepository(application, db.reminderDao())
            val prefs = UserPreferencesRepository(application)
            return ZakkirniViewModel(application, repo, prefs) as T
        }
    }
}
