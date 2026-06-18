package com.example.medicinetimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.*

class MedicineReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MEAL_REMINDER = "com.example.medicinetimer.ACTION_MEAL_REMINDER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val mealType = intent.getStringExtra("MEAL_TYPE") ?: "MEAL"
        
        if (action == ACTION_MEAL_REMINDER) {
            if (isMealAlreadyStarted(context, mealType)) {
                return 
            }
            startMealFlow(context, mealType)
        }
    }

    private fun isMealAlreadyStarted(context: Context, mealType: String): Boolean {
        val prefs = context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastStartedDate = prefs.getString("LAST_STARTED_DATE_$mealType", "")
        return today == lastStartedDate
    }

    fun startMealFlow(context: Context, mealType: String) {
        val prefs = context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        prefs.edit().apply {
            putString("LAST_STARTED_DATE_$mealType", today)
            putInt("EXTENSION_COUNT_$mealType", 0)
            apply()
        }

        updateWidget(context, 1, "Take Before-Med", mealType)
        showBeforeMealNotification(context, mealType)
        scheduleCheckDoneAlarm(context, mealType, 30)
    }

    private fun showBeforeMealNotification(context: Context, mealType: String) {
        val channelId = "medicine_alarm_channel"
        createNotificationChannel(context, channelId)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Meal Alert: $mealType")
            .setContentText("Please take your medicine BEFORE eating.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(1, builder.build())
        } catch (e: SecurityException) {}
    }

    private fun updateWidget(context: Context, step: Int, status: String, mealType: String) {
        val widgetIntent = Intent(context, MealWidget::class.java).apply {
            action = MealWidget.ACTION_UPDATE_WIDGET
            putExtra("STEP", step)
            putExtra("STATUS", status)
            putExtra("MEAL_TYPE", mealType)
        }
        context.sendBroadcast(widgetIntent)
    }

    private fun scheduleCheckDoneAlarm(context: Context, mealType: String, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_CHECK_DONE
            putExtra("MEAL_TYPE", mealType)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + (minutes.toLong() * 60 * 1000)
        alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }

    private fun createNotificationChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Medicine & Meal Alarms"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = "Loud alarms for medicine timings"
                enableVibration(true)
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
