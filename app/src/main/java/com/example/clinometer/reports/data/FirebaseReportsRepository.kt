package com.example.clinometer.reports.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Repository за управление на доклади през Firebase Firestore
 * Имплементира CRUD операции, realtime sync, rate limiting и geo queries
 */
class FirebaseReportsRepository {

    data class CreateReportOutcome(
        val status: CreateReportStatus,
        val reportId: String? = null
    )

    enum class CreateReportStatus {
        CREATED,
        MERGED_UPVOTED,
        MERGED_ALREADY_VOTED,
        RATE_LIMIT_EXCEEDED,
        INVALID_LOCATION,
        ERROR
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val reportsCollection = firestore.collection("reports")
    
    companion object {
        private const val TAG = "ReportsRepository"
        private const val MAX_REPORTS_PER_HOUR = 10 // Rate limit
        private const val MAX_QUERY_RADIUS_KM = 150.0 // Увеличен радиус за магистрали
        private const val DEFAULT_MERGE_DISTANCE_METERS = 100.0
    }
    
    /**
     * Инициализира анонимен потребител ако няма
     */
    suspend fun ensureAuthenticated(): String {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            return currentUser.uid
        }
        
        return try {
            val result = auth.signInAnonymously().await()
            result.user?.uid ?: throw Exception("Failed to get user ID")
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            throw e
        }
    }
    
