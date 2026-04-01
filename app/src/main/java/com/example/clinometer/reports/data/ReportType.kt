package com.example.clinometer.reports.data

/**
 * Типове доклади за съобщаване на карта
 */
enum class ReportType(
    val displayName: String, 
    val icon: String,
    val visibilityRadiusKm: Double, // Радиус на видимост в км
    val baseLifetimeMinutes: Int // Базово време на живот в минути
) {
    POLICE("Полиция", "🚔", 100.0, 45),      // Критично за магистрали
    CAMERA("Камера", "📹", 100.0, 60),        // Фиксирана позиция, дълго време
    ACCIDENT("Инцидент", "⚠️", 30.0, 30),    // Локален, краткосрочен
    HAZARD("Опасност", "⚡", 50.0, 45),       // Средна важност
    TRAFFIC("Трафик", "🚦", 50.0, 30),        // Градски проблем
    ROADWORK("Ремонт", "🚧", 50.0, 90);       // Дълготраен
    
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
