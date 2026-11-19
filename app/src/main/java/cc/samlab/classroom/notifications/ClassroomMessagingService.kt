package cc.samlab.classroom.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cc.samlab.classroom.MainActivity
import cc.samlab.classroom.R
import cc.samlab.classroom.data.BackendSettings
import cc.samlab.classroom.data.ClassroomApi
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AttendanceNotifications {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun prepare(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("attendance", "考勤通知", NotificationManager.IMPORTANCE_HIGH))
    }

    fun register(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> register(context, token) }
            .addOnFailureListener { Log.w("ClassroomPush", "Unable to obtain notification registration: ${it.javaClass.simpleName}") }
    }

    fun register(context: Context, token: String) {
        val app = context.applicationContext
        val address = BackendSettings(app).address
        if (address.isBlank()) return
        scope.launch {
            try {
                val api = ClassroomApi(app, address)
                if (api.currentUser()?.role == "student") api.registerPushToken(token)
            } catch (error: Exception) {
                // Retry when the app is next opened or Firebase rotates the token.
                Log.w("ClassroomPush", "Unable to register notification device: ${error.javaClass.simpleName}")
            }
        }
    }

    fun show(context: Context, title: String, body: String, taskId: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        prepare(context)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "attendance")
            .setSmallIcon(R.drawable.ic_person).setContentTitle(title).setContentText(body)
            .setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(pending).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        context.getSystemService(NotificationManager::class.java).notify("attendance-$taskId", 0, notification)
    }
}

class ClassroomMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { AttendanceNotifications.register(applicationContext, token) }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "attendance") return
        AttendanceNotifications.show(applicationContext, message.notification?.title ?: "考勤通知",
            message.notification?.body ?: "有新的考勤任务，请打开 Classroom 查看。", message.data["taskId"].orEmpty())
    }
}
