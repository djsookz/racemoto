package com.example.clinometer

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun AppCompatActivity.applySystemBarsPaddingToRoot() {
    val content = findViewById<ViewGroup>(android.R.id.content) ?: return
    val root = content.getChildAt(0) ?: return

    val initialLeft = root.paddingLeft
    val initialTop = root.paddingTop
    val initialRight = root.paddingRight
    val initialBottom = root.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(
            initialLeft + systemBarsInsets.left,
            initialTop + systemBarsInsets.top,
            initialRight + systemBarsInsets.right,
            initialBottom + systemBarsInsets.bottom
        )
        insets
    }

    ViewCompat.requestApplyInsets(root)
}
