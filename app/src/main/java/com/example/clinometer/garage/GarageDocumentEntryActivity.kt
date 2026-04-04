package com.example.clinometer.garage

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.clinometer.FullScreenImageActivity
import com.example.clinometer.R
import com.example.clinometer.applySystemBarsPaddingToRoot
import com.example.clinometer.data.GarageDocumentEntry
import com.example.clinometer.data.GarageDocumentEntryStorage
import com.example.clinometer.data.GarageOdometerConflict
import com.example.clinometer.data.GarageOdometerSource
import com.example.clinometer.data.GarageOdometerTimeline
import com.example.clinometer.data.GarageDocumentReceiptStorage
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
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GarageDocumentEntryActivity : AppCompatActivity() {

    private var profileId: Long = -1L
    private var editingEntryId: Long = -1L
    private var draftEntryId: Long = System.currentTimeMillis()
    private var editingEntry: GarageDocumentEntry? = null
    private var originalReceiptImagePath: String? = null
    private var currentReceiptImagePath: String? = null
    private var pendingCameraImageUri: Uri? = null
    private var pendingCameraCaptureFile: File? = null
    private val temporaryReceiptImagePaths = mutableSetOf<String>()
    private val documentTypeOptions = mutableListOf(
        "Fine",
        "Parking Fee",
        "Insurance",
        "Technical Inspection",
        "Tax",
        "Complex Insurance",
        "Vehicle Registration"
    )
    private var selectedDocumentType: String? = null
    private val selectedIssueDate = Calendar.getInstance()
    private var selectedReminderExactDateMillis: Long? = null
    private var reminderDateMode = GarageReminderMode.OFF
    private var selectedReminderDateLeadOption: GarageReminderDateLeadOption? = null
    private var isUpdatingReminderSwitch = false

    private lateinit var btnBack: MaterialButton
    private lateinit var btnCompleteReminder: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnReceiptImage: MaterialButton
    private lateinit var tvTitle: TextView
    private lateinit var llDocumentTypeRows: LinearLayout
    private lateinit var inputDate: TextInputLayout
    private lateinit var inputAmount: TextInputLayout
    private lateinit var inputOdometer: TextInputLayout
    private lateinit var switchReminder: SwitchMaterial
    private lateinit var llReminderConfig: LinearLayout
    private lateinit var llReminderDateModeRows: LinearLayout
    private lateinit var llReminderDateDetails: LinearLayout
    private lateinit var llReminderDateLeadRows: LinearLayout
    private lateinit var inputReminderDateInterval: TextInputLayout
    private lateinit var inputReminderExactDate: TextInputLayout
    private lateinit var tvReminderSummary: TextView
    private lateinit var cardReceiptPreview: MaterialCardView
    private lateinit var ivReceiptPreview: ImageView
    private lateinit var etDate: TextInputEditText
    private lateinit var etAmount: TextInputEditText
    private lateinit var etOdometer: TextInputEditText
    private lateinit var etReminderDateInterval: TextInputEditText
    private lateinit var etReminderExactDate: TextInputEditText

    private val dateFormatter by lazy {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    private val reminderDateFormatter by lazy {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
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
                getString(R.string.garage_document_entry_receipt_camera_permission_denied),
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
                getString(R.string.garage_document_entry_reminder_permission_denied),
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
        setContentView(R.layout.activity_garage_document_entry)
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
        setupDateInputs()
        prefillDefaults()
        setupClickListeners()
        prefillEntryForEditing()
        if (isFinishing) {
            return
        }
        updateEntryModeUi()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBackFromDocumentEntry)
        btnCompleteReminder = findViewById(R.id.btnCompleteDocumentReminder)
        btnCancel = findViewById(R.id.btnCancelDocumentEntry)
        btnSave = findViewById(R.id.btnSaveDocumentEntry)
        btnReceiptImage = findViewById(R.id.btnDocumentEntryReceiptImage)
        tvTitle = findViewById(R.id.tvDocumentEntryTitle)
        llDocumentTypeRows = findViewById(R.id.llDocumentEntryTypeRows)
        inputDate = findViewById(R.id.inputDocumentEntryDate)
        inputAmount = findViewById(R.id.inputDocumentEntryAmount)
        inputOdometer = findViewById(R.id.inputDocumentEntryOdometer)
        switchReminder = findViewById(R.id.switchDocumentEntryReminder)
        llReminderConfig = findViewById(R.id.llDocumentEntryReminderConfig)
        llReminderDateModeRows = findViewById(R.id.llDocumentEntryReminderDateModeRows)
        llReminderDateDetails = findViewById(R.id.llDocumentEntryReminderDateDetails)
        llReminderDateLeadRows = findViewById(R.id.llDocumentEntryReminderDateLeadRows)
        inputReminderDateInterval = findViewById(R.id.inputDocumentEntryReminderDateInterval)
        inputReminderExactDate = findViewById(R.id.inputDocumentEntryReminderExactDate)
        tvReminderSummary = findViewById(R.id.tvDocumentEntryReminderSummary)
        cardReceiptPreview = findViewById(R.id.cardDocumentEntryReceiptPreview)
        ivReceiptPreview = findViewById(R.id.ivDocumentEntryReceiptPreview)
        etDate = findViewById(R.id.etDocumentEntryDate)
        etAmount = findViewById(R.id.etDocumentEntryAmount)
        etOdometer = findViewById(R.id.etDocumentEntryOdometer)
        etReminderDateInterval = findViewById(R.id.etDocumentEntryReminderDateInterval)
        etReminderExactDate = findViewById(R.id.etDocumentEntryReminderExactDate)

        tvTitle.text = getString(R.string.garage_document_entry_title)
    }

    private fun setupDateInputs() {
        etDate.setOnClickListener { showIssueDatePicker() }
        inputDate.setEndIconOnClickListener { showIssueDatePicker() }
        etReminderExactDate.setOnClickListener { showReminderExactDatePicker() }
        inputReminderExactDate.setEndIconOnClickListener {
            if (selectedReminderExactDateMillis != null) {
                clearReminderExactDate()
            } else {
                showReminderExactDatePicker()
            }
        }
    }

    private fun prefillDefaults() {
        selectedDocumentType = documentTypeOptions.firstOrNull()
        etDate.setText(dateFormatter.format(selectedIssueDate.time))
    }

    private fun prefillEntryForEditing() {
        if (editingEntryId == -1L) {
            return
        }

        val entry = GarageDocumentEntryStorage.findEntry(this, profileId, editingEntryId)
        if (entry == null) {
            Toast.makeText(this, getString(R.string.garage_document_entry_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        editingEntry = entry
        bindEntryToForm(entry)
    }

    private fun bindEntryToForm(entry: GarageDocumentEntry) {
        if (entry.documentType.isNotBlank() && documentTypeOptions.none { it.equals(entry.documentType, ignoreCase = true) }) {
            documentTypeOptions.add(entry.documentType)
        }

        selectedDocumentType = entry.documentType.ifBlank { documentTypeOptions.firstOrNull() }
        etDate.setText(entry.date)
        etAmount.setText(formatEditableDecimal(entry.amount))
        etOdometer.setText(entry.odometerKm.takeIf { it > 0L }?.toString().orEmpty())
        originalReceiptImagePath = entry.imagePath
        currentReceiptImagePath = entry.imagePath
        reminderDateMode = when {
            entry.reminderExactDateMillis != null -> GarageReminderMode.EXACT
            entry.reminderDateIntervalMonths != null -> GarageReminderMode.INTERVAL
            entry.reminderEnabled && entry.expiryDateMillis != null -> GarageReminderMode.EXACT
            else -> GarageReminderMode.OFF
        }
        etReminderDateInterval.setText(entry.reminderDateIntervalMonths?.toString().orEmpty())
        selectedReminderExactDateMillis = entry.reminderExactDateMillis ?: if (
            entry.reminderEnabled && entry.reminderDateIntervalMonths == null
        ) {
            entry.expiryDateMillis
        } else {
            null
        }
        selectedReminderDateLeadOption = GarageReminderDateLeadOption.fromStorageKey(entry.reminderDateLeadPreset)
        setReminderSwitchChecked(entry.reminderEnabled)

        parseDate(entry.date)?.let { parsedDate ->
            selectedIssueDate.time = parsedDate
        }
    }

    private fun updateEntryModeUi() {
        val isReadOnly = isCompletedReadOnlyEntry()
        tvTitle.text = when {
            editingEntry == null -> getString(R.string.garage_document_entry_title)
            isReadOnly -> getString(R.string.garage_document_entry_view_title)
            else -> getString(R.string.garage_document_entry_edit_title)
        }
        btnCancel.text = getString(
            if (isReadOnly) {
                R.string.garage_maintenance_entry_close
            } else {
                R.string.garage_fuel_entry_cancel
            }
        )
        applyReadOnlyState(isReadOnly)
        renderDocumentTypeButtons()
        updateReceiptPreview()
        updateReminderUi()
        updateCompleteReminderAction()
    }

    private fun isCompletedReadOnlyEntry(): Boolean {
        return editingEntry?.reminderCompletedAt != null
    }

    private fun applyReadOnlyState(isReadOnly: Boolean) {
        inputAmount.isEnabled = !isReadOnly
        inputOdometer.isEnabled = !isReadOnly
        inputDate.isEnabled = !isReadOnly
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
        btnSave.setOnClickListener { saveDocumentEntry() }
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
        etAmount.addTextChangedListener {
            inputAmount.error = null
        }
        etOdometer.addTextChangedListener(afterTextChanged = {
            val typedValue = it?.toString().orEmpty()
            inputOdometer.error = if (typedValue.isBlank()) {
                null
            } else {
                resolveOdometerSequenceError(parseWholeNumber(typedValue))
            }
        })
        etReminderDateInterval.addTextChangedListener {
            inputReminderDateInterval.error = null
            updateReminderUi()
        }
    }

    private fun renderDocumentTypeButtons() {
        val items = buildList {
            documentTypeOptions.forEach { add(SelectorButtonItem(label = it, isOther = false)) }
            add(SelectorButtonItem(label = getString(R.string.garage_document_entry_other_button), isOther = true))
        }

        renderSelectorButtons(llDocumentTypeRows, items, MAX_TYPE_BUTTONS_PER_ROW, ::createDocumentTypeButton)
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

    private fun createDocumentTypeButton(item: SelectorButtonItem): MaterialButton {
        val isReadOnly = isCompletedReadOnlyEntry()
        return createSelectorButtonBase(item.label).apply {
            if (item.isOther) {
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@GarageDocumentEntryActivity, R.color.dark_surface))
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@GarageDocumentEntryActivity, R.color.stroke_dark))
                setTextColor(ContextCompat.getColor(this@GarageDocumentEntryActivity, R.color.text_secondary))
                isEnabled = !isReadOnly
                alpha = if (isReadOnly) 0.45f else 1f
                if (!isReadOnly) {
                    setOnClickListener { showAddCustomDocumentTypeDialog() }
                }
            } else {
                resolveDocumentTypeIconRes(item.label)?.let { iconRes ->
                    setIconResource(iconRes)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    iconPadding = dpToPx(6)
                    iconSize = dpToPx(14)
                }
                applySelectorButtonStyle(this, item.label == selectedDocumentType)
                isEnabled = !isReadOnly
                alpha = if (isReadOnly) 0.55f else 1f
                if (!isReadOnly) {
                    setOnClickListener {
                        selectedDocumentType = item.label
                        renderDocumentTypeButtons()
                    }
                }
            }
        }
    }

    private fun resolveDocumentTypeIconRes(label: String): Int? {
        return GarageDocumentTypeIcons.resolveIconRes(label)
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

    private fun showAddCustomDocumentTypeDialog() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_fuel_station, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputDialogCustomStation)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.etDialogCustomStation)
        val hint = getString(R.string.garage_document_entry_other_dialog_hint)

        inputLayout.hint = hint
        editText.hint = hint
        editText.addTextChangedListener {
            inputLayout.error = null
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.garage_document_entry_other_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.garage_document_entry_other_dialog_add, null)
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
                    inputLayout.error = getString(R.string.garage_document_entry_other_dialog_error)
                    return@setOnClickListener
                }

                val existingLabel = documentTypeOptions.firstOrNull { it.equals(customType, ignoreCase = true) }
                selectedDocumentType = existingLabel ?: customType
                if (existingLabel == null) {
                    documentTypeOptions.add(selectedDocumentType.orEmpty())
                }
                renderDocumentTypeButtons()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showIssueDatePicker() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedIssueDate.set(Calendar.YEAR, year)
                selectedIssueDate.set(Calendar.MONTH, month)
                selectedIssueDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                selectedIssueDate.set(Calendar.HOUR_OF_DAY, 0)
                selectedIssueDate.set(Calendar.MINUTE, 0)
                selectedIssueDate.set(Calendar.SECOND, 0)
                selectedIssueDate.set(Calendar.MILLISECOND, 0)
                etDate.setText(dateFormatter.format(selectedIssueDate.time))
                inputDate.error = null
                inputOdometer.error = resolveOdometerSequenceError(parseWholeNumber(etOdometer.text?.toString()))
                inputReminderExactDate.error = validateReminderExactDate(selectedReminderExactDateMillis)
                updateReminderUi()
            },
            selectedIssueDate.get(Calendar.YEAR),
            selectedIssueDate.get(Calendar.MONTH),
            selectedIssueDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showReminderExactDatePicker() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        val initialCalendar = Calendar.getInstance().apply {
            timeInMillis = selectedReminderExactDateMillis ?: selectedIssueDate.timeInMillis
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val reminderCalendar = Calendar.getInstance().apply {
                    timeInMillis = selectedReminderExactDateMillis ?: selectedIssueDate.timeInMillis
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
                source = GarageOdometerSource.DOCUMENT,
                entryId = draftEntryId,
                odometerKm = odometerKm
            )
        } else {
            null
        } ?: GarageOdometerTimeline.resolveConflict(
            context = this,
            profileId = profileId,
            source = GarageOdometerSource.DOCUMENT,
            entryId = editingEntry?.id ?: draftEntryId,
            odometerKm = odometerKm,
            dateText = etDate.text?.toString(),
            fallbackTimestamp = editingEntry?.createdAt ?: selectedIssueDate.timeInMillis
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

    private fun showReceiptImageSourceDialog() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        val options = arrayOf(
            getString(R.string.garage_document_entry_receipt_source_camera),
            getString(R.string.garage_document_entry_receipt_source_gallery)
        )

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.garage_document_entry_receipt_source_title)
            .setAdapter(createWhiteTextDialogAdapter(options)) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchReceiptCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }

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

    private fun launchReceiptCamera() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        cleanupPendingCameraCapture()

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) || cameraIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, getString(R.string.garage_document_entry_receipt_camera_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val captureFile = createReceiptCameraCaptureFile()
        if (captureFile == null) {
            Toast.makeText(this, getString(R.string.garage_document_entry_receipt_camera_error), Toast.LENGTH_SHORT).show()
            return
        }

        val captureUri = runCatching {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", captureFile)
        }.getOrNull()

        if (captureUri == null) {
            captureFile.delete()
            Toast.makeText(this, getString(R.string.garage_document_entry_receipt_camera_error), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.garage_document_entry_receipt_camera_error), Toast.LENGTH_SHORT).show()
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
                "document_receipt_${profileId}_${draftEntryId}_",
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
                GarageDocumentReceiptStorage.saveTempReceipt(
                    context = this@GarageDocumentEntryActivity,
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
                    this@GarageDocumentEntryActivity,
                    getString(R.string.garage_document_entry_receipt_error),
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
                this@GarageDocumentEntryActivity,
                getString(R.string.garage_document_entry_receipt_added),
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
        val receiptFile = GarageDocumentReceiptStorage.resolveReceiptFile(this, currentReceiptImagePath)
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
            getString(R.string.garage_document_entry_receipt_deleted),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateReceiptPreview() {
        val receiptFile = GarageDocumentReceiptStorage.resolveReceiptFile(this, currentReceiptImagePath)
        val hasImage = receiptFile?.exists() == true

        if (!hasImage) {
            if (currentReceiptImagePath == originalReceiptImagePath) {
                originalReceiptImagePath = null
            }
            currentReceiptImagePath = null
            cardReceiptPreview.visibility = View.GONE
            ivReceiptPreview.setImageDrawable(null)
            btnReceiptImage.text = getString(R.string.garage_document_entry_receipt_add)
            return
        }

        ivReceiptPreview.setImageBitmap(BitmapFactory.decodeFile(receiptFile!!.absolutePath))
        cardReceiptPreview.visibility = View.VISIBLE
        btnReceiptImage.text = getString(R.string.garage_document_entry_receipt_change)
    }

    private fun resolveReceiptImageForSave(entryId: Long): String? {
        val currentPath = currentReceiptImagePath?.trim().orEmpty()
        val originalPath = originalReceiptImagePath?.trim().orEmpty()

        if (currentPath.isEmpty()) {
            if (originalPath.isNotEmpty()) {
                GarageDocumentReceiptStorage.deleteReceipt(this, originalPath)
            }
            return null
        }

        if (currentPath == originalPath) {
            return currentPath
        }

        val finalPath = GarageDocumentReceiptStorage.promoteTempReceipt(this, currentPath, profileId, entryId)
        if (finalPath != null) {
            temporaryReceiptImagePaths.remove(currentPath)
        }

        if (finalPath != null && originalPath.isNotEmpty() && originalPath != finalPath) {
            GarageDocumentReceiptStorage.deleteReceipt(this, originalPath)
        }

        return finalPath
    }

    private fun deleteTemporaryReceiptImage(relativePath: String) {
        temporaryReceiptImagePaths.remove(relativePath)
        GarageDocumentReceiptStorage.deleteReceipt(this, relativePath)
    }

    private fun createWhiteTextDialogAdapter(options: Array<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            options
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.setTextColor(ContextCompat.getColor(this@GarageDocumentEntryActivity, R.color.white))
                return view
            }
        }
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

        llReminderDateDetails.visibility = if (reminderDateMode == GarageReminderMode.OFF) View.GONE else View.VISIBLE
        inputReminderDateInterval.visibility = if (reminderDateMode == GarageReminderMode.INTERVAL) View.VISIBLE else View.GONE
        inputReminderExactDate.visibility = if (reminderDateMode == GarageReminderMode.EXACT) View.VISIBLE else View.GONE
        inputReminderExactDate.setEndIconDrawable(
            if (selectedReminderExactDateMillis != null) {
                android.R.drawable.ic_menu_close_clear_cancel
            } else {
                android.R.drawable.ic_menu_today
            }
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

    private fun canMarkReminderCompleted(entry: GarageDocumentEntry): Boolean {
        if (!entry.reminderEnabled || entry.reminderCompletedAt != null) {
            return false
        }

        val issueTimestamp = GarageOdometerTimeline.resolveReferenceTimestamp(entry.date, entry.createdAt)
        val targetDateMillis = GarageDocumentReminderRules.resolveTargetDate(entry, issueTimestamp)
        return targetDateMillis != null && System.currentTimeMillis() >= targetDateMillis
    }

    private fun confirmCompleteReminder() {
        val entry = editingEntry ?: return
        if (!canMarkReminderCompleted(entry)) {
            return
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.garage_document_entry_complete_reminder_title)
            .setMessage(
                getString(
                    R.string.garage_document_entry_complete_reminder_message,
                    entry.documentType.ifBlank { getString(R.string.garage_document_entry_title) }
                )
            )
            .setPositiveButton(R.string.garage_document_entry_complete_reminder_confirm) { _, _ ->
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

    private fun completeReminder(entry: GarageDocumentEntry) {
        val updatedEntry = GarageDocumentReminderManager.markReminderCompleted(this, entry)
        editingEntry = updatedEntry
        bindEntryToForm(updatedEntry)
        updateEntryModeUi()

        Toast.makeText(
            this,
            getString(R.string.garage_document_entry_complete_reminder_success),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun renderReminderModeButtons() {
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
        val isEnabled = item.isEnabled
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
        if (selectedReminderDateLeadOption != null && !isDateLeadSelectable(selectedReminderDateLeadOption)) {
            selectedReminderDateLeadOption = null
        }
    }

    private fun isDateLeadSelectable(leadOption: GarageReminderDateLeadOption?): Boolean {
        return GarageMaintenanceReminderRules.resolveDateReminder(
            serviceTimestamp = selectedIssueDate.timeInMillis,
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

        val targetDateMillis = GarageMaintenanceReminderRules.resolveDateTarget(
            serviceTimestamp = selectedIssueDate.timeInMillis,
            mode = reminderDateMode,
            intervalMonths = parseReminderDateIntervalMonths(),
            exactDateMillis = selectedReminderExactDateMillis
        )
        val reminderDateMillis = GarageMaintenanceReminderRules.resolveDateReminder(
            serviceTimestamp = selectedIssueDate.timeInMillis,
            mode = reminderDateMode,
            intervalMonths = parseReminderDateIntervalMonths(),
            exactDateMillis = selectedReminderExactDateMillis,
            leadOption = selectedReminderDateLeadOption
        )

        if (targetDateMillis == null || reminderDateMillis == null) {
            showReminderSummary(null)
            return
        }

        val summary = getString(
            R.string.garage_maintenance_entry_reminder_summary_date,
            reminderDateFormatter.format(reminderDateMillis),
            reminderDateFormatter.format(targetDateMillis)
        )
        showReminderSummary(summary)
    }

    private fun showReminderSummary(text: String?, isError: Boolean = false) {
        if (text.isNullOrBlank()) {
            tvReminderSummary.text = ""
            tvReminderSummary.visibility = View.GONE
            return
        }

        tvReminderSummary.text = text
        tvReminderSummary.visibility = View.VISIBLE
        tvReminderSummary.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.error_color else R.color.text_tertiary
            )
        )
    }

    private fun validateReminderExactDate(exactDateMillis: Long?): String? {
        if (exactDateMillis == null) {
            return null
        }

        return if (exactDateMillis <= selectedIssueDate.timeInMillis) {
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

    private fun validateReminderConfiguration(): Boolean {
        if (!switchReminder.isChecked) {
            showReminderSummary(null)
            return false
        }

        var hasError = false
        var summaryError: String? = null
        val reminderDateIntervalMonths = parseReminderDateIntervalMonths()

        if (reminderDateMode == GarageReminderMode.OFF) {
            summaryError = getString(R.string.garage_maintenance_entry_reminder_error_required)
            hasError = true
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
                serviceTimestamp = selectedIssueDate.timeInMillis,
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

    private fun clearErrors() {
        inputDate.error = null
        inputAmount.error = null
        inputOdometer.error = null
        inputReminderDateInterval.error = null
        inputReminderExactDate.error = null
        updateReminderSummary()
    }

    private fun saveDocumentEntry() {
        if (isCompletedReadOnlyEntry()) {
            return
        }

        val entryId = if (editingEntryId != -1L) editingEntryId else draftEntryId
        val existingEntry = editingEntry
        val issueDate = etDate.text?.toString()?.trim().orEmpty()
        val documentType = selectedDocumentType?.trim().orEmpty()
        val rawAmount = etAmount.text?.toString()?.trim().orEmpty()
        val odometerKm = parseWholeNumber(etOdometer.text?.toString())
        val reminderEnabled = switchReminder.isChecked
        val amount = if (rawAmount.isBlank()) 0.0 else parseDecimal(rawAmount)
        val reminderDateIntervalMonths = if (reminderEnabled && reminderDateMode == GarageReminderMode.INTERVAL) {
            parseReminderDateIntervalMonths()
        } else {
            null
        }
        val reminderExactDateMillis = if (reminderEnabled && reminderDateMode == GarageReminderMode.EXACT) {
            selectedReminderExactDateMillis
        } else {
            null
        }

        clearErrors()

        var hasError = false
        if (issueDate.isBlank()) {
            inputDate.error = getString(R.string.garage_document_entry_error_date)
            hasError = true
        }

        if (documentType.isBlank()) {
            Toast.makeText(this, getString(R.string.garage_document_entry_error_type), Toast.LENGTH_SHORT).show()
            hasError = true
        }

        if (rawAmount.isNotBlank() && amount == null) {
            inputAmount.error = getString(R.string.garage_document_entry_error_amount)
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

        if (reminderEnabled) {
            if (!hasNotificationPermission()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                hasError = true
            }
            hasError = validateReminderConfiguration() || hasError
        }

        if (hasError || odometerKm == null) {
            return
        }

        val resolvedReminderLeadPreset = if (reminderEnabled) selectedReminderDateLeadOption?.storageKey else null
        val shouldResetReminderState = shouldResetReminderState(
            existingEntry = existingEntry,
            date = issueDate,
            reminderEnabled = reminderEnabled,
            reminderDateIntervalMonths = reminderDateIntervalMonths,
            reminderExactDateMillis = reminderExactDateMillis,
            reminderDateLeadPreset = resolvedReminderLeadPreset
        )

        val receiptImagePath = resolveReceiptImageForSave(entryId)
        if (currentReceiptImagePath?.isNotBlank() == true && currentReceiptImagePath != originalReceiptImagePath && receiptImagePath == null) {
            Toast.makeText(this, getString(R.string.garage_document_entry_receipt_error), Toast.LENGTH_SHORT).show()
            return
        }

        val savedEntry = GarageDocumentEntry(
            id = entryId,
            profileId = profileId,
            date = issueDate,
            documentType = documentType,
            description = existingEntry?.description.orEmpty(),
            amount = amount ?: 0.0,
            odometerKm = odometerKm,
            expiryDateMillis = null,
            imagePath = receiptImagePath,
            reminderEnabled = reminderEnabled,
            reminderDateIntervalMonths = reminderDateIntervalMonths,
            reminderExactDateMillis = reminderExactDateMillis,
            reminderDateLeadPreset = resolvedReminderLeadPreset,
            reminderTriggeredAt = if (shouldResetReminderState) null else existingEntry?.reminderTriggeredAt,
            reminderCompletedAt = if (shouldResetReminderState) null else existingEntry?.reminderCompletedAt,
            createdAt = resolveSavedCreatedAt(existingEntry, issueDate)
        )
        GarageDocumentEntryStorage.upsertEntry(this, savedEntry)
        originalReceiptImagePath = receiptImagePath
        currentReceiptImagePath = receiptImagePath
        GarageDocumentReminderManager.syncReminder(this, savedEntry)
        syncDocumentCount()
        GarageDocumentReminderManager.evaluateDueRemindersForProfile(this, profileId)

        Toast.makeText(
            this,
            getString(
                if (existingEntry != null) {
                    R.string.garage_document_entry_update_success
                } else {
                    R.string.garage_document_entry_save_success
                }
            ),
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private fun shouldResetReminderState(
        existingEntry: GarageDocumentEntry?,
        date: String,
        reminderEnabled: Boolean,
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

        val existingEffectiveExactDateMillis = existingEntry.reminderExactDateMillis ?: if (
            existingEntry.reminderEnabled && existingEntry.reminderDateIntervalMonths == null
        ) {
            existingEntry.expiryDateMillis
        } else {
            null
        }

        return existingEntry.date != date ||
            existingEntry.reminderEnabled != reminderEnabled ||
            existingEntry.reminderDateIntervalMonths != reminderDateIntervalMonths ||
            existingEffectiveExactDateMillis != reminderExactDateMillis ||
            existingEntry.reminderDateLeadPreset != reminderDateLeadPreset
    }

    private fun resolveSavedCreatedAt(existingEntry: GarageDocumentEntry?, date: String): Long {
        if (existingEntry != null && existingEntry.date == date) {
            return existingEntry.createdAt
        }
        return selectedIssueDate.timeInMillis
    }

    private fun parseDate(rawDate: String): Date? {
        val value = rawDate.trim()
        if (value.isBlank()) {
            return null
        }

        val locales = linkedSetOf(Locale.getDefault(), Locale.ENGLISH, Locale.US, Locale("bg"))
        locales.forEach { locale ->
            val parser = SimpleDateFormat("dd MMM yyyy", locale).apply {
                isLenient = false
            }
            val parsed = runCatching { parser.parse(value) }.getOrNull()
            if (parsed != null) {
                return parsed
            }
        }
        return null
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
            ?.takeIf { it.isNotEmpty() }
            ?.toLongOrNull()
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

    private fun syncDocumentCount() {
        val count = GarageDocumentEntryStorage.getCount(this, profileId)
        getSharedPreferences(EXTRA_STATS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("profile_${profileId}_documents_count", count)
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
        private const val MAX_TYPE_BUTTONS_PER_ROW = 2
        private const val MAX_REMINDER_MODE_BUTTONS_PER_ROW = 3
        private const val MAX_DATE_LEAD_BUTTONS_PER_ROW = 2
        private const val DEFAULT_REMINDER_DATE_INTERVAL_MONTHS = 12
        private const val RECEIPT_CAMERA_TEMP_DIR = "document_receipts_camera"

        fun createIntent(context: Context, profileId: Long, entryId: Long = -1L): Intent {
            return Intent(context, GarageDocumentEntryActivity::class.java).apply {
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
            GarageDocumentReceiptStorage.deleteReceipt(this, tempPath)
        }
        temporaryReceiptImagePaths.clear()
        super.onDestroy()
    }
}