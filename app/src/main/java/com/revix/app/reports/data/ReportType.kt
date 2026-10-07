package com.revix.app.reports.data

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.revix.app.R

/**
 * Типове доклади за съобщаване на карта
 */
enum class ReportType(
    @StringRes val displayNameResId: Int,
    @DrawableRes val iconResId: Int,
    val visibilityRadiusKm: Double, // Радиус на видимост в км
    val baseLifetimeMinutes: Int // Базово време на живот в минути
) {
    POLICE(R.string.report_type_police, R.drawable.police, 20.0, 45),
    CAMERA(R.string.report_type_camera, R.drawable.camera, 20.0, 60),
    ACCIDENT(R.string.report_type_accident, R.drawable.crash, 20.0, 30),
    HAZARD(R.string.report_type_hazard, R.drawable.danger, 20.0, 45),
    TRAFFIC(R.string.report_type_traffic, R.drawable.traffic, 20.0, 30),
    ROADWORK(R.string.report_type_roadwork, R.drawable.remont, 20.0, 90);

    fun getDisplayName(context: Context): String = context.getString(displayNameResId)
    
    /**
     * Изчислява бонус време според score
     */
    fun getScoreBonus(score: Int): Int {
        return when {
            score >= 11 -> 45  // Много надежден
            score >= 6 -> 30   // Надежден
            score >= 3 -> 15   // Потвърден
            else -> 0          // Нов или спорен
        }
    }
    
    /**
     * Изчислява общо време на живот (базово + score bonus)
     * @param score Текущ score на репорта
     * @param maxLifetimeMinutes Максимално допустимо време (default 120 мин)
     */
    fun getTotalLifetime(score: Int, maxLifetimeMinutes: Int = 120): Int {
        val total = baseLifetimeMinutes + getScoreBonus(score)
        return total.coerceAtMost(maxLifetimeMinutes)
    }
    
    companion object {
        fun fromString(value: String): ReportType? {
            return entries.find { it.name == value }
        }
        
        const val UPVOTE_REFRESH_MINUTES = 10 // Всеки upvote удължава с 10 мин
        const val MAX_LIFETIME_MINUTES = 120  // Максимум 2 часа жизнен цикъл
    }
}
