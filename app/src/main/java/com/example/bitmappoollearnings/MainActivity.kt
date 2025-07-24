package com.example.bitmappoollearnings

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private var isBenchmarkRun = false

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ImageAdapter

    val baseImageUrls = listOf(
        // Very Large
        "https://picsum.photos/id/1011/2076/966",
        "https://picsum.photos/id/1025/2076/966",

        // Large Portrait
        "https://picsum.photos/id/1043/1200/1800",

        // Large Square
        "https://picsum.photos/id/1059/1500/1500",

        // Medium Landscape
        "https://picsum.photos/id/1062/800/600",

        // Medium Portrait
        "https://picsum.photos/id/36/600/800",

        // Small Square
        "https://picsum.photos/id/24/300/300",

        // Small Landscape
        "https://picsum.photos/id/37/400/200",

        // Weird Aspect Ratio
        "https://picsum.photos/id/38/2000/200",
        "https://picsum.photos/id/58/200/2000"
    )


    val fullImageList = mutableListOf<String>().apply {
        repeat(30) {
            addAll(baseImageUrls.shuffled())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        isBenchmarkRun = intent.getBooleanExtra("isBenchmarkRun", false)

        BenchmarkStats.reset()

        recyclerView = findViewById<RecyclerView>(R.id.rvImages)

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        val bitmapPool = (application as MyApplication).simpleBitmapPool
        adapter = ImageAdapter(this, application as MyApplication, fullImageList, bitmapPool)
        recyclerView.adapter = adapter

        if (isBenchmarkRun) {
            waitForVisibleItemsToLoadThenScroll(recyclerView)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.shutdown()
        (application as MyApplication).simpleBitmapPool.clearBitmapPool()
    }

    fun waitForVisibleItemsToLoadThenScroll(recyclerView: RecyclerView) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val handler = Handler(Looper.getMainLooper())

        val checkEndItemsLoaded = object : Runnable {
            override fun run() {
                val first = layoutManager.findFirstVisibleItemPosition()
                val last = layoutManager.findLastVisibleItemPosition()

                val allLoaded = (first..last).all { pos ->
                    BenchmarkStats.loadedPositions.contains(pos)
                }

                if (allLoaded) {
                    Log.d("AutoScroll", "Final items ($first to $last) loaded. Finishing benchmark.")

                    BenchmarkStats.captureEndNetworkUsage()
                    BenchmarkStats.logSummary()

                    if (isBenchmarkRun) {
                        setResult(Activity.RESULT_OK)
                        finish()
                    }

                } else {
                    handler.postDelayed(this, 300)
                }
            }
        }

        val checkInitialItemsLoaded = object : Runnable {
            override fun run() {
                val first = layoutManager.findFirstVisibleItemPosition()
                val last = layoutManager.findLastVisibleItemPosition()

                val allLoaded = (first..last).all { pos ->
                    BenchmarkStats.loadedPositions.contains(pos)
                }

                if (allLoaded) {
                    Log.d("AutoScroll", "Initial visible items ($first to $last) loaded. Scrolling...")

                    recyclerView.smoothScrollToPosition(recyclerView.adapter!!.itemCount / 2)

                    recyclerView.postDelayed({
                        recyclerView.smoothScrollToPosition(recyclerView.adapter!!.itemCount - 1)

                        // After scrolling to end, checking again
                        handler.postDelayed(checkEndItemsLoaded, 1000)
                    }, 2000)

                } else {
                    handler.postDelayed(this, 200)
                }
            }
        }

        handler.postDelayed(checkInitialItemsLoaded, 300)
    }
}
