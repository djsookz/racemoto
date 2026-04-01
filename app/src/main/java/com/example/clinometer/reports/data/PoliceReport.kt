package com.example.clinometer.reports.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp

/**
 * Модел за доклад (полиция, камера, инцидент и др.)
 * Съхранява се в Firestore и се показва на картата
 */
data class PoliceReport(
    @DocumentId
    val id: String = "", // Firestore document ID
    
    val type: String = ReportType.POLICE.name, // Тип на доклада
    
    val location: GeoPoint = GeoPoint(0.0, 0.0), // Координати
    
    @ServerTimestamp
    val timestamp: Timestamp? = null, // Време на създаване (server-side)
    
    val createdAt: Timestamp? = null, // Копие на timestamp за MAX lifetime проверка
    
    val reporterUserId: String = "", // Anonymous user ID на докладващия
    
    val upvotes: Int = 0, // Брой потвърждения
    
    val downvotes: Int = 0, // Брой оспорвания
    
    val votedUserIds: List<String> = emptyList(), // User IDs на гласували (за limit 1 vote per user)
    
    val expiresAt: Timestamp? = null, // Време на изтичане (след 30-60 мин)
    
    val description: String? = null // Опционално описание
) {
    /**
     * Проверява дали докладът е изтекъл
     */
    fun isExpired(): Boolean {
        val expires = expiresAt ?: return false
        return expires.toDate().time < System.currentTimeMillis()
    }
    
    /**
     * Проверява дали User е гласувал вече
     */
    fun hasUserVoted(userId: String): Boolean {
        return votedUserIds.contains(userId)
    }
    
    /**
     * Качество на доклада (upvotes - downvotes)
     */
    fun getScore(): Int = upvotes - downvotes
    
    /**
     * Проверява дали докладът трябва да се изтрие автоматично
     * (много downvotes или изтекло време)
     */
    fun shouldBeRemoved(): Boolean {
        return isExpired() || getScore() < -2
    }
    
    /**
     * Изчислява максималното време на живот (2 часа от създаването)
     */
    fun getMaxLifetimeTimestamp(): Timestamp? {
        val created = createdAt ?: timestamp ?: return null
        val maxTime = created.toDate().time + (ReportType.MAX_LIFETIME_MINUTES * 60 * 1000)
        return Timestamp(java.util.Date(maxTime))
    }
    
    /**
     * Проверява дали репортът може да бъде удължен (не е достигнал MAX lifetime)
     */
    fun canBeExtended(): Boolean {
        val maxLifetime = getMaxLifetimeTimestamp() ?: return false
        return System.currentTimeMillis() < maxLifetime.toDate().time
    }
    
    /**
     * Получава типа на репорта като ReportType enum
     */
    fun getReportType(): ReportType {
        return ReportType.fromString(type) ?: ReportType.POLICE
    }
    
    /**
     * Преобразува в Map за запис във Firestore
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "type" to type,
            "location" to location,
            "timestamp" to timestamp,
            "createdAt" to (createdAt ?: timestamp),
            "reporterUserId" to reporterUserId,
            "upvotes" to upvotes,
            "downvotes" to downvotes,
            "votedUserIds" to votedUserIds,
            "expiresAt" to expiresAt,
            "description" to description
        )
    }
    
    companion object {
        /**
         * Създава PoliceReport от Firestore document
         */
        fun fromMap(id: String, data: Map<String, Any?>): PoliceReport? {
            return try {
                PoliceReport(
                    id = id,
                    type = data["type"] as? String ?: ReportType.POLICE.name,
                    location = data["location"] as? GeoPoint ?: GeoPoint(0.0, 0.0),
                    timestamp = data["timestamp"] as? Timestamp,
                    createdAt = data["createdAt"] as? Timestamp,
                    reporterUserId = data["reporterUserId"] as? String ?: "",
                    upvotes = (data["upvotes"] as? Long)?.toInt() ?: 0,
                    downvotes = (data["downvotes"] as? Long)?.toInt() ?: 0,
                    votedUserIds = (data["votedUserIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    expiresAt = data["expiresAt"] as? Timestamp,
                    description = data["description"] as? String
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
