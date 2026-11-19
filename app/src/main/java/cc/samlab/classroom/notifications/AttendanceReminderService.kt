package cc.samlab.classroom.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cc.samlab.classroom.MainActivity
import cc.samlab.classroom.R
import cc.samlab.classroom.data.BackendSettings
import cc.samlab.classroom.data.ClassroomApi
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Adapted from CRTicket-Android's user-started TicketMonitorService: keep a visible
// foreground notification, persist intent, poll, and allow the user to stop it.
class AttendanceReminderService : Service() {
    enum class State { Stopped, Starting, Running, Failed }

    companion object {
        private const val PREFS = "attendance_reminder"
        private const val CHANNEL = "attendance-monitor"
        private const val ID = 7301
        private const val STOP = "cc.samlab.classroom.STOP_ATTENDANCE_REMINDER"
        private val serviceState = MutableStateFlow(State.Stopped)
        val state = serviceState.asStateFlow()

        fun start(context: Context, address: String, userId: Long) {
            require(address.isNotBlank() && userId > 0)
            if (state.value in setOf(State.Starting, State.Running)) return
            val app = context.applicationContext
            app.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("enabled", true).putString("address", address).putLong("userId", userId).apply()
            serviceState.value = State.Starting
            try { ContextCompat.startForegroundService(app, Intent(app, AttendanceReminderService::class.java)) }
            catch (error: Exception) {
                app.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
                Log.w("AttendanceReminder", "Unable to request reminder: ${error.javaClass.simpleName}")
                serviceState.value = State.Failed
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            app.stopService(Intent(app, AttendanceReminderService::class.java))
            serviceState.value = State.Stopped
        }

        fun restore(context: Context, address: String, userId: Long) {
            val prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE)
            if (prefs.getBoolean("enabled", false) && prefs.getString("address", "") == address &&
                prefs.getLong("userId", 0) == userId) start(context, address, userId)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var polling: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stop(this); return START_NOT_STICKY }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val address = prefs.getString("address", "").orEmpty()
        val userId = prefs.getLong("userId", 0)
        if (!prefs.getBoolean("enabled", false) || address.isBlank() || userId <= 0) {
            serviceState.value = State.Stopped
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val notification = ongoingNotification()
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, notification)
            serviceState.value = State.Running
        } catch (error: Exception) {
            Log.w("AttendanceReminder", "Unable to start reminder: ${error.javaClass.simpleName}")
            prefs.edit().putBoolean("enabled", false).apply()
            serviceState.value = State.Failed
            stopSelf()
            return START_NOT_STICKY
        }
        if (polling?.isActive != true) polling = scope.launch { monitor(address, userId) }
        return START_STICKY
    }

    private fun ongoingNotification(retrying: Boolean = false): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.background_reminders_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, AttendanceReminderService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_attendance_task)
            .setContentTitle(getString(R.string.background_reminders))
            .setContentText(getString(if (retrying) R.string.background_reminders_retrying else R.string.background_reminders_ongoing))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.background_reminders_stop), stop).build()
    }

    private suspend fun monitor(address: String, userId: Long) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val digest = MessageDigest.getInstance("SHA-256").digest("$address|$userId".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val key = "seen_$digest"
        var retrying = false
        while (scope.isActive && prefs.getBoolean("enabled", false)) {
            try {
                if (BackendSettings(this).address != address) { stop(this); return }
                // Re-read the current encrypted session on each pass, so account changes
                // cannot leave the monitor attached to a previously signed-in student.
                val api = ClassroomApi(applicationContext, address)
                val user = api.currentUser()
                if (user == null || user.id != userId || user.role != "student") { stop(this); return }
                val tasks = api.attendanceTasks()
                val ids = (0 until tasks.length()).map { tasks.getJSONObject(it).optLong("id") }.filter { it > 0 }.toSet()
                val seen = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet()
                val initialized = prefs.getBoolean("${key}_ready", false)
                for (id in newAttendanceTasks(initialized, seen, ids)) {
                    AttendanceNotifications.show(this, getString(R.string.attendance_notification_title),
                        getString(R.string.attendance_notification_body), id.toString())
                }
                prefs.edit().putStringSet(key, (seen + ids).toList().takeLast(1000).map(Long::toString).toSet())
                    .putBoolean("${key}_ready", true).apply()
                if (retrying) {
                    retrying = false
                    runCatching { getSystemService(NotificationManager::class.java).notify(ID, ongoingNotification()) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.w("AttendanceReminder", "Attendance check failed: ${error.javaClass.simpleName}")
                if (!retrying) {
                    retrying = true
                    runCatching { getSystemService(NotificationManager::class.java).notify(ID, ongoingNotification(retrying = true)) }
                }
            }
            delay(15_000)
        }
    }

    override fun onDestroy() {
        serviceState.compareAndSet(State.Running, State.Stopped)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

internal fun newAttendanceTasks(initialized: Boolean, seen: Set<Long>, current: Set<Long>): Set<Long> =
    if (initialized) current - seen else emptySet()
