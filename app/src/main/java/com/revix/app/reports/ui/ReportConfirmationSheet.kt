package com.revix.app.reports.ui

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.revix.app.R
import com.revix.app.reports.data.PoliceReport
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

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

        val localizedReportName = getString(reportType.displayNameResId).lowercase(Locale.getDefault())
        tvQuestion.text = getString(R.string.report_confirmation_question, localizedReportName)
        val icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), reportType.iconResId)
        if (icon != null) {
            val size = (36 * resources.displayMetrics.density).toInt()
            icon.setBounds(0, 0, size, size)
            tvQuestion.setCompoundDrawablesRelative(icon, null, null, null)
            tvQuestion.compoundDrawablePadding = (10 * resources.displayMetrics.density).toInt()
            tvQuestion.gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        
        btnYes.text = getString(R.string.report_confirmation_yes)
        btnNo.text = getString(R.string.report_confirmation_no)
        btnYes.setTextColor(Color.WHITE)
        btnNo.setTextColor(Color.WHITE)

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
                tvCountdown.text = getString(R.string.report_confirmation_countdown, secondsLeft)
            }
            
            override fun onFinish() {
                // Timeout - dismiss without action
                dismiss()
            }
        }.start()
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val behavior = BottomSheetBehavior.from(bottomSheet)

        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        behavior.skipCollapsed = true
        behavior.isFitToContents = true
        behavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
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