    /**
     * Създава нов доклад
     * Ако има същия тип доклад в зададения merge радиус, вместо нов запис прави auto-confirm (upvote)
     */
    suspend fun createReport(
        type: ReportType,
        latitude: Double,
        longitude: Double,
        mergeDistanceMeters: Double? = null,
        description: String? = null
    ): CreateReportOutcome {
        return try {
            val userId = ensureAuthenticated()
            val resolvedMergeDistanceMeters = (mergeDistanceMeters ?: DEFAULT_MERGE_DISTANCE_METERS)
                .coerceIn(20.0, 300.0)
            Log.d(
                TAG,
                "createReport type=${type.name} lat=$latitude lon=$longitude mergeRadius=${resolvedMergeDistanceMeters.toInt()}m"
            )

            // Валидация на разположение
            if (!isValidLocation(latitude, longitude)) {
                Log.w(TAG, "Invalid location: $latitude, $longitude")
                return CreateReportOutcome(CreateReportStatus.INVALID_LOCATION)
            }

            // Merge logic: ако вече има същия тип в зададения радиус, потвърждаваме него вместо нов marker
            val mergeTarget = findMergeTargetReport(
                type = type,
                latitude = latitude,
                longitude = longitude,
                mergeDistanceMeters = resolvedMergeDistanceMeters
            )
            if (mergeTarget != null) {
                Log.d(
                    TAG,
                    "createReport found merge target=${mergeTarget.id} within ${resolvedMergeDistanceMeters.toInt()}m for type=${type.name}"
                )
                if (mergeTarget.hasUserVoted(userId)) {
                    Log.d(TAG, "Merge target already voted by user: ${mergeTarget.id}")
                    return CreateReportOutcome(
                        status = CreateReportStatus.MERGED_ALREADY_VOTED,
                        reportId = mergeTarget.id
                    )
                }

                val merged = voteReport(mergeTarget.id, isUpvote = true)
                if (merged) {
                    Log.d(TAG, "Merged report into existing target: ${mergeTarget.id}")
                    return CreateReportOutcome(
                        status = CreateReportStatus.MERGED_UPVOTED,
                        reportId = mergeTarget.id
                    )
                }

                return CreateReportOutcome(
                    status = CreateReportStatus.MERGED_ALREADY_VOTED,
                    reportId = mergeTarget.id
                )
            }

            // Проверка за rate limiting важи само за чисто нови доклади
            if (!canUserCreateReport(userId)) {
                Log.w(TAG, "User $userId exceeded rate limit")
                return CreateReportOutcome(CreateReportStatus.RATE_LIMIT_EXCEEDED)
            }
            
            val now = Date()
            
            // Изчисляваме време на изтичане според типа на репорта (с score = 0)
            val lifetimeMinutes = type.getTotalLifetime(score = 0)
            val expiresAt = Calendar.getInstance().apply {
                time = now
                add(Calendar.MINUTE, lifetimeMinutes)
            }.time
            
            val report = PoliceReport(
                type = type.name,
                location = GeoPoint(latitude, longitude),
                reporterUserId = userId,
                upvotes = 0,
                downvotes = 0,
                votedUserIds = emptyList(),
                expiresAt = Timestamp(expiresAt),
                createdAt = Timestamp(now), // За MAX lifetime проверка
                description = description
            )
            
            val docRef = reportsCollection.add(report.toMap()).await()
            Log.d(TAG, "Report created: ${docRef.id} (expires in $lifetimeMinutes min)")
            CreateReportOutcome(
                status = CreateReportStatus.CREATED,
                reportId = docRef.id
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create report", e)
            CreateReportOutcome(CreateReportStatus.ERROR)
        }
    }

    private suspend fun findMergeTargetReport(
        type: ReportType,
        latitude: Double,
        longitude: Double,
        mergeDistanceMeters: Double
    ): PoliceReport? {
        return try {
            val mergeRadiusKm = mergeDistanceMeters / 1000.0
            val bounds = calculateBoundingBox(latitude, longitude, mergeRadiusKm)

            val snapshot = reportsCollection
                .whereGreaterThanOrEqualTo("location", GeoPoint(bounds.minLat, bounds.minLon))
                .whereLessThanOrEqualTo("location", GeoPoint(bounds.maxLat, bounds.maxLon))
                .get()
                .await()

            val candidates = snapshot.documents
                .mapNotNull { doc -> PoliceReport.fromMap(doc.id, doc.data ?: emptyMap()) }
                .filter { report ->
                    report.type == type.name && !report.shouldBeRemoved()
                }
                .mapNotNull { report ->
                    val distanceMeters = calculateDistance(
                        latitude,
                        longitude,
                        report.location.latitude,
                        report.location.longitude
                    ) * 1000.0

                    if (distanceMeters <= mergeDistanceMeters) {
                        report to distanceMeters
                    } else {
                        null
                    }
                }

            candidates
                .sortedWith(
                    compareBy<Pair<PoliceReport, Double>> { it.second }
                        .thenByDescending { it.first.getScore() }
                        .thenByDescending { it.first.timestamp?.toDate()?.time ?: 0L }
                )
                .firstOrNull()
                ?.first
        } catch (e: Exception) {
            Log.w(TAG, "Failed to evaluate merge target, continuing with normal create", e)
            null
        }
    }
    
    /**
     * Гласуване за доклад (upvote или downvote)
     * Upvote удължава живота на репорта (+10 мин, max 2 часа)
     */
    suspend fun voteReport(reportId: String, isUpvote: Boolean): Boolean {
        return try {
            val userId = ensureAuthenticated()
            val reportRef = reportsCollection.document(reportId)
            
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(reportRef)
                val report = PoliceReport.fromMap(reportId, snapshot.data ?: emptyMap())
                
                if (report == null || report.hasUserVoted(userId)) {
                    return@runTransaction false
                }
                
                val updatedVotedUsers = report.votedUserIds + userId
                val updates = mutableMapOf<String, Any>(
                    "votedUserIds" to updatedVotedUsers
                )
                
                val newScore: Int
                if (isUpvote) {
                    updates["upvotes"] = report.upvotes + 1
                    newScore = report.getScore() + 1
                    
                    // Upvote refresh механизъм: +10 мин ако score >= 0 и не е достигнат MAX
                    if (newScore >= 0 && report.canBeExtended()) {
                        val currentExpires = report.expiresAt?.toDate() ?: Date()
                        val refreshedExpires = Calendar.getInstance().apply {
                            time = currentExpires
                            add(Calendar.MINUTE, ReportType.UPVOTE_REFRESH_MINUTES)
                        }.time
                        
                        // Проверяваме да не надхвърляме MAX lifetime (2 часа)
                        val maxLifetime = report.getMaxLifetimeTimestamp()?.toDate() ?: refreshedExpires
                        val finalExpires = if (refreshedExpires.time > maxLifetime.time) {
                            maxLifetime
                        } else {
                            refreshedExpires
                        }
                        
                        updates["expiresAt"] = Timestamp(finalExpires)
                        Log.d(TAG, "Upvote extended report lifetime by ${ReportType.UPVOTE_REFRESH_MINUTES} min")
                    }
                } else {
                    updates["downvotes"] = report.downvotes + 1
                    newScore = report.getScore() - 1
                }
                
                // Обновяваме expiresAt според новия score bonus (само ако не е удължен от upvote)
                if (!isUpvote || newScore < 0) {
                    val reportType = report.getReportType()
                    val newLifetime = reportType.getTotalLifetime(newScore)
                    val createdAt = report.createdAt?.toDate() ?: report.timestamp?.toDate() ?: Date()
                    val newExpires = Calendar.getInstance().apply {
                        time = createdAt
                        add(Calendar.MINUTE, newLifetime)
                    }.time
                    
                    // Ако новият expiresAt е по-дълъг от сегашния - update
                    val currentExpires = report.expiresAt?.toDate()?.time ?: 0
                    if (newExpires.time > currentExpires && !updates.containsKey("expiresAt")) {
                        updates["expiresAt"] = Timestamp(newExpires)
                        Log.d(TAG, "Score bonus extended lifetime to $newLifetime min")
                    }
                }
                
                transaction.update(reportRef, updates)
                true
            }.await()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vote report", e)
            false
        }
    }
    
