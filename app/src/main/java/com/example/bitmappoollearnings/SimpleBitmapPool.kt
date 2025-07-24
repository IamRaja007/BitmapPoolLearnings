package com.example.bitmappoollearnings

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import kotlin.collections.iterator

class SimpleBitmapPool(maxSizeInKB: Int) {

    val poolSize: Int
        get() = bitmapCache.size()

    private val bitmapCache = object : LruCache<String, Bitmap>(maxSizeInKB) {

        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
                Log.d("BitmapPool", "Evicted and recycled: $key")
            }
        }
    }

    fun addBitmap(bitmap: Bitmap) {
        if (bitmap.isMutable && !bitmap.isRecycled) {
            synchronized(this) {
                val key = generateKey(bitmap.width, bitmap.height, bitmap.config!!)
                if (bitmapCache.get(key) == null) {
                    bitmapCache.put(key, bitmap)
                    Log.d("BitmapPool", "ADD → ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
                    Log.d("BitmapPool", "Bitmap added to pool with key: $key")
                    Log.d("Benchmark", "Added to pool: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.d("BitmapPool", "Bitmap already in pool for the key: $key")
                }
            }
        }
    }

    fun getReusableBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        if (!BenchmarkFlags.enableBitmapPool) return null

        synchronized(this) {
            val snapshot = bitmapCache.snapshot()

            for ((key, candidate) in snapshot) {
                val canReuse = candidate != null &&
                        candidate.config == config &&
                        candidate.width >= width &&
                        candidate.height >= height &&
                        candidate.isMutable &&
                        !candidate.isRecycled

                if (canReuse) {
                    bitmapCache.remove(key)
                    Log.d("BitmapPool", "Reused bitmap: $key")
                    Log.d("Benchmark", "Bitmap reused for: ${width}x${height}")
                    BenchmarkStats.bitmapsReused.incrementAndGet()
                    return candidate
                }
            }

            Log.d("BitmapPool", "No reusable bitmap found for: ${width}x${height}")
            return null
        }
    }

    fun clearBitmapPool() {
        bitmapCache.evictAll()
        Log.d("BitmapPool", "Cleared pool")
    }

    private fun generateKey(width: Int, height: Int, config: Bitmap.Config): String {
        return "$width-$height-${config.name}"
    }
}