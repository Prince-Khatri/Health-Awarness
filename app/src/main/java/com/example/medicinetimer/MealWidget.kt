package com.example.medicinetimer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import java.util.*

class MealWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_CONTROL = "com.example.medicinetimer.ACTION_WIDGET_CONTROL"
        const val ACTION_WIDGET_EXTEND = "com.example.medicinetimer.ACTION_WIDGET_EXTEND"
        const val ACTION_UPDATE_WIDGET = "com.example.medicinetimer.ACTION_UPDATE_WIDGET"

        private const val PREFS_NAME = "MealPrefs"
        private const val KEY_STEP = "current_step"
        private const val KEY_MEAL_TYPE = "current_meal_type"

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
            views.setTextViewText(R.id.widget_title, "$mealType Tracker")
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
                3 -> "15m MED TAKEN"
                4 -> "30m MED TAKEN"
                else -> "RESET"
            }
            views.setTextViewText(R.id.btn_widget_action, buttonText)

            val btnColor = when(step) {
                0 -> "#4CAF50"
                3, 4 -> "#9C27B0"
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
        val mealType = intent.getStringExtra("MEAL_TYPE") ?: prefs.getString(KEY_MEAL_TYPE, getSuggestedMeal()) ?: "MEAL"

        when (action) {
            ACTION_WIDGET_CONTROL -> {
                val step = prefs.getInt(KEY_STEP, 0)
                when (step) {
                    0 -> {
                        val startIntent = Intent(context, MedicineReceiver::class.java).apply {
                            this.action = MedicineReceiver.ACTION_MEAL_REMINDER
                            putExtra("MEAL_TYPE", mealType)
                            putExtra("MANUAL_START", true)
                        }
                        context.sendBroadcast(startIntent)
                    }
                    1 -> {
                        val startEatingIntent = Intent(context, ActionReceiver::class.java).apply {
                            this.action = ActionReceiver.ACTION_START_EATING
                            putExtra("MEAL_TYPE", mealType)
                        }
                        context.sendBroadcast(startEatingIntent)
                    }
                    2 -> {
                        val doneIntent = Intent(context, ActionReceiver::class.java).apply {
                            this.action = ActionReceiver.ACTION_DONE
                            putExtra("MEAL_TYPE", mealType)
                        }
                        context.sendBroadcast(doneIntent)
                    }
                    3 -> {
                        // FIX: Increment from Step 3 to Step 4 so button text changes to "30m MED TAKEN"
                        updateAllWidgets(context, 4, "Took 15m Med. Waiting for 30m Med...", mealType)
                    }
                    4 -> {
                        // Increment to Step 5 to safely trigger the completion layout state
                        updateAllWidgets(context, 5, "All Medicines Taken! 👍", mealType)
                    }
                    else -> {
                        prefs.edit().remove("LAST_STARTED_DATE_$mealType").apply()
                        updateAllWidgets(context, 0, "Ready", getSuggestedMeal())
                    }
                }
            }
            ACTION_WIDGET_EXTEND -> {
                val extendIntent = Intent(context, ActionReceiver::class.java).apply {
                    this.action = ActionReceiver.ACTION_EXTEND
                    putExtra("MEAL_TYPE", mealType)
                }
                context.sendBroadcast(extendIntent)
            }
            ACTION_UPDATE_WIDGET -> {
                val newStep = intent.getIntExtra("STEP", 0)
                val status = intent.getStringExtra("STATUS") ?: "In Progress"
                updateAllWidgets(context, newStep, status, mealType)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val step = prefs.getInt(KEY_STEP, 0)
        val mealType = prefs.getString(KEY_MEAL_TYPE, getSuggestedMeal()) ?: "MEAL"
        updateAllWidgets(context, step, if (step == 0) "Ready" else "In Progress", mealType)
    }
}