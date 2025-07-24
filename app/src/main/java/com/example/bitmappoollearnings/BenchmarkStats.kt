package com.example.bitmappoollearnings

import android.net.TrafficStats
import android.os.Debug
import android.os.Process
import android.util.Log
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

object BenchmarkStats {

    var startTime = 0L
    val totalImagesLoaded = AtomicInteger(0)
    val bitmapsReused = AtomicInteger(0)
    val bitmapsAddedToPool = AtomicInteger(0)
    val cancelledTasks = AtomicInteger(0)

    private var startRxBytes = 0L
    private var endRxBytes = 0L

    // Memory metrics
    private var startUsedMemory = 0L
    private var endUsedMemory = 0L

    var currentConfig: BenchmarkConfig? = null

    private var startBlockingGcCount = 0
    private var endBlockingGcCount = 0
    private var startBlockingGcTime = 0L
    private var endBlockingGcTime = 0L


    private var startBgGcCount = 0
    private var endBgGcCount = 0
    private var startBgGcTime = 0L
    private var endBgGcTime = 0L

    val loadedPositions = Collections.synchronizedSet(mutableSetOf<Int>())

    fun reset() {
        startTime = System.currentTimeMillis()
        totalImagesLoaded.set(0)
        bitmapsReused.set(0)
        bitmapsAddedToPool.set(0)
        cancelledTasks.set(0)

        startRxBytes = TrafficStats.getUidRxBytes(Process.myUid())
        loadedPositions.clear()

        // Record memory before
        val runtime = Runtime.getRuntime()
        startUsedMemory = runtime.totalMemory() - runtime.freeMemory()

        startBlockingGcCount = Debug.getRuntimeStat("art.gc.blocking-gc-count")?.toIntOrNull() ?: 0
        startBlockingGcTime = Debug.getRuntimeStat("art.gc.blocking-gc-time")?.toLongOrNull() ?: 0L

        startBgGcCount = Debug.getRuntimeStat("art.gc.background-gc-count")?.toIntOrNull() ?: 0
        startBgGcTime = Debug.getRuntimeStat("art.gc.background-gc-time")?.toLongOrNull() ?: 0L

    }

    fun captureEndNetworkUsage() {
        endRxBytes = TrafficStats.getUidRxBytes(Process.myUid())

        val runtime = Runtime.getRuntime()
        endUsedMemory = runtime.totalMemory() - runtime.freeMemory()

        endBlockingGcCount = Debug.getRuntimeStat("art.gc.blocking-gc-count")?.toIntOrNull() ?: 0
        endBlockingGcTime = Debug.getRuntimeStat("art.gc.blocking-gc-time")?.toLongOrNull() ?: 0L

        endBgGcCount = Debug.getRuntimeStat("art.gc.background-gc-count")?.toIntOrNull() ?: 0
        endBgGcTime = Debug.getRuntimeStat("art.gc.background-gc-time")?.toLongOrNull() ?: 0L
    }

    fun logSummary() {
        val totalTime = System.currentTimeMillis() - startTime
        val dataUsedKB = (endRxBytes - startRxBytes) / 1024.0

        val runtime = Runtime.getRuntime()
        val allocatedHeapSizeInMB = runtime.totalMemory() / (1024 * 1024)
        val maxHeapSizeInMB = runtime.maxMemory() / (1024 * 1024)

        val nativeHeapUsedMB = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024)
        val nativeHeapTotalMB = Debug.getNativeHeapSize() / (1024.0 * 1024)


        val blockingGcEvents = endBlockingGcCount - startBlockingGcCount
        val blockingGcTimeMs = endBlockingGcTime - startBlockingGcTime

        val bgGcEvents = endBgGcCount - startBgGcCount
        val bgGcTimeMs = endBgGcTime - startBgGcTime

        Log.d(
            "BENCHMARK_SUMMARY", """
            ▶️ Config: usingBitmapPool=${currentConfig?.usePool}, usingDownsampling=${currentConfig?.useDownsampling}, cancellingUnnecessaryTasks=${currentConfig?.useCancellation}}
            Time Elapsed: $totalTime ms
            Total Images Loaded: ${totalImagesLoaded.get()}
            Bitmaps Reused: ${bitmapsReused.get()}
            Bitmaps Added to Pool: ${bitmapsAddedToPool.get()}
            Tasks Cancelled: ${cancelledTasks.get()}
       
            📶 Data Used: %.2f MB
            
            🔒 Blocking GC: $blockingGcEvents events, $blockingGcTimeMs ms
            🧵 Background GC: $bgGcEvents events, $bgGcTimeMs ms  

            🧠 Memory:
                Used Before Run: %.2f MB
                Used After Run: %.2f MB
                Java Heap Allocated: ${allocatedHeapSizeInMB} MB
                Java Heap Max: ${maxHeapSizeInMB} MB
                Native Heap Used: %.2f MB
                Native Heap Size: %.2f MB
     
        """.trimIndent().format(
                dataUsedKB / 1024,
                startUsedMemory / 1024.0 / 1024,
                endUsedMemory / 1024.0 / 1024,
                nativeHeapUsedMB,
                nativeHeapTotalMB
            )
        )
    }
}
