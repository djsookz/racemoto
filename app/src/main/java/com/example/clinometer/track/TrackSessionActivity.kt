package com.example.clinometer

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.asin
import kotlin.math.roundToInt
import kotlin.math.tan
import android.content.res.Configuration
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import com.example.clinometer.settings.SoundManager
import com.example.clinometer.settings.UnitsManager
import android.widget.LinearLayout
import com.example.clinometer.data.ProfileSessionSummaryStore
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.main.MainContainerActivity
import com.example.clinometer.track.catalog.TrackMode
import com.example.clinometer.track.session.TrackGateCrossingEngine
import com.example.clinometer.track.session.TrackLapTimingEngine
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

// Data class for storing lap data
data class LapData(
    val lapNumber: Int = 0,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val speedData: MutableList<Float> = mutableListOf(),
    val accelerationData: MutableList<Float> = mutableListOf(),
    val leanAngleData: MutableList<Float> = mutableListOf(),
    val gyroscopeData: MutableList<Float> = mutableListOf(),
    val routePoints: MutableList<RoutePoint> = mutableListOf(),
    val longitudinalGData: MutableList<Float> = mutableListOf(),
    val lateralGData: MutableList<Float> = mutableListOf(),
    val timestamps: MutableList<Long> = mutableListOf(),
    val displayLeanAngleData: MutableList<Float> = mutableListOf(),
    val maxBrakingData: MutableList<Float> = mutableListOf(),
    val maxAccelData: MutableList<Float> = mutableListOf(),
    val maxCorneringLeftData: MutableList<Float> = mutableListOf(),
    val maxCorneringRightData: MutableList<Float> = mutableListOf(),
    val maxResultGData: MutableList<Float> = mutableListOf(),
    val sensorData: MutableList<Any> = mutableListOf() // Placeholder - SDK handles sensor data
)

