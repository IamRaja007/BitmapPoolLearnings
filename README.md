# 🧪 Android Custom Image Loader Benchmark

A no-library, from-scratch low-tech image loader built to understand how libraries like Glide and Coil might work under the hood.

---

## 🎯 Why I Built This

Instead of using Glide blindly, I built my own image loader to explore:

* What happens without downsampling?
* Can I reuse bitmaps safely?
* Does cancelling offscreen image loads help?
* What do GC and memory stats reveal during scroll?

---

## 🛠️ Benchmark Setup

* Loads shuffled high-res images from [Picsum](https://picsum.photos)
* Displays in a `RecyclerView`
* Benchmark flags:

  ```kotlin
  data class BenchmarkConfig(
      val usePool: Boolean,
      val useDownsampling: Boolean,
      val useCancellation: Boolean
  )
  ```
* Measures scroll time, memory usage, GC events, and network stats
* Automated scroll flow for consistent results

---

## 🧵 Image Loading Flow

1. Cancel task if ViewHolder is recycled
2. Decode via `BitmapFactory`
3. Downsample based on `ImageView` size
4. Try to reuse bitmap from custom pool
5. Update UI only if still bound to correct image

---

## 📊 Key Findings

| Feature             | Impact                                           |
| ------------------- | ------------------------------------------------ |
| ❌ No Optimization   | High memory, GC storms, janky scroll             |
| ✅ Downsampling      | Lower memory, faster decode, fewer GC events     |
| ✅ Bitmap Pooling    | Reduces allocations, tricky to get right         |
| ✅ Task Cancellation | Huge win during flings, saves CPU & memory       |
| 🏆 All Combined     | Smoothest scroll, minimal GC, lowest network use |

---

## 🧠 Lessons Learned

* Reusing bitmaps requires exact match + `inMutable=true`
* Downsampling is a no-brainer
* Cancellation improves UX and performance drastically
* GC and memory stats are measurable (`Debug.getRuntimeStat()`)

---

## ❤️ Final Thoughts

Don’t use this in production — use Glide or Coil.

But if you're curious, bored, or want to learn: build one.
You'll walk away with a deep understanding of bitmaps, memory, and image loading internals.

---
