package com.revix.app

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.dispose
import coil.load
import coil.size.Scale
import com.revix.app.settings.LanguageManager
import java.io.File

class FullScreenImageActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_full_screen_image)
        
        // Hide status bar (must be after setContentView)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        
        val photoPaths = intent.getStringArrayListExtra(EXTRA_PHOTO_PATHS) ?: emptyList<String>()
        val currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
        
        if (photoPaths.isEmpty()) {
            finish()
            return
        }
        
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val btnBack = findViewById<View>(R.id.btnBackFullScreen)
        
        val adapter = FullScreenImageAdapter(photoPaths)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(currentIndex, false)
        
        // Back button
        btnBack.setOnClickListener {
            finish()
        }
    }

    companion object {
        const val EXTRA_PHOTO_PATHS = "photo_paths"
        const val EXTRA_CURRENT_INDEX = "current_index"
        const val EXTRA_SHOW_DELETE = "show_delete"
        const val EXTRA_DELETE_REQUESTED = "delete_requested"
        const val EXTRA_DELETED_INDEX = "deleted_index"
    }
    
    private class FullScreenImageAdapter(
        private val photoPaths: List<String>
    ) : RecyclerView.Adapter<FullScreenImageAdapter.ImageViewHolder>() {
        
        class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.fullScreenImageView)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_full_screen_image, parent, false)
            return ImageViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.imageView.load(File(photoPaths[position])) {
                scale(Scale.FIT)
                crossfade(false)
            }
        }

        override fun onViewRecycled(holder: ImageViewHolder) {
            holder.imageView.dispose()
            super.onViewRecycled(holder)
        }

        override fun getItemCount() = photoPaths.size
    }
}
