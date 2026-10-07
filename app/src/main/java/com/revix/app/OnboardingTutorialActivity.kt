package com.revix.app

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.revix.app.settings.LanguageManager
import com.google.android.material.button.MaterialButton

class OnboardingTutorialActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private lateinit var pager: ViewPager2
    private lateinit var pageIndicator: LinearLayout
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: TextView

    private val pages by lazy {
        listOf(
            OnboardingPage(
                iconRes = android.R.drawable.ic_menu_compass,
                titleRes = R.string.onboarding_page1_title,
                bodyRes = R.string.onboarding_page1_body
            ),
            OnboardingPage(
                iconRes = android.R.drawable.ic_menu_mapmode,
                titleRes = R.string.onboarding_page2_title,
                bodyRes = R.string.onboarding_page2_body
            ),
            OnboardingPage(
                iconRes = android.R.drawable.ic_menu_sort_by_size,
                titleRes = R.string.onboarding_page3_title,
                bodyRes = R.string.onboarding_page3_body
            ),
            OnboardingPage(
                iconRes = android.R.drawable.ic_menu_manage,
                titleRes = R.string.onboarding_page4_title,
                bodyRes = R.string.onboarding_page4_body
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_tutorial)
        applySystemBarsPaddingToRoot()

        pager = findViewById(R.id.onboardingPager)
        pageIndicator = findViewById(R.id.pageIndicator)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)

        pager.adapter = OnboardingPagerAdapter(pages)
        setupDots()
        updateControls(0)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateControls(position)
            }
        })

        btnSkip.setOnClickListener { finishTutorial() }
        btnNext.setOnClickListener {
            val lastIndex = pages.lastIndex
            if (pager.currentItem < lastIndex) {
                pager.currentItem = pager.currentItem + 1
            } else {
                finishTutorial()
            }
        }
    }

    private fun setupDots() {
        pageIndicator.removeAllViews()
        val size = (8 * resources.displayMetrics.density).toInt()
        val margin = (6 * resources.displayMetrics.density).toInt()
        repeat(pages.size) { index ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
                background = ContextCompat.getDrawable(
                    this@OnboardingTutorialActivity,
                    if (index == 0) R.drawable.bg_onboarding_dot_active else R.drawable.bg_onboarding_dot
                )
            }
            pageIndicator.addView(dot)
        }
    }

    private fun updateControls(position: Int) {
        for (i in 0 until pageIndicator.childCount) {
            pageIndicator.getChildAt(i).background = ContextCompat.getDrawable(
                this,
                if (i == position) R.drawable.bg_onboarding_dot_active else R.drawable.bg_onboarding_dot
            )
        }
        val isLast = position == pages.lastIndex
        btnNext.setText(if (isLast) R.string.onboarding_get_started else R.string.onboarding_next)
        btnSkip.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
    }

    private fun finishTutorial() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean(PREF_ONBOARDING_TUTORIAL_DONE, true)
            .apply()
        setResult(RESULT_OK)
        finish()
    }

    data class OnboardingPage(
        val iconRes: Int,
        val titleRes: Int,
        val bodyRes: Int
    )

    private class OnboardingPagerAdapter(
        private val pages: List<OnboardingPage>
    ) : RecyclerView.Adapter<OnboardingPagerAdapter.PageHolder>() {

        class PageHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivOnboardingIcon)
            val title: TextView = view.findViewById(R.id.tvOnboardingTitle)
            val body: TextView = view.findViewById(R.id.tvOnboardingBody)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_page, parent, false)
            return PageHolder(view)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val page = pages[position]
            holder.icon.setImageResource(page.iconRes)
            holder.title.setText(page.titleRes)
            holder.body.setText(page.bodyRes)
        }

        override fun getItemCount(): Int = pages.size
    }

    companion object {
        const val PREF_ONBOARDING_TUTORIAL_DONE = "onboarding_tutorial_done"

        fun isCompleted(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_ONBOARDING_TUTORIAL_DONE, false)
        }
    }
}
