package com.revix.app.track

import android.app.Dialog
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.revix.app.R
import com.revix.app.applyStickyImmersiveMode
import com.revix.app.track.catalog.TrackDefinition
import kotlin.math.max

object OfficialTrackMapDialog {
    @SuppressLint("SetJavaScriptEnabled")
    fun show(context: Context, track: TrackDefinition) {
        val assetPath = OfficialTrackSvgAssets.assetPathFor(track.id)
        if (assetPath == null) {
            Toast.makeText(context, context.getString(R.string.track_map_info_missing), Toast.LENGTH_SHORT).show()
            return
        }

        val svg = runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (svg.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.track_map_info_missing), Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_official_track_map)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#0D1117")))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        dialog.findViewById<TextView>(R.id.tvTrackMapTitle).text = track.name
        dialog.findViewById<ImageButton>(R.id.btnCloseTrackMap).setOnClickListener { dialog.dismiss() }

        val webView = dialog.findViewById<WebView>(R.id.webTrackMap)
        webView.setBackgroundColor(Color.parseColor("#0D1117"))
        webView.settings.apply {
            javaScriptEnabled = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = true
        }
        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            wrapSvg(svg),
            "text/html",
            "UTF-8",
            null
        )

        val root = dialog.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                view.setPadding(
                    max(systemBars.left, cutout.left),
                    max(systemBars.top, cutout.top),
                    max(systemBars.right, cutout.right),
                    max(systemBars.bottom, cutout.bottom)
                )
                insets
            }
        }

        dialog.setOnShowListener {
            dialog.window?.let { applyStickyImmersiveMode(it) }
        }
        dialog.window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                dialog.window?.let { applyStickyImmersiveMode(it) }
            }
        }
        dialog.setOnDismissListener { webView.destroy() }
        dialog.show()
        dialog.window?.let { applyStickyImmersiveMode(it) }
    }

    private fun wrapSvg(rawSvg: String): String {
        val svg = prepareSvg(rawSvg)
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=12.0, user-scalable=yes"/>
            <style>
              html, body {
                margin: 0;
                padding: 0;
                width: 100%;
                height: 100%;
                background: #0D1117;
              }
              .stage {
                box-sizing: border-box;
                width: 100vw;
                height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 12px;
              }
              .stage svg {
                width: min(94vw, 94vh);
                height: min(94vw, 94vh);
              }
            </style>
            </head>
            <body>
            <div class="stage">$svg</div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun prepareSvg(rawSvg: String): String {
        var svg = rawSvg
            .replace(Regex("""<\?xml[^>]*>"""), "")
            .replace(Regex("""<!DOCTYPE[^>]*>"""), "")
            .trim()
        if (!svg.contains("viewBox=", ignoreCase = true)) {
            val width = Regex("""<svg\b[^>]*\bwidth="([0-9.]+)"""").find(svg)?.groupValues?.get(1) ?: "280"
            val height = Regex("""<svg\b[^>]*\bheight="([0-9.]+)"""").find(svg)?.groupValues?.get(1) ?: "280"
            svg = svg.replaceFirst("<svg", """<svg viewBox="0 0 $width $height"""")
        }
        svg = svg.replaceFirst(Regex("""\bwidth="[^"]*""""), """width="100%"""")
        svg = svg.replaceFirst(Regex("""\bheight="[^"]*""""), """height="100%"""")
        return svg
    }
}
