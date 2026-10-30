package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AlertLogEntity
import com.example.data.UserSettings
import com.example.model.AlertType
import com.example.model.AppColorPalette
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.SoundOption
import com.example.model.TimeFormatter
import com.example.ui.PermissionStatusState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    permissionStatus: PermissionStatusState,
    alertLogs: List<AlertLogEntity>,
    previewingSoundId: String?,
    onUpdateUserName: (String) -> Unit,
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateThemeMode: (AppThemeMode) -> Unit,
    onUpdateColorPalette: (AppColorPalette) -> Unit,
    onUpdateIs24Hour: (Boolean) -> Unit,
    onUpdateDefaultSound: (SoundOption) -> Unit,
    onUpdateDefaultVibration: (Boolean) -> Unit,
    onUpdateDefaultSnooze: (Int) -> Unit,
    onUpdateDefaultAlertType: (AlertType) -> Unit,
    onPreviewSound: (SoundOption, String?, Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestFullScreenIntentPermission: () -> Unit,
    onExportBackup: ((String) -> Unit) -> Unit,
    onImportBackup: (String) -> Unit,
    onClearAlertLogs: () -> Unit
) {
    var nameInput by remember(settings.userName) { mutableStateOf(settings.userName) }
    var backupDialogText by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "الإعدادات المتكاملة",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // 1. Appearance, Language & Profile
        item {
            SettingsCard(title = "اللغة، المظهر وألوان الواجهة") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("اسم المستخدم للترحيب") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onUpdateUserName(nameInput) }) {
                        Text("حفظ")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("لغة الواجهة (مع دعم RTL):", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { onUpdateLanguage(lang) },
                            label = { Text(lang.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("الوضع الليلي والنهاري:", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onUpdateThemeMode(mode) },
                            label = { Text(mode.labelAr) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("ثيم ألوان الواجهة:", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppColorPalette.entries.forEach { palette ->
                        FilterChip(
                            selected = settings.colorPalette == palette,
                            onClick = { onUpdateColorPalette(palette) },
                            label = { Text(palette.labelAr) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("استخدام نظام 24 ساعة افتراضياً", fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = settings.is24HourFormat,
                        onCheckedChange = onUpdateIs24Hour
                    )
                }
            }
        }

        // 2. Default Alarm & Notification Settings
        item {
            SettingsCard(title = "إعدادات التنبيهات والمنبّهات الافتراضية") {
                Text("طريقة التنبيه الافتراضية للمذكرات الجديدة:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertType.entries.forEach { type ->
                        FilterChip(
                            selected = settings.defaultAlertType == type,
                            onClick = { onUpdateDefaultAlertType(type) },
                            label = { Text(type.labelAr) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("مدة التأجيل الافتراضية (Snooze):", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { mins ->
                        FilterChip(
                            selected = settings.defaultSnoozeMinutes == mins,
                            onClick = { onUpdateDefaultSnooze(mins) },
                            label = { Text("$mins د") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("الاهتزاز الافتراضي مع التنبيهات", fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = settings.defaultVibration,
                        onCheckedChange = onUpdateDefaultVibration
                    )
                }
            }
        }

        // 3. Test All Sounds Library
        item {
            SettingsCard(title = "اختبار جميع الأصوات والصوت الافتراضي") {
                SoundOption.entries.filter { it != SoundOption.CUSTOM_URI }.forEach { sound ->
                    val isDefault = settings.defaultSound == sound
                    val isPlaying = previewingSoundId == sound.id
                    Surface(
                        color = if (isDefault) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sound.titleAr, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isDefault) "الصوت الافتراضي الحالي" else sound.subtitleAr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!isDefault) {
                                    TextButton(onClick = { onUpdateDefaultSound(sound) }) {
                                        Text("تعيين كافتراضي")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onPreviewSound(sound, null, settings.defaultVibration) },
                                    modifier = Modifier.testTag("settings_test_sound_${sound.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPlaying) "إيقاف" else "تجربة")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Permissions & Android Reliability Management
        item {
            SettingsCard(title = "إدارة الأذونات والموثوقية في الخلفية") {
                PermissionRowItem(
                    title = "إذن الإشعارات (POST_NOTIFICATIONS)",
                    subtitle = if (permissionStatus.hasNotificationPermission) {
                        "ممنوح — الإشعارات تعمل بشكل طبيعي"
                    } else {
                        "غير ممنوح — اضغط لمنح الإذن وإظهار التنبيهات"
                    },
                    granted = permissionStatus.hasNotificationPermission,
                    onAction = onRequestNotificationPermission
                )

                Spacer(modifier = Modifier.height(8.dp))

                PermissionRowItem(
                    title = "المنبّهات الدقيقة (SCHEDULE_EXACT_ALARM)",
                    subtitle = if (permissionStatus.canScheduleExactAlarms) {
                        "مفعّل — دقة توقيت كاملة في الوقت المحدد تماماً"
                    } else {
                        "غير مفعّل — يتم استخدام البديل النظامي مع احتمال تأخر بسيط"
                    },
                    granted = permissionStatus.canScheduleExactAlarms,
                    onAction = onRequestExactAlarmPermission
                )

                Spacer(modifier = Modifier.height(8.dp))

                PermissionRowItem(
                    title = "العرض بملء الشاشة على القفل (Full-Screen Intent)",
                    subtitle = if (permissionStatus.canUseFullScreenIntent) {
                        "مسموح — تفتح شاشة المنبّه تلقائياً عند قفل الهاتف"
                    } else {
                        "يستخدم إشعاراً عالي الأهمية كبديل آمن؛ اضغط للتفعيل"
                    },
                    granted = permissionStatus.canUseFullScreenIntent,
                    onAction = onRequestFullScreenIntentPermission
                )
            }
        }

        // 5. Backup, Export & Import
        item {
            SettingsCard(title = "النسخ الاحتياطي، التصدير والاستيراد (JSON)") {
                Text(
                    text = "احفظ نسخة احتياطية من جميع مذكراتك وتصنيفاتك لحمايتها عند تغيير الهاتف:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onExportBackup { json ->
                                backupDialogText = json
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_backup_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير النسخة")
                    }

                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_backup_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استيراد نسخة")
                    }
                }
            }
        }

        // 6. Alert Execution History Log
        item {
            SettingsCard(title = "سجل التنبيهات والاختبارات المنفّذة (${alertLogs.size})") {
                if (alertLogs.isEmpty()) {
                    Text(
                        text = "لا توجد سجلات تنبيه بعد. عند حلول موعد أي مذكرة أو تجربتها سيظهر التوثيق الزمني هنا.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        alertLogs.take(10).forEach { log ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${log.reminderTitle} (${log.actionTaken})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${log.note} • ${TimeFormatter.formatShortDate(log.triggeredAtMillis)} ${
                                                TimeFormatter.formatTime(log.triggeredAtMillis, true)
                                            }",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        TextButton(onClick = onClearAlertLogs) {
                            Text("مسح السجل")
                        }
                    }
                }
            }
        }

        // 7. About Zakkirni
        item {
            SettingsCard(title = "معلومات التطبيق") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("ذكّرني | ZAKKIRNI — الإصدار 1.0", fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = "تطبيق أندرويد متكامل يعمل دون إنترنت لإدارة المذكرات والتذكيرات والمنبّهات القوية باستخدام Room Database و AlarmManager.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (backupDialogText != null) {
        AlertDialog(
            onDismissRequest = { backupDialogText = null },
            title = { Text("بيانات النسخة الاحتياطية (JSON)") },
            text = {
                OutlinedTextField(
                    value = backupDialogText ?: "",
                    onValueChange = {},
                    readOnly = true,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { backupDialogText = null }) {
                    Text("إغلاق")
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("استيراد نسخة احتياطية (JSON)") },
            text = {
                OutlinedTextField(
                    value = importJsonInput,
                    onValueChange = { importJsonInput = it },
                    label = { Text("الصق محتوى JSON هنا") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (importJsonInput.isNotBlank()) {
                            onImportBackup(importJsonInput)
                            importJsonInput = ""
                            showImportDialog = false
                        }
                    }
                ) {
                    Text("استيراد الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
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
private fun PermissionRowItem(
    title: String,
    subtitle: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = if (granted) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!granted) {
                TextButton(onClick = onAction) {
                    Text("تفعيل")
                }
            }
        }
    }
}
