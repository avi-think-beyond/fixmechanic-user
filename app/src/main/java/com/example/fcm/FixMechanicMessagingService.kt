package com.example.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.FirestoreProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FixMechanicMessagingService : FirebaseMessagingService() {

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: return
    runCatching {
      val db = FirestoreProvider.createFirestore(applicationContext)
      db.collection("deviceTokens").document("${uid}_android").set(
        mapOf(
          "userId" to uid,
          "role" to "MECHANIC",
          "platform" to "android",
          "token" to token,
          "updatedAt" to System.currentTimeMillis()
        )
      )
    }.onFailure {
      Log.w(TAG, "Failed persisting new FCM token", it)
    }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val title = message.notification?.title ?: message.data["title"] ?: "FixMechanic Dispatch Alert"
    val body = message.notification?.body ?: message.data["body"] ?: "You have a new roadside update."

    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "FixMechanic Emergency Dispatches",
        NotificationManager.IMPORTANCE_HIGH
      )
      manager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(body)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .build()

    manager.notify(System.currentTimeMillis().toInt(), notification)
  }

  companion object {
    private const val TAG = "FixMechFCM"
    private const val CHANNEL_ID = "fixmechanic_emergency"
  }
}
