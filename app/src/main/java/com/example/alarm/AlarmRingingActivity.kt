package com.example.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.ReminderEntity
import com.example.model.TimeFormatter
import com.example.ui.theme.ZakkirniTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()
        enableEdgeToEdge()

        val reminderId = intent?.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, -1L) ?: -1L

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ZakkirniTheme(darkTheme = true) {
                    val activeSession by AlarmPlaybackService.activeAlarm.collectAsState()
                    var loadedReminder by remember { mutableStateOf<ReminderEntity?>(null) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(reminderId) {
                        if (reminderId > 0L) {
                            loadedReminder = AppDatabase.getInstance(applicationContext)
                                .reminderDao()
                                .getReminderById(reminderId)
                        }
                    }

                    val title = activeSession?.title ?: loadedReminder?.title ?: "منبّه ذكّرني المهم"
                    val details = activeSession?.details ?: loadedReminder?.details ?: ""
                    val longNote = activeSession?.longNote ?: loadedReminder?.longNote ?: ""
                    val category = activeSession?.category ?: loadedReminder?.category ?: "المواعيد"
                    val effectiveId = activeSession?.reminderId ?: loadedReminder?.id ?: reminderId

                    AlarmRingingScreen(
                        title = title,
                        details = details,
                        longNote = longNote,
                        category = category,
                        onDismissAlarm = {
                            scope.launch {
                                val dismissIntent = Intent(
                                    applicationContext,
                                    ReminderBroadcastReceiver::class.java
                                ).apply {
                                    action = NotificationHelper.ACTION_DISMISS_ALARM
                                    putExtra(NotificationHelper.EXTRA_REMINDER_ID, effectiveId)
                                }
                                ReminderBroadcastReceiver.processIntentSuspend(applicationContext, dismissIntent)
                                finish()
                            }
                        },
                        onSnoozeAlarm = { minutes ->
                            scope.launch {
                                val snoozeIntent = Intent(
                                    applicationContext,
                                    ReminderBroadcastReceiver::class.java
                                ).apply {
                                    action = NotificationHelper.ACTION_SNOOZE_ALARM
                                    putExtra(NotificationHelper.EXTRA_REMINDER_ID, effectiveId)
                                    putExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, minutes)
                                }
                                ReminderBroadcastReceiver.processIntentSuspend(applicationContext, snoozeIntent)
                                finish()
                            }
                        },
                        onMarkCompleted = {
                            scope.launch {
                                val completeIntent = Intent(
                                    applicationContext,
                                    ReminderBroadcastReceiver::class.java
                                ).apply {
                                    action = NotificationHelper.ACTION_MARK_COMPLETED
                                    putExtra(NotificationHelper.EXTRA_REMINDER_ID, effectiveId)
                                }
                                ReminderBroadcastReceiver.processIntentSuspend(applicationContext, completeIntent)
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmRingingScreen(
    title: String,
    details: String,
    longNote: String,
    category: String,
    onDismissAlarm: () -> Unit,
    onSnoozeAlarm: (Int) -> Unit,
    onMarkCompleted: () -> Unit
) {
    var currentMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedSnoozeMinutes by remember { mutableStateOf(10) }
    var showCustomSnoozeDialog by remember { mutableStateOf(false) }
    var customSnoozeInput by remember { mutableStateOf("20") }

    LaunchedEffect(Unit) {
        while (true) {
            currentMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val timeString = remember(currentMillis) {
        SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(currentMillis))
    }
    val secondsString = remember(currentMillis) {
        SimpleDateFormat(":ss", Locale.ENGLISH).format(Date(currentMillis))
    }
    val dateString = remember(currentMillis) {
        TimeFormatter.formatFullDate(currentMillis)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090D16),
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("alarm_ringing_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header & Live Digital Clock
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "منبّه ذكّرني القوي • $category",
                            color = Color(0xFFFDE68A),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = timeString,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 2.sp,
                        modifier = Modifier.testTag("alarm_digital_clock")
                    )
                    Text(
                        text = secondsString,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFBBF24),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Text(
                    text = dateString,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center
                )
            }

            // Center Pulsing Icon & Reminder Content Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFF59E0B).copy(alpha = 0.45f),
                                    Color(0xFFF59E0B).copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(2.dp, Color(0xFFFBBF24), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "رنين المنبّه",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("alarm_reminder_title")
                        )

                        if (details.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFE2E8F0),
                                textAlign = TextAlign.Center
                            )
                        }

                        if (longNote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = longNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Bottom Controls: Smart Snooze Selector + Large Snooze & Stop Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "تأجيل المنبّه الذكي (Snooze):",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val snoozePresets = listOf(5, 10, 15, 30)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    snoozePresets.forEach { mins ->
                        val isSelected = selectedSnoozeMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF1E293B),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedSnoozeMinutes = mins }
                                .testTag("snooze_chip_$mins")
                        ) {
                            Text(
                                text = "$mins د",
                                color = if (isSelected) Color(0xFF0F172A) else Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedSnoozeMinutes !in snoozePresets) Color(0xFFF59E0B) else Color(0xFF1E293B),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showCustomSnoozeDialog = true }
                            .testTag("snooze_chip_custom")
                    ) {
                        Text(
                            text = if (selectedSnoozeMinutes !in snoozePresets) "$selectedSnoozeMinutes د (مخصص)" else "مخصص...",
                            color = if (selectedSnoozeMinutes !in snoozePresets) Color(0xFF0F172A) else Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSnoozeAlarm(selectedSnoozeMinutes) },
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("alarm_snooze_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24))
                    ) {
                        Icon(Icons.Default.Snooze, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تأجيل ($selectedSnoozeMinutes د)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onMarkCompleted,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("alarm_complete_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تم الإنجاز",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismissAlarm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .testTag("alarm_stop_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AlarmOff,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "إيقاف المنبّه نهائياً",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }

    if (showCustomSnoozeDialog) {
        AlertDialog(
            onDismissRequest = { showCustomSnoozeDialog = false },
            title = { Text("مدة تأجيل مخصصة (بالدقائق)") },
            text = {
                OutlinedTextField(
                    value = customSnoozeInput,
                    onValueChange = { customSnoozeInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("عدد الدقائق (مثلاً 25)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mins = customSnoozeInput.toIntOrNull()?.coerceIn(1, 720) ?: 15
                        selectedSnoozeMinutes = mins
                        showCustomSnoozeDialog = false
                    }
                ) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomSnoozeDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
