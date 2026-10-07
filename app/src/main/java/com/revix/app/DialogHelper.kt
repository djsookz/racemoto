package com.revix.app

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

object DialogHelper {
    /**
     * App-styled alert builder (dark card, orange accents).
     */
    fun builder(context: Context): AlertDialog.Builder =
        AlertDialog.Builder(context, R.style.CustomAlertDialog)

    /**
     * Drag-style dialog buttons:
     * - Positive / Neutral (confirm) -> orange
     * - Negative (Cancel) -> red
     */
    fun styleDialogButtons(dialog: AlertDialog) {
        dialog.setOnShowListener {
            val orange = ContextCompat.getColor(dialog.context, R.color.primary_color)
            val red = ContextCompat.getColor(dialog.context, R.color.red)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(orange)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(orange)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(red)
        }
    }

    fun show(dialog: AlertDialog): AlertDialog {
        styleDialogButtons(dialog)
        dialog.show()
        return dialog
    }

    fun show(builder: AlertDialog.Builder): AlertDialog = show(builder.create())

    fun showDeleteConfirmation(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        positiveButtonText: CharSequence = context.getString(R.string.delete),
        negativeButtonText: CharSequence = context.getString(R.string.dialog_cancel_button),
        onConfirm: () -> Unit
    ): AlertDialog {
        val dialog = builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { _, _ -> onConfirm() }
            .setNegativeButton(negativeButtonText, null)
            .create()
        return show(dialog)
    }

    fun showDeleteConfirmation(
        context: Context,
        @StringRes titleRes: Int,
        message: CharSequence,
        @StringRes positiveButtonRes: Int = R.string.delete,
        @StringRes negativeButtonRes: Int = R.string.dialog_cancel_button,
        onConfirm: () -> Unit
    ): AlertDialog {
        return showDeleteConfirmation(
            context = context,
            title = context.getString(titleRes),
            message = message,
            positiveButtonText = context.getString(positiveButtonRes),
            negativeButtonText = context.getString(negativeButtonRes),
            onConfirm = onConfirm
        )
    }
}
