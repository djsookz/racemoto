package com.revix.app.racebox

import android.view.View
import android.widget.TextView
import com.revix.app.R

/** Quiet “RaceBox Mini” chip — visible only when the session was recorded with RaceBox GPS. */
object RaceBoxSessionUi {
    fun bindLabel(view: TextView?, recordedWithRaceBox: Boolean) {
        if (view == null) return
        if (recordedWithRaceBox) {
            view.setText(R.string.racebox_mini_label)
            view.visibility = View.VISIBLE
        } else {
            view.visibility = View.GONE
        }
    }
}
