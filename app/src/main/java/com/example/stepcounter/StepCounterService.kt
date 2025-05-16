package com.example.stepcounter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class StepCounterService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    // Valor base capturado en la primera lectura para conteo relativo
    private var baseline: Int? = null
    // Conteo de pasos a partir del baseline
    private var stepCount = 0
    // Almacena el último conteo notificado para actualizar cada 2 pasos
    private var lastUpdatedStepCount = 0

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.also { sensor ->
            // Usando SENSOR_DELAY_UI para actualizaciones un poco más rápidas
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        startForegroundServiceWithNotification()
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "step_counter_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_DEFAULT para garantizar que la notificación sea visible en la barra
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_title),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val notification = buildNotification()
        startForeground(1, notification)
    }

    private fun buildNotification(): Notification {
        // Pending intent para abrir la MainActivity al hacer clic en la notificación
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "step_counter_channel")
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.steps_count, stepCount))
            .setSmallIcon(R.drawable.ic_footsteps)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Notificación permanente
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY garantiza que el servicio sea reiniciado si es finalizado
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            // El sensor devuelve el total de pasos desde el arranque
            val totalSteps = event.values[0].toInt()

            // Si aún no se ha definido el baseline, almacena el valor actual
            if (baseline == null) {
                baseline = totalSteps
            }
            // Calcula los pasos dados a partir del baseline
            stepCount = totalSteps - (baseline ?: totalSteps)

            // Actualiza solo si la diferencia es de 2 pasos o más
            if (stepCount - lastUpdatedStepCount >= 2) {
                lastUpdatedStepCount = stepCount

                // Actualiza la notificación
                val notification = buildNotification()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(1, notification)

                // Envía broadcast para actualizar la UI de MainActivity
                val uiIntent = Intent("com.example.stepcounter.STEP_UPDATE")
                uiIntent.putExtra("step_count", stepCount)
                sendBroadcast(uiIntent)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No implementado
    }
}