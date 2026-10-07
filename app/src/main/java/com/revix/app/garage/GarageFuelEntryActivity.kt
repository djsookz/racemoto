package com.revix.app.garage

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.revix.app.FullScreenImageActivity
import com.revix.app.R
import com.revix.app.applySystemBarsPaddingToRoot
import com.revix.app.data.GarageOdometerConflict
import com.revix.app.data.GarageOdometerSource
import com.revix.app.data.GarageOdometerTimeline
import com.revix.app.data.GarageFuelEntry
import com.revix.app.data.GarageFuelEntryStorage
import com.revix.app.data.GarageFuelReceiptStorage
import com.revix.app.data.SessionFuelStop
import com.revix.app.data.SessionFuelStorage
import com.revix.app.data.ProfileStorage
import com.revix.app.data.GarageReceiptImagePathCodec
import com.revix.app.settings.LanguageManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GarageFuelEntryActivity : AppCompatActivity() {

    private var profileId: Long = -1L
    private var editingEntryId: Long = -1L
    private var draftEntryId: Long = -1L
    private var editingEntry: GarageFuelEntry? = null
    private val originalReceiptImagePaths = mutableListOf<String>()
    private val currentReceiptImagePaths = mutableListOf<String>()
    private var lastOpenedReceiptPreviewIndex: Int = -1
    private var deleteArmedReceiptIndex: Int = -1
    private var pendingCameraImageUri: Uri? = null
    private var pendingCameraCaptureFile: File? = null
    private var shouldLaunchCameraAfterPermission: Boolean = false
    private val defaultStationOptions = listOf("OMV", "Shell", "Lukoil", "Petrol")
    private val defaultFuelTypeOptions = listOf("Diesel", "Diesel+", "Petrol 95", "Petrol 100", "LPG")
    private val stationOptions = mutableListOf<String>()
    private val fuelTypeOptions = mutableListOf<String>()
    private val temporaryReceiptImagePaths = mutableSetOf<String>()
    private var selectedStationOption: String? = null
    private var selectedFuelTypeOption: String? = null
    private var sessionKey: Long = 0L
    private var sessionLatitude: Double? = null
    private var sessionLongitude: Double? = null

    private lateinit var btnBack: View
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnReceiptImage: MaterialButton
    private lateinit var tvTitle: TextView
    private lateinit var tvFullTankHelper: TextView
    private lateinit var inputDate: TextInputLayout
    private lateinit var inputStation: TextInputLayout
    private lateinit var inputLitres: TextInputLayout
    private lateinit var inputPricePerLitre: TextInputLayout
    private lateinit var inputDiscountAmount: TextInputLayout
    private lateinit var inputOdometer: TextInputLayout
    private lateinit var llStationButtonRows: LinearLayout
    private lateinit var llFuelTypeButtonRows: LinearLayout
    private lateinit var etDate: TextInputEditText
    private lateinit var etStation: TextInputEditText
    private lateinit var etLitres: TextInputEditText
    private lateinit var etPricePerLitre: TextInputEditText
    private lateinit var etDiscountAmount: TextInputEditText
    private lateinit var etOdometer: TextInputEditText
    private lateinit var etNotes: TextInputEditText
    private lateinit var cardReceiptPreview: MaterialCardView
    private lateinit var llReceiptPreviewContainer: LinearLayout
    private lateinit var tvCalculatedTotalAmount: TextView
    private lateinit var switchFullTank: SwitchMaterial

    private val selectedDate = Calendar.getInstance()
    private val dateFormatter: SimpleDateFormat
        get() = GarageDateFormatter.createDateTimeFormatter(this)
    private val currencyFormatter by lazy {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            importReceiptImages(uris)
        }
    }
    private val cameraCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cameraUri = pendingCameraImageUri
        if (result.resultCode == RESULT_OK && cameraUri != null) {
            importReceiptImages(listOf(cameraUri), cleanupCapturedCameraFile = true)
        } else {
            cleanupPendingCameraCapture()
        }
    }
    private val receiptPreviewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val shouldDelete = result.data?.getBooleanExtra(FullScreenImageActivity.EXTRA_DELETE_REQUESTED, false) == true
        if (result.resultCode == RESULT_OK && shouldDelete) {
            val deletedIndex = result.data?.getIntExtra(
                FullScreenImageActivity.EXTRA_DELETED_INDEX,
                lastOpenedReceiptPreviewIndex
            ) ?: lastOpenedReceiptPreviewIndex
            removeReceiptImageAt(deletedIndex)
        }
        lastOpenedReceiptPreviewIndex = -1
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_garage_fuel_entry)
        applySystemBarsPaddingToRoot()

        profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        editingEntryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
        sessionKey = intent.getLongExtra(EXTRA_SESSION_KEY, 0L)
        if (intent.hasExtra(EXTRA_SESSION_LATITUDE)) {
            sessionLatitude = intent.getDoubleExtra(EXTRA_SESSION_LATITUDE, 0.0)
            sessionLongitude = intent.getDoubleExtra(EXTRA_SESSION_LONGITUDE, 0.0)
        }
        draftEntryId = if (editingEntryId != -1L) editingEntryId else System.currentTimeMillis()
        if (profileId == -1L) {
            finish()
            return
        }

        val profileExists = ProfileStorage.loadProfiles(this).any { it.id == profileId }
        if (!profileExists) {
            finish()
            return
        }

        bindViews()
        setupDateInput()
        setupStationSection()
        setupFuelTypeSection()
        setupCalculatedAmountInputs()
        setupClickListeners()
        prefillDefaults()
        prefillEntryForEditing()
        applySessionModeDefaults()
        updateReceiptPreview()
    }

    private fun applySessionModeDefaults() {
        if (sessionKey <= 0L || editingEntryId != -1L) return
        tvTitle.text = getString(R.string.garage_fuel_entry_session_title)
        selectedDate.timeInMillis = System.currentTimeMillis()
        etDate.setText(dateFormatter.format(selectedDate.time))
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBackFromFuelEntry)
        btnCancel = findViewById(R.id.btnCancelFuelEntry)
        btnSave = findViewById(R.id.btnSaveFuelEntry)
        btnReceiptImage = findViewById(R.id.btnFuelEntryReceiptImage)
        tvTitle = findViewById(R.id.tvFuelEntryTitle)
        tvFullTankHelper = findViewById(R.id.tvFuelEntryFullTankHelper)
        inputDate = findViewById(R.id.inputFuelEntryDate)
        inputStation = findViewById(R.id.inputFuelEntryStation)
        inputLitres = findViewById(R.id.inputFuelEntryLitres)
        inputPricePerLitre = findViewById(R.id.inputFuelEntryPricePerLitre)
        inputDiscountAmount = findViewById(R.id.inputFuelEntryDiscountAmount)
        inputOdometer = findViewById(R.id.inputFuelEntryOdometer)
        inputOdometer.suffixText = com.revix.app.settings.UnitsManager.getOdometerUnitSymbol(this)
        llStationButtonRows = findViewById(R.id.llFuelEntryStationButtonRows)
        llFuelTypeButtonRows = findViewById(R.id.llFuelEntryFuelTypeButtonRows)
        etDate = findViewById(R.id.etFuelEntryDate)
        etStation = findViewById(R.id.etFuelEntryStation)
        etLitres = findViewById(R.id.etFuelEntryLitres)
        etPricePerLitre = findViewById(R.id.etFuelEntryPricePerLitre)
        etDiscountAmount = findViewById(R.id.etFuelEntryDiscountAmount)
        etOdometer = findViewById(R.id.etFuelEntryOdometer)
        etNotes = findViewById(R.id.etFuelEntryNotes)
        cardReceiptPreview = findViewById(R.id.cardFuelEntryReceiptPreview)
        llReceiptPreviewContainer = findViewById(R.id.llFuelEntryReceiptPreviewContainer)
        tvCalculatedTotalAmount = findViewById(R.id.tvFuelEntryCalculatedTotalAmount)
        switchFullTank = findViewById(R.id.switchFuelEntryFullTank)
    }

    private fun setupDateInput() {
        etDate.setOnClickListener { showDateTimePicker() }
        inputDate.setEndIconOnClickListener { showDateTimePicker() }
    }

    private fun setupStationSection() {
        stationOptions.clear()
        stationOptions.addAll(
            loadSelectorOptions(
                defaultOptions = defaultStationOptions,
                prefsName = STATION_PREFS_NAME,
                fullListPrefsKey = STATION_OPTIONS_PREFS_KEY,
                legacyCustomPrefsKey = STATION_LEGACY_CUSTOM_PREFS_KEY
            )
        )
        etStation.addTextChangedListener {
            inputStation.error = null
        }
        renderStationButtons()
    }

    private fun setupFuelTypeSection() {
        fuelTypeOptions.clear()
        fuelTypeOptions.addAll(
            loadSelectorOptions(
                defaultOptions = defaultFuelTypeOptions,
                prefsName = FUEL_TYPE_PREFS_NAME,
                fullListPrefsKey = FUEL_TYPE_OPTIONS_PREFS_KEY,
                legacyCustomPrefsKey = FUEL_TYPE_LEGACY_CUSTOM_PREFS_KEY
            )
        )
        renderFuelTypeButtons()
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnCancel.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveFuelEntry() }
        btnReceiptImage.setOnClickListener { showReceiptImageSourceDialog() }
        switchFullTank.setOnCheckedChangeListener { _, _ -> updateFullTankHelperText() }
        etOdometer.addTextChangedListener(afterTextChanged = {
            val typedValue = it?.toString().orEmpty()
            inputOdometer.error = if (typedValue.isBlank()) {
                null
            } else {
                resolveOdometerSequenceError(readOdometerInputKm())
            }
            updateFullTankHelperText()
        })
    }

    // The odometer EditText holds the value in the user's selected unit (km or mi).
    // This converts it back to the internal kilometre base used by all storage/logic.
    private fun readOdometerInputKm(): Long? {
        val entered = parseWholeNumber(etOdometer.text?.toString()) ?: return null
        return com.revix.app.settings.UnitsManager.odometerValueToKm(entered, this)
    }

    private fun showReceiptImageSourceDialog() {
        val options = arrayOf(
            getString(R.string.garage_fuel_entry_receipt_source_camera),
            getString(R.string.garage_fuel_entry_receipt_source_gallery)
        )
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.garage_fuel_entry_receipt_source_title)
            .setAdapter(createWhiteTextDialogAdapter(options)) { _, which ->
                when (which) {
                    0 -> ensureCameraPermissionAndLaunch()
                    1 -> imagePickerLauncher.launch("image/*")
                }
            }
            .setNegativeButton(R.string.garage_fuel_entry_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
        }

        dialog.show()
    }

    private fun ensureCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchReceiptCamera()
            return
        }

        shouldLaunchCameraAfterPermission = true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
    }

    private fun setupCalculatedAmountInputs() {
        val amountWatcher: (CharSequence?) -> Unit = {
            inputLitres.error = null
            inputPricePerLitre.error = null
            inputDiscountAmount.error = null
            updateCalculatedTotalAmount()
        }

        etLitres.addTextChangedListener(afterTextChanged = amountWatcher)
        etPricePerLitre.addTextChangedListener(afterTextChanged = amountWatcher)
        etDiscountAmount.addTextChangedListener(afterTextChanged = amountWatcher)
        updateCalculatedTotalAmount()
    }

    private fun prefillDefaults() {
        etDate.setText(dateFormatter.format(selectedDate.time))
        val lastSelections = loadLastSelections()
        val defaultFuelType = fuelTypeOptions.firstOrNull {
            it.equals(getString(R.string.garage_fuel_entry_fuel_type_default), ignoreCase = true)
        } ?: fuelTypeOptions.firstOrNull() ?: getString(R.string.garage_fuel_entry_fuel_type_default)

        selectedStationOption = resolveStationSelection(lastSelections.stationText, lastSelections.stationLabel)
        if (lastSelections.stationText.isNotBlank()) {
            setStationFieldValue(lastSelections.stationText, requestFocus = false)
        }

        selectedFuelTypeOption = lastSelections.fuelTypeLabel
            ?.let { findExistingLabel(it, fuelTypeOptions) }
            ?: defaultFuelType

        renderStationButtons()
        renderFuelTypeButtons()
        updateCalculatedTotalAmount()
        updateFullTankHelperText()
    }

    private fun prefillEntryForEditing() {
        if (editingEntryId == -1L) {
            return
        }

        val entry = GarageFuelEntryStorage.findEntry(this, profileId, editingEntryId)
        if (entry == null) {
            Toast.makeText(this, getString(R.string.garage_fuel_entry_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        editingEntry = entry
        tvTitle.text = getString(R.string.garage_fuel_entry_edit_title)

        selectedStationOption = resolveStationSelection(entry.station, null)
        selectedFuelTypeOption = ensureSelectorOptionExists(
            label = entry.fuelType,
            options = fuelTypeOptions,
            prefsName = FUEL_TYPE_PREFS_NAME,
            prefsKey = FUEL_TYPE_OPTIONS_PREFS_KEY
        )

        etDate.setText(GarageDateFormatter.formatDateTime(this, entry.date, entry.createdAt))
        setStationFieldValue(entry.station, requestFocus = false)
        etLitres.setText(formatEditableDecimal(entry.litres))
        etPricePerLitre.setText(formatEditableDecimal(entry.pricePerLitre))
        etDiscountAmount.setText(formatEditableDecimal(entry.discountAmount))
        etOdometer.setText(com.revix.app.settings.UnitsManager.kmToOdometerValue(entry.odometerKm, this).toString())
        etNotes.setText(entry.notes)
        switchFullTank.isChecked = entry.isFullTank
        val decodedReceiptPaths = GarageReceiptImagePathCodec.decode(entry.receiptImagePath)
        originalReceiptImagePaths.clear()
        originalReceiptImagePaths.addAll(decodedReceiptPaths)
        currentReceiptImagePaths.clear()
        currentReceiptImagePaths.addAll(decodedReceiptPaths)

        selectedDate.time = Date(GarageDateFormatter.resolveTimestamp(this, entry.date, entry.createdAt))

        renderStationButtons()
        renderFuelTypeButtons()
        updateCalculatedTotalAmount()
        updateFullTankHelperText()
    }

    private fun launchReceiptCamera() {
        cleanupPendingCameraCapture()

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) || cameraIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, getString(R.string.garage_fuel_entry_receipt_camera_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val captureFile = createReceiptCameraCaptureFile()
        if (captureFile == null) {
            Toast.makeText(this, getString(R.string.garage_fuel_entry_receipt_camera_error), Toast.LENGTH_SHORT).show()
            return
        }

        val captureUri = runCatching {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", captureFile)
        }.getOrNull()

        if (captureUri == null) {
            captureFile.delete()
            Toast.makeText(this, getString(R.string.garage_fuel_entry_receipt_camera_error), Toast.LENGTH_SHORT).show()
            return
        }

        pendingCameraCaptureFile = captureFile
        pendingCameraImageUri = captureUri

        val captureIntent = cameraIntent.apply {
            putExtra(MediaStore.EXTRA_OUTPUT, captureUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        packageManager.queryIntentActivities(captureIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { resolveInfo ->
                grantUriPermission(
                    resolveInfo.activityInfo.packageName,
                    captureUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        runCatching {
            cameraCaptureLauncher.launch(captureIntent)
        }.onFailure {
            cleanupPendingCameraCapture()
            Toast.makeText(this, getString(R.string.garage_fuel_entry_receipt_camera_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createReceiptCameraCaptureFile(): File? {
        return runCatching {
            val picturesDir = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                RECEIPT_CAMERA_TEMP_DIR
            )
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }
            File.createTempFile(
                "fuel_receipt_${profileId}_${draftEntryId}_",
                ".jpg",
                picturesDir
            )
        }.getOrNull()
    }

    private fun importReceiptImages(uris: List<Uri>, cleanupCapturedCameraFile: Boolean = false) {
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) {
            if (cleanupCapturedCameraFile) {
                cleanupPendingCameraCapture()
            }
            return
        }

        lifecycleScope.launch {
            val importedPaths = withContext(Dispatchers.IO) {
                uniqueUris.mapNotNull { uri ->
                    GarageFuelReceiptStorage.saveTempReceipt(
                        context = this@GarageFuelEntryActivity,
                        uri = uri,
                        profileId = profileId,
                        entryId = draftEntryId
                    )
                }
            }

            if (cleanupCapturedCameraFile) {
                cleanupPendingCameraCapture()
            }

            if (importedPaths.isEmpty()) {
                Toast.makeText(
                    this@GarageFuelEntryActivity,
                    getString(R.string.garage_fuel_entry_receipt_error),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            importedPaths.forEach { path ->
                temporaryReceiptImagePaths.add(path)
                if (!currentReceiptImagePaths.contains(path)) {
                    currentReceiptImagePaths.add(path)
                }
            }
            deleteArmedReceiptIndex = -1
            updateReceiptPreview()

            Toast.makeText(
                this@GarageFuelEntryActivity,
                getString(R.string.garage_fuel_entry_receipt_added),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun cleanupPendingCameraCapture() {
        pendingCameraImageUri?.let { uri ->
            revokeUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        pendingCameraCaptureFile?.takeIf { it.exists() }?.delete()
        pendingCameraCaptureFile = null
        pendingCameraImageUri = null
        shouldLaunchCameraAfterPermission = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_CAMERA_PERMISSION) {
            return
        }

        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted && shouldLaunchCameraAfterPermission) {
            shouldLaunchCameraAfterPermission = false
            launchReceiptCamera()
            return
        }

        shouldLaunchCameraAfterPermission = false
        Toast.makeText(this, getString(R.string.garage_fuel_entry_receipt_camera_permission_denied), Toast.LENGTH_SHORT).show()
    }

    private fun openReceiptPreview(startIndex: Int) {
        if (startIndex !in currentReceiptImagePaths.indices) {
            return
        }

        val absolutePaths = currentReceiptImagePaths.mapNotNull { relativePath ->
            GarageFuelReceiptStorage.resolveReceiptFile(this, relativePath)
                ?.takeIf { it.exists() }
                ?.absolutePath
        }
        if (absolutePaths.isEmpty()) {
            return
        }

        val safeIndex = startIndex.coerceIn(0, absolutePaths.lastIndex)
        lastOpenedReceiptPreviewIndex = safeIndex

        val intent = Intent(this, FullScreenImageActivity::class.java).apply {
            putStringArrayListExtra(
                FullScreenImageActivity.EXTRA_PHOTO_PATHS,
                ArrayList(absolutePaths)
            )
            putExtra(FullScreenImageActivity.EXTRA_CURRENT_INDEX, safeIndex)
            putExtra(FullScreenImageActivity.EXTRA_SHOW_DELETE, true)
        }
        receiptPreviewLauncher.launch(intent)
    }

    private fun removeReceiptImageAt(index: Int) {
        if (index !in currentReceiptImagePaths.indices) {
            return
        }

        val removedPath = currentReceiptImagePaths.removeAt(index)
        if (!originalReceiptImagePaths.contains(removedPath)) {
            deleteTemporaryReceiptImage(removedPath)
        }
        deleteArmedReceiptIndex = when {
            deleteArmedReceiptIndex == index -> -1
            deleteArmedReceiptIndex > index -> deleteArmedReceiptIndex - 1
            else -> deleteArmedReceiptIndex
        }

        updateReceiptPreview()

        Toast.makeText(
            this,
            getString(R.string.garage_fuel_entry_receipt_deleted),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateReceiptPreview() {
        originalReceiptImagePaths.retainAll { relativePath ->
            GarageFuelReceiptStorage.resolveReceiptFile(this, relativePath)?.exists() == true
        }
        currentReceiptImagePaths.retainAll { relativePath ->
            val exists = GarageFuelReceiptStorage.resolveReceiptFile(this, relativePath)?.exists() == true
            if (!exists) {
                temporaryReceiptImagePaths.remove(relativePath)
            }
            exists
        }
        if (deleteArmedReceiptIndex !in currentReceiptImagePaths.indices) {
            deleteArmedReceiptIndex = -1
        }
        val hasImages = currentReceiptImagePaths.isNotEmpty()

        if (!hasImages) {
            deleteArmedReceiptIndex = -1
            cardReceiptPreview.visibility = View.GONE
            llReceiptPreviewContainer.removeAllViews()
            btnReceiptImage.text = getString(R.string.garage_fuel_entry_receipt_add)
            return
        }

        renderReceiptThumbnails()
        cardReceiptPreview.visibility = View.VISIBLE
        btnReceiptImage.text = getString(R.string.garage_fuel_entry_receipt_add)
    }

    private fun renderReceiptThumbnails() {
        llReceiptPreviewContainer.removeAllViews()
        val itemCount = currentReceiptImagePaths.size
        if (itemCount == 0) {
            return
        }

        val thumbnailWidthDp = when {
            itemCount <= 1 -> 164
            itemCount == 2 -> 136
            itemCount == 3 -> 118
            itemCount == 4 -> 102
            itemCount <= 6 -> 92
            else -> 84
        }
        val thumbnailWidthPx = dpToPx(thumbnailWidthDp)
        val thumbnailHeightPx = (thumbnailWidthPx * 0.72f).toInt().coerceAtLeast(dpToPx(56))

        currentReceiptImagePaths.forEachIndexed { index, relativePath ->
            val receiptFile = GarageFuelReceiptStorage.resolveReceiptFile(this, relativePath)
                ?.takeIf { it.exists() }
                ?: return@forEachIndexed

            val thumbnailCard = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(thumbnailWidthPx, thumbnailHeightPx).apply {
                    marginEnd = if (index == itemCount - 1) 0 else dpToPx(8)
                }
                radius = dpToPx(12).toFloat()
                strokeWidth = dpToPx(1)
                strokeColor = ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.stroke_dark)
                cardElevation = 0f
                setCardBackgroundColor(ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.dark_surface))
                addView(
                    buildReceiptThumbnailView(
                        filePath = receiptFile.absolutePath,
                        index = index,
                        totalCount = itemCount,
                        showDeleteBadge = deleteArmedReceiptIndex == index,
                        onDeleteRequested = { removeReceiptImageAt(index) }
                    )
                )
                setOnClickListener { openReceiptPreview(index) }
                setOnLongClickListener {
                    deleteArmedReceiptIndex = if (deleteArmedReceiptIndex == index) -1 else index
                    renderReceiptThumbnails()
                    true
                }
            }

            llReceiptPreviewContainer.addView(thumbnailCard)
        }
    }

    private fun buildReceiptThumbnailView(
        filePath: String,
        index: Int,
        totalCount: Int,
        showDeleteBadge: Boolean,
        onDeleteRequested: () -> Unit
    ): View {
        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            addView(ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(BitmapFactory.decodeFile(filePath))
            })

            addView(TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
                    marginStart = dpToPx(8)
                    bottomMargin = dpToPx(8)
                }
                setBackgroundColor(ContextCompat.getColor(context, R.color.background_transparent_80))
                setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
                text = "${index + 1}/$totalCount"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 11f
            })

            addView(ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                    topMargin = dpToPx(6)
                    marginEnd = dpToPx(6)
                }
                background = ContextCompat.getDrawable(context, R.drawable.bg_circle_trash)
                setImageResource(R.drawable.ic_close)
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                visibility = if (showDeleteBadge) View.VISIBLE else View.GONE
                setOnClickListener {
                    onDeleteRequested()
                }
            })
        }
    }

    private fun resolveReceiptImagesForSave(entryId: Long): List<String>? {
        val normalizedOriginalPaths = GarageReceiptImagePathCodec.normalize(originalReceiptImagePaths)
        val normalizedCurrentPaths = GarageReceiptImagePathCodec.normalize(currentReceiptImagePaths)

        if (normalizedCurrentPaths.isEmpty()) {
            normalizedOriginalPaths.forEach { originalPath ->
                GarageFuelReceiptStorage.deleteReceipt(this, originalPath)
            }
            return emptyList()
        }

        val originalPathSet = normalizedOriginalPaths.toSet()
        val promotedPaths = mutableListOf<String>()

        for (path in normalizedCurrentPaths) {
            if (path in originalPathSet) {
                promotedPaths.add(path)
                continue
            }

            val promotedPath = GarageFuelReceiptStorage.promoteTempReceipt(this, path, profileId, entryId)
                ?: return null

            temporaryReceiptImagePaths.remove(path)
            promotedPaths.add(promotedPath)
        }

        val promotedPathSet = promotedPaths.toSet()
        normalizedOriginalPaths
            .filter { originalPath -> originalPath !in promotedPathSet }
            .forEach { removedOriginalPath ->
                GarageFuelReceiptStorage.deleteReceipt(this, removedOriginalPath)
            }

        return GarageReceiptImagePathCodec.normalize(promotedPaths)
    }

    private fun deleteTemporaryReceiptImage(relativePath: String) {
        temporaryReceiptImagePaths.remove(relativePath)
        GarageFuelReceiptStorage.deleteReceipt(this, relativePath)
    }

    private fun renderStationButtons() {
        val stationItems = buildList {
            stationOptions.forEach { add(SelectorButtonItem(label = it, isOther = false)) }
            add(SelectorButtonItem(label = getString(R.string.garage_fuel_entry_station_other), isOther = true))
        }

        renderSelectorButtons(llStationButtonRows, stationItems, MAX_STATION_BUTTONS_PER_ROW, ::createStationButton)
    }

    private fun renderFuelTypeButtons() {
        val fuelTypeItems = buildList {
            fuelTypeOptions.forEach { add(SelectorButtonItem(label = it, isOther = false)) }
            add(SelectorButtonItem(label = getString(R.string.garage_fuel_entry_station_other), isOther = true))
        }

        renderSelectorButtons(llFuelTypeButtonRows, fuelTypeItems, MAX_FUEL_TYPE_BUTTONS_PER_ROW, ::createFuelTypeButton)
    }

    private fun renderSelectorButtons(
        container: LinearLayout,
        items: List<SelectorButtonItem>,
        maxButtonsPerRow: Int,
        createButton: (SelectorButtonItem) -> MaterialButton
    ) {
        container.removeAllViews()

        items.chunked(maxButtonsPerRow).forEach { rowItems ->
            val rowLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (container.childCount == 0) 0 else dpToPx(8)
                }
                orientation = LinearLayout.HORIZONTAL
                weightSum = maxButtonsPerRow.toFloat()
            }

            rowItems.forEachIndexed { index, item ->
                val button = createButton(item)
                rowLayout.addView(button)
                if (index < maxButtonsPerRow - 1) {
                    button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                        marginEnd = dpToPx(8)
                    }
                }
            }

            repeat(maxButtonsPerRow - rowItems.size) { emptyIndex ->
                rowLayout.addView(
                    Space(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            0,
                            1f
                        ).apply {
                            if (rowItems.size + emptyIndex < maxButtonsPerRow - 1) {
                                marginEnd = dpToPx(8)
                            }
                        }
                    }
                )
            }

            container.addView(rowLayout)
        }
    }

    private fun createSelectorButtonBase(label: String): MaterialButton {
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(44), 1f)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dpToPx(12)
            strokeWidth = dpToPx(1)
            iconPadding = dpToPx(4)
            iconSize = dpToPx(12)
            isAllCaps = false
            setPaddingRelative(dpToPx(6), 0, dpToPx(6), 0)
            text = label
            textSize = 13f
            setSingleLine(true)
        }
    }

    private fun createStationButton(item: SelectorButtonItem): MaterialButton {
        return createSelectorButtonBase(item.label).apply {

            if (item.isOther) {
                setIconResource(R.drawable.ic_add)
                iconTint = ColorStateList.valueOf(ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.accent_color))
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.dark_surface))
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.stroke_dark))
                setTextColor(ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.text_secondary))
                setOnClickListener { showAddCustomStationDialog() }
            } else {
                setIconResource(R.drawable.gas_station)
                val isSelected = item.label == selectedStationOption
                applySelectorButtonStyle(this, isSelected)
                setOnClickListener {
                    selectedStationOption = item.label
                    setStationInput(item.label)
                    renderStationButtons()
                }
                setOnLongClickListener {
                    showStationItemOptions(item.label)
                    true
                }
            }
        }
    }

    private fun createFuelTypeButton(item: SelectorButtonItem): MaterialButton {
        return createSelectorButtonBase(item.label).apply {

            if (item.isOther) {
                setIconResource(R.drawable.ic_add)
                iconTint = ColorStateList.valueOf(ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.accent_color))
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.dark_surface))
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.stroke_dark))
                setTextColor(ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.text_secondary))
                setOnClickListener { showAddCustomFuelTypeDialog() }
            } else {
                setIconResource(R.drawable.noun_fuel_nozzle)
                val isSelected = item.label == selectedFuelTypeOption
                applySelectorButtonStyle(this, isSelected)
                setOnClickListener {
                    selectedFuelTypeOption = item.label
                    renderFuelTypeButtons()
                }
                setOnLongClickListener {
                    showFuelTypeItemOptions(item.label)
                    true
                }
            }
        }
    }

    private fun applySelectorButtonStyle(button: MaterialButton, isSelected: Boolean) {
        val accentColor = ContextCompat.getColor(this, R.color.accent_color)
        val defaultBackground = ContextCompat.getColor(this, R.color.dark_surface)
        val selectedBackground = ColorUtils.setAlphaComponent(accentColor, 40)
        val stroke = if (isSelected) accentColor else ContextCompat.getColor(this, R.color.stroke_dark)
        val textColor = if (isSelected) accentColor else ContextCompat.getColor(this, R.color.text_secondary)

        button.backgroundTintList = ColorStateList.valueOf(if (isSelected) selectedBackground else defaultBackground)
        button.strokeColor = ColorStateList.valueOf(stroke)
        button.setTextColor(textColor)
        button.iconTint = ColorStateList.valueOf(textColor)
    }

    private fun showAddCustomStationDialog() {
        showOptionInputDialog(
            titleRes = R.string.garage_fuel_entry_station_other_dialog_title,
            positiveButtonRes = R.string.garage_fuel_entry_station_other_dialog_add,
            hintRes = R.string.garage_fuel_entry_station_other_dialog_hint,
            emptyErrorRes = R.string.garage_fuel_entry_station_other_dialog_error
        ) { customStation, _ ->
            val existingLabel = findExistingLabel(customStation, stationOptions)
            if (existingLabel == null) {
                stationOptions.add(customStation)
                persistSelectorOptions(stationOptions, STATION_PREFS_NAME, STATION_OPTIONS_PREFS_KEY)
                selectedStationOption = customStation
            } else {
                selectedStationOption = existingLabel
            }

            selectedStationOption?.let(::setStationInput)
            renderStationButtons()
            true
        }
    }

    private fun showAddCustomFuelTypeDialog() {
        showOptionInputDialog(
            titleRes = R.string.garage_fuel_entry_fuel_type_other_dialog_title,
            positiveButtonRes = R.string.garage_fuel_entry_station_other_dialog_add,
            hintRes = R.string.garage_fuel_entry_fuel_type_other_dialog_hint,
            emptyErrorRes = R.string.garage_fuel_entry_fuel_type_other_dialog_error
        ) { customFuelType, _ ->
            val existingLabel = findExistingLabel(customFuelType, fuelTypeOptions)
            if (existingLabel == null) {
                fuelTypeOptions.add(customFuelType)
                persistSelectorOptions(fuelTypeOptions, FUEL_TYPE_PREFS_NAME, FUEL_TYPE_OPTIONS_PREFS_KEY)
                selectedFuelTypeOption = customFuelType
            } else {
                selectedFuelTypeOption = existingLabel
            }

            renderFuelTypeButtons()
            true
        }
    }

    private fun showOptionInputDialog(
        titleRes: Int,
        positiveButtonRes: Int,
        hintRes: Int,
        emptyErrorRes: Int,
        initialValue: String = "",
        onConfirm: (String, TextInputLayout) -> Boolean
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_fuel_station, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputDialogCustomStation)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.etDialogCustomStation)
        val hint = getString(hintRes)

        inputLayout.hint = hint
        editText.hint = hint
        editText.setText(initialValue)
        if (initialValue.isNotEmpty()) {
            editText.setSelection(initialValue.length)
        }

        editText.addTextChangedListener {
            inputLayout.error = null
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(titleRes)
            .setView(dialogView)
            .setPositiveButton(positiveButtonRes, null)
            .setNegativeButton(R.string.garage_fuel_entry_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val customOption = editText.text?.toString()?.trim().orEmpty()
                if (customOption.isBlank()) {
                    inputLayout.error = getString(emptyErrorRes)
                    return@setOnClickListener
                }

                if (onConfirm(customOption, inputLayout)) {
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showStationItemOptions(stationLabel: String) {
        showSelectorItemOptions(
            title = stationLabel,
            onEdit = { showEditStationDialog(stationLabel) },
            onDelete = { deleteStationOption(stationLabel) }
        )
    }

    private fun showFuelTypeItemOptions(fuelTypeLabel: String) {
        showSelectorItemOptions(
            title = fuelTypeLabel,
            onEdit = { showEditFuelTypeDialog(fuelTypeLabel) },
            onDelete = { deleteFuelTypeOption(fuelTypeLabel) }
        )
    }

    private fun showEditStationDialog(currentLabel: String) {
        showOptionInputDialog(
            titleRes = R.string.garage_fuel_entry_station_edit_dialog_title,
            positiveButtonRes = R.string.garage_fuel_entry_save,
            hintRes = R.string.garage_fuel_entry_station_other_dialog_hint,
            emptyErrorRes = R.string.garage_fuel_entry_station_other_dialog_error,
            initialValue = currentLabel
        ) { updatedLabel, inputLayout ->
            if (hasConflictingLabel(updatedLabel, currentLabel, stationOptions)) {
                inputLayout.error = getString(R.string.garage_fuel_entry_station_duplicate_error)
                return@showOptionInputDialog false
            }

            if (renameSelectorOption(currentLabel, updatedLabel, stationOptions, STATION_PREFS_NAME, STATION_OPTIONS_PREFS_KEY)) {
                if (selectedStationOption.equals(currentLabel, ignoreCase = true)) {
                    selectedStationOption = updatedLabel
                    setStationFieldValue(replaceLeadingLabel(etStation.text?.toString().orEmpty(), currentLabel, updatedLabel))
                }
                renderStationButtons()
                Toast.makeText(this, getString(R.string.garage_fuel_entry_station_renamed), Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun showEditFuelTypeDialog(currentLabel: String) {
        showOptionInputDialog(
            titleRes = R.string.garage_fuel_entry_fuel_type_edit_dialog_title,
            positiveButtonRes = R.string.garage_fuel_entry_save,
            hintRes = R.string.garage_fuel_entry_fuel_type_other_dialog_hint,
            emptyErrorRes = R.string.garage_fuel_entry_fuel_type_other_dialog_error,
            initialValue = currentLabel
        ) { updatedLabel, inputLayout ->
            if (hasConflictingLabel(updatedLabel, currentLabel, fuelTypeOptions)) {
                inputLayout.error = getString(R.string.garage_fuel_entry_fuel_type_duplicate_error)
                return@showOptionInputDialog false
            }

            if (renameSelectorOption(currentLabel, updatedLabel, fuelTypeOptions, FUEL_TYPE_PREFS_NAME, FUEL_TYPE_OPTIONS_PREFS_KEY)) {
                if (selectedFuelTypeOption.equals(currentLabel, ignoreCase = true)) {
                    selectedFuelTypeOption = updatedLabel
                }
                renderFuelTypeButtons()
                Toast.makeText(this, getString(R.string.garage_fuel_entry_fuel_type_renamed), Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun showSelectorItemOptions(title: String, onEdit: () -> Unit, onDelete: () -> Unit) {
        val options = arrayOf(
            getString(R.string.garage_fuel_entry_selector_edit_option),
            getString(R.string.garage_fuel_entry_station_delete_option)
        )
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(title)
            .setAdapter(createWhiteTextDialogAdapter(options)) { _, which ->
                when (which) {
                    0 -> onEdit()
                    1 -> onDelete()
                }
            }
            .setNegativeButton(R.string.garage_fuel_entry_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
        }

        dialog.show()
    }

    private fun createWhiteTextDialogAdapter(options: Array<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            options
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.setTextColor(ContextCompat.getColor(this@GarageFuelEntryActivity, R.color.white))
                return view
            }
        }
    }

    private fun deleteStationOption(stationLabel: String) {
        if (!deleteSelectorOption(stationLabel, stationOptions, STATION_PREFS_NAME, STATION_OPTIONS_PREFS_KEY)) {
            return
        }

        if (selectedStationOption.equals(stationLabel, ignoreCase = true)) {
            selectedStationOption = null
        }

        renderStationButtons()
        Toast.makeText(this, getString(R.string.garage_fuel_entry_station_deleted), Toast.LENGTH_SHORT).show()
    }

    private fun deleteFuelTypeOption(fuelTypeLabel: String) {
        if (!deleteSelectorOption(fuelTypeLabel, fuelTypeOptions, FUEL_TYPE_PREFS_NAME, FUEL_TYPE_OPTIONS_PREFS_KEY)) {
            return
        }

        if (selectedFuelTypeOption.equals(fuelTypeLabel, ignoreCase = true)) {
            selectedFuelTypeOption = fuelTypeOptions.firstOrNull()
                ?: getString(R.string.garage_fuel_entry_fuel_type_default)
        }

        renderFuelTypeButtons()
        Toast.makeText(this, getString(R.string.garage_fuel_entry_fuel_type_deleted), Toast.LENGTH_SHORT).show()
    }

    private fun hasConflictingLabel(candidate: String, currentLabel: String, options: List<String>): Boolean {
        return options.any {
            !it.equals(currentLabel, ignoreCase = true) && it.equals(candidate, ignoreCase = true)
        }
    }

    private fun renameSelectorOption(
        currentLabel: String,
        updatedLabel: String,
        options: MutableList<String>,
        prefsName: String,
        prefsKey: String
    ): Boolean {
        val itemIndex = options.indexOfFirst { it.equals(currentLabel, ignoreCase = true) }
        if (itemIndex == -1) {
            return false
        }

        options[itemIndex] = updatedLabel.trim()
        persistSelectorOptions(options, prefsName, prefsKey)
        return true
    }

    private fun deleteSelectorOption(
        label: String,
        options: MutableList<String>,
        prefsName: String,
        prefsKey: String
    ): Boolean {
        val removed = options.removeAll { it.equals(label, ignoreCase = true) }
        if (removed) {
            persistSelectorOptions(options, prefsName, prefsKey)
        }
        return removed
    }

    private fun findExistingLabel(candidate: String, options: List<String>): String? {
        return options
            .firstOrNull { it.equals(candidate, ignoreCase = true) }
    }

    private fun ensureSelectorOptionExists(
        label: String,
        options: MutableList<String>,
        prefsName: String,
        prefsKey: String
    ): String {
        val normalizedLabel = label.trim()
        val existingLabel = findExistingLabel(normalizedLabel, options)
        if (existingLabel != null) {
            return existingLabel
        }

        if (normalizedLabel.isNotEmpty()) {
            options.add(normalizedLabel)
            persistSelectorOptions(options, prefsName, prefsKey)
        }

        return normalizedLabel
    }

    private fun loadSelectorOptions(
        defaultOptions: List<String>,
        prefsName: String,
        fullListPrefsKey: String,
        legacyCustomPrefsKey: String
    ): List<String> {
        val persistedOptions = loadPersistedOptionsOrNull(prefsName, fullListPrefsKey)
        if (persistedOptions != null) {
            return normalizeOptions(persistedOptions)
        }

        val legacyCustomOptions = loadPersistedOptionsOrNull(prefsName, legacyCustomPrefsKey).orEmpty()
        val seededOptions = normalizeOptions(defaultOptions + legacyCustomOptions)
        persistSelectorOptions(seededOptions, prefsName, fullListPrefsKey)
        return seededOptions
    }

    private fun loadPersistedOptionsOrNull(prefsName: String, prefsKey: String): List<String>? {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (!prefs.contains(prefsKey)) {
            return null
        }

        val json = prefs.getString(prefsKey, null)
        if (json.isNullOrBlank()) {
            return emptyList()
        }

        val type = object : TypeToken<List<String?>>() {}.type
        return Gson().fromJson<List<String?>>(json, type)
            ?.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()
    }

    private fun persistSelectorOptions(options: List<String>, prefsName: String, prefsKey: String) {
        val normalizedOptions = normalizeOptions(options)

        getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(prefsKey, Gson().toJson(normalizedOptions))
            .apply()
    }

    private fun persistLastSelections(stationText: String, stationLabel: String?, fuelTypeLabel: String) {
        val prefs = getSharedPreferences(LAST_SELECTION_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (stationText.isNotBlank()) {
                putString(lastSelectionKey(LAST_STATION_TEXT_SUFFIX), stationText)
                putString(lastSelectionKey(LAST_STATION_LABEL_SUFFIX), stationLabel)
            }
            putString(lastSelectionKey(LAST_FUEL_TYPE_LABEL_SUFFIX), fuelTypeLabel)
            apply()
        }
    }

    private fun loadLastSelections(): LastSelections {
        val prefs = getSharedPreferences(LAST_SELECTION_PREFS_NAME, Context.MODE_PRIVATE)
        return LastSelections(
            stationText = prefs.getString(lastSelectionKey(LAST_STATION_TEXT_SUFFIX), null).orEmpty(),
            stationLabel = prefs.getString(lastSelectionKey(LAST_STATION_LABEL_SUFFIX), null),
            fuelTypeLabel = prefs.getString(lastSelectionKey(LAST_FUEL_TYPE_LABEL_SUFFIX), null)
        )
    }

    private fun lastSelectionKey(suffix: String): String = "profile_${profileId}_$suffix"

    private fun normalizeOptions(options: List<String>): List<String> {
        return options
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
    }

    private fun resolveStationSelection(stationText: String, fallbackLabel: String?): String? {
        val normalizedText = stationText.trim()
        val matchedFromText = if (normalizedText.isBlank()) {
            null
        } else {
            stationOptions
                .sortedByDescending { it.length }
                .firstOrNull { option -> normalizedText.startsWith(option, ignoreCase = true) }
        }

        return matchedFromText ?: fallbackLabel?.let { findExistingLabel(it, stationOptions) }
    }

    private fun setStationInput(label: String) {
        etStation.setText(if (label.isBlank()) "" else "$label ")
        etStation.setSelection(etStation.text?.length ?: 0)
        etStation.requestFocus()
    }

    private fun setStationFieldValue(value: String, requestFocus: Boolean = true) {
        etStation.setText(value)
        etStation.setSelection(etStation.text?.length ?: 0)
        if (requestFocus) {
            etStation.requestFocus()
        }
    }

    private fun replaceLeadingLabel(currentValue: String, oldLabel: String, newLabel: String): String {
        return if (currentValue.regionMatches(0, oldLabel, 0, oldLabel.length, ignoreCase = true)) {
            newLabel + currentValue.substring(oldLabel.length)
        } else {
            "$newLabel "
        }
    }

    private fun updateFullTankHelperText() {
        val currentOdometerKm = readOdometerInputKm()
        val previousFullTankEntry = findPreviousFullTankEntry(currentOdometerKm)

        tvFullTankHelper.text = when {
            switchFullTank.isChecked && currentOdometerKm != null && currentOdometerKm > 0L && previousFullTankEntry != null -> {
                getString(
                    R.string.garage_fuel_entry_full_tank_helper_close_period,
                    formatHelperOdometer(previousFullTankEntry.odometerKm),
                    formatHelperOdometer(currentOdometerKm)
                )
            }

            switchFullTank.isChecked && previousFullTankEntry == null -> {
                getString(R.string.garage_fuel_entry_full_tank_helper_start)
            }

            switchFullTank.isChecked -> {
                getString(R.string.garage_fuel_entry_full_tank_helper_default)
            }

            previousFullTankEntry != null -> {
                getString(R.string.garage_fuel_entry_partial_helper_active_period)
            }

            else -> {
                getString(R.string.garage_fuel_entry_partial_helper_no_period)
            }
        }
    }

    private fun findPreviousFullTankEntry(currentOdometerKm: Long?): GarageFuelEntry? {
        val comparisonOdometerKm = currentOdometerKm ?: Long.MAX_VALUE

        return GarageFuelEntryStorage.loadEntries(this, profileId)
            .asSequence()
            .filter { it.id != editingEntryId }
            .filter { it.isFullTank && it.odometerKm > 0L && it.odometerKm < comparisonOdometerKm }
            .maxByOrNull { it.odometerKm }
    }

    private fun formatHelperOdometer(valueKm: Long): String {
        val um = com.revix.app.settings.UnitsManager
        return "${um.formatOdometerValue(valueKm, this)} ${um.getOdometerUnitSymbol(this)}"
    }

    private fun resolveOdometerSequenceError(odometerKm: Long?): String? {
        val conflict = GarageOdometerTimeline.resolveConflict(
            context = this,
            profileId = profileId,
            source = GarageOdometerSource.FUEL,
            entryId = editingEntry?.id ?: draftEntryId,
            odometerKm = odometerKm,
            dateText = etDate.text?.toString(),
            fallbackTimestamp = editingEntry?.createdAt ?: selectedDate.timeInMillis
        ) ?: return null

        return when (conflict.type) {
            GarageOdometerConflict.Type.PREVIOUS -> getString(
                R.string.garage_fuel_entry_error_odometer_previous,
                formatDisplayOdometer(conflict.referenceOdometerKm)
            )

            GarageOdometerConflict.Type.NEXT -> getString(
                R.string.garage_fuel_entry_error_odometer_next,
                formatDisplayOdometer(conflict.referenceOdometerKm)
            )
        }
    }

    private fun formatDisplayOdometer(valueKm: Long): String {
        val um = com.revix.app.settings.UnitsManager
        return "${um.formatOdometerValue(valueKm, this)} ${um.getOdometerUnitSymbol(this)}"
    }

    private fun showDateTimePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(Calendar.YEAR, year)
                selectedDate.set(Calendar.MONTH, month)
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                showTimePicker()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedDate.set(Calendar.MINUTE, minute)
                selectedDate.set(Calendar.SECOND, 0)
                selectedDate.set(Calendar.MILLISECOND, 0)
                etDate.setText(dateFormatter.format(selectedDate.time))
                inputDate.error = null
                inputOdometer.error = resolveOdometerSequenceError(parseWholeNumber(etOdometer.text?.toString()))
            },
            selectedDate.get(Calendar.HOUR_OF_DAY),
            selectedDate.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateCalculatedTotalAmount() {
        tvCalculatedTotalAmount.text = formatCurrency(getCalculatedTotalAmount())
    }

    private fun getCalculatedTotalAmount(): Double? {
        val litres = parseDecimal(etLitres.text?.toString())
        val pricePerLitre = parseDecimal(etPricePerLitre.text?.toString())
        val discountAmount = parseDecimal(etDiscountAmount.text?.toString()) ?: 0.0

        if (litres == null || litres <= 0.0 || pricePerLitre == null || pricePerLitre <= 0.0) {
            return null
        }

        val grossAmount = litres * pricePerLitre
        if (discountAmount < 0.0 || discountAmount > grossAmount) {
            return null
        }

        return (grossAmount - discountAmount).coerceAtLeast(0.0)
    }

    private fun formatCurrency(value: Double?): String {
        if (value == null) {
            return getString(R.string.garage_fuel_entry_total_amount_placeholder)
        }

        return getString(
            R.string.garage_fuel_entry_total_amount_format,
            currencyFormatter.format(value)
        )
    }

    private fun formatEditableDecimal(value: Double): String {
        if (value == 0.0) {
            return ""
        }

        val formatted = String.format(Locale.getDefault(), "%.2f", value)
        val decimalSeparator = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
        return formatted
            .trimEnd('0')
            .trimEnd(decimalSeparator)
    }

    private fun saveFuelEntry() {
        clearErrors()

        val existingEntry = editingEntry
        val entryId = existingEntry?.id ?: draftEntryId
        val date = etDate.text?.toString()?.trim().orEmpty()
        val station = etStation.text?.toString()?.trim().orEmpty()
        val fuelType = selectedFuelTypeOption
            ?.trim()
            .orEmpty()
            .ifBlank { fuelTypeOptions.firstOrNull() ?: getString(R.string.garage_fuel_entry_fuel_type_default) }
        val litres = parseDecimal(etLitres.text?.toString())
        val pricePerLitre = parseDecimal(etPricePerLitre.text?.toString())
        val discountAmount = parseDecimal(etDiscountAmount.text?.toString()) ?: 0.0
        val odometerKm = readOdometerInputKm()
        val notes = etNotes.text?.toString()?.trim().orEmpty()

        var hasError = false

        if (date.isBlank()) {
            inputDate.error = getString(R.string.garage_fuel_entry_error_date)
            hasError = true
        }

        if (litres == null || litres <= 0.0) {
            inputLitres.error = getString(R.string.garage_fuel_entry_error_litres)
            hasError = true
        }

        if (pricePerLitre == null || pricePerLitre <= 0.0) {
            inputPricePerLitre.error = getString(R.string.garage_fuel_entry_error_price_per_litre)
            hasError = true
        }

        if (odometerKm == null || odometerKm <= 0L) {
            inputOdometer.error = getString(R.string.garage_fuel_entry_error_odometer)
            hasError = true
        }

        val odometerSequenceError = resolveOdometerSequenceError(odometerKm)
        if (odometerSequenceError != null) {
            inputOdometer.error = odometerSequenceError
            hasError = true
        }

        val grossAmount = if (litres != null && litres > 0.0 && pricePerLitre != null && pricePerLitre > 0.0) {
            litres * pricePerLitre
        } else {
            null
        }

        if (grossAmount != null && discountAmount > grossAmount) {
            inputDiscountAmount.error = getString(R.string.garage_fuel_entry_error_discount)
            hasError = true
        }

        val resolvedTotalAmount = if (grossAmount != null) {
            (grossAmount - discountAmount).coerceAtLeast(0.0)
        } else {
            null
        }

        if (hasError || litres == null || pricePerLitre == null || resolvedTotalAmount == null || odometerKm == null) {
            return
        }

        val receiptImagePaths = resolveReceiptImagesForSave(entryId)
        if (receiptImagePaths == null) {
            Toast.makeText(this, getString(R.string.garage_fuel_entry_receipt_error), Toast.LENGTH_SHORT).show()
            return
        }
        val receiptImagePath = GarageReceiptImagePathCodec.encode(receiptImagePaths)

        GarageFuelEntryStorage.upsertEntry(
            this,
            GarageFuelEntry(
                id = entryId,
                profileId = profileId,
                date = date,
                station = station,
                fuelType = fuelType,
                litres = litres,
                pricePerLitre = pricePerLitre,
                discountAmount = discountAmount,
                totalAmount = resolvedTotalAmount,
                odometerKm = odometerKm,
                isFullTank = switchFullTank.isChecked,
                notes = notes,
                receiptImagePath = receiptImagePath,
                createdAt = existingEntry?.createdAt ?: System.currentTimeMillis(),
                sessionLatitude = sessionLatitude,
                sessionLongitude = sessionLongitude
            )
        )
        if (sessionKey > 0L && existingEntry == null) {
            SessionFuelStorage.addPendingStop(
                this,
                sessionKey,
                SessionFuelStop(
                    fuelEntryId = entryId,
                    profileId = profileId,
                    station = station,
                    litres = litres,
                    pricePerLitre = pricePerLitre,
                    totalAmount = resolvedTotalAmount,
                    odometerKm = odometerKm,
                    latitude = sessionLatitude,
                    longitude = sessionLongitude,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        originalReceiptImagePaths.clear()
        originalReceiptImagePaths.addAll(receiptImagePaths)
        currentReceiptImagePaths.clear()
        currentReceiptImagePaths.addAll(receiptImagePaths)
        persistLastSelections(
            stationText = station,
            stationLabel = resolveStationSelection(station, selectedStationOption),
            fuelTypeLabel = fuelType
        )
        syncFuelLogCounter()
        GarageMaintenanceReminderManager.evaluateDueRemindersForProfile(this, profileId)

        Toast.makeText(
            this,
            getString(
                when {
                    existingEntry != null -> R.string.garage_fuel_entry_update_success
                    sessionKey > 0L -> R.string.session_fuel_saved_toast
                    else -> R.string.garage_fuel_entry_save_success
                }
            ),
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private fun clearErrors() {
        inputDate.error = null
        inputLitres.error = null
        inputPricePerLitre.error = null
        inputDiscountAmount.error = null
        inputOdometer.error = null
    }

    private fun parseDecimal(value: String?): Double? {
        return value
            ?.trim()
            ?.replace(',', '.')
            ?.takeIf { it.isNotEmpty() }
            ?.toDoubleOrNull()
    }

    private fun parseWholeNumber(value: String?): Long? {
        return value
            ?.trim()
            ?.replace(" ", "")
            ?.replace(",", "")
            ?.takeIf { it.isNotEmpty() }
            ?.toLongOrNull()
    }

    private fun syncFuelLogCounter() {
        val count = GarageFuelEntryStorage.getCount(this, profileId)
        getSharedPreferences(EXTRA_STATS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("profile_${profileId}_fuel_logs_count", count)
            .apply()
    }

    private fun dpToPx(valueDp: Int): Int {
        return (valueDp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        cleanupPendingCameraCapture()
        temporaryReceiptImagePaths.toList().forEach { tempPath ->
            GarageFuelReceiptStorage.deleteReceipt(this, tempPath)
        }
        temporaryReceiptImagePaths.clear()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "PROFILE_ID"
        const val EXTRA_ENTRY_ID = "ENTRY_ID"
        const val EXTRA_SESSION_KEY = "SESSION_KEY"
        const val EXTRA_SESSION_LATITUDE = "SESSION_LATITUDE"
        const val EXTRA_SESSION_LONGITUDE = "SESSION_LONGITUDE"
        private const val EXTRA_STATS_PREFS = "garage_profile_extra_stats"
        private const val LAST_SELECTION_PREFS_NAME = "garage_fuel_last_selection"
        private const val LAST_STATION_TEXT_SUFFIX = "last_station_text"
        private const val LAST_STATION_LABEL_SUFFIX = "last_station_label"
        private const val LAST_FUEL_TYPE_LABEL_SUFFIX = "last_fuel_type_label"
        private const val STATION_PREFS_NAME = "garage_fuel_station_options"
        private const val STATION_OPTIONS_PREFS_KEY = "station_options"
        private const val STATION_LEGACY_CUSTOM_PREFS_KEY = "custom_station_options"
        private const val FUEL_TYPE_PREFS_NAME = "garage_fuel_type_options"
        private const val FUEL_TYPE_OPTIONS_PREFS_KEY = "fuel_type_options"
        private const val FUEL_TYPE_LEGACY_CUSTOM_PREFS_KEY = "custom_fuel_type_options"
        private const val MAX_STATION_BUTTONS_PER_ROW = 4
        private const val MAX_FUEL_TYPE_BUTTONS_PER_ROW = 3
        private const val RECEIPT_CAMERA_TEMP_DIR = "fuel_receipts_camera"
        private const val REQUEST_CAMERA_PERMISSION = 4102
    }

    private data class SelectorButtonItem(
        val label: String,
        val isOther: Boolean
    )

    private data class LastSelections(
        val stationText: String,
        val stationLabel: String?,
        val fuelTypeLabel: String?
    )

}