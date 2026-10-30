package com.example.model

import androidx.annotation.RawRes
import com.example.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class AlertType(val labelAr: String, val labelEn: String, val labelFr: String, val descAr: String) {
    NOTIFICATION(
        labelAr = "إشعار عادي",
        labelEn = "Notification",
        labelFr = "Notification",
        descAr = "تنبيه في شريط الإشعارات مع صوت خفيف وإجراءات سريعة"
    ),
    ALARM(
        labelAr = "منبّه قوي",
        labelEn = "Strong Alarm",
        labelFr = "Alarme Forte",
        descAr = "رنين مستمر وشاشة كاملة حتى توقفه أو تؤجله بنفسك"
    );

    fun getLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.AR -> labelAr
        AppLanguage.EN -> labelEn
        AppLanguage.FR -> labelFr
    }
}

enum class RecurrenceType(val labelAr: String, val labelEn: String, val labelFr: String) {
    NONE("لا يتكرر", "Does not repeat", "Ne se répète pas"),
    DAILY("كل يوم", "Every day", "Chaque jour"),
    WEEKLY("كل أسبوع", "Every week", "Chaque semaine"),
    MONTHLY("كل شهر", "Every month", "Chaque mois"),
    YEARLY("كل سنة", "Every year", "Chaque année"),
    SPECIFIC_DAYS_OF_WEEK("أيام محددة من الأسبوع", "Specific days of week", "Jours spécifiques"),
    EVERY_N_DAYS("كل عدد مخصص من الأيام", "Every N days", "Tous les N jours"),
    CUSTOM("تكرار مخصص", "Custom recurrence", "Récurrence personnalisée");

    fun getLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.AR -> labelAr
        AppLanguage.EN -> labelEn
        AppLanguage.FR -> labelFr
    }
}

enum class CustomIntervalUnit(val labelAr: String) {
    HOURS("ساعات"),
    DAYS("أيام"),
    WEEKS("أسابيع"),
    MONTHS("أشهر")
}

data class PreAlertSpec(
    val offsetMinutes: Int, // 0 = at time, 5 = 5 min before, 10, 15, 30, 60, 1440, or custom
    val alertType: AlertType
) {
    fun getLabelAr(): String {
        val timePart = when (offsetMinutes) {
            0 -> "في نفس وقت الموعد"
            5 -> "قبل 5 دقائق"
            10 -> "قبل 10 دقائق"
            15 -> "قبل 15 دقيقة"
            30 -> "قبل 30 دقيقة"
            60 -> "قبل ساعة"
            120 -> "قبل ساعتين"
            1440 -> "قبل يوم"
            else -> if (offsetMinutes % 60 == 0) {
                "قبل ${offsetMinutes / 60} ساعة"
            } else {
                "قبل $offsetMinutes دقيقة"
            }
        }
        return "$timePart (${alertType.labelAr})"
    }

    companion object {
        fun serializeList(list: List<PreAlertSpec>): String {
            if (list.isEmpty()) return ""
            return list.joinToString(";") { "${it.offsetMinutes}:${it.alertType.name}" }
        }

        fun deserializeList(raw: String): List<PreAlertSpec> {
            if (raw.isBlank()) return emptyList()
            return raw.split(";").mapNotNull { token ->
                val parts = token.split(":")
                if (parts.size == 2) {
                    val mins = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val type = runCatching { AlertType.valueOf(parts[1]) }.getOrDefault(AlertType.NOTIFICATION)
                    PreAlertSpec(mins, type)
                } else null
            }
        }
    }
}

enum class SoundOption(
    val id: String,
    val titleAr: String,
    val subtitleAr: String,
    @RawRes val rawResId: Int?
) {
    CLASSIC_ALARM("CLASSIC_ALARM", "الصوت الكلاسيكي للمنبّه", "رنين منبّه تقليدي واضح ومألوف", R.raw.sound_classic),
    DIGITAL_PULSE("DIGITAL_PULSE", "صوت رقمي ذكي", "نبضات رقمية عالية الوضوح", R.raw.sound_digital),
    STRONG_SIREN("STRONG_SIREN", "رنين قوي ومكثف", "مخصص للمواعيد الحاسمة والاستيقاظ", R.raw.sound_strong),
    CALM_CHIME("CALM_CHIME", "رنين هادئ ومتدرج", "نغمات ناعمة للمذكرات اليومية", R.raw.sound_calm),
    ZAKKIRNI_MELODY("ZAKKIRNI_MELODY", "نغمة ذكّرني المميزة", "لحن شرقي عصري مميز", R.raw.sound_melody),
    CUSTOM_URI("CUSTOM_URI", "نغمة مخصصة من الهاتف", "اختيار ملف صوتي أو نغمة من جهازك", null);

    companion object {
        fun fromId(id: String): SoundOption = entries.find { it.id == id } ?: CLASSIC_ALARM
    }
}

data class CategorySpec(
    val nameAr: String,
    val colorHex: String,
    val iconKey: String,
    val isCustom: Boolean = false
)

