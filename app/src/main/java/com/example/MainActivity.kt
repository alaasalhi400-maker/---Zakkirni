package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alarm.NotificationHelper
import com.example.ui.AppDestination
import com.example.ui.ZakkirniViewModel
import com.example.ui.screens.AddEditReminderScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReminderDetailsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ZakkirniTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openReminderId = intent?.getLongExtra(NotificationHelper.EXTRA_OPEN_REMINDER_ID, -1L) ?: -1L

        setContent {
            val viewModel: ZakkirniViewModel = viewModel(
                factory = ZakkirniViewModel.Factory(application)
            )
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            LaunchedEffect(openReminderId) {
                if (openReminderId > 0L) {
                    viewModel.openReminderDetails(openReminderId)
                }
            }

            val layoutDirection = if (settings.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                ZakkirniTheme(
                    themeMode = settings.themeMode,
                    palette = settings.colorPalette
                ) {
                    ZakkirniAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun ZakkirniAppContent(viewModel: ZakkirniViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allReminders by viewModel.allReminders.collectAsStateWithLifecycle()
    val filteredReminders by viewModel.filteredRemindersForHome.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val alertLogs by viewModel.alertLogs.collectAsStateWithLifecycle()
    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val selectedReminderId by viewModel.selectedReminderId.collectAsStateWithLifecycle()
    val editingReminder by viewModel.editingReminder.collectAsStateWithLifecycle()
    val prefilledDateMillis by viewModel.prefilledDateMillis.collectAsStateWithLifecycle()
    val selectedCalendarDayMillis by viewModel.selectedCalendarDayMillis.collectAsStateWithLifecycle()
    val nowMillis by viewModel.nowMillis.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val activeAlarmSession by viewModel.activeAlarmSession.collectAsStateWithLifecycle()
    val previewingSoundId by viewModel.previewingSoundId.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.refreshPermissionsState(notificationExplicitlyDenied = !isGranted)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionsState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val requestNotifPermAction = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val requestExactAlarmAction = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    }

    val requestFullScreenAction = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    }

    val showBottomBarAndFab = currentDestination in listOf(
        AppDestination.HOME,
        AppDestination.CALENDAR,
        AppDestination.CATEGORIES,
        AppDestination.SETTINGS
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (currentDestination == AppDestination.HOME || currentDestination == AppDestination.CALENDAR) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Request notification permission contextually when creating the first reminder if not granted
                        if (!permissionStatus.hasNotificationPermission &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        val prefill = if (currentDestination == AppDestination.CALENDAR) {
                            selectedCalendarDayMillis
                        } else null
                        viewModel.openAddReminder(prefill)
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = "إضافة مذكرة") },
                    text = { Text("مذكرة جديدة", fontWeight = FontWeight.ExtraBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_reminder")
                )
            }
        },
        bottomBar = {
            if (showBottomBarAndFab) {
                NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.HOME,
                        onClick = { viewModel.navigateTo(AppDestination.HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "الرئيسية") },
                        label = { Text("الرئيسية") },
                        modifier = Modifier.testTag("nav_home")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.CALENDAR,
                        onClick = { viewModel.navigateTo(AppDestination.CALENDAR) },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "التقويم") },
                        label = { Text("التقويم") },
                        modifier = Modifier.testTag("nav_calendar")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.CATEGORIES,
                        onClick = { viewModel.navigateTo(AppDestination.CATEGORIES) },
                        icon = { Icon(Icons.Default.Category, contentDescription = "التصنيفات") },
                        label = { Text("التصنيفات") },
                        modifier = Modifier.testTag("nav_categories")
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppDestination.SETTINGS,
                        onClick = { viewModel.navigateTo(AppDestination.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") },
                        label = { Text("الإعدادات") },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                AppDestination.HOME -> {
                    HomeScreen(
                        settings = settings,
                        allReminders = allReminders,
                        filteredReminders = filteredReminders,
                        categories = categories,
                        selectedSection = selectedSection,
                        searchQuery = searchQuery,
                        selectedCategoryFilter = selectedCategoryFilter,
                        nowMillis = nowMillis,
                        hasNotificationPermission = permissionStatus.hasNotificationPermission,
                        canScheduleExactAlarms = permissionStatus.canScheduleExactAlarms,
                        activeAlarmSession = activeAlarmSession,
                        onSelectSection = viewModel::selectSection,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSelectCategoryFilter = viewModel::selectCategoryFilter,
                        onOpenAddReminder = { viewModel.openAddReminder() },
                        onOpenReminderDetails = viewModel::openReminderDetails,
                        onToggleAlertType = viewModel::toggleAlertType,
                        onToggleCompleted = viewModel::toggleReminderCompleted,
                        onToggleEnabled = viewModel::toggleReminderEnabled,
                        onOpenSettings = { viewModel.navigateTo(AppDestination.SETTINGS) },
                        onRequestNotificationPermission = requestNotifPermAction,
                        onRequestExactAlarmPermission = requestExactAlarmAction,
                        onQuickScheduleOneMinute = { alertType ->
                            viewModel.quickScheduleTestReminder(inOneMinute = true, alertType = alertType)
                        },
                        onStopActiveAlarm = viewModel::dismissActiveAlarm
                    )
                }

                AppDestination.CALENDAR -> {
                    CalendarScreen(
                        allReminders = allReminders,
                        selectedDayMillis = selectedCalendarDayMillis,
                        settings = settings,
                        nowMillis = nowMillis,
                        onSelectDay = viewModel::selectCalendarDay,
                        onAddReminderOnDate = { dateMillis -> viewModel.openAddReminder(dateMillis) },
                        onOpenReminderDetails = viewModel::openReminderDetails,
                        onToggleAlertType = viewModel::toggleAlertType,
                        onToggleCompleted = viewModel::toggleReminderCompleted,
                        onToggleEnabled = viewModel::toggleReminderEnabled
                    )
                }

                AppDestination.CATEGORIES -> {
                    CategoriesScreen(
                        categories = categories,
                        allReminders = allReminders,
                        onSelectCategoryToFilter = { categoryName ->
                            viewModel.selectCategoryFilter(categoryName)
                            viewModel.navigateTo(AppDestination.HOME)
                        },
                        onAddCustomCategory = viewModel::addCustomCategory
                    )
                }

                AppDestination.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        permissionStatus = permissionStatus,
                        alertLogs = alertLogs,
                        previewingSoundId = previewingSoundId,
                        onUpdateUserName = viewModel::updateUserName,
                        onUpdateLanguage = viewModel::updateLanguage,
                        onUpdateThemeMode = viewModel::updateThemeMode,
                        onUpdateColorPalette = viewModel::updateColorPalette,
                        onUpdateIs24Hour = viewModel::updateIs24Hour,
                        onUpdateDefaultSound = { sound -> viewModel.updateDefaultSound(sound) },
                        onUpdateDefaultVibration = viewModel::updateDefaultVibration,
                        onUpdateDefaultSnooze = viewModel::updateDefaultSnoozeMinutes,
                        onUpdateDefaultAlertType = viewModel::updateDefaultAlertType,
                        onPreviewSound = viewModel::previewSound,
                        onRequestNotificationPermission = requestNotifPermAction,
                        onRequestExactAlarmPermission = requestExactAlarmAction,
                        onRequestFullScreenIntentPermission = requestFullScreenAction,
                        onExportBackup = viewModel::exportBackup,
                        onImportBackup = viewModel::importBackup,
                        onClearAlertLogs = viewModel::clearAlertLogs
                    )
                }

                AppDestination.ADD_EDIT_REMINDER -> {
                    AddEditReminderScreen(
                        existingReminder = editingReminder,
                        prefilledDateMillis = prefilledDateMillis,
                        settings = settings,
                        categories = categories,
                        previewingSoundId = previewingSoundId,
                        onSaveReminder = { reminder -> viewModel.saveReminder(reminder) },
                        onAddCustomCategory = viewModel::addCustomCategory,
                        onPreviewSound = viewModel::previewSound,
                        onStopSoundPreview = viewModel::stopSoundPreview,
                        onBack = viewModel::navigateBackToHome
                    )
                }

                AppDestination.REMINDER_DETAILS -> {
                    val targetReminder = allReminders.find { it.id == selectedReminderId }
                    ReminderDetailsScreen(
                        reminder = targetReminder,
                        is24Hour = settings.is24HourFormat,
                        nowMillis = nowMillis,
                        previewingSoundId = previewingSoundId,
                        onBack = viewModel::navigateBackToHome,
                        onEdit = viewModel::openEditReminder,
                        onSetAlertType = viewModel::setAlertType,
                        onToggleEnabled = viewModel::toggleReminderEnabled,
                        onToggleCompleted = viewModel::toggleReminderCompleted,
                        onToggleArchived = viewModel::toggleReminderArchived,
                        onSnooze = viewModel::snoozeReminder,
                        onTriggerNow = viewModel::triggerReminderNow,
                        onPreviewSound = viewModel::previewSound,
                        onDelete = viewModel::deleteReminder
                    )
                }
            }
        }
    }
}
