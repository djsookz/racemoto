package com.revix.app.garage

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.revix.app.R
import com.revix.app.data.VehicleData

object VehicleSearchPicker {

    fun show(
        context: Context,
        title: String,
        options: List<String>,
        onSelect: (String) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_search_picker, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPickerTitle)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnClosePicker)
        val etSearch = dialogView.findViewById<TextInputEditText>(R.id.etSearch)
        val listView = dialogView.findViewById<ListView>(R.id.lvOptions)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tvEmpty)
        val btnUseCustom = dialogView.findViewById<MaterialButton>(R.id.btnUseCustom)

        tvTitle.text = title
        tvEmpty.text = context.getString(R.string.garage_no_results_custom)

        val selectable = options.filterNot { VehicleData.isPopularBrandsHeader(context, it) }
        val adapter = ArrayAdapter(context, R.layout.dropdown_item_normal, R.id.text1, selectable)
        listView.adapter = adapter
        listView.emptyView = tvEmpty

        val dialog = AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        fun typedValue(): String = etSearch.text?.toString()?.trim().orEmpty()

        fun confirm(value: String) {
            val cleaned = value.trim()
            if (cleaned.isEmpty() || VehicleData.isPopularBrandsHeader(context, cleaned)) return
            onSelect(cleaned)
            dialog.dismiss()
        }

        fun refreshCustomAction() {
            val typed = typedValue()
            val exactMatch = selectable.any { it.equals(typed, ignoreCase = true) }
            val show = typed.isNotEmpty() && !exactMatch
            btnUseCustom.visibility = if (show) View.VISIBLE else View.GONE
            if (show) {
                btnUseCustom.text = context.getString(R.string.garage_use_custom, typed)
            }
        }

        etSearch.addTextChangedListener { text ->
            adapter.filter.filter(text?.toString() ?: "")
            refreshCustomAction()
        }
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val typed = typedValue()
                if (typed.isNotEmpty()) {
                    confirm(typed)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        btnUseCustom.setOnClickListener { confirm(typedValue()) }
        btnClose.setOnClickListener { dialog.dismiss() }
        listView.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let { confirm(it) }
        }

        refreshCustomAction()
        dialog.show()
    }
}
