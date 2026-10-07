package com.revix.app.garage

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
import android.widget.FrameLayout
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
import com.revix.app.FullScreenImageActivity
import com.revix.app.R
import com.revix.app.applySystemBarsPaddingToRoot
import com.revix.app.data.GarageMaintenanceEntry
import com.revix.app.data.GarageMaintenanceEntryStorage
import com.revix.app.data.GarageOdometerConflict
import com.revix.app.data.GarageOdometerSource
import com.revix.app.data.GarageOdometerTimeline
import com.revix.app.data.GarageReceiptImagePathCodec
import com.revix.app.data.GarageMaintenanceReceiptStorage
import com.revix.app.data.ProfileStorage
import com.revix.app.settings.LanguageManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GarageMaintenanceEntryActivity : AppCompatActivity() {

    private var profileId: Long = -1L
    private var editingEntryId: Long = -1L
    private var draftEntryId: Long = System.currentTimeMillis()
    private var editingEntry: GarageMaintenanceEntry? = null
    private var showDueActionsOnStart: Boolean = false
    private var finishAfterDismissReminder: Boolean = false
    private val originalReceiptImagePaths = mutableListOf<String>()
    private val currentReceiptImagePaths = mutableListOf<String>()
    private var lastOpenedReceiptPreviewIndex: Int = -1
    private var deleteArmedReceiptIndex: Int = -1
    private var pendingCameraImageUri: Uri? = null
    private var pendingCameraCaptureFile: File? = null
    private val temporaryReceiptImagePaths = mutableSetOf<String>()
    private val serviceTypeOptions = MaintenanceServiceTypes.defaultTypes.toMutableList()
    private var selectedServiceType: String? = null

    private lateinit var btnBack: View
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
    private lateinit var llReminderDateModeRows: LinearLayout
    private lateinit var llReminderDateDetails: LinearLayout
    private lateinit var inputReminderKmValue: TextInputLayout
    private lateinit var inputReminderDateInterval: TextInputLayout
    private lateinit var inputReminderExactDate: TextInputLayout
    private lateinit var tvCalculatedTotalAmount: TextView
    private lateinit var tvReminderSummary: TextView
    private lateinit var cardReceiptPreview: MaterialCardView
    private lateinit var llReceiptPreviewContainer: LinearLayout
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
    private val dateFormatter: SimpleDateFormat
        get() = GarageDateFormatter.createDateTimeFormatter(this)
    private val reminderDateFormatter: SimpleDateFormat
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
        showDueActionsOnStart = intent.getBooleanExtra(EXTRA_SHOW_DUE_ACTIONS, false)
        finishAfterDismissReminder = showDueActionsOnStart
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
        if (savedInstanceState == null && showDueActionsOnStart) {
            window.decorView.post { maybeShowDueReminderActions() }
        }
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
        inputOdometer.suffixText = com.revix.app.settings.UnitsManager.getOdometerUnitSymbol(this)
        inputDate = findViewById(R.id.inputMaintenanceEntryDate)
        inputDescription = findViewById(R.id.inputMaintenanceEntryDescription)
        switchReminder = findViewById(R.id.switchMaintenanceEntryReminder)
        llReminderConfig = findViewById(R.id.llMaintenanceEntryReminderConfig)
        llReminderKmModeRows = findViewById(R.id.llMaintenanceEntryReminderKmModeRows)
        llReminderKmDetails = findViewById(R.id.llMaintenanceEntryReminderKmDetails)
        llReminderDateModeRows = findViewById(R.id.llMaintenanceEntryReminderDateModeRows)
        llReminderDateDetails = findViewById(R.id.llMaintenanceEntryReminderDateDetails)
        inputReminderKmValue = findViewById(R.id.inputMaintenanceEntryReminderKmValue)
        inputReminderKmValue.suffixText = com.revix.app.settings.UnitsManager.getOdometerUnitSymbol(this)
        inputReminderDateInterval = findViewById(R.id.inputMaintenanceEntryReminderDateInterval)
        inputReminderExactDate = findViewById(R.id.inputMaintenanceEntryReminderExactDate)
        tvCalculatedTotalAmount = findViewById(R.id.tvMaintenanceEntryCalculatedTotalAmount)
        tvReminderSummary = findViewById(R.id.tvMaintenanceEntryReminderSummary)
        cardReceiptPreview = findViewById(R.id.cardMaintenanceEntryReceiptPreview)
        llReceiptPreviewContainer = findViewById(R.id.llMaintenanceEntryReceiptPreviewContainer)
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
        etDate.setText(GarageDateFormatter.formatDateTime(this, entry.date, entry.createdAt))
        etPartsCost.setText(formatEditableDecimal(entry.partsCost))
        etLaborCost.setText(formatEditableDecimal(entry.laborCost))
        etOdometer.setText(com.revix.app.settings.UnitsManager.kmToOdometerValue(entry.odometerKm, this).toString())
        etDescription.setText(entry.description)
        val decodedReceiptPaths = GarageReceiptImagePathCodec.decode(entry.receiptImagePath)
        originalReceiptImagePaths.clear()
        originalReceiptImagePaths.addAll(decodedReceiptPaths)
        currentReceiptImagePaths.clear()
        currentReceiptImagePaths.addAll(decodedReceiptPaths)

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
        etReminderKmValue.setText((entry.reminderExactKm ?: entry.reminderKmInterval)?.let { com.revix.app.settings.UnitsManager.kmToOdometerValue(it, this).toString() }.orEmpty())
        etReminderDateInterval.setText(entry.reminderDateIntervalMonths?.toString().orEmpty())
        selectedReminderExactDateMillis = entry.reminderExactDateMillis
        selectedReminderKmLeadKm = null
        selectedReminderDateLeadOption = null
        setReminderSwitchChecked(entry.reminderEnabled)

        selectedDate.time = Date(GarageDateFormatter.resolveTimestamp(this, entry.date, entry.createdAt))
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
        btnCompleteReminder.setOnClickListener { maybeShowDueReminderActions() }
        btnCancel.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveMaintenanceEntry() }
        btnReceiptImage.setOnClickListener { showReceiptImageSourceDialog() }
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
                resolveOdometerSequenceError(readOdometerInputKm())
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
            setPaddingRelative(dpToPx(8), 0, dpToPx(6), 0)
            text = label
            textSize = 12f
            setSingleLine(true)
        }
    }

    private fun createServiceTypeButton(item: SelectorButtonItem): MaterialButton {
        val isReadOnly = isCompletedReadOnlyEntry()
        val displayLabel = if (item.isOther) {
            item.label
        } else {
            MaintenanceServiceTypes.localizedLabel(this, item.label)
        }
        return createSelectorButtonBase(displayLabel).apply {
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
                    iconPadding = dpToPx(4)
                    iconSize = dpToPx(13)
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

    private fun importReceiptImages(uris: List<Uri>, cleanupCapturedCameraFile: Boolean = false) {
        if (isCompletedReadOnlyEntry()) {
            if (cleanupCapturedCameraFile) {
                cleanupPendingCameraCapture()
            }
            return
        }

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
                    GarageMaintenanceReceiptStorage.saveTempReceipt(
                        context = this@GarageMaintenanceEntryActivity,
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
                    this@GarageMaintenanceEntryActivity,
                    getString(R.string.garage_maintenance_entry_receipt_error),
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

    private fun openReceiptPreview(startIndex: Int) {
        if (startIndex !in currentReceiptImagePaths.indices) {
            return
        }

        val absolutePaths = currentReceiptImagePaths.mapNotNull { relativePath ->
            GarageMaintenanceReceiptStorage.resolveReceiptFile(this, relativePath)
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
            putExtra(FullScreenImageActivity.EXTRA_SHOW_DELETE, !isCompletedReadOnlyEntry())
        }
        receiptPreviewLauncher.launch(intent)
    }

    private fun removeReceiptImageAt(index: Int) {
        if (isCompletedReadOnlyEntry()) {
            return
        }

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
            getString(R.string.garage_maintenance_entry_receipt_deleted),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateReceiptPreview() {
        originalReceiptImagePaths.retainAll { relativePath ->
            GarageMaintenanceReceiptStorage.resolveReceiptFile(this, relativePath)?.exists() == true
        }
        currentReceiptImagePaths.retainAll { relativePath ->
            val exists = GarageMaintenanceReceiptStorage.resolveReceiptFile(this, relativePath)?.exists() == true
            if (!exists) {
                temporaryReceiptImagePaths.remove(relativePath)
            }
            exists
        }
        if (isCompletedReadOnlyEntry() || deleteArmedReceiptIndex !in currentReceiptImagePaths.indices) {
            deleteArmedReceiptIndex = -1
        }
        val hasImages = currentReceiptImagePaths.isNotEmpty()

        if (!hasImages) {
            deleteArmedReceiptIndex = -1
            cardReceiptPreview.visibility = View.GONE
            llReceiptPreviewContainer.removeAllViews()
            btnReceiptImage.text = getString(R.string.garage_maintenance_entry_receipt_add)
            return
        }

        renderReceiptThumbnails()
        cardReceiptPreview.visibility = View.VISIBLE
        btnReceiptImage.text = getString(R.string.garage_maintenance_entry_receipt_add)
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
        val canDeleteReceipts = !isCompletedReadOnlyEntry()

        currentReceiptImagePaths.forEachIndexed { index, relativePath ->
            val receiptFile = GarageMaintenanceReceiptStorage.resolveReceiptFile(this, relativePath)
                ?.takeIf { it.exists() }
                ?: return@forEachIndexed

            val thumbnailCard = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(thumbnailWidthPx, thumbnailHeightPx).apply {
                    marginEnd = if (index == itemCount - 1) 0 else dpToPx(8)
                }
                radius = dpToPx(12).toFloat()
                strokeWidth = dpToPx(1)
                strokeColor = ContextCompat.getColor(this@GarageMaintenanceEntryActivity, R.color.stroke_dark)
                cardElevation = 0f
                setCardBackgroundColor(ContextCompat.getColor(this@GarageMaintenanceEntryActivity, R.color.dark_surface))
                addView(
                    buildReceiptThumbnailView(
                        filePath = receiptFile.absolutePath,
                        index = index,
                        totalCount = itemCount,
                        showDeleteBadge = canDeleteReceipts && deleteArmedReceiptIndex == index,
                        onDeleteRequested = { removeReceiptImageAt(index) }
                    )
                )
                setOnClickListener { openReceiptPreview(index) }
                if (canDeleteReceipts) {
                    setOnLongClickListener {
                        deleteArmedReceiptIndex = if (deleteArmedReceiptIndex == index) -1 else index
                        renderReceiptThumbnails()
                        true
                    }
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
                GarageMaintenanceReceiptStorage.deleteReceipt(this, originalPath)
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

            val promotedPath = GarageMaintenanceReceiptStorage.promoteTempReceipt(this, path, profileId, entryId)
                ?: return null

            temporaryReceiptImagePaths.remove(path)
            promotedPaths.add(promotedPath)
        }

        val promotedPathSet = promotedPaths.toSet()
        normalizedOriginalPaths
            .filter { originalPath -> originalPath !in promotedPathSet }
            .forEach { removedOriginalPath ->
                GarageMaintenanceReceiptStorage.deleteReceipt(this, removedOriginalPath)
            }

        return GarageReceiptImagePathCodec.normalize(promotedPaths)
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
        val conflict = GarageOdometerTimeline.resolveConflict(
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
        val um = com.revix.app.settings.UnitsManager
        return "${um.formatOdometerValue(valueKm, this)} ${um.getOdometerUnitSymbol(this)}"
    }

    // EditText holds the value in the user's unit (km or mi); convert back to the km base.
    private fun readOdometerInputKm(): Long? {
        val entered = parseWholeNumber(etOdometer.text?.toString()) ?: return null
        return com.revix.app.settings.UnitsManager.odometerValueToKm(entered, this)
    }

    // Reminder km interval/exact value is also entered in the user's unit; convert to km base.
    private fun readReminderKmInputKm(): Long? {
        val entered = parseWholeNumber(etReminderKmValue.text?.toString()) ?: return null
        return com.revix.app.settings.UnitsManager.odometerValueToKm(entered, this)
    }

    private fun updateReminderUi() {
        val reminderEnabled = switchReminder.isChecked
        llReminderConfig.visibility = if (reminderEnabled) View.VISIBLE else View.GONE
        if (!reminderEnabled) {
            showReminderSummary(null)
            return
        }

        renderReminderModeButtons()

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
        btnCompleteReminder.visibility = if (
            !isCompletedReadOnlyEntry() &&
            entry != null &&
            canOfferDueReminderActions(entry)
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (btnCompleteReminder.visibility == View.VISIBLE) {
            btnCompleteReminder.setText(R.string.garage_maintenance_due_actions_log)
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

    private fun maybeShowDueReminderActions() {
        val entry = editingEntry ?: return
        if (isCompletedReadOnlyEntry()) {
            return
        }
        if (!canOfferDueReminderActions(entry)) {
            return
        }

        val serviceLabel = MaintenanceServiceTypes.localizedLabel(
            this,
            entry.serviceType.ifBlank { getString(R.string.garage_profile_maintenance_reminder_badge) }
        )
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.garage_maintenance_due_actions_title)
            .setMessage(getString(R.string.garage_maintenance_due_actions_message, serviceLabel))
            .setPositiveButton(R.string.garage_maintenance_due_actions_log) { _, _ ->
                logServiceFromDueReminder(entry)
            }
            .setNeutralButton(R.string.garage_maintenance_due_actions_dismiss) { _, _ ->
                dismissDueReminder(entry)
            }
            .setNegativeButton(R.string.garage_maintenance_due_actions_review, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
        }

        dialog.show()
        showDueActionsOnStart = false
    }

    private fun canOfferDueReminderActions(entry: GarageMaintenanceEntry): Boolean {
        if (!entry.reminderEnabled || entry.reminderCompletedAt != null) {
            return false
        }
        if (entry.reminderTriggeredAt != null || canMarkReminderCompleted(entry)) {
            return true
        }

        val serviceTimestamp = GarageOdometerTimeline.resolveReferenceTimestamp(entry.date, entry.createdAt)
        val targetKm = GarageMaintenanceReminderRules.resolveKmTarget(entry)
        val latestKm = GarageOdometerTimeline.latestRecordedOdometerFrom(
            context = this,
            profileId = entry.profileId,
            source = GarageOdometerSource.MAINTENANCE,
            entryId = entry.id,
            dateText = entry.date,
            fallbackTimestamp = entry.createdAt
        )
        val remainingKm = targetKm?.let { it - (latestKm ?: entry.odometerKm) }
        val targetDateMillis = GarageMaintenanceReminderRules.resolveDateTarget(entry, serviceTimestamp)
        val daysUntil = targetDateMillis?.let { GarageReminderSchedule.daysUntil(it, System.currentTimeMillis()) }
        return GarageReminderSchedule.homePhase(daysUntil, remainingKm) != GarageReminderSchedule.HomePhase.UPCOMING
    }

    private fun logServiceFromDueReminder(sourceEntry: GarageMaintenanceEntry) {
        GarageMaintenanceReminderManager.markReminderCompleted(this, sourceEntry)
        startNewEntryFromReminderTemplate(sourceEntry)
        Toast.makeText(
            this,
            getString(R.string.garage_maintenance_due_actions_logged),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun dismissDueReminder(entry: GarageMaintenanceEntry) {
        GarageMaintenanceReminderManager.markReminderCompleted(this, entry)
        Toast.makeText(
            this,
            getString(R.string.garage_maintenance_entry_complete_reminder_success),
            Toast.LENGTH_SHORT
        ).show()
        if (finishAfterDismissReminder) {
            finish()
            return
        }
        editingEntry = GarageMaintenanceEntryStorage.findEntry(this, profileId, entry.id) ?: entry.copy(
            reminderCompletedAt = System.currentTimeMillis()
        )
        bindEntryToForm(editingEntry!!)
        updateEntryModeUi()
    }

    private fun startNewEntryFromReminderTemplate(sourceEntry: GarageMaintenanceEntry) {
        editingEntryId = -1L
        editingEntry = null
        draftEntryId = System.currentTimeMillis()
        finishAfterDismissReminder = false

        etPartsCost.setText("")
        etLaborCost.setText("")
        etDescription.setText("")
        originalReceiptImagePaths.clear()
        currentReceiptImagePaths.clear()
        temporaryReceiptImagePaths.clear()
        deleteArmedReceiptIndex = -1

        selectedDate.timeInMillis = System.currentTimeMillis()
        etDate.setText(dateFormatter.format(selectedDate.time))

        if (sourceEntry.serviceType.isNotBlank() &&
            serviceTypeOptions.none { it.equals(sourceEntry.serviceType, ignoreCase = true) }
        ) {
            serviceTypeOptions.add(sourceEntry.serviceType)
        }
        selectedServiceType = sourceEntry.serviceType.ifBlank { serviceTypeOptions.firstOrNull() }

        // Prefill current reading as a hint; user must enter a higher value to save.
        val odometerKm = sourceEntry.odometerKm.takeIf { it > 0L }
            ?: GarageOdometerTimeline.latestAddedOdometer(this, profileId)
        etOdometer.setText(
            odometerKm?.let {
                com.revix.app.settings.UnitsManager.kmToOdometerValue(it, this).toString()
            }.orEmpty()
        )

        applyReminderTemplateFrom(sourceEntry)

        updateCalculatedTotalAmount()
        updateEntryModeUi()
    }

    private fun applyReminderTemplateFrom(sourceEntry: GarageMaintenanceEntry) {
        val kmInterval = sourceEntry.reminderKmInterval
            ?: sourceEntry.reminderExactKm
                ?.let { exactKm -> (exactKm - sourceEntry.odometerKm).takeIf { it > 0L } }
        reminderKmMode = if (kmInterval != null) GarageReminderMode.INTERVAL else GarageReminderMode.OFF
        etReminderKmValue.setText(
            kmInterval?.let {
                com.revix.app.settings.UnitsManager.kmToOdometerValue(it, this).toString()
            }.orEmpty()
        )

        val dateMonths = sourceEntry.reminderDateIntervalMonths
            ?: sourceEntry.reminderExactDateMillis?.let { exactMillis ->
                val serviceTimestamp = GarageOdometerTimeline.resolveReferenceTimestamp(
                    sourceEntry.date,
                    sourceEntry.createdAt
                )
                monthsBetween(serviceTimestamp, exactMillis)?.takeIf { it > 0 }
            }
        reminderDateMode = if (dateMonths != null) GarageReminderMode.INTERVAL else GarageReminderMode.OFF
        etReminderDateInterval.setText(dateMonths?.toString().orEmpty())
        selectedReminderExactDateMillis = null
        selectedReminderKmLeadKm = null
        selectedReminderDateLeadOption = null

        setReminderSwitchChecked(sourceEntry.reminderEnabled)
    }

    private fun monthsBetween(fromMillis: Long, toMillis: Long): Int? {
        if (toMillis <= fromMillis) {
            return null
        }
        val from = Calendar.getInstance().apply { timeInMillis = fromMillis }
        val to = Calendar.getInstance().apply { timeInMillis = toMillis }
        val months = (to.get(Calendar.YEAR) - from.get(Calendar.YEAR)) * 12 +
            (to.get(Calendar.MONTH) - from.get(Calendar.MONTH))
        return months.takeIf { it > 0 }
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
        val serviceOdometerKm = readOdometerInputKm() ?: return null
        val targetKm = GarageMaintenanceReminderRules.resolveKmTarget(
            serviceOdometerKm = serviceOdometerKm,
            mode = reminderKmMode,
            value = readReminderKmInputKm()
        ) ?: return null

        return getString(
            R.string.garage_maintenance_entry_reminder_summary_km,
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

        return getString(
            R.string.garage_maintenance_entry_reminder_summary_date,
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

        val serviceKm = readOdometerInputKm() ?: return null
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

        val reminderKmValue = readReminderKmInputKm()
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
            val reminderKmPoint = GarageMaintenanceReminderRules.resolveKmTarget(
                serviceOdometerKm = odometerKm,
                mode = reminderKmMode,
                value = reminderKmValue
            )
            if (reminderKmPoint == null) {
                summaryError = summaryError ?: getString(R.string.garage_maintenance_entry_reminder_error_exact_km)
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
            val reminderDatePoint = GarageMaintenanceReminderRules.resolveDateTarget(
                serviceTimestamp = selectedDate.timeInMillis,
                mode = reminderDateMode,
                intervalMonths = reminderDateIntervalMonths,
                exactDateMillis = selectedReminderExactDateMillis
            )
            if (reminderDatePoint == null) {
                summaryError = summaryError ?: getString(R.string.garage_maintenance_entry_reminder_error_exact_date)
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
        val odometerKm = readOdometerInputKm()
        val description = etDescription.text?.toString()?.trim().orEmpty()
        val reminderEnabled = switchReminder.isChecked
        val reminderKmValue = readReminderKmInputKm()
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
        val resolvedReminderKmLeadKm: Long? = null
        val resolvedReminderDateIntervalMonths = if (reminderEnabled && reminderDateMode == GarageReminderMode.INTERVAL) reminderDateIntervalMonths else null
        val resolvedReminderExactDateMillis = if (reminderEnabled && reminderDateMode == GarageReminderMode.EXACT) reminderExactDateMillis else null
        val resolvedReminderDateLeadPreset: String? = null
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

        val receiptImagePaths = resolveReceiptImagesForSave(entryId)
        if (receiptImagePaths == null) {
            Toast.makeText(this, getString(R.string.garage_maintenance_entry_receipt_error), Toast.LENGTH_SHORT).show()
            return
        }
        val receiptImagePath = GarageReceiptImagePathCodec.encode(receiptImagePaths)

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
            reminderNotifiedStages = if (shouldResetReminderState) null else existingEntry?.reminderNotifiedStages,
            reminderTriggeredAt = if (shouldResetReminderState) null else existingEntry?.reminderTriggeredAt,
            reminderTriggeredBy = if (shouldResetReminderState) null else existingEntry?.reminderTriggeredBy,
            reminderCompletedAt = if (shouldResetReminderState) null else existingEntry?.reminderCompletedAt,
            createdAt = resolveSavedCreatedAt(existingEntry, date)
        )
        GarageMaintenanceEntryStorage.upsertEntry(this, savedEntry)
        originalReceiptImagePaths.clear()
        originalReceiptImagePaths.addAll(receiptImagePaths)
        currentReceiptImagePaths.clear()
        currentReceiptImagePaths.addAll(receiptImagePaths)
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
        const val EXTRA_SHOW_DUE_ACTIONS = "extra_show_due_actions"
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

        fun createDueReminderIntent(context: Context, profileId: Long, entryId: Long): Intent {
            return createIntent(context, profileId, entryId).apply {
                putExtra(EXTRA_SHOW_DUE_ACTIONS, true)
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