package com.revix.app.drag

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

object CameraSettingsSheet {
    private const val SHEET_COLOR = 0xFF12181F.toInt()

    fun prepare(dialog: BottomSheetDialog) {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.navigationBarColor = SHEET_COLOR
        dialog.window?.setDimAmount(0.55f)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            sheet.setBackgroundColor(SHEET_COLOR)
            val metrics = sheet.resources.displayMetrics
            val landscape = sheet.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            sheet.layoutParams = sheet.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.skipCollapsed = true
            behavior.isFitToContents = true
            behavior.maxHeight = (metrics.heightPixels * 0.94f).toInt()
            behavior.maxWidth = metrics.widthPixels
            if (landscape) {
                behavior.peekHeight = metrics.heightPixels
            }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            sheet.parent?.let { parent ->
                if (parent is View) parent.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    fun compactChipRow(vararg chips: TextView) {
        val visible = chips.filter { it.visibility == View.VISIBLE }
        val single = visible.size <= 1
        visible.forEach { chip ->
            val lp = chip.layoutParams as? LinearLayout.LayoutParams ?: return@forEach
            if (single) {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                lp.weight = 0f
                val pad = (20 * chip.resources.displayMetrics.density).toInt()
                chip.setPadding(pad, chip.paddingTop, pad, chip.paddingBottom)
            } else {
                lp.width = 0
                lp.weight = 1f
            }
            chip.layoutParams = lp
        }
    }
}
