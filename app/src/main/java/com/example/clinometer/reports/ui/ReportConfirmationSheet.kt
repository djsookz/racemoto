package com.example.clinometer.reports.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.example.clinometer.R
import com.example.clinometer.reports.data.PoliceReport
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet за потвърждение дали репорт все още е валиден
 * Показва се след преминаване покрай репорт при навигация
 */
class ReportConfirmationSheet : BottomSheetDialogFragment() {
    
    private var report: PoliceReport? = null
    private var onResponse: ((Boolean) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var countdownTimer: CountDownTimer? = null
    
    companion object {
        private const val COUNTDOWN_SECONDS = 10
        
        fun newInstance(
            report: PoliceReport,
            onResponse: (Boolean) -> Unit,
            onDismiss: () -> Unit
        ): ReportConfirmationSheet {
            return ReportConfirmationSheet().apply {
                this.report = report
                this.onResponse = onResponse
                this.onDismiss = onDismiss
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.sheet_report_confirmation, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val report = this.report ?: run {
            dismiss()
            return
        }
        
        val reportType = report.getReportType()
        
        // Setup UI
        val tvQuestion = view.findViewById<TextView>(R.id.tvConfirmationQuestion)
        val tvCountdown = view.findViewById<TextView>(R.id.tvCountdown)
        val btnYes = view.findViewById<Button>(R.id.btnConfirmYes)
        val btnNo = view.findViewById<Button>(R.id.btnConfirmNo)
        
        tvQuestion.text = "Все още тук ли е\n${reportType.icon} ${reportType.displayName.lowercase()}?"
        
        // Button handlers
        btnYes.setOnClickListener {
            onResponse?.invoke(true)
            dismiss()
        }
        
        btnNo.setOnClickListener {
            onResponse?.invoke(false)
            dismiss()
        }
        
        // Countdown timer (10 seconds)
        countdownTimer = object : CountDownTimer(COUNTDOWN_SECONDS * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                tvCountdown.text = "Изчезва след: $secondsLeft сек"
            }
            
            override fun onFinish() {
                // Timeout - dismiss without action
                dismiss()
            }
        }.start()
    }
    
    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        countdownTimer?.cancel()
        onDismiss?.invoke()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        countdownTimer?.cancel()
    }
}
