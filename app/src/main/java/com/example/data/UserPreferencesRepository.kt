package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AlertType
import com.example.model.AppColorPalette
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.SoundOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zakkirni_settings")

data class UserSettings(
    val userName: String = "صديق ذكّرني",
    val language: AppLanguage = AppLanguage.AR,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val colorPalette: AppColorPalette = AppColorPalette.GOLD_MIDNIGHT,
    val is24HourFormat: Boolean = false,
    val defaultSound: SoundOption = SoundOption.CLASSIC_ALARM,
    val defaultCustomSoundUri: String? = null,
    val defaultVibration: Boolean = true,
    val defaultSnoozeMinutes: Int = 10,
    val defaultAlertType: AlertType = AlertType.NOTIFICATION,
    val highPriorityFallbackEnabled: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_PALETTE = stringPreferencesKey("color_palette")
        val IS_24_HOUR = booleanPreferencesKey("is_24_hour")
        val DEFAULT_SOUND = stringPreferencesKey("default_sound")
        val DEFAULT_CUSTOM_URI = stringPreferencesKey("default_custom_uri")
        val DEFAULT_VIBRATION = booleanPreferencesKey("default_vibration")
        val DEFAULT_SNOOZE = intPreferencesKey("default_snooze_minutes")
        val DEFAULT_ALERT_TYPE = stringPreferencesKey("default_alert_type")
        val HIGH_PRIORITY_FALLBACK = booleanPreferencesKey("high_priority_fallback")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            userName = prefs[Keys.USER_NAME] ?: "صديق ذكّرني",
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE] ?: AppLanguage.AR.code),
            themeMode = runCatching { AppThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK.name) }
                .getOrDefault(AppThemeMode.DARK),
            colorPalette = runCatching {
                AppColorPalette.valueOf(prefs[Keys.COLOR_PALETTE] ?: AppColorPalette.GOLD_MIDNIGHT.name)
            }.getOrDefault(AppColorPalette.GOLD_MIDNIGHT),
            is24HourFormat = prefs[Keys.IS_24_HOUR] ?: false,
            defaultSound = SoundOption.fromId(prefs[Keys.DEFAULT_SOUND] ?: SoundOption.CLASSIC_ALARM.id),
            defaultCustomSoundUri = prefs[Keys.DEFAULT_CUSTOM_URI],
            defaultVibration = prefs[Keys.DEFAULT_VIBRATION] ?: true,
            defaultSnoozeMinutes = prefs[Keys.DEFAULT_SNOOZE] ?: 10,
            defaultAlertType = runCatching {
                AlertType.valueOf(prefs[Keys.DEFAULT_ALERT_TYPE] ?: AlertType.NOTIFICATION.name)
            }.getOrDefault(AlertType.NOTIFICATION),
            highPriorityFallbackEnabled = prefs[Keys.HIGH_PRIORITY_FALLBACK] ?: true
        )
    }

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name.ifBlank { "صديق ذكّرني" } }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateColorPalette(palette: AppColorPalette) {
        context.dataStore.edit { it[Keys.COLOR_PALETTE] = palette.name }
    }

    suspend fun updateIs24Hour(is24Hour: Boolean) {
        context.dataStore.edit { it[Keys.IS_24_HOUR] = is24Hour }
    }

    suspend fun updateDefaultSound(sound: SoundOption, customUri: String? = null) {
        context.dataStore.edit {
            it[Keys.DEFAULT_SOUND] = sound.id
            if (customUri != null) {
                it[Keys.DEFAULT_CUSTOM_URI] = customUri
            }
        }
    }

    suspend fun updateDefaultVibration(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_VIBRATION] = enabled }
    }

    suspend fun updateDefaultSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_SNOOZE] = minutes.coerceIn(1, 240) }
    }

    suspend fun updateDefaultAlertType(alertType: AlertType) {
        context.dataStore.edit { it[Keys.DEFAULT_ALERT_TYPE] = alertType.name }
    }
}
