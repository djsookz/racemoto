package com.example.clinometer

import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

object DialogHelper {
    /**
     * Стилизира бутоните на AlertDialog:
     * - Positive button (Save/Continue) -> оранжев
     * - Negative button (Cancel) -> червен
     */
    fun styleDialogButtons(dialog: AlertDialog) {
        dialog.setOnShowListener {
            val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            
            if (btnPositive != null) {
                btnPositive.setTextColor(ContextCompat.getColor(dialog.context, R.color.primary_color))
            }
            if (btnNegative != null) {
                btnNegative.setTextColor(ContextCompat.getColor(dialog.context, android.R.color.holo_red_dark))
            }
        }
    }
}