    /**
     * Слуша за доклади в радиус около дадена позиция (realtime)
     * @param centerLat Централна latitude
     * @param centerLon Централна longitude
     * @param radiusKm Радиус в километри
     */
    fun observeNearbyReports(
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double = 50.0
    ): Flow<List<PoliceReport>> = callbackFlow {
        // Ограничаваме радиуса
        val safeRadius = radiusKm.coerceAtMost(MAX_QUERY_RADIUS_KM)
        
        // Изчисляваме bounding box за GeoPoint query
        val bounds = calculateBoundingBox(centerLat, centerLon, safeRadius)
        
        val listener: ListenerRegistration = reportsCollection
            .whereGreaterThanOrEqualTo("location", GeoPoint(bounds.minLat, bounds.minLon))
            .whereLessThanOrEqualTo("location", GeoPoint(bounds.maxLat, bounds.maxLon))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing reports", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val reports = snapshot.documents.mapNotNull { doc ->
                        PoliceReport.fromMap(doc.id, doc.data ?: emptyMap())
                    }.filter { report ->
                        // Допълнителна филтрация: проверка за точен радиус според типа
                        val distance = calculateDistance(
                            centerLat, centerLon,
                            report.location.latitude, report.location.longitude
                        )
                        
                        // Всеки тип има собствен радиус на видимост
                        val reportType = report.getReportType()
                        val maxDistanceForType = reportType.visibilityRadiusKm
                        
                        distance <= maxDistanceForType && !report.shouldBeRemoved()
                    }
                    
                    trySend(reports)
                    Log.d(TAG, "Received ${reports.size} nearby reports (filtered by type radius)")
                }
            }
        
        awaitClose {
            listener.remove()
            Log.d(TAG, "Stopped observing reports")
        }
    }
    
    /**
     * Проверка дали потребителят може да създаде доклад (rate limiting)
     */
    private suspend fun canUserCreateReport(userId: String): Boolean {
        return try {
            val oneHourAgoMillis = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, -1)
            }.timeInMillis
            
            val userReports = reportsCollection
                .whereEqualTo("reporterUserId", userId)
                .get()
                .await()
            
            val count = userReports.documents.count { document ->
                val reportData = document.data ?: return@count false
                val report = PoliceReport.fromMap(document.id, reportData) ?: return@count false
                val createdAtMillis = report.createdAt?.toDate()?.time
                    ?: report.timestamp?.toDate()?.time
                    ?: return@count false
                createdAtMillis > oneHourAgoMillis
            }
            Log.d(TAG, "User $userId has $count reports in last hour")
            
            count < MAX_REPORTS_PER_HOUR
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check rate limit", e)
            true // В случай на грешка позволяваме създаването
        }
    }
    
    /**
     * Валидира координати
     */
    private fun isValidLocation(lat: Double, lon: Double): Boolean {
        return lat in -90.0..90.0 && lon in -180.0..180.0
    }
    
    /**
     * Изчислява разстояние между две точки (Haversine formula)
     * @return Разстояние в километри
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadiusKm * c
    }
    
    /**
     * Изчислява bounding box за geo query
     */
    private fun calculateBoundingBox(
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double
    ): BoundingBox {
        val latDelta = radiusKm / 111.0 // ~111 km per degree latitude
        val lonDelta = radiusKm / (111.0 * cos(Math.toRadians(centerLat)))
        
        return BoundingBox(
            minLat = centerLat - latDelta,
            maxLat = centerLat + latDelta,
            minLon = centerLon - lonDelta,
            maxLon = centerLon + lonDelta
        )
    }
    
    /**
     * Изтрива изтекли доклади (извиква се периодично)
     */
    suspend fun cleanupExpiredReports(): Int {
        return try {
            val now = Timestamp(Date())
            val expiredReports = reportsCollection
                .whereLessThan("expiresAt", now)
                .get()
                .await()
            
            val batch = firestore.batch()
            expiredReports.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            
            batch.commit().await()
            val count = expiredReports.size()
            Log.d(TAG, "Cleaned up $count expired reports")
            count
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup expired reports", e)
            0
        }
    }
    
    /**
     * Bounding box за geo queries
     */
    private data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )
}
