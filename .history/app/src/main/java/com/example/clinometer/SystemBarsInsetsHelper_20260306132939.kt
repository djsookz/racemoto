package com.example.clinometer

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

private data class RootInitialPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

fun AppCompatActivity.applySystemBarsPaddingToRoot() {
    val content = findViewById<ViewGroup>(android.R.id.content) ?: return
    val root = content.getChildAt(0) ?: return

    // We handle system bar insets manually.
    root.fitsSystemWindows = false

    val initialPadding = (root.getTag(R.id.tag_system_bars_initial_padding) as? RootInitialPadding)
        ?: RootInitialPadding(
            left = root.paddingLeft,
            top = root.paddingTop,
            right = root.paddingRight,
            bottom = root.paddingBottom
        ).also {
            root.setTag(R.id.tag_system_bars_initial_padding, it)
        }

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())

        val insetLeft = max(systemBarsInsets.left, cutoutInsets.left)
        val insetTop = max(systemBarsInsets.top, cutoutInsets.top)
        val insetRight = max(systemBarsInsets.right, cutoutInsets.right)
        val insetBottom = max(systemBarsInsets.bottom, cutoutInsets.bottom)

        view.setPadding(
            initialPadding.left + insetLeft,
            initialPadding.top + insetTop,
            initialPadding.right + insetRight,
            initialPadding.bottom + insetBottom
        )
        insets
    }

    ViewCompat.requestApplyInsets(root)
}