object DefaultCategories {
    val list = listOf(
        CategorySpec("الدراسة", "#3B82F6", "school"),
        CategorySpec("العمل", "#F59E0B", "work"),
        CategorySpec("المواعيد", "#EF4444", "event"),
        CategorySpec("الصحة", "#10B981", "health"),
        CategorySpec("التسوق", "#8B5CF6", "shopping"),
        CategorySpec("العائلة", "#EC4899", "family"),
        CategorySpec("المهام الشخصية", "#06B6D4", "person"),
        CategorySpec("أخرى", "#64748B", "other")
    )

    val cardColorPalette = listOf(
        "#F59E0B", // Amber Gold
        "#3B82F6", // Royal Blue
        "#10B981", // Emerald Green
        "#EF4444", // Coral Red
        "#8B5CF6", // Violet
        "#EC4899", // Rose Pink
        "#06B6D4", // Cyan
        "#6366F1"  // Indigo
    )
}

enum class ReminderSection(val titleAr: String, val titleEn: String, val titleFr: String) {
    TODAY("اليوم", "Today", "Aujourd'hui"),
    TOMORROW("غداً", "Tomorrow", "Demain"),
    UPCOMING("القادمة", "Upcoming", "À venir"),
    RECURRING("المذكرات المتكررة", "Recurring", "Récurrentes"),
    COMPLETED("المكتملة", "Completed", "Terminées"),
    ARCHIVED("المؤرشفة", "Archived", "Archivées");

    fun getTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.AR -> titleAr
        AppLanguage.EN -> titleEn
        AppLanguage.FR -> titleFr
    }
}

enum class AppLanguage(val code: String, val displayName: String, val isRtl: Boolean) {
    AR("ar", "العربية", true),
    EN("en", "English", false),
    FR("fr", "Français", false);

    companion object {
        fun fromCode(code: String): AppLanguage = entries.find { it.code == code } ?: AR
    }
}

enum class AppThemeMode(val labelAr: String) {
    DARK("الوضع الليلي الفاخر"),
    LIGHT("الوضع النهاري المشرق"),
    SYSTEM("حسب إعدادات الهاتف")
}

enum class AppColorPalette(val labelAr: String, val primaryHex: Long, val secondaryHex: Long) {
    GOLD_MIDNIGHT("الذهبي الملكي", 0xFFF59E0B, 0xFF14B8A6),
    EMERALD_OASIS("الزمرد العربي", 0xFF10B981, 0xFFF59E0B),
    ROYAL_AMETHYST("الأرجواني المستقبلي", 0xFF8B5CF6, 0xFF06B6D4),
    OCEAN_BREEZE("المحيط الهادئ", 0xFF38BDF8, 0xFF818CF8)
}

