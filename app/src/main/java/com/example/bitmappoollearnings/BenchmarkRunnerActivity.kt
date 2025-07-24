package com.example.bitmappoollearnings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class BenchmarkRunnerActivity : AppCompatActivity() {

    /*
    All 8 combinations using the three parameters..
     */
    val benchmarkConfigs = listOf(
//        BenchmarkConfig(false, false, false),
//        BenchmarkConfig(false, false, true),
//        BenchmarkConfig(false, true, false),
//        BenchmarkConfig(false, true, true),
//        BenchmarkConfig(true, false, false),
//        BenchmarkConfig(true, false, true),
//        BenchmarkConfig(true, true, false),
        BenchmarkConfig(true, true, true)

    )


    private var currentIndex = 0

    private lateinit var resultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)

        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // when MainActivity returns
            currentIndex++
            startNextTest()
        }

        // Attach button logic
        findViewById<Button>(R.id.btnStartBenchmark)?.setOnClickListener {
            currentIndex = 0
            startNextTest()
        }
    }

    private fun startNextTest() {
        if (currentIndex >= benchmarkConfigs.size) {
            return
        }

        val config = benchmarkConfigs[currentIndex]

        BenchmarkFlags.enableBitmapPool = config.usePool
        BenchmarkFlags.enableDownsampling = config.useDownsampling
        BenchmarkFlags.enableTaskCancellation = config.useCancellation

        BenchmarkStats.reset()
        BenchmarkStats.currentConfig = config

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("isBenchmarkRun", true)
            putExtra("configIndex", currentIndex)
        }

        resultLauncher.launch(intent)
    }

}
