package com.example.medicinetimer

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            scheduleDailySafetyAlarms()
            Toast.makeText(this, "Daily safety alarms scheduled (8:31 AM / 6:31 PM)", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnBreakfastNow).setOnClickListener {
            triggerMealReminder("BREAKFAST")
        }

        findViewById<Button>(R.id.btnDinnerNow).setOnClickListener {
            triggerMealReminder("DINNER")
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                } catch (e: Exception) {}
            }
        }
    }

    private fun scheduleDailySafetyAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Safety alarm at 8:31 AM
        scheduleMealAlarm(alarmManager, 8, 31, "BREAKFAST", 1001)
        // Safety alarm at 6:31 PM
        scheduleMealAlarm(alarmManager, 18, 31, "DINNER", 1002)
    }

    private fun scheduleMealAlarm(alarmManager: AlarmManager, hour: Int, minute: Int, mealType: String, requestCode: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        val intent = Intent(this, MedicineReceiver::class.java).apply {
            action = MedicineReceiver.ACTION_MEAL_REMINDER
            putExtra("MEAL_TYPE", mealType)
            putExtra("MANUAL_START", false) // This is the automatic safety alarm
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Toast.makeText(this, "Exact alarm permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerMealReminder(mealType: String) {
        val intent = Intent(this, MedicineReceiver::class.java).apply {
            action = MedicineReceiver.ACTION_MEAL_REMINDER
            putExtra("MEAL_TYPE", mealType)
            putExtra("MANUAL_START", true)
        }
        sendBroadcast(intent)
        Toast.makeText(this, "Starting $mealType flow...", Toast.LENGTH_SHORT).show()
    }
}
