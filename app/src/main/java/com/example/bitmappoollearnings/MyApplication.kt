package com.example.bitmappoollearnings

import android.app.Application

class MyApplication : Application() {

   val simpleBitmapPool by lazy {
       SimpleBitmapPool(5 * 1024) //5MB
   }

    override fun onCreate() {
        super.onCreate()
    }
}