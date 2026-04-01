package com.example.clinometer.reports.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.clinometer.R
import com.example.clinometer.reports.data.PoliceReport
import com.example.clinometer.reports.data.ReportType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom Sheet диалог за докладване или гласуване за съществуващ доклад
 */
class ReportBottomSheet : BottomSheetDialogFragment() {
    
    private var mode: Mode = Mode.CREATE
    private var existingReport: PoliceReport? = null
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    
    private var onReportCreated: ((ReportType) -> Unit)? = null
    private var onVoteSubmitted: ((String, Boolean) -> Unit)? = null
    
    enum class Mode {
        CREATE,  // Създаване на нов доклад
        VOTE     // Гласуване за съществуващ доклад
    }
    
    companion object {
        private const val ARG_MODE = "mode"
        private const val ARG_REPORT_ID = "report_id"
        private const val ARG_LATITUDE = "latitude"
        private const val ARG_LONGITUDE = "longitude"
        
        /**
         * Създава Bottom Sheet за нов доклад
         */
        fun newReportSheet(latitude: Double, longitude: Double): ReportBottomSheet {
            return ReportBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, Mode.CREATE.name)
                    putDouble(ARG_LATITUDE, latitude)
                    putDouble(ARG_LONGITUDE, longitude)
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
                    putString(ARG_REPORT_ID, report.id)
                }
                existingReport = report
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        arguments?.let { args ->
            mode = Mode.valueOf(args.getString(ARG_MODE, Mode.CREATE.name))
            currentLatitude = args.getDouble(ARG_LATITUDE, 0.0)
            currentLongitude = args.getDouble(ARG_LONGITUDE, 0.0)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Създаваме UI програмно (или можеш да създадеш layout XML)
        val rootView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        
        when (mode) {
            Mode.CREATE -> setupCreateUI(rootView)
            Mode.VOTE -> setupVoteUI(rootView)
        }
        
        return rootView
    }
    
    /**
     * UI за създаване на нов доклад
     */
    private fun setupCreateUI(container: LinearLayout) {
        val title = TextView(requireContext()).apply {
            text = "Докладвай на картата"
            textSize = 20f
            setPadding(0, 0, 0, 30)
        }
        container.addView(title)
        
        // Бутони за всеки тип доклад
        ReportType.entries.forEach { type ->
            val button = Button(requireContext()).apply {
                text = "${type.icon} ${type.displayName}"
                setOnClickListener {
                    onReportTypeSelected(type)
                }
            }
            container.addView(button)
        }
        
        // Cancel бутон
        val cancelButton = Button(requireContext()).apply {
            text = "Отказ"
            setOnClickListener {
                dismiss()
            }
        }
        container.addView(cancelButton)
    }
    
    /**
     * UI за гласуване за съществуващ доклад
     */
    private fun setupVoteUI(container: LinearLayout) {
        val report = existingReport ?: run {
            dismiss()
            return
        }
        
        val reportType = ReportType.fromString(report.type) ?: ReportType.POLICE
        
        val title = TextView(requireContext()).apply {
            text = "${reportType.icon} ${reportType.displayName}"
            textSize = 20f
            setPadding(0, 0, 0, 20)
        }
        container.addView(title)
        
        val scoreText = TextView(requireContext()).apply {
            text = "Потвърждения: ${report.upvotes} | Оспорвания: ${report.downvotes}"
            setPadding(0, 0, 0, 30)
        }
        container.addView(scoreText)
        
        // Upvote бутон
        val upvoteButton = Button(requireContext()).apply {
            text = "👍 Все още е там"
            setOnClickListener {
                onVoteSubmitted?.invoke(report.id, true)
                dismiss()
            }
        }
        container.addView(upvoteButton)
        
        // Downvote бутон
        val downvoteButton = Button(requireContext()).apply {
            text = "👎 Няма го"
            setOnClickListener {
                onVoteSubmitted?.invoke(report.id, false)
                dismiss()
            }
        }
        container.addView(downvoteButton)
        
        // Cancel бутон
        val cancelButton = Button(requireContext()).apply {
            text = "Отказ"
            setOnClickListener {
                dismiss()
            }
        }
        container.addView(cancelButton)
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
