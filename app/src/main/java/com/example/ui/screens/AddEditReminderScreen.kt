package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReminderEntity
import com.example.data.UserSettings
import com.example.model.AlertType
import com.example.model.CategorySpec
import com.example.model.CustomIntervalUnit
import com.example.model.DefaultCategories
import com.example.model.PreAlertSpec
import com.example.model.RecurrenceCalculator
import com.example.model.RecurrenceType
import com.example.model.SoundOption
import com.example.model.TimeFormatter
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditReminderScreen(
    existingReminder: ReminderEntity?,
    prefilledDateMillis: Long?,
    settings: UserSettings,
    categories: List<CategorySpec>,
    previewingSoundId: String?,
    onSaveReminder: (ReminderEntity) -> Unit,
    onAddCustomCategory: (String, String) -> Unit,
    onPreviewSound: (SoundOption, String?, Boolean) -> Unit,
    onStopSoundPreview: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onStopSoundPreview()
        onBack()
    }

    val context = LocalContext.current
    val initialTime = remember(existingReminder, prefilledDateMillis) {
        existingReminder?.scheduledTimeMillis
            ?: prefilledDateMillis
            ?: (System.currentTimeMillis() + 30 * 60_000L) // Default: 30 mins from now
    }

    var title by remember { mutableStateOf(existingReminder?.title ?: "") }
    var titleError by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(existingReminder?.details ?: "") }
    var longNote by remember { mutableStateOf(existingReminder?.longNote ?: "") }
    var selectedCategory by remember {
        mutableStateOf(existingReminder?.category ?: categories.firstOrNull()?.nameAr ?: "المهام الشخصية")
    }
    var selectedColorHex by remember {
        mutableStateOf(existingReminder?.colorHex ?: DefaultCategories.cardColorPalette.first())
    }

    var scheduledMillis by remember { mutableLongStateOf(initialTime) }
    var use24HourInPicker by remember { mutableStateOf(settings.is24HourFormat) }
    var selectedAlertType by remember {
        mutableStateOf(existingReminder?.parsedAlertType ?: settings.defaultAlertType)
    }

    // Recurrence state
    var recurrenceType by remember {
        mutableStateOf(existingReminder?.parsedRecurrenceType ?: RecurrenceType.NONE)
    }
    val selectedDaysOfWeek = remember {
        val initialDays = RecurrenceCalculator.parseDaysOfWeek(existingReminder?.recurrenceDaysOfWeek ?: "")
        mutableStateListOf<Int>().apply {
            if (initialDays.isNotEmpty()) addAll(initialDays)
            else addAll(listOf(Calendar.SUNDAY, Calendar.TUESDAY, Calendar.THURSDAY))
        }
    }
    var recurrenceIntervalInput by remember {
        mutableStateOf((existingReminder?.recurrenceInterval ?: 2).toString())
    }
    var customIntervalUnit by remember {
        mutableStateOf(existingReminder?.parsedCustomUnit ?: CustomIntervalUnit.DAYS)
    }
    var recurrenceEndDateMillis by remember {
        mutableStateOf<Long?>(existingReminder?.recurrenceEndDateMillis)
    }

    // Pre-alerts list
    val preAlerts = remember {
        mutableStateListOf<PreAlertSpec>().apply {
            existingReminder?.preAlerts?.let { addAll(it) }
        }
    }
    var showAddPreAlertDialog by remember { mutableStateOf(false) }

    // Sound & Vibration state
    var selectedSound by remember {
        mutableStateOf(existingReminder?.parsedSoundOption ?: settings.defaultSound)
    }
    var customSoundUri by remember {
        mutableStateOf(existingReminder?.customSoundUri ?: settings.defaultCustomSoundUri)
    }
    var vibrationEnabled by remember {
        mutableStateOf(existingReminder?.vibrationEnabled ?: settings.defaultVibration)
    }

    var showPastTimeWarningDialog by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    // Audio file picker launcher for custom sound from device
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            customSoundUri = uri.toString()
            selectedSound = SoundOption.CUSTOM_URI
        }
    }

    val isPastTime = scheduledMillis < System.currentTimeMillis() - 30_000L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("add_edit_reminder_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        onStopSoundPreview()
                        onBack()
                    },
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
                Text(
                    text = if (existingReminder == null) "إضافة مذكرة جديدة" else "تعديل المذكرة",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    if (isPastTime) {
                        showPastTimeWarningDialog = true
                        return@Button
                    }
                    val entity = buildReminderEntity(
                        existing = existingReminder,
                        title = title,
                        details = details,
                        longNote = longNote,
                        category = selectedCategory,
                        colorHex = selectedColorHex,
                        scheduledMillis = scheduledMillis,
                        alertType = selectedAlertType,
                        recurrenceType = recurrenceType,
                        selectedDaysOfWeek = selectedDaysOfWeek,
                        recurrenceInterval = recurrenceIntervalInput.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        customUnit = customIntervalUnit,
                        endDateMillis = recurrenceEndDateMillis,
                        preAlerts = preAlerts,
                        soundOption = selectedSound,
                        customSoundUri = customSoundUri,
                        vibrationEnabled = vibrationEnabled
                    )
                    onStopSoundPreview()
                    onSaveReminder(entity)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("save_reminder_top_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("حفظ", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 1: Core Decision — "كيف تريد أن أذكّرك؟"
        SectionCard(title = "كيف تريد أن أذكّرك؟ (طريقة التنبيه)") {
            Text(
                text = "اختر طريقة التنبيه المستقلة لهذه المذكرة، ويمكنك تغييرها لاحقاً في أي وقت:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AlertModeOptionCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("select_mode_notification"),
                    title = "إشعار عادي",
                    subtitle = "تنبيه في الشريط مع صوت خفيف وتأجيل سريع",
                    icon = Icons.Default.Notifications,
                    selected = selectedAlertType == AlertType.NOTIFICATION,
                    accentColor = Color(0xFF38BDF8),
                    onClick = { selectedAlertType = AlertType.NOTIFICATION }
                )
                AlertModeOptionCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("select_mode_alarm"),
                    title = "منبّه قوي",
                    subtitle = "رنين متواصل وشاشة منبّه كاملة للمواعيد المهمة",
                    icon = Icons.Default.Alarm,
                    selected = selectedAlertType == AlertType.ALARM,
                    accentColor = Color(0xFFF59E0B),
                    onClick = { selectedAlertType = AlertType.ALARM }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 2: Basic Information (Title, Details, Category, Color, Long Note)
        SectionCard(title = "المعلومات الأساسية") {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                label = { Text("عنوان المذكرة (إجباري) *") },
                placeholder = { Text("مثلاً: مراجعة الرياضيات، موعد الطبيب، تسديد فاتورة...") },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("يرجى إدخال عنوان للمذكرة قبل الحفظ") }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_title_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("تفاصيل المذكرة (اختياري)") },
                placeholder = { Text("وصف مختصر يظهر في البطاقة والإشعار...") },
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_details_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = longNote,
                onValueChange = { longNote = it },
                label = { Text("ملاحظة طويلة أو نقاط إضافية (اختياري)") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_long_note_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "التصنيف:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.nameAr,
                        onClick = {
                            selectedCategory = cat.nameAr
                            selectedColorHex = cat.colorHex
                        },
                        label = { Text(cat.nameAr) }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { showNewCategoryDialog = true },
                    label = { Text("+ تصنيف خاص") },
                    modifier = Modifier.testTag("add_custom_category_chip")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "لون المذكرة:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DefaultCategories.cardColorPalette.forEach { hex ->
                    val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                        .getOrDefault(Color(0xFFF59E0B))
                    val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                            .clickable { selectedColorHex = hex }
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF090D16),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 3: Date & Time Selection
        SectionCard(title = "التاريخ والوقت") {
            // Automatic day name and formatted date/time display
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "اليوم المختار تلقائياً: ${TimeFormatter.formatDayName(scheduledMillis)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${TimeFormatter.formatFullDate(scheduledMillis)} • الساعة ${
                            TimeFormatter.formatTime(scheduledMillis, use24HourInPicker)
                        }",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = TimeFormatter.formatRemainingTimeAr(scheduledMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isPastTime) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تنبيه: الموعد المختار في الماضي! سيُطلب منك التأكيد قبل الحفظ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = scheduledMillis }
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val updated = Calendar.getInstance().apply {
                                    timeInMillis = scheduledMillis
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                scheduledMillis = updated.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_date_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اختيار التاريخ")
                }

                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = scheduledMillis }
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val updated = Calendar.getInstance().apply {
                                    timeInMillis = scheduledMillis
                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                scheduledMillis = updated.timeInMillis
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            use24HourInPicker
                        ).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_time_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اختيار الوقت")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick time presets + 12h/24h toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = false,
                    onClick = { scheduledMillis = System.currentTimeMillis() + 60_000L },
                    label = { Text("بعد دقيقة") },
                    modifier = Modifier.testTag("preset_1_minute")
                )
                FilterChip(
                    selected = false,
                    onClick = { scheduledMillis = System.currentTimeMillis() + 15 * 60_000L },
                    label = { Text("بعد 15 د") }
                )
                FilterChip(
                    selected = false,
                    onClick = { scheduledMillis = System.currentTimeMillis() + 60 * 60_000L },
                    label = { Text("بعد ساعة") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        scheduledMillis = cal.timeInMillis
                    },
                    label = { Text("غداً 09:00") }
                )
                FilterChip(
                    selected = use24HourInPicker,
                    onClick = { use24HourInPicker = !use24HourInPicker },
                    label = { Text(if (use24HourInPicker) "نظام 24 ساعة" else "نظام 12 ساعة") }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 4: Recurrence System
        SectionCard(title = "نظام التكرار الذكي") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RecurrenceType.entries.forEach { type ->
                    FilterChip(
                        selected = recurrenceType == type,
                        onClick = { recurrenceType = type },
                        label = { Text(type.labelAr) },
                        modifier = Modifier.testTag("recurrence_chip_${type.name}")
                    )
                }
            }

            if (recurrenceType == RecurrenceType.SPECIFIC_DAYS_OF_WEEK) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "اختر أيام الأسبوع (مثلاً: الأحد والثلاثاء والخميس):",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                val daysMap = listOf(
                    Calendar.SUNDAY to "الأحد",
                    Calendar.MONDAY to "الإثنين",
                    Calendar.TUESDAY to "الثلاثاء",
                    Calendar.WEDNESDAY to "الأربعاء",
                    Calendar.THURSDAY to "الخميس",
                    Calendar.FRIDAY to "الجمعة",
                    Calendar.SATURDAY to "السبت"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    daysMap.forEach { (dayInt, dayName) ->
                        val isDaySelected = dayInt in selectedDaysOfWeek
                        FilterChip(
                            selected = isDaySelected,
                            onClick = {
                                if (isDaySelected && selectedDaysOfWeek.size > 1) {
                                    selectedDaysOfWeek.remove(dayInt)
                                } else if (!isDaySelected) {
                                    selectedDaysOfWeek.add(dayInt)
                                }
                            },
                            label = { Text(dayName) }
                        )
                    }
                }
            }

            if (recurrenceType == RecurrenceType.EVERY_N_DAYS || recurrenceType == RecurrenceType.CUSTOM) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = recurrenceIntervalInput,
                        onValueChange = { recurrenceIntervalInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("كل عدد") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(120.dp)
                    )
                    if (recurrenceType == RecurrenceType.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CustomIntervalUnit.entries.forEach { unit ->
                                FilterChip(
                                    selected = customIntervalUnit == unit,
                                    onClick = { customIntervalUnit = unit },
                                    label = { Text(unit.labelAr) }
                                )
                            }
                        }
                    } else {
                        Text("أيام", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (recurrenceType != RecurrenceType.NONE) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (recurrenceEndDateMillis == null) {
                            "يستمر التكرار حتى توقفه بنفسك"
                        } else {
                            "ينتهي التكرار في: ${TimeFormatter.formatShortDate(recurrenceEndDateMillis!!)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        TextButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = recurrenceEndDateMillis ?: (scheduledMillis + 30L * 86_400_000L)
                                }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val endCal = Calendar.getInstance().apply {
                                            set(y, m, d, 23, 59, 59)
                                        }
                                        recurrenceEndDateMillis = endCal.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        ) {
                            Text(if (recurrenceEndDateMillis == null) "تحديد تاريخ انتهاء" else "تغيير")
                        }
                        if (recurrenceEndDateMillis != null) {
                            TextButton(onClick = { recurrenceEndDateMillis = null }) {
                                Text("إلغاء الانتهاء")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 5: Pre-Alerts (التنبيه قبل الموعد)
        SectionCard(title = "التنبيه المسبق قبل الموعد") {
            Text(
                text = "يمكنك إضافة أكثر من تنبيه مسبق للمذكرة الواحدة واختيار نوع كل تنبيه (إشعار أو منبّه) بصورة مستقلة:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (preAlerts.isEmpty()) {
                Text(
                    text = "لا توجد تنبيهات مسبقة مضافة (سيتم التنبيه في نفس وقت الموعد فقط).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    preAlerts.forEachIndexed { idx, spec ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (spec.alertType == AlertType.ALARM) Icons.Default.Alarm else Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = spec.getLabelAr(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(
                                    onClick = { preAlerts.removeAt(idx) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "حذف التنبيه المسبق")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showAddPreAlertDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_pre_alert_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة تنبيه مسبق")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 6: Sound Library, Custom Ringtone & Vibration
        SectionCard(title = "صوت التنبيه والاهتزاز") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تفعيل الاهتزاز مع التنبيه",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = { vibrationEnabled = it },
                    modifier = Modifier.testTag("vibration_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SoundOption.entries.forEach { sound ->
                val isSelected = selectedSound == sound
                val isPreviewing = previewingSoundId == sound.id
                Surface(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (sound == SoundOption.CUSTOM_URI && customSoundUri.isNullOrBlank()) {
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            } else {
                                selectedSound = sound
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    if (sound == SoundOption.CUSTOM_URI && customSoundUri.isNullOrBlank()) {
                                        audioPickerLauncher.launch(arrayOf("audio/*"))
                                    } else {
                                        selectedSound = sound
                                    }
                                }
                            )
                            Column {
                                Text(
                                    text = sound.titleAr,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (sound == SoundOption.CUSTOM_URI && !customSoundUri.isNullOrBlank()) {
                                        "ملف صوتي مخصص محدد"
                                    } else {
                                        sound.subtitleAr
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sound == SoundOption.CUSTOM_URI) {
                                TextButton(onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) }) {
                                    Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("اختيار ملف")
                                }
                            }
                            OutlinedButton(
                                onClick = { onPreviewSound(sound, customSoundUri, vibrationEnabled) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("preview_sound_${sound.id}")
                            ) {
                                Icon(
                                    imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "تجربة الصوت",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPreviewing) "إيقاف" else "تجربة الصوت", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (title.isBlank()) {
                    titleError = true
                    return@Button
                }
                if (isPastTime) {
                    showPastTimeWarningDialog = true
                    return@Button
                }
                val entity = buildReminderEntity(
                    existing = existingReminder,
                    title = title,
                    details = details,
                    longNote = longNote,
                    category = selectedCategory,
                    colorHex = selectedColorHex,
                    scheduledMillis = scheduledMillis,
                    alertType = selectedAlertType,
                    recurrenceType = recurrenceType,
                    selectedDaysOfWeek = selectedDaysOfWeek,
                    recurrenceInterval = recurrenceIntervalInput.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    customUnit = customIntervalUnit,
                    endDateMillis = recurrenceEndDateMillis,
                    preAlerts = preAlerts,
                    soundOption = selectedSound,
                    customSoundUri = customSoundUri,
                    vibrationEnabled = vibrationEnabled
                )
                onStopSoundPreview()
                onSaveReminder(entity)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("save_reminder_bottom_button"),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (existingReminder == null) "حفظ المذكرة وجدولة التنبيه" else "حفظ التعديلات وإعادة الجدولة",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    }

    // Dialog: Add Pre-Alert
    if (showAddPreAlertDialog) {
        var chosenOffset by remember { mutableIntStateOf(15) }
        var customMinutesText by remember { mutableStateOf("20") }
        var isCustomOffset by remember { mutableStateOf(false) }
        var chosenType by remember { mutableStateOf(AlertType.NOTIFICATION) }

        AlertDialog(
            onDismissRequest = { showAddPreAlertDialog = false },
            title = { Text("إضافة تنبيه مسبق") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("متى تريد التنبيه المسبق؟", fontWeight = FontWeight.Bold)
                    val presets = listOf(
                        5 to "قبل 5 دقائق",
                        10 to "قبل 10 دقائق",
                        15 to "قبل 15 دقيقة",
                        30 to "قبل 30 دقيقة",
                        60 to "قبل ساعة",
                        1440 to "قبل يوم"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { (mins, label) ->
                            FilterChip(
                                selected = !isCustomOffset && chosenOffset == mins,
                                onClick = {
                                    isCustomOffset = false
                                    chosenOffset = mins
                                },
                                label = { Text(label) }
                            )
                        }
                        FilterChip(
                            selected = isCustomOffset,
                            onClick = { isCustomOffset = true },
                            label = { Text("مدة مخصصة") }
                        )
                    }

                    if (isCustomOffset) {
                        OutlinedTextField(
                            value = customMinutesText,
                            onValueChange = { customMinutesText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("عدد الدقائق قبل الموعد") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("نوع التنبيه المسبق:", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = chosenType == AlertType.NOTIFICATION,
                            onClick = { chosenType = AlertType.NOTIFICATION },
                            label = { Text("إشعار عادي") }
                        )
                        FilterChip(
                            selected = chosenType == AlertType.ALARM,
                            onClick = { chosenType = AlertType.ALARM },
                            label = { Text("منبّه قوي") }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalOffset = if (isCustomOffset) {
                            customMinutesText.toIntOrNull()?.coerceIn(1, 10080) ?: 20
                        } else {
                            chosenOffset
                        }
                        preAlerts.add(PreAlertSpec(finalOffset, chosenType))
                        showAddPreAlertDialog = false
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPreAlertDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog: Past Time Warning
    if (showPastTimeWarningDialog) {
        AlertDialog(
            onDismissRequest = { showPastTimeWarningDialog = false },
            title = { Text("تنبيه: الموعد المختار في الماضي") },
            text = {
                Text("لقد اخترت تاريخاً أو وقتاً مضى. هل تريد ضبط الموعد ليوم غد في نفس الساعة، أم حفظه كما هو؟")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPastTimeWarningDialog = false
                        val tomorrowMillis = scheduledMillis + 86_400_000L
                        val entity = buildReminderEntity(
                            existing = existingReminder,
                            title = title,
                            details = details,
                            longNote = longNote,
                            category = selectedCategory,
                            colorHex = selectedColorHex,
                            scheduledMillis = tomorrowMillis,
                            alertType = selectedAlertType,
                            recurrenceType = recurrenceType,
                            selectedDaysOfWeek = selectedDaysOfWeek,
                            recurrenceInterval = recurrenceIntervalInput.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            customUnit = customIntervalUnit,
                            endDateMillis = recurrenceEndDateMillis,
                            preAlerts = preAlerts,
                            soundOption = selectedSound,
                            customSoundUri = customSoundUri,
                            vibrationEnabled = vibrationEnabled
                        )
                        onStopSoundPreview()
                        onSaveReminder(entity)
                    }
                ) {
                    Text("جدولة لغدٍ في نفس الساعة")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPastTimeWarningDialog = false
                        val entity = buildReminderEntity(
                            existing = existingReminder,
                            title = title,
                            details = details,
                            longNote = longNote,
                            category = selectedCategory,
                            colorHex = selectedColorHex,
                            scheduledMillis = scheduledMillis,
                            alertType = selectedAlertType,
                            recurrenceType = recurrenceType,
                            selectedDaysOfWeek = selectedDaysOfWeek,
                            recurrenceInterval = recurrenceIntervalInput.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            customUnit = customIntervalUnit,
                            endDateMillis = recurrenceEndDateMillis,
                            preAlerts = preAlerts,
                            soundOption = selectedSound,
                            customSoundUri = customSoundUri,
                            vibrationEnabled = vibrationEnabled
                        )
                        onStopSoundPreview()
                        onSaveReminder(entity)
                    }
                ) {
                    Text("حفظ كما هو")
                }
            }
        )
    }

    // Dialog: Add Custom Category
    if (showNewCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = { Text("إنشاء تصنيف خاص جديد") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("اسم التصنيف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            onAddCustomCategory(newCategoryName.trim(), selectedColorHex)
                            selectedCategory = newCategoryName.trim()
                            newCategoryName = ""
                            showNewCategoryDialog = false
                        }
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

private fun buildReminderEntity(
    existing: ReminderEntity?,
    title: String,
    details: String,
    longNote: String,
    category: String,
    colorHex: String,
    scheduledMillis: Long,
    alertType: AlertType,
    recurrenceType: RecurrenceType,
    selectedDaysOfWeek: List<Int>,
    recurrenceInterval: Int,
    customUnit: CustomIntervalUnit,
    endDateMillis: Long?,
    preAlerts: List<PreAlertSpec>,
    soundOption: SoundOption,
    customSoundUri: String?,
    vibrationEnabled: Boolean
): ReminderEntity {
    return ReminderEntity(
        id = existing?.id ?: 0L,
        title = title.trim(),
        details = details.trim(),
        longNote = longNote.trim(),
        category = category,
        colorHex = colorHex,
        scheduledTimeMillis = scheduledMillis,
        originalTimeMillis = scheduledMillis,
        alertType = alertType.name,
        recurrenceType = recurrenceType.name,
        recurrenceDaysOfWeek = selectedDaysOfWeek.sorted().joinToString(","),
        recurrenceInterval = recurrenceInterval,
        customIntervalUnit = customUnit.name,
        recurrenceEndDateMillis = endDateMillis,
        preAlertsSerialized = PreAlertSpec.serializeList(preAlerts),
        soundId = soundOption.id,
        customSoundUri = customSoundUri,
        vibrationEnabled = vibrationEnabled,
        isEnabled = true,
        isCompleted = false,
        isArchived = false,
        snoozeUntilMillis = null
    )
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun AlertModeOptionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
