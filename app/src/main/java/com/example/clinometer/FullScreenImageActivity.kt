package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import android.graphics.BitmapFactory

class FullScreenImageActivity : AppCompatActivity() {
    
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
        val showDeleteButton = intent.getBooleanExtra(EXTRA_SHOW_DELETE, false)
        
        if (photoPaths.isEmpty()) {
            finish()
            return
        }
        
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val btnBack = findViewById<ImageButton>(R.id.btnBackFullScreen)
        val btnDelete = findViewById<ImageButton>(R.id.btnDeleteFullScreen)
        
        val adapter = FullScreenImageAdapter(photoPaths)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(currentIndex, false)
        btnDelete.visibility = if (showDeleteButton) View.VISIBLE else View.GONE
        
        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        btnDelete.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_DELETE_REQUESTED, true))
            finish()
        }
    }

    companion object {
        const val EXTRA_PHOTO_PATHS = "photo_paths"
        const val EXTRA_CURRENT_INDEX = "current_index"
        const val EXTRA_SHOW_DELETE = "show_delete"
        const val EXTRA_DELETE_REQUESTED = "delete_requested"
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
            val photoPath = photoPaths[position]
            val bitmap = BitmapFactory.decodeFile(photoPath)
            holder.imageView.setImageBitmap(bitmap)
        }
        
        override fun getItemCount() = photoPaths.size
    }
}
