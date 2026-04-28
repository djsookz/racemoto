package com.example.clinometer.main.session

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import com.example.clinometer.ForegroundService

object NavigationServiceLauncher {

    fun connect(
        context: Context,
        isServiceRunning: Boolean,
        isNavigationActive: Boolean,
        serviceConnection: ServiceConnection
    ) {
        when {
            isServiceRunning -> bindToRunningForegroundService(context, serviceConnection)
            isNavigationActive -> startAndBindNavigationService(context, serviceConnection)
        }
    }

    private fun bindToRunningForegroundService(
        context: Context,
        serviceConnection: ServiceConnection
    ) {
        context.bindService(
            Intent(context, ForegroundService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun startAndBindNavigationService(
        context: Context,
        serviceConnection: ServiceConnection
    ) {
        val serviceIntent = Intent(context, ForegroundService::class.java).apply {
            putExtra("PRE_WARMING_MODE", true)
        }
        context.startService(serviceIntent)

        val activateIntent = Intent(context, ForegroundService::class.java).apply {
            putExtra("ACTIVATE_NORMAL_MODE", true)
        }
        context.startService(activateIntent)

        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}