object RecurrenceCalculator {
    /**
     * Computes the next occurrence timestamp strictly after [afterTimeMillis] based on the
     * reminder's original schedule so that Snooze never corrupts recurrence timing.
     */
    fun computeNextOccurrence(
        baseScheduledMillis: Long,
        afterTimeMillis: Long,
        recurrenceType: RecurrenceType,
        recurrenceDaysOfWeek: Set<Int>, // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7)
        recurrenceInterval: Int,
        customUnit: CustomIntervalUnit = CustomIntervalUnit.DAYS,
        endDateMillis: Long? = null
    ): Long? {
        if (recurrenceType == RecurrenceType.NONE) return null

        val baseCal = Calendar.getInstance().apply { timeInMillis = baseScheduledMillis }
        val hour = baseCal.get(Calendar.HOUR_OF_DAY)
        val minute = baseCal.get(Calendar.MINUTE)
        val interval = recurrenceInterval.coerceAtLeast(1)

        val candidate = Calendar.getInstance().apply {
            timeInMillis = baseScheduledMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (recurrenceType) {
            RecurrenceType.NONE -> return null
            RecurrenceType.DAILY -> {
                while (candidate.timeInMillis <= afterTimeMillis) {
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            RecurrenceType.WEEKLY -> {
                while (candidate.timeInMillis <= afterTimeMillis) {
                    candidate.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            RecurrenceType.MONTHLY -> {
                while (candidate.timeInMillis <= afterTimeMillis) {
                    candidate.add(Calendar.MONTH, 1)
                }
            }
            RecurrenceType.YEARLY -> {
                while (candidate.timeInMillis <= afterTimeMillis) {
                    candidate.add(Calendar.YEAR, 1)
                }
            }
            RecurrenceType.EVERY_N_DAYS -> {
                while (candidate.timeInMillis <= afterTimeMillis) {
                    candidate.add(Calendar.DAY_OF_YEAR, interval)
                }
            }
            RecurrenceType.CUSTOM -> {
                while (candidate.timeInMillis <= afterTimeMillis) {
                    when (customUnit) {
                        CustomIntervalUnit.HOURS -> candidate.add(Calendar.HOUR_OF_DAY, interval)
                        CustomIntervalUnit.DAYS -> candidate.add(Calendar.DAY_OF_YEAR, interval)
                        CustomIntervalUnit.WEEKS -> candidate.add(Calendar.WEEK_OF_YEAR, interval)
                        CustomIntervalUnit.MONTHS -> candidate.add(Calendar.MONTH, interval)
                    }
                }
            }
            RecurrenceType.SPECIFIC_DAYS_OF_WEEK -> {
                val activeDays = if (recurrenceDaysOfWeek.isEmpty()) {
                    setOf(baseCal.get(Calendar.DAY_OF_WEEK))
                } else {
                    recurrenceDaysOfWeek
                }
                val searchCal = Calendar.getInstance().apply {
                    timeInMillis = maxOf(baseScheduledMillis, afterTimeMillis)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (searchCal.timeInMillis <= afterTimeMillis) {
                    searchCal.add(Calendar.DAY_OF_YEAR, 1)
                }
                var safetyCounter = 0
                while (safetyCounter < 14 && searchCal.get(Calendar.DAY_OF_WEEK) !in activeDays) {
                    searchCal.add(Calendar.DAY_OF_YEAR, 1)
                    safetyCounter++
                }
                candidate.timeInMillis = searchCal.timeInMillis
            }
        }

        val nextMillis = candidate.timeInMillis
        if (endDateMillis != null && nextMillis > endDateMillis) {
            return null
        }
        return nextMillis
    }

    fun parseDaysOfWeek(csv: String): Set<Int> {
        if (csv.isBlank()) return emptySet()
        return csv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun formatDaysOfWeekAr(days: Set<Int>): String {
        if (days.isEmpty()) return ""
        val names = mapOf(
            Calendar.SUNDAY to "الأحد",
            Calendar.MONDAY to "الإثنين",
            Calendar.TUESDAY to "الثلاثاء",
            Calendar.WEDNESDAY to "الأربعاء",
            Calendar.THURSDAY to "الخميس",
            Calendar.FRIDAY to "الجمعة",
            Calendar.SATURDAY to "السبت"
        )
        return days.sorted().mapNotNull { names[it] }.joinToString("، ")
    }
}

object TimeFormatter {
    private val arabicLocale = Locale("ar")

    fun formatDayName(timeMillis: Long, lang: AppLanguage = AppLanguage.AR): String {
        val locale = when (lang) {
            AppLanguage.AR -> arabicLocale
            AppLanguage.EN -> Locale.ENGLISH
            AppLanguage.FR -> Locale.FRENCH
        }
        return SimpleDateFormat("EEEE", locale).format(Date(timeMillis))
    }

    fun formatFullDate(timeMillis: Long, lang: AppLanguage = AppLanguage.AR): String {
        val locale = when (lang) {
            AppLanguage.AR -> arabicLocale
            AppLanguage.EN -> Locale.ENGLISH
            AppLanguage.FR -> Locale.FRENCH
        }
        return SimpleDateFormat("EEEE، d MMMM yyyy", locale).format(Date(timeMillis))
    }

    fun formatShortDate(timeMillis: Long, lang: AppLanguage = AppLanguage.AR): String {
        val locale = when (lang) {
            AppLanguage.AR -> arabicLocale
            AppLanguage.EN -> Locale.ENGLISH
            AppLanguage.FR -> Locale.FRENCH
        }
        return SimpleDateFormat("d MMM yyyy", locale).format(Date(timeMillis))
    }

    fun formatTime(timeMillis: Long, is24Hour: Boolean, lang: AppLanguage = AppLanguage.AR): String {
        val locale = when (lang) {
            AppLanguage.AR -> arabicLocale
            AppLanguage.EN -> Locale.ENGLISH
            AppLanguage.FR -> Locale.FRENCH
        }
        val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
        return SimpleDateFormat(pattern, locale).format(Date(timeMillis))
    }

    fun formatRemainingTimeAr(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = targetMillis - nowMillis
        if (diff <= 0) {
            val pastMinutes = abs(diff) / 60_000L
            return when {
                pastMinutes < 1 -> "حان الموعد الآن"
                pastMinutes < 60 -> "فات الموعد منذ $pastMinutes دقيقة"
                pastMinutes < 1440 -> "فات الموعد منذ ${pastMinutes / 60} ساعة"
                else -> "فات الموعد منذ ${pastMinutes / 1440} يوم"
            }
        }
        val totalMinutes = (diff + 59_999L) / 60_000L
        val days = totalMinutes / 1440
        val hours = (totalMinutes % 1440) / 60
        val minutes = totalMinutes % 60

        return when {
            days > 0 && hours > 0 -> "متبقٍ $days يوم و $hours ساعة"
            days > 0 -> "متبقٍ $days يوم"
            hours > 0 && minutes > 0 -> "متبقٍ $hours ساعة و $minutes دقيقة"
            hours > 0 -> "متبقٍ $hours ساعة"
            else -> "متبقٍ $minutes دقيقة"
        }
    }

    fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = millis1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = millis2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    fun isTomorrow(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return isSameDay(targetMillis, tomorrow.timeInMillis)
    }
}
