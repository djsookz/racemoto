package com.revix.app

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
import android.widget.ImageButton
import android.widget.ImageView
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
import com.revix.app.ForegroundService
import com.revix.app.lean.MotorcycleLeanAccelGate
import com.revix.app.racebox.RaceBoxDebugGate
import com.revix.app.racebox.RaceBoxManager
import com.revix.app.racebox.RaceBoxProtocol
import com.revix.app.utils.GnssSpeedSanitizer
import com.revix.app.utils.LapTimeFormatter
import com.revix.app.utils.RoutePointDistance
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.revix.app.drag.CameraSettingsSheet
import com.revix.app.drag.DragRunVideoCapabilities
import com.revix.app.drag.DragRunVideoCaps
import com.revix.app.drag.DragRunVideoSettings
import com.revix.app.track.TrackSessionVideoSettings
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
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import com.revix.app.settings.SoundManager
import com.revix.app.settings.UnitsManager
import android.view.Gravity
import android.widget.LinearLayout
import com.revix.app.data.ProfileSessionSummaryStore
import com.revix.app.data.ProfileStorage
import com.revix.app.main.MainContainerActivity
import com.revix.app.main.ui.ScreenKeepOnController
import com.revix.app.track.TrackGForceChartSmoothing
import com.revix.app.track.TrackLapDataStore
import com.revix.app.track.TrackLapStreamWriter
import com.revix.app.track.catalog.TrackMode
import com.revix.app.track.GateCrossingTiming
import com.revix.app.track.TrackGateDirection
import com.revix.app.track.TrackSessionVideoOverlayService
import com.revix.app.video.ConcurrentPhoneCameras
import com.revix.app.video.DualCameraHolder
import com.revix.app.video.DualCameraPipLayout
import com.revix.app.video.PhoneDualCameraBinder
import com.revix.app.track.interpolateLocationAtGateCrossing
import com.revix.app.track.resolveGateCrossingTiming
import com.revix.app.track.session.TrackGateCrossingEngine
import com.revix.app.track.session.TrackLapTimingEngine
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
    private lateinit var lapSummaryRow: View
    private lateinit var cardSessionCurrent: View
    private lateinit var cardSessionBestLap: View
    private lateinit var cardSessionLastLap: View
    private lateinit var tvSessionCurrentTimerValue: TextView
    private lateinit var tvSessionCurrentTimerLabel: TextView
    private lateinit var tvSessionP2pRunMeta: TextView
    private var llSessionCurrentBody: LinearLayout? = null
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
    private var secondaryTelemetryRow: View? = null
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
    private lateinit var btnCameraFeedFullscreen: MaterialButton
    private lateinit var cameraFeedHud: TrackSessionLiveHudView
    private lateinit var pipSwapButton: ImageButton
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
    private var tvTrackWeatherWind: TextView? = null
    private var ivTrackWeatherCondition: ImageView? = null
    private var tvTrackNameHeader: TextView? = null
    private var tvLapProgressPercent: TextView? = null
    private var tvLapProgressDistance: TextView? = null
    private var speedDeltaGrid: View? = null
    private var trackId: String = ""
    private var trackName: String = ""
    private var vehicleName: String = ""
    private var vehicleSpecs: String = ""
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
    private var sessionCameraPreview: Preview? = null
    private var cameraVideoCapture: VideoCapture<Recorder>? = null
    private var cameraHudStreamObserved = false
    private var isCameraFeedFullscreen = false
    private val cameraFullscreenSavedVisibilities = mutableMapOf<Int, Int>()
    private var cameraFullscreenSavedPadding = IntArray(4)
    private var cameraFullscreenSavedCameraLp: LinearLayout.LayoutParams? = null
    private var cameraFullscreenSavedLeftColLp: LinearLayout.LayoutParams? = null
    private var activeVideoRecording: Recording? = null
    private var isVideoRecordingActive = false
    private var sessionVideoRawFile: File? = null
    private var sessionVideoFinalFile: File? = null
    private var videoRecordingStartElapsedRealtimeMs: Long = 0L
    private var videoRecordingStartWallTimeMs: Long = 0L
    private var videoSyncMarkerOffsetMs: Long? = null
    private var sessionOrientationLockedForVideo: Boolean = false
    private val savedSessionVideoClips = mutableListOf<TrackSessionVideoClip>()
    private var pendingCreateOutingAfterVideoFinalize = false
    private var pendingDiscardVideoAfterFinalize = false
    private var pendingRestartVideoAfterInterrupt = false
    private var isFinalizingSessionVideo = false
    private var awaitingVideoProcessingForOuting = false
    private var sessionVideoDiscardRequested = false
    private var sessionVideoInterruptedToastShown = false
    private var sessionTelemetryStartWallTimeMs: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val hideCameraPreviewHeaderRunnable = Runnable { hideCameraPreviewHeaderChrome() }
    private var trackWakeLock: PowerManager.WakeLock? = null
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDisplay()
            handler.postDelayed(this, 100)
        }
    }
    private val trackPoints = mutableListOf<TrackPoint>()
    private val trackPointTypes = mutableListOf<com.revix.app.tracking.CustomTrack.TrackPoint.PointType>() // Types of each point
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
    /** Live append-only writer for the in-progress lap (full 25 Hz GPS + IMU on disk). */
    private var lapStream: TrackLapStreamWriter? = null
    private var liveSessionId: String = ""
    private var liveOutingNumber: Int = 0
    private var sessionDistanceKmAccum: Double = 0.0
    private var streamFlushCounter: Int = 0
    private var sessionStartTime: Long = 0L
    private var sessionEndTime: Long = 0L
    private val lapTimes = mutableListOf<Long>()
    private var liveHudMiniMapPoints: List<GeoPoint> = emptyList()
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
    private var displayedPredictedLapSeconds: Float = Float.NaN
    private var awaitingStart: Boolean = false
    private var awaitingStartDialog: androidx.appcompat.app.AlertDialog? = null
    private var awaitingStartMessageView: TextView? = null
    private var lastLocationTimeMs: Long = 0L
    private var raceBoxLongGSmooth = 0f
    private var raceBoxLatGSmooth = 0f
    private var hasRaceBoxGSmooth = false
    /** Phone HUD EMA over the same 5 Hz chartQuality G (mirrors RaceBox HUD LP). */
    private var phoneLongGSmooth = 0f
    private var phoneLatGSmooth = 0f
    private var hasPhoneGSmooth = false
    /** Display-only G follow. Stats, peaks, and freeze logic keep using current/measurement. */
    private var displayHudLongG = 0f
    private var displayHudLatG = 0f
    private var hasDisplayHudG = false
    private var lastRaceBoxTelemetryPersistMs = 0L
    private var lastPhoneTelemetryPersistMs = 0L
    /** Light LP on HUD G (RaceBox raw and phone chartQuality share this alpha). */
    private val raceBoxGSmoothAlpha = 0.20f
    private fun hudGDisplayAlpha(): Float = if (isMotorcycle) 0.11f else 0.16f
    private val raceBoxTelemetryMinIntervalMs = 20L // match Drag longitudinal sample rate
    private val chartLongLpState = TrackGForceChartSmoothing.LowPassState()
    private val chartLatLpState = TrackGForceChartSmoothing.LowPassState()
    /** Inertial G after drag-style 5 Hz LP (forward accel → negative), for lap chart series. */
    private var chartQualityLongitudinalG = 0f
    private var chartQualityLateralG = 0f
    private var lastPredictionDisplayUpdateMs: Long = 0L
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
    private val leanDisplayDeadbandDeg: Float = 0.6f
    private val leanDisplayDirectionThresholdDeg: Float = 2.2f
    private val leanDisplaySmoothingAlpha: Float = 0.22f
    private val leanDisplaySmoothingAlphaGyro: Float = 0.28f
    private val forceNoGyroLeanLogicOnGyro: Boolean = false
    private val leanDisplaySnapToZeroDeg: Float = 0.25f
    private var previousLocationForCrossing: Location? = null
    private var lastStartFinishCrossAtMs: Long = 0L
    private val startFinishCrossDebounceMs: Long = 1500L
    private val pointToPointStartHintMeters: Double = 1500.0
    private val synthesizedGateWidthMeters = 12.0
    private val gateDetectionEndPadMeters = 28.0
    private val minimumUsableGateLengthMeters = 2.0f
    private var startForwardFilteredMs2: Float = 0f
    private var startLateralFilteredMs2: Float = 0f
    private var startDirectionGoodSamples: Int = 0
    private val startDirectionFilterAlpha = 0.28f
    private val startDirectionMinForwardMs2 = 0.26f
    private val startDirectionRatio = 1.55f
    private val startDirectionRequiredSamples = 3
    private var trackLengthMeters: Float = 0f
    private var customTrackPersistedCalibrated: Boolean = false
    private var customTrackCalibratedInSession: Boolean = false
    private val sectorProgressWaypoints = mutableListOf<GeoPoint>()
    private var sectorProgressLengthMeters: Float = 0f
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

    // Position-based predictive delta-T (professional approach, like VBOX/Racelogic):
    // align the current position to the same position on the reference lap and show the
    // real time difference there, instead of extrapolating the whole lap from speed.
    // Reference lap is stored as a monotonic table of (progress-from-start meters -> elapsed ms).
    private val currentLapRefDistances = mutableListOf<Float>()
    private val currentLapRefElapsedMs = mutableListOf<Long>()
    private val bestLapRefDistances = mutableListOf<Float>()
    private val bestLapRefElapsedMs = mutableListOf<Long>()
    private var bestLapRefTotalMs: Long = 0L
    // Track-best reference, rebuilt from the persisted best historical lap's telemetry.
    private val trackBestRefDistances = mutableListOf<Float>()
    private val trackBestRefElapsedMs = mutableListOf<Long>()
    private var trackBestRefTotalMs: Long = 0L
    private val maxPredictiveReferenceSamples = 4000
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val CAMERA_PERMISSION_REQUEST = 1002
        private const val MIN_DISTANCE_FOR_UPDATE = 1f
        private const val MIN_TIME_FOR_UPDATE = 100L
        private const val TRACK_UI_PREFS = "track_ui_prefs"
        private const val SESSION_VIDEO_PREROLL_MS = 3_000L
        private const val CAMERA_PREVIEW_HEADER_HIDE_DELAY_MS = 5_000L
        private const val MIN_USABLE_SESSION_VIDEO_BYTES = 12_288L
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
    /**
     * Separate gravity reference used ONLY for HUD/inertial G extraction.
     * Madgwick [gravity] still updates for lean; this freezes during sustained
     * accel/cornering so centripetal force is not absorbed as "gravity".
     */
    private val gForceGravity = FloatArray(3) { 0f }
    private var gForceGravityInitialized = false
    private var gForceGravityFrozen = false
    private var gForceGravityFreezeCounter = 0
    private val gForceGravityFreezeCountThreshold = 3
    private val gForceGravityFreezeMagThreshold = 0.38f
    private val gForceGravityUnfreezeMagThreshold = 0.20f
    private val gForceGravityTrackAlpha = 0.94f
    private val madgwick = MadgwickAHRS(beta = 0.033f)
    private val latestGyroForMadgwick = FloatArray(3)
    private var lastMadgwickUpdateNs: Long = 0L
    private val rotationMatrix = FloatArray(9) { 0f }
    private val worldAccel = FloatArray(3) { 0f }
    private var displayLX = 0f
    private var displayLY = 0f
    private var currentLongitudinalG = 0f
    private var lastTelemetrySpeedKmh = 0f
    private var currentLateralG = 0f
    // Measurement stream: calibrated G before display smoothing (stats + lap telemetry).
    private var measurementLongitudinalG = 0f
    private var measurementLateralG = 0f
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
    private val statsFilterAlpha = 0.60f
    private val statsDeadbandG = 0.02f
    private val statsPeakEntryHysteresisG = 0.01f
    private val statsPeakExitHysteresisG = 0.008f
    private val statsPeakHoldMs = 50L
    private val noGyroGpsLongStatsFilterAlpha = 0.45f
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
    private val raceBoxTrackSampleListener: (RaceBoxProtocol.Sample) -> Unit = listener@{ sample ->
        if (!RaceBoxDebugGate.shouldOverridePhoneGps(this)) return@listener
        runOnUiThread { applyRaceBoxTelemetry(sample) }
    }
    private var latestRollRateDegPerSec: Float = 0f
    private var latestYawRateDegPerSec: Float = 0f
    private val leanAccelGate = MotorcycleLeanAccelGate()
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
        val vehicleIdentity = HudVehicleIdentity.from(selectedProfile, this)
        vehicleName = vehicleIdentity.title
        vehicleSpecs = vehicleIdentity.specs
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
        if (!com.revix.app.billing.ProGate.ensureDragOrTrack(
                this,
                com.revix.app.billing.ProAccess.Feature.TRACK
            )
        ) {
            finish()
            return
        }
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
    private fun initializeViews(preserveActiveSessionUi: Boolean = false) {
        tvCurrentLap = findViewById(R.id.tvCurrentLap)
        lapSummaryRow = findViewById(R.id.lapSummaryRow)
        cardSessionCurrent = findViewById(R.id.cardSessionCurrent)
        cardSessionBestLap = findViewById(R.id.cardSessionBestLap)
        cardSessionLastLap = findViewById(R.id.cardSessionLastLap)
        tvSessionCurrentTimerValue = findViewById(R.id.tvSessionCurrentTimerValue)
        tvSessionCurrentTimerLabel = findViewById(R.id.tvSessionCurrentTimerLabel)
        tvSessionP2pRunMeta = findViewById(R.id.tvSessionP2pRunMeta)
        llSessionCurrentBody = findViewById(R.id.llSessionCurrentBody)
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
        secondaryTelemetryRow = findViewById(R.id.secondaryTelemetryRow)
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
        cameraPreviewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        cameraPreviewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        cameraPreviewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            layoutPipSwapButton()
        }
        llCameraPreviewHeader = findViewById(R.id.llCameraPreviewHeader)
        llCameraPreviewPlaceholder = findViewById(R.id.llCameraPreviewPlaceholder)
        tvCameraPreviewPlaceholder = findViewById(R.id.tvCameraPreviewPlaceholder)
        tvCameraPreviewStatus = findViewById(R.id.tvCameraPreviewStatus)
        btnCameraModeInline = findViewById(R.id.btnCameraModeInline)
        btnCameraFeedFullscreen = findViewById(R.id.btnCameraFeedFullscreen)
        cameraFeedHud = findViewById(R.id.cameraFeedHud)
        pipSwapButton = findViewById(R.id.btnTrackPipSwap)
        pipSwapButton.setOnClickListener { swapDualCameras() }
        cameraFeedHud.attachPreview(cameraPreviewView)
        cameraFeedHud.setSessionConfig(
            isMotorcycle = isMotorcycle,
            speedUnitLabel = UnitsManager.getSpeedUnitSymbol(this).uppercase(Locale.getDefault()),
            speedFactor = UnitsManager.convertSpeed(1f, UnitsManager.getSpeedUnit(this)),
            miniMapPoints = liveHudMiniMapPoints,
            trackName = trackName,
            vehicleName = vehicleName,
            vehicleSpecs = vehicleSpecs,
            startMarker = hudStartMarkerPoint(),
            followMiniMap = TrackSessionVideoSettings.map3dEnabled(this)
        )
        if (!cameraHudStreamObserved) {
            cameraHudStreamObserved = true
            cameraPreviewView.previewStreamState.observe(this) { state ->
                if (state == PreviewView.StreamState.STREAMING) {
                    refreshLiveHudPreviewAspect()
                }
            }
        }
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
        tvTrackWeatherWind = findViewById(R.id.tvTrackWeatherWind)
        ivTrackWeatherCondition = findViewById(R.id.ivTrackWeatherCondition)
        tvTrackNameHeader = findViewById(R.id.tvTrackNameHeader)
        tvLapProgressPercent = findViewById(R.id.tvLapProgressPercent)
        tvLapProgressDistance = findViewById(R.id.tvLapProgressDistance)
        speedDeltaGrid = findViewById(R.id.speedDeltaGrid)
        enforceDpTextSizes(findViewById(android.R.id.content))
        topTelemetryRow.visibility = View.VISIBLE
        cardTopLeanTelemetry.visibility = if (isMotorcycle && !usesMergedMotoLeanGForce()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        btnTopLeanZero.visibility = if (isMotorcycle) View.VISIBLE else View.GONE
        if (!preserveActiveSessionUi) {
            resetCarGaugeDynamicScale()
        }
        configureTelemetryProfileUi()
        // Keep one authoritative G-force surface from XML for all profiles.
        motoGForceContainer.visibility = View.VISIBLE
        speedGauge.visibility = View.GONE
        if (!preserveActiveSessionUi) {
            resetPredictiveGapCard()
            updateCurrentLapBadge(displayCurrentLapNumber())
            progressLapDistance.max = lapProgressMax
            updateLapDistanceProgress(0f)
            updateTopSpeedTelemetry(0f)
            updateTopLeanTelemetry(0f)
            updateMotoGForceCard()
            updateLapSummaryCards()
        } else {
            progressLapDistance.max = lapProgressMax
        }
        updateTopRightWeatherHeader()
        updateTrackNameHeader()
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

    private fun usesCompactLiveSessionHeader(): Boolean {
        return speedDeltaGrid != null &&
            resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    }

    private fun usesSpeedDeltaPills(): Boolean = speedDeltaGrid != null

    private fun usesLiveSessionLapCard(): Boolean = tvLapProgressPercent != null

    private fun usesMergedMotoLeanGForce(): Boolean = isMotorcycle

    private fun updateTrackNameHeader() {
        val name = trackName.trim().ifBlank { "Track" }
        tvTrackNameHeader?.text = name.uppercase(Locale.getDefault())
    }

    private fun updateTopRightWeatherHeader() {
        val tempView = tvTrackWeatherTemp ?: return
        val humidityView = tvTrackWeatherHumidity
        val windView = tvTrackWeatherWind
        val iconView = ivTrackWeatherCondition
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val cachedTemp = prefs.getFloat("cached_temperature", Float.NaN)
        val cachedHumidity = prefs.getInt("cached_humidity", -1)
        val cachedWindKph = prefs.getFloat("cached_wind_kph", Float.NaN)
        val cachedWeatherIcon = prefs.getInt("cached_weather_icon", -1)
        val compact = usesCompactLiveSessionHeader() || iconView != null

        tempView.text = if (!cachedTemp.isNaN()) {
            if (compact) UnitsManager.formatTemperature(cachedTemp, this, decimals = 0)
            else "TEMP ${UnitsManager.formatTemperature(cachedTemp, this, decimals = 0)}"
        } else {
            val unit = UnitsManager.getTemperatureUnit(this)
            if (compact) "--${unit.symbol}" else "TEMP --${unit.symbol}"
        }

        humidityView?.text = if (cachedHumidity in 0..100) {
            if (compact) "${cachedHumidity}%" else "HUM ${cachedHumidity}%"
        } else {
            if (compact) "--%" else "HUM --%"
        }

        val speedUnit = UnitsManager.getSpeedUnit(this)
        windView?.text = if (!cachedWindKph.isNaN() && cachedWindKph >= 0f) {
            val converted = UnitsManager.convertSpeed(cachedWindKph, speedUnit)
            "${converted.toInt()} ${speedUnit.symbol}"
        } else {
            getString(R.string.track_hud_weather_wind_placeholder)
        }

        if (iconView != null) {
            val iconRes = if (cachedWeatherIcon != 0 && cachedWeatherIcon != -1) {
                cachedWeatherIcon
            } else {
                R.drawable.ic_weather_cloudy
            }
            iconView.setImageResource(iconRes)
        }
    }

    private fun configureTelemetryProfileUi() {
        val cardSpacingPx = dpToPx(8f)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val compactHeader = usesCompactLiveSessionHeader()
        val speedParams = cardTopSpeedTelemetry.layoutParams as LinearLayout.LayoutParams
        if (compactHeader) {
            speedParams.width = 0
            speedParams.weight = 1f
            speedParams.marginEnd = dpToPx(6f)
        } else if (!isLandscape && isMotorcycle) {
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
        if (compactHeader) {
            predictiveParams.width = 0
            predictiveParams.height = dpToPx(72f)
            predictiveParams.weight = 1f
            predictiveParams.marginStart = dpToPx(6f)
            predictiveParams.marginEnd = 0
        } else if (!isLandscape) {
            predictiveParams.width = 0
            predictiveParams.height = dpToPx(if (isMotorcycle) 126f else 96f)
            predictiveParams.weight = if (isMotorcycle) 1.05f else 1f
            predictiveParams.marginEnd = if (isMotorcycle) cardSpacingPx else 0
        }
        cardPredictiveLap.layoutParams = predictiveParams

        val leanParams = cardTopLeanTelemetry.layoutParams as LinearLayout.LayoutParams
        if (compactHeader) {
            leanParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            leanParams.weight = 0f
            leanParams.marginEnd = 0
        } else if (!isLandscape) {
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
        if (usesSpeedDeltaPills()) {
            llTopSpeedMotoBody.visibility = View.VISIBLE
            rlTopSpeedCarBody.visibility = View.GONE
            if (!isLandscape) {
                secondaryTelemetryRow?.visibility = if (isMotorcycle && !usesMergedMotoLeanGForce()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        } else {
            llTopSpeedMotoBody.visibility = if (isMotorcycle) View.VISIBLE else View.GONE
            rlTopSpeedCarBody.visibility = if (isMotorcycle) View.GONE else View.VISIBLE
        }
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
            tvMotoGHeader.text = getString(R.string.track_hud_gforce_longitudinal)
            tvMotoTotalLabel.text = getString(R.string.track_hud_total)
            tvMotoLeftFooterLabel.text = getString(R.string.track_hud_max)
            tvMotoRightFooterLabel.text = getString(R.string.track_hud_max)
            tvMotoMaxBrakingValue.setTextColor(Color.parseColor("#EB3E23"))
            tvMotoMaxAccelValue.setTextColor(Color.parseColor("#00E985"))
        } else {
            tvMotoGHeader.text = getString(R.string.track_hud_gforce_lateral_long)
            tvMotoTotalLabel.text = getString(R.string.track_hud_result)
            tvMotoLeftFooterLabel.text = getString(R.string.track_hud_left_x)
            tvMotoRightFooterLabel.text = getString(R.string.track_hud_right_x)
            tvMotoMaxBrakingValue.setTextColor(Color.parseColor("#54B8FF"))
            tvMotoMaxAccelValue.setTextColor(Color.parseColor("#8CCBFF"))
        }
        updatePointToPointTelemetryUi()
    }

    private fun updatePointToPointTelemetryUi() {
        val isPointToPoint = currentTrackMode == TrackMode.POINT_TO_POINT
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val predictiveHeightDp = if (isMotorcycle) 126f else 96f
        val secondaryRowMarginDp = 8f
        val portraitRunTimerTextPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            if (isMotorcycle) 22f else 26f,
            resources.displayMetrics
        )

        if (isPointToPoint) {
            cardPredictiveLap.visibility = View.GONE
            cardSessionBestLap.visibility = View.GONE
            cardSessionLastLap.visibility = View.GONE
            val showSecondaryRow = isMotorcycle && !isLandscape && !usesMergedMotoLeanGForce()
            secondaryTelemetryRow?.visibility = if (showSecondaryRow) View.VISIBLE else View.GONE

            if (!usesCompactLiveSessionHeader() && !usesLiveSessionLapCard()) {
                val currentParams = cardSessionCurrent.layoutParams as LinearLayout.LayoutParams
                currentParams.height = 0
                currentParams.weight = 1f
                currentParams.topMargin = 0
                currentParams.bottomMargin = 0
                cardSessionCurrent.layoutParams = currentParams
            }

            if (!isLandscape) {
                if (usesCompactLiveSessionHeader()) {
                    llSessionCurrentBody?.gravity = Gravity.START
                    tvSessionCurrentTimerLabel.text = getString(R.string.track_hud_run_time)
                    tvSessionCurrentTimerLabel.textSize = 11f
                    tvSessionP2pRunMeta.visibility = View.VISIBLE
                } else {
                    llSessionCurrentBody?.gravity = Gravity.CENTER
                    tvSessionCurrentTimerLabel.text = getString(R.string.track_hud_run_time)
                    tvSessionCurrentTimerLabel.textSize = 10f
                    tvSessionCurrentTimerValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, portraitRunTimerTextPx)
                    tvSessionP2pRunMeta.visibility = View.VISIBLE
                }
            } else if (usesLiveSessionLapCard()) {
                llSessionCurrentBody?.gravity = Gravity.START
                tvSessionCurrentTimerLabel.visibility = View.VISIBLE
                tvSessionCurrentTimerLabel.text = getString(R.string.track_hud_run_time)
                tvSessionCurrentTimerLabel.textSize = 11f
                tvSessionP2pRunMeta.visibility = View.VISIBLE
            } else {
                tvSessionCurrentTimerLabel.visibility = View.GONE
                tvSessionCurrentTimerValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                tvSessionP2pRunMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                tvSessionP2pRunMeta.visibility = View.VISIBLE
                applyLandscapeSpeedExtremaTextSize(15f)
            }

            if (isMotorcycle && !isLandscape) {
                val leanParams = cardTopLeanTelemetry.layoutParams as LinearLayout.LayoutParams
                leanParams.width = LinearLayout.LayoutParams.MATCH_PARENT
                leanParams.weight = 0f
                leanParams.marginEnd = 0
                cardTopLeanTelemetry.layoutParams = leanParams
            }
        } else {
            cardPredictiveLap.visibility = View.VISIBLE
            cardSessionBestLap.visibility = View.VISIBLE
            cardSessionLastLap.visibility = View.VISIBLE
            secondaryTelemetryRow?.visibility = when {
                usesMergedMotoLeanGForce() -> View.GONE
                usesCompactLiveSessionHeader() && !isMotorcycle -> View.GONE
                else -> View.VISIBLE
            }
            tvSessionCurrentTimerLabel.visibility = View.VISIBLE
            tvSessionP2pRunMeta.visibility = View.GONE

            if (!usesCompactLiveSessionHeader() && !usesLiveSessionLapCard()) {
                val currentParams = cardSessionCurrent.layoutParams as LinearLayout.LayoutParams
                currentParams.height = 0
                currentParams.weight = 1f
                currentParams.bottomMargin = if (isLandscape) dpToPx(6f) else dpToPx(4f)
                cardSessionCurrent.layoutParams = currentParams
            }

            if (!isLandscape) {
                if (usesCompactLiveSessionHeader()) {
                    llSessionCurrentBody?.gravity = Gravity.START
                    tvSessionCurrentTimerLabel.text = getString(R.string.track_hud_current_lap_time)
                    tvSessionCurrentTimerLabel.textSize = 11f
                } else {
                    llSessionCurrentBody?.gravity = Gravity.TOP or Gravity.START
                    tvSessionCurrentTimerLabel.text = getString(R.string.track_hud_current)
                    tvSessionCurrentTimerLabel.textSize = 8f
                    tvSessionCurrentTimerValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
                }
            } else if (usesLiveSessionLapCard()) {
                llSessionCurrentBody?.gravity = Gravity.START
                tvSessionCurrentTimerLabel.text = getString(R.string.track_hud_current_lap_time)
                tvSessionCurrentTimerLabel.textSize = 11f
            } else {
                tvSessionCurrentTimerLabel.text = getString(R.string.track_hud_current)
                tvSessionCurrentTimerLabel.textSize = 8f
                tvSessionCurrentTimerValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                applyLandscapeSpeedExtremaTextSize(10f)
            }

            if (!isLandscape && isMotorcycle && !usesCompactLiveSessionHeader()) {
                val leanParams = cardTopLeanTelemetry.layoutParams as LinearLayout.LayoutParams
                leanParams.width = 0
                leanParams.weight = 0.95f
                cardTopLeanTelemetry.layoutParams = leanParams
            }
        }

        if (!isLandscape) {
            val baseMinHeight = 128f
            val extraHeight = if (isPointToPoint && !isMotorcycle) {
                predictiveHeightDp + secondaryRowMarginDp
            } else {
                0f
            }
            cardCameraPreview.minimumHeight = dpToPx(baseMinHeight + extraHeight)
        } else {
            cardCameraPreview.minimumHeight = dpToPx(96f)
        }

        updateP2pRunMetaUi()
    }

    private fun applyLandscapeSpeedExtremaTextSize(valueSp: Float) {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
        if (usesSpeedDeltaPills()) return
        tvTopMaxSpeedValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, valueSp)
        tvTopAvgSpeedValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, valueSp)
        tvTopMaxSpeedValueCar.setTextSize(TypedValue.COMPLEX_UNIT_SP, valueSp)
        tvTopAvgSpeedValueCar.setTextSize(TypedValue.COMPLEX_UNIT_SP, valueSp)
    }

    private fun formatHudGValue(value: Float): String =
        String.format(Locale.US, "%.2f", value)

    private fun formatHudGValueWithUnit(value: Float): String =
        String.format(Locale.US, "%.2fg", value)

    private fun updateP2pRunMetaUi() {
        if (currentTrackMode != TrackMode.POINT_TO_POINT) return

        val distanceKm = (lapDistanceAccum / 1000.0).coerceAtLeast(0.0)
        val distanceLabel = UnitsManager.formatDistance(distanceKm, this, decimals = 2)
        tvSessionP2pRunMeta.text = getString(
            R.string.track_hud_run_meta_format,
            distanceLabel
        )
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
        if (!isCameraFeedFullscreen) {
            previewParams?.let {
                if (!isLandscape) {
                    it.height = 0
                    it.weight = 1f
                    it.topMargin = dpToPx(6f)
                    it.bottomMargin = dpToPx(4f)
                }
                cardCameraPreview.layoutParams = it
            }
        }
        updatePointToPointTelemetryUi()
        cardCameraPreview.visibility = View.VISIBLE

        llCameraPreviewHeader.visibility = View.GONE
        if (::btnCameraFeedFullscreen.isInitialized) {
            btnCameraFeedFullscreen.visibility = View.GONE
        }
        btnCameraModeInline.visibility = if (isCameraEnabled) View.VISIBLE else View.GONE
        cameraPreviewView.visibility = if (isCameraEnabled) View.VISIBLE else View.INVISIBLE
        if (!isCameraEnabled) {
            hidePipSwapButton()
        }
        updateCameraFeedHud()

        if (!isCameraEnabled) {
            cancelCameraPreviewHeaderAutoHide()
            setCameraFeedFullscreen(false)
            val restoredParams = cardCameraPreview.layoutParams as? LinearLayout.LayoutParams
            restoredParams?.let {
                if (!isLandscape) {
                    it.height = 0
                    it.weight = 1f
                    it.topMargin = dpToPx(6f)
                    it.bottomMargin = dpToPx(4f)
                }
                cardCameraPreview.layoutParams = it
            }
            llCameraPreviewPlaceholder.visibility = View.VISIBLE
            btnCameraMode.visibility = View.VISIBLE
            tvCameraPreviewPlaceholder.text = getString(R.string.track_camera_placeholder_hint)
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_off)
            return
        }

        btnCameraMode.visibility = View.GONE
        llCameraPreviewPlaceholder.visibility = View.GONE
        revealCameraPreviewHeaderChrome()
        if (!isVideoRecordingActive) {
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_ready)
        }
    }

    private fun cancelCameraPreviewHeaderAutoHide() {
        handler.removeCallbacks(hideCameraPreviewHeaderRunnable)
        if (::llCameraPreviewHeader.isInitialized) {
            llCameraPreviewHeader.animate().cancel()
            llCameraPreviewHeader.alpha = 1f
        }
        if (::btnCameraFeedFullscreen.isInitialized) {
            btnCameraFeedFullscreen.animate().cancel()
            btnCameraFeedFullscreen.alpha = 1f
        }
    }

    private fun scheduleCameraPreviewHeaderAutoHide() {
        handler.removeCallbacks(hideCameraPreviewHeaderRunnable)
        if (sessionCameraMode == SessionCameraMode.OFF) return
        handler.postDelayed(hideCameraPreviewHeaderRunnable, CAMERA_PREVIEW_HEADER_HIDE_DELAY_MS)
    }

    private fun revealCameraPreviewHeaderChrome() {
        if (!::llCameraPreviewHeader.isInitialized) return
        if (sessionCameraMode == SessionCameraMode.OFF) {
            cancelCameraPreviewHeaderAutoHide()
            llCameraPreviewHeader.visibility = View.GONE
            if (::btnCameraFeedFullscreen.isInitialized) {
                btnCameraFeedFullscreen.visibility = View.GONE
            }
            return
        }
        llCameraPreviewHeader.animate().cancel()
        llCameraPreviewHeader.alpha = 1f
        llCameraPreviewHeader.visibility = View.VISIBLE
        if (::btnCameraFeedFullscreen.isInitialized) {
            btnCameraFeedFullscreen.animate().cancel()
            btnCameraFeedFullscreen.alpha = 1f
            btnCameraFeedFullscreen.visibility = View.VISIBLE
            btnCameraFeedFullscreen.bringToFront()
            updateCameraFeedFullscreenButton()
        }
        scheduleCameraPreviewHeaderAutoHide()
    }

    private fun hideCameraPreviewHeaderChrome() {
        if (!::llCameraPreviewHeader.isInitialized) return
        if (sessionCameraMode == SessionCameraMode.OFF) {
            llCameraPreviewHeader.visibility = View.GONE
            if (::btnCameraFeedFullscreen.isInitialized) {
                btnCameraFeedFullscreen.visibility = View.GONE
            }
            return
        }
        if (llCameraPreviewHeader.visibility != View.VISIBLE &&
            (!::btnCameraFeedFullscreen.isInitialized || btnCameraFeedFullscreen.visibility != View.VISIBLE)
        ) {
            return
        }
        hideCameraChromeView(llCameraPreviewHeader)
        if (::btnCameraFeedFullscreen.isInitialized) {
            hideCameraChromeView(btnCameraFeedFullscreen)
        }
    }

    private fun hideCameraChromeView(view: View) {
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
            }
            .start()
    }

    private fun updateCameraFeedFullscreenButton() {
        if (!::btnCameraFeedFullscreen.isInitialized) return
        if (isCameraFeedFullscreen) {
            btnCameraFeedFullscreen.setIconResource(R.drawable.ic_fullscreen_exit)
            btnCameraFeedFullscreen.contentDescription = getString(R.string.track_camera_exit_fullscreen)
        } else {
            btnCameraFeedFullscreen.setIconResource(R.drawable.ic_fullscreen)
            btnCameraFeedFullscreen.contentDescription = getString(R.string.track_camera_fullscreen)
        }
    }

    private fun toggleCameraFeedFullscreen() {
        if (sessionCameraMode == SessionCameraMode.OFF) return
        setCameraFeedFullscreen(!isCameraFeedFullscreen)
        revealCameraPreviewHeaderChrome()
    }

    private fun setCameraFeedFullscreen(enabled: Boolean) {
        if (enabled == isCameraFeedFullscreen) return
        isCameraFeedFullscreen = enabled
        if (enabled) {
            enterCameraFeedFullscreen()
        } else {
            exitCameraFeedFullscreen()
        }
        updateCameraFeedFullscreenButton()
        cameraPreviewView.post { refreshLiveHudPreviewAspect() }
    }

    private fun cameraFullscreenHideTargets(): List<View> {
        return listOfNotNull(
            findViewById(R.id.topTelemetryRow),
            findViewById(R.id.secondaryTelemetryRow),
            findViewById(R.id.cardCenterTelemetry),
            findViewById(R.id.telemetryGapSpacer),
            findViewById(R.id.cardLaps),
            findViewById(R.id.lapSummaryRow),
            findViewById(R.id.rightLandscapeColumn),
            findViewById(R.id.leftTopTelemetryRow),
            findViewById(R.id.topRightHeaderSlot),
            findViewById(R.id.tvTrackNameHeader)
        )
    }

    private fun enterCameraFeedFullscreen() {
        val contentArea = findViewById<ViewGroup>(R.id.contentArea) ?: return
        cameraFullscreenSavedVisibilities.clear()
        cameraFullscreenSavedPadding = intArrayOf(
            contentArea.paddingLeft,
            contentArea.paddingTop,
            contentArea.paddingRight,
            contentArea.paddingBottom
        )
        cameraFullscreenHideTargets().forEach { view ->
            cameraFullscreenSavedVisibilities[view.id] = view.visibility
            view.visibility = View.GONE
        }
        contentArea.setPadding(0, 0, 0, 0)

        findViewById<View>(R.id.leftLandscapeColumn)?.let { leftCol ->
            val leftLp = leftCol.layoutParams as? LinearLayout.LayoutParams
            if (leftLp != null) {
                cameraFullscreenSavedLeftColLp = LinearLayout.LayoutParams(leftLp)
                leftLp.width = 0
                leftLp.weight = 1f
                leftLp.marginEnd = 0
                leftCol.layoutParams = leftLp
            }
        }

        val cameraLp = cardCameraPreview.layoutParams as? LinearLayout.LayoutParams
        if (cameraLp != null) {
            cameraFullscreenSavedCameraLp = LinearLayout.LayoutParams(cameraLp)
            cameraLp.width = LinearLayout.LayoutParams.MATCH_PARENT
            cameraLp.height = 0
            cameraLp.weight = 1f
            cameraLp.topMargin = 0
            cameraLp.bottomMargin = 0
            cameraLp.marginStart = 0
            cameraLp.marginEnd = 0
            cardCameraPreview.layoutParams = cameraLp
        }
        applyCameraPreviewScaleForHud()
        contentArea.requestLayout()
        cardCameraPreview.post {
            applyCameraPreviewScaleForHud()
            refreshLiveHudPreviewAspect()
            cameraFeedHud.invalidate()
        }
    }

    private fun exitCameraFeedFullscreen() {
        val contentArea = findViewById<ViewGroup>(R.id.contentArea)
        cameraFullscreenSavedVisibilities.forEach { (id, visibility) ->
            findViewById<View>(id)?.visibility = visibility
        }
        cameraFullscreenSavedVisibilities.clear()
        if (contentArea != null && cameraFullscreenSavedPadding.size == 4) {
            contentArea.setPadding(
                cameraFullscreenSavedPadding[0],
                cameraFullscreenSavedPadding[1],
                cameraFullscreenSavedPadding[2],
                cameraFullscreenSavedPadding[3]
            )
        }
        cameraFullscreenSavedLeftColLp?.let { saved ->
            findViewById<View>(R.id.leftLandscapeColumn)?.layoutParams = LinearLayout.LayoutParams(saved)
        }
        cameraFullscreenSavedLeftColLp = null
        cameraFullscreenSavedCameraLp?.let { saved ->
            cardCameraPreview.layoutParams = LinearLayout.LayoutParams(saved)
        }
        cameraFullscreenSavedCameraLp = null
        applyCameraPreviewScaleForHud()
        contentArea?.requestLayout()
        cardCameraPreview.post {
            applyCameraPreviewScaleForHud()
            refreshLiveHudPreviewAspect()
            cameraFeedHud.invalidate()
        }
    }

    private fun applyCameraPreviewScaleForHud() {
        if (!::cameraPreviewView.isInitialized) return
        cameraPreviewView.scaleType = PreviewView.ScaleType.FIT_CENTER
    }

    private fun refreshLiveHudMiniMapShape() {
        val isCircuit = currentTrackMode == TrackMode.CIRCUIT
        val completedLaps = loadCompletedLapsForMiniMap()
        val lapRouteFallback = completedLaps
            .maxByOrNull { lap -> lap.routePoints.size }
            ?.routePoints
            ?.map { point -> point.geoPoint }
            .orEmpty()
        val resolved = TrackMiniMapShapeResolver(this).resolveMiniMapPoints(
            trackId = trackId,
            orderedLaps = completedLaps,
            routeFallback = lapRouteFallback.ifEmpty { progressRoutePoints.toList() },
            isCircuit = isCircuit
        )
        liveHudMiniMapPoints = sequenceOf(resolved, lapRouteFallback, progressRoutePoints.toList())
            .firstOrNull { points -> points.size >= 12 }
            .orEmpty()
        if (::cameraFeedHud.isInitialized) {
            updateCameraFeedHud()
        }
    }

    private fun loadCompletedLapsForMiniMap(): List<LapData> {
        if (liveSessionId.isEmpty() || liveOutingNumber <= 0 || totalLaps <= 0) return emptyList()
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val laps = ArrayList<LapData>(totalLaps)
        for (lapIndex in 1..totalLaps) {
            val lap = TrackLapDataStore.loadLapForMap(
                this,
                prefs,
                liveSessionId,
                liveOutingNumber,
                lapIndex
            )
            if (lap != null && lap.routePoints.size >= 12) {
                laps.add(lap)
            }
        }
        return laps
    }

    private fun updateCameraFeedHud() {
        if (!::cameraFeedHud.isInitialized) return
        val cameraOn = sessionCameraMode != SessionCameraMode.OFF
        cameraFeedHud.visibility = if (cameraOn) View.VISIBLE else View.GONE
        if (!cameraOn) return

        cameraFeedHud.setSessionConfig(
            isMotorcycle = isMotorcycle,
            speedUnitLabel = UnitsManager.getSpeedUnitSymbol(this).uppercase(Locale.getDefault()),
            speedFactor = UnitsManager.convertSpeed(1f, UnitsManager.getSpeedUnit(this)),
            miniMapPoints = liveHudMiniMapPoints,
            trackName = trackName,
            vehicleName = vehicleName,
            vehicleSpecs = vehicleSpecs,
            startMarker = hudStartMarkerPoint(),
            followMiniMap = TrackSessionVideoSettings.map3dEnabled(this)
        )

        val currentLapTimeMs = when {
            isRecording && !awaitingStart && lapStartTime > 0L -> {
                (System.currentTimeMillis() - lapStartTime).coerceAtLeast(0L)
            }
            else -> 0L
        }
        val resultG = sqrt(
            displayHudLongG * displayHudLongG + displayHudLatG * displayHudLatG
        )
        cameraFeedHud.update(
            TrackSessionHudRenderer.Frame(
                currentLapNumber = displayCurrentLapNumber(),
                currentLapTimeMs = currentLapTimeMs,
                lastLapTimeMs = lapTimes.lastOrNull(),
                bestLapTimeMs = bestLapTime.takeIf { it != Long.MAX_VALUE },
                recentLaps = lapTimes.mapIndexed { index, duration ->
                    TrackSessionHudRenderer.RecentLap(index + 1, duration)
                }.takeLast(3).asReversed(),
                speedKmh = if (awaitingStart) 0f else lastTelemetrySpeedKmh,
                geoPoint = lastLocation?.let { location -> GeoPoint(location.latitude, location.longitude) },
                g = TrackSessionHudRenderer.gFrameFromLive(
                    longitudinalG = displayHudLongG,
                    lateralG = displayHudLatG,
                    maxBraking = maxBraking,
                    maxAccel = maxAcceleration,
                    maxLeft = maxCorneringLeftG,
                    maxRight = maxCorneringRightG,
                    maxResultG = max(maxCarResultG, resultG)
                ),
                leanDeg = if (hasDisplayLeanAngle) displayLeanAngle else currentCalibratedLean
            )
        )
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
        flCenterTelemetry.minimumHeight = 0

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
            else -> LapTimeFormatter.ZERO
        }
        val bestValue = if (bestLapTime != Long.MAX_VALUE) formatLapCardTime(bestLapTime) else LapTimeFormatter.PLACEHOLDER
        val lastValue = if (lapTimes.isNotEmpty()) formatLapCardTime(lapTimes.last()) else LapTimeFormatter.PLACEHOLDER
        val bestLapMarker = if (bestLapNumber > 0) "L $bestLapNumber" else "L -"

        tvSessionCurrentTimerValue.text = currentValue
        tvSessionBestLapValue.text = bestValue
        tvSessionBestLapMarker.text = bestLapMarker
        tvSessionLastLapValue.text = lastValue
        updateCameraFeedHud()
    }

    private fun formatCurrentLapCardTime(timeMs: Long): String = LapTimeFormatter.formatMs(timeMs)

    private fun formatLapCardTime(timeMs: Long): String = LapTimeFormatter.formatMs(timeMs)

    private fun resolveMotoAxisMaxG(): Float {
        val dynamic = max(maxBraking, maxAcceleration)
        return dynamic.coerceIn(0.9f, maxDisplayG)
    }

    private fun resetHudDisplayG() {
        displayHudLongG = 0f
        displayHudLatG = 0f
        hasDisplayHudG = false
    }

    private fun stepHudDisplayG(sourceLong: Float, sourceLat: Float) {
        if (!hasDisplayHudG) {
            displayHudLongG = sourceLong
            displayHudLatG = sourceLat
            hasDisplayHudG = true
        } else {
            val alpha = hudGDisplayAlpha()
            displayHudLongG = alpha * sourceLong + (1f - alpha) * displayHudLongG
            displayHudLatG = alpha * sourceLat + (1f - alpha) * displayHudLatG
        }
    }

    private fun applyHudDisplayGToGauges() {
        if (::speedGauge.isInitialized) {
            speedGauge.gForceX = displayHudLatG
            speedGauge.gForceY = displayHudLongG
        }
        if (::gGaugeTrackCar.isInitialized) {
            gGaugeTrackCar.gForceX = displayHudLatG
            gGaugeTrackCar.gForceY = displayHudLongG
        }
    }

    private fun updateMotoGForceCard() {
        val brakingG = max(0f, displayHudLongG)
        val accelG = max(0f, -displayHudLongG)
        val leftX = max(0f, displayHudLatG)
        val rightX = max(0f, -displayHudLatG)
        val resultG = sqrt(displayHudLongG * displayHudLongG + displayHudLatG * displayHudLatG)

        if (isMotorcycle) {
            val totalLongitudinal = maxBraking + maxAcceleration

            tvMotoBrakingValue.text = formatHudGValue(brakingG)
            tvMotoAccelValue.text = formatHudGValue(accelG)
            tvMotoMaxBrakingValue.text = formatHudGValueWithUnit(maxBraking)
            tvMotoMaxAccelValue.text = formatHudGValueWithUnit(maxAcceleration)
            tvMotoTotalValue.text = formatHudGValue(totalLongitudinal)

            pbMotoBraking.progress = (brakingG * 100f).roundToInt().coerceIn(0, pbMotoBraking.max)
            pbMotoAccel.progress = (accelG * 100f).roundToInt().coerceIn(0, pbMotoAccel.max)

            val dotColor = if (displayHudLongG >= 0f) {
                Color.parseColor("#EB3E23")
            } else {
                Color.parseColor("#00E985")
            }
            viewMotoLongitudinalDot.backgroundTintList = ColorStateList.valueOf(dotColor)

            val axisMaxG = resolveMotoAxisMaxG()
            val normalized = (displayHudLongG / axisMaxG).coerceIn(-1f, 1f)
            val mergeLeanGForce = usesMergedMotoLeanGForce()
            rlMotoAxis.post {
                if (mergeLeanGForce) {
                    val axisWidth = viewMotoAxisLine.width
                    val dotWidth = viewMotoLongitudinalDot.width
                    if (axisWidth > 0 && dotWidth > 0) {
                        val halfTravel = ((axisWidth - dotWidth) / 2f).coerceAtLeast(1f)
                        viewMotoLongitudinalDot.translationX = -normalized * halfTravel
                        viewMotoLongitudinalDot.translationY = 0f
                    }
                } else {
                    val axisHeight = viewMotoAxisLine.height
                    val dotHeight = viewMotoLongitudinalDot.height
                    if (axisHeight > 0 && dotHeight > 0) {
                        val halfTravel = ((axisHeight - dotHeight) / 2f).coerceAtLeast(1f)
                        viewMotoLongitudinalDot.translationY = -normalized * halfTravel
                        viewMotoLongitudinalDot.translationX = 0f
                    }
                }
            }
            return
        }

        tvCarLateralLeftValue.text = formatHudGValue(leftX)
        tvCarLateralRightValue.text = formatHudGValue(rightX)
        tvCarBrakingValue.text = formatHudGValue(brakingG)
        tvCarAccelValue.text = formatHudGValue(accelG)
        if (isRecording && !awaitingStart && lapStartTime > 0L) {
            val peakSource = sqrt(
                measurementLongitudinalG * measurementLongitudinalG +
                    measurementLateralG * measurementLateralG
            )
            maxCarResultG = max(maxCarResultG, peakSource)
        }
        tvCarTotalValue.text = formatHudGValue(maxCarResultG)

        pbCarBraking.progress = (brakingG * 100f).roundToInt().coerceIn(0, pbCarBraking.max)
        pbCarAccel.progress = (accelG * 100f).roundToInt().coerceIn(0, pbCarAccel.max)
        pbCarLateralLeft.progress = (leftX * 100f).roundToInt().coerceIn(0, pbCarLateralLeft.max)
        pbCarLateralRight.progress = (rightX * 100f).roundToInt().coerceIn(0, pbCarLateralRight.max)

        gGaugeTrackCar.visualMaxG = resolveCarGaugeVisualMaxG(resultG)
        gGaugeTrackCar.gForceX = displayHudLatG
        gGaugeTrackCar.gForceY = displayHudLongG
        gGaugeTrackCar.peakGForce = maxCarResultG
        updateCameraFeedHud()
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

        val speedStr = UnitsManager.formatSpeedValueWhole(speedValue, this)
        val maxStr = UnitsManager.formatSpeedValueWhole(maxSpeed, this)
        val avgStr = UnitsManager.formatSpeedValueWhole(avgSpeed, this)
        val unit = UnitsManager.getSpeedUnitSymbol(this)
        if (usesSpeedDeltaPills()) {
            tvTopSpeedValue.text = "$speedStr $unit"
            tvTopMaxSpeedValue.text = "$maxStr $unit"
            tvTopAvgSpeedValue.text = "$avgStr $unit"
        } else {
            tvTopSpeedValue.text = speedStr
            tvTopMaxSpeedValue.text = maxStr
            tvTopAvgSpeedValue.text = avgStr
        }
        tvTopSpeedValueCar.text = speedStr
        tvTopMaxSpeedValueCar.text = maxStr
        tvTopAvgSpeedValueCar.text = avgStr
        lastTelemetrySpeedKmh = speedValue
        updateCameraFeedHud()
    }

    private fun updateTopLeanTelemetry(leanAngle: Float = currentCalibratedLean) {
        if (!isMotorcycle) return

        val targetLean = if (abs(leanAngle) < leanDisplayDeadbandDeg) 0f else leanAngle
        val leanSmoothingAlpha = if (useCalibratedBikeLeanAxes()) {
            leanDisplaySmoothingAlphaGyro
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
                if (usesMergedMotoLeanGForce()) {
                    tvTopLeanDirection.visibility = View.INVISIBLE
                } else {
                    tvTopLeanDirection.text = ""
                    tvTopLeanDirection.visibility = View.GONE
                }
            }
            displayLeanAngle < 0f -> {
                tvTopLeanDirection.visibility = View.VISIBLE
                tvTopLeanDirection.text = getString(R.string.track_lean_direction_left)
            }
            else -> {
                tvTopLeanDirection.visibility = View.VISIBLE
                tvTopLeanDirection.text = getString(R.string.track_lean_direction_right)
            }
        }
        updateCameraFeedHud()
    }

    /** True when motorcycle lean should use calibrated bike axes (forward/right), not raw phone X/Y. */
    private fun useCalibratedBikeLeanAxes(): Boolean {
        return gyroscope != null &&
            DragCalibration.isUniversalCalibrated &&
            !forceNoGyroLeanLogicOnGyro
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

        // Offset трябва да ползва същия rightVector, който activateOrientationRuntime току-що зададе.
        updateProfileLeanOffsetForOrientation(isLandscape)
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
        // Prefer calibrated bike RIGHT axis whenever universal axes are active.
        if (useCalibratedBikeLeanAxes()) {
            return DragCalibration.computeLeanOffsetDegFromBaseline(baseline)
        }

        val mag = sqrt(
            baseline[0] * baseline[0] +
                baseline[1] * baseline[1] +
                baseline[2] * baseline[2]
        ).coerceAtLeast(0.0001f)

        // Legacy phone-axis fallback when drag/universal calibration is missing.
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

    private fun displayCurrentLapNumber(): Int {
        if (currentTrackMode == TrackMode.POINT_TO_POINT) return 1
        return (currentLap + 1).coerceAtLeast(1)
    }

    private fun updateCurrentLapBadge(lapNumber: Int) {
        val safeLapNumber = lapNumber.coerceAtLeast(1)
        tvCurrentLap.text = when {
            currentTrackMode == TrackMode.POINT_TO_POINT && safeLapNumber <= 0 ->
                getString(R.string.track_current_run_format, 1)
            currentTrackMode == TrackMode.POINT_TO_POINT ->
                getString(R.string.track_current_run_format, safeLapNumber)
            isCalibratingFirstLap() ->
                getString(R.string.track_current_lap_calibrating_format, safeLapNumber)
            else ->
                getString(R.string.track_current_lap_format, safeLapNumber)
        }
    }

    private fun isCustomTrackSession(): Boolean = trackId.startsWith("custom_")

    private fun isTrackDistanceCalibrated(): Boolean {
        if (!isCustomTrackSession()) return true
        return customTrackPersistedCalibrated || customTrackCalibratedInSession
    }

    private fun isCalibratingFirstLap(): Boolean {
        return isCustomTrackSession() &&
            !isTrackDistanceCalibrated() &&
            lapTimes.isEmpty() &&
            isRecording &&
            !awaitingStart &&
            lapStartTime > 0L &&
            currentTrackMode == TrackMode.CIRCUIT
    }

    private fun rebuildSectorProgressWaypoints(
        customTrackV2: com.revix.app.tracking.CustomTrackDefinitionV2
    ) {
        sectorProgressWaypoints.clear()
        sectorProgressLengthMeters = 0f
        val built = buildCustomRoutePoints(customTrackV2, currentTrackMode)
        if (built.size < 2) return
        sectorProgressWaypoints.addAll(built)
        sectorProgressLengthMeters = TrackSectorProgress.pathLengthMeters(built)
    }

    private fun resolveSectorBasedProgress(): Float? {
        val location = lastLocation ?: return null
        if (sectorProgressWaypoints.size < 2) return null
        return TrackSectorProgress.projectProgress(
            waypoints = sectorProgressWaypoints,
            location = location,
            closeLoop = currentTrackMode == TrackMode.CIRCUIT
        )
    }

    private fun computeGpsDistanceProgress(): Float {
        val targetDistance = resolveLapDistanceTargetMeters()
        if (targetDistance <= 0f || lapDistanceAccum <= 0f) return 0f
        return (lapDistanceAccum / targetDistance).coerceIn(0f, 0.998f)
    }

    private fun resolveLiveLapProgressTarget(): Float {
        if (!isRecording || awaitingStart || lapStartTime <= 0L) return 0f

        val targetDistance = resolveLapDistanceTargetMeters()
        if (targetDistance <= 0f) return 0f

        val gpsProgress = computeGpsDistanceProgress()

        // Custom tracks: checkpoint polyline is only for length estimation, not live projection.
        // GPS odometry stays monotonic and matches how official circuit tracks behave.
        if (isCustomTrackSession()) {
            return gpsProgress
        }

        if (currentTrackMode == TrackMode.POINT_TO_POINT) {
            val projectedProgress = resolveProjectedLapProgress()
            if (projectedProgress != null) {
                val projectedDistance = projectedProgress * targetDistance
                return (projectedDistance.coerceAtLeast(lapDistanceAccum) / targetDistance)
                    .coerceIn(0f, 0.998f)
            }
        }

        return gpsProgress
    }

    private fun markCustomTrackCalibratedFromSession(completedDistanceMeters: Float) {
        if (!isCustomTrackSession()) return
        if (completedDistanceMeters.isFinite() && completedDistanceMeters > 100f) {
            customTrackCalibratedInSession = true
            setTrackLengthMeters(completedDistanceMeters)
        }
        updateCurrentLapBadge(displayCurrentLapNumber())
    }

    private fun setTrackLengthMeters(lengthMeters: Float) {
        if (lengthMeters > 50f) {
            trackLengthMeters = lengthMeters
        }
    }

    private fun buildCustomRoutePoints(
        customTrackV2: com.revix.app.tracking.CustomTrackDefinitionV2,
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

    private fun resolveLapDistanceTargetMeters(): Float {
        return when {
            trackLengthMeters > 100f -> trackLengthMeters
            sectorProgressLengthMeters > 100f -> sectorProgressLengthMeters
            progressRouteLengthMeters > 100f -> progressRouteLengthMeters
            bestLapDistance > 100f -> bestLapDistance
            else -> 0f
        }
    }

    private fun updateLapDistanceProgress(forcedProgress: Float? = null) {
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val rawTarget = forcedProgress?.coerceIn(0f, 1f) ?: resolveLiveLapProgressTarget()

        if (forcedProgress != null) {
            // Forced resets (0f on new lap, 1f on finish) — snap immediately
            smoothedLapProgress = rawTarget
        } else {
            val dtSec = if (lastLapProgressUpdateNs > 0L) {
                ((nowNs - lastLapProgressUpdateNs) / 1_000_000_000.0).toFloat().coerceIn(0.05f, 1.0f)
            } else {
                0.10f
            }
            val speedMs = (lastLocation?.speed ?: 0f).coerceAtLeast(0f)
            val referenceMeters = resolveLapDistanceTargetMeters().coerceAtLeast(
                when {
                    progressRouteLengthMeters > 100f -> progressRouteLengthMeters
                    sectorProgressLengthMeters > 100f -> sectorProgressLengthMeters
                    else -> 1000f
                }
            )

            val desired = rawTarget.coerceAtLeast(smoothedLapProgress)
            val speedStep = if (speedMs > 1f) {
                (speedMs * dtSec / referenceMeters * 1.15f).coerceIn(0.002f, 0.12f)
            } else {
                0.0015f
            }
            smoothedLapProgress = min(desired, smoothedLapProgress + speedStep)
        }

        lastLapProgressUpdateNs = nowNs

        progressLapDistance.progress = (smoothedLapProgress * lapProgressMax).toInt()
        updateLapProgressLabels()
    }

    private fun updateLapProgressLabels() {
        val percentView = tvLapProgressPercent ?: return
        val distanceView = tvLapProgressDistance ?: return
        val percent = (smoothedLapProgress * 100f).roundToInt().coerceIn(0, 100)
        percentView.text = getString(R.string.track_lap_progress_percent, percent)

        val currentKm = (lapDistanceAccum / 1000.0).coerceAtLeast(0.0)
        val targetMeters = resolveLapDistanceTargetMeters()
        val currentLabel = UnitsManager.formatDistance(currentKm, this, decimals = 2)
        if (targetMeters > 100f) {
            val totalLabel = UnitsManager.formatDistance((targetMeters / 1000.0), this, decimals = 2)
            distanceView.text = getString(R.string.track_lap_progress_distance, currentLabel, totalLabel)
        } else {
            distanceView.text = currentLabel
        }
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

    private fun hudStartMarkerPoint(): GeoPoint? {
        val line = getStartLinePoints() ?: getFinishLinePoints() ?: return null
        return GeoPoint(
            latitude = (line.first.latitude + line.second.latitude) / 2.0,
            longitude = (line.first.longitude + line.second.longitude) / 2.0
        )
    }

    private fun lineMidpoint(line: com.revix.app.tracking.GateLine): GeoPoint {
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
            trackPointTypes.add(com.revix.app.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
        }
    }

    private fun addPointToPointGateTriggers(startLine: ResolvedGateLine, finishLine: ResolvedGateLine) {
        trackPoints.add(TrackPoint(startLine.start.latitude, startLine.start.longitude))
        trackPoints.add(TrackPoint(startLine.end.latitude, startLine.end.longitude))
        trackPointTypes.add(com.revix.app.tracking.CustomTrack.TrackPoint.PointType.START)
        trackPointTypes.add(com.revix.app.tracking.CustomTrack.TrackPoint.PointType.START)

        trackPoints.add(TrackPoint(finishLine.start.latitude, finishLine.start.longitude))
        trackPoints.add(TrackPoint(finishLine.end.latitude, finishLine.end.longitude))
        trackPointTypes.add(com.revix.app.tracking.CustomTrack.TrackPoint.PointType.FINISH)
        trackPointTypes.add(com.revix.app.tracking.CustomTrack.TrackPoint.PointType.FINISH)

        startFinishLineIndices.addAll(listOf(0, 1, 2, 3))
    }

    private fun addCircuitPointFallback(center: GeoPoint) {
        val midpoint = TrackPoint(center.latitude, center.longitude)
        trackPoints.addAll(listOf(midpoint, midpoint))
        trackPointTypes.add(com.revix.app.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
        trackPointTypes.add(com.revix.app.tracking.CustomTrack.TrackPoint.PointType.START_FINISH)
    }

    private fun addPointToPointPointFallback(startCenter: GeoPoint, finishCenter: GeoPoint) {
        trackPoints.add(TrackPoint(startCenter.latitude, startCenter.longitude))
        trackPoints.add(TrackPoint(finishCenter.latitude, finishCenter.longitude))
        trackPointTypes.add(com.revix.app.tracking.CustomTrack.TrackPoint.PointType.START)
        trackPointTypes.add(com.revix.app.tracking.CustomTrack.TrackPoint.PointType.FINISH)
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
        customTrackV2: com.revix.app.tracking.CustomTrackDefinitionV2,
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

        val customTrack = com.revix.app.tracking.CustomTrackStorage.loadCustomTrackV2(this, trackId)
            ?: return
        val existing = customTrack.measuredDistanceMeters
        val shouldPersist = when {
            existing == null -> true
            existing < 100f -> true
            else -> kotlin.math.abs(existing - measuredMeters) / existing > 0.08f
        }
        if (!shouldPersist) return

        com.revix.app.tracking.CustomTrackStorage.saveCustomTrackV2(
            this,
            customTrack.copy(measuredDistanceMeters = measuredMeters)
        )
        setTrackLengthMeters(measuredMeters)
        customTrackCalibratedInSession = true
        customTrackPersistedCalibrated = true
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
            onBackPressed()
        }
        btnTopLeanZero.setOnClickListener {
            calibrateLeanZero()
        }
        btnCameraMode.setOnClickListener {
            if (sessionCameraMode != SessionCameraMode.OFF) return@setOnClickListener
            requestOrApplySessionCameraMode(lastSavedSessionCameraMode())
        }
        btnCameraModeInline.setOnClickListener {
            revealCameraPreviewHeaderChrome()
            showSessionCameraSettingsSheet()
        }
        btnCameraFeedFullscreen.setOnClickListener {
            toggleCameraFeedFullscreen()
        }
        cameraFeedHud.setOnClickListener {
            revealCameraPreviewHeaderChrome()
        }
        cameraPreviewView.setOnClickListener {
            revealCameraPreviewHeaderChrome()
        }
        btnPredictiveGapMode.setOnClickListener {
            showPredictiveGapModeMenu()
        }
        cardPredictiveLap.setOnClickListener {
            if (cardPredictiveLap.visibility == View.VISIBLE) {
                showPredictiveGapModeMenu()
            }
        }
    }

    private fun lastSavedSessionCameraMode(): SessionCameraMode {
        return when (TrackSessionVideoSettings.lens(this)) {
            DragRunVideoSettings.LensOption.FRONT -> SessionCameraMode.FRONT
            else -> SessionCameraMode.REAR
        }
    }

    private fun showSessionCameraSettingsSheet() {
        if (isRecording || activeVideoRecording != null) {
            showToast(getString(R.string.track_camera_change_while_recording))
            return
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showToast(getString(R.string.track_camera_unavailable))
            return
        }

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_drag_video_settings, null)
        dialog.setContentView(view)
        CameraSettingsSheet.prepare(dialog)

        val chipHd = view.findViewById<TextView>(R.id.chipQualityHd)
        val chipFhd = view.findViewById<TextView>(R.id.chipQualityFhd)
        val chipUhd = view.findViewById<TextView>(R.id.chipQualityUhd)
        val chip30 = view.findViewById<TextView>(R.id.chipFps30)
        val chip60 = view.findViewById<TextView>(R.id.chipFps60)
        val chipRear = view.findViewById<TextView>(R.id.chipLensRear)
        val chipFront = view.findViewById<TextView>(R.id.chipLensFront)
        val chipBoth = view.findViewById<TextView>(R.id.chipLensBoth)
        val chipMicOn = view.findViewById<TextView>(R.id.chipMicOn)
        val chipMicOff = view.findViewById<TextView>(R.id.chipMicOff)
        val chipMap3d = view.findViewById<TextView>(R.id.chipMap3d)
        val chipMapFull = view.findViewById<TextView>(R.id.chipMapFull)
        val llPhoneCameraSettings = view.findViewById<View>(R.id.llPhoneCameraSettings)
        val llTrackMapStyle = view.findViewById<View>(R.id.llTrackMapStyle)

        fun currentCaps(): DragRunVideoCaps? {
            val provider = cameraProvider ?: return null
            return DragRunVideoCapabilities.query(provider, TrackSessionVideoSettings.lens(this))
        }

        fun selectChip(chip: TextView, selected: Boolean) {
            chip.setBackgroundResource(
                if (selected) R.drawable.bg_drag_video_chip_selected else R.drawable.bg_drag_video_chip
            )
        }

        fun chipVisibility(visible: Boolean): Int {
            return if (visible) View.VISIBLE else View.GONE
        }

        fun refreshChips() {
            val caps = currentCaps()
            val quality = TrackSessionVideoSettings.quality(this)
            val fps = TrackSessionVideoSettings.fps(this)
            val lens = TrackSessionVideoSettings.lens(this)
            val dualSupported = ConcurrentPhoneCameras.isSupported(this, cameraProvider)
            val dual = dualSupported && TrackSessionVideoSettings.dualEnabled(this)
            val mic = TrackSessionVideoSettings.micEnabled(this)
            val map3d = TrackSessionVideoSettings.map3dEnabled(this)
            val allowedFps = caps?.fpsFor(quality) ?: setOf(30, 60)
            chipHd.visibility = chipVisibility(caps == null || DragRunVideoSettings.QualityOption.HD in caps.qualities)
            chipFhd.visibility = chipVisibility(caps == null || DragRunVideoSettings.QualityOption.FHD in caps.qualities)
            chipUhd.visibility = chipVisibility(
                !dual && (caps == null || DragRunVideoSettings.QualityOption.UHD in caps.qualities)
            )
            chip30.visibility = chipVisibility(30 in allowedFps)
            chip60.visibility = chipVisibility(60 in allowedFps)
            chipRear.visibility = chipVisibility(caps == null || DragRunVideoSettings.LensOption.REAR in caps.lenses)
            chipFront.visibility = chipVisibility(caps == null || DragRunVideoSettings.LensOption.FRONT in caps.lenses)
            chipBoth.visibility = chipVisibility(dualSupported)
            CameraSettingsSheet.compactChipRow(chipHd, chipFhd, chipUhd)
            CameraSettingsSheet.compactChipRow(chip30, chip60)
            CameraSettingsSheet.compactChipRow(chipRear, chipFront, chipBoth)
            CameraSettingsSheet.compactChipRow(chipMap3d, chipMapFull)
            selectChip(chipHd, quality == DragRunVideoSettings.QualityOption.HD)
            selectChip(chipFhd, quality == DragRunVideoSettings.QualityOption.FHD)
            selectChip(chipUhd, quality == DragRunVideoSettings.QualityOption.UHD)
            selectChip(chip30, fps == 30)
            selectChip(chip60, fps == 60)
            selectChip(chipRear, !dual && lens == DragRunVideoSettings.LensOption.REAR)
            selectChip(chipFront, !dual && lens == DragRunVideoSettings.LensOption.FRONT)
            selectChip(chipBoth, dual)
            selectChip(chipMicOn, mic)
            selectChip(chipMicOff, !mic)
            selectChip(chipMap3d, map3d)
            selectChip(chipMapFull, !map3d)
            llPhoneCameraSettings.visibility = View.VISIBLE
            llTrackMapStyle.visibility = View.VISIBLE
        }

        fun applyAndRebind(
            quality: DragRunVideoSettings.QualityOption = TrackSessionVideoSettings.quality(this),
            fps: Int = TrackSessionVideoSettings.fps(this),
            lens: DragRunVideoSettings.LensOption = TrackSessionVideoSettings.lens(this),
            mic: Boolean = TrackSessionVideoSettings.micEnabled(this),
            dual: Boolean = TrackSessionVideoSettings.dualEnabled(this)
        ) {
            if (isRecording || activeVideoRecording != null) {
                showToast(getString(R.string.track_camera_change_while_recording))
                return
            }
            val caps = cameraProvider?.let { DragRunVideoCapabilities.query(it, lens) }
            val (safeQuality, safeFps) = caps?.sanitize(quality, fps) ?: (quality to fps)
            val dualOk = dual && ConcurrentPhoneCameras.isSupported(this, cameraProvider)
            TrackSessionVideoSettings.save(
                this,
                quality = safeQuality,
                fps = safeFps,
                lens = lens,
                micEnabled = mic,
                dualEnabled = dualOk
            )
            refreshChips()
            val mode = when (lens) {
                DragRunVideoSettings.LensOption.FRONT -> SessionCameraMode.FRONT
                else -> SessionCameraMode.REAR
            }
            requestOrApplySessionCameraMode(mode)
        }

        chipHd.setOnClickListener { applyAndRebind(quality = DragRunVideoSettings.QualityOption.HD) }
        chipFhd.setOnClickListener { applyAndRebind(quality = DragRunVideoSettings.QualityOption.FHD) }
        chipUhd.setOnClickListener { applyAndRebind(quality = DragRunVideoSettings.QualityOption.UHD) }
        chip30.setOnClickListener { applyAndRebind(fps = 30) }
        chip60.setOnClickListener { applyAndRebind(fps = 60) }
        chipRear.setOnClickListener {
            applyAndRebind(lens = DragRunVideoSettings.LensOption.REAR, dual = false)
        }
        chipFront.setOnClickListener {
            applyAndRebind(lens = DragRunVideoSettings.LensOption.FRONT, dual = false)
        }
        chipBoth.setOnClickListener { applyAndRebind(dual = true) }
        chipMicOn.setOnClickListener {
            TrackSessionVideoSettings.save(this, micEnabled = true)
            refreshChips()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                pendingSessionCameraMode = sessionCameraMode.takeIf { it != SessionCameraMode.OFF } ?: lastSavedSessionCameraMode()
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), CAMERA_PERMISSION_REQUEST)
            }
        }
        chipMicOff.setOnClickListener { applyAndRebind(mic = false) }
        chipMap3d.setOnClickListener {
            TrackSessionVideoSettings.save(this, map3dEnabled = true)
            refreshChips()
            updateCameraFeedHud()
        }
        chipMapFull.setOnClickListener {
            TrackSessionVideoSettings.save(this, map3dEnabled = false)
            refreshChips()
            updateCameraFeedHud()
        }

        val btnTrackCameraOff = view.findViewById<TextView>(R.id.btnTrackCameraOff)
        btnTrackCameraOff.visibility = View.VISIBLE
        btnTrackCameraOff.setOnClickListener {
            dialog.dismiss()
            applySessionCameraMode(SessionCameraMode.OFF)
        }
        refreshChips()
        dialog.show()
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
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (TrackSessionVideoSettings.micEnabled(this)) {
            permissions += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        return permissions.toTypedArray()
    }

    private fun applySessionCameraMode(mode: SessionCameraMode) {
        pendingSessionCameraMode = null
        sessionCameraMode = mode
        if (mode != SessionCameraMode.OFF) {
            TrackSessionVideoSettings.save(
                this,
                lens = if (mode == SessionCameraMode.FRONT) {
                    DragRunVideoSettings.LensOption.FRONT
                } else {
                    DragRunVideoSettings.LensOption.REAR
                }
            )
        }
        updateCameraButtonUi()
        updateCameraPreviewCardVisibility()
        refreshTrackCameraKeepScreenOn()

        if (mode == SessionCameraMode.OFF) {
            unbindSessionCamera()
            return
        }

        tvCameraPreviewPlaceholder.text = getString(mode.labelResId)
        bindSessionCameraPreview()
        refreshLiveHudMiniMapShape()
    }

    private fun refreshTrackCameraKeepScreenOn() {
        ScreenKeepOnController.setKeepOnOverride(
            this,
            sessionCameraMode != SessionCameraMode.OFF
        )
    }

    private fun bindSessionCameraPreview() {
        if (activeVideoRecording != null || isVideoRecordingActive) {
            return
        }
        val lensFacing = sessionCameraMode.lensFacing ?: return
        val targetRotation = resolveSessionVideoTargetRotation()

        if (!isVideoRecordingActive) {
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_ready)
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                if (activeVideoRecording != null || isVideoRecordingActive) {
                    return@addListener
                }
                val provider = providerFuture.get()
                cameraProvider = provider
                val requestedLens = TrackSessionVideoSettings.lens(this@TrackSessionActivity)
                val caps = DragRunVideoCapabilities.query(provider, requestedLens)
                val lens = requestedLens.takeIf { it in caps.lenses } ?: caps.lenses.first()
                val (qualityOption, fps) = caps.sanitize(
                    TrackSessionVideoSettings.quality(this@TrackSessionActivity),
                    TrackSessionVideoSettings.fps(this@TrackSessionActivity)
                )
                val dualRequested = TrackSessionVideoSettings.dualEnabled(this@TrackSessionActivity)
                if (
                    qualityOption != TrackSessionVideoSettings.quality(this@TrackSessionActivity) ||
                    fps != TrackSessionVideoSettings.fps(this@TrackSessionActivity) ||
                    lens != requestedLens
                ) {
                    TrackSessionVideoSettings.save(
                        this@TrackSessionActivity,
                        quality = qualityOption,
                        fps = fps,
                        lens = lens
                    )
                }
                sessionCameraMode = when (lens) {
                    DragRunVideoSettings.LensOption.FRONT -> SessionCameraMode.FRONT
                    else -> SessionCameraMode.REAR
                }
                val dual = dualRequested && ConcurrentPhoneCameras.advertised(this@TrackSessionActivity, provider)
                DualCameraPipLayout.hud = DualCameraPipLayout.Hud.TRACK
                val bound = PhoneDualCameraBinder.bind(
                    context = this@TrackSessionActivity,
                    owner = this@TrackSessionActivity,
                    provider = provider,
                    previewView = cameraPreviewView,
                    rotation = targetRotation,
                    lens = lens,
                    dual = dual,
                    quality = qualityOption.cameraQuality,
                    fps = fps
                )
                if (dualRequested &&
                    ConcurrentPhoneCameras.isProbed(this@TrackSessionActivity) &&
                    !ConcurrentPhoneCameras.isSupported(this@TrackSessionActivity, provider)
                ) {
                    TrackSessionVideoSettings.save(this@TrackSessionActivity, dualEnabled = false)
                }
                cameraVideoCapture = bound.second
                sessionCameraPreview = bound.first
                tvCameraPreviewPlaceholder.text = getString(sessionCameraMode.labelResId)
                updateCameraButtonUi()
                cameraPreviewView.post { refreshLiveHudPreviewAspect() }
                maybeRestartSessionVideoAfterInterrupt()
            } catch (error: Exception) {
                Log.e("TrackSessionActivity", "Unable to bind session camera", error)
                sessionCameraMode = SessionCameraMode.OFF
                cameraVideoCapture = null
                sessionCameraPreview = null
                DualCameraHolder.release()
                cameraProvider?.unbindAll()
                pendingRestartVideoAfterInterrupt = false
                updateCameraButtonUi()
                updateCameraPreviewCardVisibility()
                refreshTrackCameraKeepScreenOn()
                showToast(getString(R.string.track_camera_unavailable))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun unbindSessionCamera() {
        if (activeVideoRecording != null) return
        DualCameraHolder.release()
        cameraProvider?.unbindAll()
        cameraVideoCapture = null
        sessionCameraPreview = null
        hidePipSwapButton()
        llCameraPreviewPlaceholder.visibility = View.VISIBLE
    }

    private fun refreshLiveHudPreviewAspect() {
        if (!::cameraFeedHud.isInitialized) return
        val info = sessionCameraPreview?.resolutionInfo
        if (info != null) {
            val size = info.resolution
            cameraFeedHud.setContentAspect(
                TrackSessionLiveHudView.displayedAspect(size.width, size.height, info.rotationDegrees)
            )
        }
        layoutPipSwapButton()
    }

    private fun hidePipSwapButton() {
        if (::pipSwapButton.isInitialized) {
            pipSwapButton.visibility = View.GONE
        }
    }

    private fun layoutPipSwapButton() {
        if (!::pipSwapButton.isInitialized || !::cameraPreviewView.isInitialized) return
        val dual = DualCameraHolder.isActive() && sessionCameraMode != SessionCameraMode.OFF
        pipSwapButton.visibility = if (dual) View.VISIBLE else View.GONE
        if (!dual) return
        val parentW = cameraPreviewView.width
        val parentH = cameraPreviewView.height
        if (parentW <= 1 || parentH <= 1) return
        val info = sessionCameraPreview?.resolutionInfo
        val aspect = if (info != null) {
            TrackSessionLiveHudView.displayedAspect(
                info.resolution.width,
                info.resolution.height,
                info.rotationDegrees
            )
        } else {
            parentW.toFloat() / parentH.toFloat()
        }
        val parentAspect = parentW.toFloat() / parentH.toFloat()
        val videoW: Int
        val videoH: Int
        if (parentAspect > aspect) {
            videoH = parentH
            videoW = (videoH * aspect).toInt().coerceAtLeast(1)
        } else {
            videoW = parentW
            videoH = (videoW / aspect).toInt().coerceAtLeast(1)
        }
        val videoLeft = (parentW - videoW) / 2f
        val videoTop = (parentH - videoH) / 2f
        val pip = DualCameraPipLayout.pipInDisplay(videoW.toFloat(), videoH.toFloat())
        DualCameraPipLayout.layoutSwapButton(pipSwapButton, videoLeft, videoTop, pip)
    }

    private fun swapDualCameras() {
        if (!DualCameraHolder.isActive()) return
        if (isRecording || activeVideoRecording != null) {
            showToast(getString(R.string.track_camera_change_while_recording))
            return
        }
        val next = ConcurrentPhoneCameras.otherLens(TrackSessionVideoSettings.lens(this))
        TrackSessionVideoSettings.save(this, lens = next, dualEnabled = true)
        val mode = if (next == DragRunVideoSettings.LensOption.FRONT) {
            SessionCameraMode.FRONT
        } else {
            SessionCameraMode.REAR
        }
        requestOrApplySessionCameraMode(mode)
    }

    private fun resetSessionVideoState(clearSavedMetadata: Boolean, deleteFiles: Boolean) {
        if (deleteFiles) {
            deleteFileIfExists(sessionVideoRawFile)
            if (sessionVideoFinalFile != sessionVideoRawFile) {
                deleteFileIfExists(sessionVideoFinalFile)
            }
            if (clearSavedMetadata) {
                savedSessionVideoClips.forEach { clip ->
                    clip.uri.takeIf { it.isNotBlank() }?.let(::deleteVideoUriIfExists)
                    clip.path.takeIf { it.isNotBlank() }?.let { deleteFileIfExists(File(it)) }
                }
            }
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
        pendingRestartVideoAfterInterrupt = false
        isFinalizingSessionVideo = false
        awaitingVideoProcessingForOuting = false
        sessionVideoDiscardRequested = false
        sessionVideoInterruptedToastShown = false
        if (clearSavedMetadata) {
            savedSessionVideoClips.clear()
        }
        unlockSessionOrientationAfterCameraRecording()
    }

    private fun lockSessionOrientationForCameraRecording() {
        if (sessionOrientationLockedForVideo) return
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        sessionOrientationLockedForVideo = true
    }

    private fun unlockSessionOrientationAfterCameraRecording() {
        if (!sessionOrientationLockedForVideo) return
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        sessionOrientationLockedForVideo = false
    }

    private data class ProcessedSessionVideo(
        val file: File,
        val actualTrimStartMs: Long
    )

    private fun startSessionVideoRecordingIfNeeded() {
        resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
        beginSessionVideoRecording(isContinuationClip = false)
    }

    private fun maybeRestartSessionVideoAfterInterrupt() {
        if (!pendingRestartVideoAfterInterrupt) return
        if (!isRecording || sessionCameraMode == SessionCameraMode.OFF) {
            pendingRestartVideoAfterInterrupt = false
            return
        }
        if (activeVideoRecording != null || isVideoRecordingActive || isFinalizingSessionVideo) {
            return
        }
        if (cameraVideoCapture == null) {
            return
        }
        pendingRestartVideoAfterInterrupt = false
        beginSessionVideoRecording(isContinuationClip = true)
        showToast(getString(R.string.track_camera_video_resumed_part))
    }

    private fun softStopSessionVideoForInterrupt() {
        if (!isRecording) return
        if (activeVideoRecording == null) return
        if (pendingCreateOutingAfterVideoFinalize || pendingDiscardVideoAfterFinalize) return
        pendingRestartVideoAfterInterrupt = true
        tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_processing)
        activeVideoRecording?.stop()
    }

    private fun beginSessionVideoRecording(isContinuationClip: Boolean) {
        if (sessionCameraMode == SessionCameraMode.OFF) {
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_off)
            return
        }

        val videoCapture = cameraVideoCapture
        if (videoCapture == null) {
            if (!isContinuationClip) {
                bindSessionCameraPreview()
                showToast(getString(R.string.track_camera_unavailable))
            }
            return
        }

        val rawFile = buildSessionVideoFile(if (isContinuationClip) "raw_part" else "raw")
        sessionVideoRawFile = rawFile
        videoRecordingStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
        videoRecordingStartWallTimeMs = System.currentTimeMillis()
        if (!isContinuationClip) {
            videoSyncMarkerOffsetMs = null
        }

        try {
            lockSessionOrientationForCameraRecording()
            videoCapture.targetRotation = resolveSessionVideoTargetRotation()
            val outputOptions = FileOutputOptions.Builder(rawFile).build()
            var pendingRecording = videoCapture.output.prepareRecording(this, outputOptions)
            if (
                TrackSessionVideoSettings.micEnabled(this) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) {
                pendingRecording = pendingRecording.withAudioEnabled()
            }
            activeVideoRecording = pendingRecording.start(
                ContextCompat.getMainExecutor(this),
                ::handleSessionVideoRecordEvent
            )
        } catch (error: Exception) {
            Log.e("TrackSessionActivity", "Unable to start session video recording", error)
            unlockSessionOrientationAfterCameraRecording()
            deleteFileIfExists(rawFile)
            sessionVideoRawFile = null
            activeVideoRecording = null
            isVideoRecordingActive = false
            if (!isContinuationClip) {
                resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
            } else {
                pendingRestartVideoAfterInterrupt = false
            }
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
        val keepPartialOnError = !shouldDiscard &&
            isRecording &&
            !shouldCreateOuting &&
            rawFile != null &&
            rawFile.exists() &&
            rawFile.length() >= MIN_USABLE_SESSION_VIDEO_BYTES

        activeVideoRecording = null
        isVideoRecordingActive = false
        pendingCreateOutingAfterVideoFinalize = false
        pendingDiscardVideoAfterFinalize = false

        if (shouldDiscard) {
            isFinalizingSessionVideo = false
            awaitingVideoProcessingForOuting = false
            pendingRestartVideoAfterInterrupt = false
            deleteFileIfExists(rawFile)
            resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_ready)
            return
        }

        val usableRawFile = when {
            rawFile != null && rawFile.exists() && rawFile.length() >= MIN_USABLE_SESSION_VIDEO_BYTES -> rawFile
            else -> null
        }

        if (usableRawFile == null) {
            Log.e("TrackSessionActivity", "Session video finalize failed: ${event.error}")
            deleteFileIfExists(rawFile)
            sessionVideoRawFile = null
            sessionVideoFinalFile = null
            isFinalizingSessionVideo = false
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_ready)
            if (shouldCreateOuting || awaitingVideoProcessingForOuting) {
                awaitingVideoProcessingForOuting = false
                if (savedSessionVideoClips.isEmpty()) {
                    showToast(getString(R.string.track_camera_video_failed))
                }
                createOuting()
            } else if (isRecording && pendingRestartVideoAfterInterrupt) {
                notifySessionVideoInterrupted()
            }
            return
        }

        if (event.hasError() && !keepPartialOnError && !shouldCreateOuting && !awaitingVideoProcessingForOuting) {
            // Unexpected error while idle — keep any previously saved clips.
            Log.e("TrackSessionActivity", "Session video finalize error (no keep): ${event.error}")
            deleteFileIfExists(usableRawFile)
            sessionVideoRawFile = null
            isFinalizingSessionVideo = false
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_ready)
            return
        }

        if (event.hasError() && isRecording && !shouldCreateOuting) {
            pendingRestartVideoAfterInterrupt = true
            notifySessionVideoInterrupted()
        }

        tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_processing)
        isFinalizingSessionVideo = true
        val trimStartMs = resolveRequestedSessionVideoTrimStartMs()
        val createOutingWhenDone = shouldCreateOuting || awaitingVideoProcessingForOuting
        awaitingVideoProcessingForOuting = false
        Thread {
            val processedVideo = processSessionVideoFile(usableRawFile, trimStartMs)
            persistProcessedSessionVideo(
                rawFile = usableRawFile,
                processedVideo = processedVideo,
                shouldCreateOuting = createOutingWhenDone,
                shouldDiscard = false
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
            if (sessionVideoDiscardRequested) {
                deleteFileIfExists(processedFile)
                if (processedFile != rawFile) {
                    deleteFileIfExists(rawFile)
                }
                sessionVideoRawFile = null
                runOnUiThread {
                    isFinalizingSessionVideo = false
                    resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
                }
                return
            }
            val sessionStartSessionElapsedMs = processedVideo?.let { video ->
                resolveSessionVideoStartSessionElapsedMs(video.actualTrimStartMs)
            }
            if (sessionStartSessionElapsedMs != null && processedVideo != null && processedFile != null) {
                val clip = TrackSessionVideoClip(
                    uri = "",
                    path = processedFile.absolutePath,
                    camera = currentSessionCameraLabel(),
                    sessionStartOffsetMs = (-sessionStartSessionElapsedMs).coerceAtLeast(0L),
                    sessionElapsedAtStartMs = sessionStartSessionElapsedMs,
                    overlayExported = false,
                    kind = TrackSessionVideoKind.RECORDING,
                    sourceTrimStartMs = processedVideo.actualTrimStartMs
                )
                savedSessionVideoClips += clip
                sessionVideoFinalFile = processedFile
                if (processedFile != rawFile) {
                    deleteFileIfExists(rawFile)
                }
            } else {
                deleteFileIfExists(processedFile)
                if (processedFile != rawFile) {
                    deleteFileIfExists(rawFile)
                }
            }
            sessionVideoRawFile = null
        }

        runOnUiThread {
            isFinalizingSessionVideo = false
            if (sessionCameraMode == SessionCameraMode.OFF) {
                tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_off)
            } else {
                tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_ready)
            }

            if (shouldCreateOuting) {
                createOuting()
            } else if (isRecording && pendingRestartVideoAfterInterrupt) {
                notifySessionVideoInterrupted()
                // Restart only when visible again — onResume / camera bind will pick this up.
            }
        }
    }

    private fun notifySessionVideoInterrupted() {
        if (sessionVideoInterruptedToastShown) return
        sessionVideoInterruptedToastShown = true
        showToast(getString(R.string.track_camera_video_interrupted))
    }

    private fun processSessionVideoFile(rawFile: File, trimStartMs: Long): ProcessedSessionVideo? {
        if (!rawFile.exists()) return null
        return ProcessedSessionVideo(rawFile, trimStartMs.coerceAtLeast(0L))
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
        val popup = PopupMenu(this, if (btnPredictiveGapMode.visibility == View.VISIBLE) {
            btnPredictiveGapMode
        } else {
            cardPredictiveLap
        })
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

    private fun formatLapTimePrecise(timeMs: Long): String = LapTimeFormatter.formatMs(timeMs)

    private fun resetPredictiveGapCard() {
        tvPredictiveGapSignValue.text = "+"
        tvPredictiveGapValue.text = if (usesSpeedDeltaPills()) "0.0s" else "0.0"
        val baseColor = ContextCompat.getColor(this, R.color.track_neon_green)
        tvPredictiveGapSignValue.setTextColor(baseColor)
        tvPredictiveGapValue.setTextColor(baseColor)
    }

    private fun resetPredictiveEstimatorState(clearGauge: Boolean) {
        displayedPredictedLapSeconds = Float.NaN
        lastPredictionDisplayUpdateMs = 0L
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
        tvPredictiveGapValue.text = if (usesSpeedDeltaPills()) {
            String.format(Locale.US, "%.1fs", displayValue)
        } else {
            String.format(Locale.US, "%.1f", displayValue)
        }
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
        var bestSessionId: String? = null
        var bestOuting = 0

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
                bestSessionId = sessionId
                bestOuting = outing
            }
        }

        trackBestLapTime = bestTime
        trackBestLapNumber = bestLap

        trackBestRefDistances.clear()
        trackBestRefElapsedMs.clear()
        trackBestRefTotalMs = 0L
        if (bestSessionId != null && bestLap > 0 && bestTime != Long.MAX_VALUE) {
            buildTrackBestReferenceFromStorage(sharedPrefs, bestSessionId!!, bestOuting, bestLap, bestTime)
        }
    }

    // Rebuild the (distance-travelled -> elapsed) reference table for the historical best lap from
    // its persisted telemetry (route points + relative timestamps). Distance travelled is the
    // cumulative ground distance between consecutive recorded points, matching the live lap's
    // lapDistanceAccum alignment key.
    private fun buildTrackBestReferenceFromStorage(
        sharedPrefs: android.content.SharedPreferences,
        sessionId: String,
        outing: Int,
        lapNumber: Int,
        totalLapMs: Long
    ) {
        val lap = TrackLapDataStore.loadLap(this, sharedPrefs, sessionId, outing, lapNumber) ?: return
        if (lap.routePoints.size < 5) return

        val results = FloatArray(1)
        var cumulative = 0f
        var prev: com.revix.app.GeoPoint? = null
        var lastProgress = Float.NaN
        for (rp in lap.routePoints) {
            val p = prev
            if (p != null) {
                android.location.Location.distanceBetween(
                    p.latitude, p.longitude,
                    rp.geoPoint.latitude, rp.geoPoint.longitude,
                    results
                )
                val step = results[0]
                if (step.isFinite() && step in 0f..60f) cumulative += step
            }
            prev = rp.geoPoint
            if (lastProgress.isNaN() || cumulative > lastProgress + 1.0f) {
                if (trackBestRefDistances.size < maxPredictiveReferenceSamples) {
                    trackBestRefDistances.add(cumulative)
                    trackBestRefElapsedMs.add(rp.timestamp.coerceAtLeast(0L))
                    lastProgress = cumulative
                }
            }
        }

        if (trackBestRefDistances.size >= 5) {
            trackBestRefTotalMs = totalLapMs
        } else {
            trackBestRefDistances.clear()
            trackBestRefElapsedMs.clear()
        }
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
        btnLap.text = getString(R.string.track_button_cancel)
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
        sectorProgressWaypoints.clear()
        sectorProgressLengthMeters = 0f
        customTrackPersistedCalibrated = false
        customTrackCalibratedInSession = false
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
            val customTrackV2 = com.revix.app.tracking.CustomTrackStorage.loadCustomTrackV2(this, trackId)
            if (customTrackV2 != null) {
                trackName = customTrackV2.name
                currentTrackMode = when (customTrackV2.mode as com.revix.app.tracking.CustomTrackMode?) {
                    com.revix.app.tracking.CustomTrackMode.POINT_TO_POINT -> TrackMode.POINT_TO_POINT
                    else -> TrackMode.CIRCUIT
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
                customTrackPersistedCalibrated = (customTrackV2.measuredDistanceMeters ?: 0f) > 100f
                customTrackCalibratedInSession = customTrackPersistedCalibrated
                rebuildSectorProgressWaypoints(customTrackV2)
                if (trackLengthMeters <= 50f && sectorProgressLengthMeters > 50f) {
                    trackLengthMeters = sectorProgressLengthMeters
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
        updatePointToPointTelemetryUi()
        updateTrackNameHeader()
        refreshLiveHudMiniMapShape()
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
            AlertDialog.Builder(this, R.style.CustomAlertDialog)
                .setTitle(getString(R.string.track_calibration_missing_title))
                .setMessage(getString(R.string.track_calibration_missing_message))
                .setPositiveButton(getString(R.string.drag_calibration_open_button)) { _, _ -> openCalibrationScreen() }
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
        if (!com.revix.app.billing.ProGate.ensureDragOrTrack(
                this,
                com.revix.app.billing.ProAccess.Feature.TRACK
            )
        ) {
            return
        }
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
        updateCurrentLapBadge(displayCurrentLapNumber())
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
        lapData.clear()
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
        sessionDistanceKmAccum = 0.0
        streamFlushCounter = 0
        discardLapStream()
        liveSessionId = ""
        liveOutingNumber = 0
        sectorDistanceAccum = 0f
        lapDistanceAccum = 0f
        bestLapDistance = 0f
        currentProjectedRouteDistanceMeters = Float.NaN
        projectedRouteDistanceAtLapStartMeters = Float.NaN
        clearCurrentLapReferenceSamples()
        bestLapRefDistances.clear()
        bestLapRefElapsedMs.clear()
        bestLapRefTotalMs = 0L
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
        gForceGravity[0] = 0f
        gForceGravity[1] = 0f
        gForceGravity[2] = 0f
        gForceGravityInitialized = false
        gForceGravityFrozen = false
        gForceGravityFreezeCounter = 0
        noGyroGpsLongStatsSmooth = 0f
        noGyroLeanLatGSmooth = 0f
        latestRollRateDegPerSec = 0f
        latestYawRateDegPerSec = 0f
        leanAccelGate.reset()
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
        lastTelemetrySpeedKmh = 0f
        currentLateralG = 0f
        measurementLongitudinalG = 0f
        measurementLateralG = 0f
        resetHudDisplayG()
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
        lastRaceBoxTelemetryPersistMs = 0L
        hasRaceBoxGSmooth = false
        hasPhoneGSmooth = false
        phoneLongGSmooth = 0f
        phoneLatGSmooth = 0f
        resetHudDisplayG()
        chartLongLpState.reset()
        chartLatLpState.reset()
        chartQualityLongitudinalG = 0f
        chartQualityLateralG = 0f
        lastStartFinishCrossAtMs = 0L
        // Stream live samples only after timing has started (awaitingStart waits for S/F).
        if (!awaitingStart && lapStartTime > 0L) {
            openLapStream(lapNumber = 1, startTime = lapStartTime)
        }
        // Do not mark an active/resumable session until at least one lap is completed.
        startSessionVideoRecordingIfNeeded()
        refreshLiveHudMiniMapShape()
    }

    private fun markActiveTrackSession() {
        ensureLivePersistIdentity()
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        val editor = sharedPrefs.edit()
            .putBoolean("has_active_session", true)
            .putBoolean("active_session_has_lap", true)
            .putString("active_track_id", trackId)
            .putString("active_track_name", trackName)
            .putString("active_session_id", liveSessionId)

        val isResumeSession = intent.getBooleanExtra("resume_session", false)
        if (isResumeSession) {
            editor.remove("pending_session_id_raw")
        } else {
            val pendingRaw = liveSessionId.substringAfter('_', missingDelimiterValue = liveSessionId)
            editor.putString("pending_session_id_raw", pendingRaw)
        }
        editor.apply()
    }

    private fun ensureLivePersistIdentity() {
        if (liveSessionId.isNotEmpty() && liveOutingNumber > 0) return

        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val isResumeSession = intent.getBooleanExtra("resume_session", false)
        val sessionIdWithProfile = if (isResumeSession) {
            intent.getStringExtra("session_id").orEmpty().ifBlank {
                getSharedPreferences("track_sessions", MODE_PRIVATE)
                    .getString("active_session_id", null)
                    .orEmpty()
            }
        } else {
            val existingPending = getSharedPreferences("track_sessions", MODE_PRIVATE)
                .getString("pending_session_id_raw", null)
            val pendingRaw = existingPending ?: run {
                val startedAt = sessionStartTime.takeIf { it > 0L } ?: System.currentTimeMillis()
                val date = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(startedAt))
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(startedAt))
                "${trackId}_${date}_${time.replace(":", "")}_${startedAt}"
            }
            getSharedPreferences("track_sessions", MODE_PRIVATE).edit()
                .putString("pending_session_id_raw", pendingRaw)
                .apply()
            "${currentProfileId}_${pendingRaw}"
        }
        liveSessionId = sessionIdWithProfile
        val outingPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        liveOutingNumber = outingPrefs.getInt("${liveSessionId}_outing_count", 0) + 1
    }

    private fun discardLapStream() {
        lapStream?.discard()
        lapStream = null
    }

    private fun openLapStream(lapNumber: Int, startTime: Long) {
        ensureLivePersistIdentity()
        discardLapStream()
        val safeSession = liveSessionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dir = File(filesDir, "track_lap_stream")
        lapStream = TrackLapStreamWriter(
            dir = dir,
            baseName = "${safeSession}_o${liveOutingNumber}_l${lapNumber}"
        )
        currentLapData = LapData(lapNumber = lapNumber, startTime = startTime)
        streamFlushCounter = 0
    }

    private fun appendRouteSample(
        latitude: Double,
        longitude: Double,
        speedKmh: Float,
        angle: Float,
        timestampMs: Long,
        absoluteTimeMs: Long
    ) {
        val stream = lapStream ?: return
        stream.appendRoutePoint(
            latitude = latitude,
            longitude = longitude,
            speedKmh = GnssSpeedSanitizer.sanitizeReportedKmh(speedKmh, isStationary),
            angle = angle,
            timestampMs = timestampMs,
            absoluteTimeMs = absoluteTimeMs
        )
        maybeFlushLapStream()
    }

    private fun maybeFlushLapStream() {
        streamFlushCounter++
        if (streamFlushCounter % 50 == 0) {
            lapStream?.flushQuietly()
        }
    }

    /**
     * Seal live stream to durable `.bin` (no full RAM materialize / no Gson on UI thread).
     * JSON mirror is written asynchronously by [TrackLapDataStore].
     */
    private fun flushCompletedLapToDisk(lapNumber: Int, endTime: Long): LapData {
        ensureLivePersistIdentity()
        val startTime = currentLapData.startTime
        val stream = lapStream
        val streamMax = stream?.maxRouteSpeedKmh ?: 0f
        lapStream = null

        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val editor = prefs.edit()
        val meta = TrackLapStreamWriter.LapBinMeta(
            lapNumber = lapNumber,
            startTime = startTime,
            endTime = endTime,
            maxSpeedKmh = streamMax
        )
        val targetBin = TrackLapDataStore.binFileFor(
            context = this,
            sessionId = liveSessionId,
            outingNumber = liveOutingNumber,
            lapIndex = lapNumber
        )
        if (stream != null) {
            val sealed = stream.sealToDurableFile(targetBin)
            if (sealed) {
                TrackLapDataStore.saveLapBin(
                    context = this,
                    editor = editor,
                    sessionId = liveSessionId,
                    outingNumber = liveOutingNumber,
                    lapIndex = lapNumber,
                    sealedBin = targetBin,
                    meta = meta
                )
            } else {
                // Keep data: materialize once and write JSON (may hitch; rare path).
                val source = when {
                    targetBin.exists() -> targetBin
                    stream.streamFile.exists() -> stream.streamFile
                    else -> null
                }
                if (source != null) {
                    val fullLap = TrackLapStreamWriter.readFromFile(
                        source,
                        meta,
                        TrackLapStreamWriter.ReadMode.FULL
                    )
                    if (source.absolutePath != targetBin.absolutePath) {
                        source.delete()
                    }
                    TrackLapDataStore.saveLap(
                        context = this,
                        editor = editor,
                        sessionId = liveSessionId,
                        outingNumber = liveOutingNumber,
                        lapIndex = lapNumber,
                        lap = fullLap
                    )
                } else {
                    android.util.Log.e(
                        "TrackSessionActivity",
                        "Lap $lapNumber seal failed — no durable telemetry file"
                    )
                }
            }
        }

        val distanceKm = (lapDistanceAccum / 1000.0).coerceAtLeast(0.0)
        sessionDistanceKmAccum += distanceKm
        if (streamMax.isFinite() && streamMax > 0f) {
            editor.putString(
                "${liveSessionId}_outing_${liveOutingNumber}_lap_${lapNumber}_max_speed",
                String.format(java.util.Locale.US, "%.1f km/h", streamMax)
            )
        }
        editor.apply()

        // Keep metadata only — never retain full telemetry lists across laps.
        return LapData(lapNumber = lapNumber, startTime = startTime, endTime = endTime)
    }

    private fun discardLivePersistedLaps() {
        discardLapStream()
        if (liveSessionId.isEmpty() || liveOutingNumber <= 0) return
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val editor = prefs.edit()
        for (i in 1..lapData.size.coerceAtLeast(totalLaps)) {
            TrackLapDataStore.deleteLap(this, liveSessionId, liveOutingNumber, i)
            editor.remove(TrackLapDataStore.prefsKey(liveSessionId, liveOutingNumber, i))
            editor.remove("${liveSessionId}_outing_${liveOutingNumber}_lap_${i}_max_speed")
        }
        editor.apply()
    }

    private fun materializeOpenLapFromStream(endTime: Long = System.currentTimeMillis()): LapData? {
        val stream = lapStream ?: return null
        if (!stream.hasSamples()) return null
        val lapNumber = currentLapData.lapNumber.coerceAtLeast(currentLap + 1).coerceAtLeast(1)
        val startTime = currentLapData.startTime
        val lap = stream.finalizeToLapData(lapNumber, startTime, endTime)
        lapStream = null
        currentLapData = LapData(lapNumber = lapNumber, startTime = startTime, endTime = endTime)
        return lap
    }
    private fun stopRecording() {
        if (!hasCompletedLap()) {
            stopRecordingWithoutSaving(
                showDataLostToast = false,
                resumeIdleLocationTracking = true
            )
            showToast(getString(R.string.track_session_not_saved_no_laps))
            return
        }
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
            pendingRestartVideoAfterInterrupt = false
            tvCameraPreviewStatus.text = getString(R.string.track_camera_preview_status_processing)
            activeVideoRecording?.stop()
        } else if (isFinalizingSessionVideo) {
            awaitingVideoProcessingForOuting = true
            pendingRestartVideoAfterInterrupt = false
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
        discardLivePersistedLaps()
        // Cancel / discard before a completed lap must not leave a resumable session card.
        clearActiveSession()
        liveSessionId = ""
        liveOutingNumber = 0
        sessionDistanceKmAccum = 0.0
        
        btnStartStop.text = getString(R.string.track_button_start)
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
        // Не премахваме ACCELEROMETER сензора, защото се нуждаем от него за g-сили
        handler.removeCallbacks(updateRunnable)
        locationManager.removeUpdates(this)
        if (activeVideoRecording != null) {
            pendingDiscardVideoAfterFinalize = true
            pendingCreateOutingAfterVideoFinalize = false
            pendingRestartVideoAfterInterrupt = false
            awaitingVideoProcessingForOuting = false
            sessionVideoDiscardRequested = true
            activeVideoRecording?.stop()
        } else if (isFinalizingSessionVideo) {
            sessionVideoDiscardRequested = true
            pendingRestartVideoAfterInterrupt = false
            awaitingVideoProcessingForOuting = false
        } else {
            resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
        }
        if (resumeIdleLocationTracking) {
            refreshTrackDistanceCacheFromLastKnownLocation()
            startLocationUpdates()
        }
        updateLapSummaryCards()
        if (showDataLostToast) {
            showToast(getString(R.string.track_data_lost))
        }
    }
    private fun recordLap(
        crossingLocation: Location? = null,
        crossingWallTimeMs: Long? = null
    ) {
        if (isRecording) {
            currentLap++
            totalLaps++
            updateCurrentLapBadge(displayCurrentLapNumber())
            // Prefer interpolated gate-crossing time over the GPS tick that detected it.
            val lapEndTime = crossingWallTimeMs ?: System.currentTimeMillis()
            val lapTime = (lapEndTime - lapStartTime).coerceAtLeast(0L)
            val lapTimeFormatted = formatTime(lapTime)
            lapTimes.add(lapTime)
            if (totalLaps == 1) {
                markActiveTrackSession()
            }
            
            // Play lap completion sound
            soundManager.playLapComplete()
            
            // Persist full lap to disk, keep only a lightweight stub in RAM.
            val stub = flushCompletedLapToDisk(currentLap, lapEndTime)
            lapData.add(stub)
            refreshLiveHudMiniMapShape()
            android.util.Log.d(
                "TrackSessionActivity",
                "Saved lap $currentLap to disk (stream pts=${stub.lapNumber})"
            )
            
            val isNewBest = lapTime < bestLapTime
            if (isNewBest) {
                // Play personal best sound
                soundManager.playPersonalBest()
                bestLapTime = lapTime
                bestLapNumber = currentLap
                val beatsTrackBest = lapTime < trackBestLapTime
                if (beatsTrackBest) {
                    trackBestLapTime = lapTime
                    trackBestLapNumber = currentLap
                }
                // Snapshot sector times and distances of this best lap
                bestSectorTimes.clear()
                bestSectorTimes.addAll(sectorTimes)
                bestSectorDistances.clear()
                bestSectorDistances.addAll(sectorDistances)
                bestLapDistance = sectorDistances.sum()
                // Snapshot the position->time reference table for position-based delta-T.
                snapshotBestLapReference(lapTime)
                if (beatsTrackBest) {
                    trackBestRefDistances.clear()
                    trackBestRefDistances.addAll(currentLapRefDistances)
                    trackBestRefElapsedMs.clear()
                    trackBestRefElapsedMs.addAll(currentLapRefElapsedMs)
                    trackBestRefTotalMs = lapTime
                }
                applyPredictiveGapSourceUi()
            }

            // Learn real lap distance from first valid custom lap and persist for next sessions.
            val completedLapDistanceMeters = lapDistanceAccum
            if (currentLap == 1 && currentTrackMode == TrackMode.CIRCUIT) {
                maybePersistCustomMeasuredDistance(completedLapDistanceMeters)
            } else if (currentTrackMode == TrackMode.POINT_TO_POINT && lapTimes.isEmpty()) {
                maybePersistCustomMeasuredDistance(completedLapDistanceMeters)
            }
            markCustomTrackCalibratedFromSession(completedLapDistanceMeters)

            addLapToUI(currentLap, lapTimeFormatted, isNewBest)
            updateLapSummaryCards(0L)
            // Circuit S/F: next lap starts at the same interpolated crossing instant.
            lapStartTime = lapEndTime
            sectorStartTime = lapStartTime
            currentSector = 0
            sectorTimes.clear() // Clear sector times for new lap
            sectorDistances.clear()
            lapDistanceAccum = 0f
            sectorDistanceAccum = 0f
            clearCurrentLapReferenceSamples()
            projectedRouteDistanceAtLapStartMeters = currentProjectedRouteDistanceMeters
            if (currentProjectedRouteDistanceMeters.isFinite()) {
                lastProjectedAlongMeters = currentProjectedRouteDistanceMeters
            }
            resetPredictiveEstimatorState(clearGauge = true)
            speedGauge.unlockPredictiveColor()
            
            // Start new lap stream (RAM stays empty aside from HUD state).
            openLapStream(lapNumber = currentLap + 1, startTime = lapStartTime)
            lastRaceBoxTelemetryPersistMs = 0L
            hasPhoneGSmooth = false
            phoneLongGSmooth = 0f
            phoneLatGSmooth = 0f
            resetHudDisplayG()
            chartLongLpState.reset()
            chartLatLpState.reset()
            chartQualityLongitudinalG = 0f
            chartQualityLateralG = 0f
            seedLapStartAtCrossing(crossingLocation)

            showToast(getString(R.string.track_lap_saved, currentLap, lapTimeFormatted))
            updateLapDistanceProgress(0f)
        }
    }

    private fun seedLapStartAtCrossing(crossingLocation: Location?) {
        val location = crossingLocation ?: return
        val gateLine = getFinishLinePoints() ?: getStartLinePoints() ?: return
        val previous = previousLocationForCrossing ?: location
        val crossing = interpolateLocationAtGateCrossing(
            previous = previous,
            current = location,
            lineStartLat = gateLine.first.geoPoint.latitude,
            lineStartLon = gateLine.first.geoPoint.longitude,
            lineEndLat = gateLine.second.geoPoint.latitude,
            lineEndLon = gateLine.second.geoPoint.longitude
        ) ?: location

        val speedKmh = GnssSpeedSanitizer.sanitizeFromLocation(
            location = crossing,
            previous = previousLocationForCrossing,
            isStationaryHint = isStationary
        )
        appendRouteSample(
            latitude = crossing.latitude,
            longitude = crossing.longitude,
            speedKmh = speedKmh,
            angle = currentCalibratedLean,
            timestampMs = 0L,
            absoluteTimeMs = crossing.time
        )
        updateP2pRunMetaUi()
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
        val magnitude = abs(value)
        if (magnitude <= 0.002f) return 0f
        if (magnitude >= statsDeadbandG) return value
        val t = (magnitude / statsDeadbandG).coerceIn(0f, 1f)
        return value * t * t
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

    private fun updateStatsPeakDetector(detector: PeakDetector, sample: Float, nowMs: Long): Float {
        val currentMax = detector.committed

        if (sample > currentMax + statsPeakEntryHysteresisG) {
            if (detector.candidateSinceMs == 0L) {
                detector.candidate = sample
                detector.candidateSinceMs = nowMs
            } else {
                detector.candidate = max(detector.candidate, sample)
            }

            if (nowMs - detector.candidateSinceMs >= statsPeakHoldMs) {
                detector.committed = max(detector.committed, detector.candidate)
                detector.candidate = 0f
                detector.candidateSinceMs = 0L
            }
        } else if (sample < currentMax + statsPeakExitHysteresisG) {
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

        maxAcceleration = updateStatsPeakDetector(accelerationPeakDetector, accelerationSample, nowMs)
        maxBraking = updateStatsPeakDetector(brakingPeakDetector, brakingSample, nowMs)
        maxCorneringLeftG = updateStatsPeakDetector(corneringLeftPeakDetector, corneringLeftSample, nowMs)
        maxCorneringRightG = updateStatsPeakDetector(corneringRightPeakDetector, corneringRightSample, nowMs)
    }

    private fun updateNoGyroSessionStatistics(measurementLatG: Float) {
        if (!isRecording || awaitingStart || lapStartTime <= 0L) return

        val nowMs = SystemClock.elapsedRealtime()

        // Cornering maxima follow the measurement stream, not display-smoothed UI values.
        statsFilteredLatG = statsFilterAlpha * measurementLatG + (1f - statsFilterAlpha) * statsFilteredLatG
        val lateral = applyStatsDeadband(statsFilteredLatG)
        val corneringLeftSample = max(0f, lateral)
        val corneringRightSample = max(0f, -lateral)
        maxCorneringLeftG = updateStatsPeakDetector(corneringLeftPeakDetector, corneringLeftSample, nowMs)
        maxCorneringRightG = updateStatsPeakDetector(corneringRightPeakDetector, corneringRightSample, nowMs)

        // Accel/braking maxima are GPS-only for no-gyro car profiles.
        if (!hasGpsGForce) return
        noGyroGpsLongStatsSmooth = noGyroGpsLongStatsFilterAlpha * gpsLongG +
            (1f - noGyroGpsLongStatsFilterAlpha) * noGyroGpsLongStatsSmooth
        val longitudinal = applyStatsDeadband(noGyroGpsLongStatsSmooth)
        val accelerationSample = max(0f, -longitudinal)
        val brakingSample = max(0f, longitudinal)
        maxAcceleration = updateStatsPeakDetector(accelerationPeakDetector, accelerationSample, nowMs)
        maxBraking = updateStatsPeakDetector(brakingPeakDetector, brakingSample, nowMs)
    }

    /**
     * Keep a gravity baseline for G extraction that does not chase centripetal accel.
     * Live Madgwick/LP gravity still drives lean; this one freezes under dynamic load.
     */
    private fun updateGForceGravityReference(rawAccel: FloatArray) {
        val rawMag = sqrt(
            rawAccel[0] * rawAccel[0] +
                rawAccel[1] * rawAccel[1] +
                rawAccel[2] * rawAccel[2]
        )
        val magDev = abs(rawMag - SensorManager.GRAVITY_EARTH)
        val speedMs = lastLocation?.speed ?: 0f
        val dynamicByMag = magDev > gForceGravityFreezeMagThreshold
        // Hold freeze while HUD already shows real cornering/accel (covers moto lean ≈1g cases).
        val dynamicByHud =
            abs(currentLateralG) > 0.18f ||
                abs(currentLongitudinalG) > 0.18f ||
                (speedMs > 4f && lastGyroMagnitude > 0.55f)
        val nearIdle =
            magDev < gForceGravityUnfreezeMagThreshold &&
                abs(currentLateralG) < 0.10f &&
                abs(currentLongitudinalG) < 0.10f &&
                speedMs < 1.8f

        if (dynamicByMag || dynamicByHud) {
            gForceGravityFreezeCounter =
                (gForceGravityFreezeCounter + 1).coerceAtMost(40)
        } else if (nearIdle) {
            gForceGravityFreezeCounter =
                (gForceGravityFreezeCounter - 1).coerceAtLeast(0)
        }
        gForceGravityFrozen = gForceGravityFreezeCounter >= gForceGravityFreezeCountThreshold

        if (!gForceGravityInitialized) {
            if (DragCalibration.isUniversalCalibrated) {
                val g = DragCalibration.gravityVector
                gForceGravity[0] = g[0]
                gForceGravity[1] = g[1]
                gForceGravity[2] = g[2]
            } else {
                // Seed from raw at rest — never from live Madgwick (absorbs dynamics).
                gForceGravity[0] = rawAccel[0]
                gForceGravity[1] = rawAccel[1]
                gForceGravity[2] = rawAccel[2]
            }
            gForceGravityInitialized = true
            return
        }

        if (!gForceGravityFrozen) {
            // Prefer static calibration baseline when available; else slowly track raw
            // accel while idle (never live Madgwick — that chases cornering into gravity).
            if (DragCalibration.isUniversalCalibrated) {
                val g = DragCalibration.gravityVector
                gForceGravity[0] = gForceGravityTrackAlpha * gForceGravity[0] + (1f - gForceGravityTrackAlpha) * g[0]
                gForceGravity[1] = gForceGravityTrackAlpha * gForceGravity[1] + (1f - gForceGravityTrackAlpha) * g[1]
                gForceGravity[2] = gForceGravityTrackAlpha * gForceGravity[2] + (1f - gForceGravityTrackAlpha) * g[2]
            } else {
                gForceGravity[0] = gForceGravityTrackAlpha * gForceGravity[0] + (1f - gForceGravityTrackAlpha) * rawAccel[0]
                gForceGravity[1] = gForceGravityTrackAlpha * gForceGravity[1] + (1f - gForceGravityTrackAlpha) * rawAccel[1]
                gForceGravity[2] = gForceGravityTrackAlpha * gForceGravity[2] + (1f - gForceGravityTrackAlpha) * rawAccel[2]
            }
        }
    }

    private fun resolveGForceGravityRef(): FloatArray {
        if (DragCalibration.isUniversalCalibrated) {
            return DragCalibration.gravityVector
        }
        // Frozen/static G ref only — never live Madgwick/LP gravity (snap-to-center source).
        return gForceGravity
    }

    /**
     * RaceBox-style phone G: raw−static/frozen gravity (already in [deviceLinearAccel]) →
     * vehicle axes → shared 5 Hz LP ([chartQuality*]) → light HUD EMA.
     * No deadband, bias learning, TYPE_LINEAR high-pass, or GPS hybrid overwrite.
     */
    private fun updateInertialForcesFromLinearAcceleration(deviceLinearAccel: FloatArray) {
        val timestampNs = SystemClock.elapsedRealtimeNanos()
        val forwardMps2: Float
        val lateralMps2: Float
        if (DragCalibration.isUniversalCalibrated && hasSmartMotionCalibration) {
            forwardMps2 = DragCalibration.getSignedForwardAccelerationFromLinear(deviceLinearAccel)
            lateralMps2 = DragCalibration.getSignedLateralAccelerationFromLinear(deviceLinearAccel)
        } else {
            forwardMps2 = deviceLinearAccel[1]
            lateralMps2 = deviceLinearAccel[0]
        }

        updateChartQualityImuG(
            forwardAccelMps2 = forwardMps2,
            lateralAccelMps2 = lateralMps2,
            timestampNs = timestampNs
        )

        val rawLong = chartQualityLongitudinalG
        val rawLat = chartQualityLateralG
        if (!hasPhoneGSmooth) {
            phoneLongGSmooth = rawLong
            phoneLatGSmooth = rawLat
            hasPhoneGSmooth = true
        } else {
            phoneLongGSmooth =
                raceBoxGSmoothAlpha * rawLong + (1f - raceBoxGSmoothAlpha) * phoneLongGSmooth
            phoneLatGSmooth =
                raceBoxGSmoothAlpha * rawLat + (1f - raceBoxGSmoothAlpha) * phoneLatGSmooth
        }

        measurementLongitudinalG = clamp(rawLong, -maxDisplayG, maxDisplayG)
        measurementLateralG = clamp(rawLat, -maxDisplayG, maxDisplayG)
        currentLongitudinalG = clamp(phoneLongGSmooth, -maxDisplayG, maxDisplayG)
        currentLateralG = clamp(phoneLatGSmooth, -maxDisplayG, maxDisplayG)
        stepHudDisplayG(measurementLongitudinalG, measurementLateralG)
        applyHudDisplayGToGauges()

        updateSessionGForceStatistics(measurementLateralG, measurementLongitudinalG, confidence = 1f)
        runOnUiThread { updateMotoGForceCard() }
    }

    private fun formatTime(timeMs: Long): String = LapTimeFormatter.formatMsTwoDecimalCentis(timeMs)

    private fun formatTimeWithMillis3(timeMs: Long): String =
        LapTimeFormatter.formatMs(timeMs, padMinutes = true)
    override fun onSensorChanged(event: SensorEvent?) {
        if (RaceBoxDebugGate.shouldOverridePhoneGps(this) && RaceBoxManager.isConnected) {
            return
        }
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
                    // Lower Madgwick accel feedback while dynamic so lean gravity
                    // absorbs sustained cornering more slowly (G uses frozen ref below).
                    val rawMagGyro = sqrt(
                        ev.values[0] * ev.values[0] +
                            ev.values[1] * ev.values[1] +
                            ev.values[2] * ev.values[2]
                    )
                    madgwick.beta = if (abs(rawMagGyro - SensorManager.GRAVITY_EARTH) > 0.35f ||
                        abs(currentLateralG) > 0.20f ||
                        abs(currentLongitudinalG) > 0.20f
                    ) {
                        0.012f
                    } else {
                        0.033f
                    }
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
                updateGForceGravityReference(ev.values)

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
                    // Always build linear acceleration from raw − static/frozen gravity.
                    // Do not blend TYPE_LINEAR_ACCELERATION — it high-passes sustained G on many phones.
                    val gRef = resolveGForceGravityRef()
                    linearAccel[0] = ev.values[0] - gRef[0]
                    linearAccel[1] = ev.values[1] - gRef[1]
                    linearAccel[2] = ev.values[2] - gRef[2]
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
                    val useCalibratedRollAxis = useCalibratedBikeLeanAxes()
                    val rawRollRateRad = if (useCalibratedRollAxis) {
                        // Roll around bike forward axis, independent of phone mounting orientation.
                        val fw = DragCalibration.forwardVector
                        gx * fw[0] + gy * fw[1] + gz * fw[2]
                    } else if (isLandscape) {
                        gx * leanSign
                    } else {
                        gy
                    }
                    // Calibrated axes: +roll about forward = lean right (matches +asin(g·right)).
                    // Legacy phone axes keep the historical inverted sign.
                    val rollRateDeg = if (useCalibratedRollAxis) {
                        rawRollRateRad * radToDeg
                    } else {
                        -rawRollRateRad * radToDeg
                    }
                    val rollRateFilterAlpha = if (useCalibratedRollAxis) 0.16f else 0.25f
                    latestRollRateDegPerSec =
                        rollRateFilterAlpha * rollRateDeg +
                            (1f - rollRateFilterAlpha) * latestRollRateDegPerSec
                    val down = DragCalibration.gravityVector
                    val rawYawDeg = if (useCalibratedRollAxis) {
                        MotorcycleLeanAccelGate.yawRateDegPerSec(gx, gy, gz, down[0], down[1], down[2])
                    } else {
                        gz * radToDeg
                    }
                    latestYawRateDegPerSec =
                        0.18f * rawYawDeg + 0.82f * latestYawRateDegPerSec

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
                    if (isRecording && lapStartTime > 0L) {
                        lapStream?.appendGyro(gx, gy, gz)
                        maybeFlushLapStream()
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
            if (selectedProfileId != -1L) {
                reloadMotionCalibrationForProfile(selectedProfileId)
            } else {
                updateProfileLeanOffsetForOrientation(isLandscape)
            }
            // Re-seed lean fusion after axis swap so L/R stay consistent with the new rightVector.
            hasGyroIntegratedLean = false
            leanGyroIntegrationTimestampNs = 0L
            leanAccelGate.reset()
            lastLeanOrientationLandscape = isLandscape
        }

        val useAdvancedLeanFusion = useCalibratedBikeLeanAxes()
        val accelReferenceTilt = if (totalGravity > 0f) {
            if (useAdvancedLeanFusion) {
                // Lean from gravity projection on calibrated bike RIGHT axis.
                // Positive = lean right, negative = lean left (matches gyro·forward roll rate).
                val rv = DragCalibration.rightVector
                val rightComponent = ((x * rv[0] + y * rv[1] + z * rv[2]) / totalGravity).toDouble().coerceIn(-1.0, 1.0)
                (Math.toDegrees(Math.asin(rightComponent))).toFloat()
            } else if (isLandscape) {
                (-Math.toDegrees(Math.asin(((leanSign * y) / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
            } else {
                (-Math.toDegrees(Math.asin((x / totalGravity).toDouble().coerceIn(-1.0, 1.0)))).toFloat()
            }
        } else 0f

        // Complementary filter: gyro for fast lean changes, accel reference corrects drift.
        if (!hasGyroIntegratedLean) {
            gyroIntegratedLeanDeg = accelReferenceTilt
            filteredAngle = accelReferenceTilt
            hasGyroIntegratedLean = true
        } else {
            val speedMps = lastLocation?.speed ?: 0f
            val kinematicLatG = MotorcycleLeanAccelGate.kinematicLateralG(speedMps, latestYawRateDegPerSec)
            val straightGain = if (gyroscope == null) {
                val dynamicLoadG = (worldMag / SensorManager.GRAVITY_EARTH).coerceAtLeast(0f)
                val accelMotionTrust = (1f - dynamicLoadG * 0.55f).coerceIn(0.18f, 1f)
                val gyroSpinPenalty = (lastGyroMagnitude / 4.0f).coerceIn(0f, 1f)
                val accelTrust = (accelMotionTrust * (1f - 0.25f * gyroSpinPenalty)).coerceIn(0.15f, 1f)
                if (useAdvancedLeanFusion) {
                    (0.04f + (0.20f - 0.04f) * accelTrust).coerceIn(0.04f, 0.20f)
                } else {
                    (minAccelCorrection + (maxAccelCorrection - minAccelCorrection) * accelTrust)
                        .coerceIn(minAccelCorrection, maxAccelCorrection)
                }
            } else {
                MotorcycleLeanAccelGate.STRAIGHT_ACCEL_GAIN
            }
            val correctionGain = if (gyroscope == null) {
                straightGain
            } else {
                leanAccelGate.correctionGain(
                    speedMps = speedMps,
                    yawRateDegPerSec = latestYawRateDegPerSec,
                    kinematicLateralG = kinematicLatG,
                    gyroLeanDeg = gyroIntegratedLeanDeg,
                    accelLeanDeg = accelReferenceTilt,
                    straightGain = straightGain
                )
            }

            gyroIntegratedLeanDeg += correctionGain * (accelReferenceTilt - gyroIntegratedLeanDeg)
            filteredAngle = gyroIntegratedLeanDeg
        }

        val shouldApplyRuntimeAutoZero =
            isMotorcycle &&
                leanAutoZeroPending &&
                !(useAdvancedLeanFusion && hasProfileLeanOffset)

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
        displayLeanAngleSample: Float
    ) {
        if (isRecording && lapStartTime > 0L) {
            // Phone path: drag-style axis projection + 5 Hz LP into chartQuality* before store.
            // RaceBox path updates chartQuality* in applyRaceBoxTelemetry instead.
            val raceBoxActive = RaceBoxDebugGate.shouldOverridePhoneGps(this) && RaceBoxManager.isConnected
            if (!raceBoxActive) {
                // chartQuality* already updated in updateInertialForcesFromLinearAcceleration
                // (same 5 Hz LP RaceBox uses) — do not re-filter here.
                val nowElapsed = SystemClock.elapsedRealtime()
                if (nowElapsed - lastPhoneTelemetryPersistMs < 20L) {
                    return
                }
                lastPhoneTelemetryPersistMs = nowElapsed
            }

            val sampleTimestamp = System.currentTimeMillis()
            if (lapStream == null) {
                openLapStream(
                    lapNumber = currentLapData.lapNumber.coerceAtLeast(1),
                    startTime = lapStartTime
                )
            }
            val stream = lapStream ?: return
            stream.appendTelemetry(
                ax = deviceAccel.getOrElse(0) { 0f },
                ay = deviceAccel.getOrElse(1) { 0f },
                az = deviceAccel.getOrElse(2) { 0f },
                lean = currentCalibratedLean,
                displayLean = if (displayLeanAngleSample.isFinite()) displayLeanAngleSample else currentCalibratedLean,
                longG = chartQualityLongitudinalG,
                latG = chartQualityLateralG,
                maxBrake = maxBraking,
                maxAccel = maxAcceleration,
                maxCornL = maxCorneringLeftG,
                maxCornR = maxCorneringRightG,
                maxResult = maxCarResultG,
                timestampMs = sampleTimestamp
            )
            maybeFlushLapStream()
        }
    }

    /**
     * Same capture path as Drag: filter vehicle-axis accel in m/s² at ~5 Hz, store inertial G
     * (forward accel → negative) for charts/map.
     */
    private fun updateChartQualityImuG(
        forwardAccelMps2: Float,
        lateralAccelMps2: Float,
        timestampNs: Long
    ) {
        val filtForward = TrackGForceChartSmoothing.applyLowPassMps2(
            forwardAccelMps2,
            timestampNs,
            chartLongLpState
        )
        val filtLateral = TrackGForceChartSmoothing.applyLowPassMps2(
            lateralAccelMps2,
            timestampNs,
            chartLatLpState
        )
        chartQualityLongitudinalG = (-filtForward / 9.81f).coerceIn(-3.5f, 3.5f)
        chartQualityLateralG = (-filtLateral / 9.81f).coerceIn(-3.5f, 3.5f)
    }

    private fun clamp(v: Float, min: Float, max: Float) = when {
        v < min -> min
        v > max -> max
        else -> v
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onLocationChanged(location: Location) {
        if (RaceBoxDebugGate.shouldOverridePhoneGps(this) &&
            RaceBoxManager.isConnected &&
            location.provider != "racebox"
        ) {
            return
        }
        previousLocationForCrossing = lastLocation
        lastLocation = Location(location)
        val speedKmh = GnssSpeedSanitizer.sanitizeFromLocation(
            location = location,
            previous = previousLocationForCrossing,
            isStationaryHint = isStationary
        )
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
            recordPredictiveReferenceSample()
            lastLocationTimeMs = nowT
            
            sessionSpeedSumKmh += speedKmh
            sessionSpeedSamples += 1

            val currentTime = System.currentTimeMillis()
            val relativeTimestamp = currentTime - lapStartTime

            // Full GPS rate (RaceBox 25 Hz / phone as delivered) streamed to disk — not RAM lists.
            if (lapStream == null) {
                openLapStream(
                    lapNumber = currentLapData.lapNumber.coerceAtLeast(1),
                    startTime = lapStartTime
                )
            }
            appendRouteSample(
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = speedKmh,
                angle = currentCalibratedLean,
                timestampMs = relativeTimestamp,
                absoluteTimeMs = location.time
            )
            updateP2pRunMetaUi()
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
        ignoreDebounce: Boolean = false,
        requireArrowDirection: Boolean = false
    ): Boolean {
        val previous = previousLocationForCrossing ?: return false
        val gpsStepMeters = previous.distanceTo(location).toDouble()
        if (gpsStepMeters < 0.8) {
            return false
        }
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
            val padded = paddedGateEndpoints(point1, point2, gateDetectionEndPadMeters)
            if (padded != null) {
                crossed = gateCrossingEngine.didCrossLine(
                    previousLat = previous.latitude,
                    previousLon = previous.longitude,
                    currentLat = location.latitude,
                    currentLon = location.longitude,
                    lineStartLat = padded.first.latitude,
                    lineStartLon = padded.first.longitude,
                    lineEndLat = padded.second.latitude,
                    lineEndLon = padded.second.longitude
                )
                if (crossed) {
                    android.util.Log.d("TrackSessionActivity", "✅ PADDED LINE CROSS DETECTED")
                }
            }
        }

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
            val nearCorridor = maxOf(28.0, gpsStepMeters * 0.55)
            if (sideChanged && minDistanceToLine <= nearCorridor) {
                crossed = true
                android.util.Log.d("TrackSessionActivity", "✅ TOLERANT LINE CROSS DETECTED (near-line side change)")
            }
        }

        if (!crossed) {
            return false
        }

        if (requireArrowDirection) {
            val alongArrow = TrackGateDirection.isCrossingWithArrow(
                previousLat = previous.latitude,
                previousLon = previous.longitude,
                currentLat = location.latitude,
                currentLon = location.longitude,
                lineStartLat = point1.geoPoint.latitude,
                lineStartLon = point1.geoPoint.longitude,
                lineEndLat = point2.geoPoint.latitude,
                lineEndLon = point2.geoPoint.longitude
            )
            if (!alongArrow) {
                android.util.Log.d(
                    "TrackSessionActivity",
                    "⏸️ Gate crossed against the start arrow (swap the two line points to reverse)"
                )
                return false
            }
        }

        if (!ignoreDebounce && now - lastStartFinishCrossAtMs < startFinishCrossDebounceMs) {
            android.util.Log.d("TrackSessionActivity", "⏸️ Start/finish debounce active (${now - lastStartFinishCrossAtMs}ms)")
            return false
        }

        android.util.Log.d("TrackSessionActivity", "✅ STRICT LINE CROSS DETECTED")
        lastStartFinishCrossAtMs = now
        return true
    }

    private fun paddedGateEndpoints(
        point1: TrackPoint,
        point2: TrackPoint,
        padMeters: Double
    ): Pair<GeoPoint, GeoPoint>? {
        val start = point1.geoPoint
        val end = point2.geoPoint
        if (distanceMeters(start, end) < 0.5f) return null
        val bearing = bearingDegrees(start, end)
        return offsetGeoPointByBearing(start, (bearing + 180.0) % 360.0, padMeters) to
            offsetGeoPointByBearing(end, bearing, padMeters)
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
                tvLapTime.text = getString(R.string.track_distance_to_start_finish, meters)

                // Check initial start/finish line (indices 0 and 1)
                val crossed = checkStartFinishLineCrossing(
                    location = location,
                    point1 = startLine.first,
                    point2 = startLine.second,
                    requireArrowDirection = isCustomTrackSession()
                )
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
                    ignoreDebounce = currentTrackMode == TrackMode.POINT_TO_POINT,
                    requireArrowDirection = isCustomTrackSession() &&
                        currentTrackMode != TrackMode.POINT_TO_POINT
                )
                if (crossed) {
                    val crossingTiming = resolveCrossingTiming(
                        location,
                        finishLine.first,
                        finishLine.second
                    )
                    val crossingWallMs =
                        crossingTiming?.crossingWallTimeMs ?: System.currentTimeMillis()
                    val crossingLocation = crossingTiming?.location ?: location

                    if (currentTrackMode == TrackMode.POINT_TO_POINT) {
                        finalizePointToPointRun(
                            crossingLocation = crossingLocation,
                            crossingWallTimeMs = crossingWallMs
                        )
                        android.util.Log.d("TrackSessionActivity", "✅ POINT_TO_POINT FINISH LINE CROSSED - stopping session")
                        stopRecording()
                        return
                    }

                    val lapElapsedTime = lapTimingEngine.lapElapsedMs(lapStartTime, crossingWallMs)
                    if (!lapTimingEngine.canCompleteLap(lapStartTime, crossingWallMs)) {
                        android.util.Log.d("TrackSessionActivity", "⏸️ Crossed line but too soon! Elapsed: ${lapElapsedTime / 1000}s (need ${lapTimingEngine.minLapTimeMs / 1000}s)")
                        return
                    }
                    
                    // Record sector and lap
                    val sectorTime = (crossingWallMs - sectorStartTime).coerceAtLeast(0L)
                    sectorTimes.add(sectorTime)
                    sectorDistances.add(sectorDistanceAccum)
                    
                    recordLap(
                        crossingLocation = crossingLocation,
                        crossingWallTimeMs = crossingWallMs
                    )

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
            
            // Check if this is START_FINISH point (lap boundary)
            val isStartFinish = targetIndex < trackPointTypes.size && 
                               trackPointTypes[targetIndex] == com.revix.app.tracking.CustomTrack.TrackPoint.PointType.START_FINISH
            
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
                recordLap(location)
                // For custom circuit tracks with 2 points (start/finish duplicated), 
                // go back to point 1 (the duplicate) to check for next lap completion
                // Point 0 is ONLY for initial start detection
                currentTrackPointIndex = if (trackPoints.size == 2) 1 else 0
            }
        }
    }

    private fun beginTimedSession(location: Location) {
        awaitingStart = false

        val startLine = getStartLinePoints()
        val crossingTiming = startLine?.let { line ->
            resolveCrossingTiming(location, line.first, line.second)
        }
        val crossingLocation = crossingTiming?.location
            ?: (lastLocation ?: location)
        // Prefer interpolated gate time so 1 Hz GPS does not delay lap start to the detecting tick.
        lapStartTime = crossingTiming?.crossingWallTimeMs ?: System.currentTimeMillis()
        if (sessionTelemetryStartWallTimeMs <= 0L) {
            sessionTelemetryStartWallTimeMs = lapStartTime
        }
        updateSessionVideoSyncMarkerIfNeeded()
        updateProjectedRouteDistance(location)
        projectedRouteDistanceAtLapStartMeters = currentProjectedRouteDistanceMeters
        clearCurrentLapReferenceSamples()
        updateCurrentLapBadge(displayCurrentLapNumber())
        updateLapDistanceProgress(0f)
        android.util.Log.d(
            "TrackSessionActivity",
            "⏰ LAP START TIME SET: $lapStartTime (interpolated=${crossingTiming != null}, ratio=${crossingTiming?.ratio})"
        )

        val currentSpeedKmh = GnssSpeedSanitizer.sanitizeFromLocation(
            location = crossingLocation,
            previous = previousLocationForCrossing,
            isStationaryHint = isStationary
        )
        openLapStream(lapNumber = 1, startTime = lapStartTime)
        appendRouteSample(
            latitude = crossingLocation.latitude,
            longitude = crossingLocation.longitude,
            speedKmh = currentSpeedKmh,
            angle = currentCalibratedLean,
            timestampMs = 0L,
            absoluteTimeMs = crossingLocation.time.takeIf { it > 0L } ?: lapStartTime
        )
        updateP2pRunMetaUi()

        sectorStartTime = lapStartTime
        currentSector = 0
        currentTrackPointIndex = if (hasGateBasedTriggering()) 2 else 1
        statsFilteredLongG = 0f
        statsFilteredLatG = 0f
        maxLeanAngle = 0f
        maxLeanLeftAngle = 0f
        maxLeanRightAngle = 0f
        currentLongitudinalG = 0f
        lastTelemetrySpeedKmh = 0f
        currentLateralG = 0f
        measurementLongitudinalG = 0f
        measurementLateralG = 0f
        resetHudDisplayG()
        noGyroGpsLongStatsSmooth = 0f
        noGyroLeanLatGSmooth = 0f
        speedGauge.resetGForceHistory()
        resetPeakDetectors()
        smoothedConfidence = 1f
        latestRollRateDegPerSec = 0f
        latestYawRateDegPerSec = 0f
        leanAccelGate.reset()
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

    /**
     * Interpolated position + wall time for a gate crossing between previous and current GPS fix.
     * Same path for phone GPS and RaceBox samples.
     */
    private fun resolveCrossingTiming(
        location: Location,
        lineStart: TrackPoint,
        lineEnd: TrackPoint
    ): GateCrossingTiming? {
        val previous = previousLocationForCrossing ?: return null
        return resolveGateCrossingTiming(
            previous = previous,
            current = location,
            lineStartLat = lineStart.geoPoint.latitude,
            lineStartLon = lineStart.geoPoint.longitude,
            lineEndLat = lineEnd.geoPoint.latitude,
            lineEndLon = lineEnd.geoPoint.longitude
        )
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
            tvLapTime.text = getString(R.string.track_distance_to_start, meters)
        } else {
            tvLapTime.text = getString(R.string.track_approach_start_to_begin)
        }

        val strictCrossed = checkStartFinishLineCrossing(
            location = location,
            point1 = startLineA,
            point2 = startLineB,
            ignoreDebounce = true,
            requireArrowDirection = isCustomTrackSession()
        )

        if (strictCrossed) {
            android.util.Log.d("TrackSessionActivity", "POINT_TO_POINT start line crossed - run started")
            beginTimedSession(location)
        }
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
            metersLabel = getString(R.string.track_distance_meters_short, meters),
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
        val prefix = getString(R.string.track_distance_from_start_finish_prefix)
        val body = if (isNearStartLine) {
            getString(R.string.track_cross_start_finish_to_begin)
        } else {
            getString(R.string.track_approach_start_finish_to_begin)
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

    private fun finalizePointToPointRun(
        crossingLocation: Location? = null,
        crossingWallTimeMs: Long? = null
    ) {
        if (!isRecording || lapStartTime <= 0L) return

        val finishTimestamp = crossingWallTimeMs ?: System.currentTimeMillis()
        val runElapsedMs = (finishTimestamp - lapStartTime).coerceAtLeast(0L)
        if (crossingLocation != null) {
            android.util.Log.d(
                "TrackSessionActivity",
                "P2P finish interpolated at ${crossingLocation.latitude},${crossingLocation.longitude} t=$finishTimestamp"
            )
        }

        currentLap = 1
        totalLaps = 1
        updateCurrentLapBadge(displayCurrentLapNumber())
        updateLapDistanceProgress(1f)

        lapTimes.clear()
        lapTimes.add(runElapsedMs)
        bestLapTime = runElapsedMs
        bestLapNumber = 1
        updateLapSummaryCards(runElapsedMs)

        markActiveTrackSession()
        val stub = flushCompletedLapToDisk(lapNumber = 1, endTime = finishTimestamp)
        if (lapData.isEmpty()) {
            lapData.add(stub)
        } else {
            lapData[lapData.lastIndex] = stub
        }
        currentLapData = stub
        refreshLiveHudMiniMapShape()

        val completedDistanceMeters = lapDistanceAccum
        maybePersistCustomMeasuredDistance(completedDistanceMeters)
        markCustomTrackCalibratedFromSession(completedDistanceMeters)

        showToast(getString(R.string.track_finish_toast, formatTime(runElapsedMs)))
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
        if (com.revix.app.BuildConfig.DEBUG) {
            RaceBoxManager.ensureInitialized(this)
            RaceBoxManager.addSampleListener(raceBoxTrackSampleListener)
        }
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
        refreshTrackCameraKeepScreenOn()
        if (sessionCameraMode != SessionCameraMode.OFF && activeVideoRecording == null) {
            bindSessionCameraPreview()
        } else {
            maybeRestartSessionVideoAfterInterrupt()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Never recreate the UI / rebind CameraX while a session video is recording.
        if (activeVideoRecording != null || isVideoRecordingActive) {
            return
        }

        val wasAwaitingStartDialogVisible = awaitingStartDialog?.isShowing == true
        dismissAwaitingStartDialog()

        setContentView(getLayoutResourceId())
        applySystemBarsPaddingToRoot()
        setupBottomNavigation()
        initializeViews(preserveActiveSessionUi = true)
        setupClickListeners()

        val profileId = ProfileStorage.getSelectedProfileId(this)
        if (profileId != -1L) {
            DragCalibration.setProfile(profileId)
            reloadLeanCalibrationForProfile(profileId, forceResetRuntime = false)
            reloadMotionCalibrationForProfile(profileId)
        }

        restoreActiveSessionUiAfterConfigurationChange()

        if (wasAwaitingStartDialogVisible && awaitingStart && isRecording) {
            showAwaitingStartDialog()
        }
    }

    private fun restoreActiveSessionUiAfterConfigurationChange() {
        if (isRecording) {
            btnStartStop.text = getString(R.string.track_button_stop)
            btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        } else {
            btnStartStop.text = getString(R.string.track_button_start)
            btnStartStop.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
        }

        updateCurrentLapBadge(displayCurrentLapNumber())
        rebuildRecordedLapsUi()
        applyPredictiveGapSourceUi()
        updateDisplay()
        updateMotoGForceCard()
        speedGauge.gForceX = displayHudLatG
        speedGauge.gForceY = displayHudLongG
        gGaugeTrackCar.gForceX = displayHudLatG
        gGaugeTrackCar.gForceY = displayHudLongG
        if (isMotorcycle) {
            speedGauge.setLeanAngle(displayLeanAngle)
            leanVisualizer.setLeanAngle(displayLeanAngle)
            updateTopLeanTelemetry(currentCalibratedLean)
        }
        updateCameraButtonUi()
        updateCameraPreviewCardVisibility()
        refreshLiveHudMiniMapShape()
        if (sessionCameraMode != SessionCameraMode.OFF) {
            bindSessionCameraPreview()
        }
        if (isRecording) {
            handler.removeCallbacks(updateRunnable)
            handler.post(updateRunnable)
        }
    }

    private fun rebuildRecordedLapsUi() {
        llLapsContainer.removeAllViews()
        if (lapData.isEmpty()) {
            tvNoLaps.visibility = View.VISIBLE
            return
        }

        tvNoLaps.visibility = View.GONE
        lapData.asReversed().forEachIndexed { reversedIndex, lap ->
            val sourceIndex = lapData.lastIndex - reversedIndex
            val durationMs = when {
                lap.endTime > lap.startTime -> lap.endTime - lap.startTime
                sourceIndex in lapTimes.indices -> lapTimes[sourceIndex]
                else -> 0L
            }
            addLapToUI(
                lapNumber = lap.lapNumber,
                lapTime = formatTime(durationMs),
                isBestLap = lap.lapNumber == bestLapNumber
            )
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

    override fun onStop() {
        // Soft-stop camera so Part 1 is kept when leaving for a call / another app.
        if (isRecording) {
            softStopSessionVideoForInterrupt()
        }
        super.onStop()
    }
    override fun onDestroy() {
        if (com.revix.app.BuildConfig.DEBUG) {
            RaceBoxManager.removeSampleListener(raceBoxTrackSampleListener)
        }
        unlockSessionOrientationAfterCameraRecording()
        ScreenKeepOnController.setKeepOnOverride(this, false)
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(hideCameraPreviewHeaderRunnable)
        locationManager.removeUpdates(this)
        releaseTrackWakeLock()
        unbindSessionCamera()
        soundManager.release()
    }

    private fun applyRaceBoxTelemetry(sample: RaceBoxProtocol.Sample) {
        val rawLong = sample.displayLongitudinalG
        val rawLat = sample.displayLateralG
        if (!hasRaceBoxGSmooth) {
            raceBoxLongGSmooth = rawLong
            raceBoxLatGSmooth = rawLat
            hasRaceBoxGSmooth = true
        } else {
            raceBoxLongGSmooth =
                raceBoxGSmoothAlpha * rawLong + (1f - raceBoxGSmoothAlpha) * raceBoxLongGSmooth
            raceBoxLatGSmooth =
                raceBoxGSmoothAlpha * rawLat + (1f - raceBoxGSmoothAlpha) * raceBoxLatGSmooth
        }

        currentLateralG = raceBoxLatGSmooth
        currentLongitudinalG = raceBoxLongGSmooth
        measurementLateralG = raceBoxLatGSmooth
        measurementLongitudinalG = raceBoxLongGSmooth
        stepHudDisplayG(rawLong, rawLat)
        applyHudDisplayGToGauges()
        if (isMotorcycle) {
            filteredAngle = sample.leanDegApprox
            currentCalibratedLean = (sample.leanDegApprox - offsetAngle).coerceIn(-90f, 90f)
            if (currentCalibratedLean < 0f) {
                maxLeanLeftAngle = max(maxLeanLeftAngle, abs(currentCalibratedLean))
            } else if (currentCalibratedLean > 0f) {
                maxLeanRightAngle = max(maxLeanRightAngle, currentCalibratedLean)
            }
            updateTopLeanTelemetry(currentCalibratedLean)
            if (::speedGauge.isInitialized) {
                speedGauge.setLeanAngle(displayLeanAngle)
            }
        }
        updateSessionGForceStatistics(raceBoxLatGSmooth, raceBoxLongGSmooth, confidence = 1f)
        updateMotoGForceCard()

        // Same IMU axes Drag uses for its accel chart (gX forward, gY lateral), with shared 5 Hz LP.
        val sampleNs = SystemClock.elapsedRealtimeNanos()
        updateChartQualityImuG(
            forwardAccelMps2 = sample.gForceX * 9.81f,
            lateralAccelMps2 = sample.gForceY * 9.81f,
            timestampNs = sampleNs
        )

        if (isRecording && lapStartTime > 0L) {
            val nowElapsed = SystemClock.elapsedRealtime()
            if (nowElapsed - lastRaceBoxTelemetryPersistMs >= raceBoxTelemetryMinIntervalMs) {
                lastRaceBoxTelemetryPersistMs = nowElapsed
                appendCurrentLapTelemetrySample(
                    deviceAccel = floatArrayOf(sample.gForceX, sample.gForceY, sample.gForceZ),
                    displayLeanAngleSample = if (isMotorcycle) displayLeanAngle else currentCalibratedLean
                )
            }
        }

        if (sample.fixOk) {
            onLocationChanged(sample.location)
        }
    }

    private fun acquireTrackWakeLock() {
        startLocationKeeperService()
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
        stopLocationKeeperService()
    }

    // Track сесията ползва LocationManager директно, но на заключен екран Android
    // throttle-ва location-а, защото activity-то е на заден план. Като пуснем
    // ForegroundService (вече истински foreground service от тип location), целият
    // процес се води "на преден план" и GPS-ът продължава да тече. PRE_WARMING_MODE
    // го държи лек - без измервания, само промоция на процеса.
    private fun startLocationKeeperService() {
        try {
            val intent = Intent(this, ForegroundService::class.java).apply {
                putExtra("PRE_WARMING_MODE", true)
            }
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            android.util.Log.w("TrackSessionActivity", "Unable to start location keeper service", e)
        }
    }

    private fun stopLocationKeeperService() {
        try {
            stopService(Intent(this, ForegroundService::class.java))
        } catch (e: Exception) {
            android.util.Log.w("TrackSessionActivity", "Unable to stop location keeper service", e)
        }
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
    // Progress in meters from the current lap's start, measured along the track centerline
    // (position-based). This is the alignment key shared by the reference and the live lap.
    // Collect a strictly-increasing (distance-travelled -> elapsed) sample for the live lap so it
    // can become the reference table when it turns out to be the best lap. Distance travelled
    // (lapDistanceAccum) is used as the alignment key because it is always monotonic and does not
    // depend on a predefined route geometry, unlike route projection.
    private fun recordPredictiveReferenceSample() {
        if (!isRecording || awaitingStart || lapStartTime <= 0L) return
        val progress = lapDistanceAccum
        if (!progress.isFinite() || progress < 0f) return
        val elapsed = System.currentTimeMillis() - lapStartTime
        if (elapsed < 0L) return
        val lastP = currentLapRefDistances.lastOrNull()
        if (lastP == null || progress > lastP + 1.0f) {
            if (currentLapRefDistances.size < maxPredictiveReferenceSamples) {
                currentLapRefDistances.add(progress)
                currentLapRefElapsedMs.add(elapsed)
            }
        }
    }

    private fun snapshotBestLapReference(totalLapMs: Long) {
        if (currentLapRefDistances.size < 5) return
        bestLapRefDistances.clear()
        bestLapRefDistances.addAll(currentLapRefDistances)
        bestLapRefElapsedMs.clear()
        bestLapRefElapsedMs.addAll(currentLapRefElapsedMs)
        bestLapRefTotalMs = totalLapMs
    }

    private fun clearCurrentLapReferenceSamples() {
        currentLapRefDistances.clear()
        currentLapRefElapsedMs.clear()
    }

    // Active reference table for the selected predictive mode.
    private fun activeRefDistances(): List<Float> = when (predictiveGapSource) {
        PredictiveGapSource.SESSION_BEST -> bestLapRefDistances
        PredictiveGapSource.TRACK_BEST -> trackBestRefDistances
    }

    private fun activeRefElapsedMs(): List<Long> = when (predictiveGapSource) {
        PredictiveGapSource.SESSION_BEST -> bestLapRefElapsedMs
        PredictiveGapSource.TRACK_BEST -> trackBestRefElapsedMs
    }

    private fun activeRefTotalMs(): Long = when (predictiveGapSource) {
        PredictiveGapSource.SESSION_BEST -> bestLapRefTotalMs
        PredictiveGapSource.TRACK_BEST -> trackBestRefTotalMs
    }

    private fun interpolateReferenceElapsedMs(
        distances: List<Float>,
        elapsed: List<Long>,
        progress: Float
    ): Float? {
        val n = distances.size
        if (n < 2 || elapsed.size != n) return null
        if (progress <= distances.first()) return elapsed.first().toFloat()
        if (progress >= distances.last()) return elapsed.last().toFloat()
        var lo = 0
        var hi = n - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (distances[mid] <= progress) lo = mid else hi = mid
        }
        val d0 = distances[lo]
        val d1 = distances[hi]
        val t0 = elapsed[lo].toFloat()
        val t1 = elapsed[hi].toFloat()
        val f = if (d1 > d0) (progress - d0) / (d1 - d0) else 0f
        return t0 + f * (t1 - t0)
    }

    // Professional position-based delta-T (aligns by track position, like VBOX/Racelogic):
    // delta = elapsed_now - reference_elapsed_at_same_position. Returns true if rendered.
    private fun updatePositionBasedPredictiveGap(): Boolean {
        if (!isRecording || awaitingStart || lapStartTime <= 0L) return false
        val refDistances = activeRefDistances()
        val refElapsed = activeRefElapsedMs()
        val refTotalMs = activeRefTotalMs()
        if (refDistances.size < 5 || refTotalMs <= 0L) return false

        val progress = lapDistanceAccum
        if (!progress.isFinite() || progress < 0f) return false
        val refElapsedMs = interpolateReferenceElapsedMs(refDistances, refElapsed, progress) ?: return false

        val deltaSeconds = ((System.currentTimeMillis() - lapStartTime) - refElapsedMs) / 1000f
        val referenceSeconds = refTotalMs / 1000f
        if (referenceSeconds <= 0f) return false

        val target = referenceSeconds + deltaSeconds
        displayedPredictedLapSeconds = if (displayedPredictedLapSeconds.isNaN()) {
            target
        } else {
            0.30f * target + 0.70f * displayedPredictedLapSeconds
        }

        val nowUi = System.currentTimeMillis()
        if (nowUi - lastPredictionDisplayUpdateMs >= 250L) {
            lastPredictionDisplayUpdateMs = nowUi
            speedGauge.setPredictiveGap(displayedPredictedLapSeconds, referenceSeconds)
            updatePredictiveGapCard(displayedPredictedLapSeconds, referenceSeconds)
        }
        return true
    }

    private fun updateGauge() {
        if (!awaitingStart) {
            lastLocation?.let { location ->
                // Same stationary/Doppler floor as recorded samples — live gauge was still raw GPS.
                val currentSpeed = GnssSpeedSanitizer.sanitizeFromLocation(
                    location = location,
                    previous = previousLocationForCrossing,
                    isStationaryHint = isStationary
                )
                speedGauge.setSpeed(currentSpeed)
            }
        } else {
            speedGauge.setSpeed(0f)
        }

        // Professional position-based delta-T. If there is no usable reference yet
        // (e.g. first lap, or historical best without telemetry), just reset the gap.
        if (!updatePositionBasedPredictiveGap()) {
            resetPredictiveGapCard()
            resetPredictiveEstimatorState(clearGauge = true)
        }

        if (isMotorcycle && leanAngleData.isNotEmpty()) {
            val leanAngle = abs(leanAngleData.last())
            speedGauge.setLeanAngle(leanAngle)
        }
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    override fun onBackPressed() {
        if (isRecording) {
            DialogHelper.show(
                DialogHelper.builder(this)
                    .setTitle(getString(R.string.track_exit_confirm_title))
                    .setMessage(getString(R.string.track_exit_confirm_message))
                    .setPositiveButton(getString(R.string.track_exit_confirm_positive)) { _, _ ->
                        stopRecordingWithoutSaving()
                        super.onBackPressed()
                        overridePendingTransition(0, 0)
                    }
                    .setNegativeButton(getString(R.string.dialog_cancel_button), null)
            )
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
                ?.let { meters -> getString(R.string.track_distance_meters_short, meters) }
                ?: getString(R.string.track_distance_unknown),
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
        if (!hasCompletedLap()) {
            runOnUiThread {
                resetSessionVideoState(clearSavedMetadata = true, deleteFiles = true)
                showToast(getString(R.string.track_session_not_saved_no_laps))
            }
            return
        }
        Thread {
            try {
                val finalMaxLeanLeft = maxLeanLeftAngle
                val finalMaxLeanRight = maxLeanRightAngle
                val finalMaxLeanAngle = max(maxLeanAngle, max(finalMaxLeanLeft, finalMaxLeanRight))
                // Requested behavior: duration must be the sum of all recorded lap times.
                val totalLapDurationMs = lapTimes.sum()
                val sessionDurationFormatted = formatTimeWithMillis3(totalLapDurationMs)
                val bestLapFormatted = if (bestLapTime == Long.MAX_VALUE) LapTimeFormatter.PLACEHOLDER else formatTimeWithMillis3(bestLapTime)
                val envPrefs = PreferenceManager.getDefaultSharedPreferences(this)
                val cachedTemperature = envPrefs.getFloat("cached_temperature", Float.NaN)
                val cachedHumidity = envPrefs.getInt("cached_humidity", -1)
                val cachedWindKph = envPrefs.getFloat("cached_wind_kph", Float.NaN)
                val cachedWeatherIcon = envPrefs.getInt("cached_weather_icon", -1)
                // Persist the raw base values (°C / km/h) so the displayed unit always follows the
                // current setting — even for sessions saved while a different unit was selected.
                val sessionTemperature = if (!cachedTemperature.isNaN()) {
                    String.format(Locale.getDefault(), "%.1f", cachedTemperature)
                } else {
                    "--"
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
                val primaryClip = savedSessionVideoClips.firstOrNull()
                val sessionVideoUri = primaryClip?.uri.orEmpty()
                val sessionVideoPath = primaryClip?.path.orEmpty()
                val sessionVideoCamera = primaryClip?.camera.orEmpty()
                val sessionVideoStartOffsetMs = primaryClip?.sessionStartOffsetMs ?: 0L
                val sessionVideoElapsedAtStartMs = primaryClip?.sessionElapsedAtStartMs
                    ?: -sessionVideoStartOffsetMs
                val sessionVideoOverlayExported = primaryClip?.overlayExported ?: false
                val sessionVideoClipsJson = TrackSessionVideoClipsCodec.encode(savedSessionVideoClips.toList())
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
                    "videoOverlayExported" to sessionVideoOverlayExported.toString(),
                    "videoClipsJson" to TrackSessionVideoClipsCodec.encode(savedSessionVideoClips.toList()),
                    "recordedWithRaceBox" to RaceBoxDebugGate.shouldOverridePhoneGps(this).toString()
                )
                val isResumeSession = intent.getBooleanExtra("resume_session", false)
                val trackSessionsPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
                ensureLivePersistIdentity()
                val sessionIdRaw = when {
                    liveSessionId.isNotEmpty() && liveSessionId.contains('_') ->
                        liveSessionId.substringAfter('_')
                    isResumeSession -> {
                        val fullSessionId = intent.getStringExtra("session_id") ?: trackId
                        if (fullSessionId.matches(Regex("\\d+_.*"))) {
                            fullSessionId.substringAfter("_")
                        } else {
                            fullSessionId
                        }
                    }
                    else -> {
                        trackSessionsPrefs.getString("pending_session_id_raw", null)
                            ?: run {
                                val date = outingData["date"] ?: ""
                                val time = outingData["time"] ?: ""
                                val timestamp = System.currentTimeMillis()
                                "${trackId}_${date}_${time.replace(":", "")}_${timestamp}"
                            }
                    }
                }
                saveOutingDataWithSessionId(outingData, sessionIdRaw)
                val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
                val currentProfileId = ProfileStorage.getSelectedProfileId(this)
                val sessionIdWithProfile = "${currentProfileId}_${sessionIdRaw}"
                clearActiveSession()
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
                    if (savedSessionVideoClips.isNotEmpty()) {
                        TrackSessionVideoOverlayService.start(
                            this@TrackSessionActivity,
                            sessionIdWithProfile,
                            outingNumber
                        )
                    }
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
        val sessionId = if (liveSessionId.isNotEmpty()) liveSessionId else "${currentProfileId}_${sessionIdRaw}"
        val outingNumber = if (liveOutingNumber > 0) {
            liveOutingNumber
        } else {
            sharedPrefs.getInt("${sessionId}_outing_count", 0) + 1
        }
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
        editor.putBoolean(
            "${sessionId}_outing_${outingNumber}_recorded_with_racebox",
            outingData["recordedWithRaceBox"].toBoolean()
        )
        // Distance / per-lap max speed were written as each lap finished (stream → disk).
        editor.putFloat(
            "${sessionId}_outing_${outingNumber}_distance_km",
            sessionDistanceKmAccum.toFloat()
        )
        val videoUri = outingData["videoUri"].orEmpty()
        val videoPath = outingData["videoPath"].orEmpty()
        val videoCamera = outingData["videoCamera"].orEmpty()
        val videoSessionStartOffsetMs = outingData["videoSessionStartOffsetMs"]?.toLongOrNull() ?: 0L
        val videoSessionElapsedAtStartMs = outingData["videoSessionElapsedAtStartMs"]?.toLongOrNull() ?: -videoSessionStartOffsetMs.coerceAtLeast(0L)
        val videoOverlayExported = outingData["videoOverlayExported"].toBoolean()
        val videoClipsJson = outingData["videoClipsJson"].orEmpty()
        val clipsForPersist = TrackSessionVideoClipsCodec.decode(videoClipsJson).ifEmpty {
            if (videoUri.isNotBlank() || videoPath.isNotBlank()) {
                listOf(
                    TrackSessionVideoClip(
                        uri = videoUri,
                        path = videoPath,
                        camera = videoCamera,
                        sessionStartOffsetMs = videoSessionStartOffsetMs,
                        sessionElapsedAtStartMs = videoSessionElapsedAtStartMs,
                        overlayExported = videoOverlayExported
                    )
                )
            } else {
                emptyList()
            }
        }
        TrackSessionVideoClipsCodec.writeToEditor(editor, sessionId, outingNumber, clipsForPersist)
        editor.putBoolean(
            "${sessionId}_outing_${outingNumber}_telemetry_overlay_pending",
            clipsForPersist.isNotEmpty() &&
                clipsForPersist.none { clip -> clip.kind == TrackSessionVideoKind.SESSION }
        )
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
        val openLap = materializeOpenLapFromStream(sessionEndTime.takeIf { it > 0L } ?: System.currentTimeMillis())
        if (openLap == null) {
            editor.remove(snapshotKey)
            return
        }

        val lastSavedLap = lapData.lastOrNull()
        val isDuplicateOfLastSavedLap = lastSavedLap != null &&
            lastSavedLap.lapNumber == openLap.lapNumber &&
            lastSavedLap.startTime == openLap.startTime &&
            lastSavedLap.endTime == openLap.endTime
        if (isDuplicateOfLastSavedLap) {
            editor.remove(snapshotKey)
            return
        }

        // Keep a tiny marker in prefs; full payload goes to the same file store as finished laps.
        val snapshotLapIndex = 10_000 + outingNumber
        TrackLapDataStore.saveLap(
            context = this,
            editor = editor,
            sessionId = sessionId,
            outingNumber = outingNumber,
            lapIndex = snapshotLapIndex,
            lap = openLap
        )
        editor.putString(snapshotKey, "file:$snapshotLapIndex")
    }
    
    private fun saveLapData(editor: android.content.SharedPreferences.Editor, sessionId: String, outingNumber: Int) {
        // Finished laps were already flushed to TrackLapDataStore during the session.
        // Do not overwrite those JSON files with empty RAM stubs.
        for (i in lapData.indices) {
            val lapIndex = i + 1
            val exists = TrackLapDataStore.lapFileExists(this, sessionId, outingNumber, lapIndex)
            if (!exists) {
                android.util.Log.e(
                    "TrackSessionActivity",
                    "Missing on-disk lap $lapIndex for $sessionId outing $outingNumber"
                )
            }
            editor.putString(TrackLapDataStore.prefsKey(sessionId, outingNumber, lapIndex), "file")
        }
        editor.putInt("${sessionId}_outing_${outingNumber}_lap_data_count", lapData.size)
        android.util.Log.d("TrackSessionActivity", "Confirmed ${lapData.size} lap files on disk")
    }
    private fun clearActiveSession() {
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("has_active_session", false)
            .putBoolean("active_session_has_lap", false)
            .remove("active_track_id")
            .remove("active_track_name")
            .remove("active_session_id")
            .remove("pending_session_id_raw")
            .apply()
    }

    private fun hasCompletedLap(): Boolean = lapTimes.isNotEmpty()
}

private object TrackSectorProgress {
    fun pathLengthMeters(points: List<GeoPoint>): Float {
        if (points.size < 2) return 0f
        val results = FloatArray(1)
        var total = 0f
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            Location.distanceBetween(
                start.latitude,
                start.longitude,
                end.latitude,
                end.longitude,
                results
            )
            total += results[0]
        }
        return total
    }

    fun projectProgress(
        waypoints: List<GeoPoint>,
        location: Location,
        closeLoop: Boolean = false
    ): Float? {
        if (waypoints.size < 2) return null

        val results = FloatArray(1)
        var bestAlong = 0f
        var bestDistance = Float.MAX_VALUE
        var cumulative = 0f

        val segmentCount = if (closeLoop && waypoints.size >= 2) {
            waypoints.size
        } else {
            waypoints.lastIndex
        }
        if (segmentCount < 1) return null

        for (index in 0 until segmentCount) {
            val start = waypoints[index]
            val end = waypoints[(index + 1) % waypoints.size]
            val segmentLength = distanceBetween(start, end, results)
            if (segmentLength <= 0.5f) {
                cumulative += segmentLength
                continue
            }

            val projection = projectPointOntoSegmentMeters(
                pointLat = location.latitude,
                pointLon = location.longitude,
                startLat = start.latitude,
                startLon = start.longitude,
                endLat = end.latitude,
                endLon = end.longitude,
                segmentLengthMeters = segmentLength,
                results = results
            )
            val along = cumulative + projection.coerceIn(0f, segmentLength)
            val distanceToSegment = results[0]
            if (distanceToSegment < bestDistance) {
                bestDistance = distanceToSegment
                bestAlong = along
            }
            cumulative += segmentLength
        }

        val totalLength = if (closeLoop) cumulative else pathLengthMeters(waypoints)
        if (totalLength <= 50f) return null
        return (bestAlong / totalLength).coerceIn(0f, 0.998f)
    }

    private fun distanceBetween(start: GeoPoint, end: GeoPoint, results: FloatArray): Float {
        Location.distanceBetween(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude,
            results
        )
        return results[0]
    }

    private fun projectPointOntoSegmentMeters(
        pointLat: Double,
        pointLon: Double,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        segmentLengthMeters: Float,
        results: FloatArray
    ): Float {
        val originLat = startLat
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = kotlin.math.max(111_320.0 * kotlin.math.cos(Math.toRadians(originLat)), 1e-6)

        fun toLocalX(lon: Double) = (lon - startLon) * metersPerDegLon
        fun toLocalY(lat: Double) = (lat - startLat) * metersPerDegLat

        val bx = toLocalX(endLon)
        val by = toLocalY(endLat)
        val px = toLocalX(pointLon)
        val py = toLocalY(pointLat)

        val dx = bx
        val dy = by
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq <= 1e-9) {
            0.0
        } else {
            (px * dx + py * dy) / lenSq
        }.coerceIn(0.0, 1.0)

        val projX = t * dx
        val projY = t * dy
        val distX = px - projX
        val distY = py - projY
        results[0] = kotlin.math.sqrt(distX * distX + distY * distY).toFloat()
        return (t * segmentLengthMeters).toFloat()
    }
}
