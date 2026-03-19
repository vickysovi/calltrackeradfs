package com.calltracker

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.os.Bundle
import android.provider.CallLog
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

data class CallStats(
    var count: Int = 0,
    var totalDurationSeconds: Long = 0L
)

data class DayStats(
    val dateLabel: String,
    val incoming: CallStats = CallStats(),
    val outgoing: CallStats = CallStats(),
    val missed: CallStats = CallStats(),
    val blocked: CallStats = CallStats()
) {
    val totalDuration get() = incoming.totalDurationSeconds + outgoing.totalDurationSeconds
}

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 100

    private lateinit var spinnerFilter: Spinner
    private lateinit var btnRefresh: Button

    private lateinit var tvIncomingCount: TextView
    private lateinit var tvOutgoingCount: TextView
    private lateinit var tvMissedCount: TextView
    private lateinit var tvBlockedCount: TextView
    private lateinit var tvTotalCount: TextView

    private lateinit var tvIncomingDuration: TextView
    private lateinit var tvOutgoingDuration: TextView
    private lateinit var tvMissedDuration: TextView
    private lateinit var tvBlockedDuration: TextView
    private lateinit var tvTotalDuration: TextView

    private lateinit var tvLastUpdated: TextView
    private lateinit var tvStatus: TextView
    private lateinit var llDayWise: LinearLayout
    private lateinit var tvDayWiseHeader: TextView

    private val filterOptions = arrayOf(
        "All Time", "Today", "Last 7 Days", "Last 30 Days"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupSpinner()
        if (hasPermissions()) loadCallStats() else requestPermissions()
        btnRefresh.setOnClickListener {
            if (hasPermissions()) { loadCallStats(); toast("Stats refreshed!") }
            else requestPermissions()
        }
    }

    private fun bindViews() {
        spinnerFilter      = findViewById(R.id.spinnerFilter)
        btnRefresh         = findViewById(R.id.btnRefresh)
        tvIncomingCount    = findViewById(R.id.tvIncomingCount)
        tvOutgoingCount    = findViewById(R.id.tvOutgoingCount)
        tvMissedCount      = findViewById(R.id.tvMissedCount)
        tvBlockedCount     = findViewById(R.id.tvBlockedCount)
        tvTotalCount       = findViewById(R.id.tvTotalCount)
        tvIncomingDuration = findViewById(R.id.tvIncomingDuration)
        tvOutgoingDuration = findViewById(R.id.tvOutgoingDuration)
        tvMissedDuration   = findViewById(R.id.tvMissedDuration)
        tvBlockedDuration  = findViewById(R.id.tvBlockedDuration)
        tvTotalDuration    = findViewById(R.id.tvTotalDuration)
        tvLastUpdated      = findViewById(R.id.tvLastUpdated)
        tvStatus           = findViewById(R.id.tvStatus)
        llDayWise          = findViewById(R.id.llDayWise)
        tvDayWiseHeader    = findViewById(R.id.tvDayWiseHeader)
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = adapter
        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (hasPermissions()) loadCallStats()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun hasPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() = ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE),
        PERMISSION_REQUEST_CODE
    )

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) loadCallStats()
            else tvStatus.text = "⚠ Permissions denied."
        }
    }

    private fun getFilterTimestamp(): Long {
        val cal = Calendar.getInstance()
        return when (spinnerFilter.selectedItemPosition) {
            0 -> 0L
            1 -> { cal.set(Calendar.HOUR_OF_DAY,0); cal.set(Calendar.MINUTE,0)
                   cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0); cal.timeInMillis }
            2 -> { cal.add(Calendar.DAY_OF_YEAR, -7); cal.timeInMillis }
            3 -> { cal.add(Calendar.DAY_OF_YEAR, -30); cal.timeInMillis }
            else -> 0L
        }
    }

    private fun loadCallStats() {
        val incoming = CallStats(); val outgoing = CallStats()
        val missed   = CallStats(); val blocked  = CallStats()

        val dayMap = LinkedHashMap<String, DayStats>()
        val keyFmt   = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val labelFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

        val fromTs = getFilterTimestamp()
        val sel    = if (fromTs > 0) "${CallLog.Calls.DATE} >= ?" else null
        val args   = if (fromTs > 0) arrayOf(fromTs.toString()) else null

        val cursor: Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.TYPE, CallLog.Calls.DURATION, CallLog.Calls.DATE),
            sel, args, "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use { c ->
            val tCol = c.getColumnIndex(CallLog.Calls.TYPE)
            val dCol = c.getColumnIndex(CallLog.Calls.DURATION)
            val dtCol= c.getColumnIndex(CallLog.Calls.DATE)

            while (c.moveToNext()) {
                val type     = c.getInt(tCol)
                val duration = c.getLong(dCol)
                val date     = Date(c.getLong(dtCol))
                val day      = dayMap.getOrPut(keyFmt.format(date)) {
                    DayStats(labelFmt.format(date))
                }

                when (type) {
                    CallLog.Calls.INCOMING_TYPE -> {
                        incoming.count++; incoming.totalDurationSeconds += duration
                        day.incoming.count++; day.incoming.totalDurationSeconds += duration
                    }
                    CallLog.Calls.OUTGOING_TYPE -> {
                        outgoing.count++; outgoing.totalDurationSeconds += duration
                        day.outgoing.count++; day.outgoing.totalDurationSeconds += duration
                    }
                    CallLog.Calls.MISSED_TYPE -> {
                        missed.count++; day.missed.count++
                    }
                    CallLog.Calls.BLOCKED_TYPE, CallLog.Calls.REJECTED_TYPE -> {
                        blocked.count++; day.blocked.count++
                    }
                }
            }
        }

        val totalCount    = incoming.count + outgoing.count + missed.count + blocked.count
        val totalDuration = incoming.totalDurationSeconds + outgoing.totalDurationSeconds

        tvIncomingCount.text    = incoming.count.toString()
        tvOutgoingCount.text    = outgoing.count.toString()
        tvMissedCount.text      = missed.count.toString()
        tvBlockedCount.text     = blocked.count.toString()
        tvTotalCount.text       = totalCount.toString()
        tvIncomingDuration.text = formatDuration(incoming.totalDurationSeconds)
        tvOutgoingDuration.text = formatDuration(outgoing.totalDurationSeconds)
        tvMissedDuration.text   = "—"
        tvBlockedDuration.text  = "—"
        tvTotalDuration.text    = formatDuration(totalDuration)

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        tvLastUpdated.text = "Last updated: ${sdf.format(Date())}"
        tvStatus.text = "✓ Loaded · ${filterOptions[spinnerFilter.selectedItemPosition]}"

        buildDayWiseTable(dayMap)
    }

    private fun buildDayWiseTable(dayMap: LinkedHashMap<String, DayStats>) {
        llDayWise.removeAllViews()
        if (dayMap.isEmpty()) {
            tvDayWiseHeader.text = "📅 Day-Wise Breakdown  (0 days)"
            llDayWise.addView(TextView(this).apply {
                text = "No calls found for selected period."
                textSize = 13f; setTextColor(Color.parseColor("#6B7280"))
                setPadding(0, 12, 0, 12)
            })
            return
        }
        tvDayWiseHeader.text = "📅 Day-Wise Breakdown  (${dayMap.size} days)"
        llDayWise.addView(makeHeaderRow())
        llDayWise.addView(makeDivider("#00FFB2", 1))
        dayMap.values.forEachIndexed { i, day ->
            llDayWise.addView(makeDayRow(day, i % 2 == 1))
            llDayWise.addView(makeDivider("#1E2132", 1))
        }
    }

    private fun makeHeaderRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, dp(8))
        addView(cell("DATE",      isDate = true,  color = "#00FFB2", bold = true))
        addView(cell("📲 IN",     isDate = false, color = "#00E5A0", bold = true))
        addView(cell("📤 OUT",    isDate = false, color = "#3B8EFF", bold = true))
        addView(cell("❌ MISS",   isDate = false, color = "#FF7A3D", bold = true))
        addView(cell("🚫 BLK",   isDate = false, color = "#FF4444", bold = true))
        addView(cell("⏱ TIME",   isDate = false, color = "#00FFB2", bold = true))
    }

    private fun makeDayRow(day: DayStats, alt: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(if (alt) Color.parseColor("#0D1020") else Color.TRANSPARENT)
        setPadding(0, dp(11), 0, dp(11))
        addView(cell(day.dateLabel,                         isDate = true,  color = "#C9D1E3"))
        addView(cell(day.incoming.count.toString(),          isDate = false, color = "#00E5A0"))
        addView(cell(day.outgoing.count.toString(),          isDate = false, color = "#3B8EFF"))
        addView(cell(day.missed.count.toString(),            isDate = false, color = "#FF7A3D"))
        addView(cell(day.blocked.count.toString(),           isDate = false, color = "#FF4444"))
        addView(cell(formatDuration(day.totalDuration),      isDate = false, color = "#C9D1E3"))
    }

    private fun cell(
        text: String, isDate: Boolean, color: String, bold: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = text
        textSize  = if (isDate) 11f else 12f
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = if (isDate) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            if (isDate) 0 else dp(60),
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { if (isDate) weight = 1f }
    }

    private fun makeDivider(hex: String, heightDp: Int = 1) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp))
        setBackgroundColor(Color.parseColor(hex))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0L) return "0s"
        val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
        return buildString {
            if (h > 0) append("${h}h "); if (m > 0) append("${m}m "); append("${s}s")
        }.trim()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
