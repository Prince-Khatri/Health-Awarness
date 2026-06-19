package com.example.medicinetimer

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

class MealWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_CONTROL = "com.example.medicinetimer.ACTION_WIDGET_CONTROL"
        const val ACTION_WIDGET_EXTEND = "com.example.medicinetimer.ACTION_WIDGET_EXTEND"
        const val ACTION_UPDATE_WIDGET = "com.example.medicinetimer.ACTION_UPDATE_WIDGET"

        private const val PREFS_NAME = "MealPrefs"
        private const val KEY_STEP = "current_step"
        private const val KEY_MEAL_TYPE = "current_meal_type"

        // =================================================================
        // TIME CONFIGURATION ZONE (Change your intervals easily here)
        // =================================================================
        private const val IS_DEBUG_MODE = true // Set to false for Production deployment

        // Production Config (Real-world timings)
        private const val PROD_EATING_TIMEOUT_MS = 30L * 60 * 1000  // 30 minutes
        private const val PROD_EXTENSION_TIME_MS = 10L * 60 * 1000  // 10 minutes
        private const val PROD_MED1_DELAY_MS     = 15L * 60 * 1000  // 15 minutes
        private const val PROD_MED2_DELAY_MS     = 30L * 60 * 1000  // 30 minutes

        // Debug Testing Config (Fast execution intervals)
        private const val DEBUG_EATING_TIMEOUT_MS = 30_000L          // 30 seconds
        private const val DEBUG_EXTENSION_TIME_MS = 15_000L          // 15 seconds
        private const val DEBUG_MED1_DELAY_MS     = 15_000L          // 15 seconds
        private const val DEBUG_MED2_DELAY_MS     = 30_000L          // 30 seconds

        // Dynamic getters to automatically switch between Debug and Production modes
        private val EATING_TIMEOUT_MS: Long
            get() = if (IS_DEBUG_MODE) DEBUG_EATING_TIMEOUT_MS else PROD_EATING_TIMEOUT_MS

        private val EXTENSION_TIME_MS: Long
            get() = if (IS_DEBUG_MODE) DEBUG_EXTENSION_TIME_MS else PROD_EXTENSION_TIME_MS

        private val MED1_DELAY_MS: Long
            get() = if (IS_DEBUG_MODE) DEBUG_MED1_DELAY_MS else PROD_MED1_DELAY_MS

        private val MED2_DELAY_MS: Long
            get() = if (IS_DEBUG_MODE) DEBUG_MED2_DELAY_MS else PROD_MED2_DELAY_MS
        // =================================================================

        fun updateAllWidgets(context: Context, step: Int, status: String, mealType: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_STEP, step).putString(KEY_MEAL_TYPE, mealType).apply()

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MealWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            if (appWidgetIds.isNotEmpty()) {
                val views = RemoteViews(context.packageName, R.layout.meal_widget)
                drawWidget(context, views, step, status, mealType)
                appWidgetManager.updateAppWidget(appWidgetIds, views)
            }
        }

        private fun getSuggestedMeal(): String {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return if (hour in 5..14) "BREAKFAST" else "DINNER"
        }

        private fun drawWidget(context: Context, views: RemoteViews, step: Int, status: String, mealType: String) {
            views.setTextViewText(R.id.widget_title, "$mealType Tracker${if (IS_DEBUG_MODE) " (DB)" else ""}")
            views.setTextViewText(R.id.widget_status, status)

            val activeColor = Color.parseColor("#4CAF50")
            val inactiveColor = Color.parseColor("#CCCCCC")
            views.setTextColor(R.id.dot1, if (step >= 1) activeColor else inactiveColor)
            views.setTextColor(R.id.dot2, if (step >= 2) activeColor else inactiveColor)
            views.setTextColor(R.id.dot3, if (step >= 3) activeColor else inactiveColor)
            views.setTextColor(R.id.dot4, if (step >= 4) activeColor else inactiveColor)

            val buttonText = when(step) {
                0 -> "START MEAL"
                1 -> "BEFORE MED TAKEN"
                2 -> "I'M DONE EATING"
                3 -> "15m MED (LOCKED)"
                4 -> "30m MED (LOCKED)"
                else -> "RESET WORKFLOW"
            }
            views.setTextViewText(R.id.btn_widget_action, buttonText)

            val btnColor = when(step) {
                0 -> "#4CAF50"
                3, 4 -> "#555555" // Visual dull lock color representation
                else -> "#2196F3"
            }
            views.setInt(R.id.btn_widget_action, "setBackgroundColor", Color.parseColor(btnColor))
            views.setViewVisibility(R.id.btn_widget_extend, if (step == 2) View.VISIBLE else View.GONE)

            val baseId = if (mealType == "BREAKFAST") 6000 else 7000

            val controlIntent = Intent(context, MealWidget::class.java).apply {
                action = ACTION_WIDGET_CONTROL
                putExtra("MEAL_TYPE", mealType)
            }
            val controlPI = PendingIntent.getBroadcast(context, baseId + 1, controlIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_widget_action, controlPI)

            val extendIntent = Intent(context, MealWidget::class.java).apply {
                action = ACTION_WIDGET_EXTEND
                putExtra("MEAL_TYPE", mealType)
            }
            val extendPI = PendingIntent.getBroadcast(context, baseId + 2, extendIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_widget_extend, extendPI)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mealType = intent.getStringExtra("MEAL_TYPE") ?: prefs.getString(KEY_MEAL_TYPE, "BREAKFAST") ?: "BREAKFAST"

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        when (action) {
            ACTION_WIDGET_CONTROL -> {
                val step = prefs.getInt(KEY_STEP, 0)

                // Enforce interaction stage lock parameters for 3 & 4
                if (step == 3 || step == 4) return

                when (step) {
                    0 -> {
                        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                        prefs.edit().putString("LAST_STARTED_DATE_$mealType", today).apply()

                        cancelAlarmByCode(context, alarmManager, MedicineReceiver.RC_NAG_BREAKFAST, Intent(context, MedicineReceiver::class.java))
                        cancelAlarmByCode(context, alarmManager, MedicineReceiver.RC_NAG_DINNER, Intent(context, MedicineReceiver::class.java))

                        updateAllWidgets(context, 1, "Before-Med Taken. Start Food?", mealType)
                    }
                    1 -> {
                        cancelAlarmByCode(context, alarmManager, MedicineReceiver.RC_NAG_BREAKFAST, Intent(context, MedicineReceiver::class.java))
                        cancelAlarmByCode(context, alarmManager, MedicineReceiver.RC_NAG_DINNER, Intent(context, MedicineReceiver::class.java))

                        val checkIntent = Intent(context, ActionReceiver::class.java).apply {
                            this.action = ActionReceiver.ACTION_CHECK_DONE
                            putExtra("MEAL_TYPE", mealType)
                        }
                        val reqCode = if (mealType == "BREAKFAST") ActionReceiver.RC_CHECK_BREAKFAST else ActionReceiver.RC_CHECK_DINNER
                        val pi = PendingIntent.getBroadcast(context, reqCode, checkIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + EATING_TIMEOUT_MS, pi)

                        val readableTime = if (IS_DEBUG_MODE) "${DEBUG_EATING_TIMEOUT_MS / 1000}s" else "30m"
                        updateAllWidgets(context, 2, "Eating $mealType ($readableTime)", mealType)
                    }
                    2 -> {
                        cancelAlarmByCode(context, alarmManager, ActionReceiver.RC_CHECK_BREAKFAST, Intent(context, ActionReceiver::class.java))
                        cancelAlarmByCode(context, alarmManager, ActionReceiver.RC_CHECK_DINNER, Intent(context, ActionReceiver::class.java))

                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(2)

                        scheduleMedAlarm(context, alarmManager, mealType, MED1_DELAY_MS, 1, ActionReceiver.RC_MED1_BREAKFAST, ActionReceiver.RC_MED1_DINNER)
                        scheduleMedAlarm(context, alarmManager, mealType, MED2_DELAY_MS, 2, ActionReceiver.RC_MED2_BREAKFAST, ActionReceiver.RC_MED2_DINNER)

                        val readableTime = if (IS_DEBUG_MODE) "${DEBUG_MED1_DELAY_MS / 1000}s" else "15m"
                        updateAllWidgets(context, 3, "Waiting for Med 1 ($readableTime)", mealType)
                    }
                    else -> {
                        performGlobalReset(context, mealType)
                    }
                }
            }
            ACTION_WIDGET_EXTEND -> {
                val step = prefs.getInt(KEY_STEP, 0)
                val count = prefs.getInt("EXTENSION_COUNT_$mealType", 0)

                if (step == 2 && count < 3) {
                    cancelAlarmByCode(context, alarmManager, ActionReceiver.RC_CHECK_BREAKFAST, Intent(context, ActionReceiver::class.java))
                    cancelAlarmByCode(context, alarmManager, ActionReceiver.RC_CHECK_DINNER, Intent(context, ActionReceiver::class.java))

                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(2)

                    prefs.edit().putInt("EXTENSION_COUNT_$mealType", count + 1).apply()

                    val checkIntent = Intent(context, ActionReceiver::class.java).apply {
                        this.action = ActionReceiver.ACTION_CHECK_DONE
                        putExtra("MEAL_TYPE", mealType)
                    }
                    val reqCode = if (mealType == "BREAKFAST") ActionReceiver.RC_CHECK_BREAKFAST else ActionReceiver.RC_CHECK_DINNER
                    val pi = PendingIntent.getBroadcast(context, reqCode, checkIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + EXTENSION_TIME_MS, pi)

                    updateAllWidgets(context, 2, "Extended (${count + 1}/3)", mealType)
                }
            }
            ACTION_UPDATE_WIDGET -> {
                val newStep = intent.getIntExtra("STEP", 0)
                val status = intent.getStringExtra("STATUS") ?: "In Progress"
                updateAllWidgets(context, newStep, status, mealType)
            }
        }
    }

    private fun cancelAlarmByCode(context: Context, am: AlarmManager, code: Int, intentBase: Intent) {
        PendingIntent.getBroadcast(context, code, intentBase, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            am.cancel(it)
            it.cancel()
        }
    }

    private fun scheduleMedAlarm(context: Context, am: AlarmManager, mealType: String, delayMillis: Long, index: Int, brCode: Int, dnCode: Int) {
        val intent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_AFTER_MED
            putExtra("MEAL_TYPE", mealType)
            putExtra("MED_INDEX", index)
        }
        val code = if (mealType == "BREAKFAST") brCode else dnCode
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMillis, pi)
    }

    private fun performGlobalReset(context: Context, mealType: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val medReceiverIntent = Intent(context, MedicineReceiver::class.java)
        val actionReceiverIntent = Intent(context, ActionReceiver::class.java)

        val codesToClear = intArrayOf(
            MedicineReceiver.RC_NAG_BREAKFAST, MedicineReceiver.RC_NAG_DINNER,
            ActionReceiver.RC_CHECK_BREAKFAST, ActionReceiver.RC_CHECK_DINNER,
            ActionReceiver.RC_MED1_BREAKFAST, ActionReceiver.RC_MED2_BREAKFAST,
            ActionReceiver.RC_MED1_DINNER, ActionReceiver.RC_MED2_DINNER
        )

        for (code in codesToClear) {
            val targetIntent = if (code < 3000) medReceiverIntent else actionReceiverIntent
            PendingIntent.getBroadcast(context, code, targetIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("LAST_STARTED_DATE_$mealType")
            remove("EXTENSION_COUNT_$mealType")
            remove(KEY_STEP)
            apply()
        }

        updateAllWidgets(context, 0, "Ready", getSuggestedMeal())
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val step = prefs.getInt(KEY_STEP, 0)
        val mealType = prefs.getString(KEY_MEAL_TYPE, getSuggestedMeal()) ?: "BREAKFAST"
        updateAllWidgets(context, step, if (step == 0) "Ready" else "In Progress", mealType)
    }
}