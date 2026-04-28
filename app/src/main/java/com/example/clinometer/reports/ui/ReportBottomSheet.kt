package com.example.clinometer.reports.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.clinometer.R
import com.example.clinometer.reports.data.PoliceReport
import com.example.clinometer.reports.data.ReportType
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
        titleView.text = "Докладвай на картата"
        subtitleView.text = "Избери какво искаш да докладваш"
        subtitleView.visibility = View.VISIBLE
        actionsContainer.removeAllViews()
        
        ReportType.entries.forEach { type ->
            val button = createSheetActionButton("${type.icon} ${type.displayName}").apply {
                text = "${type.icon} ${type.displayName}"
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

        titleView.text = "${reportType.icon} ${reportType.displayName}"
        subtitleView.text = "Потвърждения: ${report.upvotes} | Оспорвания: ${report.downvotes}"
        subtitleView.visibility = View.VISIBLE
        actionsContainer.removeAllViews()

        val upvoteButton = createSheetActionButton("👍 Все още е там").apply {
            text = "👍 Все още е там"
            setOnClickListener {
                onVoteSubmitted?.invoke(report.id, true)
                dismiss()
            }
        }
        actionsContainer.addView(upvoteButton)
        
        val downvoteButton = createSheetActionButton("👎 Няма го").apply {
            text = "👎 Няма го"
            setOnClickListener {
                onVoteSubmitted?.invoke(report.id, false)
                dismiss()
            }
        }
        actionsContainer.addView(downvoteButton)
    }

    private fun createSheetActionButton(label: String): MaterialButton {
        val marginTop = (12 * resources.displayMetrics.density).toInt()
        return MaterialButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { params ->
                params.topMargin = marginTop
            }
            text = label
            minHeight = (52 * resources.displayMetrics.density).toInt()
            insetTop = 0
            insetBottom = 0
            cornerRadius = (14 * resources.displayMetrics.density).toInt()
            setTextColor(resources.getColor(android.R.color.black, null))
            textSize = 16f
            setBackgroundColor(0xFFD9D9D9.toInt())
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