class TrackSessionActivity : BaseActivity(), SensorEventListener, LocationListener {
    override fun getLayoutResourceId(): Int = R.layout.activity_track_session
    override fun getNavigationItemId(): Int = R.id.navTrack
    private lateinit var tvCurrentLap: TextView
    private lateinit var tvSessionCurrentTimerValue: TextView
    private lateinit var tvSessionBestLapValue: TextView
    private lateinit var tvSessionBestLapMarker: TextView
    private lateinit var tvSessionLastLapValue: TextView
    private lateinit var progressLapDistance: ProgressBar
    private lateinit var topTelemetryRow: LinearLayout
    private lateinit var cardTopSpeedTelemetry: View
    private lateinit var tvTopSpeedValue: TextView
    private lateinit var tvTopMaxSpeedValue: TextView
    private lateinit var tvTopAvgSpeedValue: TextView
    private lateinit var llTopSpeedMotoBody: View
    private lateinit var rlTopSpeedCarBody: View
    private lateinit var tvTopSpeedValueCar: TextView
    private lateinit var tvTopMaxSpeedValueCar: TextView
    private lateinit var tvTopAvgSpeedValueCar: TextView
    private lateinit var cardTopLeanTelemetry: View
    private lateinit var leanVisualizer: LeanVisualizerView
    private lateinit var tvTopLeanValue: TextView
    private lateinit var tvTopLeanDirection: TextView
    private lateinit var cardPredictiveLap: View
    private lateinit var tvPredictiveReference: TextView
    private lateinit var btnPredictiveGapMode: MaterialButton
    private lateinit var tvPredictiveGapSignValue: TextView
    private lateinit var tvPredictiveGapValue: TextView
    private lateinit var motoGForceContainer: View
    private lateinit var tvMotoBrakingValue: TextView
    private lateinit var tvMotoAccelValue: TextView
    private lateinit var tvMotoMaxBrakingValue: TextView
    private lateinit var tvMotoMaxAccelValue: TextView
    private lateinit var tvMotoGHeader: TextView
    private lateinit var tvMotoTotalLabel: TextView
    private lateinit var tvMotoLeftFooterLabel: TextView
    private lateinit var tvMotoRightFooterLabel: TextView
    private lateinit var tvMotoTotalValue: TextView
    private lateinit var llMotoTotalValue: View
    private lateinit var pbMotoBraking: ProgressBar
    private lateinit var pbMotoAccel: ProgressBar
    private lateinit var llMotoBody: View
    private lateinit var carGForceLayout: View
    private lateinit var gGaugeTrackCar: GGaugeView
    private lateinit var carGScaleControls: View
    private lateinit var tvCarLateralLeftValue: TextView
    private lateinit var tvCarLateralRightValue: TextView
    private lateinit var tvCarBrakingValue: TextView
    private lateinit var tvCarAccelValue: TextView
    private lateinit var tvCarTotalValue: TextView
    private lateinit var pbCarLateralLeft: ProgressBar
    private lateinit var pbCarLateralRight: ProgressBar
    private lateinit var pbCarBraking: ProgressBar
    private lateinit var pbCarAccel: ProgressBar
    private lateinit var rlMotoAxis: View
    private lateinit var tvMotoAxisBrakeLabel: TextView
    private lateinit var tvMotoAxisAccelLabel: TextView
    private lateinit var viewMotoAxisLine: View
    private lateinit var viewMotoTickTop: View
    private lateinit var viewMotoTickMid: View
    private lateinit var viewMotoTickBottom: View
    private lateinit var viewMotoLongitudinalDot: View
    private lateinit var gGaugeTrack: GGaugeView
    private lateinit var cardCenterTelemetry: View
    private lateinit var flCenterTelemetry: View
    private lateinit var speedGauge: SpeedGaugeView
    private lateinit var cardCameraPreview: View
    private lateinit var cameraPreviewView: PreviewView
    private lateinit var llCameraPreviewHeader: View
    private lateinit var llCameraPreviewPlaceholder: View
    private lateinit var tvCameraPreviewPlaceholder: TextView
    private lateinit var tvCameraPreviewStatus: TextView
    private lateinit var btnCameraModeInline: MaterialButton
    private lateinit var tvLapTime: TextView
    private lateinit var llLapsContainer: LinearLayout
    private lateinit var tvNoLaps: TextView
    private lateinit var telemetryGapSpacer: View
    private lateinit var btnStartStop: MaterialButton
    private lateinit var btnLap: MaterialButton
    private lateinit var btnCameraMode: MaterialButton
    private lateinit var btnTopLeanZero: MaterialButton
    private var tvTrackWeatherTemp: TextView? = null
    private var tvTrackWeatherHumidity: TextView? = null
    private var trackId: String = ""
    private var trackName: String = ""
    private var isMotorcycle: Boolean = true
    private var isRecording: Boolean = false
    private var currentLap: Int = 0
    private var lapStartTime: Long = 0
    private var sectorStartTime: Long = 0
    private var currentSector: Int = 0
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var rotationVector: Sensor? = null
    private var geomagneticRotationVector: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var magnetometer: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private lateinit var locationManager: LocationManager
    private val gyroscopeData = mutableListOf<Float>()
    private val speedData = mutableListOf<Float>()
    private var sessionCameraMode = SessionCameraMode.OFF
    private var pendingSessionCameraMode: SessionCameraMode? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraVideoCapture: VideoCapture<Recorder>? = null
    private var activeVideoRecording: Recording? = null
    private var isVideoRecordingActive = false
    private var sessionVideoRawFile: File? = null
    private var sessionVideoFinalFile: File? = null
    private var videoRecordingStartElapsedRealtimeMs: Long = 0L
    private var videoRecordingStartWallTimeMs: Long = 0L
    private var videoSyncMarkerOffsetMs: Long? = null
    private var savedSessionVideoUri: String? = null
    private var savedSessionVideoPath: String? = null
    private var savedSessionVideoCameraLabel: String? = null
    private var savedSessionVideoStartOffsetMs: Long? = null
    private var savedSessionVideoStartSessionElapsedMs: Long? = null
    private var savedSessionVideoOverlayExported = false
    private var pendingCreateOutingAfterVideoFinalize = false
    private var pendingDiscardVideoAfterFinalize = false
    private var sessionTelemetryStartWallTimeMs: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var trackWakeLock: PowerManager.WakeLock? = null
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDisplay()
            handler.postDelayed(this, 100)
        }
    }
    private val trackPoints = mutableListOf<TrackPoint>()
    private val trackPointTypes = mutableListOf<com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType>() // Types of each point
    private val startFinishLineIndices = mutableListOf<Int>() // Indices of start/finish line points
    private val gateCrossingEngine = TrackGateCrossingEngine(lineThresholdMeters = 30.0)
    private val lapTimingEngine = TrackLapTimingEngine(minLapTimeMs = 10_000L)
    private var currentTrackPointIndex = 0
    private var lastLocation: Location? = null
    private val accelerationData = mutableListOf<Float>()
    private val leanAngleData = mutableListOf<Float>()
    
    // Sound manager for track events
    private lateinit var soundManager: SoundManager
    
    // Lap data storage
    private val lapData = mutableListOf<LapData>()
    private var currentLapData = LapData()
    private var sessionStartTime: Long = 0L
    private var sessionEndTime: Long = 0L
    private val lapTimes = mutableListOf<Long>()
    private var totalLaps = 0
    private var bestLapTime: Long = Long.MAX_VALUE
    private var bestLapNumber: Int = 0
    private var trackBestLapTime: Long = Long.MAX_VALUE
    private var trackBestLapNumber: Int = 0
    private var currentLapTime: Long = 0
    private val sectorTimes = mutableListOf<Long>() // Current lap sector times
    private val bestSectorTimes = mutableListOf<Long>() // Sector times from best LAP (not theoretical)
    private var sectorDistanceAccum: Float = 0f // meters traveled in current sector
    private var lapDistanceAccum: Float = 0f // meters traveled in current lap
    private val sectorDistances = mutableListOf<Float>() // Current lap sector distances (meters)
    private val bestSectorDistances = mutableListOf<Float>() // Sector distances from best lap (meters)
    private var bestLapDistance: Float = 0f // meters (sum of best lap sector distances)
    private enum class PredictiveGapSource {
        SESSION_BEST,
        TRACK_BEST
    }
    private var predictiveGapSource: PredictiveGapSource = PredictiveGapSource.SESSION_BEST
    private var lastSectorChangeAtMs: Long = 0L
    private var lastPredictedLapSeconds: Float = Float.NaN
    private var displayedPredictedLapSeconds: Float = Float.NaN
    private val sectorCrossFreezeMs: Long = 400
    private val speedWindowMs: Long = 2000
    private val speedSamplesMs = ArrayDeque<Pair<Long, Float>>() // (timestamp, speed m/s)
    private var awaitingStart: Boolean = false
    private var awaitingStartDialog: androidx.appcompat.app.AlertDialog? = null
    private var awaitingStartMessageView: TextView? = null
    private var lastLocationTimeMs: Long = 0L
    private var lastPredictionDisplayUpdateMs: Long = 0L
    private var lastPredictionComputeAtMs: Long = 0L
    private val predictionDisplayIntervalMs: Long = 1000L
    private val startProximityMeters: Float = 20f  // Must pass very close to start/finish to begin
    private val sectorProximityMeters: Float = 50f
    private var currentTrackMode: TrackMode = TrackMode.CIRCUIT
    private var maxSpeed: Float = 0f
    private var sessionSpeedSumKmh: Float = 0f
    private var sessionSpeedSamples: Int = 0
    private var maxAcceleration: Float = 0f
    private var maxBraking: Float = 0f
    private var maxCorneringLeftG: Float = 0f
    private var maxCorneringRightG: Float = 0f
    private var maxCarResultG: Float = 0f
    private var maxLeanAngle: Float = 0f
    private var maxLeanLeftAngle: Float = 0f
    private var maxLeanRightAngle: Float = 0f
    private var displayLeanAngle: Float = 0f
    private var hasDisplayLeanAngle: Boolean = false
    private val leanDisplayDeadbandDeg: Float = 1.8f
    private val leanDisplayDirectionThresholdDeg: Float = 2.2f
    private val leanDisplaySmoothingAlpha: Float = 0.16f
    private val leanDisplaySmoothingAlphaGyro: Float = 0.11f
    private val forceNoGyroLeanLogicOnGyro: Boolean = false
    private val leanDisplaySnapToZeroDeg: Float = 0.25f
    private var previousLocationForCrossing: Location? = null
    private var lastStartFinishCrossAtMs: Long = 0L
    private val startFinishCrossDebounceMs: Long = 1500L
    private val pointToPointStartHintMeters: Double = 1500.0
    private val synthesizedGateWidthMeters = 12.0
    private val minimumUsableGateLengthMeters = 2.0f
    private var startForwardFilteredMs2: Float = 0f
    private var startLateralFilteredMs2: Float = 0f
    private var startDirectionGoodSamples: Int = 0
    private val startDirectionFilterAlpha = 0.28f
    private val startDirectionMinForwardMs2 = 0.26f
    private val startDirectionRatio = 1.55f
    private val startDirectionRequiredSamples = 3
    private var trackLengthMeters: Float = 0f
    private var currentDistanceToLapLineMeters: Float = Float.NaN
    private var currentDistanceToStartLineMeters: Float = Float.NaN

    private enum class TriggerGateRole {
        CIRCUIT_START_FINISH,
        START,
        FINISH
    }

    private enum class SessionCameraMode(
        val lensFacing: Int?,
        val labelResId: Int
    ) {
        OFF(null, R.string.track_camera_menu_off),
        REAR(CameraSelector.LENS_FACING_BACK, R.string.track_camera_label_rear),
        FRONT(CameraSelector.LENS_FACING_FRONT, R.string.track_camera_label_front)
    }

    private data class ResolvedGateLine(
        val start: GeoPoint,
        val end: GeoPoint
    )
    private var currentDistanceToFinishLineMeters: Float = Float.NaN
    private val progressRoutePoints = mutableListOf<GeoPoint>()
    private val progressRouteCumulativeMeters = mutableListOf<Float>()
    private var progressRouteLengthMeters: Float = 0f
    private var currentProjectedRouteDistanceMeters: Float = Float.NaN
    private var projectedRouteDistanceAtLapStartMeters: Float = Float.NaN
    private var smoothedLapProgress: Float = 0f
    private var lastProjectedSegmentIndex: Int = 0
    private var lastProjectedAlongMeters: Float = Float.NaN
    private var lastLapProgressUpdateNs: Long = 0L
    private val lapProgressMax = 1000
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val CAMERA_PERMISSION_REQUEST = 1002
        private const val MIN_DISTANCE_FOR_UPDATE = 1f
        private const val MIN_TIME_FOR_UPDATE = 100L
        private const val TRACK_UI_PREFS = "track_ui_prefs"
        private const val SESSION_VIDEO_PREROLL_MS = 3_000L
    }
    private val gravity = FloatArray(3) { 0f }
    private val gravitySensorValues = FloatArray(3) { 0f }
    private var gravitySensorTimestampNs: Long = 0L
    private val magneticFieldValues = FloatArray(3) { 0f }
    private var magneticFieldTimestampNs: Long = 0L
    private val latestRawAccel = FloatArray(3) { 0f }
    private val linearAccel = FloatArray(3) { 0f }
    private val linearAccelSensorValues = FloatArray(3) { 0f }
    private var hasLinearAccelSensorSample = false
    private var linearAccelSensorTimestampNs: Long = 0L
    private var noGyroLinearSensorBlend = 0.50f
    private val alphaGravity = 0.8f
    // Drag-compatible no-gyro gravity LP used for Track parity.
    private val dragCompatGravity = FloatArray(3) { 0f }
    private val dragCompatGravityAlpha = 0.8f
    private val noGyroGravityFromSensorBlend = 0.70f
    private val noGyroGravityAlpha = 0.88f
    private val noGyroLinearSensorMaxAgeNs = 120_000_000L
    private val gravitySensorMaxAgeNs = 220_000_000L
    private val accelMagRotationMaxSkewNs = 180_000_000L
    private val minNoGyroLinearBlend = 0.22f
    private val maxNoGyroLinearBlend = 0.68f
    // No-gyro gravity freeze: pause gravity LP updates during real acceleration
    // so the filter doesn't absorb real G into the gravity estimate.
    private var noGyroGravityFrozen = false
    private var noGyroFreezeCounter = 0
    private val noGyroFreezeCountThreshold = 3
    private var noGyroCalGravityMag = SensorManager.GRAVITY_EARTH
    private var noGyroFreezeThreshold = 0.45f
    private val madgwick = MadgwickAHRS(beta = 0.033f)
    private val latestGyroForMadgwick = FloatArray(3)
    private var lastMadgwickUpdateNs: Long = 0L
    private val rotationMatrix = FloatArray(9) { 0f }
    private val worldAccel = FloatArray(3) { 0f }
    private var displayLX = 0f
    private var displayLY = 0f
    private var currentLongitudinalG = 0f
    private var currentLateralG = 0f
    private val maxDisplayG = 3.0f
    // Heading smoothing for projecting world accel into vehicle frame
    private var hasSmoothedBearing = false
    private var smoothedBearingRad = 0f
    private val bearingAlpha = 0.2f
    // GPS-based G-force for no-gyro devices (vibration-immune kinematics)
    private var gpsLongG = 0f
    private var gpsLatG = 0f
    private var gpsSmoothedLongG = 0f
    private var gpsSmoothedLatG = 0f
    private var gpsGTimeMs = 0L
    private var prevGpsSpeedMs = Float.NaN
    private var prevGpsBearingRad = Float.NaN
    private var prevGpsFixTimeMs = 0L
    private var hasGpsGForce = false
    private var noGyroGpsLongStatsSmooth = 0f
    private var noGyroLeanLatGSmooth = 0f
    // Stationary bias removal and deadband
    private var forwardBiasG = 0f
    private var lateralBiasG = 0f
    private var longSignMultiplier = 1f
    private var longSignMismatchStreak = 0
    private val biasAlpha = 0.02f
    private val deadbandG = 0.05f
    // Prefer hardware linear acceleration if available
    private var preferLinearAccel = false
    // Stationary detection
    private var stationaryCounter = 0
    private var isStationary = false
    private val stationaryAccThreshold = 0.15f // m/s^2
    private val stationaryCountToLock = 8
    // Signal smoothing
    private var forwardGSmooth = 0f
    private var lateralGSmooth = 0f
    private val gSmoothAlpha = 0.3f
    private var statsFilteredLongG = 0f
    private var statsFilteredLatG = 0f
    private val statsFilterAlpha = 0.35f
    private val statsDeadbandG = 0.06f
    private val minConfidenceForStats = 0.25f
    private val confidenceLowPassAlpha = 0.15f
    private var smoothedConfidence = 1f
    private var lastGyroMagnitude = 0f
    private var accelTimestampNs: Long = 0L
    private var rotationTimestampNs: Long = 0L
    private var gyroTimestampNs: Long = 0L
    private var leanGyroIntegrationTimestampNs: Long = 0L
    private var worldAccelTimestampNs: Long = 0L
    private val worldFusionAlpha = 0.35f
    private val fusedWorldAccel = FloatArray(3) { 0f }
    private var hasFusedWorldAccel = false
    private val peakEntryHysteresisG = 0.03f
    private val peakExitHysteresisG = 0.015f
    private val peakHoldMs = 120L
    private data class PeakDetector(
        var committed: Float = 0f,
        var candidate: Float = 0f,
        var candidateSinceMs: Long = 0L
    )
    private val accelerationPeakDetector = PeakDetector()
    private val brakingPeakDetector = PeakDetector()
    private val corneringLeftPeakDetector = PeakDetector()
    private val corneringRightPeakDetector = PeakDetector()
    // Lean angle fusion state (gyro + accel reference)
    private var filteredAngle: Float = 0f
    private var offsetAngle: Float = 0f
    private var currentCalibratedLean: Float = 0f
    private var latestRollRateDegPerSec: Float = 0f
    private var gyroIntegratedLeanDeg: Float = 0f
    private var hasGyroIntegratedLean: Boolean = false
    private var selectedProfileId: Long = -1L
    private var runtimeLeanOffsetDeg: Float = 0f
    private var profileLeanOffsetDeg: Float = 0f
    private var hasProfileLeanOffset: Boolean = false
    private var lastLeanOrientationLandscape: Boolean? = null
    private var leanAutoZeroPending: Boolean = false
    private var leanAutoZeroAccumDeg: Float = 0f
    private var leanAutoZeroSampleCount: Int = 0
    private var leanCalibrationSnapshot: LeanCalibrationSnapshot = LeanCalibrationSnapshot()
    private val gyroBiasRad = FloatArray(3) { 0f }
    private var hasGyroBiasCompensation: Boolean = false
    private var hasSmartMotionCalibration: Boolean = false
    private val radToDeg = 57.29578f
    private val minAccelCorrection = 0.03f
    private val maxAccelCorrection = 0.22f
    private val leanAutoZeroMaxAbsTiltDeg = 22f
    private val leanAutoZeroMaxRollRateDegPerSec = 4.0f
    private val leanAutoZeroMaxWorldLinearAccMs2 = 0.40f
    private val leanAutoZeroRequiredSamples = 8
    private val noGyroDeadbandScale = 0.52f
    private val noGyroGScaleFloor = 0.86f
    private val noGyroDisplayAlphaMin = 0.40f
    private val noGyroDisplayAlphaRange = 0.42f
    private val noGyroGSmoothAlpha = 0.52f
    private val noGyroBiasLearnAlphaScale = 0.30f
    private val noGyroBiasCompensationBase = 0.46f
    private val noGyroBiasCompensationRange = 0.26f
    private val noGyroLowGBoostMax = 1.22f
    private val noGyroLowGBoostRangeG = 0.28f
    private var noGyroDeadbandScaleRuntime = noGyroDeadbandScale
    private var noGyroGScaleFloorRuntime = noGyroGScaleFloor
    private var noGyroDisplayAlphaMinRuntime = noGyroDisplayAlphaMin
    private var noGyroDisplayAlphaRangeRuntime = noGyroDisplayAlphaRange
    private var noGyroGSmoothAlphaRuntime = noGyroGSmoothAlpha
    private var noGyroBiasLearnAlphaScaleRuntime = noGyroBiasLearnAlphaScale
    private var noGyroBiasCompensationBaseRuntime = noGyroBiasCompensationBase
    private var noGyroBiasCompensationRangeRuntime = noGyroBiasCompensationRange
    private var noGyroLowGBoostMaxRuntime = noGyroLowGBoostMax
    private var noGyroLowGBoostRangeGRuntime = noGyroLowGBoostRangeG
    private val carGaugeBaseVisualMaxG = 1.5f
    private val carGaugeVisualStepG = 0.3f
    private var carGaugeDynamicMaxG = carGaugeBaseVisualMaxG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBarsPaddingToRoot()
        
        // ✅ КРИТИЧНО: Инициализираме DragCalibration и задаваме профила
        // Това е необходимо за да работи калибрацията правилно
        DragCalibration.init(this)
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        selectedProfileId = currentProfileId
        DragCalibration.setProfile(currentProfileId)
        android.util.Log.d("TrackSessionActivity", "🔧 DragCalibration initialized for profile $currentProfileId, isUniversalCalibrated=${DragCalibration.isUniversalCalibrated}")
        
        // Initialize sound manager
        soundManager = SoundManager(this)
        
        trackId = intent.getStringExtra("track_id") ?: ""
        trackName = intent.getStringExtra("track_name") ?: "Track"
        val selectedProfile = ProfileStorage.loadProfiles(this).find { it.id == currentProfileId }
        val hasProfileVehicleType = selectedProfile != null
        val profileIsMotorcycle = selectedProfile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        val hasIntentVehicleMode = intent.hasExtra("is_motorcycle")
        val hasTrackIntentVehicleMode = intent.hasExtra("track_is_motorcycle")
        val intentIsMotorcycle = intent.getBooleanExtra("is_motorcycle", true)
        val trackIntentIsMotorcycle = intent.getBooleanExtra("track_is_motorcycle", true)

        isMotorcycle = when {
            hasProfileVehicleType -> profileIsMotorcycle
            hasIntentVehicleMode -> intentIsMotorcycle
            hasTrackIntentVehicleMode -> trackIntentIsMotorcycle
            else -> true
        }

        if (hasIntentVehicleMode && hasTrackIntentVehicleMode && intentIsMotorcycle != trackIntentIsMotorcycle) {
            Log.w(
                "TrackSessionActivity",
                "Mismatched vehicle extras (is_motorcycle=$intentIsMotorcycle, track_is_motorcycle=$trackIntentIsMotorcycle); using is_motorcycle"
            )
        }

        if (hasIntentVehicleMode && hasProfileVehicleType && intentIsMotorcycle != profileIsMotorcycle) {
            Log.w(
                "TrackSessionActivity",
                "Mismatched vehicle mode (intent=$intentIsMotorcycle, profile=$profileIsMotorcycle); using profile mode"
            )
        }

        if (hasTrackIntentVehicleMode && hasProfileVehicleType && trackIntentIsMotorcycle != profileIsMotorcycle) {
            Log.w(
                "TrackSessionActivity",
                "Mismatched vehicle mode (track_intent=$trackIntentIsMotorcycle, profile=$profileIsMotorcycle); using profile mode"
            )
        }
        reloadLeanCalibrationForProfile(currentProfileId, forceResetRuntime = true)
        reloadMotionCalibrationForProfile(currentProfileId)
        val isResumeSession = intent.getBooleanExtra("resume_session", false)
        val sessionId = intent.getStringExtra("session_id") ?: ""
        initializeViews()
        setupClickListeners()
        setupSensors()
        loadTrackData()  // ✅ CRITICAL: Load track data BEFORE starting location updates
        loadTrackBestLapReference()
        applyPredictiveGapSourceUi()
        setupLocation()
        if (isResumeSession && sessionId.isNotEmpty()) {
            // For resume sessions, we don't need to clear active session
            // The session will continue with the existing sessionId
            // Do NOT auto-start. Wait for user to press Start (same as New Session)
        } else {
            android.util.Log.d("TrackSessionActivity", "🆕 NEW SESSION: clearing active session")
            // For new sessions, clear any existing active session
            clearActiveSession()
        }
    }
    private fun initializeViews() {
        tvCurrentLap = findViewById(R.id.tvCurrentLap)
        tvSessionCurrentTimerValue = findViewById(R.id.tvSessionCurrentTimerValue)
        tvSessionBestLapValue = findViewById(R.id.tvSessionBestLapValue)
        tvSessionBestLapMarker = findViewById(R.id.tvSessionBestLapMarker)
        tvSessionLastLapValue = findViewById(R.id.tvSessionLastLapValue)
        progressLapDistance = findViewById(R.id.progressLapDistance)
        topTelemetryRow = findViewById(R.id.topTelemetryRow)
        cardTopSpeedTelemetry = findViewById(R.id.cardTopSpeedTelemetry)
        llTopSpeedMotoBody = findViewById(R.id.llTopSpeedMotoBody)
        rlTopSpeedCarBody = findViewById(R.id.rlTopSpeedCarBody)
        tvTopSpeedValue = findViewById(R.id.tvTopSpeedValue)
        tvTopMaxSpeedValue = findViewById(R.id.tvTopMaxSpeedValue)
        tvTopAvgSpeedValue = findViewById(R.id.tvTopAvgSpeedValue)
        tvTopSpeedValueCar = findViewById(R.id.tvTopSpeedValueCar)
        tvTopMaxSpeedValueCar = findViewById(R.id.tvTopMaxSpeedValueCar)
        tvTopAvgSpeedValueCar = findViewById(R.id.tvTopAvgSpeedValueCar)
        cardTopLeanTelemetry = findViewById(R.id.cardTopLeanTelemetry)
        leanVisualizer = findViewById(R.id.leanVisualizer)
        tvTopLeanValue = findViewById(R.id.tvTopLeanValue)
        tvTopLeanDirection = findViewById(R.id.tvTopLeanDirection)
        cardPredictiveLap = findViewById(R.id.cardPredictiveLap)
        tvPredictiveReference = findViewById(R.id.tvPredictiveReference)
        btnPredictiveGapMode = findViewById(R.id.btnPredictiveGapMode)
        tvPredictiveGapSignValue = findViewById(R.id.tvPredictiveGapSignValue)
        tvPredictiveGapValue = findViewById(R.id.tvPredictiveGapValue)
        motoGForceContainer = findViewById(R.id.motoGForceContainer)
        tvMotoBrakingValue = findViewById(R.id.tvMotoBrakingValue)
        tvMotoAccelValue = findViewById(R.id.tvMotoAccelValue)
        tvMotoMaxBrakingValue = findViewById(R.id.tvMotoMaxBrakingValue)
        tvMotoMaxAccelValue = findViewById(R.id.tvMotoMaxAccelValue)
        tvMotoGHeader = findViewById(R.id.tvMotoGHeader)
        tvMotoTotalLabel = findViewById(R.id.tvMotoTotalLabel)
        tvMotoLeftFooterLabel = findViewById(R.id.tvMotoLeftFooterLabel)
        tvMotoRightFooterLabel = findViewById(R.id.tvMotoRightFooterLabel)
        tvMotoTotalValue = findViewById(R.id.tvMotoTotalValue)
        llMotoTotalValue = findViewById(R.id.llMotoTotalValue)
        pbMotoBraking = findViewById(R.id.pbMotoBraking)
        pbMotoAccel = findViewById(R.id.pbMotoAccel)
        llMotoBody = findViewById(R.id.llMotoBody)
        carGForceLayout = findViewById(R.id.carGForceLayout)
        gGaugeTrackCar = findViewById(R.id.gGaugeTrackCar)
        carGScaleControls = findViewById(R.id.carGScaleControls)
        tvCarLateralLeftValue = findViewById(R.id.tvCarLateralLeftValue)
        tvCarLateralRightValue = findViewById(R.id.tvCarLateralRightValue)
        tvCarBrakingValue = findViewById(R.id.tvCarBrakingValue)
        tvCarAccelValue = findViewById(R.id.tvCarAccelValue)
        tvCarTotalValue = findViewById(R.id.tvCarTotalValue)
        pbCarLateralLeft = findViewById(R.id.pbCarLateralLeft)
        pbCarLateralRight = findViewById(R.id.pbCarLateralRight)
        pbCarBraking = findViewById(R.id.pbCarBraking)
        pbCarAccel = findViewById(R.id.pbCarAccel)
        rlMotoAxis = findViewById(R.id.rlMotoAxis)
        tvMotoAxisBrakeLabel = findViewById(R.id.tvMotoAxisBrakeLabel)
        tvMotoAxisAccelLabel = findViewById(R.id.tvMotoAxisAccelLabel)
        viewMotoAxisLine = findViewById(R.id.viewMotoAxisLine)
        viewMotoTickTop = findViewById(R.id.viewMotoTickTop)
        viewMotoTickMid = findViewById(R.id.viewMotoTickMid)
        viewMotoTickBottom = findViewById(R.id.viewMotoTickBottom)
        viewMotoLongitudinalDot = findViewById(R.id.viewMotoLongitudinalDot)
        gGaugeTrack = findViewById(R.id.gGaugeTrack)
        cardCenterTelemetry = findViewById(R.id.cardCenterTelemetry)
        flCenterTelemetry = findViewById(R.id.flCenterTelemetry)
        speedGauge = findViewById(R.id.speedGauge)
        cardCameraPreview = findViewById(R.id.cardCameraPreview)
        cameraPreviewView = findViewById(R.id.cameraPreviewView)
        llCameraPreviewHeader = findViewById(R.id.llCameraPreviewHeader)
        llCameraPreviewPlaceholder = findViewById(R.id.llCameraPreviewPlaceholder)
        tvCameraPreviewPlaceholder = findViewById(R.id.tvCameraPreviewPlaceholder)
        tvCameraPreviewStatus = findViewById(R.id.tvCameraPreviewStatus)
        btnCameraModeInline = findViewById(R.id.btnCameraModeInline)
        tvLapTime = findViewById(R.id.tvLapTime)
        llLapsContainer = findViewById(R.id.llLapsContainer)
        tvNoLaps = findViewById(R.id.tvNoLaps)
        telemetryGapSpacer = findViewById(R.id.telemetryGapSpacer)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnLap = findViewById(R.id.btnLap)
        btnCameraMode = findViewById(R.id.btnCameraMode)
        btnTopLeanZero = findViewById(R.id.btnTopLeanZero)
        tvTrackWeatherTemp = findViewById(R.id.tvTrackWeatherTemp)
        tvTrackWeatherHumidity = findViewById(R.id.tvTrackWeatherHumidity)
        enforceDpTextSizes(findViewById(android.R.id.content))
        topTelemetryRow.visibility = View.VISIBLE
        cardPredictiveLap.visibility = View.VISIBLE
        cardTopLeanTelemetry.visibility = if (isMotorcycle) View.VISIBLE else View.GONE
        btnTopLeanZero.visibility = if (isMotorcycle) View.VISIBLE else View.GONE
        resetCarGaugeDynamicScale()
        configureTelemetryProfileUi()
        // Keep one authoritative G-force surface from XML for all profiles.
        motoGForceContainer.visibility = View.VISIBLE
        speedGauge.visibility = View.GONE
        resetPredictiveGapCard()
        updateCurrentLapBadge(0)
        progressLapDistance.max = lapProgressMax
        updateLapDistanceProgress(0f)
        updateTopSpeedTelemetry(0f)
        updateTopLeanTelemetry(0f)
        updateMotoGForceCard()
        updateLapSummaryCards()
        updateTopRightWeatherHeader()
        updateCameraButtonUi()
        updateCameraPreviewCardVisibility()
    }

    private fun enforceDpTextSizes(root: View?) {
        if (root == null) return
        val pixelsPerSp = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            1f,
            resources.displayMetrics
        )
        if (pixelsPerSp <= 0f) return

        when (root) {
            is TextView -> {
                val sizeInSp = root.textSize / pixelsPerSp
                root.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeInSp)
            }
            is ViewGroup -> {
                for (index in 0 until root.childCount) {
                    enforceDpTextSizes(root.getChildAt(index))
                }
            }
        }
    }

    private fun dpToPx(valueDp: Float): Int {
        return (valueDp * resources.displayMetrics.density).roundToInt()
    }

    private fun updateTopRightWeatherHeader() {
        val tempView = tvTrackWeatherTemp ?: return
        val humidityView = tvTrackWeatherHumidity ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val cachedTemp = prefs.getFloat("cached_temperature", Float.NaN)
        val cachedHumidity = prefs.getInt("cached_humidity", -1)

        tempView.text = if (!cachedTemp.isNaN()) {
            "TEMP ${UnitsManager.formatTemperature(cachedTemp, this, decimals = 0)}"
        } else {
            val unit = UnitsManager.getTemperatureUnit(this)
            "TEMP --${unit.symbol}"
        }

        humidityView.text = if (cachedHumidity in 0..100) {
            "HUM ${cachedHumidity}%"
        } else {
            "HUM --%"
        }
    }

    private fun configureTelemetryProfileUi() {
        val cardSpacingPx = dpToPx(8f)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val speedParams = cardTopSpeedTelemetry.layoutParams as LinearLayout.LayoutParams
        if (!isLandscape && isMotorcycle) {
            speedParams.width = 0
            speedParams.weight = 1.05f
            speedParams.marginEnd = cardSpacingPx
        } else if (!isLandscape) {
            speedParams.width = 0
            speedParams.weight = 1.10f
            speedParams.marginEnd = cardSpacingPx
        }
        cardTopSpeedTelemetry.layoutParams = speedParams

        val predictiveParams = cardPredictiveLap.layoutParams as LinearLayout.LayoutParams
        if (!isLandscape) {
            predictiveParams.width = 0
            predictiveParams.height = dpToPx(if (isMotorcycle) 126f else 96f)
            predictiveParams.weight = if (isMotorcycle) 1.05f else 1f
            predictiveParams.marginEnd = if (isMotorcycle) cardSpacingPx else 0
        }
        cardPredictiveLap.layoutParams = predictiveParams

        val leanParams = cardTopLeanTelemetry.layoutParams as LinearLayout.LayoutParams
        if (!isLandscape) {
            leanParams.width = 0
            leanParams.weight = 0.95f
        }
        cardTopLeanTelemetry.layoutParams = leanParams

        val showMotoAxis = isMotorcycle
        val showMotoBody = isMotorcycle
        val showCarBody = !isMotorcycle

        llMotoBody.visibility = if (showMotoBody) View.VISIBLE else View.GONE
        carGForceLayout.visibility = if (showCarBody) View.VISIBLE else View.GONE
        carGScaleControls.visibility = View.GONE
        llTopSpeedMotoBody.visibility = if (isMotorcycle) View.VISIBLE else View.GONE
        rlTopSpeedCarBody.visibility = if (isMotorcycle) View.GONE else View.VISIBLE
        llMotoTotalValue.visibility = if (showMotoBody) View.VISIBLE else View.GONE
        gGaugeTrack.visibility = View.GONE
        tvMotoAxisBrakeLabel.visibility = if (showMotoAxis) View.VISIBLE else View.GONE
        tvMotoAxisAccelLabel.visibility = if (showMotoAxis) View.VISIBLE else View.GONE
        viewMotoAxisLine.visibility = if (showMotoAxis) View.VISIBLE else View.GONE
        viewMotoTickTop.visibility = if (showMotoAxis) View.VISIBLE else View.GONE
        viewMotoTickMid.visibility = if (showMotoAxis) View.VISIBLE else View.GONE
        viewMotoTickBottom.visibility = if (showMotoAxis) View.VISIBLE else View.GONE
        viewMotoLongitudinalDot.visibility = if (showMotoAxis) View.VISIBLE else View.GONE
        updateCenterTelemetrySizing()
        updateTelemetryGapSpacer()

        if (isMotorcycle) {
            tvMotoGHeader.text = "G-FORCE - LONGITUDINAL"
            tvMotoTotalLabel.text = "TOTAL"
            tvMotoLeftFooterLabel.text = "MAX"
            tvMotoRightFooterLabel.text = "MAX"
            tvMotoMaxBrakingValue.setTextColor(Color.parseColor("#EB3E23"))
            tvMotoMaxAccelValue.setTextColor(Color.parseColor("#00E985"))
        } else {
            tvMotoGHeader.text = "G-FORCE - LATERAL / LONG"
            tvMotoTotalLabel.text = "RESULT"
            tvMotoLeftFooterLabel.text = "LEFT X"
            tvMotoRightFooterLabel.text = "RIGHT X"
            tvMotoMaxBrakingValue.setTextColor(Color.parseColor("#54B8FF"))
            tvMotoMaxAccelValue.setTextColor(Color.parseColor("#8CCBFF"))
        }
    }

    private fun updateTelemetryGapSpacer() {
        val params = telemetryGapSpacer.layoutParams as? LinearLayout.LayoutParams ?: return
        params.height = 0
        params.weight = 0f
        telemetryGapSpacer.layoutParams = params
        telemetryGapSpacer.visibility = View.GONE
    }

    private fun updateCameraPreviewCardVisibility() {
        val isCameraEnabled = sessionCameraMode != SessionCameraMode.OFF
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val previewParams = cardCameraPreview.layoutParams as? LinearLayout.LayoutParams
        previewParams?.let {
            if (!isLandscape) {
                it.height = 0
                it.weight = 1f
                it.topMargin = dpToPx(6f)
                it.bottomMargin = dpToPx(4f)
            }
            cardCameraPreview.layoutParams = it
        }
        cardCameraPreview.minimumHeight = dpToPx(if (isLandscape) 96f else 128f)
        cardCameraPreview.visibility = View.VISIBLE

        llCameraPreviewHeader.visibility = if (isCameraEnabled) View.VISIBLE else View.GONE
        btnCameraModeInline.visibility = if (isCameraEnabled) View.VISIBLE else View.GONE
        cameraPreviewView.visibility = if (isCameraEnabled) View.VISIBLE else View.INVISIBLE

        if (!isCameraEnabled) {
            llCameraPreviewPlaceholder.visibility = View.VISIBLE
            btnCameraMode.visibility = View.VISIBLE
            tvCameraPreviewPlaceholder.text = getString(R.string.track_camera_placeholder_hint)
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_off)
            return
        }

        btnCameraMode.visibility = View.GONE
        llCameraPreviewPlaceholder.visibility = View.GONE
        if (!isVideoRecordingActive) {
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_ready)
        }
    }

    private fun updateCameraButtonUi() {
        btnCameraMode.text = getString(R.string.track_camera_select_button).uppercase(Locale.getDefault())

        val tintColor = when (sessionCameraMode) {
            SessionCameraMode.OFF -> Color.parseColor("#1C2128")
            SessionCameraMode.REAR,
            SessionCameraMode.FRONT -> Color.parseColor("#FF6020")
        }
        btnCameraMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF6020"))
        btnCameraModeInline.backgroundTintList = ColorStateList.valueOf(tintColor)
    }

    private fun updateCenterTelemetrySizing() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val frameMinHeight = when {
            isLandscape -> 0
            isMotorcycle -> 0
            else -> dpToPx(156f)
        }
        flCenterTelemetry.minimumHeight = frameMinHeight

        val cardParams = cardCenterTelemetry.layoutParams as? LinearLayout.LayoutParams ?: return
        if (!isLandscape) {
            cardParams.bottomMargin = dpToPx(4f)
        }
        cardCenterTelemetry.layoutParams = cardParams
    }

    private fun resetCarGaugeDynamicScale() {
        carGaugeDynamicMaxG = carGaugeBaseVisualMaxG
        gGaugeTrackCar.visualMaxG = carGaugeBaseVisualMaxG
    }

    private fun resolveCarGaugeVisualMaxG(currentResultG: Float): Float {
        val requiredMax = max(carGaugeBaseVisualMaxG, max(maxCarResultG, currentResultG))
        if (requiredMax <= carGaugeDynamicMaxG) {
            return carGaugeDynamicMaxG
        }

        val stepsAboveBase = ceil(((requiredMax - carGaugeBaseVisualMaxG) / carGaugeVisualStepG).toDouble()).toInt()
        carGaugeDynamicMaxG = carGaugeBaseVisualMaxG + stepsAboveBase * carGaugeVisualStepG
        return carGaugeDynamicMaxG
    }

    private fun updateLapSummaryCards(currentLapElapsedMs: Long? = null) {
        val currentValue = when {
            isRecording && !awaitingStart && lapStartTime > 0L -> {
                formatCurrentLapCardTime(currentLapElapsedMs ?: (System.currentTimeMillis() - lapStartTime))
            }
            else -> "0:00.000"
        }
        val bestValue = if (bestLapTime != Long.MAX_VALUE) formatLapCardTime(bestLapTime) else "--:--.---"
        val lastValue = if (lapTimes.isNotEmpty()) formatLapCardTime(lapTimes.last()) else "--:--.---"
        val bestLapMarker = if (bestLapNumber > 0) "L $bestLapNumber" else "L -"

        tvSessionCurrentTimerValue.text = currentValue
        tvSessionBestLapValue.text = bestValue
        tvSessionBestLapMarker.text = bestLapMarker
        tvSessionLastLapValue.text = lastValue
    }

    private fun formatCurrentLapCardTime(timeMs: Long): String {
        val safeTime = timeMs.coerceAtLeast(0L)
        val totalSeconds = safeTime / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = safeTime % 1000
        return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
    }

    private fun formatLapCardTime(timeMs: Long): String {
        val safeTime = timeMs.coerceAtLeast(0L)
        val totalSeconds = safeTime / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = safeTime % 1000
        return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
    }

    private fun resolveMotoAxisMaxG(): Float {
        val dynamic = max(maxBraking, maxAcceleration)
        return dynamic.coerceIn(0.9f, maxDisplayG)
    }

    private fun updateMotoGForceCard() {
        val brakingG = max(0f, currentLongitudinalG)
        val accelG = max(0f, -currentLongitudinalG)
        val leftX = max(0f, currentLateralG)
        val rightX = max(0f, -currentLateralG)
        val resultG = sqrt(currentLongitudinalG * currentLongitudinalG + currentLateralG * currentLateralG)

        if (isMotorcycle) {
            val totalLongitudinal = maxBraking + maxAcceleration

            tvMotoBrakingValue.text = String.format(Locale.US, "%.1f", brakingG)
            tvMotoAccelValue.text = String.format(Locale.US, "%.1f", accelG)
            tvMotoMaxBrakingValue.text = String.format(Locale.US, "%.1fg", maxBraking)
            tvMotoMaxAccelValue.text = String.format(Locale.US, "%.1fg", maxAcceleration)
            tvMotoTotalValue.text = String.format(Locale.US, "%.1f", totalLongitudinal)

            pbMotoBraking.progress = (brakingG * 100f).roundToInt().coerceIn(0, pbMotoBraking.max)
            pbMotoAccel.progress = (accelG * 100f).roundToInt().coerceIn(0, pbMotoAccel.max)

            val dotColor = if (currentLongitudinalG >= 0f) {
                Color.parseColor("#EB3E23")
            } else {
                Color.parseColor("#00E985")
            }
            viewMotoLongitudinalDot.backgroundTintList = ColorStateList.valueOf(dotColor)

            val axisMaxG = resolveMotoAxisMaxG()
            val normalized = (currentLongitudinalG / axisMaxG).coerceIn(-1f, 1f)
            rlMotoAxis.post {
                val axisHeight = viewMotoAxisLine.height
                val dotHeight = viewMotoLongitudinalDot.height
                if (axisHeight > 0 && dotHeight > 0) {
                    val halfTravel = ((axisHeight - dotHeight) / 2f).coerceAtLeast(1f)
                    viewMotoLongitudinalDot.translationY = -normalized * halfTravel
                }
            }
            return
        }

        tvCarLateralLeftValue.text = String.format(Locale.US, "%.1f", leftX)
        tvCarLateralRightValue.text = String.format(Locale.US, "%.1f", rightX)
        tvCarBrakingValue.text = String.format(Locale.US, "%.1f", brakingG)
        tvCarAccelValue.text = String.format(Locale.US, "%.1f", accelG)
        if (isRecording && !awaitingStart && lapStartTime > 0L) {
            maxCarResultG = max(maxCarResultG, resultG)
        }
        tvCarTotalValue.text = String.format(Locale.US, "%.1f", maxCarResultG)

        pbCarBraking.progress = (brakingG * 100f).roundToInt().coerceIn(0, pbCarBraking.max)
        pbCarAccel.progress = (accelG * 100f).roundToInt().coerceIn(0, pbCarAccel.max)
        pbCarLateralLeft.progress = (leftX * 100f).roundToInt().coerceIn(0, pbCarLateralLeft.max)
        pbCarLateralRight.progress = (rightX * 100f).roundToInt().coerceIn(0, pbCarLateralRight.max)

        gGaugeTrackCar.visualMaxG = resolveCarGaugeVisualMaxG(resultG)
        gGaugeTrackCar.gForceX = currentLateralG
        gGaugeTrackCar.gForceY = currentLongitudinalG
        gGaugeTrackCar.peakGForce = maxCarResultG
    }

    private fun updateTopSpeedTelemetry(currentSpeedKmh: Float? = null) {
        val speedValue = when {
            awaitingStart -> 0f
            currentSpeedKmh != null -> currentSpeedKmh
            else -> (lastLocation?.speed ?: 0f) * 3.6f
        }.coerceAtLeast(0f)
        val avgSpeed = if (sessionSpeedSamples > 0) {
            sessionSpeedSumKmh / sessionSpeedSamples
        } else {
            0f
        }

        tvTopSpeedValue.text = speedValue.roundToInt().toString()
        tvTopMaxSpeedValue.text = maxSpeed.roundToInt().toString()
        tvTopAvgSpeedValue.text = avgSpeed.roundToInt().toString()
        tvTopSpeedValueCar.text = speedValue.roundToInt().toString()
        tvTopMaxSpeedValueCar.text = maxSpeed.roundToInt().toString()
        tvTopAvgSpeedValueCar.text = avgSpeed.roundToInt().toString()
    }

    private fun updateTopLeanTelemetry(leanAngle: Float = currentCalibratedLean) {
        if (!isMotorcycle) return

        val targetLean = if (abs(leanAngle) < leanDisplayDeadbandDeg) 0f else leanAngle
        val leanSmoothingAlpha = if (gyroscope != null && hasSmartMotionCalibration) {
            if (forceNoGyroLeanLogicOnGyro) leanDisplaySmoothingAlpha else leanDisplaySmoothingAlphaGyro
        } else {
            leanDisplaySmoothingAlpha
        }
        if (!hasDisplayLeanAngle) {
            displayLeanAngle = targetLean
            hasDisplayLeanAngle = true
        } else {
            displayLeanAngle += leanSmoothingAlpha * (targetLean - displayLeanAngle)
        }
        if (targetLean == 0f && abs(displayLeanAngle) < leanDisplaySnapToZeroDeg) {
            displayLeanAngle = 0f
        }

        val absLean = abs(displayLeanAngle)
        tvTopLeanValue.text = "${absLean.roundToInt()}°"
        leanVisualizer.setLeanAngle(displayLeanAngle)
        when {
            absLean < leanDisplayDirectionThresholdDeg -> {
                tvTopLeanDirection.text = ""
                tvTopLeanDirection.visibility = View.GONE
            }
            displayLeanAngle < 0f -> {
                tvTopLeanDirection.visibility = View.VISIBLE
                tvTopLeanDirection.text = "◀ LEFT"
            }
            else -> {
                tvTopLeanDirection.visibility = View.VISIBLE
                tvTopLeanDirection.text = "RIGHT ▶"
            }
        }
    }

    private fun reloadLeanCalibrationForProfile(profileId: Long, forceResetRuntime: Boolean = false) {
        val profileChanged = selectedProfileId != profileId
        selectedProfileId = profileId
        leanCalibrationSnapshot = LeanCalibrationStore.loadSnapshot(this, profileId)
        if (forceResetRuntime || profileChanged) {
            runtimeLeanOffsetDeg = 0f
            resetLeanAutoZeroState()
        }
        lastLeanOrientationLandscape = null
        updateProfileLeanOffsetForOrientation(
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        )
    }

    private fun reloadMotionCalibrationForProfile(profileId: Long) {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hasOrientationCalibration = DragCalibration.activateOrientationRuntime(isLandscape)
        val snapshot = MotionCalibrationStore.loadSnapshot(this, profileId, isLandscape)
        hasSmartMotionCalibration = snapshot.calibrated && hasOrientationCalibration
        val hasSmartUniversalCalibration = hasSmartMotionCalibration
        hasGyroBiasCompensation = hasSmartUniversalCalibration && snapshot.hasGyroBias && gyroscope != null
        if (hasGyroBiasCompensation) {
            gyroBiasRad[0] = snapshot.gyroBiasX
            gyroBiasRad[1] = snapshot.gyroBiasY
            gyroBiasRad[2] = snapshot.gyroBiasZ
        } else {
            gyroBiasRad[0] = 0f
            gyroBiasRad[1] = 0f
            gyroBiasRad[2] = 0f
        }

        applyNoGyroRuntimeTuning(snapshot)

        // Compute calibrated gravity magnitude for no-gyro gravity freeze
        if (gyroscope == null && DragCalibration.isUniversalCalibrated) {
            val gv = DragCalibration.gravityVector
            val gMag = sqrt(gv[0] * gv[0] + gv[1] * gv[1] + gv[2] * gv[2])
            if (gMag in 8.0f..11.0f) noGyroCalGravityMag = gMag
            noGyroFreezeThreshold = (DragCalibration.maxVibrationBaseline * 1.4f + 0.15f)
                .coerceIn(0.30f, 1.0f)
        }
    }

    private fun applyNoGyroRuntimeTuning(snapshot: MotionCalibrationStore.Snapshot) {
        // Keep defaults when calibration quality is low or unavailable.
        if (!snapshot.calibrated) {
            noGyroDeadbandScaleRuntime = noGyroDeadbandScale
            noGyroGScaleFloorRuntime = noGyroGScaleFloor
            noGyroDisplayAlphaMinRuntime = noGyroDisplayAlphaMin
            noGyroDisplayAlphaRangeRuntime = noGyroDisplayAlphaRange
            noGyroGSmoothAlphaRuntime = noGyroGSmoothAlpha
            noGyroBiasLearnAlphaScaleRuntime = noGyroBiasLearnAlphaScale
            noGyroBiasCompensationBaseRuntime = noGyroBiasCompensationBase
            noGyroBiasCompensationRangeRuntime = noGyroBiasCompensationRange
            noGyroLowGBoostMaxRuntime = noGyroLowGBoostMax
            noGyroLowGBoostRangeGRuntime = noGyroLowGBoostRangeG
            return
        }

        val quality = snapshot.qualityScore.coerceIn(0f, 1f)
        val stillScore = (snapshot.stillSamples / 220f).coerceIn(0f, 1f)
        val forwardScore = (snapshot.forwardSamples / 20f).coerceIn(0f, 1f)
        val sampleScore = (0.6f * stillScore + 0.4f * forwardScore).coerceIn(0f, 1f)

        val noiseFloor = max(
            snapshot.stillVibrationMag,
            max(snapshot.forwardNoiseFloor, snapshot.stillLinearAvg)
        )
        val noiseScore = (1f - (noiseFloor / 0.28f)).coerceIn(0f, 1f)

        val responsiveness = (
            0.45f * quality +
                0.35f * sampleScore +
                0.20f * noiseScore
            ).coerceIn(0f, 1f)

        // Higher responsiveness -> lower deadband and snappier display response.
        noGyroDeadbandScaleRuntime = (0.66f - 0.30f * responsiveness).coerceIn(0.34f, 0.66f)
        noGyroGScaleFloorRuntime = (0.82f + 0.14f * responsiveness).coerceIn(0.82f, 0.96f)
        noGyroDisplayAlphaMinRuntime = (0.44f + 0.20f * responsiveness).coerceIn(0.44f, 0.68f)
        noGyroDisplayAlphaRangeRuntime = (0.30f + 0.22f * responsiveness).coerceIn(0.30f, 0.54f)
        noGyroGSmoothAlphaRuntime = (0.52f + 0.24f * responsiveness).coerceIn(0.52f, 0.78f)
        noGyroBiasLearnAlphaScaleRuntime = (0.16f + 0.14f * responsiveness).coerceIn(0.16f, 0.30f)
        noGyroBiasCompensationBaseRuntime = (0.32f + 0.12f * responsiveness).coerceIn(0.32f, 0.50f)
        noGyroBiasCompensationRangeRuntime = (0.14f + 0.12f * responsiveness).coerceIn(0.14f, 0.30f)
        noGyroLowGBoostMaxRuntime = (1.18f + 0.14f * responsiveness).coerceIn(1.18f, 1.34f)
        noGyroLowGBoostRangeGRuntime = (0.24f + 0.06f * responsiveness).coerceIn(0.24f, 0.34f)
    }

    private fun updateProfileLeanOffsetForOrientation(isLandscape: Boolean) {
        val baseline = DragCalibration.getBaselineForOrientation(isLandscape)
        hasProfileLeanOffset = baseline != null
        profileLeanOffsetDeg = if (baseline != null) {
            computeLeanOffsetDegFromBaseline(baseline, isLandscape)
        } else {
            0f
        }
        offsetAngle = profileLeanOffsetDeg + runtimeLeanOffsetDeg
    }

    private fun computeLeanOffsetDegFromBaseline(baseline: FloatArray, isLandscape: Boolean): Float {
        val mag = sqrt(
            baseline[0] * baseline[0] +
                baseline[1] * baseline[1] +
                baseline[2] * baseline[2]
        ).coerceAtLeast(0.0001f)

        // Ако използваме advanced fusion с rightVector, изчисляваме offset по СЪЩИЯ начин
        val useAdvancedLeanFusion =
            gyroscope != null &&
                DragCalibration.isUniversalCalibrated &&
                hasSmartMotionCalibration &&
                !forceNoGyroLeanLogicOnGyro
        
        if (useAdvancedLeanFusion) {
            // Проекция на baseline върху rightVector (същата логика като за текущия lean)
            val rv = DragCalibration.rightVector
            val baselineRightComponent = (baseline[0] * rv[0] + baseline[1] * rv[1] + baseline[2] * rv[2]) / mag
            return (Math.toDegrees(asin(baselineRightComponent.coerceIn(-1f, 1f).toDouble()))).toFloat()
                .coerceIn(-89f, 89f)
        }

        // Старата логика за fallback (без advanced fusion)
        val leanSign = resolveLeanDirectionSign(isLandscape)
        val normalizedComponent = if (isLandscape) {
            (leanSign * baseline[1]) / mag
        } else {
            baseline[0] / mag
        }

        return (-Math.toDegrees(asin(normalizedComponent.coerceIn(-1f, 1f).toDouble()))).toFloat()
            .coerceIn(-89f, 89f)
    }

    private fun resolveLeanDirectionSign(isLandscape: Boolean): Float {
        if (!isLandscape) return 1f
        return when (resolveDisplayRotation()) {
            Surface.ROTATION_90 -> -1f
            Surface.ROTATION_270 -> 1f
            else -> 1f
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: windowManager.defaultDisplay.rotation
        } else {
            windowManager.defaultDisplay.rotation
        }
    }

    private fun calibrateLeanZero() {
        if (!isMotorcycle) return
        runtimeLeanOffsetDeg = filteredAngle - profileLeanOffsetDeg
        offsetAngle = profileLeanOffsetDeg + runtimeLeanOffsetDeg
        resetLeanAutoZeroState()
        currentCalibratedLean = 0f
        displayLeanAngle = 0f
        hasDisplayLeanAngle = false
        maxLeanAngle = 0f
        maxLeanLeftAngle = 0f
        maxLeanRightAngle = 0f
        speedGauge.setLeanAngle(0f)
        updateTopLeanTelemetry(0f)
    }

    private fun resetLeanAutoZeroState() {
        leanAutoZeroPending = false
        leanAutoZeroAccumDeg = 0f
        leanAutoZeroSampleCount = 0
    }

    private fun beginLeanAutoZeroWindow() {
        if (!isMotorcycle) return
        leanAutoZeroPending = true
        leanAutoZeroAccumDeg = 0f
        leanAutoZeroSampleCount = 0
    }

    private fun updateLeanAutoZero(
        accelReferenceTilt: Float,
        worldLinearMagMs2: Float,
        rollRateDegPerSec: Float,
        candidateRuntimeOffsetDeg: Float
    ) {
        if (!leanAutoZeroPending || !isMotorcycle) return

        val stableTilt = abs(accelReferenceTilt) <= leanAutoZeroMaxAbsTiltDeg
        val stableRollRate = abs(rollRateDegPerSec) <= leanAutoZeroMaxRollRateDegPerSec
        val stableLinear = worldLinearMagMs2 <= leanAutoZeroMaxWorldLinearAccMs2

        if (!(stableTilt && stableRollRate && stableLinear)) {
            if (leanAutoZeroSampleCount > 0) {
                leanAutoZeroSampleCount = (leanAutoZeroSampleCount - 1).coerceAtLeast(0)
                leanAutoZeroAccumDeg *= 0.85f
            }
            return
        }

        leanAutoZeroAccumDeg += candidateRuntimeOffsetDeg
        leanAutoZeroSampleCount += 1

        if (leanAutoZeroSampleCount >= leanAutoZeroRequiredSamples) {
            runtimeLeanOffsetDeg = leanAutoZeroAccumDeg / leanAutoZeroSampleCount
            offsetAngle = profileLeanOffsetDeg + runtimeLeanOffsetDeg
            resetLeanAutoZeroState()
        }
    }

    private fun updateCurrentLapBadge(lapNumber: Int) {
        val safeLapNumber = lapNumber.coerceAtLeast(0)
        tvCurrentLap.text = "LAP $safeLapNumber"
    }

    private fun setTrackLengthMeters(lengthMeters: Float) {
        if (lengthMeters > 50f) {
            trackLengthMeters = lengthMeters
        }
    }

    private fun buildCustomRoutePoints(
        customTrackV2: com.example.clinometer.tracking.CustomTrackDefinitionV2,
        mode: TrackMode
    ): List<GeoPoint> {
        val route = mutableListOf<GeoPoint>()
        val startMid = customTrackV2.startGate?.let { lineMidpoint(it) }
        val finishMid = customTrackV2.finishGate?.let { lineMidpoint(it) }

        when (mode) {
            TrackMode.CIRCUIT -> {
                startMid?.let { route.add(it) }
                route.addAll(customTrackV2.referencePath)
                val loopEnd = startMid ?: route.firstOrNull()
                if (loopEnd != null && route.lastOrNull() != loopEnd) {
                    route.add(loopEnd)
                }
            }

            TrackMode.POINT_TO_POINT -> {
                startMid?.let { route.add(it) }
                route.addAll(customTrackV2.referencePath)
                finishMid?.let { route.add(it) }
            }
        }

        return route
    }

    private fun rebuildProgressRoute(points: List<GeoPoint>, closeLoop: Boolean) {
        progressRoutePoints.clear()
        progressRouteCumulativeMeters.clear()
        progressRouteLengthMeters = 0f
        currentProjectedRouteDistanceMeters = Float.NaN
        projectedRouteDistanceAtLapStartMeters = Float.NaN
        smoothedLapProgress = 0f
        lastProjectedSegmentIndex = 0
        lastProjectedAlongMeters = Float.NaN
        lastLapProgressUpdateNs = 0L

        if (points.size < 2) return

        progressRoutePoints.addAll(points)
        if (closeLoop && progressRoutePoints.size >= 2) {
            val first = progressRoutePoints.first()
            val last = progressRoutePoints.last()
            if (distanceMeters(first, last) > 2f) {
                progressRoutePoints.add(first)
            }
        }

        if (progressRoutePoints.size < 2) {
            progressRoutePoints.clear()
            return
        }

        var cumulative = 0f
        progressRouteCumulativeMeters.add(0f)
        for (index in 0 until progressRoutePoints.lastIndex) {
            cumulative += distanceMeters(progressRoutePoints[index], progressRoutePoints[index + 1])
            progressRouteCumulativeMeters.add(cumulative)
        }
        progressRouteLengthMeters = cumulative
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results)
        return results[0]
    }

    private fun projectLocationToRouteDistance(location: Location): Float {
        val nSeg = progressRoutePoints.size - 1
        if (nSeg < 1 || progressRouteCumulativeMeters.size != progressRoutePoints.size) {
            return Float.NaN
        }

        val origin = progressRoutePoints.first()
        val refLatRad = Math.toRadians(origin.latitude)
        val metersPerDegLat = 111_132.0
        val metersPerDegLon = 111_320.0 * cos(refLatRad)

        fun toLocalX(lon: Double): Double = (lon - origin.longitude) * metersPerDegLon
        fun toLocalY(lat: Double): Double = (lat - origin.latitude) * metersPerDegLat

        fun normalizeBearingDiffDeg(a: Float, b: Float): Float {
            var diff = abs(a - b) % 360f
            if (diff > 180f) diff = 360f - diff
            return diff
        }

        fun circularAlongDeltaMeters(a: Float, b: Float, length: Float): Float {
            val d1 = abs(a - b)
            val d2 = abs((a + length) - b)
            val d3 = abs((a - length) - b)
            return min(d1, min(d2, d3))
        }

        val px = toLocalX(location.longitude)
        val py = toLocalY(location.latitude)

        val isCircuit = currentTrackMode == TrackMode.CIRCUIT
        val speedMs = location.speed.coerceAtLeast(0f)
        val hasHeading = location.hasBearing() && speedMs > 8f
        val bearingDeg = location.bearing

        val avgSegLen = (progressRouteLengthMeters / nSeg.toFloat()).coerceAtLeast(1f)
        val lookAheadMeters = (70f + speedMs * 1.8f).coerceIn(70f, 220f)
        val lookBackMeters = 55f
        val lookAheadMin = min(20, nSeg).coerceAtLeast(1)
        val lookBackMin = min(10, nSeg).coerceAtLeast(1)
        val lookAhead = ((lookAheadMeters / avgSegLen).toInt()).coerceIn(lookAheadMin, nSeg)
        val lookBack = ((lookBackMeters / avgSegLen).toInt()).coerceIn(lookBackMin, nSeg)

        if (lastProjectedSegmentIndex < 0 || lastProjectedSegmentIndex >= nSeg) {
            lastProjectedSegmentIndex = 0
        }

        var bestScore = Double.POSITIVE_INFINITY
        var bestDistSq = Double.POSITIVE_INFINITY
        var bestAlong = Float.NaN
        var bestSegIdx = lastProjectedSegmentIndex

        fun evaluateSegment(index: Int) {
            val i = if (isCircuit) ((index % nSeg) + nSeg) % nSeg else index
            if (i < 0 || i >= nSeg) return
            val a = progressRoutePoints[i]
            val b = progressRoutePoints[i + 1]

            val ax = toLocalX(a.longitude)
            val ay = toLocalY(a.latitude)
            val bx = toLocalX(b.longitude)
            val by = toLocalY(b.latitude)

            val dx = bx - ax
            val dy = by - ay
            val segLenSq = dx * dx + dy * dy
            if (segLenSq <= 1e-6) return

            val t = (((px - ax) * dx + (py - ay) * dy) / segLenSq).coerceIn(0.0, 1.0)
            val projX = ax + t * dx
            val projY = ay + t * dy
            val dSq = (px - projX) * (px - projX) + (py - projY) * (py - projY)
            val segLen = kotlin.math.sqrt(segLenSq).toFloat()
            val along = progressRouteCumulativeMeters[i] + (t.toFloat() * segLen)

            var score = dSq

            if (hasHeading) {
                val segBearing = Math.toDegrees(atan2(dx, dy)).toFloat().let { if (it < 0f) it + 360f else it }
                val headingDiff = normalizeBearingDiffDeg(bearingDeg, segBearing)
                val headingPenaltyMeters = (headingDiff / 90f) * 28f
                score += headingPenaltyMeters * headingPenaltyMeters
            }

            if (lastProjectedAlongMeters.isFinite()) {
                val deltaMeters = if (isCircuit && progressRouteLengthMeters > 50f) {
                    circularAlongDeltaMeters(along, lastProjectedAlongMeters, progressRouteLengthMeters)
                } else {
                    abs(along - lastProjectedAlongMeters)
                }
                val freeDelta = 28f + speedMs * 1.5f
                val excess = (deltaMeters - freeDelta).coerceAtLeast(0f)
                score += excess * excess
            }

            if (score < bestScore) {
                bestScore = score
                bestDistSq = dSq
                bestAlong = along
                bestSegIdx = i
            }
        }

        // Search local window around last known segment
        for (offset in -lookBack..lookAhead) {
            evaluateSegment(lastProjectedSegmentIndex + offset)
        }

        // If local match is poor (>60m), fall back to global search
        if (bestDistSq > 3600.0) {
            for (i in 0 until nSeg) {
                evaluateSegment(i)
            }
        }

        lastProjectedSegmentIndex = bestSegIdx
        if (bestAlong.isFinite()) {
            lastProjectedAlongMeters = bestAlong
        }
        return bestAlong
    }

    private fun updateProjectedRouteDistance(location: Location) {
        currentProjectedRouteDistanceMeters = projectLocationToRouteDistance(location)

        if (isRecording && !awaitingStart && lapStartTime > 0L &&
            !currentProjectedRouteDistanceMeters.isNaN() &&
            projectedRouteDistanceAtLapStartMeters.isNaN()
        ) {
            projectedRouteDistanceAtLapStartMeters = currentProjectedRouteDistanceMeters
            if (currentProjectedRouteDistanceMeters.isFinite()) {
                lastProjectedAlongMeters = currentProjectedRouteDistanceMeters
            }
        }
    }

    private fun resolveProjectedLapProgress(): Float? {
        if (!isRecording || awaitingStart || lapStartTime <= 0L) return null
        if (progressRouteLengthMeters <= 50f) return null
        if (!currentProjectedRouteDistanceMeters.isFinite() || !projectedRouteDistanceAtLapStartMeters.isFinite()) return null

        val traveledMeters = if (currentTrackMode == TrackMode.CIRCUIT) {
            var delta = currentProjectedRouteDistanceMeters - projectedRouteDistanceAtLapStartMeters
            if (delta < 0f) delta += progressRouteLengthMeters
            delta
        } else {
            (currentProjectedRouteDistanceMeters - projectedRouteDistanceAtLapStartMeters).coerceAtLeast(0f)
        }

        val denominator = if (currentTrackMode == TrackMode.CIRCUIT) {
            progressRouteLengthMeters
        } else {
            (progressRouteLengthMeters - projectedRouteDistanceAtLapStartMeters).coerceAtLeast(30f)
        }

        return if (denominator <= 0f) null else (traveledMeters / denominator).coerceIn(0f, 0.998f)
    }

    private fun resolvePredictiveTraveledMeters(referenceDistanceMeters: Float): Float {
        val projectedProgress = resolveProjectedLapProgress()
        if (projectedProgress != null && projectedProgress.isFinite()) {
            return (projectedProgress * referenceDistanceMeters).coerceIn(0f, referenceDistanceMeters)
        }

        return lapDistanceAccum.coerceIn(0f, referenceDistanceMeters)
    }

    private fun resolveLapDistanceTargetMeters(): Float {
        return when {
            trackLengthMeters > 100f -> trackLengthMeters
            progressRouteLengthMeters > 100f -> progressRouteLengthMeters
            bestLapDistance > 100f -> bestLapDistance
            else -> 0f
        }
    }

    private fun updateLapDistanceProgress(forcedProgress: Float? = null) {
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val rawTarget = forcedProgress?.coerceIn(0f, 1f) ?: run {
            if (!isRecording || awaitingStart || lapStartTime <= 0L) {
                0f
            } else {
                val targetDistance = resolveLapDistanceTargetMeters()
                val progressDistance = if (currentTrackMode == TrackMode.POINT_TO_POINT) {
                    resolveProjectedLapProgress()?.let { projected ->
                        (projected * targetDistance).coerceAtLeast(lapDistanceAccum)
                    } ?: lapDistanceAccum
                } else {
                    lapDistanceAccum
                }
                if (targetDistance <= 0f || progressDistance <= 0f) {
                    0f
                } else {
                    (progressDistance / targetDistance).coerceIn(0f, 0.998f)
                }
            }
        }

        if (forcedProgress != null) {
            // Forced resets (0f on new lap, 1f on finish) — snap immediately
            smoothedLapProgress = rawTarget
        } else {
            val dtSec = if (lastLapProgressUpdateNs > 0L) {
                ((nowNs - lastLapProgressUpdateNs) / 1_000_000_000.0).toFloat().coerceIn(0.05f, 1.2f)
            } else {
                0.10f
            }
            val speedMs = (lastLocation?.speed ?: 0f).coerceAtLeast(0f)
            val referenceMeters = when {
                currentTrackMode == TrackMode.CIRCUIT && progressRouteLengthMeters > 100f -> progressRouteLengthMeters
                resolveLapDistanceTargetMeters() > 100f -> resolveLapDistanceTargetMeters()
                progressRouteLengthMeters > 100f -> progressRouteLengthMeters
                else -> 1000f
            }

            val maxForwardMeters = max(4f, speedMs * dtSec * 1.9f + 10f)
            val maxForwardStep = (maxForwardMeters / referenceMeters).coerceIn(0.004f, 0.20f)
            val cappedTarget = rawTarget
                .coerceAtMost(smoothedLapProgress + maxForwardStep)
                .coerceAtLeast(smoothedLapProgress)

            val jump = cappedTarget - smoothedLapProgress
            val alpha = when {
                jump > 0.06f -> 0.72f
                jump > 0.02f -> 0.58f
                else -> 0.42f
            }
            smoothedLapProgress += alpha * jump
        }

        lastLapProgressUpdateNs = nowNs

        progressLapDistance.progress = (smoothedLapProgress * lapProgressMax).toInt()
    }

    private fun updateDistanceToLapLine(location: Location) {
        if (trackPoints.isEmpty()) {
            currentDistanceToLapLineMeters = Float.NaN
            currentDistanceToStartLineMeters = Float.NaN
            currentDistanceToFinishLineMeters = Float.NaN
            return
        }

        if (hasGateBasedTriggering()) {
            val startLine = getStartLinePoints()
            val finishLine = getFinishLinePoints()
            if (startLine == null || finishLine == null) {
                currentDistanceToLapLineMeters = Float.NaN
                currentDistanceToStartLineMeters = Float.NaN
                currentDistanceToFinishLineMeters = Float.NaN
                return
            }

            currentDistanceToStartLineMeters = gateCrossingEngine.distanceToLineMeters(
                pointLat = location.latitude,
                pointLon = location.longitude,
                lineStartLat = startLine.first.geoPoint.latitude,
                lineStartLon = startLine.first.geoPoint.longitude,
                lineEndLat = startLine.second.geoPoint.latitude,
                lineEndLon = startLine.second.geoPoint.longitude
            ).toFloat()

            currentDistanceToFinishLineMeters = gateCrossingEngine.distanceToLineMeters(
                pointLat = location.latitude,
                pointLon = location.longitude,
                lineStartLat = finishLine.first.geoPoint.latitude,
                lineStartLon = finishLine.first.geoPoint.longitude,
                lineEndLat = finishLine.second.geoPoint.latitude,
                lineEndLon = finishLine.second.geoPoint.longitude
            ).toFloat()
        } else {
            val startPoint = trackPoints.firstOrNull()
            val finishPoint = when {
                currentTrackMode == TrackMode.POINT_TO_POINT -> trackPoints.lastOrNull()
                trackPoints.size >= 2 -> trackPoints[1]
                else -> trackPoints.firstOrNull()
            }

            currentDistanceToStartLineMeters = startPoint?.let { distanceToTrackPoint(location, it) } ?: Float.NaN
            currentDistanceToFinishLineMeters = finishPoint?.let { distanceToTrackPoint(location, it) } ?: Float.NaN
        }

        currentDistanceToLapLineMeters = if (awaitingStart) {
            currentDistanceToStartLineMeters
        } else {
            currentDistanceToFinishLineMeters
        }
    }

    private fun lineMidpoint(line: com.example.clinometer.tracking.GateLine): GeoPoint {
        return GeoPoint(
            latitude = (line.start.latitude + line.end.latitude) / 2.0,
            longitude = (line.start.longitude + line.end.longitude) / 2.0
        )
    }

    private fun hasGateBasedTriggering(): Boolean {
        return startFinishLineIndices.size >= 4 && startFinishLineIndices.all { it in trackPoints.indices }
    }

    private fun getStartLinePoints(): Pair<TrackPoint, TrackPoint>? {
        if (startFinishLineIndices.size < 2) return null
        val start = trackPoints.getOrNull(startFinishLineIndices[0]) ?: return null
        val end = trackPoints.getOrNull(startFinishLineIndices[1]) ?: return null
        return start to end
    }

    private fun getFinishLinePoints(): Pair<TrackPoint, TrackPoint>? {
        if (startFinishLineIndices.size < 4) return null
        val start = trackPoints.getOrNull(startFinishLineIndices[2]) ?: return null
        val end = trackPoints.getOrNull(startFinishLineIndices[3]) ?: return null
        return start to end
    }

    private fun addCircuitGateTrigger(line: ResolvedGateLine) {
        val gateStart = TrackPoint(line.start.latitude, line.start.longitude)
        val gateEnd = TrackPoint(line.end.latitude, line.end.longitude)
        trackPoints.addAll(listOf(gateStart, gateEnd, gateStart, gateEnd))
        startFinishLineIndices.addAll(listOf(0, 1, 2, 3))
        repeat(4) {
            trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
        }
    }

    private fun addPointToPointGateTriggers(startLine: ResolvedGateLine, finishLine: ResolvedGateLine) {
        trackPoints.add(TrackPoint(startLine.start.latitude, startLine.start.longitude))
        trackPoints.add(TrackPoint(startLine.end.latitude, startLine.end.longitude))
        trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START)
        trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START)

        trackPoints.add(TrackPoint(finishLine.start.latitude, finishLine.start.longitude))
        trackPoints.add(TrackPoint(finishLine.end.latitude, finishLine.end.longitude))
        trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.FINISH)
        trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.FINISH)

        startFinishLineIndices.addAll(listOf(0, 1, 2, 3))
    }

    private fun addCircuitPointFallback(center: GeoPoint) {
        val midpoint = TrackPoint(center.latitude, center.longitude)
        trackPoints.addAll(listOf(midpoint, midpoint))
        trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
        trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
    }

    private fun addPointToPointPointFallback(startCenter: GeoPoint, finishCenter: GeoPoint) {
        trackPoints.add(TrackPoint(startCenter.latitude, startCenter.longitude))
        trackPoints.add(TrackPoint(finishCenter.latitude, finishCenter.longitude))
        trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START)
        trackPointTypes.add(com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.FINISH)
    }

    private fun resolveUsableGateLine(
        gateStart: GeoPoint?,
        gateEnd: GeoPoint?,
        routePoints: List<GeoPoint>,
        role: TriggerGateRole
    ): ResolvedGateLine? {
        if (gateStart != null && gateEnd != null && distanceMeters(gateStart, gateEnd) >= minimumUsableGateLengthMeters) {
            return ResolvedGateLine(start = gateStart, end = gateEnd)
        }

        val center = resolveFallbackGateCenter(gateStart, gateEnd, routePoints, role) ?: return null
        val travelBearing = estimateGateTravelBearing(routePoints, role) ?: return null
        android.util.Log.d(
            "TrackSessionActivity",
            "Synthesizing ${role.name.lowercase(Locale.US)} gate from route heading for $trackId"
        )
        return buildGateLineAroundCenter(center, travelBearing)
    }

    private fun resolveFallbackGateCenter(
        gateStart: GeoPoint?,
        gateEnd: GeoPoint?,
        routePoints: List<GeoPoint>,
        role: TriggerGateRole
    ): GeoPoint? {
        return when {
            gateStart != null && gateEnd != null -> GeoPoint(
                latitude = (gateStart.latitude + gateEnd.latitude) / 2.0,
                longitude = (gateStart.longitude + gateEnd.longitude) / 2.0
            )
            gateStart != null -> gateStart
            gateEnd != null -> gateEnd
            role == TriggerGateRole.FINISH -> routePoints.lastOrNull()
            else -> routePoints.firstOrNull()
        }
    }

    private fun estimateGateTravelBearing(routePoints: List<GeoPoint>, role: TriggerGateRole): Double? {
        val segment = when (role) {
            TriggerGateRole.FINISH -> findDistinctRouteSegment(routePoints, searchFromStart = false)
            TriggerGateRole.CIRCUIT_START_FINISH,
            TriggerGateRole.START -> findDistinctRouteSegment(routePoints, searchFromStart = true)
        } ?: return null

        return bearingDegrees(segment.first, segment.second)
    }

    private fun findDistinctRouteSegment(
        routePoints: List<GeoPoint>,
        searchFromStart: Boolean
    ): Pair<GeoPoint, GeoPoint>? {
        if (routePoints.size < 2) return null

        if (searchFromStart) {
            for (index in 0 until routePoints.lastIndex) {
                val from = routePoints[index]
                val to = routePoints[index + 1]
                if (distanceMeters(from, to) >= minimumUsableGateLengthMeters) {
                    return from to to
                }
            }
        } else {
            for (index in routePoints.lastIndex downTo 1) {
                val from = routePoints[index - 1]
                val to = routePoints[index]
                if (distanceMeters(from, to) >= minimumUsableGateLengthMeters) {
                    return from to to
                }
            }
        }

        return null
    }

    private fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val fromLocation = Location("gate_from").apply {
            latitude = from.latitude
            longitude = from.longitude
        }
        val toLocation = Location("gate_to").apply {
            latitude = to.latitude
            longitude = to.longitude
        }
        return fromLocation.bearingTo(toLocation).toDouble()
    }

    private fun buildGateLineAroundCenter(center: GeoPoint, travelBearingDegrees: Double): ResolvedGateLine {
        val lineBearing = (travelBearingDegrees + 90.0) % 360.0
        val halfWidthMeters = synthesizedGateWidthMeters / 2.0
        return ResolvedGateLine(
            start = offsetGeoPointByBearing(center, lineBearing, halfWidthMeters),
            end = offsetGeoPointByBearing(center, (lineBearing + 180.0) % 360.0, halfWidthMeters)
        )
    }

    private fun offsetGeoPointByBearing(center: GeoPoint, bearingDegrees: Double, distanceMeters: Double): GeoPoint {
        val bearingRad = Math.toRadians(bearingDegrees)
        val dNorth = cos(bearingRad) * distanceMeters
        val dEast = sin(bearingRad) * distanceMeters
        val dLat = dNorth / 111_320.0
        val dLon = dEast / (111_320.0 * cos(Math.toRadians(center.latitude)).coerceAtLeast(0.0001))
        return GeoPoint(
            latitude = center.latitude + dLat,
            longitude = center.longitude + dLon
        )
    }

    private fun distanceToTrackPoint(location: Location, trackPoint: TrackPoint): Float {
        val trackLocation = Location("track_point").apply {
            latitude = trackPoint.geoPoint.latitude
            longitude = trackPoint.geoPoint.longitude
        }
        return location.distanceTo(trackLocation)
    }

    private fun calculateCustomTrackLengthMeters(
        customTrackV2: com.example.clinometer.tracking.CustomTrackDefinitionV2,
        mode: TrackMode
    ): Float {
        val measuredDistance = customTrackV2.measuredDistanceMeters
        if (measuredDistance != null && measuredDistance > 50f) {
            return measuredDistance
        }

        val route = buildCustomRoutePoints(customTrackV2, mode)

        val routeDistance = calculatePathDistanceMeters(route, closeLoop = false)
        if (routeDistance > 50f) {
            return routeDistance
        }

        return calculatePathDistanceMeters(
            customTrackV2.referencePath,
            closeLoop = mode == TrackMode.CIRCUIT
        )
    }

    private fun maybePersistCustomMeasuredDistance(measuredMeters: Float) {
        if (!trackId.startsWith("custom_")) return
        if (!measuredMeters.isFinite() || measuredMeters < 100f) return

        val customTrack = com.example.clinometer.tracking.CustomTrackStorage.loadCustomTrackV2(this, trackId)
            ?: return
        val existing = customTrack.measuredDistanceMeters
        val shouldPersist = when {
            existing == null -> true
            existing < 100f -> true
            else -> kotlin.math.abs(existing - measuredMeters) / existing > 0.08f
        }
        if (!shouldPersist) return

        com.example.clinometer.tracking.CustomTrackStorage.saveCustomTrackV2(
            this,
            customTrack.copy(measuredDistanceMeters = measuredMeters)
        )
        setTrackLengthMeters(measuredMeters)
        android.util.Log.d(
            "TrackSessionActivity",
            "Updated custom measured distance for $trackId: ${existing ?: -1f}m -> ${measuredMeters}m"
        )
    }

    private fun calculatePathDistanceMeters(points: List<GeoPoint>, closeLoop: Boolean): Float {
        if (points.size < 2) return 0f
        val results = FloatArray(1)
        var totalMeters = 0f

        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
            totalMeters += results[0]
        }

        if (closeLoop) {
            val first = points.first()
            val last = points.last()
            Location.distanceBetween(last.latitude, last.longitude, first.latitude, first.longitude, results)
            totalMeters += results[0]
        }

        return totalMeters
    }
    private fun setupClickListeners() {
        btnStartStop.setOnClickListener {
            toggleRecording()
        }
        btnLap.setOnClickListener {
            if (currentTrackMode == TrackMode.POINT_TO_POINT) {
                onBackPressed()
            } else {
                recordLap()
            }
        }
        btnTopLeanZero.setOnClickListener {
            calibrateLeanZero()
        }
        btnCameraMode.setOnClickListener {
            showSessionCameraModeMenu(btnCameraMode)
        }
        btnCameraModeInline.setOnClickListener {
            showSessionCameraModeMenu(btnCameraModeInline)
        }
        btnPredictiveGapMode.setOnClickListener {
            showPredictiveGapModeMenu()
        }
    }

    private fun showSessionCameraModeMenu(anchorView: View) {
        if (isRecording || activeVideoRecording != null) {
            showToast(getString(R.string.track_camera_change_while_recording))
            return
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showToast(getString(R.string.track_camera_unavailable))
            return
        }

        val popup = PopupMenu(this, anchorView)
        popup.menu.add(0, 1, 0, getString(R.string.track_camera_menu_off))
        popup.menu.add(0, 2, 1, getString(R.string.track_camera_menu_rear))
        popup.menu.add(0, 3, 2, getString(R.string.track_camera_menu_front))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> applySessionCameraMode(SessionCameraMode.OFF)
                2 -> requestOrApplySessionCameraMode(SessionCameraMode.REAR)
                3 -> requestOrApplySessionCameraMode(SessionCameraMode.FRONT)
            }
            true
        }
        popup.show()
    }

    private fun requestOrApplySessionCameraMode(mode: SessionCameraMode) {
        if (mode == SessionCameraMode.OFF) {
            applySessionCameraMode(mode)
            return
        }

        val requiredPermissions = requiredSessionVideoPermissions()
        if (requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }) {
            applySessionCameraMode(mode)
        } else {
            pendingSessionCameraMode = mode
            ActivityCompat.requestPermissions(this, requiredPermissions, CAMERA_PERMISSION_REQUEST)
        }
    }

    private fun requiredSessionVideoPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        return permissions.toTypedArray()
    }

    private fun applySessionCameraMode(mode: SessionCameraMode) {
        pendingSessionCameraMode = null
        sessionCameraMode = mode
        updateCameraButtonUi()
        updateCameraPreviewCardVisibility()

        if (mode == SessionCameraMode.OFF) {
            unbindSessionCamera()
            return
        }

        tvCameraPreviewPlaceholder.text = getString(mode.labelResId)
        bindSessionCameraPreview()
    }

    private fun bindSessionCameraPreview() {
        val lensFacing = sessionCameraMode.lensFacing ?: return
        val targetRotation = resolveSessionVideoTargetRotation()

        if (!isVideoRecordingActive) {
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_ready)
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build().also {
                    it.surfaceProvider = cameraPreviewView.surfaceProvider
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD))
                    )
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder).also {
                    it.targetRotation = targetRotation
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                provider.bindToLifecycle(this, selector, preview, videoCapture)
                cameraVideoCapture = videoCapture
            } catch (error: Exception) {
                Log.e("TrackSessionActivity", "Unable to bind session camera", error)
                sessionCameraMode = SessionCameraMode.OFF
                cameraVideoCapture = null
                cameraProvider?.unbindAll()
                updateCameraButtonUi()
                updateCameraPreviewCardVisibility()
                showToast(getString(R.string.track_camera_unavailable))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun unbindSessionCamera() {
        if (activeVideoRecording != null) return
        cameraProvider?.unbindAll()
        cameraVideoCapture = null
        llCameraPreviewPlaceholder.visibility = View.VISIBLE
    }

    private fun resetSessionVideoState(clearSavedMetadata: Boolean, deleteFiles: Boolean) {
        if (deleteFiles) {
            deleteFileIfExists(sessionVideoRawFile)
            if (sessionVideoFinalFile != sessionVideoRawFile) {
                deleteFileIfExists(sessionVideoFinalFile)
            }
            savedSessionVideoUri?.takeIf { it.isNotBlank() }?.let(::deleteVideoUriIfExists)
            savedSessionVideoPath?.takeIf { it.isNotBlank() }?.let { deleteFileIfExists(File(it)) }
        }

        activeVideoRecording = null
        isVideoRecordingActive = false
        sessionVideoRawFile = null
        sessionVideoFinalFile = null
        videoRecordingStartElapsedRealtimeMs = 0L
        videoRecordingStartWallTimeMs = 0L
        videoSyncMarkerOffsetMs = null
        pendingCreateOutingAfterVideoFinalize = false
        pendingDiscardVideoAfterFinalize = false
        if (clearSavedMetadata) {
            savedSessionVideoUri = null
            savedSessionVideoPath = null
            savedSessionVideoCameraLabel = null
            savedSessionVideoStartOffsetMs = null
            savedSessionVideoStartSessionElapsedMs = null
            savedSessionVideoOverlayExported = false
        }
    }

    private data class ProcessedSessionVideo(
        val file: File,
        val actualTrimStartMs: Long
    )

    private fun startSessionVideoRecordingIfNeeded() {
        resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)

        if (sessionCameraMode == SessionCameraMode.OFF) {
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_off)
            return
        }

        val videoCapture = cameraVideoCapture
        if (videoCapture == null) {
            bindSessionCameraPreview()
            showToast(getString(R.string.track_camera_unavailable))
            return
        }

        val rawFile = buildSessionVideoFile("raw")
        sessionVideoRawFile = rawFile
        savedSessionVideoCameraLabel = currentSessionCameraLabel()
        videoRecordingStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
        videoRecordingStartWallTimeMs = System.currentTimeMillis()

        try {
            videoCapture.targetRotation = resolveSessionVideoTargetRotation()
            val outputOptions = FileOutputOptions.Builder(rawFile).build()
            var pendingRecording = videoCapture.output.prepareRecording(this, outputOptions)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                pendingRecording = pendingRecording.withAudioEnabled()
            }
            activeVideoRecording = pendingRecording.start(
                ContextCompat.getMainExecutor(this),
                ::handleSessionVideoRecordEvent
            )
        } catch (error: Exception) {
            Log.e("TrackSessionActivity", "Unable to start session video recording", error)
            resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
            showToast(getString(R.string.track_camera_video_failed))
        }
    }

    private fun handleSessionVideoRecordEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                isVideoRecordingActive = true
                videoRecordingStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
                videoRecordingStartWallTimeMs = System.currentTimeMillis()
                tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_recording)
                llCameraPreviewPlaceholder.visibility = View.GONE
            }

            is VideoRecordEvent.Finalize -> handleSessionVideoFinalize(event)
        }
    }

    private fun handleSessionVideoFinalize(event: VideoRecordEvent.Finalize) {
        val rawFile = sessionVideoRawFile
        val shouldCreateOuting = pendingCreateOutingAfterVideoFinalize
        val shouldDiscard = pendingDiscardVideoAfterFinalize

        activeVideoRecording = null
        isVideoRecordingActive = false
        pendingCreateOutingAfterVideoFinalize = false
        pendingDiscardVideoAfterFinalize = false

        if (event.hasError() || rawFile == null || !rawFile.exists()) {
            Log.e("TrackSessionActivity", "Session video finalize failed: ${event.error}")
            resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_ready)
            if (shouldCreateOuting) {
                showToast(getString(R.string.track_camera_video_failed))
                createOuting()
            }
            return
        }

        tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_processing)
        val trimStartMs = resolveRequestedSessionVideoTrimStartMs()
        Thread {
            val processedVideo = processSessionVideoFile(rawFile, trimStartMs)
            persistProcessedSessionVideo(
                rawFile = rawFile,
                processedVideo = processedVideo,
                shouldCreateOuting = shouldCreateOuting,
                shouldDiscard = shouldDiscard
            )
        }.start()
    }

    private fun persistProcessedSessionVideo(
        rawFile: File,
        processedVideo: ProcessedSessionVideo?,
        shouldCreateOuting: Boolean,
        shouldDiscard: Boolean
    ) {
        val processedFile = processedVideo?.file
        if (shouldDiscard) {
            deleteFileIfExists(processedFile)
            if (processedFile != rawFile) {
                deleteFileIfExists(rawFile)
            }
            resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
        } else {
            val savedUri = processedFile?.let {
                TrackSessionVideoExport.saveVideoToLibrary(this, it, buildSessionVideoBaseTitle())
            }
            val sessionStartSessionElapsedMs = processedVideo?.let { video ->
                resolveSessionVideoStartSessionElapsedMs(video.actualTrimStartMs)
            }
            savedSessionVideoUri = savedUri?.toString()
            savedSessionVideoPath = null
            sessionVideoFinalFile = null
            savedSessionVideoStartOffsetMs = sessionStartSessionElapsedMs?.let { (-it).coerceAtLeast(0L) }
            savedSessionVideoStartSessionElapsedMs = sessionStartSessionElapsedMs
            savedSessionVideoOverlayExported = false

            if (savedUri != null) {
                savedSessionVideoCameraLabel = currentSessionCameraLabel()
                deleteFileIfExists(processedFile)
                if (processedFile != rawFile) {
                    deleteFileIfExists(rawFile)
                }
            } else {
                sessionVideoFinalFile = processedFile
                savedSessionVideoPath = processedFile?.absolutePath
                savedSessionVideoCameraLabel = if (processedFile != null) currentSessionCameraLabel() else null
                if (processedFile != null && processedFile != rawFile) {
                    deleteFileIfExists(rawFile)
                }
            }
        }

        runOnUiThread {
            if (sessionCameraMode == SessionCameraMode.OFF) {
                tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_off)
            } else {
                tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_ready)
            }

            if (shouldCreateOuting) {
                if (savedSessionVideoUri != null || savedSessionVideoPath != null) {
                    showToast(getString(R.string.track_camera_video_saved))
                } else {
                    showToast(getString(R.string.track_camera_video_failed))
                }
                createOuting()
            }
        }
    }

    private fun processSessionVideoFile(rawFile: File, trimStartMs: Long): ProcessedSessionVideo? {
        if (!rawFile.exists()) return null
        if (trimStartMs <= 0L) return ProcessedSessionVideo(rawFile, 0L)

        val trimmedFile = buildSessionVideoFile("trimmed")
        return try {
            val actualTrimStartMs = trimVideoFile(rawFile, trimmedFile, trimStartMs)
            if (actualTrimStartMs != null) {
                ProcessedSessionVideo(trimmedFile, actualTrimStartMs)
            } else {
                deleteFileIfExists(trimmedFile)
                ProcessedSessionVideo(rawFile, 0L)
            }
        } catch (error: Exception) {
            Log.e("TrackSessionActivity", "Unable to trim session video", error)
            deleteFileIfExists(trimmedFile)
            ProcessedSessionVideo(rawFile, 0L)
        }
    }

    private fun trimVideoFile(sourceFile: File, targetFile: File, startMs: Long): Long? {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        return try {
            extractor = MediaExtractor().apply {
                setDataSource(sourceFile.absolutePath)
            }

            var sourceVideoTrackIndex = -1
            val selectedTrackIndexes = mutableListOf<Int>()
            val selectedTrackFormats = mutableMapOf<Int, android.media.MediaFormat>()
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    selectedTrackIndexes += index
                    selectedTrackFormats[index] = format
                    if (mime.startsWith("video/") && sourceVideoTrackIndex < 0) {
                        sourceVideoTrackIndex = index
                    }
                }
            }

            if (sourceVideoTrackIndex < 0 || selectedTrackIndexes.isEmpty()) {
                return null
            }

            val orientationHintDegrees = resolveVideoOrientationHintDegrees(sourceFile)

            extractor.selectTrack(sourceVideoTrackIndex)
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val actualStartUs = extractor.sampleTime.coerceAtLeast(0L)
            extractor.unselectTrack(sourceVideoTrackIndex)

            muxer = MediaMuxer(targetFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndexMap = mutableMapOf<Int, Int>()
            var maxInputSize = 262_144
            selectedTrackIndexes.forEach { sourceTrackIndex ->
                val format = selectedTrackFormats[sourceTrackIndex] ?: return@forEach
                extractor.selectTrack(sourceTrackIndex)
                trackIndexMap[sourceTrackIndex] = muxer.addTrack(format)
                if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxInputSize = max(maxInputSize, format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            if (orientationHintDegrees != 0) {
                muxer.setOrientationHint(orientationHintDegrees)
            }
            extractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            muxer.start()

            val buffer = ByteBuffer.allocate(maxInputSize.coerceAtLeast(262_144))
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sourceTrackIndex = extractor.sampleTrackIndex
                if (sourceTrackIndex < 0) {
                    break
                }

                val targetTrackIndex = trackIndexMap[sourceTrackIndex]
                if (targetTrackIndex == null) {
                    extractor.advance()
                    continue
                }

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < actualStartUs) {
                    extractor.advance()
                    continue
                }

                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = sampleTimeUs - actualStartUs
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(targetTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            actualStartUs / 1000L
        } finally {
            try {
                muxer?.stop()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                extractor?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveRequestedSessionVideoTrimStartMs(): Long {
        val videoStartWallTimeMs = videoRecordingStartWallTimeMs
        val sessionStartWallTimeMs = sessionTelemetryStartWallTimeMs
        if (videoStartWallTimeMs > 0L && sessionStartWallTimeMs > 0L) {
            val sessionStartOffsetMs = sessionStartWallTimeMs - videoStartWallTimeMs
            return (sessionStartOffsetMs - SESSION_VIDEO_PREROLL_MS).coerceAtLeast(0L)
        }

        return ((videoSyncMarkerOffsetMs ?: 0L) - SESSION_VIDEO_PREROLL_MS).coerceAtLeast(0L)
    }

    private fun resolveSessionVideoStartSessionElapsedMs(actualTrimStartMs: Long): Long {
        val videoStartWallTimeMs = videoRecordingStartWallTimeMs
        val sessionStartWallTimeMs = sessionTelemetryStartWallTimeMs
        if (videoStartWallTimeMs > 0L && sessionStartWallTimeMs > 0L) {
            return (videoStartWallTimeMs + actualTrimStartMs) - sessionStartWallTimeMs
        }

        val legacySessionStartOffsetMs = ((videoSyncMarkerOffsetMs ?: 0L) - actualTrimStartMs).coerceAtLeast(0L)
        return -legacySessionStartOffsetMs
    }

    private fun resolveSessionVideoTargetRotation(): Int {
        return if (::cameraPreviewView.isInitialized) {
            cameraPreviewView.display?.rotation ?: resolveDisplayRotation()
        } else {
            resolveDisplayRotation()
        }
    }

    private fun resolveVideoOrientationHintDegrees(sourceFile: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(sourceFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?.let { rotation ->
                    when (((rotation % 360) + 360) % 360) {
                        90, 180, 270 -> ((rotation % 360) + 360) % 360
                        else -> 0
                    }
                }
                ?: 0
        } catch (_: Exception) {
            0
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun updateSessionVideoSyncMarkerIfNeeded() {
        if (sessionCameraMode == SessionCameraMode.OFF) return
        if (videoSyncMarkerOffsetMs != null) return
        if (videoRecordingStartElapsedRealtimeMs <= 0L) return

        videoSyncMarkerOffsetMs = (SystemClock.elapsedRealtime() - videoRecordingStartElapsedRealtimeMs)
            .coerceAtLeast(0L)
    }

    private fun buildSessionVideoFile(tag: String): File {
        val directory = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "track_sessions")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, "track_session_${tag}_${System.currentTimeMillis()}.mp4")
    }

    private fun buildSessionVideoBaseTitle(): String? {
        val directTrackName = trackName.trim().takeIf { it.isNotBlank() }
        if (directTrackName != null) return directTrackName

        return trackId.trim().takeIf { it.isNotBlank() }
    }

    private fun currentSessionCameraLabel(): String {
        return when (sessionCameraMode) {
            SessionCameraMode.FRONT -> getString(R.string.track_camera_label_front)
            SessionCameraMode.REAR -> getString(R.string.track_camera_label_rear)
            SessionCameraMode.OFF -> ""
        }
    }

    private fun deleteFileIfExists(file: File?) {
        if (file == null) return
        if (file.exists()) {
            file.delete()
        }
    }

    private fun deleteVideoUriIfExists(uriString: String) {
        runCatching {
            contentResolver.delete(Uri.parse(uriString), null, null)
        }
    }

    private fun showPredictiveGapModeMenu() {
        val popup = PopupMenu(this, btnPredictiveGapMode)
        popup.menu.add(0, 1, 0, getString(R.string.track_predictive_mode_session))
        popup.menu.add(0, 2, 1, getString(R.string.track_predictive_mode_track))

        popup.setOnMenuItemClickListener { item ->
            predictiveGapSource = when (item.itemId) {
                2 -> PredictiveGapSource.TRACK_BEST
                else -> PredictiveGapSource.SESSION_BEST
            }
            applyPredictiveGapSourceUi()
            resetPredictiveEstimatorState(clearGauge = false)
            true
        }
        popup.show()
    }

    private fun applyPredictiveGapSourceUi() {
        when (predictiveGapSource) {
            PredictiveGapSource.SESSION_BEST -> {
                btnPredictiveGapMode.text = getString(R.string.track_predictive_mode_session)
                tvPredictiveReference.text = sessionBestReferenceText()
            }
            PredictiveGapSource.TRACK_BEST -> {
                btnPredictiveGapMode.text = getString(R.string.track_predictive_mode_track)
                tvPredictiveReference.text = trackBestReferenceText()
            }
        }
    }

    private fun sessionBestReferenceText(): String {
        return if (bestLapNumber > 0 && bestLapTime != Long.MAX_VALUE) {
            "LAP $bestLapNumber - ${formatLapTimePrecise(bestLapTime)}"
        } else {
            getString(R.string.track_predictive_reference_session_placeholder)
        }
    }

    private fun trackBestReferenceText(): String {
        return if (trackBestLapTime != Long.MAX_VALUE) {
            val prefix = if (trackBestLapNumber > 0) "LAP $trackBestLapNumber" else "TRACK"
            "$prefix - ${formatLapTimePrecise(trackBestLapTime)}"
        } else {
            getString(R.string.track_predictive_reference_track_placeholder)
        }
    }

    private fun formatLapTimePrecise(timeMs: Long): String {
        val safe = timeMs.coerceAtLeast(0L)
        val minutes = safe / 60_000L
        val seconds = (safe % 60_000L) / 1000L
        val millis = safe % 1000L
        return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
    }

    private fun resetPredictiveGapCard() {
        tvPredictiveGapSignValue.text = "+"
        tvPredictiveGapValue.text = "0.0"
        val baseColor = ContextCompat.getColor(this, R.color.track_neon_green)
        tvPredictiveGapSignValue.setTextColor(baseColor)
        tvPredictiveGapValue.setTextColor(baseColor)
    }

    private fun resetPredictiveEstimatorState(clearGauge: Boolean) {
        lastPredictedLapSeconds = Float.NaN
        displayedPredictedLapSeconds = Float.NaN
        lastPredictionDisplayUpdateMs = 0L
        lastPredictionComputeAtMs = 0L
        if (clearGauge) {
            speedGauge.setPredictiveGap(0f, 0f)
        }
    }

    private fun updatePredictiveGapCard(predictedLapSeconds: Float, referenceLapSeconds: Float) {
        if (!predictedLapSeconds.isFinite() || !referenceLapSeconds.isFinite() || referenceLapSeconds <= 0f) {
            resetPredictiveGapCard()
            return
        }

        val gapSeconds = predictedLapSeconds - referenceLapSeconds
        val isPositiveGap = gapSeconds > 0f
        val sign = if (gapSeconds < 0f) "-" else "+"
        val displayValue = kotlin.math.abs(gapSeconds)
        val color = ContextCompat.getColor(this, if (isPositiveGap) R.color.accent_red else R.color.track_neon_green)

        tvPredictiveGapSignValue.text = sign
        tvPredictiveGapValue.text = String.format(Locale.US, "%.1f", displayValue)
        tvPredictiveGapSignValue.setTextColor(color)
        tvPredictiveGapValue.setTextColor(color)
    }

    private fun loadTrackBestLapReference() {
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val allKeys = sharedPrefs.all.keys
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val expectedMode = if (currentTrackMode == TrackMode.POINT_TO_POINT) "point_to_point" else "circuit"

        var bestTime = Long.MAX_VALUE
        var bestLap = 0

        val sessionIds = allKeys
            .asSequence()
            .filter { it.endsWith("_outing_count") }
            .map { it.removeSuffix("_outing_count") }
            .toSet()

        for (sessionId in sessionIds) {
            if (!sessionId.startsWith("${currentProfileId}_")) continue

            val parsedTrackId = TrackSessionIdUtils.extractTrackIdFromSessionId(this, sessionId)
            if (parsedTrackId != trackId) continue

            val outingCount = sharedPrefs.getInt("${sessionId}_outing_count", 0)
            for (outing in 1..outingCount) {
                val outingMode = sharedPrefs.getString("${sessionId}_outing_${outing}_mode", null)
                if (outingMode != null && outingMode != expectedMode) continue

                val outingBestText = sharedPrefs.getString("${sessionId}_outing_${outing}_best_lap", null) ?: continue
                val outingBestMs = parseLapTimeToMillis(outingBestText)
                if (outingBestMs !in 1 until bestTime) continue

                bestTime = outingBestMs
                bestLap = resolveLapNumberForBestTime(sharedPrefs, sessionId, outing, outingBestMs)
            }
        }

        trackBestLapTime = bestTime
        trackBestLapNumber = bestLap
    }

    private fun resolveLapNumberForBestTime(
        sharedPrefs: android.content.SharedPreferences,
        sessionId: String,
        outing: Int,
        bestLapMs: Long
    ): Int {
        val lapCountFromSummary = sharedPrefs
            .getString("${sessionId}_outing_${outing}_laps", null)
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0
        val lapCountFromData = sharedPrefs.getInt("${sessionId}_outing_${outing}_lap_data_count", 0).coerceAtLeast(0)
        val lapCount = max(lapCountFromSummary, lapCountFromData)

        for (lap in 1..lapCount) {
            val lapText = sharedPrefs.getString("${sessionId}_outing_${outing}_lap_${lap}", null) ?: continue
            if (parseLapTimeToMillis(lapText) == bestLapMs) {
                return lap
            }
        }

        return 0
    }

    private fun parseLapTimeToMillis(value: String): Long {
        return try {
            val parts = value.trim().split(":")
            if (parts.size != 2) return Long.MAX_VALUE
            val minutes = parts[0].toLong()
            val secParts = parts[1].split(".")
            val seconds = secParts[0].toLong()
            val millisText = secParts.getOrElse(1) { "0" }
            val millis = when (millisText.length) {
                0 -> 0L
                1 -> millisText.toLong() * 100L
                2 -> millisText.toLong() * 10L
                else -> millisText.take(3).toLong()
            }
            minutes * 60_000L + seconds * 1000L + millis
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun updateSecondaryActionButton() {
        if (currentTrackMode == TrackMode.POINT_TO_POINT) {
            btnLap.text = "CANCEL"
        } else {
            btnLap.text = getString(R.string.track_button_lap)
        }
    }
    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        geomagneticRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        preferLinearAccel = linearAccelSensor != null
        // ВИНАГИ получаваме ACCELEROMETER сензора (за g-сили), дори когато има TYPE_LINEAR_ACCELERATION
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (accelerometer == null) {
            Log.w("TrackSession", "No accelerometer sensor available")
        }
        if (rotationVector == null) {
            Log.w("TrackSession", "Rotation vector not available")
        }
        if (gyroscope == null) {
            Log.w("TrackSession", "Gyroscope not available - using accelerometer-only lean fusion")
        } else {
            Log.i("TrackSession", "Gyroscope available - advanced lean fusion enabled when calibration exists")
        }
        
        // Keep motion sensors active while screen is open so lean feels immediate
        // before/after recording too.
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        geomagneticRotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }
    
    private fun setupLocation() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        } else {
            startLocationUpdates()
        }
    }
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_FOR_UPDATE, MIN_DISTANCE_FOR_UPDATE, this)
        }
    }

    private fun refreshTrackDistanceCacheFromLastKnownLocation() {
        currentDistanceToLapLineMeters = Float.NaN
        currentDistanceToStartLineMeters = Float.NaN
        currentDistanceToFinishLineMeters = Float.NaN

        val location = lastLocation ?: resolveLastKnownTrackLocation() ?: return
        updateDistanceToLapLine(location)
    }
    private fun loadTrackData() {
        trackPoints.clear()
        trackPointTypes.clear()
        startFinishLineIndices.clear()
        trackLengthMeters = 0f
        progressRoutePoints.clear()
        progressRouteCumulativeMeters.clear()
        progressRouteLengthMeters = 0f
        currentProjectedRouteDistanceMeters = Float.NaN
        projectedRouteDistanceAtLapStartMeters = Float.NaN
        currentDistanceToLapLineMeters = Float.NaN
        currentDistanceToStartLineMeters = Float.NaN
        currentDistanceToFinishLineMeters = Float.NaN
        var hasValidStartTrigger = false
        
        val isOfficial = if (intent.hasExtra("is_official")) {
            intent.getBooleanExtra("is_official", true)
        } else {
            !trackId.startsWith("custom_")
        }
        
        if (isOfficial) {
            // Load official track data
            val trackManager = TrackManager(this)
            val trackDefinition = trackManager.getTrackDefinition(trackId)
            val trackData = trackManager.loadTrackData(trackId)
            currentTrackMode = trackDefinition?.mode ?: TrackMode.CIRCUIT
            setTrackLengthMeters(((trackDefinition?.lengthKm ?: 0.0) * 1000.0).toFloat())

            val officialRoutePoints: List<GeoPoint> = when {
                trackDefinition?.lapSequence?.isNotEmpty() == true -> {
                    trackDefinition.lapSequence.map { point ->
                        GeoPoint(point.latitude, point.longitude)
                    }
                }
                !trackData?.trackPoints.isNullOrEmpty() -> {
                    trackData?.trackPoints?.map { it.geoPoint } ?: emptyList()
                }
                else -> emptyList()
            }
            rebuildProgressRoute(officialRoutePoints, closeLoop = currentTrackMode == TrackMode.CIRCUIT)
            if (trackLengthMeters <= 50f && progressRouteLengthMeters > 50f) {
                trackLengthMeters = progressRouteLengthMeters
            }

            val startFinishGate = trackDefinition?.startFinishGate
            val startGate = trackDefinition?.startGate
            val finishGate = trackDefinition?.finishGate
            val resolvedStartFinishGate = resolveUsableGateLine(
                gateStart = startFinishGate?.start,
                gateEnd = startFinishGate?.end,
                routePoints = officialRoutePoints,
                role = TriggerGateRole.CIRCUIT_START_FINISH
            )
            val resolvedStartGate = resolveUsableGateLine(
                gateStart = startGate?.start,
                gateEnd = startGate?.end,
                routePoints = officialRoutePoints,
                role = TriggerGateRole.START
            )
            val resolvedFinishGate = resolveUsableGateLine(
                gateStart = finishGate?.start,
                gateEnd = finishGate?.end,
                routePoints = officialRoutePoints,
                role = TriggerGateRole.FINISH
            )

            if (currentTrackMode == TrackMode.CIRCUIT) {
                when {
                    resolvedStartFinishGate != null -> {
                        addCircuitGateTrigger(resolvedStartFinishGate)
                        hasValidStartTrigger = true
                    }
                    else -> {
                        val fallbackStartPoint = resolveFallbackGateCenter(
                            gateStart = startFinishGate?.start,
                            gateEnd = startFinishGate?.end,
                            routePoints = officialRoutePoints,
                            role = TriggerGateRole.CIRCUIT_START_FINISH
                        )
                        if (fallbackStartPoint != null) {
                            addCircuitPointFallback(fallbackStartPoint)
                            hasValidStartTrigger = true
                        }
                    }
                }
            } else {
                when {
                    resolvedStartGate != null && resolvedFinishGate != null -> {
                        addPointToPointGateTriggers(resolvedStartGate, resolvedFinishGate)
                        hasValidStartTrigger = true
                    }
                    else -> {
                        val fallbackStartPoint = resolveFallbackGateCenter(
                            gateStart = startGate?.start,
                            gateEnd = startGate?.end,
                            routePoints = officialRoutePoints,
                            role = TriggerGateRole.START
                        )
                        val fallbackFinishPoint = resolveFallbackGateCenter(
                            gateStart = finishGate?.start,
                            gateEnd = finishGate?.end,
                            routePoints = officialRoutePoints,
                            role = TriggerGateRole.FINISH
                        )
                        if (fallbackStartPoint != null && fallbackFinishPoint != null) {
                            addPointToPointPointFallback(fallbackStartPoint, fallbackFinishPoint)
                            hasValidStartTrigger = true
                        }
                    }
                }
            }

            if (trackPoints.isEmpty() && trackDefinition != null && trackDefinition.lapSequence.isNotEmpty()) {
                trackPoints.addAll(
                    trackDefinition.lapSequence.map { point ->
                        TrackPoint(point.latitude, point.longitude)
                    }
                )
            } else if (trackPoints.isEmpty() && trackData != null) {
                trackPoints.addAll(trackData.trackPoints)
            }

            if (trackPoints.isEmpty()) {
                val s = TrackPoint(41.073128, 23.517839)
                trackPoints.add(s)
                android.util.Log.e("TrackSessionActivity", "Official track has no usable points: $trackId. Applied safe fallback point.")
            }
            
            awaitingStart = hasValidStartTrigger
            if (awaitingStart) {
                android.util.Log.d("TrackSessionActivity", "⏰ awaitingStart set to TRUE for official track (valid start trigger)")
            } else {
                    android.util.Log.w("TrackSessionActivity", "⚠️ Official track has no valid start trigger, session will start immediately")
            }
        } else {
            // Load custom track data from schema v2
            val customTrackV2 = com.example.clinometer.tracking.CustomTrackStorage.loadCustomTrackV2(this, trackId)
            if (customTrackV2 != null) {
                trackName = customTrackV2.name
                currentTrackMode = when (customTrackV2.mode) {
                    com.example.clinometer.tracking.CustomTrackMode.CIRCUIT -> TrackMode.CIRCUIT
                    com.example.clinometer.tracking.CustomTrackMode.POINT_TO_POINT -> TrackMode.POINT_TO_POINT
                }
                val customRoutePrimary = buildCustomRoutePoints(customTrackV2, currentTrackMode)
                val customRoutePrimaryDistance = calculatePathDistanceMeters(customRoutePrimary, closeLoop = false)
                val customProgressRoute = if (customRoutePrimaryDistance > 50f) {
                    customRoutePrimary
                } else {
                    customTrackV2.referencePath
                }
                rebuildProgressRoute(customProgressRoute, closeLoop = currentTrackMode == TrackMode.CIRCUIT)
                setTrackLengthMeters(
                    calculateCustomTrackLengthMeters(customTrackV2, currentTrackMode)
                )
                if (trackLengthMeters <= 50f && progressRouteLengthMeters > 50f) {
                    trackLengthMeters = progressRouteLengthMeters
                }

                val customTriggerRoutePoints = customProgressRoute.ifEmpty { customRoutePrimary }
                val resolvedCustomStartGate = resolveUsableGateLine(
                    gateStart = customTrackV2.startGate?.start,
                    gateEnd = customTrackV2.startGate?.end,
                    routePoints = customTriggerRoutePoints,
                    role = if (currentTrackMode == TrackMode.CIRCUIT) {
                        TriggerGateRole.CIRCUIT_START_FINISH
                    } else {
                        TriggerGateRole.START
                    }
                )
                val resolvedCustomFinishGate = resolveUsableGateLine(
                    gateStart = customTrackV2.finishGate?.start,
                    gateEnd = customTrackV2.finishGate?.end,
                    routePoints = customTriggerRoutePoints,
                    role = TriggerGateRole.FINISH
                )

                when (currentTrackMode) {
                    TrackMode.CIRCUIT -> {
                        when {
                            resolvedCustomStartGate != null -> {
                                addCircuitGateTrigger(resolvedCustomStartGate)
                                hasValidStartTrigger = true
                            }
                            else -> {
                                val fallbackStartPoint = resolveFallbackGateCenter(
                                    gateStart = customTrackV2.startGate?.start,
                                    gateEnd = customTrackV2.startGate?.end,
                                    routePoints = customTriggerRoutePoints,
                                    role = TriggerGateRole.CIRCUIT_START_FINISH
                                )
                                if (fallbackStartPoint != null) {
                                    addCircuitPointFallback(fallbackStartPoint)
                                    hasValidStartTrigger = true
                                } else {
                                    android.util.Log.w("TrackSessionActivity", "Custom circuit missing usable start trigger: $trackId")
                                }
                            }
                        }
                    }
                    TrackMode.POINT_TO_POINT -> {
                        when {
                            resolvedCustomStartGate != null && resolvedCustomFinishGate != null -> {
                                addPointToPointGateTriggers(resolvedCustomStartGate, resolvedCustomFinishGate)
                                hasValidStartTrigger = true
                            }
                            else -> {
                                val fallbackStartPoint = resolveFallbackGateCenter(
                                    gateStart = customTrackV2.startGate?.start,
                                    gateEnd = customTrackV2.startGate?.end,
                                    routePoints = customTriggerRoutePoints,
                                    role = TriggerGateRole.START
                                )
                                val fallbackFinishPoint = resolveFallbackGateCenter(
                                    gateStart = customTrackV2.finishGate?.start,
                                    gateEnd = customTrackV2.finishGate?.end,
                                    routePoints = customTriggerRoutePoints,
                                    role = TriggerGateRole.FINISH
                                )
                                if (fallbackStartPoint != null && fallbackFinishPoint != null) {
                                    addPointToPointPointFallback(fallbackStartPoint, fallbackFinishPoint)
                                    hasValidStartTrigger = true
                                }
                            }
                        }
                    }
                }

                if (trackPoints.isEmpty()) {
                    val fallback = TrackPoint(41.073128, 23.517839)
                    trackPoints.add(fallback)
                    android.util.Log.e("TrackSessionActivity", "Custom track V2 has no usable points: $trackId. Applied safe fallback point.")
                }

                android.util.Log.d("TrackSessionActivity", "Loaded custom track via V2: ${customTrackV2.name} (${customTrackV2.mode}) with ${trackPoints.size} points")
                
                // ✅ CRITICAL: Await start only when a valid start trigger exists
                awaitingStart = hasValidStartTrigger
                if (awaitingStart) {
                    android.util.Log.d("TrackSessionActivity", "⏰ awaitingStart set to TRUE for custom track (valid start trigger)")
                } else {
                    android.util.Log.w("TrackSessionActivity", "⚠️ Custom track has no valid start trigger, session will start immediately")
                }
            } else {
                android.util.Log.e("TrackSessionActivity", "Custom track not found: $trackId")
                // Fallback to default track
                val s = TrackPoint(41.073128, 23.517839)
                trackPoints.addAll(listOf(s))
                awaitingStart = false
            }
        }

        updateSecondaryActionButton()
    }
    private fun toggleRecording() {
        // Don't toggle isRecording here! It will be set in startSession().
        if (!isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }
    private fun startRecording() {
        verifyCalibrationAndStartSession()
    }

    private fun verifyCalibrationAndStartSession() {
        val selectedProfileId = ProfileStorage.getSelectedProfileId(this)
        if (selectedProfileId != -1L) {
            DragCalibration.setProfile(selectedProfileId)
        }

        if (!DragCalibration.isCalibrated) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Calibration Missing")
                .setMessage(
                    "Track session can start, but forward/lateral G-force will be less reliable without calibration. " +
                        "Open calibration now?"
                )
                .setPositiveButton("Open Calibration") { _, _ -> openCalibrationScreen() }
                .setNeutralButton("Continue Anyway") { _, _ -> startSessionImmediately() }
                .setNegativeButton(getString(R.string.dialog_cancel_button), null)
                .show()
        } else {
            startSessionImmediately()
        }
    }

    private fun openCalibrationScreen() {
        val selectedProfileId = ProfileStorage.getSelectedProfileId(this)
        startActivity(Intent(this, DragCalibrationActivity::class.java).apply {
            putExtra("PROFILE_ID", selectedProfileId)
            putExtra("IS_FIRST_PROFILE", false)
            putExtra("IS_NEW_PROFILE", false)
            putExtra("IS_FIRST_LAUNCH", false)
        })
    }

    private fun startSessionImmediately() {
        refreshTrackDistanceCacheFromLastKnownLocation()
        startLocationUpdates()
        startSession()
    }

    private fun startSession() {
        // For both official and custom tracks: show dialog if awaitingStart is true
        if (awaitingStart) {
            showAwaitingStartDialog()
        }

        resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
        isRecording = true
        acquireTrackWakeLock()
        sessionStartTime = System.currentTimeMillis()
        btnStartStop.text = getString(R.string.track_button_stop)
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        // ACCELEROMETER вече е регистриран в setupSensors() (винаги активен за g-сили)
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        geomagneticRotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        handler.post(updateRunnable)
        currentLap = 0
        updateCurrentLapBadge(0)
        updateLapDistanceProgress(0f)
        updateTopSpeedTelemetry(0f)
        updateTopLeanTelemetry(0f)
        updateLapSummaryCards(0L)
        resetCarGaugeDynamicScale()
        
        // ✅ Keep zero until start crossing only when awaitingStart is enabled
        lapStartTime = if (awaitingStart) 0L else System.currentTimeMillis()
        sessionTelemetryStartWallTimeMs = lapStartTime
        
        sectorStartTime = 0L
        currentSector = 0
        lapTimes.clear()
        totalLaps = 0
        bestLapTime = Long.MAX_VALUE
        bestLapNumber = 0
        currentLapTime = 0
        sectorTimes.clear()
        sectorDistances.clear()
        bestSectorTimes.clear()
        bestSectorDistances.clear()
        speedData.clear()
        sessionSpeedSumKmh = 0f
        sessionSpeedSamples = 0
        sectorDistanceAccum = 0f
        lapDistanceAccum = 0f
        bestLapDistance = 0f
        currentProjectedRouteDistanceMeters = Float.NaN
        projectedRouteDistanceAtLapStartMeters = Float.NaN
        resetPredictiveEstimatorState(clearGauge = true)
        speedGauge.unlockPredictiveColor()
        maxSpeed = 0f
        maxAcceleration = 0f
        maxBraking = 0f
        maxCorneringLeftG = 0f
        maxCorneringRightG = 0f
        maxCarResultG = 0f
        maxLeanAngle = 0f
        maxLeanLeftAngle = 0f
        maxLeanRightAngle = 0f
        resetPeakDetectors()
        statsFilteredLongG = 0f
        statsFilteredLatG = 0f
        smoothedConfidence = 1f
        forwardBiasG = 0f
        lateralBiasG = 0f
        longSignMultiplier = 1f
        longSignMismatchStreak = 0
        displayLY = 0f
        displayLX = 0f
        forwardGSmooth = 0f
        lateralGSmooth = 0f
        dragCompatGravity[0] = 0f
        dragCompatGravity[1] = 0f
        dragCompatGravity[2] = 0f
        noGyroGpsLongStatsSmooth = 0f
        noGyroLeanLatGSmooth = 0f
        latestRollRateDegPerSec = 0f
        gyroIntegratedLeanDeg = 0f
        hasGyroIntegratedLean = false
        leanGyroIntegrationTimestampNs = 0L
        lastMadgwickUpdateNs = 0L
        latestGyroForMadgwick[0] = 0f
        latestGyroForMadgwick[1] = 0f
        latestGyroForMadgwick[2] = 0f
        madgwick.reset()
        filteredAngle = 0f
        runtimeLeanOffsetDeg = 0f
        offsetAngle = profileLeanOffsetDeg + runtimeLeanOffsetDeg
        beginLeanAutoZeroWindow()
        startForwardFilteredMs2 = 0f
        startLateralFilteredMs2 = 0f
        startDirectionGoodSamples = 0
        currentLongitudinalG = 0f
        currentLateralG = 0f
        applyPredictiveGapSourceUi()
        resetPredictiveGapCard()
        updateMotoGForceCard()
        updateLapSummaryCards(0L)
        
        // Initialize first lap data
        // NOTE: For custom tracks, startTime will be set when crossing start/finish line
        currentLapData = LapData(
            lapNumber = 1,
            startTime = lapStartTime
        )
        previousLocationForCrossing = null
        lastLocation = null
        lastLocationTimeMs = 0L
        lastStartFinishCrossAtMs = 0L
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("has_active_session", true).putString("active_track_id", trackId).putString("active_track_name", trackName).apply()
        startSessionVideoRecordingIfNeeded()
    }
    private fun stopRecording() {
        isRecording = false
        resetLeanAutoZeroState()
        releaseTrackWakeLock()
        sessionEndTime = System.currentTimeMillis()
        updateLapDistanceProgress(0f)
        
        // Set end time for current lap data
        currentLapData = currentLapData.copy(endTime = sessionEndTime)
        
        btnStartStop.text = getString(R.string.track_button_start)
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
        // Не премахваме ACCELEROMETER сензора, защото се нуждаем от него за g-сили
        handler.removeCallbacks(updateRunnable)
        locationManager.removeUpdates(this)
        updateLapSummaryCards()
        if (activeVideoRecording != null) {
            pendingCreateOutingAfterVideoFinalize = true
            pendingDiscardVideoAfterFinalize = false
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_processing)
            activeVideoRecording?.stop()
        } else {
            createOuting()
        }
    }
    private fun stopRecordingWithoutSaving(
        showDataLostToast: Boolean = true,
        resumeIdleLocationTracking: Boolean = false
    ) {
        isRecording = false
        resetLeanAutoZeroState()
        releaseTrackWakeLock()
        sessionEndTime = System.currentTimeMillis()
        updateLapDistanceProgress(0f)
        
        // Set end time for current lap data
        currentLapData = currentLapData.copy(endTime = sessionEndTime)
        
        btnStartStop.text = getString(R.string.track_button_start)
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
        // Не премахваме ACCELEROMETER сензора, защото се нуждаем от него за g-сили
        handler.removeCallbacks(updateRunnable)
        locationManager.removeUpdates(this)
        if (activeVideoRecording != null) {
            pendingDiscardVideoAfterFinalize = true
            pendingCreateOutingAfterVideoFinalize = false
            activeVideoRecording?.stop()
        } else {
            resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
        }
        clearActiveSession()
        if (resumeIdleLocationTracking) {
            refreshTrackDistanceCacheFromLastKnownLocation()
            startLocationUpdates()
        }
        updateLapSummaryCards()
        if (showDataLostToast) {
            showToast(getString(R.string.track_data_lost))
        }
    }
    private fun recordLap() {
        if (isRecording) {
            currentLap++
            totalLaps++
            updateCurrentLapBadge(currentLap + 1)
            val lapTime = System.currentTimeMillis() - lapStartTime
            val lapTimeFormatted = formatTime(lapTime)
            lapTimes.add(lapTime)
            
            // Play lap completion sound
            soundManager.playLapComplete()
            
            // Save current lap data
            currentLapData = currentLapData.copy(
                lapNumber = currentLap,
                endTime = System.currentTimeMillis()
            )
            lapData.add(currentLapData)
            android.util.Log.d("TrackSessionActivity", "Saved lap $currentLap data: ${currentLapData.routePoints.size} route points, ${currentLapData.speedData.size} speed samples, ${currentLapData.accelerationData.size} accel samples")
            
            val isNewBest = lapTime < bestLapTime
            if (isNewBest) {
                // Play personal best sound
                soundManager.playPersonalBest()
                bestLapTime = lapTime
                bestLapNumber = currentLap
                if (lapTime < trackBestLapTime) {
                    trackBestLapTime = lapTime
                    trackBestLapNumber = currentLap
                }
                // Snapshot sector times and distances of this best lap
                bestSectorTimes.clear()
                bestSectorTimes.addAll(sectorTimes)
                bestSectorDistances.clear()
                bestSectorDistances.addAll(sectorDistances)
                bestLapDistance = sectorDistances.sum()
                applyPredictiveGapSourceUi()
            }

            // Learn real lap distance from first valid custom lap and persist for next sessions.
            val completedLapDistanceMeters = lapDistanceAccum
            if (currentLap == 1 && currentTrackMode == TrackMode.CIRCUIT) {
                maybePersistCustomMeasuredDistance(completedLapDistanceMeters)
            }

            addLapToUI(currentLap, lapTimeFormatted, isNewBest)
            updateLapSummaryCards(0L)
            lapStartTime = System.currentTimeMillis()
            sectorStartTime = lapStartTime
            currentSector = 0
            sectorTimes.clear() // Clear sector times for new lap
            sectorDistances.clear()
            lapDistanceAccum = 0f
            sectorDistanceAccum = 0f
            projectedRouteDistanceAtLapStartMeters = currentProjectedRouteDistanceMeters
            if (currentProjectedRouteDistanceMeters.isFinite()) {
                lastProjectedAlongMeters = currentProjectedRouteDistanceMeters
            }
            resetPredictiveEstimatorState(clearGauge = true)
            speedGauge.unlockPredictiveColor()
            
            // Start new lap data collection
            currentLapData = LapData(
                lapNumber = currentLap + 1,
                startTime = lapStartTime
            )
            
            showToast(getString(R.string.track_lap_saved, currentLap, lapTimeFormatted))
            updateLapDistanceProgress(0f)
        }
    }
    private fun updateDisplay() {
        if (isRecording && !awaitingStart) {
            val currentTime = System.currentTimeMillis()
            val lapTime = currentTime - lapStartTime
            tvLapTime.text = formatTime(lapTime)
            updateLapSummaryCards(lapTime)
            updateStatistics()
            updateGauge()
            updateLapDistanceProgress()
        } else {
            updateLapSummaryCards()
        }
    }
    private fun updateStatistics() {
        lastLocation?.let { location ->
            val currentSpeed = location.speed * 3.6f
            maxSpeed = max(maxSpeed, currentSpeed)
            updateTopSpeedTelemetry(currentSpeed)
        }
        // Lean angle UI is updated in the sensor pipeline; maintain only max here
        if (isMotorcycle) {
            maxLeanAngle = max(maxLeanLeftAngle, maxLeanRightAngle)
        }
    }

    private fun applyStatsDeadband(value: Float): Float {
        return if (abs(value) < statsDeadbandG) 0f else value
    }

    private fun resetPeakDetectors() {
        accelerationPeakDetector.committed = 0f
        accelerationPeakDetector.candidate = 0f
        accelerationPeakDetector.candidateSinceMs = 0L

        brakingPeakDetector.committed = 0f
        brakingPeakDetector.candidate = 0f
        brakingPeakDetector.candidateSinceMs = 0L

        corneringLeftPeakDetector.committed = 0f
        corneringLeftPeakDetector.candidate = 0f
        corneringLeftPeakDetector.candidateSinceMs = 0L

        corneringRightPeakDetector.committed = 0f
        corneringRightPeakDetector.candidate = 0f
        corneringRightPeakDetector.candidateSinceMs = 0L
    }

    private fun updatePeakDetector(detector: PeakDetector, sample: Float, nowMs: Long): Float {
        val currentMax = detector.committed

        if (sample > currentMax + peakEntryHysteresisG) {
            if (detector.candidateSinceMs == 0L) {
                detector.candidate = sample
                detector.candidateSinceMs = nowMs
            } else {
                detector.candidate = max(detector.candidate, sample)
            }

            if (nowMs - detector.candidateSinceMs >= peakHoldMs) {
                detector.committed = max(detector.committed, detector.candidate)
                detector.candidate = 0f
                detector.candidateSinceMs = 0L
            }
        } else if (sample < currentMax + peakExitHysteresisG) {
            detector.candidate = 0f
            detector.candidateSinceMs = 0L
        }

        return detector.committed
    }

    private fun computeSampleConfidence(nowNs: Long): Float {
        val gravityMagnitude = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
        val gravityScore = (1f - (abs(gravityMagnitude - SensorManager.GRAVITY_EARTH) / 2.0f)).coerceIn(0f, 1f)

        val accelAgeMs = if (accelTimestampNs == 0L) Float.MAX_VALUE else (nowNs - accelTimestampNs) / 1_000_000f
        val rotationAgeMs = if (rotationTimestampNs == 0L) Float.MAX_VALUE else (nowNs - rotationTimestampNs) / 1_000_000f
        val worldAgeMs = if (worldAccelTimestampNs == 0L) Float.MAX_VALUE else (nowNs - worldAccelTimestampNs) / 1_000_000f
        val gyroAgeMs = if (gyroTimestampNs == 0L) Float.MAX_VALUE else (nowNs - gyroTimestampNs) / 1_000_000f

        val accelFreshness = (1f - (accelAgeMs / 180f)).coerceIn(0f, 1f)
        val rotationFreshness = (1f - (rotationAgeMs / 280f)).coerceIn(0f, 1f)
        val worldFreshness = (1f - (worldAgeMs / 200f)).coerceIn(0f, 1f)
        val gyroFreshness = (1f - (gyroAgeMs / 300f)).coerceIn(0f, 1f)

        val gyroStability = (1f - ((lastGyroMagnitude - 2.5f) / 5.5f)).coerceIn(0f, 1f)
        val speedMs = lastLocation?.speed ?: 0f
        // Keep confidence from collapsing at low speed; low-speed driving still needs stable G output.
        val speedScore = if (speedMs > 5f) 1f else if (speedMs > 1.5f) 0.95f else 0.90f

        val weighted = 0.28f * gravityScore +
            0.20f * accelFreshness +
            0.20f * rotationFreshness +
            0.15f * worldFreshness +
            0.10f * gyroFreshness +
            0.07f * gyroStability

        val rawConfidence = (weighted * speedScore).coerceIn(0f, 1f)
        smoothedConfidence = confidenceLowPassAlpha * rawConfidence + (1f - confidenceLowPassAlpha) * smoothedConfidence
        return smoothedConfidence.coerceIn(0f, 1f)
    }

    private fun updateSessionGForceStatistics(rawLatG: Float, rawLongG: Float, confidence: Float) {
        if (!isRecording || awaitingStart || lapStartTime <= 0L) return
        if (confidence < minConfidenceForStats) return

        statsFilteredLatG = statsFilterAlpha * rawLatG + (1f - statsFilterAlpha) * statsFilteredLatG
        statsFilteredLongG = statsFilterAlpha * rawLongG + (1f - statsFilterAlpha) * statsFilteredLongG

        val lateral = applyStatsDeadband(statsFilteredLatG)
        val longitudinal = applyStatsDeadband(statsFilteredLongG)

        val nowMs = SystemClock.elapsedRealtime()
        val accelerationSample = max(0f, -longitudinal)
        val brakingSample = max(0f, longitudinal)
        val corneringLeftSample = max(0f, lateral)
        val corneringRightSample = max(0f, -lateral)

        maxAcceleration = updatePeakDetector(accelerationPeakDetector, accelerationSample, nowMs)
        maxBraking = updatePeakDetector(brakingPeakDetector, brakingSample, nowMs)
        maxCorneringLeftG = updatePeakDetector(corneringLeftPeakDetector, corneringLeftSample, nowMs)
        maxCorneringRightG = updatePeakDetector(corneringRightPeakDetector, corneringRightSample, nowMs)
    }

    private fun updateNoGyroSessionStatistics(liveLatG: Float) {
        if (!isRecording || awaitingStart || lapStartTime <= 0L) return

        val nowMs = SystemClock.elapsedRealtime()

        // Left/right maxima follow the same live lateral signal used by the UI.
        statsFilteredLatG = statsFilterAlpha * liveLatG + (1f - statsFilterAlpha) * statsFilteredLatG
        val lateral = applyStatsDeadband(statsFilteredLatG)
        val corneringLeftSample = max(0f, lateral)
        val corneringRightSample = max(0f, -lateral)
        maxCorneringLeftG = updatePeakDetector(corneringLeftPeakDetector, corneringLeftSample, nowMs)
        maxCorneringRightG = updatePeakDetector(corneringRightPeakDetector, corneringRightSample, nowMs)

        // Accel/braking maxima are GPS-only for no-gyro car profiles.
        if (!hasGpsGForce) return
        noGyroGpsLongStatsSmooth = 0.22f * gpsLongG + 0.78f * noGyroGpsLongStatsSmooth
        val longitudinal = applyStatsDeadband(noGyroGpsLongStatsSmooth)
        val accelerationSample = max(0f, -longitudinal)
        val brakingSample = max(0f, longitudinal)
        maxAcceleration = updatePeakDetector(accelerationPeakDetector, accelerationSample, nowMs)
        maxBraking = updatePeakDetector(brakingPeakDetector, brakingSample, nowMs)
    }

    private fun updateInertialForcesFromLinearAcceleration(deviceLinearAccel: FloatArray) {
        val isCalibrated = DragCalibration.isUniversalCalibrated && hasSmartMotionCalibration
        val hasGyroSensor = gyroscope != null
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val confidence = computeSampleConfidence(nowNs)
        val confidenceForFiltering = if (hasGyroSensor) {
            confidence.coerceAtLeast(0.72f)
        } else {
            confidence.coerceAtLeast(0.58f)
        }
        val speedMs = lastLocation?.speed ?: 0f

        val calibratedLatG: Float
        val calibratedLongG: Float

        if (isCalibrated) {
            val forwardAccel = DragCalibration.getSignedForwardAccelerationFromLinear(deviceLinearAccel)
            val lateralAccel = DragCalibration.getSignedLateralAccelerationFromLinear(deviceLinearAccel)

            calibratedLatG = -lateralAccel / 9.81f
            calibratedLongG = -forwardAccel / 9.81f
        } else {
            calibratedLatG = deviceLinearAccel[0] / 9.81f
            calibratedLongG = deviceLinearAccel[1] / 9.81f
        }

        if (!hasGyroSensor) {
            // Track no-gyro UI: use the exact Drag-style G-force pipeline.
            val rawX = latestRawAccel[0]
            val rawY = latestRawAccel[1]
            val rawZ = latestRawAccel[2]

            var uiRawLatG: Float
            var uiRawLongG: Float

            if (DragCalibration.isUniversalCalibrated) {
                val rawAccel = floatArrayOf(rawX, rawY, rawZ)
                val calibratedGravity = DragCalibration.gravityVector
                val forwardAccel = DragCalibration.getSignedForwardAcceleration(rawAccel, calibratedGravity)
                val lateralAccel = DragCalibration.getSignedLateralAcceleration(rawAccel, calibratedGravity)

                uiRawLatG = -lateralAccel / 9.81f
                uiRawLongG = -forwardAccel / 9.81f
            } else {
                dragCompatGravity[0] = dragCompatGravityAlpha * dragCompatGravity[0] + (1f - dragCompatGravityAlpha) * rawX
                dragCompatGravity[1] = dragCompatGravityAlpha * dragCompatGravity[1] + (1f - dragCompatGravityAlpha) * rawY
                dragCompatGravity[2] = dragCompatGravityAlpha * dragCompatGravity[2] + (1f - dragCompatGravityAlpha) * rawZ

                val linearX = rawX - dragCompatGravity[0]
                val linearY = rawY - dragCompatGravity[1]
                uiRawLatG = linearX / 9.81f
                uiRawLongG = linearY / 9.81f
            }

            val deltaX = abs(uiRawLatG - displayLX)
            val deltaY = abs(uiRawLongG - displayLY)
            val alphaX = if (deltaX > 0.5f) 0.3f else 0.5f
            val alphaY = if (deltaY > 0.5f) 0.3f else 0.5f

            displayLX = alphaX * uiRawLatG + (1f - alphaX) * displayLX
            displayLY = alphaY * uiRawLongG + (1f - alphaY) * displayLY

            forwardGSmooth = displayLY
            lateralGSmooth = displayLX

            val finalLongG = clamp(forwardGSmooth, -maxDisplayG, maxDisplayG)
            val finalLatG = clamp(lateralGSmooth, -maxDisplayG, maxDisplayG)

            currentLongitudinalG = finalLongG
            currentLateralG = finalLatG
            speedGauge.gForceX = finalLatG
            speedGauge.gForceY = finalLongG

            updateNoGyroSessionStatistics(finalLatG)
            appendAccelerationHistory(deviceLinearAccel)
            appendCurrentLapTelemetrySample(
                deviceAccel = deviceLinearAccel,
                sampleTimestamp = System.currentTimeMillis(),
                displayLeanAngleSample = if (isMotorcycle) displayLeanAngle else currentCalibratedLean
            )
            runOnUiThread { updateMotoGForceCard() }
            return
        }

        var fusedLatG = calibratedLatG
        var fusedLongG = calibratedLongG

        if (hasFusedWorldAccel && hasSmoothedBearing) {
            val east = fusedWorldAccel[0]
            val north = fusedWorldAccel[1]
            val sinB = sin(smoothedBearingRad)
            val cosB = cos(smoothedBearingRad)

            val forwardAccelFromHeading = east * sinB + north * cosB
            val rightAccelFromHeading = east * cosB - north * sinB

            val headingLatG = -rightAccelFromHeading / 9.81f
            val headingLongG = -forwardAccelFromHeading / 9.81f

            // Blend heading projection conservatively to avoid longitudinal sign-flips under noise.
            val speedQuality = when {
                speedMs >= 10f -> 1f
                speedMs >= 5f -> 0.65f
                speedMs >= 2.5f -> 0.35f
                else -> 0.15f
            }
            val gyroPenalty = (lastGyroMagnitude / 5.5f).coerceIn(0f, 1f)
            val blendQuality = (confidence * speedQuality * (1f - 0.45f * gyroPenalty)).coerceIn(0f, 1f)
            val baseLatBlend = if (isCalibrated) {
                0.18f
            } else if (hasGyroSensor) {
                0.45f
            } else {
                0.58f
            }
            val baseLongBlend = if (isCalibrated) {
                0.06f
            } else if (hasGyroSensor) {
                0.14f
            } else {
                0.42f
            }
            val latBlend = (baseLatBlend * blendQuality).coerceIn(0f, baseLatBlend)
            var longBlend = (baseLongBlend * blendQuality).coerceIn(0f, baseLongBlend)

            // Do not let heading projection flatten or flip real longitudinal acceleration.
            if (hasGyroSensor) {
                val headingTooWeak = kotlin.math.abs(headingLongG) < kotlin.math.abs(fusedLongG) * 0.45f
                val oppositeDirection = (headingLongG * fusedLongG) < 0f
                if (headingTooWeak || oppositeDirection) {
                    longBlend *= 0.28f
                }
            }

            fusedLatG = (1f - latBlend) * fusedLatG + latBlend * headingLatG
            fusedLongG = (1f - longBlend) * fusedLongG + longBlend * headingLongG
        }

        // Learn bias only when the bike is effectively stationary.
        // Including !isRecording here causes sustained cornering G to be absorbed as "bias"
        // during test rides and the dot snaps back to center.
        val nearZeroDynamicG = abs(fusedLongG) < 0.08f && abs(fusedLatG) < 0.08f
        val biasLearningSpeedLimit = if (hasGyroSensor) 0.45f else 0.35f
        val gyroStableForBias = !hasGyroSensor || lastGyroMagnitude < 0.45f
        val strictNearIdle = speedMs < biasLearningSpeedLimit && nearZeroDynamicG && gyroStableForBias
        val canLearnBias = strictNearIdle ||
            (awaitingStart && speedMs < 1.2f && nearZeroDynamicG && gyroStableForBias)
        if (canLearnBias) {
            val effectiveBiasAlpha = if (hasGyroSensor) {
                biasAlpha
            } else {
                biasAlpha * noGyroBiasLearnAlphaScaleRuntime
            }
            forwardBiasG = (1f - effectiveBiasAlpha) * forwardBiasG + effectiveBiasAlpha * fusedLongG
            lateralBiasG = (1f - effectiveBiasAlpha) * lateralBiasG + effectiveBiasAlpha * fusedLatG
        }

        var correctedLongG = if (hasGyroSensor) {
            fusedLongG - forwardBiasG
        } else {
            // Use gentler center compensation on no-gyro phones to preserve low-G motion.
            val lowGBlend = (abs(fusedLongG) / 0.35f).coerceIn(0f, 1f)
            val compensation = (noGyroBiasCompensationBaseRuntime + noGyroBiasCompensationRangeRuntime * lowGBlend)
                .coerceIn(0.28f, 1f)
            fusedLongG - forwardBiasG * compensation
        }
        var correctedLatG = if (hasGyroSensor) {
            fusedLatG - lateralBiasG
        } else {
            val lowGBlend = (abs(fusedLatG) / 0.35f).coerceIn(0f, 1f)
            val compensation = (noGyroBiasCompensationBaseRuntime + noGyroBiasCompensationRangeRuntime * lowGBlend)
                .coerceIn(0.28f, 1f)
            fusedLatG - lateralBiasG * compensation
        }

        // Motorcycle gyro path: auto-correct occasional sign inversion using GPS longitudinal direction.
        if (hasGyroSensor && isMotorcycle && hasGpsGForce) {
            val gpsReliable = kotlin.math.abs(gpsLongG) > 0.035f && speedMs > 4.5f
            val sensorReliable = kotlin.math.abs(correctedLongG) > 0.04f
            if (gpsReliable && sensorReliable) {
                val signMismatch = correctedLongG * gpsLongG < 0f
                longSignMismatchStreak = if (signMismatch) {
                    (longSignMismatchStreak + 1).coerceAtMost(20)
                } else {
                    (longSignMismatchStreak - 1).coerceAtLeast(0)
                }
                if (longSignMismatchStreak >= 6) {
                    longSignMultiplier *= -1f
                    longSignMismatchStreak = 0
                }
            }
            correctedLongG *= longSignMultiplier
        }

        val confidenceDeadbandBoost = if (hasGyroSensor) 0.03f else 0.045f
        var adaptiveLongDeadband = deadbandG + (1f - confidenceForFiltering) * confidenceDeadbandBoost
        var adaptiveLatDeadband = deadbandG + (1f - confidenceForFiltering) * confidenceDeadbandBoost
        if (isCalibrated) {
            // Project baseline vibration envelope onto calibrated axes for axis-specific deadbands.
            val vibrationLongMs2 = abs(
                DragCalibration.maxVibrXUniversal * DragCalibration.forwardVector[0] +
                    DragCalibration.maxVibrYUniversal * DragCalibration.forwardVector[1] +
                    DragCalibration.maxVibrZUniversal * DragCalibration.forwardVector[2]
            )
            val vibrationLatMs2 = abs(
                DragCalibration.maxVibrXUniversal * DragCalibration.rightVector[0] +
                    DragCalibration.maxVibrYUniversal * DragCalibration.rightVector[1] +
                    DragCalibration.maxVibrZUniversal * DragCalibration.rightVector[2]
            )
            val vibrationLongG = (vibrationLongMs2 / 9.81f).coerceAtLeast(0f)
            val vibrationLatG = (vibrationLatMs2 / 9.81f).coerceAtLeast(0f)
            adaptiveLongDeadband = max(adaptiveLongDeadband, (vibrationLongG * 1.35f).coerceAtLeast(deadbandG))
            adaptiveLatDeadband = max(adaptiveLatDeadband, (vibrationLatG * 1.20f).coerceAtLeast(deadbandG))
        }
        if (!hasGyroSensor) {
            // No-gyro devices need a slightly softer deadband to avoid feeling sluggish.
            adaptiveLongDeadband *= noGyroDeadbandScaleRuntime
            adaptiveLatDeadband *= noGyroDeadbandScaleRuntime
        } else {
            // Gyro devices: keep vibration filtering, but avoid zero-lock during real accel/brake.
            val hasRealLongSignal = kotlin.math.abs(correctedLongG) > 0.035f || kotlin.math.abs(gpsLongG) > 0.03f
            if (hasRealLongSignal) {
                adaptiveLongDeadband = adaptiveLongDeadband.coerceAtMost(0.02f)
            }
        }
        fun applySoftDeadband(value: Float, deadband: Float): Float {
            val magnitude = abs(value)
            if (magnitude <= 0.002f) return 0f
            if (deadband <= 0f || magnitude >= deadband) return value
            val t = (magnitude / deadband).coerceIn(0f, 1f)
            return value * t * t
        }
        correctedLongG = applySoftDeadband(correctedLongG, adaptiveLongDeadband)
        correctedLatG = applySoftDeadband(correctedLatG, adaptiveLatDeadband)
        val lowConfidenceLongSuppressThreshold = if (hasGyroSensor) 0.14f else 0.10f
        val lowConfidenceLongSuppressG = if (hasGyroSensor) 0.03f else 0.02f
        if (confidence < lowConfidenceLongSuppressThreshold && abs(correctedLongG) < lowConfidenceLongSuppressG) {
            correctedLongG = applySoftDeadband(correctedLongG, lowConfidenceLongSuppressG)
        }

        if (!hasGyroSensor) {
            // Boost only low amplitudes so no-gyro devices react earlier around 0.1-0.2g.
            fun boostLowG(value: Float): Float {
                val mag = abs(value)
                if (mag <= 0f || mag >= noGyroLowGBoostRangeGRuntime) return value
                val t = 1f - (mag / noGyroLowGBoostRangeGRuntime)
                val gain = 1f + (noGyroLowGBoostMaxRuntime - 1f) * t * t
                return value * gain
            }
            correctedLongG = boostLowG(correctedLongG)
            correctedLatG = boostLowG(correctedLatG)

            if (!isMotorcycle) {
                // Reuse the no-gyro lean channel as a stable left/right source for cars.
                // Left lean is negative in lean UI, while car lateral left is positive.
                val leanLateralRaw = (-tan(Math.toRadians(currentCalibratedLean.toDouble()))).toFloat()
                    .coerceIn(-2.2f, 2.2f)
                val leanAlpha = if (speedMs >= 8f) 0.24f else 0.16f
                noGyroLeanLatGSmooth += leanAlpha * (leanLateralRaw - noGyroLeanLatGSmooth)

                val leanWeight = ((speedMs - 1.5f) / 8f).coerceIn(0.35f, 0.82f)
                correctedLatG = leanWeight * noGyroLeanLatGSmooth + (1f - leanWeight) * correctedLatG
            }

            // Hybrid GPS+Sensor: GPS leads for longitudinal (accel/brake),
            // sensors lead for lateral (cornering). Between GPS ticks sensors
            // fill in; when a fresh GPS tick arrives we crossfade smoothly.
            if (hasGpsGForce) {
                val ageMs = (System.currentTimeMillis() - gpsGTimeMs).coerceAtLeast(0)
                val gpsFreshness = (1f - ageMs / 1800f).coerceIn(0f, 1f)

                // Smoothly track GPS G values at accel rate for fluid crossfade
                val gpsTrkAlpha = 0.15f
                gpsSmoothedLongG += gpsTrkAlpha * (gpsLongG - gpsSmoothedLongG)
                gpsSmoothedLatG += gpsTrkAlpha * (gpsLatG - gpsSmoothedLatG)

                // Longitudinal: GPS dominant (80%) — accel/brake is very accurate from dv/dt
                // Freshness scales GPS weight: fresh tick → 80% GPS, stale → falls to sensor
                val gpsLongWeight = 0.80f * gpsFreshness
                correctedLongG = gpsLongWeight * gpsSmoothedLongG + (1f - gpsLongWeight) * correctedLongG

                // Lateral: Sensor dominant — GPS bearing is unreliable at low speed
                // At high speed GPS lateral can contribute ~20%, at low speed → 0%
                val latSpeedFactor = ((speedMs - 3f) / 8f).coerceIn(0f, 1f)
                val gpsLatWeight = 0.20f * gpsFreshness * latSpeedFactor
                correctedLatG = gpsLatWeight * gpsSmoothedLatG + (1f - gpsLatWeight) * correctedLatG

                // When GPS says near-zero and sensor agrees roughly, suppress residual vibration
                val gpsTotalG = sqrt(gpsSmoothedLongG * gpsSmoothedLongG + gpsSmoothedLatG * gpsSmoothedLatG)
                val sensorTotalG = sqrt(correctedLongG * correctedLongG + correctedLatG * correctedLatG)
                if (gpsTotalG < 0.04f && sensorTotalG < 0.12f && gpsFreshness > 0.3f) {
                    val suppressBlend = 0.60f * gpsFreshness
                    correctedLongG *= (1f - suppressBlend)
                    correctedLatG *= (1f - suppressBlend)
                }
            }
            // If no GPS G available yet, falls through with sensor-only values
        }

        val statsLongG = correctedLongG
        val statsLatG = correctedLatG

        val confidenceScale = if (hasGyroSensor) {
            (0.94f + 0.06f * confidenceForFiltering).coerceIn(0.94f, 1f)
        } else {
            val noGyroScaleFloor = noGyroGScaleFloorRuntime.coerceAtLeast(0.92f)
            (noGyroScaleFloor + (1f - noGyroScaleFloor) * confidenceForFiltering)
                .coerceIn(noGyroScaleFloor, 1f)
        }
        correctedLongG *= confidenceScale
        correctedLatG *= confidenceScale

        val adaptiveDisplayAlpha = if (hasGyroSensor) {
            (0.36f + 0.24f * confidenceForFiltering).coerceIn(0.36f, 0.62f)
        } else if (hasGpsGForce) {
            // Hybrid mode: GPS keeps signal stable, respond reasonably fast
            0.62f
        } else {
            val baseAlpha = noGyroDisplayAlphaMinRuntime + noGyroDisplayAlphaRangeRuntime * confidenceForFiltering
            val frozenBoost = if (noGyroGravityFrozen) 0.15f else 0f
            (baseAlpha + frozenBoost).coerceIn(noGyroDisplayAlphaMinRuntime, 0.82f)
        }
        displayLY = adaptiveDisplayAlpha * correctedLongG + (1f - adaptiveDisplayAlpha) * displayLY
        displayLX = adaptiveDisplayAlpha * correctedLatG + (1f - adaptiveDisplayAlpha) * displayLX

        val gResponseAlpha = if (hasGyroSensor) gSmoothAlpha else if (hasGpsGForce) {
            0.48f
        } else {
            val base = noGyroGSmoothAlphaRuntime.coerceIn(0.34f, 0.46f)
            if (noGyroGravityFrozen) (base + 0.12f).coerceAtMost(0.58f) else base
        }
        forwardGSmooth = gResponseAlpha * displayLY + (1f - gResponseAlpha) * forwardGSmooth
        lateralGSmooth = gResponseAlpha * displayLX + (1f - gResponseAlpha) * lateralGSmooth

        val finalLongG = clamp(forwardGSmooth, -maxDisplayG, maxDisplayG)
        val finalLatG = clamp(lateralGSmooth, -maxDisplayG, maxDisplayG)

        currentLongitudinalG = finalLongG
        currentLateralG = finalLatG
        speedGauge.gForceX = finalLatG
        speedGauge.gForceY = finalLongG
        updateSessionGForceStatistics(statsLatG, statsLongG, confidence)
        runOnUiThread { updateMotoGForceCard() }
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val milliseconds = (timeMs % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, milliseconds)
    }

    private fun formatTimeWithMillis3(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val milliseconds = timeMs % 1000
        return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, milliseconds)
    }
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { ev ->
            // Използваме същата логика като в ForegroundService.kt за g-сили
            // Това трябва да работи винаги, не само когато записваме
            if (ev.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                if (gyroscope != null) {
                    // Coarse alignment: seed Madgwick from first accel reading
                    if (!madgwick.isInitialized) {
                        madgwick.seedFromAccelerometer(ev.values[0], ev.values[1], ev.values[2])
                        val mg = madgwick.getGravityVector()
                        gravity[0] = mg[0]
                        gravity[1] = mg[1]
                        gravity[2] = mg[2]
                    }
                    // Gyro phones: stable gravity via Madgwick, resistant to short lateral jerks.
                    val dtSec = if (lastMadgwickUpdateNs > 0L) {
                        ((ev.timestamp - lastMadgwickUpdateNs) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
                    } else {
                        0.01f
                    }
                    madgwick.samplePeriodSec = dtSec
                    madgwick.update(
                        latestGyroForMadgwick[0],
                        latestGyroForMadgwick[1],
                        latestGyroForMadgwick[2],
                        ev.values[0],
                        ev.values[1],
                        ev.values[2]
                    )
                    lastMadgwickUpdateNs = ev.timestamp

                    val mg = madgwick.getGravityVector()
                    gravity[0] = mg[0]
                    gravity[1] = mg[1]
                    gravity[2] = mg[2]
                } else {
                    // No-gyro phones: freeze gravity LP during real acceleration.
                    // When raw magnitude deviates from calibrated gravity, the user is
                    // accelerating — don't let the LP absorb it into gravity.
                    val rawMag = sqrt(ev.values[0] * ev.values[0] + ev.values[1] * ev.values[1] + ev.values[2] * ev.values[2])
                    val magDeviation = abs(rawMag - noGyroCalGravityMag)
                    if (magDeviation > noGyroFreezeThreshold) {
                        noGyroFreezeCounter = (noGyroFreezeCounter + 1).coerceAtMost(30)
                    } else {
                        noGyroFreezeCounter = (noGyroFreezeCounter - 2).coerceAtLeast(0)
                    }
                    noGyroGravityFrozen = noGyroFreezeCounter >= noGyroFreezeCountThreshold

                    if (!noGyroGravityFrozen) {
                        // Safe to update gravity: near 1G means no significant acceleration
                        val gravityFresh = gravitySensorTimestampNs > 0L &&
                            (ev.timestamp - gravitySensorTimestampNs) <= gravitySensorMaxAgeNs
                        if (gravityFresh) {
                            val targetX = noGyroGravityFromSensorBlend * gravitySensorValues[0] +
                                (1f - noGyroGravityFromSensorBlend) * ev.values[0]
                            val targetY = noGyroGravityFromSensorBlend * gravitySensorValues[1] +
                                (1f - noGyroGravityFromSensorBlend) * ev.values[1]
                            val targetZ = noGyroGravityFromSensorBlend * gravitySensorValues[2] +
                                (1f - noGyroGravityFromSensorBlend) * ev.values[2]
                            gravity[0] = noGyroGravityAlpha * gravity[0] + (1f - noGyroGravityAlpha) * targetX
                            gravity[1] = noGyroGravityAlpha * gravity[1] + (1f - noGyroGravityAlpha) * targetY
                            gravity[2] = noGyroGravityAlpha * gravity[2] + (1f - noGyroGravityAlpha) * targetZ
                        } else {
                            gravity[0] = alphaGravity * gravity[0] + (1 - alphaGravity) * ev.values[0]
                            gravity[1] = alphaGravity * gravity[1] + (1 - alphaGravity) * ev.values[1]
                            gravity[2] = alphaGravity * gravity[2] + (1 - alphaGravity) * ev.values[2]
                        }
                    }
                    // When frozen: gravity[] keeps last good values → raw - gravity = real acceleration
                }
                accelTimestampNs = ev.timestamp

                latestRawAccel[0] = ev.values[0]
                latestRawAccel[1] = ev.values[1]
                latestRawAccel[2] = ev.values[2]

                if (gyroscope == null && rotationVector == null && geomagneticRotationVector == null) {
                    updateRotationMatrixFromAccelMag(ev.timestamp)
                }
            }
            
            // Останалата логика работи само когато записваме
            // TODO: Върни тази проверка след тестване на g-силите!
            // if (!isRecording || awaitingStart || lapStartTime == 0L) return@let
            when (ev.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    rotationTimestampNs = ev.timestamp
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, ev.values)
                }
                Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                    // Stable orientation fallback for devices without gyroscope.
                    rotationTimestampNs = ev.timestamp
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, ev.values)
                }
                Sensor.TYPE_GRAVITY -> {
                    gravitySensorValues[0] = ev.values[0]
                    gravitySensorValues[1] = ev.values[1]
                    gravitySensorValues[2] = ev.values[2]
                    gravitySensorTimestampNs = ev.timestamp
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magneticFieldValues[0] = ev.values[0]
                    magneticFieldValues[1] = ev.values[1]
                    magneticFieldValues[2] = ev.values[2]
                    magneticFieldTimestampNs = ev.timestamp
                    if (gyroscope == null && rotationVector == null && geomagneticRotationVector == null) {
                        updateRotationMatrixFromAccelMag(ev.timestamp)
                    }
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    // Keep sensor-linear sample as one input, but compute final G pipeline on
                    // accelerometer events where we can also fuse against raw-accel-derived linear.
                    linearAccelSensorValues[0] = ev.values[0]
                    linearAccelSensorValues[1] = ev.values[1]
                    linearAccelSensorValues[2] = ev.values[2]
                    hasLinearAccelSensorSample = true
                    linearAccelSensorTimestampNs = ev.timestamp
                    
                    // Единен pipeline: G-силите се изчисляват в processLinearAccelerationAndUpdate
                    
                    // SDK handles sensor data - no need to collect
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    // Always build linear acceleration from raw accel; when hardware linear sensor
                    // exists, blend both sources to avoid transient-only peak behavior.
                    val fallbackLinearX = ev.values[0] - gravity[0]
                    val fallbackLinearY = ev.values[1] - gravity[1]
                    val fallbackLinearZ = ev.values[2] - gravity[2]

                    if (preferLinearAccel && hasLinearAccelSensorSample) {
                        if (gyroscope == null) {
                            val sensorFresh = linearAccelSensorTimestampNs > 0L &&
                                (ev.timestamp - linearAccelSensorTimestampNs) <= noGyroLinearSensorMaxAgeNs

                            if (sensorFresh) {
                                val dx = linearAccelSensorValues[0] - fallbackLinearX
                                val dy = linearAccelSensorValues[1] - fallbackLinearY
                                val dz = linearAccelSensorValues[2] - fallbackLinearZ
                                val disagreement = sqrt(dx * dx + dy * dy + dz * dz)
                                val targetBlend = (maxNoGyroLinearBlend - (disagreement / 2.8f).coerceIn(0f, 0.46f))
                                    .coerceIn(minNoGyroLinearBlend, maxNoGyroLinearBlend)
                                noGyroLinearSensorBlend = 0.18f * targetBlend + 0.82f * noGyroLinearSensorBlend

                                linearAccel[0] = noGyroLinearSensorBlend * linearAccelSensorValues[0] +
                                    (1f - noGyroLinearSensorBlend) * fallbackLinearX
                                linearAccel[1] = noGyroLinearSensorBlend * linearAccelSensorValues[1] +
                                    (1f - noGyroLinearSensorBlend) * fallbackLinearY
                                linearAccel[2] = noGyroLinearSensorBlend * linearAccelSensorValues[2] +
                                    (1f - noGyroLinearSensorBlend) * fallbackLinearZ
                            } else {
                                linearAccel[0] = fallbackLinearX
                                linearAccel[1] = fallbackLinearY
                                linearAccel[2] = fallbackLinearZ
                            }
                        } else {
                            // Gyro phones: keep longitudinal signal closer to raw-gravity subtraction.
                            // TYPE_LINEAR_ACCELERATION can suppress sustained acceleration on some devices.
                            linearAccel[0] = 0.45f * linearAccelSensorValues[0] + 0.55f * fallbackLinearX
                            linearAccel[1] = 0.25f * linearAccelSensorValues[1] + 0.75f * fallbackLinearY
                            linearAccel[2] = 0.45f * linearAccelSensorValues[2] + 0.55f * fallbackLinearZ
                        }
                    } else {
                        linearAccel[0] = fallbackLinearX
                        linearAccel[1] = fallbackLinearY
                        linearAccel[2] = fallbackLinearZ
                    }
                    updateInertialForcesFromLinearAcceleration(linearAccel)
                    updateStartDirectionGate(linearAccel)
                    processLinearAccelerationAndUpdate(linearAccel, ev.timestamp)
                    
                    // Единен pipeline: G-силите се изчисляват в processLinearAccelerationAndUpdate
                    
                    // SDK handles sensor data - no need to collect
                }
                Sensor.TYPE_GYROSCOPE -> {
                    val gx = ev.values[0] - if (hasGyroBiasCompensation) gyroBiasRad[0] else 0f
                    val gy = ev.values[1] - if (hasGyroBiasCompensation) gyroBiasRad[1] else 0f
                    val gz = ev.values[2] - if (hasGyroBiasCompensation) gyroBiasRad[2] else 0f

                    latestGyroForMadgwick[0] = gx
                    latestGyroForMadgwick[1] = gy
                    latestGyroForMadgwick[2] = gz
                    gyroTimestampNs = ev.timestamp
                    val gyroMag = sqrt(gx * gx + gy * gy + gz * gz)
                    lastGyroMagnitude = 0.2f * gyroMag + 0.8f * lastGyroMagnitude

                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val leanSign = resolveLeanDirectionSign(isLandscape)
                    val rawRollRateRad = if (DragCalibration.isUniversalCalibrated && hasSmartMotionCalibration) {
                        // Roll around bike forward axis, independent of phone mounting orientation.
                        val fw = DragCalibration.forwardVector
                        gx * fw[0] + gy * fw[1] + gz * fw[2]
                    } else if (isLandscape) gx * leanSign else gy
                    val rollRateDeg = -rawRollRateRad * radToDeg
                    val rollRateFilterAlpha = if (DragCalibration.isUniversalCalibrated && hasSmartMotionCalibration) 0.16f else 0.25f
                    latestRollRateDegPerSec =
                        rollRateFilterAlpha * rollRateDeg +
                            (1f - rollRateFilterAlpha) * latestRollRateDegPerSec

                    val useGyroLeanIntegration = !forceNoGyroLeanLogicOnGyro
                    if (useGyroLeanIntegration && hasGyroIntegratedLean && leanGyroIntegrationTimestampNs > 0L) {
                        val dtSec = ((ev.timestamp - leanGyroIntegrationTimestampNs) / 1_000_000_000f).coerceIn(0f, 0.06f)
                        if (dtSec > 0f) {
                            gyroIntegratedLeanDeg = (gyroIntegratedLeanDeg + latestRollRateDegPerSec * dtSec).coerceIn(-89f, 89f)
                        }
                    }
                    leanGyroIntegrationTimestampNs = if (useGyroLeanIntegration) ev.timestamp else 0L

                    gyroscopeData.add(gx)
                    gyroscopeData.add(gy)
                    gyroscopeData.add(gz)
                    if (gyroscopeData.size > 1000) {
                        gyroscopeData.removeAt(0)
                    }
                    // Add to current lap data
                    if (isRecording && lapStartTime > 0L) {
                        currentLapData.gyroscopeData.add(gx)
                        currentLapData.gyroscopeData.add(gy)
                        currentLapData.gyroscopeData.add(gz)
                        android.util.Log.d("TrackSessionActivity", "Added gyro data to lap: ${ev.values.size} values")
                        
                        // SDK handles sensor data - no need to collect
                    }
                }
            }
        }
        
        // Update gauge with current data including predictive gap
        updateGauge()
    }

    private fun updateRotationMatrixFromAccelMag(timestampNs: Long) {
        val accelFresh = accelTimestampNs > 0L && (timestampNs - accelTimestampNs) <= accelMagRotationMaxSkewNs
        val magFresh = magneticFieldTimestampNs > 0L && (timestampNs - magneticFieldTimestampNs) <= accelMagRotationMaxSkewNs
        if (!accelFresh || !magFresh) return

        val accelVector = if (gravitySensorTimestampNs > 0L && (timestampNs - gravitySensorTimestampNs) <= gravitySensorMaxAgeNs) {
            gravitySensorValues
        } else {
            latestRawAccel
        }

        val candidate = FloatArray(9)
        if (SensorManager.getRotationMatrix(candidate, null, accelVector, magneticFieldValues)) {
            System.arraycopy(candidate, 0, rotationMatrix, 0, 9)
            rotationTimestampNs = timestampNs
        }
    }

    private fun processLinearAccelerationAndUpdate(deviceAccel: FloatArray, timestampNs: Long) {
        // Transform device linear acceleration into world ENU frame
        if (!rotationMatrix.all { it == 0f }) {
            worldAccel[0] = rotationMatrix[0] * deviceAccel[0] + rotationMatrix[1] * deviceAccel[1] + rotationMatrix[2] * deviceAccel[2] // East
            worldAccel[1] = rotationMatrix[3] * deviceAccel[0] + rotationMatrix[4] * deviceAccel[1] + rotationMatrix[5] * deviceAccel[2] // North
            worldAccel[2] = rotationMatrix[6] * deviceAccel[0] + rotationMatrix[7] * deviceAccel[1] + rotationMatrix[8] * deviceAccel[2] // Up
        } else {
            worldAccel[0] = deviceAccel[0]
            worldAccel[1] = deviceAccel[1]
            worldAccel[2] = deviceAccel[2]
        }

        fusedWorldAccel[0] = worldFusionAlpha * worldAccel[0] + (1f - worldFusionAlpha) * fusedWorldAccel[0]
        fusedWorldAccel[1] = worldFusionAlpha * worldAccel[1] + (1f - worldFusionAlpha) * fusedWorldAccel[1]
        fusedWorldAccel[2] = worldFusionAlpha * worldAccel[2] + (1f - worldFusionAlpha) * fusedWorldAccel[2]
        hasFusedWorldAccel = true
        worldAccelTimestampNs = timestampNs

        val east = worldAccel[0]
        val north = worldAccel[1]

        // Stationary detection on linear acceleration magnitude
        val worldMag = kotlin.math.sqrt((east * east + north * north + worldAccel[2] * worldAccel[2]).toDouble()).toFloat()
        if (worldMag < stationaryAccThreshold) {
            stationaryCounter = (stationaryCounter + 1).coerceAtMost(1000)
        } else {
            stationaryCounter = (stationaryCounter - 2).coerceAtLeast(0)
        }
        isStationary = stationaryCounter >= stationaryCountToLock

        // Professional-style complementary lean fusion:
        // 1) gyro handles fast transitions, 2) accel reference slowly corrects drift.
        val x = gravity[0]
        val y = gravity[1]
        val z = gravity[2]
        val totalGravity = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val leanSign = resolveLeanDirectionSign(isLandscape)
        if (lastLeanOrientationLandscape == null || lastLeanOrientationLandscape != isLandscape) {
            updateProfileLeanOffsetForOrientation(isLandscape)
            if (selectedProfileId != -1L) {
                reloadMotionCalibrationForProfile(selectedProfileId)
            }
            lastLeanOrientationLandscape = isLandscape
        }

        val useAdvancedLeanFusion =
            gyroscope != null &&
                DragCalibration.isUniversalCalibrated &&
                hasSmartMotionCalibration &&
                !forceNoGyroLeanLogicOnGyro
        val accelReferenceTilt = if (totalGravity > 0f) {
            if (useAdvancedLeanFusion) {
                // Lean from gravity projection on calibrated bike RIGHT axis.
                val rv = DragCalibration.rightVector
                val rightComponent = ((x * rv[0] + y * rv[1] + z * rv[2]) / totalGravity).toDouble().coerceIn(-1.0, 1.0)
                (Math.toDegrees(Math.asin(rightComponent))).toFloat()
            } else if (isLandscape) {
                (-Math.toDegrees(Math.asin(((leanSign * y) / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
            } else {
                (-Math.toDegrees(Math.asin((x / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
            }
        } else 0f

        if (useAdvancedLeanFusion) {
            // Madgwick AHRS already fuses gyro + accel into a vibration-resistant
            // quaternion.  Use its gravity projection on the calibrated RIGHT axis
            // directly — no separate gyro integration needed.
            if (!hasGyroIntegratedLean) {
                filteredAngle = accelReferenceTilt
                hasGyroIntegratedLean = true
            } else {
                filteredAngle += 0.35f * (accelReferenceTilt - filteredAngle)
            }
        } else {
            if (!hasGyroIntegratedLean) {
                gyroIntegratedLeanDeg = accelReferenceTilt
                hasGyroIntegratedLean = true
            }

            val dynamicLoadG = (worldMag / SensorManager.GRAVITY_EARTH).coerceAtLeast(0f)
            val accelMotionTrust = (1f - dynamicLoadG * 0.55f).coerceIn(0.18f, 1f)
            val gyroSpinPenalty = (lastGyroMagnitude / 4.0f).coerceIn(0f, 1f)
            val accelTrust = (accelMotionTrust * (1f - 0.25f * gyroSpinPenalty)).coerceIn(0.15f, 1f)
            val correctionGain = (minAccelCorrection + (maxAccelCorrection - minAccelCorrection) * accelTrust)
                .coerceIn(minAccelCorrection, maxAccelCorrection)

            gyroIntegratedLeanDeg += correctionGain * (accelReferenceTilt - gyroIntegratedLeanDeg)
            filteredAngle = gyroIntegratedLeanDeg
        }

        val shouldApplyRuntimeAutoZero =
            isMotorcycle &&
                leanAutoZeroPending &&
                !(gyroscope != null && hasSmartMotionCalibration && hasProfileLeanOffset)

        if (shouldApplyRuntimeAutoZero) {
            val candidateRuntimeOffsetDeg = filteredAngle - profileLeanOffsetDeg
            updateLeanAutoZero(
                accelReferenceTilt = accelReferenceTilt,
                worldLinearMagMs2 = worldMag,
                rollRateDegPerSec = latestRollRateDegPerSec,
                candidateRuntimeOffsetDeg = candidateRuntimeOffsetDeg
            )
        }
        currentCalibratedLean = (filteredAngle - offsetAngle).coerceIn(-90f, 90f)

        if (isRecording && !awaitingStart && lapStartTime > 0L) {
            if (currentCalibratedLean < 0f) {
                maxLeanLeftAngle = max(maxLeanLeftAngle, abs(currentCalibratedLean))
            } else if (currentCalibratedLean > 0f) {
                maxLeanRightAngle = max(maxLeanRightAngle, currentCalibratedLean)
            }
            maxLeanAngle = max(maxLeanLeftAngle, maxLeanRightAngle)
        }

        if (isMotorcycle) {
            // Sensor callbacks are delivered on the main looper here; avoid posting
            // another UI task per sample because it introduces visible lean lag.
            updateTopLeanTelemetry(currentCalibratedLean)
            speedGauge.setLeanAngle(displayLeanAngle)
        }

        appendAccelerationHistory(deviceAccel)
        appendCurrentLapTelemetrySample(
            deviceAccel = deviceAccel,
            sampleTimestamp = System.currentTimeMillis(),
            displayLeanAngleSample = if (isMotorcycle) displayLeanAngle else currentCalibratedLean
        )
    }

    private fun appendAccelerationHistory(deviceAccel: FloatArray) {
        accelerationData.add(deviceAccel[0])
        accelerationData.add(deviceAccel[1])
        accelerationData.add(deviceAccel[2])
        if (accelerationData.size > 1500) {
            repeat(3) { accelerationData.removeAt(0) }
        }
    }

    private fun appendCurrentLapTelemetrySample(
        deviceAccel: FloatArray,
        sampleTimestamp: Long,
        displayLeanAngleSample: Float
    ) {
        if (isRecording && lapStartTime > 0L) {
            currentLapData.accelerationData.addAll(deviceAccel.toList())
            currentLapData.leanAngleData.add(currentCalibratedLean)
            currentLapData.displayLeanAngleData.add(
                if (displayLeanAngleSample.isFinite()) displayLeanAngleSample else currentCalibratedLean
            )
            currentLapData.longitudinalGData.add(currentLongitudinalG)
            currentLapData.lateralGData.add(currentLateralG)
            currentLapData.maxBrakingData.add(maxBraking)
            currentLapData.maxAccelData.add(maxAcceleration)
            currentLapData.maxCorneringLeftData.add(maxCorneringLeftG)
            currentLapData.maxCorneringRightData.add(maxCorneringRightG)
            currentLapData.maxResultGData.add(maxCarResultG)
            currentLapData.timestamps.add(sampleTimestamp)
        } else {
            android.util.Log.d("TrackSessionActivity", "Not adding sensor data: isRecording=$isRecording, awaitingStart=$awaitingStart, lapStartTime=$lapStartTime")
        }
    }

    private fun clamp(v: Float, min: Float, max: Float) = when {
        v < min -> min
        v > max -> max
        else -> v
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onLocationChanged(location: Location) {
        previousLocationForCrossing = lastLocation
        lastLocation = location
        val speedKmh = location.speed * 3.6f
        updateTopSpeedTelemetry(speedKmh)
        updateDistanceToLapLine(location)
        updateProjectedRouteDistance(location)
        
        // ✅ CRITICAL FIX: Only record GPS data if we're recording AND lapStartTime is set
        if (isRecording && lapStartTime > 0L) {
            // Accumulate distance traveled in current sector and lap
            val nowT = location.time
            if (lastLocationTimeMs != 0L) {
                val dtSec = ((nowT - lastLocationTimeMs) / 1000f).coerceIn(0.05f, 1.5f)
                val gpsStepMeters = previousLocationForCrossing?.distanceTo(location) ?: 0f
                val maxReasonableStep = max(3f, location.speed * dtSec * 1.8f + 8f)
                val inc = gpsStepMeters.coerceIn(0f, maxReasonableStep)
                sectorDistanceAccum += inc
                lapDistanceAccum += inc
                updateLapDistanceProgress()
            }
            lastLocationTimeMs = nowT
            
            speedData.add(speedKmh)
            sessionSpeedSumKmh += speedKmh
            sessionSpeedSamples += 1
            // Add to current lap data
            currentLapData.speedData.add(speedKmh)
            
            val currentTime = System.currentTimeMillis()
            val relativeTimestamp = currentTime - lapStartTime
            
            android.util.Log.d("TrackSessionActivity", "📍 GPS DATA ADDED:")
            android.util.Log.d("TrackSessionActivity", "   Current time: $currentTime")
            android.util.Log.d("TrackSessionActivity", "   Lap start time: $lapStartTime")
            android.util.Log.d("TrackSessionActivity", "   Relative timestamp: $relativeTimestamp")
            android.util.Log.d("TrackSessionActivity", "   Speed: ${speedKmh}km/h")
            android.util.Log.d("TrackSessionActivity", "   Total GPS points so far: ${currentLapData.routePoints.size + 1}")
            
            currentLapData.routePoints.add(RoutePoint(
                geoPoint = com.example.clinometer.GeoPoint(location.latitude, location.longitude),
                speed = speedKmh,
                angle = currentCalibratedLean,
                timestamp = relativeTimestamp, // Използваме относително време!
                absoluteTime = location.time
            ))
        } else {
            android.util.Log.d("TrackSessionActivity", "Not adding GPS data: isRecording=$isRecording, awaitingStart=$awaitingStart, lapStartTime=$lapStartTime")
        }
        // Keep rolling window of speed samples (m/s)
        val now = System.currentTimeMillis()
        speedSamplesMs.addLast(now to location.speed)
        while (speedSamplesMs.isNotEmpty() && now - speedSamplesMs.first().first > speedWindowMs) {
            speedSamplesMs.removeFirst()
        }
        // Smooth GPS bearing when available and speed is reasonable
        if (location.hasBearing() && location.speed > 1.5f) { // > ~5.4 km/h
            val bRad = Math.toRadians(location.bearing.toDouble()).toFloat()
            smoothedBearingRad = if (!hasSmoothedBearing) {
                hasSmoothedBearing = true
                bRad
            } else {
                // Smooth angle with wrap-around awareness
                val delta = atan2(sin(bRad - smoothedBearingRad), cos(bRad - smoothedBearingRad))
                smoothedBearingRad + bearingAlpha * delta
            }
        }

        // GPS-based G-force for no-gyro phones: pure kinematics, immune to vibrations
        if (gyroscope == null) {
            val gpsNowMs = location.time
            val currentSpeedMs = location.speed
            if (!prevGpsSpeedMs.isNaN() && prevGpsFixTimeMs > 0L) {
                val dt = ((gpsNowMs - prevGpsFixTimeMs) / 1000.0).coerceIn(0.08, 3.0).toFloat()

                // Longitudinal G: speed change → acceleration/braking
                // Convention: negative = accelerating, positive = braking
                gpsLongG = -((currentSpeedMs - prevGpsSpeedMs) / dt / SensorManager.GRAVITY_EARTH)

                // Lateral G: heading rate × speed → cornering force
                if (location.hasBearing() && currentSpeedMs > 2.5f && !prevGpsBearingRad.isNaN()) {
                    val bRad = Math.toRadians(location.bearing.toDouble()).toFloat()
                    val dBearing = atan2(sin(bRad - prevGpsBearingRad), cos(bRad - prevGpsBearingRad))
                    val turnRate = dBearing / dt
                    gpsLatG = -(currentSpeedMs * turnRate / SensorManager.GRAVITY_EARTH)
                } else if (currentSpeedMs < 1.5f) {
                    // Decay lateral at very low speed where bearing is unreliable
                    gpsLatG *= 0.5f
                }

                hasGpsGForce = true
                gpsGTimeMs = System.currentTimeMillis()
            }
            if (location.hasBearing() && currentSpeedMs > 2.5f) {
                prevGpsBearingRad = Math.toRadians(location.bearing.toDouble()).toFloat()
            }
            prevGpsSpeedMs = currentSpeedMs
            prevGpsFixTimeMs = gpsNowMs
        }

        // Only check track point proximity if we're actually recording
        if (isRecording) {
            checkTrackPointProximity(location)
        }
    }
    
    private fun checkStartFinishLineCrossing(
        location: Location, 
        point1: TrackPoint, 
        point2: TrackPoint,
        ignoreDebounce: Boolean = false
    ): Boolean {
        val previous = previousLocationForCrossing ?: return false
        val now = System.currentTimeMillis()
        var crossed = gateCrossingEngine.didCrossLine(
            previousLat = previous.latitude,
            previousLon = previous.longitude,
            currentLat = location.latitude,
            currentLon = location.longitude,
            lineStartLat = point1.geoPoint.latitude,
            lineStartLon = point1.geoPoint.longitude,
            lineEndLat = point2.geoPoint.latitude,
            lineEndLon = point2.geoPoint.longitude
        )

        if (!crossed) {
            val previousSide = lineSide(
                point1.geoPoint.latitude,
                point1.geoPoint.longitude,
                point2.geoPoint.latitude,
                point2.geoPoint.longitude,
                previous.latitude,
                previous.longitude
            )
            val currentSide = lineSide(
                point1.geoPoint.latitude,
                point1.geoPoint.longitude,
                point2.geoPoint.latitude,
                point2.geoPoint.longitude,
                location.latitude,
                location.longitude
            )
            val previousDistanceToLine = gateCrossingEngine.distanceToLineMeters(
                pointLat = previous.latitude,
                pointLon = previous.longitude,
                lineStartLat = point1.geoPoint.latitude,
                lineStartLon = point1.geoPoint.longitude,
                lineEndLat = point2.geoPoint.latitude,
                lineEndLon = point2.geoPoint.longitude
            )
            val currentDistanceToLine = gateCrossingEngine.distanceToLineMeters(
                pointLat = location.latitude,
                pointLon = location.longitude,
                lineStartLat = point1.geoPoint.latitude,
                lineStartLon = point1.geoPoint.longitude,
                lineEndLat = point2.geoPoint.latitude,
                lineEndLon = point2.geoPoint.longitude
            )
            val minDistanceToLine = minOf(previousDistanceToLine, currentDistanceToLine)

            val sideChanged = (previousSide > 0.0 && currentSide < 0.0) || (previousSide < 0.0 && currentSide > 0.0)
            val veryNearLine = minDistanceToLine <= 12.0
            if (sideChanged && veryNearLine) {
                crossed = true
                android.util.Log.d("TrackSessionActivity", "✅ TOLERANT LINE CROSS DETECTED (near-line side change)")
            }
        }

        if (!crossed) {
            return false
        }

        if (!ignoreDebounce && now - lastStartFinishCrossAtMs < startFinishCrossDebounceMs) {
            android.util.Log.d("TrackSessionActivity", "⏸️ Start/finish debounce active (${now - lastStartFinishCrossAtMs}ms)")
            return false
        }

        android.util.Log.d("TrackSessionActivity", "✅ STRICT LINE CROSS DETECTED")
        lastStartFinishCrossAtMs = now
        return true
    }

    private fun lineSide(
        lineStartLat: Double,
        lineStartLon: Double,
        lineEndLat: Double,
        lineEndLon: Double,
        pointLat: Double,
        pointLon: Double
    ): Double {
        val ax = lineStartLon
        val ay = lineStartLat
        val bx = lineEndLon
        val by = lineEndLat
        val px = pointLon
        val py = pointLat
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax)
    }
    
    private fun checkTrackPointProximity(location: Location) {
        if (trackPoints.isEmpty()) return
        
        // For custom circuit tracks with start/finish LINE (4 points total: 2 + 2 duplicate):
        // Check if we're crossing the start/finish line
        if (hasGateBasedTriggering()) {
            val startLine = getStartLinePoints() ?: return
            val finishLine = getFinishLinePoints() ?: return

            // Check line crossing
            if (awaitingStart) {
                if (currentTrackMode == TrackMode.POINT_TO_POINT) {
                    handlePointToPointStagingAndStart(location, startLine.first, startLine.second)
                    return
                }

                val distanceToStartLine = gateCrossingEngine.distanceToLineMeters(
                    pointLat = location.latitude,
                    pointLon = location.longitude,
                    lineStartLat = startLine.first.geoPoint.latitude,
                    lineStartLon = startLine.first.geoPoint.longitude,
                    lineEndLat = startLine.second.geoPoint.latitude,
                    lineEndLon = startLine.second.geoPoint.longitude
                )
                updateAwaitingStartDialog(distanceToStartLine)
                val meters = distanceToStartLine.toInt().coerceAtLeast(0)
                tvLapTime.text = "До старт/финал: ${meters} m"

                // Check initial start/finish line (indices 0 and 1)
                val crossed = checkStartFinishLineCrossing(location, startLine.first, startLine.second)
                if (crossed) {
                    beginTimedSession(location)
                }
                return
            } else if (lapStartTime == 0L) {
                // This should not happen - awaitingStart should handle this case
                android.util.Log.w("TrackSessionActivity", "⚠️ UNEXPECTED: lapStartTime == 0L but not awaitingStart")
                return
            } else {
                val crossed = checkStartFinishLineCrossing(
                    location = location,
                    point1 = finishLine.first,
                    point2 = finishLine.second,
                    ignoreDebounce = currentTrackMode == TrackMode.POINT_TO_POINT
                )
                if (crossed) {
                    if (currentTrackMode == TrackMode.POINT_TO_POINT) {
                        finalizePointToPointRun()
                        android.util.Log.d("TrackSessionActivity", "✅ POINT_TO_POINT FINISH LINE CROSSED - stopping session")
                        stopRecording()
                        return
                    }

                    val lapElapsedTime = lapTimingEngine.lapElapsedMs(lapStartTime)
                    if (!lapTimingEngine.canCompleteLap(lapStartTime)) {
                        android.util.Log.d("TrackSessionActivity", "⏸️ Crossed line but too soon! Elapsed: ${lapElapsedTime / 1000}s (need ${lapTimingEngine.minLapTimeMs / 1000}s)")
                        return
                    }
                    
                    android.util.Log.d("TrackSessionActivity", "🎉 CROSSED START/FINISH LINE!")
                    android.util.Log.d("TrackSessionActivity", "🏁 LAP COMPLETED!")
                    
                    // 🔍 DIAGNOSTIC: Log finish timing information
                    android.util.Log.d("TrackSessionActivity", "⏰ FINISH TIMING DIAGNOSTIC:")
                    android.util.Log.d("TrackSessionActivity", "   Finish line detected at: ${System.currentTimeMillis()}")
                    android.util.Log.d("TrackSessionActivity", "   Lap duration: ${lapElapsedTime}ms")
                    android.util.Log.d("TrackSessionActivity", "   Total GPS points: ${currentLapData.routePoints.size}")
                    if (currentLapData.routePoints.isNotEmpty()) {
                        android.util.Log.d("TrackSessionActivity", "   Last GPS point timestamp: ${currentLapData.routePoints.last().timestamp}")
                    }
                    
                    // Record sector and lap
                    val sectorTime = System.currentTimeMillis() - sectorStartTime
                    sectorTimes.add(sectorTime)
                    sectorDistances.add(sectorDistanceAccum)
                    lastSectorChangeAtMs = System.currentTimeMillis()
                    
                    recordLap()
                    
                    // Stay at lap completion line for next lap
                    currentTrackPointIndex = startFinishLineIndices[2]
                }
                return
            }
        }
        
        // Fallback for old single-point logic (shouldn't happen for new custom tracks)
        val targetIndex = if (awaitingStart) 0 else currentTrackPointIndex
        if (targetIndex >= trackPoints.size) return
        
        val trackPoint = trackPoints[targetIndex]
        val trackLocation = Location("track").apply {
            latitude = trackPoint.geoPoint.latitude
            longitude = trackPoint.geoPoint.longitude
        }
        val distance = location.distanceTo(trackLocation)
        val threshold = if (awaitingStart) startProximityMeters else sectorProximityMeters
        
        android.util.Log.d("TrackSessionActivity", "📍 Check point $targetIndex: distance=${distance.toInt()}m, threshold=${threshold.toInt()}m, awaitingStart=$awaitingStart")
        
        val shouldTrigger = distance < threshold
        
        if (shouldTrigger) {
            // Anti-bounce: For lap completion point, require minimum lap time
            if (!awaitingStart && targetIndex == trackPoints.size - 1 && currentTrackMode == TrackMode.CIRCUIT) {
                val lapElapsedTime = lapTimingEngine.lapElapsedMs(lapStartTime)
                
                if (!lapTimingEngine.canCompleteLap(lapStartTime)) {
                    android.util.Log.d("TrackSessionActivity", "⏸️ Too soon for lap! Elapsed: ${lapElapsedTime / 1000}s (need ${lapTimingEngine.minLapTimeMs / 1000}s)")
                    return
                }
            }
            
            android.util.Log.d("TrackSessionActivity", "🎉 TRIGGERED at index $targetIndex")
            
            // Handle awaiting start
            if (awaitingStart) {
                android.util.Log.d("TrackSessionActivity", "🚀 SESSION STARTED!")
                beginTimedSession(location)
                return
            }
            
            // Record sector
            val sectorTime = System.currentTimeMillis() - sectorStartTime
            sectorTimes.add(sectorTime)
            sectorDistances.add(sectorDistanceAccum)
            lastSectorChangeAtMs = System.currentTimeMillis()
            
            // Check if this is START_FINISH point (lap boundary)
            val isStartFinish = targetIndex < trackPointTypes.size && 
                               trackPointTypes[targetIndex] == com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.START_FINISH
            
            if (bestLapTime != Long.MAX_VALUE && bestSectorTimes.isNotEmpty()) {
                val justCompletedSectors = sectorTimes.size
                val currentElapsedMs = sectorTimes.sum()
                val isSlower = if (isStartFinish) {
                    currentElapsedMs >= bestLapTime
                } else {
                    val bestElapsedMs = bestSectorTimes.take(justCompletedSectors).sum()
                    currentElapsedMs >= bestElapsedMs
                }
                speedGauge.lockPredictiveColor(isSlower)
            }
            
            // Update best sector
            if (currentSector < bestSectorTimes.size) {
                if (sectorTime < bestSectorTimes[currentSector]) {
                    bestSectorTimes[currentSector] = sectorTime
                }
            } else {
                bestSectorTimes.add(sectorTime)
            }
            
            currentSector++
            sectorStartTime = System.currentTimeMillis()
            sectorDistanceAccum = 0f
            
            // Advance to next point
            currentTrackPointIndex++
            
            // Check if we completed a lap (reached end with START_FINISH point)
            if (currentTrackPointIndex >= trackPoints.size) {
                android.util.Log.d("TrackSessionActivity", "🏁 LAP COMPLETED!")
                if (currentTrackMode == TrackMode.POINT_TO_POINT) {
                    finalizePointToPointRun()
                    android.util.Log.d("TrackSessionActivity", "✅ POINT_TO_POINT FINISH REACHED - stopping session")
                    stopRecording()
                    return
                }
                recordLap()
                // For custom circuit tracks with 2 points (start/finish duplicated), 
                // go back to point 1 (the duplicate) to check for next lap completion
                // Point 0 is ONLY for initial start detection
                currentTrackPointIndex = if (trackPoints.size == 2) 1 else 0
            }
        }
    }

    private fun beginTimedSession(location: Location) {
        awaitingStart = false
        lapStartTime = System.currentTimeMillis()
        if (sessionTelemetryStartWallTimeMs <= 0L) {
            sessionTelemetryStartWallTimeMs = lapStartTime
        }
        updateSessionVideoSyncMarkerIfNeeded()
        updateProjectedRouteDistance(location)
        projectedRouteDistanceAtLapStartMeters = currentProjectedRouteDistanceMeters
        updateCurrentLapBadge(1)
        updateLapDistanceProgress(0f)
        android.util.Log.d("TrackSessionActivity", "⏰ LAP START TIME SET: $lapStartTime")

        val currentLocation = lastLocation
        if (currentLocation != null) {
            val currentSpeedKmh = currentLocation.speed * 3.6f
            currentLapData.speedData.add(currentSpeedKmh)
            currentLapData.routePoints.add(
                RoutePoint(
                    geoPoint = com.example.clinometer.GeoPoint(currentLocation.latitude, currentLocation.longitude),
                    speed = currentSpeedKmh,
                    angle = currentCalibratedLean,
                    timestamp = 0L,
                    absoluteTime = currentLocation.time
                )
            )
        }

        val updatedRoutePoints = currentLapData.routePoints.map { routePoint ->
            routePoint.copy(timestamp = routePoint.absoluteTime - lapStartTime)
        }
        currentLapData = currentLapData.copy(
            startTime = lapStartTime,
            routePoints = updatedRoutePoints.toMutableList()
        )

        sectorStartTime = lapStartTime
        currentSector = 0
        currentTrackPointIndex = if (hasGateBasedTriggering()) 2 else 1
        statsFilteredLongG = 0f
        statsFilteredLatG = 0f
        maxLeanAngle = 0f
        maxLeanLeftAngle = 0f
        maxLeanRightAngle = 0f
        currentLongitudinalG = 0f
        currentLateralG = 0f
        noGyroGpsLongStatsSmooth = 0f
        noGyroLeanLatGSmooth = 0f
        speedGauge.resetGForceHistory()
        resetPeakDetectors()
        smoothedConfidence = 1f
        latestRollRateDegPerSec = 0f
        gyroIntegratedLeanDeg = 0f
        hasGyroIntegratedLean = false
        leanGyroIntegrationTimestampNs = 0L
        filteredAngle = 0f
        offsetAngle = profileLeanOffsetDeg + runtimeLeanOffsetDeg

        linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        geomagneticRotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        handler.post(updateRunnable)
        sectorDistanceAccum = 0f
        lapDistanceAccum = 0f
        lastLocationTimeMs = location.time
        resetPredictiveEstimatorState(clearGauge = true)
        speedGauge.unlockPredictiveColor()
        updateMotoGForceCard()
        dismissAwaitingStartDialog()
    }

    private fun handlePointToPointStagingAndStart(location: Location, startLineA: TrackPoint, startLineB: TrackPoint) {
        val distanceToStartLine = gateCrossingEngine.distanceToLineMeters(
            pointLat = location.latitude,
            pointLon = location.longitude,
            lineStartLat = startLineA.geoPoint.latitude,
            lineStartLon = startLineA.geoPoint.longitude,
            lineEndLat = startLineB.geoPoint.latitude,
            lineEndLon = startLineB.geoPoint.longitude
        )

        updateAwaitingStartDialog(distanceToStartLine)

        if (distanceToStartLine <= pointToPointStartHintMeters) {
            val meters = distanceToStartLine.toInt().coerceAtLeast(0)
            tvLapTime.text = "До старта: ${meters} m"
        } else {
            tvLapTime.text = "Приближи се до старта, за да започне състезанието"
        }

        val strictCrossed = checkStartFinishLineCrossing(
            location = location,
            point1 = startLineA,
            point2 = startLineB,
            ignoreDebounce = true
        )

        if (strictCrossed) {
            val gpsCourseDirectionConfirmed = resolvePointToPointStartCourseDirectionConfirmation(
                startLineA = startLineA,
                startLineB = startLineB,
                location = location
            )
            val directionConfirmed = gpsCourseDirectionConfirmed ?: isStartDirectionConfirmed()

            if (directionConfirmed) {
                android.util.Log.d(
                    "TrackSessionActivity",
                    "POINT_TO_POINT start line crossed - run started (gpsDirection=$gpsCourseDirectionConfirmed)"
                )
                beginTimedSession(location)
            } else {
                tvLapTime.text = "Премини през линията в посока към трасето"
            }
        }
    }

    private fun resolvePointToPointStartCourseDirectionConfirmation(
        startLineA: TrackPoint,
        startLineB: TrackPoint,
        location: Location
    ): Boolean? {
        val previous = previousLocationForCrossing ?: return null
        val courseReferencePoint = resolvePointToPointCourseReferencePoint() ?: return null

        val targetSide = lineSide(
            startLineA.geoPoint.latitude,
            startLineA.geoPoint.longitude,
            startLineB.geoPoint.latitude,
            startLineB.geoPoint.longitude,
            courseReferencePoint.latitude,
            courseReferencePoint.longitude
        )
        if (kotlin.math.abs(targetSide) < 1e-9) return null

        val previousSide = lineSide(
            startLineA.geoPoint.latitude,
            startLineA.geoPoint.longitude,
            startLineB.geoPoint.latitude,
            startLineB.geoPoint.longitude,
            previous.latitude,
            previous.longitude
        )
        val currentSide = lineSide(
            startLineA.geoPoint.latitude,
            startLineA.geoPoint.longitude,
            startLineB.geoPoint.latitude,
            startLineB.geoPoint.longitude,
            location.latitude,
            location.longitude
        )

        val targetIsPositive = targetSide > 0.0
        val previousWasOutside = if (targetIsPositive) previousSide <= 0.0 else previousSide >= 0.0
        val currentIsInside = if (targetIsPositive) currentSide >= 0.0 else currentSide <= 0.0
        val confirmed = previousWasOutside && currentIsInside

        android.util.Log.d(
            "TrackSessionActivity",
            "POINT_TO_POINT GPS start direction confirmation: targetSide=$targetSide, previousSide=$previousSide, currentSide=$currentSide, confirmed=$confirmed"
        )

        return confirmed
    }

    private fun resolvePointToPointCourseReferencePoint(): GeoPoint? {
        val startLine = getStartLinePoints()
        if (startLine != null) {
            val startCenter = GeoPoint(
                latitude = (startLine.first.latitude + startLine.second.latitude) / 2.0,
                longitude = (startLine.first.longitude + startLine.second.longitude) / 2.0
            )
            progressRoutePoints.firstOrNull { point ->
                distanceMeters(startCenter, point) > 20f
            }?.let { return it }
        }

        val firstSnapHelperIndex = trackPointTypes.indexOfFirst {
            it == com.example.clinometer.tracking.CustomTrack.TrackPoint.PointType.SNAP_HELPER
        }
        if (firstSnapHelperIndex in trackPoints.indices) {
            return trackPoints[firstSnapHelperIndex].geoPoint
        }

        if (startFinishLineIndices.size >= 4) {
            val finishStart = trackPoints.getOrNull(startFinishLineIndices[2])
            val finishEnd = trackPoints.getOrNull(startFinishLineIndices[3])
            if (finishStart != null && finishEnd != null) {
                return GeoPoint(
                    latitude = (finishStart.latitude + finishEnd.latitude) / 2.0,
                    longitude = (finishStart.longitude + finishEnd.longitude) / 2.0
                )
            }
        }

        return trackPoints.getOrNull(2)?.geoPoint
    }

    private fun updateStartDirectionGate(deviceLinearAccel: FloatArray) {
        if (!isRecording || !awaitingStart) return

        val hasDirectionalCalibration = DragCalibration.isUniversalCalibrated && hasSmartMotionCalibration
        if (!hasDirectionalCalibration) {
            startForwardFilteredMs2 = 0f
            startLateralFilteredMs2 = 0f
            startDirectionGoodSamples = 0
            return
        }

        val forwardMs2 = DragCalibration.getSignedForwardAccelerationFromLinear(deviceLinearAccel).coerceAtLeast(0f)
        val lateralMs2 = kotlin.math.abs(DragCalibration.getSignedLateralAccelerationFromLinear(deviceLinearAccel))

        startForwardFilteredMs2 =
            startDirectionFilterAlpha * forwardMs2 + (1f - startDirectionFilterAlpha) * startForwardFilteredMs2
        startLateralFilteredMs2 =
            startDirectionFilterAlpha * lateralMs2 + (1f - startDirectionFilterAlpha) * startLateralFilteredMs2

        val directionalPulse =
            startForwardFilteredMs2 > startDirectionMinForwardMs2 &&
                startForwardFilteredMs2 > startLateralFilteredMs2 * startDirectionRatio

        if (directionalPulse) {
            startDirectionGoodSamples = (startDirectionGoodSamples + 1).coerceAtMost(startDirectionRequiredSamples + 2)
        } else {
            startDirectionGoodSamples = (startDirectionGoodSamples - 1).coerceAtLeast(0)
        }
    }

    private fun isStartDirectionConfirmed(): Boolean {
        val hasDirectionalCalibration = DragCalibration.isUniversalCalibrated && hasSmartMotionCalibration
        if (!hasDirectionalCalibration) {
            return true
        }
        return startDirectionGoodSamples >= startDirectionRequiredSamples
    }

    private fun updateAwaitingStartDialog(distanceToStartLineMeters: Double) {
        val dialog = awaitingStartDialog ?: return
        val messageView = awaitingStartMessageView ?: return

        val meters = distanceToStartLineMeters.toInt().coerceAtLeast(0)
        val message = buildAwaitingStartMessage(
            metersLabel = "${meters} м",
            isNearStartLine = distanceToStartLineMeters <= pointToPointStartHintMeters
        )
        if (dialog.isShowing) {
            messageView.text = message
        }
    }

    private fun resolveAwaitingStartDistanceMeters(): Double? {
        val cachedDistance = currentDistanceToStartLineMeters
        if (cachedDistance.isFinite() && cachedDistance >= 0f) {
            return cachedDistance.toDouble()
        }

        val location = lastLocation ?: resolveLastKnownTrackLocation() ?: return null
        return if (hasGateBasedTriggering()) {
            val startLine = getStartLinePoints() ?: return null
            gateCrossingEngine.distanceToLineMeters(
                pointLat = location.latitude,
                pointLon = location.longitude,
                lineStartLat = startLine.first.geoPoint.latitude,
                lineStartLon = startLine.first.geoPoint.longitude,
                lineEndLat = startLine.second.geoPoint.latitude,
                lineEndLon = startLine.second.geoPoint.longitude
            )
        } else {
            val startPoint = trackPoints.firstOrNull() ?: return null
            distanceToTrackPoint(location, startPoint).toDouble()
        }
    }

    private fun resolveLastKnownTrackLocation(): Location? {
        if (!::locationManager.isInitialized) return null
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return sequenceOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { location -> location.time }
    }

    private fun buildAwaitingStartMessage(metersLabel: String, isNearStartLine: Boolean): CharSequence {
        val prefix = "Разстояние от старт/финал: "
        val body = if (isNearStartLine) {
            "Когато пресечеш старт/финал линията, измерването започва."
        } else {
            "Приближи се до старт/финал линията, за да започне състезанието."
        }

        val fullMessage = "$prefix$metersLabel\n$body"
        val spannable = SpannableStringBuilder(fullMessage)
        val valueStart = prefix.length
        val valueEnd = valueStart + metersLabel.length
        spannable.setSpan(
            ForegroundColorSpan(getColor(R.color.primary_color)),
            valueStart,
            valueEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private fun finalizePointToPointRun() {
        if (!isRecording || lapStartTime <= 0L) return

        val finishTimestamp = System.currentTimeMillis()
        val runElapsedMs = finishTimestamp - lapStartTime

        currentLap = 1
        totalLaps = 1
        updateCurrentLapBadge(1)
        updateLapDistanceProgress(1f)

        lapTimes.clear()
        lapTimes.add(runElapsedMs)
        bestLapTime = runElapsedMs
        updateLapSummaryCards(runElapsedMs)

        currentLapData = currentLapData.copy(
            lapNumber = 1,
            endTime = finishTimestamp
        )
        if (lapData.isEmpty()) {
            lapData.add(currentLapData)
        } else {
            lapData[lapData.lastIndex] = currentLapData
        }

        maybePersistCustomMeasuredDistance(lapDistanceAccum)

        showToast("Финиш! Време: ${formatTime(runElapsedMs)}")
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                }
            }

            CAMERA_PERMISSION_REQUEST -> {
                val requestedMode = pendingSessionCameraMode
                pendingSessionCameraMode = null
                if (requestedMode != null && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    applySessionCameraMode(requestedMode)
                } else {
                    showToast(getString(R.string.track_camera_permission_denied))
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        updateTopRightWeatherHeader()
        val latestProfileId = ProfileStorage.getSelectedProfileId(this)
        if (latestProfileId != -1L) {
            reloadLeanCalibrationForProfile(latestProfileId, forceResetRuntime = !isRecording)
            reloadMotionCalibrationForProfile(latestProfileId)
        }
        // Re-register while activity is visible for immediate lean response.
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        geomagneticRotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (sessionCameraMode != SessionCameraMode.OFF && activeVideoRecording == null) {
            bindSessionCameraPreview()
        }
    }
    override fun onPause() {
        super.onPause()
        // Keep sensors active while recording so tracking continues with locked screen.
        if (isRecording) return
        accelerometer?.let { sensorManager.unregisterListener(this, it) }
        linearAccelSensor?.let { sensorManager.unregisterListener(this, it) }
        rotationVector?.let { sensorManager.unregisterListener(this, it) }
        geomagneticRotationVector?.let { sensorManager.unregisterListener(this, it) }
        gravitySensor?.let { sensorManager.unregisterListener(this, it) }
        magnetometer?.let { sensorManager.unregisterListener(this, it) }
        gyroscope?.let { sensorManager.unregisterListener(this, it) }
        unbindSessionCamera()
    }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        locationManager.removeUpdates(this)
        releaseTrackWakeLock()
        unbindSessionCamera()
        soundManager.release()
    }

    private fun acquireTrackWakeLock() {
        if (trackWakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        trackWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:TrackSessionWakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseTrackWakeLock() {
        trackWakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        trackWakeLock = null
    }
    private fun addLapToUI(lapNumber: Int, lapTime: String, isBestLap: Boolean) {
        tvNoLaps.visibility = android.view.View.GONE
        val inflater = layoutInflater
        val lapView = inflater.inflate(R.layout.lap_item_session_template, llLapsContainer, false)
        enforceDpTextSizes(lapView)
        lapView.tag = lapNumber
        val tvLapNumber = lapView.findViewById<TextView>(R.id.tvLapNumber)
        val tvLapTime = lapView.findViewById<TextView>(R.id.tvLapTime)
        tvLapNumber.text = getString(R.string.track_lap_label, lapNumber)
        tvLapTime.text = lapTime
        llLapsContainer.addView(lapView, 0)
        if (isBestLap) {
            markBestLapInUi(lapNumber)
        }
    }

    private fun markBestLapInUi(bestLapNumber: Int) {
        for (index in 0 until llLapsContainer.childCount) {
            val lapView = llLapsContainer.getChildAt(index)
            val tvLapNumber = lapView.findViewById<TextView>(R.id.tvLapNumber)
            val tvBestLapMarker = lapView.findViewById<TextView>(R.id.tvBestLapMarker)
            val lapNumber = lapView.tag as? Int ?: continue
            val baseLabel = getString(R.string.track_lap_label, lapNumber)
            tvLapNumber.text = baseLabel
            tvBestLapMarker.visibility = if (lapNumber == bestLapNumber) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
    private fun updateGauge() {
        if (!awaitingStart) {
            lastLocation?.let { location ->
                val currentSpeed = location.speed * 3.6f
                speedGauge.setSpeed(currentSpeed)
            }
        } else {
            speedGauge.setSpeed(0f)
        }
        
        val referenceLapSeconds = when (predictiveGapSource) {
            PredictiveGapSource.SESSION_BEST -> {
                if (bestLapTime != Long.MAX_VALUE) bestLapTime / 1000f else Float.NaN
            }
            PredictiveGapSource.TRACK_BEST -> {
                if (trackBestLapTime != Long.MAX_VALUE) trackBestLapTime / 1000f
                else if (bestLapTime != Long.MAX_VALUE) bestLapTime / 1000f
                else Float.NaN
            }
        }
        val referenceDistanceMeters = when (predictiveGapSource) {
            PredictiveGapSource.SESSION_BEST -> bestLapDistance
            PredictiveGapSource.TRACK_BEST -> {
                when {
                    trackLengthMeters > 100f -> trackLengthMeters
                    bestLapDistance > 100f -> bestLapDistance
                    else -> 0f
                }
            }
        }

        // Lap-level predictive gap: predictedLap = elapsed + (referenceDistance - traveled) / effectiveSpeed
        if (
            isRecording &&
            !awaitingStart &&
            lapStartTime > 0 &&
            referenceLapSeconds.isFinite() &&
            referenceLapSeconds > 0f &&
            referenceDistanceMeters > 0f
        ) {
            val nowMs = System.currentTimeMillis()
            val elapsedLapSeconds = (nowMs - lapStartTime) / 1000f

            // Avoid spike right after sector crossing
            if (nowMs - lastSectorChangeAtMs < sectorCrossFreezeMs) {
                if (!lastPredictedLapSeconds.isNaN()) {
                    speedGauge.setPredictiveGap(lastPredictedLapSeconds, referenceLapSeconds)
                    updatePredictiveGapCard(lastPredictedLapSeconds, referenceLapSeconds)
                }
                return
            }

            val rollingSpeedMs = getRollingSpeedMs()
            val avgLapSpeedMs = if (elapsedLapSeconds > 0f && lapDistanceAccum > 0f) lapDistanceAccum / elapsedLapSeconds else 0f
            val effectiveSpeedMs = when {
                rollingSpeedMs > 0f && avgLapSpeedMs > 0f -> {
                    val blended = 0.70f * rollingSpeedMs + 0.30f * avgLapSpeedMs
                    blended.coerceIn(
                        kotlin.math.min(rollingSpeedMs, avgLapSpeedMs) * 0.85f,
                        kotlin.math.max(rollingSpeedMs, avgLapSpeedMs) * 1.05f
                    )
                }
                rollingSpeedMs > 0f -> rollingSpeedMs
                else -> avgLapSpeedMs
            }
            val traveledDistanceMeters = resolvePredictiveTraveledMeters(referenceDistanceMeters)
            val remainingDistance = (referenceDistanceMeters - traveledDistanceMeters).coerceAtLeast(0f)

            if (remainingDistance > 20f && effectiveSpeedMs < 1.0f) {
                if (!displayedPredictedLapSeconds.isNaN()) {
                    speedGauge.setPredictiveGap(displayedPredictedLapSeconds, referenceLapSeconds)
                    updatePredictiveGapCard(displayedPredictedLapSeconds, referenceLapSeconds)
                }
                return
            }

            var predictedLapSeconds = if (effectiveSpeedMs > 0f) {
                elapsedLapSeconds + (remainingDistance / effectiveSpeedMs)
            } else {
                Float.POSITIVE_INFINITY
            }

            // Track-best mode was too optimistic with speed-only projection.
            // Cross-check against current progress vs reference and keep the conservative side.
            if (predictiveGapSource == PredictiveGapSource.TRACK_BEST) {
                val progress01 = if (referenceDistanceMeters > 0f) {
                    (traveledDistanceMeters / referenceDistanceMeters).coerceIn(0f, 0.998f)
                } else {
                    0f
                }

                if (progress01 >= 0.03f) {
                    val expectedElapsedAtProgress = referenceLapSeconds * progress01
                    val progressBasedPrediction = referenceLapSeconds + (elapsedLapSeconds - expectedElapsedAtProgress)
                    if (progressBasedPrediction.isFinite()) {
                        val earlyTrust = ((progress01 - 0.03f) / 0.22f).coerceIn(0f, 1f)
                        val stabilizedProgressPrediction =
                            referenceLapSeconds + earlyTrust * (progressBasedPrediction - referenceLapSeconds)
                        predictedLapSeconds = max(predictedLapSeconds, stabilizedProgressPrediction)
                    }
                }
            }

            if (!predictedLapSeconds.isFinite()) {
                if (!displayedPredictedLapSeconds.isNaN()) {
                    speedGauge.setPredictiveGap(displayedPredictedLapSeconds, referenceLapSeconds)
                    updatePredictiveGapCard(displayedPredictedLapSeconds, referenceLapSeconds)
                }
                return
            }

            // Rate-limit based on real elapsed compute interval
            if (!lastPredictedLapSeconds.isNaN()) {
                val dtSec = if (lastPredictionComputeAtMs > 0L) {
                    ((nowMs - lastPredictionComputeAtMs) / 1000f).coerceIn(0.05f, 1.2f)
                } else {
                    0.1f
                }
                val maxDelta = 1.2f * dtSec
                val delta = predictedLapSeconds - lastPredictedLapSeconds
                val clamped = delta.coerceIn(-maxDelta, maxDelta)
                predictedLapSeconds = lastPredictedLapSeconds + clamped
            }
            lastPredictedLapSeconds = predictedLapSeconds
            lastPredictionComputeAtMs = nowMs

            // Exponential smoothing for display, adaptive by speed
            val alpha = if (effectiveSpeedMs < 5f) 0.10f else 0.16f
            displayedPredictedLapSeconds = if (displayedPredictedLapSeconds.isNaN()) predictedLapSeconds
                else (alpha * predictedLapSeconds + (1 - alpha) * displayedPredictedLapSeconds)

            // Throttle UI updates for readability
            val nowUi = System.currentTimeMillis()
            if (nowUi - lastPredictionDisplayUpdateMs >= predictionDisplayIntervalMs) {
                lastPredictionDisplayUpdateMs = nowUi
                speedGauge.setPredictiveGap(displayedPredictedLapSeconds, referenceLapSeconds)
                updatePredictiveGapCard(displayedPredictedLapSeconds, referenceLapSeconds)
            }
        } else {
            resetPredictiveGapCard()
            resetPredictiveEstimatorState(clearGauge = true)
        }
        
        // G-forces are now set inside processLinearAccelerationAndUpdate with proper projection
        if (isMotorcycle && leanAngleData.isNotEmpty()) {
            val leanAngle = abs(leanAngleData.last())
            speedGauge.setLeanAngle(leanAngle)
        }
    }

    private fun getRollingSpeedMs(): Float {
        if (speedSamplesMs.isEmpty()) return 0f
        var sum = 0f
        speedSamplesMs.forEach { sum += it.second }
        return sum / speedSamplesMs.size
    }
    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    override fun onBackPressed() {
        if (isRecording) {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Потвърждение").setMessage("Сигурни ли сте? Ще се изгубят всички данни от измерването!").setPositiveButton("ДА, излез") { _, _ ->
                stopRecordingWithoutSaving()
                super.onBackPressed()
                overridePendingTransition(0, 0)
            }.setNegativeButton("Отказ", null).show()
        } else {
            super.onBackPressed()
            overridePendingTransition(0, 0)
        }
    }

    private fun showAwaitingStartDialog() {
        val initialDistanceMeters = resolveAwaitingStartDistanceMeters()
        val message = buildAwaitingStartMessage(
            metersLabel = initialDistanceMeters
                ?.toInt()
                ?.coerceAtLeast(0)
                ?.let { meters -> "$meters м" }
                ?: "-- м",
            isNearStartLine = initialDistanceMeters?.let { distance ->
                distance <= pointToPointStartHintMeters
            } ?: false
        )
        dismissAwaitingStartDialog()

        val dialogView = layoutInflater.inflate(R.layout.dialog_track_awaiting_start, null)
        val messageView = dialogView.findViewById<TextView>(R.id.tvAwaitingStartMessage)
        val cancelButton = dialogView.findViewById<TextView>(R.id.btnAwaitingStartCancel)

        messageView.text = message
        cancelButton.setOnClickListener {
            dismissAwaitingStartDialog()
            stopRecordingWithoutSaving(
                showDataLostToast = false,
                resumeIdleLocationTracking = true
            )
        }

        awaitingStartMessageView = messageView
        awaitingStartDialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        awaitingStartDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        awaitingStartDialog?.show()
    }

    private fun dismissAwaitingStartDialog() {
        awaitingStartDialog?.dismiss()
        awaitingStartDialog = null
        awaitingStartMessageView = null
    }
    private fun createOuting() {
        Thread {
            try {
                val finalMaxLeanLeft = maxLeanLeftAngle
                val finalMaxLeanRight = maxLeanRightAngle
                val finalMaxLeanAngle = max(maxLeanAngle, max(finalMaxLeanLeft, finalMaxLeanRight))
                // Requested behavior: duration must be the sum of all recorded lap times.
                val totalLapDurationMs = lapTimes.sum()
                val sessionDurationFormatted = formatTimeWithMillis3(totalLapDurationMs)
                val bestLapFormatted = if (bestLapTime == Long.MAX_VALUE) "--:--.---" else formatTimeWithMillis3(bestLapTime)
                val envPrefs = PreferenceManager.getDefaultSharedPreferences(this)
                val cachedTemperature = envPrefs.getFloat("cached_temperature", Float.NaN)
                val cachedHumidity = envPrefs.getInt("cached_humidity", -1)
                val cachedWindKph = envPrefs.getFloat("cached_wind_kph", Float.NaN)
                val cachedWeatherIcon = envPrefs.getInt("cached_weather_icon", -1)
                val sessionTemperature = if (!cachedTemperature.isNaN()) {
                    UnitsManager.formatTemperature(cachedTemperature, this, decimals = 0)
                } else {
                    val unit = UnitsManager.getTemperatureUnit(this)
                    "--${unit.symbol}"
                }
                val sessionHumidity = if (cachedHumidity in 0..100) {
                    "${cachedHumidity}%"
                } else {
                    "--%"
                }
                val sessionWindSpeed = if (!cachedWindKph.isNaN()) {
                    String.format(Locale.getDefault(), "%.0f km/h", cachedWindKph)
                } else {
                    "-- km/h"
                }
                val sessionVideoUri = savedSessionVideoUri.orEmpty()
                val sessionVideoPath = savedSessionVideoPath.orEmpty()
                val sessionVideoCamera = savedSessionVideoCameraLabel.orEmpty()
                val sessionVideoStartOffsetMs = savedSessionVideoStartOffsetMs ?: 0L
                val sessionVideoElapsedAtStartMs = savedSessionVideoStartSessionElapsedMs ?: -sessionVideoStartOffsetMs
                val sessionVideoOverlayExported = savedSessionVideoOverlayExported
                val outingData = mapOf(
                    "trackName" to trackName,
                    "mode" to if (currentTrackMode == TrackMode.POINT_TO_POINT) "point_to_point" else "circuit",
                    "date" to java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(sessionStartTime)),
                    "time" to java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sessionStartTime)),
                    "duration" to sessionDurationFormatted,
                    "totalLaps" to totalLaps.toString(),
                    "bestLapTime" to bestLapFormatted,
                    "maxSpeed" to String.format(Locale.getDefault(), "%.0f km/h", maxSpeed),
                    "maxAcceleration" to String.format("%.2f G", maxAcceleration),
                    "maxBraking" to String.format("%.2f G", maxBraking),
                    "maxCorneringLeftG" to String.format("%.2f G", maxCorneringLeftG),
                    "maxCorneringRightG" to String.format("%.2f G", maxCorneringRightG),
                    "maxCorneringG" to String.format("%.2f G", max(maxCorneringLeftG, maxCorneringRightG)),
                    "maxLeanAngle" to String.format("%.1f°", finalMaxLeanAngle),
                    "maxLeanLeftAngle" to String.format("%.1f°", finalMaxLeanLeft),
                    "maxLeanRightAngle" to String.format("%.1f°", finalMaxLeanRight),
                    "temperature" to sessionTemperature,
                    "humidity" to sessionHumidity,
                    "windSpeed" to sessionWindSpeed,
                    "weatherIcon" to cachedWeatherIcon.toString(),
                    "videoUri" to sessionVideoUri,
                    "videoPath" to sessionVideoPath,
                    "videoCamera" to sessionVideoCamera,
                    "videoSessionStartOffsetMs" to sessionVideoStartOffsetMs.toString(),
                    "videoSessionElapsedAtStartMs" to sessionVideoElapsedAtStartMs.toString(),
                    "videoOverlayExported" to sessionVideoOverlayExported.toString()
                )
                val isResumeSession = intent.getBooleanExtra("resume_session", false)
                val sessionIdRaw = if (isResumeSession) {
                    // For resume, sessionId already contains profileId prefix, so we need to extract the raw part
                    val fullSessionId = intent.getStringExtra("session_id") ?: trackId
                    if (fullSessionId.matches(Regex("\\d+_.*"))) {
                        // Remove profileId prefix: "123_serres_circuit_..." -> "serres_circuit_..."
                        fullSessionId.substringAfter("_")
                    } else {
                        fullSessionId
                    }
                } else {
                    val date = outingData["date"] ?: ""
                    val time = outingData["time"] ?: ""
                    val timestamp = System.currentTimeMillis()
                    "${trackId}_${date}_${time.replace(":", "")}_${timestamp}"
                }
                saveOutingDataWithSessionId(outingData, sessionIdRaw)
                val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
                val currentProfileId = ProfileStorage.getSelectedProfileId(this)
                val sessionIdWithProfile = "${currentProfileId}_${sessionIdRaw}"
                val outingNumber = sharedPrefs.getInt("${sessionIdWithProfile}_outing_count", 1)
                runOnUiThread {
                    showToast(getString(R.string.track_session_saved, totalLaps, bestLapFormatted))
                    val intent = Intent(this@TrackSessionActivity, TrackSessionDetailActivity::class.java)
                    intent.putExtra("trackName", trackName)
                    intent.putExtra("trackId", sessionIdWithProfile) // This contains profileId prefix
                    intent.putExtra("outingNumber", outingNumber)
                    intent.putExtra("date", outingData["date"])
                    intent.putExtra("time", outingData["time"])
                    intent.putExtra("duration", outingData["duration"])
                    intent.putExtra("totalLaps", outingData["totalLaps"])
                    intent.putExtra("bestLapTime", outingData["bestLapTime"])
                    intent.putExtra("maxSpeed", outingData["maxSpeed"])
                    intent.putExtra("maxAcceleration", outingData["maxAcceleration"])
                    intent.putExtra("maxBraking", outingData["maxBraking"])
                    intent.putExtra("maxCorneringLeft", outingData["maxCorneringLeftG"])
                    intent.putExtra("maxCorneringRight", outingData["maxCorneringRightG"])
                    intent.putExtra("maxCorneringG", outingData["maxCorneringG"])
                    intent.putExtra("maxLeanAngle", outingData["maxLeanAngle"])
                    startActivity(intent)
                    finish()
                    overridePendingTransition(0, 0)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast(getString(R.string.track_save_error, e.message ?: "Unknown"))
                    val intent = Intent(this@TrackSessionActivity, MainContainerActivity::class.java).apply {
                        putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_TRACK)
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                }
            }
        }.start()
    }
    private fun saveOutingDataWithSessionId(outingData: Map<String, String>, sessionIdRaw: String) {
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val sessionId = "${currentProfileId}_${sessionIdRaw}"
        val outingNumber = sharedPrefs.getInt("${sessionId}_outing_count", 0) + 1
        val editor = sharedPrefs.edit()
        editor.putString("${sessionId}_outing_${outingNumber}_date", outingData["date"])
        editor.putString("${sessionId}_outing_${outingNumber}_time", outingData["time"])
        editor.putString("${sessionId}_outing_${outingNumber}_mode", outingData["mode"])
        editor.putString("${sessionId}_outing_${outingNumber}_duration", outingData["duration"])
        editor.putString("${sessionId}_outing_${outingNumber}_laps", outingData["totalLaps"])
        editor.putString("${sessionId}_outing_${outingNumber}_best_lap", outingData["bestLapTime"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_speed", outingData["maxSpeed"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_acceleration", outingData["maxAcceleration"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_braking", outingData["maxBraking"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_cornering_left", outingData["maxCorneringLeftG"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_cornering_right", outingData["maxCorneringRightG"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_cornering", outingData["maxCorneringG"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_lean_angle", outingData["maxLeanAngle"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_lean_left", outingData["maxLeanLeftAngle"])
        editor.putString("${sessionId}_outing_${outingNumber}_max_lean_right", outingData["maxLeanRightAngle"])
        editor.putString("${sessionId}_outing_${outingNumber}_temperature", outingData["temperature"])
        editor.putString("${sessionId}_outing_${outingNumber}_humidity", outingData["humidity"])
        editor.putString("${sessionId}_outing_${outingNumber}_wind_speed", outingData["windSpeed"])
        editor.putInt("${sessionId}_outing_${outingNumber}_weather_icon", outingData["weatherIcon"]?.toIntOrNull() ?: -1)
        val videoUri = outingData["videoUri"].orEmpty()
        val videoPath = outingData["videoPath"].orEmpty()
        val videoCamera = outingData["videoCamera"].orEmpty()
        val videoSessionStartOffsetMs = outingData["videoSessionStartOffsetMs"]?.toLongOrNull() ?: 0L
        val videoSessionElapsedAtStartMs = outingData["videoSessionElapsedAtStartMs"]?.toLongOrNull() ?: -videoSessionStartOffsetMs.coerceAtLeast(0L)
        val videoOverlayExported = outingData["videoOverlayExported"].toBoolean()
        if (videoUri.isNotBlank()) {
            editor.putString("${sessionId}_outing_${outingNumber}_video_uri", videoUri)
        } else {
            editor.remove("${sessionId}_outing_${outingNumber}_video_uri")
        }
        if (videoPath.isNotBlank()) {
            editor.putString("${sessionId}_outing_${outingNumber}_video_path", videoPath)
        } else {
            editor.remove("${sessionId}_outing_${outingNumber}_video_path")
        }
        if (videoCamera.isNotBlank()) {
            editor.putString("${sessionId}_outing_${outingNumber}_video_camera", videoCamera)
        } else {
            editor.remove("${sessionId}_outing_${outingNumber}_video_camera")
        }
        editor.putLong("${sessionId}_outing_${outingNumber}_video_session_start_offset_ms", videoSessionStartOffsetMs)
        editor.putLong("${sessionId}_outing_${outingNumber}_video_session_elapsed_at_start_ms", videoSessionElapsedAtStartMs)
        editor.putBoolean("${sessionId}_outing_${outingNumber}_video_overlay_exported", videoOverlayExported)
        editor.putInt("${sessionId}_outing_count", outingNumber)
        for (i in lapTimes.indices) {
            editor.putString("${sessionId}_outing_${outingNumber}_lap_${i + 1}", formatTime(lapTimes[i]))
        }
        
        // Save lap data
        saveLapData(editor, sessionId, outingNumber)
        saveVideoExportLapSnapshot(editor, sessionId, outingNumber)
        
        editor.apply()
        ProfileSessionSummaryStore.refreshTrackSummary(this, currentProfileId)
    }

    private fun saveVideoExportLapSnapshot(
        editor: android.content.SharedPreferences.Editor,
        sessionId: String,
        outingNumber: Int
    ) {
        val snapshotKey = "${sessionId}_outing_${outingNumber}_video_export_lap_data"
        val hasSnapshot = currentLapData.startTime > 0L &&
            (currentLapData.routePoints.isNotEmpty() ||
                currentLapData.timestamps.isNotEmpty() ||
                currentLapData.speedData.isNotEmpty())
        if (!hasSnapshot) {
            editor.remove(snapshotKey)
            return
        }

        val lastSavedLap = lapData.lastOrNull()
        val isDuplicateOfLastSavedLap = lastSavedLap != null &&
            lastSavedLap.lapNumber == currentLapData.lapNumber &&
            lastSavedLap.startTime == currentLapData.startTime &&
            lastSavedLap.endTime == currentLapData.endTime
        if (isDuplicateOfLastSavedLap) {
            editor.remove(snapshotKey)
            return
        }

        val gson = com.google.gson.Gson()
        editor.putString(snapshotKey, gson.toJson(currentLapData))
    }
    
    private fun saveLapData(editor: android.content.SharedPreferences.Editor, sessionId: String, outingNumber: Int) {
        val gson = com.google.gson.Gson()
        android.util.Log.d("TrackSessionActivity", "Saving ${lapData.size} lap data entries")
        
        // Apply smart map matching to each lap
        val processedLapData = mutableListOf<LapData>()
        
        for (lap in lapData) {
            if (lap.routePoints.isNotEmpty() && lap.sensorData.isNotEmpty()) {
                processedLapData.add(lap)
            } else {
                processedLapData.add(lap)
            }
        }
        
        for (i in processedLapData.indices) {
            val lap = processedLapData[i]
            val lapKey = "${sessionId}_outing_${outingNumber}_lap_data_${i + 1}"
            val json = gson.toJson(lap)
            editor.putString(lapKey, json)
            android.util.Log.d("TrackSessionActivity", "Saved lap ${i + 1}: ${lap.routePoints.size} route points (smart map matched), ${lap.speedData.size} speed samples, ${lap.accelerationData.size} accel samples")
        }
        editor.putInt("${sessionId}_outing_${outingNumber}_lap_data_count", processedLapData.size)
        android.util.Log.d("TrackSessionActivity", "Set lap data count to ${processedLapData.size}")
    }
    private fun clearActiveSession() {
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("has_active_session", false).remove("active_track_id").remove("active_track_name").apply()
    }
}
