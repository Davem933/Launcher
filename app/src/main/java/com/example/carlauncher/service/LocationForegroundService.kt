package com.example.carlauncher.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class LocationForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
