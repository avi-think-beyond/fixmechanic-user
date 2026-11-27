package com.example.data

import android.content.Context
import com.example.R
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirestoreProvider {

  private val directTaskExecutor = Executor { runnable -> runnable.run() }

  fun getDatabaseId(context: Context): String {
    return runCatching {
      context.getString(R.string.firestore_database_id).trim()
    }.getOrDefault("(default)").ifBlank { "(default)" }
  }

  fun createFirestore(context: Context, isTestOrEmulator: Boolean = false): FirebaseFirestore {
    val dbId = getDatabaseId(context)
    val db = if (dbId == "(default)") {
      FirebaseFirestore.getInstance()
    } else {
      FirebaseFirestore.getInstance(dbId)
    }
    if (isTestOrEmulator) {
      db.firestoreSettings = FirebaseFirestoreSettings.Builder()
        .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
        .build()
    }
    return db
  }

  suspend fun <T> Task<T>.awaitResult(): T {
    return suspendCancellableCoroutine { cont ->
      addOnSuccessListener(directTaskExecutor) { result ->
        if (cont.isActive) cont.resume(result)
      }
      addOnFailureListener(directTaskExecutor) { err ->
        if (cont.isActive) cont.resumeWithException(err)
      }
      addOnCanceledListener(directTaskExecutor) {
        if (cont.isActive) cont.cancel()
      }
    }
  }
}
