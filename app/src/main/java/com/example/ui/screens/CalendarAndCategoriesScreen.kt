package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReminderEntity
import com.example.data.UserSettings
import com.example.model.CategorySpec
import com.example.model.DefaultCategories
import com.example.model.TimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CalendarScreen(
    allReminders: List<ReminderEntity>,
    selectedDayMillis: Long,
    settings: UserSettings,
    nowMillis: Long,
    onSelectDay: (Long) -> Unit,
    onAddReminderOnDate: (Long) -> Unit,
    onOpenReminderDetails: (Long) -> Unit,
    onToggleAlertType: (ReminderEntity) -> Unit,
    onToggleCompleted: (ReminderEntity, Boolean) -> Unit,
    onToggleEnabled: (ReminderEntity, Boolean) -> Unit
) {
    var isWeeklyView by remember { mutableStateOf(false) }

    val selectedCal = remember(selectedDayMillis) {
        Calendar.getInstance().apply { timeInMillis = selectedDayMillis }
    }
    var displayedYear by remember { mutableIntStateOf(selectedCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(selectedCal.get(Calendar.MONTH)) }

    val monthTitle = remember(displayedYear, displayedMonth, settings.language) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        SimpleDateFormat("MMMM yyyy", Locale("ar")).format(Date(cal.timeInMillis))
    }

    val remindersForSelectedDay = remember(allReminders, selectedDayMillis) {
        allReminders.filter { !it.isArchived && TimeFormatter.isSameDay(it.effectiveTriggerTimeMillis, selectedDayMillis) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar_screen"),
        contentPadding = PaddingValues(bottom = 104.dp)
    ) {
        // Header + Monthly/Weekly Toggle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "التقويم الذكي",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isWeeklyView,
                        onClick = { isWeeklyView = false },
                        label = { Text("شهري") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = isWeeklyView,
                        onClick = { isWeeklyView = true },
                        label = { Text("أسبوعي") },
                        leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // Calendar Grid Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Month Navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                if (displayedMonth == 0) {
                                    displayedMonth = 11
                                    displayedYear -= 1
                                } else {
                                    displayedMonth -= 1
                                }
                            },
                            modifier = Modifier.testTag("calendar_prev_month")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "الشهر السابق")
                        }

                        Text(
                            text = monthTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        IconButton(
                            onClick = {
                                if (displayedMonth == 11) {
                                    displayedMonth = 0
                                    displayedYear += 1
                                } else {
                                    displayedMonth += 1
                                }
                            },
                            modifier = Modifier.testTag("calendar_next_month")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "الشهر التالي")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Days of week header (Arabic Sun..Sat)
                    val weekDays = listOf("أحد", "إثن", "ثلا", "أرب", "خمي", "جمع", "سبت")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekDays.forEach { dName ->
                            Text(
                                text = dName,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isWeeklyView) {
                        WeeklyCalendarRow(
                            selectedDayMillis = selectedDayMillis,
                            allReminders = allReminders,
                            nowMillis = nowMillis,
                            onSelectDay = onSelectDay
                        )
                    } else {
                        MonthlyCalendarGrid(
                            year = displayedYear,
                            month = displayedMonth,
                            selectedDayMillis = selectedDayMillis,
                            allReminders = allReminders,
                            nowMillis = nowMillis,
                            onSelectDay = onSelectDay
                        )
                    }
                }
            }
        }

        // Selected Date Summary & "Add Reminder on this Date" Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = TimeFormatter.formatFullDate(selectedDayMillis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "عدد المذكرات في هذا اليوم: ${remindersForSelectedDay.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        val targetCal = Calendar.getInstance().apply {
                            timeInMillis = selectedDayMillis
                            set(Calendar.HOUR_OF_DAY, 10)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        onAddReminderOnDate(targetCal.timeInMillis)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("calendar_add_on_date_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مذكرة في هذا اليوم")
                }
            }
        }

        if (remindersForSelectedDay.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "لا توجد مذكرات مجدولة في هذا اليوم",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "اضغط على «مذكرة في هذا اليوم» لإنشاء تذكير أو منبّه في هذا التاريخ مباشرًة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(remindersForSelectedDay, key = { it.id }) { reminder ->
                ReminderCardItem(
                    reminder = reminder,
                    is24Hour = settings.is24HourFormat,
                    nowMillis = nowMillis,
                    onClick = { onOpenReminderDetails(reminder.id) },
                    onToggleAlertType = { onToggleAlertType(reminder) },
                    onToggleCompleted = { checked -> onToggleCompleted(reminder, checked) },
                    onToggleEnabled = { enabled -> onToggleEnabled(reminder, enabled) }
                )
            }
        }
    }
}

