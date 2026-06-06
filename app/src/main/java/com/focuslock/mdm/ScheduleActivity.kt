package com.focuslock.mdm

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import java.util.Calendar
import java.util.Locale

class ScheduleActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var planText: EditText
    private lateinit var tabLayout: TabLayout
    private lateinit var tabTasks: View
    private lateinit var tabWindows: View
    private lateinit var tabPlan: View
    private lateinit var tabAnalytics: View
    private lateinit var taskContainer: LinearLayout
    private lateinit var addTaskButton: Button
    private lateinit var analyticsSummary: TextView
    private lateinit var analyticsStats: LinearLayout
    private lateinit var analyticsTopApps: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        container = findViewById(R.id.containerSchedules)
        planText = findViewById(R.id.etPlanText)
        tabLayout = findViewById(R.id.tabSchedule)
        tabTasks = findViewById(R.id.tabTasks)
        tabWindows = findViewById(R.id.tabWindows)
        tabPlan = findViewById(R.id.tabPlan)
        tabAnalytics = findViewById(R.id.tabAnalytics)
        taskContainer = findViewById(R.id.containerTasks)
        addTaskButton = findViewById(R.id.btnAddTask)
        analyticsSummary = findViewById(R.id.tvAnalyticsSummary)
        analyticsStats = findViewById(R.id.analyticsStatsContainer)
        analyticsTopApps = findViewById(R.id.analyticsTopAppsContainer)

        val addButton = findViewById<Button>(R.id.btnAddWindow)
        val savePlanButton = findViewById<Button>(R.id.btnSavePlan)

        val prefs = getSharedPreferences(Constants.PREFS_MAIN, MODE_PRIVATE)
        planText.setText(prefs.getString(Constants.KEY_PLAN_TEXT, "") ?: "")

        addButton.setOnClickListener {
            showScheduleEditor(null)
        }

        addTaskButton.setOnClickListener {
            if (!TaskManager.areAllTasksCompleted(this)) {
                Toast.makeText(this, "Finish all tasks before editing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showTaskEditor(null)
        }

        savePlanButton.setOnClickListener {
            prefs.edit().putString(Constants.KEY_PLAN_TEXT, planText.text?.toString().orEmpty()).apply()
            Toast.makeText(this, "Plan saved", Toast.LENGTH_SHORT).show()
        }

        setupTabs()

        applyPersonalization()
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
        applyPersonalization()
        renderAnalytics()
    }

    private fun applyPersonalization() {
        val theme = UiPrefs.getTheme(this)
        val font = UiPrefs.getFont(this)
        val density = UiPrefs.getDensity(this)
        val wallpaper = UiPrefs.getWallpaper(this)
        val accent = UiPrefs.getAccent(this).color
        val background = UiPrefs.getBackground(this).color
        val textScale = UiPrefs.getTextScale(this)
        val effectiveTheme = if (background == android.graphics.Color.TRANSPARENT) {
            theme.copy(accent = accent)
        } else {
            theme.copy(accent = accent, background = background)
        }

        val root = findViewById<View>(R.id.scheduleRoot)
        val content = findViewById<View>(R.id.scheduleContent)
        UiStyler.applyWallpaperOrColor(root, effectiveTheme, wallpaper)
        UiStyler.applyTypefaceRecursive(root, font.typeface)

        val padding = UiStyler.dpToPx(this, density.contentPaddingDp)
        content.setPadding(padding, padding, padding, padding)

        window.statusBarColor = effectiveTheme.background
        window.navigationBarColor = effectiveTheme.background

        findViewById<TextView>(R.id.tvScheduleTitle).setTextColor(effectiveTheme.textPrimary)
        findViewById<TextView>(R.id.tvPlanTitle).setTextColor(effectiveTheme.textPrimary)
        findViewById<TextView>(R.id.tvAnalyticsTitle).setTextColor(effectiveTheme.textPrimary)
        findViewById<TextView>(R.id.tvAnalyticsSubtitle).setTextColor(effectiveTheme.textSecondary)
        findViewById<TextView>(R.id.tvTopAppsTitle).setTextColor(effectiveTheme.textPrimary)

        tabLayout.setTabTextColors(effectiveTheme.textSecondary, effectiveTheme.textPrimary)
        tabLayout.setSelectedTabIndicatorColor(effectiveTheme.accent)

        val addButton = findViewById<Button>(R.id.btnAddWindow)
        addButton.backgroundTintList = android.content.res.ColorStateList.valueOf(effectiveTheme.accent)
        addButton.setTextColor(effectiveTheme.textPrimary)
        setHeightDp(addButton, density.buttonHeightDp)

        addTaskButton.backgroundTintList = android.content.res.ColorStateList.valueOf(effectiveTheme.accent)
        addTaskButton.setTextColor(effectiveTheme.textPrimary)
        setHeightDp(addTaskButton, density.buttonHeightDp)

        val saveButton = findViewById<Button>(R.id.btnSavePlan)
        saveButton.backgroundTintList = android.content.res.ColorStateList.valueOf(effectiveTheme.card)
        saveButton.setTextColor(effectiveTheme.textPrimary)
        setHeightDp(saveButton, density.buttonHeightDp)

        planText.setBackgroundColor(effectiveTheme.input)
        planText.setTextColor(effectiveTheme.textPrimary)
        planText.setHintTextColor(effectiveTheme.textSecondary)

        UiStyler.applyCardBackground(findViewById(R.id.analyticsSummaryCard), effectiveTheme.card)

        renderSchedules(effectiveTheme, font)
        renderTasks(effectiveTheme, font)

        content.scaleX = textScale
        content.scaleY = textScale
    }

    private fun setupTabs() {
        if (tabLayout.tabCount == 0) {
            tabLayout.addTab(tabLayout.newTab().setText("Tasks"))
            tabLayout.addTab(tabLayout.newTab().setText("Windows"))
            tabLayout.addTab(tabLayout.newTab().setText("Plan"))
            tabLayout.addTab(tabLayout.newTab().setText("Analytics"))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                selectTab(tab.position)
            }
        })

        selectTab(0)
    }

    private fun selectTab(index: Int) {
        tabTasks.visibility = if (index == 0) View.VISIBLE else View.GONE
        tabWindows.visibility = if (index == 1) View.VISIBLE else View.GONE
        tabPlan.visibility = if (index == 2) View.VISIBLE else View.GONE
        tabAnalytics.visibility = if (index == 3) View.VISIBLE else View.GONE

        if (index == 0) {
            renderTasks(UiPrefs.getTheme(this), UiPrefs.getFont(this))
        } else if (index == 3) {
            renderAnalytics()
        }
    }

    private fun renderAnalytics() {
        val theme = UiPrefs.getTheme(this)
        val font = UiPrefs.getFont(this)
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val stats24 = queryUsageStats(now - dayMs, now)
        val stats7d = queryUsageStats(now - 7L * dayMs, now)
        val total24 = stats24.sumOf { it.totalTimeInForeground }
        val total7d = stats7d.sumOf { it.totalTimeInForeground }
        val launches24 = countAppLaunches(now - dayMs, now)
        val uniqueApps24 = stats24.size

        val activeWindow = ScheduleManager.getActiveWindow(this)
        val schedules = ScheduleManager.getSchedules(this)
        val todayCount = schedules.count { isWindowToday(it) }
        val scheduledMinutes = schedules
            .filter { isWindowToday(it) }
            .sumOf { windowDurationMinutes(it) }
        val (tasksCompleted, tasksTotal) = TaskManager.getCompletionRatio(this)

        analyticsSummary.text = buildString {
            if (stats24.isEmpty()) {
                append("No usage data. Ensure Usage Access is granted.\n")
            }
            append("Last 24h usage: ${formatDuration(total24)}\n")
            append("Last 7d usage: ${formatDuration(total7d)}\n")
            append("App launches (24h): $launches24")
        }
        analyticsSummary.typeface = font.typeface
        analyticsSummary.setTextColor(theme.textPrimary)

        analyticsStats.removeAllViews()
        addAnalyticsRow(analyticsStats, "Active window", activeWindow?.message ?: "None", theme, font)
        addAnalyticsRow(analyticsStats, "Windows today", todayCount.toString(), theme, font)
        addAnalyticsRow(analyticsStats, "Scheduled minutes", formatMinutes(scheduledMinutes), theme, font)
        addAnalyticsRow(analyticsStats, "Tasks completed", "$tasksCompleted / $tasksTotal", theme, font)
        addAnalyticsRow(analyticsStats, "Unique apps (24h)", uniqueApps24.toString(), theme, font)

        analyticsTopApps.removeAllViews()
        val topApps = stats24
            .sortedByDescending { it.totalTimeInForeground }
            .take(5)
        if (topApps.isEmpty()) {
            addAnalyticsRow(analyticsTopApps, "No data", "", theme, font)
        } else {
            topApps.forEach { stat ->
                val label = getAppLabel(stat.packageName)
                addAnalyticsRow(
                    analyticsTopApps,
                    label,
                    formatDuration(stat.totalTimeInForeground),
                    theme,
                    font
                )
            }
        }
    }

    private fun queryUsageStats(startMs: Long, endMs: Long): List<UsageStats> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)
        return stats.orEmpty().filter { it.totalTimeInForeground > 0L }
    }

    private fun countAppLaunches(startMs: Long, endMs: Long): Int {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(startMs, endMs)
        val event = UsageEvents.Event()
        var count = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                count += 1
            }
        }
        return count
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun isWindowToday(schedule: ScheduleWindow): Boolean {
        val now = Calendar.getInstance()
        return when (schedule.repeat) {
            RepeatType.DAILY -> true
            RepeatType.WEEKLY -> {
                if (schedule.daysOfWeek.isEmpty()) true
                else schedule.daysOfWeek.contains(now.get(Calendar.DAY_OF_WEEK))
            }
            RepeatType.MONTHLY -> {
                val day = schedule.dayOfMonth
                day in 1..31 && now.get(Calendar.DAY_OF_MONTH) == day
            }
        }
    }

    private fun windowDurationMinutes(schedule: ScheduleWindow): Int {
        val start = schedule.startMinutes
        val end = schedule.endMinutes
        if (start == end) return 0
        return if (end > start) {
            end - start
        } else {
            1_440 - start + end
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = (ms / 60_000).coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    private fun addAnalyticsRow(
        container: LinearLayout,
        label: String,
        value: String,
        theme: UiPrefs.UiTheme,
        font: UiPrefs.UiFont
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6, 0, 6)
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(theme.textSecondary)
            typeface = font.typeface
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueView = TextView(this).apply {
            text = value
            setTextColor(theme.textPrimary)
            typeface = font.typeface
        }

        row.addView(labelView)
        row.addView(valueView)
        container.addView(row)
    }

    private fun renderTasks(theme: UiPrefs.UiTheme, font: UiPrefs.UiFont) {
        taskContainer.removeAllViews()
        val tasks = TaskManager.getTasks(this)

        if (tasks.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No tasks yet"
                textSize = 13f
                typeface = font.typeface
                setTextColor(theme.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 12)
            }
            taskContainer.addView(empty)
            return
        }

        val canEditTasks = TaskManager.areAllTasksCompleted(this)

        tasks.sortedByDescending { it.priority }.forEach { task ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(theme.card)
                setPadding(16, 16, 16, 16)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 12
                layoutParams = lp
            }

            val title = TextView(this).apply {
                text = task.title
                textSize = 14f
                typeface = font.typeface
                setTextColor(theme.textPrimary)
            }

            val subtitle = TextView(this).apply {
                text = buildString {
                    append(formatTaskRepeat(task))
                    if (task.timeBlocks.isNotEmpty()) {
                        append(" • ")
                        append(task.timeBlocks.joinToString { block ->
                            "${formatTime(block.startMinutes)}-${formatTime(block.endMinutes)}"
                        })
                    } else {
                        append(" • Anytime")
                    }
                }
                textSize = 12f
                typeface = font.typeface
                setTextColor(theme.textSecondary)
            }

            val progressText = TextView(this).apply {
                val progress = TaskManager.getProgressForTask(this@ScheduleActivity, task.id)
                val done = TaskManager.isTaskCompleted(this@ScheduleActivity, task)
                text = if (done) {
                    "Completed"
                } else if (task.validationMode == TaskValidationMode.APP_TIMER) {
                    val minutesDone = progress?.minutesDone ?: 0
                    "Progress: ${minutesDone}m / ${task.durationMinutes}m"
                } else {
                    "Manual task"
                }
                textSize = 12f
                typeface = font.typeface
                setTextColor(if (done) theme.accent else theme.textSecondary)
                setPadding(0, 6, 0, 6)
            }

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val editButton = Button(this).apply {
                text = "Edit"
                textSize = 12f
                typeface = font.typeface
                setTextColor(theme.textPrimary)
                backgroundTintList = android.content.res.ColorStateList.valueOf(theme.card)
                isEnabled = canEditTasks
                setOnClickListener {
                    if (!canEditTasks) {
                        Toast.makeText(this@ScheduleActivity, "Finish all tasks before editing", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    showTaskEditor(task)
                }
            }

            val deleteButton = Button(this).apply {
                text = "Delete"
                textSize = 12f
                typeface = font.typeface
                setTextColor(theme.textPrimary)
                backgroundTintList = android.content.res.ColorStateList.valueOf(theme.card)
                isEnabled = canEditTasks
                setOnClickListener {
                    if (!canEditTasks) {
                        Toast.makeText(this@ScheduleActivity, "Finish all tasks before editing", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    TaskManager.removeTask(this@ScheduleActivity, task.id)
                    renderTasks(theme, font)
                }
            }

            actions.addView(editButton)
            actions.addView(deleteButton)

            if (task.validationMode == TaskValidationMode.MANUAL) {
                val completeButton = Button(this).apply {
                    text = "Complete"
                    textSize = 12f
                    typeface = font.typeface
                    setTextColor(theme.textPrimary)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(theme.accent)
                    isEnabled = !TaskManager.isTaskCompleted(this@ScheduleActivity, task)
                    setOnClickListener {
                        TaskManager.markManualDone(this@ScheduleActivity, task.id)
                        renderTasks(theme, font)
                    }
                }
                actions.addView(completeButton)
            }

            card.addView(title)
            card.addView(subtitle)
            card.addView(progressText)
            card.addView(actions)

            taskContainer.addView(card)
        }
    }

    private fun showTaskEditor(existing: TaskItem?) {
        if (!TaskManager.areAllTasksCompleted(this)) {
            Toast.makeText(this, "Finish all tasks before editing", Toast.LENGTH_SHORT).show()
            return
        }
        val theme = UiPrefs.getTheme(this)
        val font = UiPrefs.getFont(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 8)
        }

        val titleInput = EditText(this).apply {
            hint = "Task title"
            setText(existing?.title.orEmpty())
        }

        val durationInput = EditText(this).apply {
            hint = "Duration minutes"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existing?.durationMinutes?.toString().orEmpty())
        }

        val notesInput = EditText(this).apply {
            hint = "Notes"
            setText(existing?.notes.orEmpty())
        }

        val tagsInput = EditText(this).apply {
            hint = "Tags (comma separated)"
            setText(existing?.tags?.joinToString(", ").orEmpty())
        }

        val locationInput = EditText(this).apply {
            hint = "Location"
            setText(existing?.location.orEmpty())
        }

        val customFieldsInput = EditText(this).apply {
            hint = "Custom fields (key=value per line)"
            setText(existing?.customFields?.entries?.joinToString("\n") { "${it.key}=${it.value}" }.orEmpty())
        }

        val checklistInput = EditText(this).apply {
            hint = "Checklist (one item per line)"
            setText(existing?.checklist?.joinToString("\n") { it.text }.orEmpty())
        }

        val prioritySpinner = Spinner(this)
        val priorities = arrayOf("Low", "Medium", "High", "Critical")
        prioritySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, priorities)
        prioritySpinner.setSelection((existing?.priority ?: 1).coerceIn(1, 4) - 1)

        val validationSpinner = Spinner(this)
        val validationOptions = arrayOf("Manual", "App + Timer")
        validationSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, validationOptions)
        validationSpinner.setSelection(if (existing?.validationMode == TaskValidationMode.APP_TIMER) 1 else 0)

        val repeatSpinner = Spinner(this)
        val repeatOptions = arrayOf("Daily", "Weekly", "Monthly")
        repeatSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, repeatOptions)
        repeatSpinner.setSelection(
            when (existing?.repeat) {
                TaskRepeat.WEEKLY -> 1
                TaskRepeat.MONTHLY -> 2
                else -> 0
            }
        )

        val daysButton = Button(this).apply { text = "Select days" }
        val selectedDays = existing?.daysOfWeek?.toMutableSet() ?: mutableSetOf()
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val dayValues = intArrayOf(
            Calendar.SUNDAY,
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY
        )

        daysButton.setOnClickListener {
            val checks = dayValues.map { selectedDays.contains(it) }.toBooleanArray()
            AlertDialog.Builder(this)
                .setTitle("Repeat days")
                .setMultiChoiceItems(dayNames, checks) { _, which, isChecked ->
                    if (isChecked) selectedDays.add(dayValues[which]) else selectedDays.remove(dayValues[which])
                }
                .setPositiveButton("OK", null)
                .show()
        }

        val dayOfMonthInput = EditText(this).apply {
            hint = "Day of month (1-31)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (existing?.dayOfMonth ?: 0 > 0) existing?.dayOfMonth.toString() else "")
        }

        val blockContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val blocks = existing?.timeBlocks?.toMutableList() ?: mutableListOf()

        val useBlocksCheck = CheckBox(this).apply {
            text = "Use time blocks"
            isChecked = existing?.timeBlocks?.isNotEmpty() == true
        }

        fun addBlockRow(block: TaskTimeBlock) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
            }

            val startButton = Button(this).apply { text = "Start: ${formatTime(block.startMinutes)}" }
            val endButton = Button(this).apply { text = "End: ${formatTime(block.endMinutes)}" }

            startButton.setOnClickListener {
                pickTime(block.startMinutes) { minutes ->
                    val index = blocks.indexOf(block)
                    if (index >= 0) {
                        blocks[index] = TaskTimeBlock(minutes, blocks[index].endMinutes)
                        startButton.text = "Start: ${formatTime(minutes)}"
                    }
                }
            }

            endButton.setOnClickListener {
                pickTime(block.endMinutes) { minutes ->
                    val index = blocks.indexOf(block)
                    if (index >= 0) {
                        blocks[index] = TaskTimeBlock(blocks[index].startMinutes, minutes)
                        endButton.text = "End: ${formatTime(minutes)}"
                    }
                }
            }

            row.addView(startButton)
            row.addView(endButton)
            blockContainer.addView(row)
        }

        fun ensureDefaultBlock() {
            if (blocks.isEmpty()) {
                val block = TaskTimeBlock(8 * 60, 10 * 60)
                blocks.add(block)
                addBlockRow(block)
            }
        }

        blocks.forEach { addBlockRow(it) }

        val addBlockButton = Button(this).apply { text = "Add time block" }
        addBlockButton.setOnClickListener {
            val block = TaskTimeBlock(8 * 60, 10 * 60)
            blocks.add(block)
            addBlockRow(block)
        }

        fun updateBlockVisibility() {
            val show = useBlocksCheck.isChecked
            addBlockButton.visibility = if (show) View.VISIBLE else View.GONE
            blockContainer.visibility = if (show) View.VISIBLE else View.GONE
            if (show) ensureDefaultBlock()
        }

        useBlocksCheck.setOnCheckedChangeListener { _, _ ->
            updateBlockVisibility()
        }

        val allowedAppsButton = Button(this).apply {
            text = "Allowed apps (${existing?.allowedApps?.size ?: 0})"
        }

        val selectedApps = existing?.allowedApps?.toMutableSet() ?: mutableSetOf()
        val launchable = packageManager.getInstalledApplications(0)
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .mapNotNull { info ->
                try {
                    val label = packageManager.getApplicationLabel(info).toString()
                    Pair(info.packageName, label)
                } catch (_: Exception) { null }
            }
            .sortedBy { it.second.lowercase(Locale.getDefault()) }

        allowedAppsButton.setOnClickListener {
            val labels = launchable.map { it.second }.toTypedArray()
            val checked = launchable.map { selectedApps.contains(it.first) }.toBooleanArray()
            AlertDialog.Builder(this)
                .setTitle("Allowed apps")
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    val pkg = launchable[which].first
                    if (isChecked) selectedApps.add(pkg) else selectedApps.remove(pkg)
                }
                .setPositiveButton("OK") { _, _ ->
                    allowedAppsButton.text = "Allowed apps (${selectedApps.size})"
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        fun updateRepeatVisibility() {
            val weekly = repeatSpinner.selectedItemPosition == 1
            val monthly = repeatSpinner.selectedItemPosition == 2
            daysButton.visibility = if (weekly) View.VISIBLE else View.GONE
            dayOfMonthInput.visibility = if (monthly) View.VISIBLE else View.GONE
        }

        repeatSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateRepeatVisibility()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        updateRepeatVisibility()

        wrap.addView(titleInput)
        wrap.addView(durationInput)
        wrap.addView(repeatSpinner)
        wrap.addView(daysButton)
        wrap.addView(dayOfMonthInput)
        wrap.addView(useBlocksCheck)
        wrap.addView(addBlockButton)
        wrap.addView(blockContainer)
        wrap.addView(validationSpinner)
        wrap.addView(allowedAppsButton)
        wrap.addView(prioritySpinner)
        wrap.addView(tagsInput)
        wrap.addView(locationInput)
        wrap.addView(notesInput)
        wrap.addView(checklistInput)
        wrap.addView(customFieldsInput)

        updateBlockVisibility()

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Task" else "Edit Task")
            .setView(wrap)
            .setPositiveButton("Save") { _, _ ->
                val repeat = when (repeatSpinner.selectedItemPosition) {
                    1 -> TaskRepeat.WEEKLY
                    2 -> TaskRepeat.MONTHLY
                    else -> TaskRepeat.DAILY
                }
                val dayOfMonth = when (repeat) {
                    TaskRepeat.MONTHLY -> {
                        val fallback = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                        dayOfMonthInput.text?.toString()?.toIntOrNull() ?: fallback
                    }
                    else -> 0
                }

                val duration = durationInput.text?.toString()?.toIntOrNull() ?: 0
                val validationMode = if (validationSpinner.selectedItemPosition == 1) {
                    TaskValidationMode.APP_TIMER
                } else {
                    TaskValidationMode.MANUAL
                }

                val resolvedValidation = if (
                    validationMode == TaskValidationMode.APP_TIMER && selectedApps.isEmpty()
                ) {
                    Toast.makeText(
                        this,
                        "Select at least one app for timer validation. Falling back to manual.",
                        Toast.LENGTH_LONG
                    ).show()
                    TaskValidationMode.MANUAL
                } else {
                    validationMode
                }

                val tags = tagsInput.text?.toString()?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()

                val checklist = checklistInput.text?.toString()
                    ?.lines()
                    ?.mapNotNull { line ->
                        val text = line.trim()
                        if (text.isEmpty()) null else TaskChecklistItem(text, done = false)
                    } ?: emptyList()

                val customFields = customFieldsInput.text?.toString()
                    ?.lines()
                    ?.mapNotNull { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                    }?.toMap() ?: emptyMap()

                val priority = (prioritySpinner.selectedItemPosition + 1).coerceIn(1, 4)

                val resolvedBlocks = if (useBlocksCheck.isChecked) blocks.toList() else emptyList()

                val task = if (existing == null) {
                    TaskManager.newTask(
                        title = titleInput.text?.toString()?.trim().orEmpty(),
                        durationMinutes = duration,
                        allowedApps = selectedApps.toList(),
                        repeat = repeat,
                        daysOfWeek = selectedDays.toList(),
                        dayOfMonth = dayOfMonth,
                        timeBlocks = resolvedBlocks,
                        priority = priority,
                        tags = tags,
                        notes = notesInput.text?.toString()?.trim().orEmpty(),
                        location = locationInput.text?.toString()?.trim().orEmpty(),
                        checklist = checklist,
                        customFields = customFields,
                        validationMode = resolvedValidation
                    )
                } else {
                    existing.copy(
                        title = titleInput.text?.toString()?.trim().orEmpty(),
                        durationMinutes = duration,
                        allowedApps = selectedApps.toList(),
                        repeat = repeat,
                        daysOfWeek = selectedDays.toList(),
                        dayOfMonth = dayOfMonth,
                        timeBlocks = resolvedBlocks,
                        priority = priority,
                        tags = tags,
                        notes = notesInput.text?.toString()?.trim().orEmpty(),
                        location = locationInput.text?.toString()?.trim().orEmpty(),
                        checklist = checklist,
                        customFields = customFields,
                        validationMode = resolvedValidation
                    )
                }

                if (existing == null) {
                    TaskManager.addTask(this, task)
                } else {
                    TaskManager.updateTask(this, task)
                }
                renderTasks(theme, font)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatTaskRepeat(task: TaskItem): String {
        return when (task.repeat) {
            TaskRepeat.DAILY -> "Daily"
            TaskRepeat.WEEKLY -> {
                if (task.daysOfWeek.isEmpty()) "Weekly"
                else "Weekly (${task.daysOfWeek.joinToString { dayName(it) }})"
            }
            TaskRepeat.MONTHLY -> "Monthly (day ${task.dayOfMonth})"
        }
    }

    private fun renderSchedules() {
        val theme = UiPrefs.getTheme(this)
        val font = UiPrefs.getFont(this)
        renderSchedules(theme, font)
    }

    private fun renderSchedules(theme: UiPrefs.UiTheme, font: UiPrefs.UiFont) {
        container.removeAllViews()
        val schedules = (ScheduleManager.getSchedules(this) + buildTaskWindows())

        if (schedules.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No windows yet"
                textSize = 13f
                typeface = font.typeface
                setTextColor(theme.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 12)
            }
            container.addView(empty)
            return
        }

        schedules.forEach { schedule ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(theme.card)
                setPadding(16, 16, 16, 16)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 12
                layoutParams = lp
            }

            val title = TextView(this).apply {
                text = "${formatTime(schedule.startMinutes)} - ${formatTime(schedule.endMinutes)}"
                textSize = 14f
                typeface = font.typeface
                setTextColor(theme.textPrimary)
            }

            val subtitle = TextView(this).apply {
                text = formatRepeatLabel(schedule)
                textSize = 12f
                typeface = font.typeface
                setTextColor(theme.textSecondary)
            }

            val message = TextView(this).apply {
                text = schedule.message
                textSize = 12f
                typeface = font.typeface
                setTextColor(theme.textPrimary)
                setPadding(0, 8, 0, 8)
            }

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            if (schedule.source == ScheduleSource.MANUAL) {
                val editButton = Button(this).apply {
                    text = "Edit"
                    textSize = 12f
                    typeface = font.typeface
                    setTextColor(theme.textPrimary)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(theme.card)
                    setOnClickListener { showScheduleEditor(schedule) }
                }

                val deleteButton = Button(this).apply {
                    text = "Delete"
                    textSize = 12f
                    typeface = font.typeface
                    setTextColor(theme.textPrimary)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(theme.card)
                    setOnClickListener {
                        ScheduleManager.removeSchedule(this@ScheduleActivity, schedule.id)
                        renderSchedules()
                    }
                }
                actions.addView(editButton)
                actions.addView(deleteButton)
            } else {
                val tag = TextView(this).apply {
                    text = "Task window"
                    textSize = 12f
                    typeface = font.typeface
                    setTextColor(theme.textSecondary)
                }
                actions.addView(tag)
            }

            card.addView(title)
            card.addView(subtitle)
            card.addView(message)
            card.addView(actions)

            container.addView(card)
        }
    }

    private fun buildTaskWindows(): List<ScheduleWindow> {
        val tasks = TaskManager.getTasks(this)
        val windows = mutableListOf<ScheduleWindow>()
        tasks.forEach { task ->
            val repeat = when (task.repeat) {
                TaskRepeat.DAILY -> RepeatType.DAILY
                TaskRepeat.WEEKLY -> RepeatType.WEEKLY
                TaskRepeat.MONTHLY -> RepeatType.MONTHLY
            }
            task.timeBlocks.forEachIndexed { index, block ->
                windows.add(
                    ScheduleWindow(
                        id = "task_${task.id}_$index",
                        startMinutes = block.startMinutes,
                        endMinutes = block.endMinutes,
                        repeat = repeat,
                        daysOfWeek = task.daysOfWeek,
                        dayOfMonth = task.dayOfMonth,
                        message = "Task: ${task.title}",
                        source = ScheduleSource.TASK,
                        taskId = task.id
                    )
                )
            }
        }
        return windows
    }

    private fun setHeightDp(view: View, heightDp: Int) {
        val params = view.layoutParams
        params.height = UiStyler.dpToPx(this, heightDp)
        view.layoutParams = params
    }

    private fun showScheduleEditor(existing: ScheduleWindow?) {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 8)
        }

        val startButton = Button(this)
        val endButton = Button(this)

        var startMinutes = existing?.startMinutes ?: (8 * 60)
        var endMinutes = existing?.endMinutes ?: (10 * 60)

        startButton.text = "Start: ${formatTime(startMinutes)}"
        endButton.text = "End: ${formatTime(endMinutes)}"

        startButton.setOnClickListener {
            pickTime(startMinutes) { minutes ->
                startMinutes = minutes
                startButton.text = "Start: ${formatTime(minutes)}"
            }
        }

        endButton.setOnClickListener {
            pickTime(endMinutes) { minutes ->
                endMinutes = minutes
                endButton.text = "End: ${formatTime(minutes)}"
            }
        }

        val repeatSpinner = Spinner(this)
        val repeatOptions = arrayOf("Daily", "Weekly", "Monthly")
        repeatSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, repeatOptions)
        repeatSpinner.setSelection(
            when (existing?.repeat) {
                RepeatType.WEEKLY -> 1
                RepeatType.MONTHLY -> 2
                else -> 0
            }
        )

        val daysButton = Button(this).apply {
            text = "Select days"
        }
        val selectedDays = existing?.daysOfWeek?.toMutableSet() ?: mutableSetOf()
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val dayValues = intArrayOf(
            Calendar.SUNDAY,
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY
        )

        daysButton.setOnClickListener {
            val checks = dayValues.map { selectedDays.contains(it) }.toBooleanArray()
            AlertDialog.Builder(this)
                .setTitle("Repeat days")
                .setMultiChoiceItems(dayNames, checks) { _, which, isChecked ->
                    if (isChecked) selectedDays.add(dayValues[which]) else selectedDays.remove(dayValues[which])
                }
                .setPositiveButton("OK", null)
                .show()
        }

        val dayOfMonthInput = EditText(this).apply {
            hint = "Day of month (1-31)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (existing?.dayOfMonth ?: 0 > 0) existing?.dayOfMonth.toString() else "")
        }

        val messageInput = EditText(this).apply {
            hint = "Window message"
            setText(existing?.message.orEmpty())
        }

        wrap.addView(startButton)
        wrap.addView(endButton)
        wrap.addView(repeatSpinner)
        wrap.addView(daysButton)
        wrap.addView(dayOfMonthInput)
        wrap.addView(messageInput)

        fun updateRepeatVisibility() {
            val weekly = repeatSpinner.selectedItemPosition == 1
            val monthly = repeatSpinner.selectedItemPosition == 2
            daysButton.visibility = if (weekly) View.VISIBLE else View.GONE
            dayOfMonthInput.visibility = if (monthly) View.VISIBLE else View.GONE
        }

        repeatSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateRepeatVisibility()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        updateRepeatVisibility()

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Window" else "Edit Window")
            .setView(wrap)
            .setPositiveButton("Save") { _, _ ->
                val repeat = when (repeatSpinner.selectedItemPosition) {
                    1 -> RepeatType.WEEKLY
                    2 -> RepeatType.MONTHLY
                    else -> RepeatType.DAILY
                }
                val dayOfMonth = when (repeat) {
                    RepeatType.MONTHLY -> {
                        val fallback = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                        dayOfMonthInput.text?.toString()?.toIntOrNull() ?: fallback
                    }
                    else -> 0
                }
                val message = messageInput.text?.toString()?.trim().orEmpty()

                val schedule = if (existing == null) {
                    ScheduleManager.newSchedule(
                        startMinutes = startMinutes,
                        endMinutes = endMinutes,
                        repeat = repeat,
                        daysOfWeek = selectedDays.toList(),
                        dayOfMonth = dayOfMonth,
                        message = message
                    )
                } else {
                    existing.copy(
                        startMinutes = startMinutes,
                        endMinutes = endMinutes,
                        repeat = repeat,
                        daysOfWeek = selectedDays.toList(),
                        dayOfMonth = dayOfMonth,
                        message = message
                    )
                }

                if (existing == null) {
                    ScheduleManager.addSchedule(this, schedule)
                } else {
                    ScheduleManager.updateSchedule(this, schedule)
                }
                renderSchedules()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickTime(initialMinutes: Int, onSelected: (Int) -> Unit) {
        val hour = initialMinutes / 60
        val minute = initialMinutes % 60
        TimePickerDialog(this, { _, h, m ->
            onSelected(h * 60 + m)
        }, hour, minute, false).show()
    }

    private fun formatTime(minutes: Int): String {
        val hour = minutes / 60
        val minute = minutes % 60
        val ampm = if (hour >= 12) "PM" else "AM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, ampm)
    }

    private fun formatRepeatLabel(schedule: ScheduleWindow): String {
        return when (schedule.repeat) {
            RepeatType.DAILY -> "Daily"
            RepeatType.WEEKLY -> {
                if (schedule.daysOfWeek.isEmpty()) "Weekly"
                else "Weekly (${schedule.daysOfWeek.joinToString { dayName(it) }})"
            }
            RepeatType.MONTHLY -> "Monthly (day ${schedule.dayOfMonth})"
        }
    }

    private fun dayName(day: Int): String {
        return when (day) {
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> "Sun"
        }
    }
}
