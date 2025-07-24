package com.example.bitmappoollearnings

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ImageAdapter(
    private val context: Context,
    private val application: MyApplication,
    private val imageList: List<String>,
    private val bitmapPool: SimpleBitmapPool
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    @Volatile
    private var isShuttingDown = false

    private val threadPool = Executors.newFixedThreadPool(5)

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView = view.findViewById<ImageView>(R.id.imageView)
        var boundUrl: String? = null // Used to prevent incorrect image binding
        var currentTask: Future<*>? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_layout, parent, false)
        return ImageViewHolder(view)
    }

    override fun getItemCount(): Int = imageList.size

    override fun onViewRecycled(holder: ImageViewHolder) {
        if (BenchmarkFlags.enableTaskCancellation) {

            //Future.cancel(true) returns false if the task is already completed or was never started.
            val wasCancelled = holder.currentTask?.cancel(true) == true
            if (wasCancelled) {
                BenchmarkStats.cancelledTasks.incrementAndGet()
                Log.d("ImageAdapter", "Task cancelled for ${holder.boundUrl}")
            } else {
                Log.d("ImageAdapter", "Task not cancelled (already completed) for ${holder.boundUrl}")
            }
        } else {
            Log.d("ImageAdapter", "Task NOT cancelled for ${holder.boundUrl}")
        }
        holder.currentTask = null

        if (BenchmarkFlags.enableBitmapPool) {
            val bmp = (holder.imageView.drawable as? BitmapDrawable)?.bitmap
            if (bmp != null && !bmp.isRecycled) {
                bitmapPool.addBitmap(bmp)
                BenchmarkStats.bitmapsAddedToPool.incrementAndGet()
            }
        } else {
            Log.d("BitmapPool", "Skipping add to pool for ${holder.boundUrl}")
        }


        holder.imageView.setImageDrawable(null)
        holder.boundUrl = null
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageList[position]
        holder.boundUrl = imageUrl

        // Cancel any previous task (just in case this holder is reused quickly)
        holder.currentTask?.cancel(true)

        // Skip if adapter is shutting down
        if (isShuttingDown) {
            Log.d("ImageAdapter", "🚫 Skipping task for $imageUrl because adapter is shutting down")
            return
        }

        // Submit the new image loading task here
        val future = threadPool.submit {
            try {
                Log.d("ImageAdapter", "---------- Task started for $imageUrl ----------")

                val bitmap = decodeImageFromUrl(imageUrl,holder.imageView)

                if (Thread.currentThread().isInterrupted) {
                    Log.d("ImageAdapter", "---------- Task interrupted for $imageUrl ----------")
                    return@submit
                }

                Log.d("ImageAdapter", "---------- Task completed for $imageUrl ----------")

                if (context is Activity && !context.isFinishing) {
                    context.runOnUiThread {
                        if (holder.boundUrl == imageUrl) {
                            if (bitmap != null && !bitmap.isRecycled) {
                                holder.imageView.setImageBitmap(bitmap)
                                BenchmarkStats.loadedPositions.add(holder.adapterPosition)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageAdapter", "Error loading $imageUrl", e)
            }
        }

        holder.currentTask = future
    }


    fun decodeImageFromUrl(urlStr: String,imageView: ImageView): Bitmap? {
        if (isShuttingDown) return null

        val url = URL(urlStr)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        url.openStream().use {
            BitmapFactory.decodeStream(it, null, options)
        }

        val width = options.outWidth
        val height = options.outHeight
        val config = Bitmap.Config.ARGB_8888

        /**
         * In Android, a view is "laid out" when it has completed both measurement and layout, and now knows:
         * -> Its actual width and height
         * -> Its position within its parent
         */

        val targetWidth = imageView.width.takeIf {
            it > 0
        } ?: 500 //if the view has not been laid out yet, this will be 500
        val targetHeight = imageView.height.takeIf { it > 0 } ?: 300//if the view has not been laid out yet, this will be 300

        var finalWidth = width
        var finalHeight = height

        if (!BenchmarkFlags.enableDownsampling) {
            options.inSampleSize = 1
            finalWidth = width
            finalHeight = height
        }else{
            //we are trying to dynamically downscale, acc to the imageview width and height in the device
            val inSampleSize = calculateInSampleSize(width, height, targetWidth, targetHeight)
            options.inSampleSize =  inSampleSize

            finalWidth = width /inSampleSize
            finalHeight = height /inSampleSize
        }


        options.inJustDecodeBounds = false
        options.inPreferredConfig = config
        options.inMutable = true

        Log.d("BitmapPool", "Trying to reuse for: ${finalWidth}x${finalHeight}")

        val reusable = application.simpleBitmapPool.getReusableBitmap(finalWidth, finalHeight, config)
        if (reusable != null && !reusable.isRecycled) {
            BenchmarkStats.bitmapsReused.incrementAndGet() // Reuse happened
            options.inBitmap = reusable
            Log.d("BitmapPool", "Yippee! Reusing bitmap from pool")
        } else {
            Log.d("BitmapPool", "Ah! Gosh .. Allocating new bitmap")
        }

        val finalBitmap= url.openStream().use {
            BitmapFactory.decodeStream(it, null, options)
        }

        Log.d("DownSampling", "Decoded Bitmap size: ${finalBitmap?.width} x ${finalBitmap?.height}")

        Log.d("Benchmark", "Decoded: ${finalBitmap?.width}x${finalBitmap?.height}, inSampleSize: ${options.inSampleSize}")
        if (finalBitmap != null) {
            BenchmarkStats.totalImagesLoaded.incrementAndGet() // confirmed image load
        }
        return finalBitmap
    }

    fun calculateInSampleSize(imageWidth: Int, imageHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1

        if (imageHeight > reqHeight || imageWidth > reqWidth) {
            val halfHeight = imageHeight / 2
            val halfWidth = imageWidth / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }


    fun shutdown() {
        isShuttingDown = true
        threadPool.shutdownNow() // forcefully stop all running threads
    }
}

