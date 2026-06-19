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

class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START_EATING = "com.example.medicinetimer.ACTION_START_EATING"
        const val ACTION_DONE = "com.example.medicinetimer.ACTION_DONE"
        const val ACTION_CHECK_DONE = "com.example.medicinetimer.ACTION_CHECK_DONE"
        const val ACTION_EXTEND = "com.example.medicinetimer.ACTION_EXTEND"
        const val ACTION_AFTER_MED = "com.example.medicinetimer.ACTION_AFTER_MED"

        // Explicit unique codes to completely isolate schedules
        const val RC_CHECK_BREAKFAST = 3001
        const val RC_CHECK_DINNER = 3002
        const val RC_MED1_BREAKFAST = 4001
        const val RC_MED2_BREAKFAST = 4002
        const val RC_MED1_DINNER = 5001
        const val RC_MED2_DINNER = 5002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val mealType = intent.getStringExtra("MEAL_TYPE") ?: "BREAKFAST"

        when (action) {
            ACTION_START_EATING -> {
                cancelNagAlarms(context, mealType)
                context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE).edit()
                    .putInt("current_step", 2).apply()

                scheduleNextCheck(context, mealType, 30) // Primary 30 mins Eating Timer
                updateWidget(context, 2, "Eating $mealType...", mealType)
            }
            ACTION_DONE -> {
                handleDone(context, mealType)
            }
            ACTION_CHECK_DONE -> {
                showCheckDoneNotification(context, mealType)
            }
            ACTION_EXTEND -> {
                handleExtend(context, mealType)
            }
            ACTION_AFTER_MED -> {
                val medIndex = intent.getIntExtra("MED_INDEX", 1)
                handleAfterMed(context, mealType, medIndex)
            }
        }
    }

    private fun handleDone(context: Context, mealType: String) {
        cancelCheckDoneAlarm(context, mealType)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(2) // Remove locking food alert

        context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE).edit().apply {
            putInt("EXTENSION_COUNT_$mealType", 0)
            putInt("CURRENT_STEP_$mealType", 3)
            apply()
        }

        // Med 1: Post-meal 15 minutes
        scheduleAfterMealMedicine(context, mealType, 1, 1)
        // Med 2: Post-meal 30 minutes
        scheduleAfterMealMedicine(context, mealType, 2, 2)

        updateWidget(context, 3, "Waiting for Med 1 (15m)", mealType)
    }

    private fun handleExtend(context: Context, mealType: String) {
        val prefs = context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("EXTENSION_COUNT_$mealType", 0)

        if (count < 3) {
            prefs.edit().putInt("EXTENSION_COUNT_$mealType", count + 1).apply()
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(2)

            scheduleNextCheck(context, mealType, 1) //10
            updateWidget(context, 2, "Extended (${count + 1}/3)", mealType)
        }
    }

    private fun showCheckDoneNotification(context: Context, mealType: String) {
        val prefs = context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("EXTENSION_COUNT_$mealType", 0)

        val channelId = "medicine_alarm_channel"
        createNotificationChannel(context, channelId)

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⚠️ Finish your $mealType!")
            .setContentText("Extension: $count/3. Tap widget button to complete.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setOngoing(true)
            .setAutoCancel(false)

        try {
            NotificationManagerCompat.from(context).notify(2, builder.build())
        } catch (e: SecurityException) {}

        // If maximum extensions (3) reached, loop alarm every 5 mins aggressively
        if (count >= 3) {
            scheduleNextCheck(context, mealType, 5)
            updateWidget(context, 2, "MUST CONFIRM DONE!", mealType)
        } else {
            updateWidget(context, 2, "Are you done?", mealType)
        }
    }

    private fun handleAfterMed(context: Context, mealType: String, medIndex: Int) {
        val step = if (medIndex == 1) 3 else 4
        val status = if (medIndex == 1) "Take 15m Med" else "Take 30m Med"

        context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE).edit()
            .putInt("CURRENT_STEP_$mealType", step).apply()

        updateWidget(context, step, status, mealType)

        val channelId = "medicine_alarm_channel"
        createNotificationChannel(context, channelId)

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⚠️ $mealType Medicine Time ($medIndex/2)")
            .setContentText("Please take your medication immediately.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setOngoing(true) // Keeps it ringing/visible until they interact
            .setAutoCancel(false)

        try {
            val notificationId = if (mealType == "BREAKFAST") 20 + medIndex else 30 + medIndex
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {}
    }

    private fun cancelNagAlarms(context: Context, mealType: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MedicineReceiver::class.java).apply { action = MedicineReceiver.ACTION_MEAL_REMINDER }
        val code = if (mealType == "BREAKFAST") MedicineReceiver.RC_NAG_BREAKFAST else MedicineReceiver.RC_NAG_DINNER
        PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun cancelCheckDoneAlarm(context: Context, mealType: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ActionReceiver::class.java).apply { action = ACTION_CHECK_DONE }
        val reqCode = if (mealType == "BREAKFAST") RC_CHECK_BREAKFAST else RC_CHECK_DINNER
        PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun scheduleNextCheck(context: Context, mealType: String, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ActionReceiver::class.java).apply {
            action = ACTION_CHECK_DONE
            putExtra("MEAL_TYPE", mealType)
        }
        val reqCode = if (mealType == "BREAKFAST") RC_CHECK_BREAKFAST else RC_CHECK_DINNER
        val pendingIntent = PendingIntent.getBroadcast(
            context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + (minutes.toLong() * 60 * 1000)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }

    private fun scheduleAfterMealMedicine(context: Context, mealType: String, minutes: Int, medIndex: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ActionReceiver::class.java).apply {
            action = ACTION_AFTER_MED
            putExtra("MEAL_TYPE", mealType)
            putExtra("MED_INDEX", medIndex)
        }
        val reqCode = if (mealType == "BREAKFAST") {
            if (medIndex == 1) RC_MED1_BREAKFAST else RC_MED2_BREAKFAST
        } else {
            if (medIndex == 1) RC_MED1_DINNER else RC_MED2_DINNER
        }
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

    private fun updateWidget(context: Context, step: Int, status: String, mealType: String) {
        val widgetIntent = Intent(context, MealWidget::class.java).apply {
            action = MealWidget.ACTION_UPDATE_WIDGET
            putExtra("STEP", step)
            putExtra("STATUS", status)
            putExtra("MEAL_TYPE", mealType)
        }
        context.sendBroadcast(widgetIntent)
    }
}