@Composable
private fun MonthlyCalendarGrid(
    year: Int,
    month: Int,
    selectedDayMillis: Long,
    allReminders: List<ReminderEntity>,
    nowMillis: Long,
    onSelectDay: (Long) -> Unit
) {
    val firstDayCal = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
    }
    val daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val startOffset = firstDayCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY

    val totalCells = ((startOffset + daysInMonth + 6) / 7) * 7
    val rows = totalCells / 7

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - startOffset + 1
                    if (dayNumber in 1..daysInMonth) {
                        val dayCal = Calendar.getInstance().apply {
                            set(year, month, dayNumber, 12, 0, 0)
                        }
                        val dayMillis = dayCal.timeInMillis
                        val count = allReminders.count {
                            !it.isArchived && TimeFormatter.isSameDay(it.effectiveTriggerTimeMillis, dayMillis)
                        }
                        val isSelected = TimeFormatter.isSameDay(dayMillis, selectedDayMillis)
                        val isToday = TimeFormatter.isSameDay(dayMillis, nowMillis)

                        CalendarDayCell(
                            modifier = Modifier.weight(1f),
                            dayNumber = dayNumber,
                            reminderCount = count,
                            isSelected = isSelected,
                            isToday = isToday,
                            onClick = { onSelectDay(dayMillis) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyCalendarRow(
    selectedDayMillis: Long,
    allReminders: List<ReminderEntity>,
    nowMillis: Long,
    onSelectDay: (Long) -> Unit
) {
    val startOfWeekCal = remember(selectedDayMillis) {
        Calendar.getInstance().apply {
            timeInMillis = selectedDayMillis
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 12)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0 until 7) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = startOfWeekCal.timeInMillis
                add(Calendar.DAY_OF_YEAR, i)
            }
            val dayMillis = dayCal.timeInMillis
            val dayNumber = dayCal.get(Calendar.DAY_OF_MONTH)
            val count = allReminders.count {
                !it.isArchived && TimeFormatter.isSameDay(it.effectiveTriggerTimeMillis, dayMillis)
            }
            val isSelected = TimeFormatter.isSameDay(dayMillis, selectedDayMillis)
            val isToday = TimeFormatter.isSameDay(dayMillis, nowMillis)

            CalendarDayCell(
                modifier = Modifier.weight(1f),
                dayNumber = dayNumber,
                reminderCount = count,
                isSelected = isSelected,
                isToday = isToday,
                onClick = { onSelectDay(dayMillis) }
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier = Modifier,
    dayNumber: Int,
    reminderCount: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    reminderCount > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                color = if (isToday && !isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayNumber.toString(),
                fontWeight = if (isSelected || reminderCount > 0) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
            if (reminderCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = reminderCount.toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoriesScreen(
    categories: List<CategorySpec>,
    allReminders: List<ReminderEntity>,
    onSelectCategoryToFilter: (String) -> Unit,
    onAddCustomCategory: (String, String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf(DefaultCategories.cardColorPalette.first()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("categories_screen"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "التصنيفات والبحث السريع",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "اضغط على أي تصنيف لعرض مذكراته أو أنشئ تصنيفاً خاصاً بك",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("create_category_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تصنيف خاص")
                }
            }
        }

        items(categories, key = { it.nameAr }) { category ->
            val count = allReminders.count { !it.isArchived && it.category == category.nameAr }
            val catColor = runCatching { Color(android.graphics.Color.parseColor(category.colorHex)) }
                .getOrDefault(Color(0xFFF59E0B))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelectCategoryToFilter(category.nameAr) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(catColor.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = catColor
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = category.nameAr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (category.isCustom) "تصنيف مخصص • $count مذكرة" else "تصنيف أساسي • $count مذكرة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = catColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "$count مذكرة",
                            color = catColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("إنشاء تصنيف خاص جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("اسم التصنيف (مثلاً: المشاريع، الأدوية)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("اختر لون التصنيف:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DefaultCategories.cardColorPalette.take(6).forEach { hex ->
                            val c = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (selectedHex == hex) 2.5.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedHex = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            onAddCustomCategory(newCategoryName.trim(), selectedHex)
                            newCategoryName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("حفظ التصنيف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
