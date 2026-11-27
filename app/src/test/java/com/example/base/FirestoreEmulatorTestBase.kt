package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FirestoreProvider
import com.example.data.FirestoreProvider.awaitResult
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var databaseId: String

  protected fun isPortOpen(port: Int): Boolean {
    return try {
      Socket(EMULATOR_HOST, port).use { true }
    } catch (_: Exception) {
      false
    }
  }

  protected fun areEmulatorsRunning(): Boolean {
    return isPortOpen(FIRESTORE_PORT) && isPortOpen(AUTH_PORT) && isPortOpen(SPARK_PORT)
  }

  @Before
  open fun setUpFirebase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    databaseId = FirestoreProvider.getDatabaseId(context)
    if (!areEmulatorsRunning()) return

    val app = if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
          .setApplicationId("1:111465055412:android:fixmechanicuser")
          .setProjectId(PROJECT_ID)
          .setApiKey("fake-api-key-for-emulator")
          .build()
      )
    } else {
      FirebaseApp.getInstance()
    }

    auth = FirebaseAuth.getInstance(app)
    runCatching { auth.useEmulator(EMULATOR_HOST, AUTH_PORT) }

    firestore = FirestoreProvider.createFirestore(context, isTestOrEmulator = true)
    runCatching { firestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT) }

    clearEmulators()
  }

  @After
  open fun tearDownFirebase() {
    if (!areEmulatorsRunning()) return
    runCatching { auth.signOut() }
    clearEmulators()
  }

  protected fun clearEmulators() {
    try {
      val fsUrl = URL("http://$EMULATOR_HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID/databases/(default)/documents")
      (fsUrl.openConnection() as HttpURLConnection).run {
        requestMethod = "DELETE"
        responseCode
        disconnect()
      }
      val authUrl = URL("http://$EMULATOR_HOST:$AUTH_PORT/emulator/v1/projects/$PROJECT_ID/accounts")
      (authUrl.openConnection() as HttpURLConnection).run {
        requestMethod = "DELETE"
        responseCode
        disconnect()
      }
    } catch (_: Exception) {}
  }

  protected suspend fun signInTestUser(email: String): String = withContext(Dispatchers.IO) {
    withTimeout(AUTH_TIMEOUT_MS) {
      val result = try {
        auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).awaitResult()
      } catch (_: FirebaseAuthInvalidUserException) {
        auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).awaitResult()
      } catch (_: Exception) {
        auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).awaitResult()
      }
      checkNotNull(result.user?.uid) { "User auth failed" }
    }
  }

  protected fun seedFirestoreDocViaRest(collection: String, docId: String, jsonFieldsBody: String) {
    val url = URL(
      "http://$EMULATOR_HOST:$FIRESTORE_PORT/v1/projects/$PROJECT_ID/databases/(default)/documents/$collection?documentId=$docId"
    )
    (url.openConnection() as HttpURLConnection).run {
      requestMethod = "POST"
      setRequestProperty("Content-Type", "application/json")
      setRequestProperty("Authorization", "Bearer owner")
      doOutput = true
      OutputStreamWriter(outputStream).use { it.write("""{"fields":$jsonFieldsBody}""") }
      responseCode
      disconnect()
    }
  }

  protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
    var current: Throwable? = this
    while (current != null) {
      if (current is FirebaseFirestoreException) return current
      current = current.cause
    }
    return null
  }

  companion object {
    const val EMULATOR_HOST = "127.0.0.1"
    const val FIRESTORE_PORT = 8085
    const val AUTH_PORT = 9099
    const val SPARK_PORT = 5055
    const val PROJECT_ID = "fixmechanic-firebase"
    const val DEFAULT_PASSWORD = "password123"
    const val AUTH_TIMEOUT_MS = 10000L
  }
}
