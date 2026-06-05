package com.ryzix.vm.qemu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.ryzix.vm.MainActivity
import com.ryzix.vm.R

class QEMUService : Service() {

    private val binder = LocalBinder()
    private val TAG = "QEMUService"
    private val CHANNEL_ID = "ryzix_vm_channel"
    private val NOTIFICATION_ID = 1

    inner class LocalBinder : Binder() {
        fun getService(): QEMUService = this@QEMUService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vmName = intent?.getStringExtra("vm_name") ?: "VM"
        startForeground(NOTIFICATION_ID, buildNotification(vmName))
        Log.i(TAG, "QEMUService started for: $vmName")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        QEMUBridge.stopQEMU()
        Log.i(TAG, "QEMUService destroyed")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ryzix VM",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Virtual Machine running in background"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(vmName: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Ryzix VM")
            .setContentText("$vmName is running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
