package com.example.clinometer.garage

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.clinometer.FullScreenImageActivity
import com.example.clinometer.R
import com.example.clinometer.applySystemBarsPaddingToRoot
import com.example.clinometer.data.GarageMaintenanceEntry
import com.example.clinometer.data.GarageMaintenanceEntryStorage
import com.example.clinometer.data.GarageOdometerConflict
import com.example.clinometer.data.GarageOdometerSource
import com.example.clinometer.data.GarageOdometerTimeline
import com.example.clinometer.data.GarageMaintenanceReceiptStorage
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.settings.LanguageManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GarageMaintenanceEntryActivity : AppCompatActivity() {

    private var profileId: Long = -1L
    private var editingEntryId: Long = -1L
    private var draftEntryId: Long = System.currentTimeMillis()
    private var editingEntry: GarageMaintenanceEntry? = null
    private var originalReceiptImagePath: String? = null
    private var currentReceiptImagePath: String? = null
    private var pendingCameraImageUri: Uri? = null
    private var pendingCameraCaptureFile: File? = null
    private val temporaryReceiptImagePaths = mutableSetOf<String>()
    private val serviceTypeOptions = mutableListOf(
        "Oil Change",
        "Engine",
        "Wheels",
        "Gearbox",
        "Suspension",
        "Brakes",
        "Electrical"
    )
    private var selectedServiceType: String? = null

    private lateinit var btnBack: MaterialButton
    private lateinit var btnCompleteReminder: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnReceiptImage: MaterialButton
    private lateinit var tvTitle: TextView
    private lateinit var llServiceTypeRows: LinearLayout
    private lateinit var inputPartsCost: TextInputLayout
    private lateinit var inputLaborCost: TextInputLayout
    private lateinit var inputOdometer: TextInputLayout
    private lateinit var inputDate: TextInputLayout
    private lateinit var inputDescription: TextInputLayout
    private lateinit var switchReminder: SwitchMaterial
    private lateinit var llReminderConfig: LinearLayout
    private lateinit var llReminderKmModeRows: LinearLayout
    private lateinit var llReminderKmDetails: LinearLayout
    private lateinit var llReminderKmLeadRows: LinearLayout
    private lateinit var llReminderDateModeRows: LinearLayout
    private lateinit var llReminderDateDetails: LinearLayout
    private lateinit var llReminderDateLeadRows: LinearLayout
    private lateinit var inputReminderKmValue: TextInputLayout
    private lateinit var inputReminderDateInterval: TextInputLayout
    private lateinit var inputReminderExactDate: TextInputLayout
    private lateinit var tvCalculatedTotalAmount: TextView
    private lateinit var tvReminderSummary: TextView
    private lateinit var cardReceiptPreview: MaterialCardView
    private lateinit var ivReceiptPreview: ImageView
    private lateinit var etPartsCost: TextInputEditText
    private lateinit var etLaborCost: TextInputEditText
    private lateinit var etOdometer: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etReminderKmValue: TextInputEditText
    private lateinit var etReminderDateInterval: TextInputEditText
    private lateinit var etReminderExactDate: TextInputEditText
    private val selectedDate = Calendar.getInstance()
    private var selectedReminderExactDateMillis: Long? = null
    private var reminderKmMode = GarageReminderMode.OFF
    private var reminderDateMode = GarageReminderMode.OFF
    private var selectedReminderKmLeadKm: Long? = null
    private var selectedReminderDateLeadOption: GarageReminderDateLeadOption? = null
    private var isUpdatingReminderSwitch = false
    private val dateFormatter by lazy {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    }
    private val reminderDateFormatter by lazy {
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
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchReceiptCamera()
        } else {
            Toast.makeText(
                this,
                getString(R.string.garage_maintenance_entry_receipt_camera_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
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
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            setReminderSwitchChecked(false)
            Toast.makeText(
                this,
                getString(R.string.garage_maintenance_entry_reminder_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
        updateReminderUi()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_garage_maintenance_entry)
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
        setupCalculatedTotalAmount()
        prefillDefaults()
        setupClickListeners()
        prefillEntryForEditing()
        if (isFinishing) {
            return
        }
        updateEntryModeUi()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBackFromMaintenanceEntry)
        btnCompleteReminder = findViewById(R.id.btnCompleteMaintenanceReminder)
        btnCancel = findViewById(R.id.btnCancelMaintenanceEntry)
        btnSave = findViewById(R.id.btnSaveMaintenanceEntry)
        btnReceiptImage = findViewById(R.id.btnMaintenanceEntryReceiptImage)
        tvTitle = findViewById(R.id.tvMaintenanceEntryTitle)
        llServiceTypeRows = findViewById(R.id.llMaintenanceEntryServiceTypeRows)
        inputPartsCost = findViewById(R.id.inputMaintenanceEntryPartsCost)
        inputLaborCost = findViewById(R.id.inputMaintenanceEntryLaborCost)
        inputOdometer = findViewById(R.id.inputMaintenanceEntryOdometer)
        inputDate = findViewById(R.id.inputMaintenanceEntryDate)
        inputDescription = findViewById(R.id.inputMaintenanceEntryDescription)
        switchReminder = findViewById(R.id.switchMaintenanceEntryReminder)
        llReminderConfig = findViewById(R.id.llMaintenanceEntryReminderConfig)
        llReminderKmModeRows = findViewById(R.id.llMaintenanceEntryReminderKmModeRows)
        llReminderKmDetails = findViewById(R.id.llMaintenanceEntryReminderKmDetails)
        llReminderKmLeadRows = findViewById(R.id.llMaintenanceEntryReminderKmLeadRows)
        llReminderDateModeRows = findViewById(R.id.llMaintenanceEntryReminderDateModeRows)
        llReminderDateDetails = findViewById(R.id.llMaintenanceEntryReminderDateDetails)
        llReminderDateLeadRows = findViewById(R.id.llMaintenanceEntryReminderDateLeadRows)
        inputReminderKmValue = findViewById(R.id.inputMaintenanceEntryReminderKmValue)
        inputReminderDateInterval = findViewById(R.id.inputMaintenanceEntryReminderDateInterval)
        inputReminderExactDate = findViewById(R.id.inputMaintenanceEntryReminderExactDate)
        tvCalculatedTotalAmount = findViewById(R.id.tvMaintenanceEntryCalculatedTotalAmount)
        tvReminderSummary = findViewById(R.id.tvMaintenanceEntryReminderSummary)
        cardReceiptPreview = findViewById(R.id.cardMaintenanceEntryReceiptPreview)
        ivReceiptPreview = findViewById(R.id.ivMaintenanceEntryReceiptPreview)
        etPartsCost = findViewById(R.id.etMaintenanceEntryPartsCost)
        etLaborCost = findViewById(R.id.etMaintenanceEntryLaborCost)
        etOdometer = findViewById(R.id.etMaintenanceEntryOdometer)
        etDate = findViewById(R.id.etMaintenanceEntryDate)
        etDescription = findViewById(R.id.etMaintenanceEntryDescription)
        etReminderKmValue = findViewById(R.id.etMaintenanceEntryReminderKmValue)
        etReminderDateInterval = findViewById(R.id.etMaintenanceEntryReminderDateInterval)
        etReminderExactDate = findViewById(R.id.etMaintenanceEntryReminderExactDate)

        tvTitle.text = getString(R.string.garage_maintenance_entry_title)
    }

    private fun setupDateInput() {
        etDate.setOnClickListener { showDateTimePicker() }
        inputDate.setEndIconOnClickListener { showDateTimePicker() }
    }

    private fun prefillDefaults() {
        selectedServiceType = serviceTypeOptions.firstOrNull()
        etDate.setText(dateFormatter.format(selectedDate.time))
    }

    private fun setupCalculatedTotalAmount() {
        val amountWatcher: (CharSequence?) -> Unit = {
            updateCalculatedTotalAmount()
        }

        etPartsCost.addTextChangedListener(afterTextChanged = amountWatcher)
        etLaborCost.addTextChangedListener(afterTextChanged = amountWatcher)
        updateCalculatedTotalAmount()
    }

    private fun prefillEntryForEditing() {
        if (editingEntryId == -1L) {
            return
        }

        val entry = GarageMaintenanceEntryStorage.findEntry(this, profileId, editingEntryId)
        if (entry == null) {
            Toast.makeText(this, getString(R.string.garage_maintenance_entry_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        editingEntry = entry
        bindEntryToForm(entry)
    }

    private fun bindEntryToForm(entry: GarageMaintenanceEntry) {
        if (entry.serviceType.isNotBlank() && serviceTypeOptions.none { it.equals(entry.serviceType, ignoreCase = true) }) {
            serviceTypeOptions.add(entry.serviceType)
        }

        selectedServiceType = entry.serviceType.ifBlank { serviceTypeOptions.firstOrNull() }
        etDate.setText(entry.date)
        etPartsCost.setText(formatEditableDecimal(entry.partsCost))
        etLaborCost.setText(formatEditableDecimal(entry.laborCost))
        etOdometer.setText(entry.odometerKm.toString())
        etDescription.setText(entry.description)
        originalReceiptImagePath = entry.receiptImagePath
        currentReceiptImagePath = entry.receiptImagePath

        reminderKmMode = when {
            entry.reminderExactKm != null -> GarageReminderMode.EXACT
            entry.reminderKmInterval != null -> GarageReminderMode.INTERVAL
            else -> GarageReminderMode.OFF
        }
        reminderDateMode = when {
            entry.reminderExactDateMillis != null -> GarageReminderMode.EXACT
            entry.reminderDateIntervalMonths != null -> GarageReminderMode.INTERVAL
            else -> GarageReminderMode.OFF
        }
        etReminderKmValue.setText((entry.reminderExactKm ?: entry.reminderKmInterval)?.toString().orEmpty())
        etReminderDateInterval.setText(entry.reminderDateIntervalMonths?.toString().orEmpty())
        selectedReminderExactDateMillis = entry.reminderExactDateMillis
        selectedReminderKmLeadKm = entry.reminderKmLeadKm
        selectedReminderDateLeadOption = GarageReminderDateLeadOption.fromStorageKey(entry.reminderDateLeadPreset)
        setReminderSwitchChecked(entry.reminderEnabled)

        runCatching { dateFormatter.parse(entry.date) }
            .getOrNull()
            ?.let { parsedDate -> selectedDate.time = parsedDate }
    }

    private fun updateEntryModeUi() {
        val isReadOnly = isCompletedReadOnlyEntry()
        tvTitle.text = when {
            editingEntry == null -> getString(R.string.garage_maintenance_entry_title)
            isReadOnly -> getString(R.string.garage_maintenance_entry_view_title)
            else -> getString(R.string.garage_maintenance_entry_edit_title)
        }
        btnCancel.text = getString(
            if (isReadOnly) {
                R.string.garage_maintenance_entry_close
            } else {
                R.string.garage_fuel_entry_cancel
            }
        )
        applyReadOnlyState(isReadOnly)
        renderServiceTypeButtons()
        updateReceiptPreview()
        updateReminderUi()
        updateCompleteReminderAction()
    }

    private fun isCompletedReadOnlyEntry(): Boolean {
        return editingEntry?.reminderCompletedAt != null
    }

    private fun applyReadOnlyState(isReadOnly: Boolean) {
        inputPartsCost.isEnabled = !isReadOnly
        inputLaborCost.isEnabled = !isReadOnly
        inputOdometer.isEnabled = !isReadOnly
        inputDate.isEnabled = !isReadOnly
        inputDescription.isEnabled = !isReadOnly
        inputReminderKmValue.isEnabled = !isReadOnly
        inputReminderDateInterval.isEnabled = !isReadOnly
        inputReminderExactDate.isEnabled = !isReadOnly
        inputDate.isEndIconVisible = !isReadOnly
        inputReminderExactDate.isEndIconVisible = !isReadOnly
        etDate.isClickable = !isReadOnly
        etReminderExactDate.isClickable = !isReadOnly
        switchReminder.isEnabled = !isReadOnly
        btnReceiptImage.visibility = if (isReadOnly) View.GONE else View.VISIBLE
        btnSave.visibility = if (isReadOnly) View.GONE else View.VISIBLE
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnCompleteReminder.setOnClickListener { confirmCompleteReminder() }
        btnCancel.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveMaintenanceEntry() }
        btnReceiptImage.setOnClickListener { showReceiptImageSourceDialog() }
        cardReceiptPreview.setOnClickListener { openReceiptPreview() }
        switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingReminderSwitch) {
                return@setOnCheckedChangeListener
            }

            if (isChecked && !hasNotificationPermission()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            updateReminderUi()
        }
        etDescription.addTextChangedListener {
            inputDescription.error = null
        }
        etOdometer.addTextChangedListener(afterTextChanged = {
            val typedValue = it?.toString().orEmpty()
            inputOdometer.error = if (typedValue.isBlank()) {
                null
            } else {
                resolveOdometerSequenceError(parseWholeNumber(typedValue))
            }
            inputReminderKmValue.error = null
            updateReminderUi()
        })
        etReminderKmValue.addTextChangedListener {
            inputReminderKmValue.error = null
            updateReminderUi()
        }
        etReminderDateInterval.addTextChangedListener {
            inputReminderDateInterval.error = null
            updateReminderUi()
        }
        etReminderExactDate.setOnClickListener { showReminderExactDatePicker() }
        inputReminderExactDate.setEndIconOnClickListener {
            if (selectedReminderExactDateMillis != null) {
                clearReminderExactDate()
            } else {
                showReminderExactDatePicker()
            }
        }
    }

    private fun showReceiptImageSourceDialog() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        val options = arrayOf(
            getString(R.string.garage_maintenance_entry_receipt_source_camera),
            getString(R.string.garage_maintenance_entry_receipt_source_gallery)
        )
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.garage_maintenance_entry_receipt_source_title)
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
        if (isCompletedReadOnlyEntry()) {
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchReceiptCamera()
            return
        }

        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun renderServiceTypeButtons() {
        val serviceItems = buildList {
            serviceTypeOptions.forEach { add(SelectorButtonItem(label = it, isOther = false)) }
            add(SelectorButtonItem(label = getString(R.string.garage_maintenance_entry_other_button), isOther = true))
        }

        renderSelectorButtons(llServiceTypeRows, serviceItems, MAX_SERVICE_BUTTONS_PER_ROW, ::createServiceTypeButton)
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
                        layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
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

    private fun createServiceTypeButton(item: SelectorButtonItem): MaterialButton {
        val isReadOnly = isCompletedReadOnlyEntry()
        return createSelectorButtonBase(item.label).apply {
            if (item.isOther) {
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@GarageMaintenanceEntryActivity, R.color.dark_surface))
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@GarageMaintenanceEntryActivity, R.color.stroke_dark))
                setTextColor(ContextCompat.getColor(this@GarageMaintenanceEntryActivity, R.color.text_secondary))
                isEnabled = !isReadOnly
                alpha = if (isReadOnly) 0.45f else 1f
                if (!isReadOnly) {
                    setOnClickListener { showAddCustomServiceTypeDialog() }
                }
            } else {
                resolveServiceTypeIconRes(item.label)?.let { iconRes ->
                    setIconResource(iconRes)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    iconPadding = dpToPx(6)
                    iconSize = dpToPx(14)
                }
                val isSelected = item.label == selectedServiceType
                applySelectorButtonStyle(this, isSelected)
                isEnabled = !isReadOnly
                alpha = if (isReadOnly) 0.55f else 1f
                if (!isReadOnly) {
                    setOnClickListener {
                        selectedServiceType = item.label
                        renderServiceTypeButtons()
                    }
                }
            }
        }
    }

    private fun resolveServiceTypeIconRes(label: String): Int? {
        return GarageMaintenanceServiceIcons.resolveIconRes(label)
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

    private fun showAddCustomServiceTypeDialog() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_fuel_station, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputDialogCustomStation)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.etDialogCustomStation)
        val hint = getString(R.string.garage_maintenance_entry_other_dialog_hint)

        inputLayout.hint = hint
        editText.hint = hint
        editText.addTextChangedListener {
            inputLayout.error = null
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.garage_maintenance_entry_other_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.garage_maintenance_entry_other_dialog_add, null)
            .setNegativeButton(R.string.garage_fuel_entry_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val customType = editText.text?.toString()?.trim().orEmpty()
                if (customType.isBlank()) {
                    inputLayout.error = getString(R.string.garage_maintenance_entry_other_dialog_error)
                    return@setOnClickListener
                }

                val existingLabel = serviceTypeOptions.firstOrNull { it.equals(customType, ignoreCase = true) }
                selectedServiceType = existingLabel ?: customType
                if (existingLabel == null) {
                    serviceTypeOptions.add(selectedServiceType.orEmpty())
                }
                renderServiceTypeButtons()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showDateTimePicker() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

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

    private fun launchReceiptCamera() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        cleanupPendingCameraCapture()

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) || cameraIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, getString(R.string.garage_maintenance_entry_receipt_camera_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val captureFile = createReceiptCameraCaptureFile()
        if (captureFile == null) {
            Toast.makeText(this, getString(R.string.garage_maintenance_entry_receipt_camera_error), Toast.LENGTH_SHORT).show()
            return
        }

        val captureUri = runCatching {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", captureFile)
        }.getOrNull()

        if (captureUri == null) {
            captureFile.delete()
            Toast.makeText(this, getString(R.string.garage_maintenance_entry_receipt_camera_error), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.garage_maintenance_entry_receipt_camera_error), Toast.LENGTH_SHORT).show()
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
                "maintenance_receipt_${profileId}_${draftEntryId}_",
                ".jpg",
                picturesDir
            )
        }.getOrNull()
    }

    private fun importReceiptImage(uri: Uri, cleanupCapturedCameraFile: Boolean = false) {
        if (isCompletedReadOnlyEntry()) {
            if (cleanupCapturedCameraFile) {
                cleanupPendingCameraCapture()
            }
            return
        }

        lifecycleScope.launch {
            val tempImagePath = withContext(Dispatchers.IO) {
                GarageMaintenanceReceiptStorage.saveTempReceipt(
                    context = this@GarageMaintenanceEntryActivity,
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
                    this@GarageMaintenanceEntryActivity,
                    getString(R.string.garage_maintenance_entry_receipt_error),
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
                this@GarageMaintenanceEntryActivity,
                getString(R.string.garage_maintenance_entry_receipt_added),
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
    }

    private fun openReceiptPreview() {
        val receiptFile = GarageMaintenanceReceiptStorage.resolveReceiptFile(this, currentReceiptImagePath)
        if (receiptFile?.exists() != true) {
            return
        }

        val intent = Intent(this, FullScreenImageActivity::class.java).apply {
            putStringArrayListExtra(
                FullScreenImageActivity.EXTRA_PHOTO_PATHS,
                arrayListOf(receiptFile.absolutePath)
            )
            putExtra(FullScreenImageActivity.EXTRA_CURRENT_INDEX, 0)
            putExtra(FullScreenImageActivity.EXTRA_SHOW_DELETE, !isCompletedReadOnlyEntry())
        }
        receiptPreviewLauncher.launch(intent)
    }

    private fun removeCurrentReceiptImage() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        currentReceiptImagePath
            ?.takeIf { it != originalReceiptImagePath }
            ?.let(::deleteTemporaryReceiptImage)

        currentReceiptImagePath = null
        updateReceiptPreview()

        Toast.makeText(
            this,
            getString(R.string.garage_maintenance_entry_receipt_deleted),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateReceiptPreview() {
        val receiptFile = GarageMaintenanceReceiptStorage.resolveReceiptFile(this, currentReceiptImagePath)
        val hasImage = receiptFile?.exists() == true

        if (!hasImage) {
            if (currentReceiptImagePath == originalReceiptImagePath) {
                originalReceiptImagePath = null
            }
            currentReceiptImagePath = null
            cardReceiptPreview.visibility = View.GONE
            ivReceiptPreview.setImageDrawable(null)
            btnReceiptImage.text = getString(R.string.garage_maintenance_entry_receipt_add)
            return
        }

        ivReceiptPreview.setImageBitmap(BitmapFactory.decodeFile(receiptFile!!.absolutePath))
        cardReceiptPreview.visibility = View.VISIBLE
        btnReceiptImage.text = getString(R.string.garage_maintenance_entry_receipt_change)
    }

    private fun resolveReceiptImageForSave(entryId: Long): String? {
        val currentPath = currentReceiptImagePath?.trim().orEmpty()
        val originalPath = originalReceiptImagePath?.trim().orEmpty()

        if (currentPath.isEmpty()) {
            if (originalPath.isNotEmpty()) {
                GarageMaintenanceReceiptStorage.deleteReceipt(this, originalPath)
            }
            return null
        }

        if (currentPath == originalPath) {
            return currentPath
        }

        val finalPath = GarageMaintenanceReceiptStorage.promoteTempReceipt(this, currentPath, profileId, entryId)
        if (finalPath != null) {
            temporaryReceiptImagePaths.remove(currentPath)
        }

        if (finalPath != null && originalPath.isNotEmpty() && originalPath != finalPath) {
            GarageMaintenanceReceiptStorage.deleteReceipt(this, originalPath)
        }

        return finalPath
    }

    private fun deleteTemporaryReceiptImage(relativePath: String) {
        temporaryReceiptImagePaths.remove(relativePath)
        GarageMaintenanceReceiptStorage.deleteReceipt(this, relativePath)
    }

    private fun createWhiteTextDialogAdapter(options: Array<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            options
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.setTextColor(ContextCompat.getColor(this@GarageMaintenanceEntryActivity, R.color.white))
                return view
            }
        }
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
                updateReminderUi()
            },
            selectedDate.get(Calendar.HOUR_OF_DAY),
            selectedDate.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateCalculatedTotalAmount() {
        tvCalculatedTotalAmount.text = formatCalculatedTotalAmount(getCalculatedTotalAmount())
    }

    private fun getCalculatedTotalAmount(): Double? {
        val partsCost = parseDecimal(etPartsCost.text?.toString())
        val laborCost = parseDecimal(etLaborCost.text?.toString())

        if (partsCost == null && laborCost == null) {
            return null
        }

        return (partsCost ?: 0.0).coerceAtLeast(0.0) + (laborCost ?: 0.0).coerceAtLeast(0.0)
    }

    private fun formatCalculatedTotalAmount(value: Double?): String {
        if (value == null) {
            return getString(R.string.garage_maintenance_entry_total_amount_placeholder)
        }

        return getString(
            R.string.garage_maintenance_entry_total_amount_format,
            currencyFormatter.format(value)
        )
    }

    private fun showReminderExactDatePicker() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        val initialCalendar = Calendar.getInstance().apply {
            timeInMillis = selectedReminderExactDateMillis ?: selectedDate.timeInMillis
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val reminderCalendar = Calendar.getInstance().apply {
                    timeInMillis = selectedReminderExactDateMillis ?: selectedDate.timeInMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                showReminderExactTimePicker(reminderCalendar)
            },
            initialCalendar.get(Calendar.YEAR),
            initialCalendar.get(Calendar.MONTH),
            initialCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showReminderExactTimePicker(reminderCalendar: Calendar) {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                reminderCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                reminderCalendar.set(Calendar.MINUTE, minute)
                reminderCalendar.set(Calendar.SECOND, 0)
                reminderCalendar.set(Calendar.MILLISECOND, 0)
                selectedReminderExactDateMillis = reminderCalendar.timeInMillis
                inputReminderExactDate.error = null
                updateReminderUi()
            },
            reminderCalendar.get(Calendar.HOUR_OF_DAY),
            reminderCalendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun clearReminderExactDate() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        selectedReminderExactDateMillis = null
        inputReminderExactDate.error = null
        updateReminderUi()
    }

    private fun resolveOdometerSequenceError(odometerKm: Long?): String? {
        val conflict = if (editingEntry == null) {
            GarageOdometerTimeline.resolveLatestAddedConflict(
                context = this,
                profileId = profileId,
                source = GarageOdometerSource.MAINTENANCE,
                entryId = draftEntryId,
                odometerKm = odometerKm
            )
        } else {
            null
        } ?: GarageOdometerTimeline.resolveConflict(
            context = this,
            profileId = profileId,
            source = GarageOdometerSource.MAINTENANCE,
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

    private fun updateReminderUi() {
        val reminderEnabled = switchReminder.isChecked
        llReminderConfig.visibility = if (reminderEnabled) View.VISIBLE else View.GONE
        if (!reminderEnabled) {
            showReminderSummary(null)
            return
        }

        ensureReminderLeadSelectionsAreValid()
        renderReminderModeButtons()
        renderReminderLeadButtons()

        llReminderKmDetails.visibility = if (reminderKmMode == GarageReminderMode.OFF) View.GONE else View.VISIBLE
        llReminderDateDetails.visibility = if (reminderDateMode == GarageReminderMode.OFF) View.GONE else View.VISIBLE
        inputReminderDateInterval.visibility = if (reminderDateMode == GarageReminderMode.INTERVAL) View.VISIBLE else View.GONE
        inputReminderExactDate.visibility = if (reminderDateMode == GarageReminderMode.EXACT) View.VISIBLE else View.GONE

        inputReminderKmValue.hint = getString(
            if (reminderKmMode == GarageReminderMode.EXACT) {
                R.string.garage_maintenance_entry_reminder_exact_km
            } else {
                R.string.garage_maintenance_entry_reminder_km_interval
            }
        )
        inputReminderExactDate.setEndIconDrawable(
            if (selectedReminderExactDateMillis != null) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_today
        )
        etReminderExactDate.setText(
            selectedReminderExactDateMillis?.let { reminderDateFormatter.format(it) }.orEmpty()
        )

        updateReminderSummary()
    }

    private fun updateCompleteReminderAction() {
        val entry = editingEntry
        btnCompleteReminder.visibility = if (!isCompletedReadOnlyEntry() && entry != null && canMarkReminderCompleted(entry)) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun canMarkReminderCompleted(entry: GarageMaintenanceEntry): Boolean {
        if (!entry.reminderEnabled || entry.reminderCompletedAt != null) {
            return false
        }

        val serviceTimestamp = GarageOdometerTimeline.resolveReferenceTimestamp(entry.date, entry.createdAt)
        val targetKmReachedAt = GarageMaintenanceReminderRules.resolveKmTarget(entry)?.let {
            GarageOdometerTimeline.firstReachedTargetTimestampAfter(
                context = this,
                profileId = entry.profileId,
                source = GarageOdometerSource.MAINTENANCE,
                entryId = entry.id,
                targetOdometerKm = it,
                dateText = entry.date,
                fallbackTimestamp = entry.createdAt
            )
        }
        val targetDateMillis = GarageMaintenanceReminderRules.resolveDateTarget(entry, serviceTimestamp)

        return targetKmReachedAt != null || (targetDateMillis != null && System.currentTimeMillis() >= targetDateMillis)
    }

    private fun confirmCompleteReminder() {
        val entry = editingEntry ?: return
        if (!canMarkReminderCompleted(entry)) {
            return
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.garage_maintenance_entry_complete_reminder_title)
            .setMessage(
                getString(
                    R.string.garage_maintenance_entry_complete_reminder_message,
                    entry.serviceType.ifBlank { getString(R.string.garage_profile_maintenance_reminder_badge) }
                )
            )
            .setPositiveButton(R.string.garage_maintenance_entry_complete_reminder_confirm) { _, _ ->
                completeReminder(entry)
            }
            .setNegativeButton(R.string.garage_fuel_entry_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
        }

        dialog.show()
    }

    private fun completeReminder(entry: GarageMaintenanceEntry) {
        val updatedEntry = GarageMaintenanceReminderManager.markReminderCompleted(this, entry)
        editingEntry = updatedEntry
        bindEntryToForm(updatedEntry)
        updateEntryModeUi()

        Toast.makeText(
            this,
            getString(R.string.garage_maintenance_entry_complete_reminder_success),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setReminderSwitchChecked(checked: Boolean) {
        isUpdatingReminderSwitch = true
        switchReminder.isChecked = checked
        isUpdatingReminderSwitch = false
    }

    private fun hasNotificationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun renderReminderModeButtons() {
        renderReminderOptionButtons(
            container = llReminderKmModeRows,
            items = listOf(
                ReminderOptionButtonItem(
                    label = getString(R.string.garage_maintenance_entry_reminder_mode_off),
                    isSelected = reminderKmMode == GarageReminderMode.OFF,
                    onClick = {
                        reminderKmMode = GarageReminderMode.OFF
                        inputReminderKmValue.error = null
                        updateReminderUi()
                    }
                ),
                ReminderOptionButtonItem(
                    label = getString(R.string.garage_maintenance_entry_reminder_mode_interval),
                    isSelected = reminderKmMode == GarageReminderMode.INTERVAL,
                    onClick = {
                        reminderKmMode = GarageReminderMode.INTERVAL
                        inputReminderKmValue.error = null
                        updateReminderUi()
                    }
                ),
                ReminderOptionButtonItem(
                    label = getString(R.string.garage_maintenance_entry_reminder_mode_exact),
                    isSelected = reminderKmMode == GarageReminderMode.EXACT,
                    onClick = {
                        reminderKmMode = GarageReminderMode.EXACT
                        inputReminderKmValue.error = null
                        updateReminderUi()
                    }
                )
            ),
            maxButtonsPerRow = MAX_REMINDER_MODE_BUTTONS_PER_ROW
        )

        renderReminderOptionButtons(
            container = llReminderDateModeRows,
            items = listOf(
                ReminderOptionButtonItem(
                    label = getString(R.string.garage_maintenance_entry_reminder_mode_off),
                    isSelected = reminderDateMode == GarageReminderMode.OFF,
                    onClick = {
                        reminderDateMode = GarageReminderMode.OFF
                        inputReminderDateInterval.error = null
                        inputReminderExactDate.error = null
                        updateReminderUi()
                    }
                ),
                ReminderOptionButtonItem(
                    label = getString(R.string.garage_maintenance_entry_reminder_mode_interval),
                    isSelected = reminderDateMode == GarageReminderMode.INTERVAL,
                    onClick = {
                        reminderDateMode = GarageReminderMode.INTERVAL
                        ensureDefaultReminderDateIntervalMonths()
                        inputReminderDateInterval.error = null
                        updateReminderUi()
                    }
                ),
                ReminderOptionButtonItem(
                    label = getString(R.string.garage_maintenance_entry_reminder_mode_exact),
                    isSelected = reminderDateMode == GarageReminderMode.EXACT,
                    onClick = {
                        reminderDateMode = GarageReminderMode.EXACT
                        inputReminderExactDate.error = null
                        updateReminderUi()
                    }
                )
            ),
            maxButtonsPerRow = MAX_REMINDER_MODE_BUTTONS_PER_ROW
        )
    }

    private fun renderReminderLeadButtons() {
        renderReminderOptionButtons(
            container = llReminderKmLeadRows,
            items = GarageMaintenanceReminderRules.kmLeadOptions.map { leadKm ->
                ReminderOptionButtonItem(
                    label = "${formatDisplayOdometer(leadKm)} km",
                    isSelected = selectedReminderKmLeadKm == leadKm,
                    isEnabled = isKmLeadSelectable(leadKm),
                    onClick = {
                        selectedReminderKmLeadKm = leadKm
                        updateReminderUi()
                    }
                )
            },
            maxButtonsPerRow = MAX_KM_LEAD_BUTTONS_PER_ROW
        )

        renderReminderOptionButtons(
            container = llReminderDateLeadRows,
            items = GarageReminderDateLeadOption.values().map { leadOption ->
                ReminderOptionButtonItem(
                    label = getString(leadOption.labelResId),
                    isSelected = selectedReminderDateLeadOption == leadOption,
                    isEnabled = isDateLeadSelectable(leadOption),
                    onClick = {
                        selectedReminderDateLeadOption = leadOption
                        updateReminderUi()
                    }
                )
            },
            maxButtonsPerRow = MAX_DATE_LEAD_BUTTONS_PER_ROW
        )
    }

    private fun renderReminderOptionButtons(
        container: LinearLayout,
        items: List<ReminderOptionButtonItem>,
        maxButtonsPerRow: Int
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
                val button = createReminderOptionButton(item)
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
                        layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
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

    private fun createReminderOptionButton(item: ReminderOptionButtonItem): MaterialButton {
        val isReadOnly = isCompletedReadOnlyEntry()
        val isEnabled = item.isEnabled && !isReadOnly
        return createSelectorButtonBase(item.label).apply {
            applyReminderOptionButtonStyle(this, item.isSelected, isEnabled)
            alpha = if (isEnabled) 1f else 0.45f
            this.isEnabled = isEnabled
            setOnClickListener {
                if (isEnabled) {
                    item.onClick()
                }
            }
        }
    }

    private fun applyReminderOptionButtonStyle(button: MaterialButton, isSelected: Boolean, isEnabled: Boolean) {
        val accentColor = ContextCompat.getColor(this, R.color.accent_color)
        val defaultBackground = ContextCompat.getColor(this, R.color.card_background)
        val selectedBackground = ColorUtils.setAlphaComponent(accentColor, 40)
        val disabledStroke = ContextCompat.getColor(this, R.color.stroke_dark)
        val activeStroke = if (isSelected) accentColor else disabledStroke
        val textColor = when {
            !isEnabled -> ContextCompat.getColor(this, R.color.text_tertiary)
            isSelected -> accentColor
            else -> ContextCompat.getColor(this, R.color.text_secondary)
        }

        button.backgroundTintList = ColorStateList.valueOf(if (isSelected) selectedBackground else defaultBackground)
        button.strokeColor = ColorStateList.valueOf(activeStroke)
        button.setTextColor(textColor)
    }

    private fun ensureReminderLeadSelectionsAreValid() {
        if (selectedReminderKmLeadKm != null && !isKmLeadSelectable(selectedReminderKmLeadKm)) {
            selectedReminderKmLeadKm = null
        }
        if (selectedReminderDateLeadOption != null && !isDateLeadSelectable(selectedReminderDateLeadOption)) {
            selectedReminderDateLeadOption = null
        }
    }

    private fun isKmLeadSelectable(leadKm: Long?): Boolean {
        val serviceOdometerKm = parseWholeNumber(etOdometer.text?.toString()) ?: return false
        return GarageMaintenanceReminderRules.resolveKmReminder(
            serviceOdometerKm = serviceOdometerKm,
            mode = reminderKmMode,
            value = parseWholeNumber(etReminderKmValue.text?.toString()),
            leadKm = leadKm
        ) != null
    }

    private fun isDateLeadSelectable(leadOption: GarageReminderDateLeadOption?): Boolean {
        return GarageMaintenanceReminderRules.resolveDateReminder(
            serviceTimestamp = selectedDate.timeInMillis,
            mode = reminderDateMode,
            intervalMonths = parseReminderDateIntervalMonths(),
            exactDateMillis = selectedReminderExactDateMillis,
            leadOption = leadOption
        ) != null
    }

    private fun updateReminderSummary() {
        if (!switchReminder.isChecked) {
            showReminderSummary(null)
            return
        }

        val summaryLines = buildList {
            resolveReminderKmSummary()?.let(::add)
            resolveReminderDateSummary()?.let(::add)
            if (size > 1) {
                add(getString(R.string.garage_maintenance_entry_reminder_summary_first_wins))
            }
        }

        showReminderSummary(summaryLines.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }

    private fun resolveReminderKmSummary(): String? {
        val serviceOdometerKm = parseWholeNumber(etOdometer.text?.toString()) ?: return null
        val targetKm = GarageMaintenanceReminderRules.resolveKmTarget(
            serviceOdometerKm = serviceOdometerKm,
            mode = reminderKmMode,
            value = parseWholeNumber(etReminderKmValue.text?.toString())
        ) ?: return null
        val reminderKm = GarageMaintenanceReminderRules.resolveKmReminder(
            serviceOdometerKm = serviceOdometerKm,
            mode = reminderKmMode,
            value = parseWholeNumber(etReminderKmValue.text?.toString()),
            leadKm = selectedReminderKmLeadKm
        ) ?: return null

        return getString(
            R.string.garage_maintenance_entry_reminder_summary_km,
            formatDisplayOdometer(reminderKm),
            formatDisplayOdometer(targetKm)
        )
    }

    private fun resolveReminderDateSummary(): String? {
        val targetDateMillis = GarageMaintenanceReminderRules.resolveDateTarget(
            serviceTimestamp = selectedDate.timeInMillis,
            mode = reminderDateMode,
            intervalMonths = parseReminderDateIntervalMonths(),
            exactDateMillis = selectedReminderExactDateMillis
        ) ?: return null
        val reminderDateMillis = GarageMaintenanceReminderRules.resolveDateReminder(
            serviceTimestamp = selectedDate.timeInMillis,
            mode = reminderDateMode,
            intervalMonths = parseReminderDateIntervalMonths(),
            exactDateMillis = selectedReminderExactDateMillis,
            leadOption = selectedReminderDateLeadOption
        ) ?: return null

        return getString(
            R.string.garage_maintenance_entry_reminder_summary_date,
            reminderDateFormatter.format(reminderDateMillis),
            reminderDateFormatter.format(targetDateMillis)
        )
    }

    private fun showReminderSummary(message: String?, isError: Boolean = false) {
        if (message.isNullOrBlank()) {
            tvReminderSummary.visibility = View.GONE
            tvReminderSummary.text = ""
            return
        }

        tvReminderSummary.visibility = View.VISIBLE
        tvReminderSummary.text = message
        tvReminderSummary.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.error_color else R.color.text_tertiary
            )
        )
    }

    private fun validateReminderExactKm(exactKm: Long?): String? {
        if (exactKm == null) {
            return null
        }

        val serviceKm = parseWholeNumber(etOdometer.text?.toString()) ?: return null
        return if (exactKm <= serviceKm) {
            getString(R.string.garage_maintenance_entry_reminder_error_exact_km)
        } else {
            null
        }
    }

    private fun validateReminderExactDate(exactDateMillis: Long?): String? {
        if (exactDateMillis == null) {
            return null
        }

        return if (exactDateMillis <= selectedDate.timeInMillis) {
            getString(R.string.garage_maintenance_entry_reminder_error_exact_date)
        } else {
            null
        }
    }

    private fun parseReminderDateIntervalMonths(): Int? {
        return parseWholeNumber(etReminderDateInterval.text?.toString())
            ?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
            ?.toInt()
    }

    private fun ensureDefaultReminderDateIntervalMonths() {
        if (!etReminderDateInterval.text?.toString().isNullOrBlank()) {
            return
        }

        etReminderDateInterval.setText(DEFAULT_REMINDER_DATE_INTERVAL_MONTHS.toString())
        etReminderDateInterval.setSelection(etReminderDateInterval.text?.length ?: 0)
    }

    private fun validateReminderConfiguration(odometerKm: Long): Boolean {
        if (!switchReminder.isChecked) {
            showReminderSummary(null)
            return false
        }

        var hasError = false
        var summaryError: String? = null

        val reminderKmValue = parseWholeNumber(etReminderKmValue.text?.toString())
        val reminderDateIntervalMonths = parseReminderDateIntervalMonths()

        if (reminderKmMode == GarageReminderMode.OFF && reminderDateMode == GarageReminderMode.OFF) {
            summaryError = getString(R.string.garage_maintenance_entry_reminder_error_required)
            hasError = true
        }

        if (reminderKmMode == GarageReminderMode.INTERVAL && (reminderKmValue == null || reminderKmValue <= 0L)) {
            inputReminderKmValue.error = getString(R.string.garage_maintenance_entry_reminder_error_km_interval)
            hasError = true
        }

        if (reminderKmMode == GarageReminderMode.EXACT) {
            val exactKmError = validateReminderExactKm(reminderKmValue)
            if (reminderKmValue == null || exactKmError != null) {
                inputReminderKmValue.error = exactKmError ?: getString(R.string.garage_maintenance_entry_reminder_error_exact_km)
                hasError = true
            }
        }

        if (reminderKmMode != GarageReminderMode.OFF) {
            val reminderKmPoint = GarageMaintenanceReminderRules.resolveKmReminder(
                serviceOdometerKm = odometerKm,
                mode = reminderKmMode,
                value = reminderKmValue,
                leadKm = selectedReminderKmLeadKm
            )
            if (reminderKmPoint == null) {
                summaryError = summaryError ?: getString(R.string.garage_maintenance_entry_reminder_error_km_lead)
                hasError = true
            }
        }

        if (reminderDateMode == GarageReminderMode.INTERVAL && (reminderDateIntervalMonths == null || reminderDateIntervalMonths <= 0)) {
            inputReminderDateInterval.error = getString(R.string.garage_maintenance_entry_reminder_error_month_interval)
            hasError = true
        }

        if (reminderDateMode == GarageReminderMode.EXACT) {
            val exactDateError = validateReminderExactDate(selectedReminderExactDateMillis)
            if (selectedReminderExactDateMillis == null || exactDateError != null) {
                inputReminderExactDate.error = exactDateError ?: getString(R.string.garage_maintenance_entry_reminder_error_exact_date)
                hasError = true
            }
        }

        if (reminderDateMode != GarageReminderMode.OFF) {
            val reminderDatePoint = GarageMaintenanceReminderRules.resolveDateReminder(
                serviceTimestamp = selectedDate.timeInMillis,
                mode = reminderDateMode,
                intervalMonths = reminderDateIntervalMonths,
                exactDateMillis = selectedReminderExactDateMillis,
                leadOption = selectedReminderDateLeadOption
            )
            if (reminderDatePoint == null) {
                summaryError = summaryError ?: getString(R.string.garage_maintenance_entry_reminder_error_date_lead)
                hasError = true
            }
        }

        if (summaryError != null) {
            showReminderSummary(summaryError, isError = true)
        } else if (!hasError) {
            updateReminderSummary()
        }

        return hasError
    }

    private fun saveMaintenanceEntry() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        clearErrors()

        val existingEntry = editingEntry
        val entryId = existingEntry?.id ?: draftEntryId
        val date = etDate.text?.toString()?.trim().orEmpty()
        val serviceType = selectedServiceType
            ?.trim()
            .orEmpty()
            .ifBlank { serviceTypeOptions.firstOrNull().orEmpty() }
        val partsCost = parseDecimal(etPartsCost.text?.toString()) ?: 0.0
        val laborCost = parseDecimal(etLaborCost.text?.toString()) ?: 0.0
        val odometerKm = parseWholeNumber(etOdometer.text?.toString())
        val description = etDescription.text?.toString()?.trim().orEmpty()
        val reminderEnabled = switchReminder.isChecked
        val reminderKmValue = parseWholeNumber(etReminderKmValue.text?.toString())
        val reminderDateIntervalMonths = parseReminderDateIntervalMonths()
        val reminderExactDateMillis = selectedReminderExactDateMillis

        var hasError = false

        if (date.isBlank()) {
            inputDate.error = getString(R.string.garage_fuel_entry_error_date)
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

        if (reminderEnabled && odometerKm != null && validateReminderConfiguration(odometerKm)) {
            hasError = true
        }

        if (hasError || odometerKm == null) {
            return
        }

        val resolvedReminderKmInterval = if (reminderEnabled && reminderKmMode == GarageReminderMode.INTERVAL) reminderKmValue else null
        val resolvedReminderExactKm = if (reminderEnabled && reminderKmMode == GarageReminderMode.EXACT) reminderKmValue else null
        val resolvedReminderKmLeadKm = if (reminderEnabled && reminderKmMode != GarageReminderMode.OFF) selectedReminderKmLeadKm else null
        val resolvedReminderDateIntervalMonths = if (reminderEnabled && reminderDateMode == GarageReminderMode.INTERVAL) reminderDateIntervalMonths else null
        val resolvedReminderExactDateMillis = if (reminderEnabled && reminderDateMode == GarageReminderMode.EXACT) reminderExactDateMillis else null
        val resolvedReminderDateLeadPreset = if (reminderEnabled && reminderDateMode != GarageReminderMode.OFF) selectedReminderDateLeadOption?.storageKey else null
        val shouldResetReminderState = shouldResetReminderState(
            existingEntry = existingEntry,
            date = date,
            odometerKm = odometerKm,
            reminderEnabled = reminderEnabled,
            reminderKmInterval = resolvedReminderKmInterval,
            reminderExactKm = resolvedReminderExactKm,
            reminderKmLeadKm = resolvedReminderKmLeadKm,
            reminderDateIntervalMonths = resolvedReminderDateIntervalMonths,
            reminderExactDateMillis = resolvedReminderExactDateMillis,
            reminderDateLeadPreset = resolvedReminderDateLeadPreset
        )

        val receiptImagePath = resolveReceiptImageForSave(entryId)
        if (currentReceiptImagePath?.isNotBlank() == true && currentReceiptImagePath != originalReceiptImagePath && receiptImagePath == null) {
            Toast.makeText(this, getString(R.string.garage_maintenance_entry_receipt_error), Toast.LENGTH_SHORT).show()
            return
        }

        val savedEntry = GarageMaintenanceEntry(
            id = entryId,
            profileId = profileId,
            date = date,
            serviceType = serviceType,
            partsCost = partsCost,
            laborCost = laborCost,
            odometerKm = odometerKm,
            description = description,
            receiptImagePath = receiptImagePath,
            reminderEnabled = reminderEnabled,
            reminderKmInterval = resolvedReminderKmInterval,
            reminderExactKm = resolvedReminderExactKm,
            reminderKmLeadKm = resolvedReminderKmLeadKm,
            reminderDateIntervalMonths = resolvedReminderDateIntervalMonths,
            reminderExactDateMillis = resolvedReminderExactDateMillis,
            reminderDateLeadPreset = resolvedReminderDateLeadPreset,
            reminderTriggeredAt = if (shouldResetReminderState) null else existingEntry?.reminderTriggeredAt,
            reminderTriggeredBy = if (shouldResetReminderState) null else existingEntry?.reminderTriggeredBy,
            reminderCompletedAt = if (shouldResetReminderState) null else existingEntry?.reminderCompletedAt,
            createdAt = resolveSavedCreatedAt(existingEntry, date)
        )
        GarageMaintenanceEntryStorage.upsertEntry(this, savedEntry)
        originalReceiptImagePath = receiptImagePath
        currentReceiptImagePath = receiptImagePath
        GarageMaintenanceReminderManager.syncReminder(this, savedEntry)
        syncMaintenanceCount()
        GarageMaintenanceReminderManager.evaluateDueRemindersForProfile(this, profileId)

        Toast.makeText(
            this,
            getString(
                if (existingEntry != null) {
                    R.string.garage_maintenance_entry_update_success
                } else {
                    R.string.garage_maintenance_entry_save_success
                }
            ),
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private fun clearErrors() {
        inputDate.error = null
        inputOdometer.error = null
        inputReminderKmValue.error = null
        inputReminderDateInterval.error = null
        inputReminderExactDate.error = null
        updateReminderSummary()
    }

    private fun formatEditableDecimal(value: Double): String {
        if (value == 0.0) {
            return ""
        }

        val formatted = String.format(Locale.getDefault(), "%.2f", value)
        val decimalSeparator = java.text.DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
        return formatted
            .trimEnd('0')
            .trimEnd(decimalSeparator)
    }

    private fun shouldResetReminderState(
        existingEntry: GarageMaintenanceEntry?,
        date: String,
        odometerKm: Long,
        reminderEnabled: Boolean,
        reminderKmInterval: Long?,
        reminderExactKm: Long?,
        reminderKmLeadKm: Long?,
        reminderDateIntervalMonths: Int?,
        reminderExactDateMillis: Long?,
        reminderDateLeadPreset: String?
    ): Boolean {
        if (!reminderEnabled) {
            return true
        }

        if (existingEntry == null) {
            return true
        }

        return existingEntry.date != date ||
            existingEntry.odometerKm != odometerKm ||
            existingEntry.reminderEnabled != reminderEnabled ||
            existingEntry.reminderKmInterval != reminderKmInterval ||
            existingEntry.reminderExactKm != reminderExactKm ||
            existingEntry.reminderKmLeadKm != reminderKmLeadKm ||
            existingEntry.reminderDateIntervalMonths != reminderDateIntervalMonths ||
            existingEntry.reminderExactDateMillis != reminderExactDateMillis ||
            existingEntry.reminderDateLeadPreset != reminderDateLeadPreset
    }

    private fun resolveSavedCreatedAt(existingEntry: GarageMaintenanceEntry?, date: String): Long {
        if (existingEntry != null && existingEntry.date == date) {
            return existingEntry.createdAt
        }
        return selectedDate.timeInMillis
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

    private fun syncMaintenanceCount() {
        val count = GarageMaintenanceEntryStorage.getCount(this, profileId)
        getSharedPreferences(EXTRA_STATS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("profile_${profileId}_maintenance_count", count)
            .apply()
    }

    private fun dpToPx(valueDp: Int): Int {
        return (valueDp * resources.displayMetrics.density).toInt()
    }

    private data class SelectorButtonItem(
        val label: String,
        val isOther: Boolean
    )

    private data class ReminderOptionButtonItem(
        val label: String,
        val isSelected: Boolean,
        val isEnabled: Boolean = true,
        val onClick: () -> Unit
    )

    companion object {
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_ENTRY_ID = "extra_entry_id"
        private const val EXTRA_STATS_PREFS = "garage_profile_extra_stats"
        private const val MAX_SERVICE_BUTTONS_PER_ROW = 3
        private const val MAX_REMINDER_MODE_BUTTONS_PER_ROW = 3
        private const val MAX_KM_LEAD_BUTTONS_PER_ROW = 4
        private const val MAX_DATE_LEAD_BUTTONS_PER_ROW = 2
        private const val DEFAULT_REMINDER_DATE_INTERVAL_MONTHS = 12
        private const val RECEIPT_CAMERA_TEMP_DIR = "maintenance_receipts_camera"

        fun createIntent(context: Context, profileId: Long, entryId: Long = -1L): Intent {
            return Intent(context, GarageMaintenanceEntryActivity::class.java).apply {
                putExtra(EXTRA_PROFILE_ID, profileId)
                if (entryId != -1L) {
                    putExtra(EXTRA_ENTRY_ID, entryId)
                }
            }
        }
    }

    override fun onDestroy() {
        cleanupPendingCameraCapture()
        temporaryReceiptImagePaths.toList().forEach { tempPath ->
            GarageMaintenanceReceiptStorage.deleteReceipt(this, tempPath)
        }
        temporaryReceiptImagePaths.clear()
        super.onDestroy()
    }
}