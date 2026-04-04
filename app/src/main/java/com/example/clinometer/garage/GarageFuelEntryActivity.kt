package com.example.clinometer.garage

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
import com.example.clinometer.FullScreenImageActivity
import com.example.clinometer.R
import com.example.clinometer.applySystemBarsPaddingToRoot
import com.example.clinometer.data.GarageOdometerConflict
import com.example.clinometer.data.GarageOdometerSource
import com.example.clinometer.data.GarageOdometerTimeline
import com.example.clinometer.data.GarageFuelEntry
import com.example.clinometer.data.GarageFuelEntryStorage
import com.example.clinometer.data.GarageFuelReceiptStorage
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.settings.LanguageManager
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
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GarageFuelEntryActivity : AppCompatActivity() {

    private var profileId: Long = -1L
    private var editingEntryId: Long = -1L
    private var draftEntryId: Long = -1L
    private var editingEntry: GarageFuelEntry? = null
    private var originalReceiptImagePath: String? = null
    private var currentReceiptImagePath: String? = null
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

    private lateinit var btnBack: MaterialButton
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
    private lateinit var ivReceiptPreview: ImageView
    private lateinit var tvCalculatedTotalAmount: TextView
    private lateinit var switchFullTank: SwitchMaterial

    private val selectedDate = Calendar.getInstance()
    private val dateFormatter by lazy {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    }
    private val currencyFormatter by lazy {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(::importReceiptImage)
    }
    private val cameraCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cameraUri = pendingCameraImageUri
        if (result.resultCode == RESULT_OK && cameraUri != null) {
            importReceiptImage(cameraUri, cleanupCapturedCameraFile = true)
        } else {
            cleanupPendingCameraCapture()
        }
    }
    private val receiptPreviewLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val shouldDelete = result.data?.getBooleanExtra(FullScreenImageActivity.EXTRA_DELETE_REQUESTED, false) == true
        if (result.resultCode == RESULT_OK && shouldDelete) {
            removeCurrentReceiptImage()
        }
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
        updateReceiptPreview()
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
        ivReceiptPreview = findViewById(R.id.ivFuelEntryReceiptPreview)
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
        cardReceiptPreview.setOnClickListener { openReceiptPreview() }
        switchFullTank.setOnCheckedChangeListener { _, _ -> updateFullTankHelperText() }
        etOdometer.addTextChangedListener(afterTextChanged = {
            val typedValue = it?.toString().orEmpty()
            inputOdometer.error = if (typedValue.isBlank()) {
                null
            } else {
                resolveOdometerSequenceError(parseWholeNumber(typedValue))
            }
            updateFullTankHelperText()
        })
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

        etDate.setText(entry.date)
        setStationFieldValue(entry.station, requestFocus = false)
        etLitres.setText(formatEditableDecimal(entry.litres))
        etPricePerLitre.setText(formatEditableDecimal(entry.pricePerLitre))
        etDiscountAmount.setText(formatEditableDecimal(entry.discountAmount))
        etOdometer.setText(entry.odometerKm.toString())
        etNotes.setText(entry.notes)
        switchFullTank.isChecked = entry.isFullTank
        originalReceiptImagePath = entry.receiptImagePath
        currentReceiptImagePath = entry.receiptImagePath

        runCatching { dateFormatter.parse(entry.date) }
            .getOrNull()
            ?.let { parsedDate -> selectedDate.time = parsedDate }

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

    private fun importReceiptImage(uri: Uri, cleanupCapturedCameraFile: Boolean = false) {
        lifecycleScope.launch {
            val tempImagePath = withContext(Dispatchers.IO) {
                GarageFuelReceiptStorage.saveTempReceipt(
                    context = this@GarageFuelEntryActivity,
                    uri = uri,
                    profileId = profileId,
                    entryId = draftEntryId
                )
            }

            if (cleanupCapturedCameraFile) {
                cleanupPendingCameraCapture()
            }

            if (tempImagePath == null) {
                Toast.makeText(
                    this@GarageFuelEntryActivity,
                    getString(R.string.garage_fuel_entry_receipt_error),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            currentReceiptImagePath
                ?.takeIf { it != originalReceiptImagePath }
                ?.let(::deleteTemporaryReceiptImage)

            temporaryReceiptImagePaths.add(tempImagePath)
            currentReceiptImagePath = tempImagePath
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

    private fun openReceiptPreview() {
        val receiptFile = GarageFuelReceiptStorage.resolveReceiptFile(this, currentReceiptImagePath)
        if (receiptFile?.exists() != true) {
            return
        }

        val intent = Intent(this, FullScreenImageActivity::class.java).apply {
            putStringArrayListExtra(
                FullScreenImageActivity.EXTRA_PHOTO_PATHS,
                arrayListOf(receiptFile.absolutePath)
            )
            putExtra(FullScreenImageActivity.EXTRA_CURRENT_INDEX, 0)
            putExtra(FullScreenImageActivity.EXTRA_SHOW_DELETE, true)
        }
        receiptPreviewLauncher.launch(intent)
    }

    private fun removeCurrentReceiptImage() {
        currentReceiptImagePath
            ?.takeIf { it != originalReceiptImagePath }
            ?.let(::deleteTemporaryReceiptImage)

        currentReceiptImagePath = null
        updateReceiptPreview()

        Toast.makeText(
            this,
            getString(R.string.garage_fuel_entry_receipt_deleted),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateReceiptPreview() {
        val receiptFile = GarageFuelReceiptStorage.resolveReceiptFile(this, currentReceiptImagePath)
        val hasImage = receiptFile?.exists() == true

        if (!hasImage) {
            if (currentReceiptImagePath == originalReceiptImagePath) {
                originalReceiptImagePath = null
            }
            currentReceiptImagePath = null
            cardReceiptPreview.visibility = View.GONE
            ivReceiptPreview.setImageDrawable(null)
            btnReceiptImage.text = getString(R.string.garage_fuel_entry_receipt_add)
            return
        }

        ivReceiptPreview.setImageBitmap(BitmapFactory.decodeFile(receiptFile!!.absolutePath))
        cardReceiptPreview.visibility = View.VISIBLE
        btnReceiptImage.text = getString(R.string.garage_fuel_entry_receipt_change)
    }

    private fun resolveReceiptImageForSave(entryId: Long): String? {
        val currentPath = currentReceiptImagePath?.trim().orEmpty()
        val originalPath = originalReceiptImagePath?.trim().orEmpty()

        if (currentPath.isEmpty()) {
            if (originalPath.isNotEmpty()) {
                GarageFuelReceiptStorage.deleteReceipt(this, originalPath)
            }
            return null
        }

        if (currentPath == originalPath) {
            return currentPath
        }

        val finalPath = GarageFuelReceiptStorage.promoteTempReceipt(this, currentPath, profileId, entryId)
        if (finalPath != null) {
            temporaryReceiptImagePaths.remove(currentPath)
        }

        if (finalPath != null && originalPath.isNotEmpty() && originalPath != finalPath) {
            GarageFuelReceiptStorage.deleteReceipt(this, originalPath)
        }

        return finalPath
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

        val type = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson<List<String>>(json, type)
            ?: emptyList()
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
        val currentOdometerKm = parseWholeNumber(etOdometer.text?.toString())
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
        return NumberFormat.getIntegerInstance(Locale.US).format(valueKm)
    }

    private fun resolveOdometerSequenceError(odometerKm: Long?): String? {
        val conflict = if (editingEntry == null) {
            GarageOdometerTimeline.resolveLatestAddedConflict(
                context = this,
                profileId = profileId,
                source = GarageOdometerSource.FUEL,
                entryId = draftEntryId,
                odometerKm = odometerKm
            )
        } else {
            null
        } ?: GarageOdometerTimeline.resolveConflict(
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
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format(valueKm)
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
        val odometerKm = parseWholeNumber(etOdometer.text?.toString())
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

        val receiptImagePath = resolveReceiptImageForSave(entryId)
        if (currentReceiptImagePath?.isNotBlank() == true && currentReceiptImagePath != originalReceiptImagePath && receiptImagePath == null) {
            Toast.makeText(this, getString(R.string.garage_fuel_entry_receipt_error), Toast.LENGTH_SHORT).show()
            return
        }

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
                createdAt = existingEntry?.createdAt ?: System.currentTimeMillis()
            )
        )
        originalReceiptImagePath = receiptImagePath
        currentReceiptImagePath = receiptImagePath
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
                if (existingEntry != null) {
                    R.string.garage_fuel_entry_update_success
                } else {
                    R.string.garage_fuel_entry_save_success
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