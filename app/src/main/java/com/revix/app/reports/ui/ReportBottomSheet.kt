package com.revix.app.reports.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.revix.app.R
import com.revix.app.reports.data.PoliceReport
import com.revix.app.reports.data.ReportType
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

/**
 * Bottom Sheet диалог за докладване или гласуване за съществуващ доклад
 */
class ReportBottomSheet : BottomSheetDialogFragment() {
    
    private var mode: Mode = Mode.CREATE
    private var existingReport: PoliceReport? = null
    
    private var onReportCreated: ((ReportType) -> Unit)? = null
    private var onVoteSubmitted: ((String, Boolean) -> Unit)? = null
    
    enum class Mode {
        CREATE,  // Създаване на нов доклад
        VOTE     // Гласуване за съществуващ доклад
    }
    
    companion object {
        private const val ARG_MODE = "mode"
        
        /**
         * Създава Bottom Sheet за нов доклад
         */
        fun newReportSheet(): ReportBottomSheet {
            return ReportBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, Mode.CREATE.name)
                }
            }
        }
        
        /**
         * Създава Bottom Sheet за гласуване за съществуващ доклад
         */
        fun voteSheet(report: PoliceReport): ReportBottomSheet {
            return ReportBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, Mode.VOTE.name)
                }
                existingReport = report
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        arguments?.let { args ->
            mode = Mode.valueOf(args.getString(ARG_MODE, Mode.CREATE.name))
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.sheet_report_bottom, container, false)
        val titleView = rootView.findViewById<TextView>(R.id.tvReportSheetTitle)
        val subtitleView = rootView.findViewById<TextView>(R.id.tvReportSheetSubtitle)
        val actionsContainer = rootView.findViewById<LinearLayout>(R.id.llReportSheetActions)
        val cancelButton = rootView.findViewById<MaterialButton>(R.id.btnReportSheetCancel)
        
        when (mode) {
            Mode.CREATE -> setupCreateUI(titleView, subtitleView, actionsContainer)
            Mode.VOTE -> setupVoteUI(titleView, subtitleView, actionsContainer)
        }

        cancelButton.setOnClickListener { dismiss() }
        
        return rootView
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val behavior = BottomSheetBehavior.from(bottomSheet)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val targetHeight = (resources.displayMetrics.heightPixels * if (isLandscape) 0.92f else 0.78f).toInt()

        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = targetHeight
        }

        behavior.skipCollapsed = true
        behavior.isFitToContents = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
    
    /**
     * UI за създаване на нов доклад
     */
    private fun setupCreateUI(
        titleView: TextView,
        subtitleView: TextView,
        actionsContainer: LinearLayout
    ) {
        titleView.text = getString(R.string.report_sheet_title)
        subtitleView.text = getString(R.string.report_sheet_subtitle_create)
        subtitleView.visibility = View.VISIBLE
        actionsContainer.removeAllViews()
        
        ReportType.entries.forEachIndexed { index, type ->
            val localizedName = getString(type.displayNameResId)
            val button = createSheetActionButton(
                label = localizedName,
                iconRes = type.iconResId,
                addTopMargin = index != 0
            ).apply {
                setOnClickListener {
                    onReportTypeSelected(type)
                }
            }
            actionsContainer.addView(button)
        }
    }
    
    /**
     * UI за гласуване за съществуващ доклад
     */
    private fun setupVoteUI(
        titleView: TextView,
        subtitleView: TextView,
        actionsContainer: LinearLayout
    ) {
        val report = existingReport ?: run {
            dismiss()
            return
        }
        
        val reportType = ReportType.fromString(report.type) ?: ReportType.POLICE

        titleView.text = getString(reportType.displayNameResId)
        val titleIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), reportType.iconResId)
        if (titleIcon != null) {
            val size = (28 * resources.displayMetrics.density).toInt()
            titleIcon.setBounds(0, 0, size, size)
            titleView.setCompoundDrawablesRelative(titleIcon, null, null, null)
            titleView.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
        }
        subtitleView.text = formatReportAge(report)
        subtitleView.visibility = View.VISIBLE
        actionsContainer.removeAllViews()

        val likeLabel = if (report.upvotes > 0) {
            getString(R.string.report_vote_like_with_count, report.upvotes)
        } else {
            getString(R.string.report_vote_still_there)
        }
        val upvoteButton = createSheetActionButton(
            label = likeLabel,
            addTopMargin = false
        ).apply {
            text = likeLabel
            setOnClickListener {
                onVoteSubmitted?.invoke(report.id, true)
                dismiss()
            }
        }
        actionsContainer.addView(upvoteButton)
    }

    private fun formatReportAge(report: PoliceReport): String {
        val created = report.createdAt?.toDate() ?: report.timestamp?.toDate()
        if (created == null) return getString(R.string.report_added_just_now)
        val minutes = ((System.currentTimeMillis() - created.time) / 60_000L).toInt().coerceAtLeast(0)
        return when {
            minutes < 1 -> getString(R.string.report_added_just_now)
            minutes < 60 -> getString(R.string.report_added_minutes_ago, minutes)
            else -> getString(R.string.report_added_hours_ago, (minutes / 60).coerceAtLeast(1))
        }
    }

    private fun createSheetActionButton(
        label: String,
        addTopMargin: Boolean,
        iconRes: Int? = null
    ): MaterialButton {
        val density = resources.displayMetrics.density
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val marginTopDp = if (isLandscape) 6 else 12
        val minHeightDp = if (isLandscape) 40 else 52
        val cornerDp = if (isLandscape) 10 else 14
        val textSizeSp = if (isLandscape) 14f else 16f
        val iconSizeDp = if (isLandscape) 28 else 34
        val marginTop = if (addTopMargin) (marginTopDp * density).toInt() else 0
        return MaterialButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { params ->
                params.topMargin = marginTop
            }
            text = label
            minHeight = (minHeightDp * density).toInt()
            insetTop = 0
            insetBottom = 0
            cornerRadius = (cornerDp * density).toInt()
            setTextColor(resources.getColor(android.R.color.black, null))
            textSize = textSizeSp
            setBackgroundColor(0xFFD9D9D9.toInt())
            if (iconRes != null) {
                icon = androidx.core.content.ContextCompat.getDrawable(context, iconRes)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = (10 * density).toInt()
                iconSize = (iconSizeDp * density).toInt()
                iconTint = null
            }
        }
    }
    
    /**
     * Обработва избора на тип доклад
     */
    private fun onReportTypeSelected(type: ReportType) {
        onReportCreated?.invoke(type)
        dismiss()
    }
    
    /**
     * Set callback за създаване на доклад
     */
    fun setOnReportCreatedListener(listener: (ReportType) -> Unit) {
        onReportCreated = listener
    }
    
    /**
     * Set callback за гласуване
     */
    fun setOnVoteSubmittedListener(listener: (String, Boolean) -> Unit) {
        onVoteSubmitted = listener
    }
}
