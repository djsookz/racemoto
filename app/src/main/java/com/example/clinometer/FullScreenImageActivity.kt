package com.example.clinometer

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
        
        val photoPaths = intent.getStringArrayListExtra("photo_paths") ?: emptyList<String>()
        val currentIndex = intent.getIntExtra("current_index", 0)
        
        if (photoPaths.isEmpty()) {
            finish()
            return
        }
        
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val btnBack = findViewById<ImageButton>(R.id.btnBackFullScreen)
        
        val adapter = FullScreenImageAdapter(photoPaths)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(currentIndex, false)
        
        // Back button
        btnBack.setOnClickListener {
            finish()
        }
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
