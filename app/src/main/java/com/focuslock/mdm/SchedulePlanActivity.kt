package com.focuslock.mdm

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SchedulePlanActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_plan)

        val prefs = getSharedPreferences(Constants.PREFS_MAIN, MODE_PRIVATE)
        val text = prefs.getString(Constants.KEY_PLAN_TEXT, "") ?: ""
        findViewById<TextView>(R.id.tvPlanText).text = text
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
    }
}
