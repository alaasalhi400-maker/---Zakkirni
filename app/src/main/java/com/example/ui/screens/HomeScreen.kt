package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.alarm.ActiveAlarmSession
import com.example.data.ReminderEntity
import com.example.data.UserSettings
import com.example.model.AlertType
import com.example.model.CategorySpec
import com.example.model.RecurrenceCalculator
import com.example.model.RecurrenceType
import com.example.model.ReminderSection
import com.example.model.TimeFormatter

@Composable
fun HomeScreen(
    settings: UserSettings,
    allReminders: List<ReminderEntity>,
    filteredReminders: List<ReminderEntity>,
    categories: List<CategorySpec>,
    selectedSection: ReminderSection,
    searchQuery: String,
    selectedCategoryFilter: String?,
    nowMillis: Long,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    activeAlarmSession: ActiveAlarmSession?,
    onSelectSection: (ReminderSection) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategoryFilter: (String?) -> Unit,
    onOpenAddReminder: () -> Unit,
    onOpenReminderDetails: (Long) -> Unit,
    onToggleAlertType: (ReminderEntity) -> Unit,
    onToggleCompleted: (ReminderEntity, Boolean) -> Unit,
    onToggleEnabled: (ReminderEntity, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onQuickScheduleOneMinute: (AlertType) -> Unit,
    onStopActiveAlarm: () -> Unit
) {
    val activeUpcomingCount = remember(allReminders) {
        allReminders.count { !it.isCompleted && !it.isArchived && it.isEnabled }
    }
    val alarmModeCount = remember(allReminders) {
        allReminders.count { !it.isCompleted && !it.isArchived && it.isEnabled && it.parsedAlertType == AlertType.ALARM }
    }
    val notificationModeCount = remember(allReminders) {
        allReminders.count { !it.isCompleted && !it.isArchived && it.isEnabled && it.parsedAlertType == AlertType.NOTIFICATION }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(bottom = 104.dp)
    ) {
        // 1. Top App Bar with Logo, Name, Date, Greeting, and Settings Icon
        item {
            HomeTopHeader(
                userName = settings.userName,
                nowMillis = nowMillis,
                settings = settings,
                onOpenSettings = onOpenSettings
            )
        }

        // 2. Active Ringing Alarm Banner (if currently ringing)
        if (activeAlarmSession != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("active_alarm_banner"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔔 المنبّه يرن الآن: ${activeAlarmSession.title}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (activeAlarmSession.details.isNotBlank()) {
                                Text(
                                    text = activeAlarmSession.details,
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Button(
                            onClick = onStopActiveAlarm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFFDC2626)
                            ),
                            modifier = Modifier.testTag("banner_stop_alarm_button")
                        ) {
                            Text("إيقاف فوراً", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Contextual Permission Guidance Banners (Section 14)
        if (!hasNotificationPermission || !canScheduleExactAlarms) {
            item {
                PermissionGuidanceBanner(
                    hasNotificationPermission = hasNotificationPermission,
                    canScheduleExactAlarms = canScheduleExactAlarms,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRequestExactAlarmPermission = onRequestExactAlarmPermission
                )
            }
        }

        // 4. Hero Summary Card with Visual Artwork & Quick 1-Minute Verification Actions
        item {
            HeroSummaryBanner(
                activeUpcomingCount = activeUpcomingCount,
                alarmModeCount = alarmModeCount,
                notificationModeCount = notificationModeCount,
                onQuickScheduleOneMinute = onQuickScheduleOneMinute
            )
        }

        // 5. Instant Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("ابحث بالعنوان، التفاصيل، التصنيف أو التاريخ...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "بحث")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("home_search_input")
            )
        }

        // 6. Horizontal Category Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryFilter == null,
                    onClick = { onSelectCategoryFilter(null) },
                    label = { Text("الكل") },
                    modifier = Modifier.testTag("category_chip_all")
                )
                categories.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat.nameAr
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCategoryFilter(cat.nameAr) },
                        label = { Text(cat.nameAr) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("category_chip_${cat.nameAr}")
                    )
                }
            }
        }

        // 7. Six Section Tabs (Today, Tomorrow, Upcoming, Recurring, Completed, Archived)
        item {
            val sections = ReminderSection.entries
            ScrollableTabRow(
                selectedTabIndex = sections.indexOf(selectedSection),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                sections.forEach { section ->
                    val selected = section == selectedSection
                    val count = remember(allReminders, section, nowMillis) {
                        countForSection(allReminders, section, nowMillis)
                    }
                    Tab(
                        selected = selected,
                        onClick = { onSelectSection(section) },
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .testTag("section_tab_${section.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = section.getTitle(settings.language),
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = count.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Reminder Cards List or Empty State
        if (filteredReminders.isEmpty()) {
            item {
                EmptySectionState(
                    section = selectedSection,
                    hasSearchQuery = searchQuery.isNotBlank() || selectedCategoryFilter != null,
                    onAddClick = onOpenAddReminder
                )
            }
        } else {
            items(
                items = filteredReminders,
                key = { it.id }
            ) { reminder ->
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

private fun countForSection(
    reminders: List<ReminderEntity>,
    section: ReminderSection,
    now: Long
): Int = reminders.count { r ->
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

@Composable
private fun HomeTopHeader(
    userName: String,
    nowMillis: Long,
    settings: UserSettings,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.AlarmOn,
                    contentDescription = "شعار ذكّرني",
                    tint = Color(0xFF090D16),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ذكّرني | ZAKKIRNI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "مرحباً $userName • ${TimeFormatter.formatFullDate(nowMillis, settings.language)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("home_settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "الإعدادات",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PermissionGuidanceBanner(
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("permission_guidance_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF59E0B).copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تنبيه الأذونات لضمان دقة المواعيد",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (!hasNotificationPermission) {
                Text(
                    text = "• إذن الإشعارات غير ممنوح: لن تظهر الإشعارات في شريط التنبيهات حتى تسمح بالإذن.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onRequestNotificationPermission,
                    modifier = Modifier.testTag("grant_notification_permission_btn")
                ) {
                    Text("منح إذن الإشعارات الآن")
                }
            }
            if (!canScheduleExactAlarms) {
                Text(
                    text = "• إذن المنبّهات الدقيقة غير مفعّل: قد يتأخر التذكير بضع دقائق بسبب توفير الطاقة. يفضّل تفعيله للمواعيد الحساسة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onRequestExactAlarmPermission,
                    modifier = Modifier.testTag("grant_exact_alarm_permission_btn")
                ) {
                    Text("تفعيل المنبّهات الدقيقة")
                }
            }
        }
    }
}

@Composable
private fun HeroSummaryBanner(
    activeUpcomingCount: Int,
    alarmModeCount: Int,
    notificationModeCount: Int,
    onQuickScheduleOneMinute: (AlertType) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_zakkirni_1793368480488),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF090D16).copy(alpha = 0.92f),
                                Color(0xFF0F172A).copy(alpha = 0.80f),
                                Color(0xFF090D16).copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ملخص المذكرات القادمة",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFBBF24)
                        )
                        Text(
                            text = "لديك $activeUpcomingCount مذكرة نشطة",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.testTag("hero_upcoming_count")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBadge(
                            icon = Icons.Default.Notifications,
                            label = "$notificationModeCount إشعار",
                            tint = Color(0xFF38BDF8)
                        )
                        StatBadge(
                            icon = Icons.Default.Alarm,
                            label = "$alarmModeCount منبّه",
                            tint = Color(0xFFFBBF24)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick 1-minute test action bar for instant verification (Section 19)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onQuickScheduleOneMinute(AlertType.NOTIFICATION) }
                            .testTag("quick_test_notification_1m")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ إشعار بعد دقيقة",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.24f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onQuickScheduleOneMinute(AlertType.ALARM) }
                            .testTag("quick_test_alarm_1m")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ منبّه بعد دقيقة",
                                color = Color(0xFFFDE68A),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ReminderCardItem(
    reminder: ReminderEntity,
    is24Hour: Boolean,
    nowMillis: Long,
    onClick: () -> Unit,
    onToggleAlertType: () -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    val accentColor = remember(reminder.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(reminder.colorHex)) }
            .getOrDefault(Color(0xFFF59E0B))
    }
    val isAlarmMode = reminder.parsedAlertType == AlertType.ALARM

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("reminder_card_${reminder.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (reminder.isEnabled && !reminder.isCompleted) 0.9f else 0.55f
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isAlarmMode && reminder.isEnabled && !reminder.isCompleted) {
                        Color(0xFFF59E0B).copy(alpha = 0.45f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            // Top Row: Complete Icon, Title, Category, and Interactive Alert Mode Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onToggleCompleted(!reminder.isCompleted) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("complete_btn_${reminder.id}")
                ) {
                    Icon(
                        imageVector = if (reminder.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (reminder.isCompleted) "مكتملة" else "تحديد كمكتملة",
                        tint = if (reminder.isCompleted) Color(0xFF10B981) else accentColor
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (reminder.details.isNotBlank()) {
                        Text(
                            text = reminder.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Interactive Alert Type Badge (Bell for Notification, Alarm for Strong Alarm)
                // Clicking switches between Notification and Strong Alarm without recreating the reminder!
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAlarmMode) {
                        Color(0xFFEF4444).copy(alpha = 0.18f)
                    } else {
                        Color(0xFF38BDF8).copy(alpha = 0.18f)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleAlertType)
                        .testTag("toggle_alert_mode_${reminder.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isAlarmMode) Icons.Default.Alarm else Icons.Default.Notifications,
                            contentDescription = reminder.parsedAlertType.labelAr,
                            tint = if (isAlarmMode) Color(0xFFF87171) else Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAlarmMode) "منبّه" else "إشعار",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAlarmMode) Color(0xFFF87171) else Color(0xFF38BDF8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Row: Date, Time, Recurrence, Remaining Time, and Enable/Disable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${TimeFormatter.formatShortDate(reminder.effectiveTriggerTimeMillis)} • ${
                                TimeFormatter.formatTime(reminder.effectiveTriggerTimeMillis, is24Hour)
                            }",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Category badge
                        Surface(
                            color = accentColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = reminder.category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // Remaining time badge
                        val statusText = when {
                            reminder.isCompleted -> "مكتملة ✓"
                            reminder.isArchived -> "مؤرشفة"
                            !reminder.isEnabled -> "معطّلة مؤقتاً"
                            reminder.snoozeUntilMillis != null -> "مؤجلة (${
                                TimeFormatter.formatRemainingTimeAr(reminder.snoozeUntilMillis, nowMillis)
                            })"
                            else -> TimeFormatter.formatRemainingTimeAr(reminder.scheduledTimeMillis, nowMillis)
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (reminder.effectiveTriggerTimeMillis < nowMillis && !reminder.isCompleted) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    // Recurrence or Pre-alerts summary if present
                    if (reminder.parsedRecurrenceType != RecurrenceType.NONE || reminder.preAlerts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (reminder.parsedRecurrenceType != RecurrenceType.NONE) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val recText = if (reminder.parsedRecurrenceType == RecurrenceType.SPECIFIC_DAYS_OF_WEEK) {
                                    val days = RecurrenceCalculator.parseDaysOfWeek(reminder.recurrenceDaysOfWeek)
                                    "كل ${RecurrenceCalculator.formatDaysOfWeekAr(days)}"
                                } else {
                                    reminder.parsedRecurrenceType.labelAr
                                }
                                Text(
                                    text = recText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (reminder.preAlerts.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Snooze,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${reminder.preAlerts.size} تنبيه مسبق",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (!reminder.isCompleted && !reminder.isArchived) {
                    Switch(
                        checked = reminder.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("switch_enable_${reminder.id}")
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySectionState(
    section: ReminderSection,
    hasSearchQuery: Boolean,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasSearchQuery) {
                "لا توجد مذكرات تطابق البحث الحالي"
            } else {
                "لا توجد مذكرات في قسم «${section.titleAr}»"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "اضغط على زر (+) لإضافة مذكرة جديدة واختيار طريقة التنبيه (إشعار عادي أو منبّه قوي).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("empty_state_add_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("إضافة مذكرة الآن", fontWeight = FontWeight.Bold)
        }
    }
}
