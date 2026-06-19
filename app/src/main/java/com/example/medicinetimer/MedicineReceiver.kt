package com.example.medicinetimer

import android.app.AlarmManager
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

        const val RC_DAILY_BREAKFAST = 1001
        const val RC_DAILY_DINNER = 1002
        const val RC_NAG_BREAKFAST = 2001
        const val RC_NAG_DINNER = 2002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val mealType = intent.getStringExtra("MEAL_TYPE") ?: "BREAKFAST"
        val isManual = intent.getBooleanExtra("MANUAL_START", false)

        if (action == ACTION_MEAL_REMINDER) {
            val prefs = context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE)
            val currentStep = prefs.getInt("current_step", 0)

            // If an automatic safety sweep triggers but you already started or completed the cycle, skip it
            if (!isManual && isMealAlreadyStarted(context, mealType)) {
                return
            }

            // Core change: If the user hasn't advanced past Step 0 or Step 1, fire notifications and loop
            if (currentStep <= 1) {
                showBeforeMealNotification(context, mealType)
                // Schedule the recursive 10-minute nagging reminder
                scheduleNagAlarm(context, mealType, 10)
            }
        }
    }

    private fun isMealAlreadyStarted(context: Context, mealType: String): Boolean {
        val prefs = context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return today == prefs.getString("LAST_STARTED_DATE_$mealType", "")
    }

    private fun showBeforeMealNotification(context: Context, mealType: String) {
        val channelId = "medicine_alarm_channel"
        createNotificationChannel(context, channelId)

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⚠️ $mealType MEDICINE TIME NOW") // Customized to show meal type
            .setContentText("Please take your medicine BEFORE eating. Tap the widget to confirm!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setOngoing(true)
            .setAutoCancel(false)

        try {
            val notifyId = if (mealType == "BREAKFAST") 10 else 11
            NotificationManagerCompat.from(context).notify(notifyId, builder.build())
        } catch (e: SecurityException) {}
    }

    private fun scheduleNagAlarm(context: Context, mealType: String, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MedicineReceiver::class.java).apply {
            action = ACTION_MEAL_REMINDER
            putExtra("MEAL_TYPE", mealType)
            putExtra("MANUAL_START", true)
        }
        val reqCode = if (mealType == "BREAKFAST") RC_NAG_BREAKFAST else RC_NAG_DINNER
        val pendingIntent = PendingIntent.getBroadcast(
            context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + (minutes.toLong() * 60 * 1000)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }

    private fun createNotificationChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Medicine & Meal Alarms"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = "CRITICAL: Urgent alarms for medicine timings"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)

                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM) // Forces sound out of alarm stream even if phone is on vibrate
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}