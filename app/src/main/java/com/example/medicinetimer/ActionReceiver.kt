package com.example.medicinetimer

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START_EATING = "com.example.medicinetimer.ACTION_START_EATING"
        const val ACTION_DONE = "com.example.medicinetimer.ACTION_DONE"
        const val ACTION_CHECK_DONE = "com.example.medicinetimer.ACTION_CHECK_DONE"
        const val ACTION_EXTEND = "com.example.medicinetimer.ACTION_EXTEND"
        const val ACTION_AFTER_MED = "com.example.medicinetimer.ACTION_AFTER_MED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val mealType = intent.getStringExtra("MEAL_TYPE") ?: "MEAL"

        when (action) {
            ACTION_START_EATING -> {
                cancelCheckDoneAlarm(context, mealType)
                // Start eating phase - check if done in 30 mins
                scheduleNextCheck(context, mealType, 30)
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
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(2)
        cancelCheckDoneAlarm(context, mealType)

        // --- THE FIXED CALCULATIONS ---
        // Med 1: 15 mins after meal completion
        scheduleAfterMealMedicine(context, mealType, 15, 1)

        // Med 2: 30 mins AFTER Med 1 = 15 + 30 = 45 mins after meal completion
        scheduleAfterMealMedicine(context, mealType, 30, 2)

        context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE)
            .edit().putInt("EXTENSION_COUNT_$mealType", 0).apply()

        updateWidget(context, 3, "Waiting for Med 1 (15m)", mealType)
    }

    private fun handleExtend(context: Context, mealType: String) {
        val prefs = context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("EXTENSION_COUNT_$mealType", 0)

        if (count < 3) {
            prefs.edit().putInt("EXTENSION_COUNT_$mealType", count + 1).apply()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(2)

            scheduleNextCheck(context, mealType, 10)
            updateWidget(context, 2, "Extended (${count + 1}/3)", mealType)
        }
    }

    private fun showCheckDoneNotification(context: Context, mealType: String) {
        val prefs = context.getSharedPreferences("MealPrefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("EXTENSION_COUNT_$mealType", 0)

        val channelId = "medicine_alarm_channel"
        val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Finish your $mealType!")
            .setContentText("Extension: $count/3. Use widget to confirm DONE.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setOngoing(true)
            .setAutoCancel(false)

        try {
            NotificationManagerCompat.from(context).notify(2, builder.build())
        } catch (e: SecurityException) {}

        if (count >= 3) {
            scheduleNextCheck(context, mealType, 5)
        }

        updateWidget(context, 2, "Are you done?", mealType)
    }

    private fun handleAfterMed(context: Context, mealType: String, medIndex: Int) {
        // Step 3 for first post-meal med, Step 4 for second post-meal med
        val step = if (medIndex == 1) 3 else 4
        val status = if (medIndex == 1) "Take 15m Med" else "Take 30m Med"
        updateWidget(context, step, status, mealType)

        val channelId = "medicine_alarm_channel"
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$mealType Medicine Alarm")
            .setContentText("Time to take your medicine ($medIndex/2).")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        try {
            // Unique notification ID per meal + med index pair
            val notificationId = if (mealType == "BREAKFAST") 20 + medIndex else 30 + medIndex
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
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

    private fun cancelCheckDoneAlarm(context: Context, mealType: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ActionReceiver::class.java).apply {
            action = ACTION_CHECK_DONE
            putExtra("MEAL_TYPE", mealType)
        }
        val reqCode = if (mealType == "BREAKFAST") 201 else 202
        val pendingIntent = PendingIntent.getBroadcast(
            context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun scheduleNextCheck(context: Context, mealType: String, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ActionReceiver::class.java).apply {
            action = ACTION_CHECK_DONE
            putExtra("MEAL_TYPE", mealType)
        }
        val reqCode = if (mealType == "BREAKFAST") 201 else 202
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
        // Unique request codes for breakfast med 1/2 vs dinner med 1/2
        val baseCode = if (mealType == "BREAKFAST") 300 else 400
        val pendingIntent = PendingIntent.getBroadcast(
            context, baseCode + medIndex, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + (minutes.toLong() * 60 * 1000)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }
}