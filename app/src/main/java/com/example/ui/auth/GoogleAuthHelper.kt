package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.FirestoreProvider.awaitResult
import com.example.ui.theme.Navy900
import com.example.ui.theme.SurfaceWhite
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

fun getRuntimeFirebaseProjectId(context: Context): String {
  return runCatching {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    FirebaseApp.getInstance().options.projectId?.takeIf { it.isNotBlank() } ?: "unconfigured"
  }.getOrDefault("unconfigured")
}

private fun resolveWebClientId(context: Context): String? {
  val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
  if (resId == 0) return null
  return runCatching { context.getString(resId).trim() }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun ensureFirebaseInitialized(context: Context): FirebaseAuth? {
  return runCatching {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    FirebaseAuth.getInstance()
  }.getOrNull()
}

fun formatAuthErrorMessage(context: Context, error: Throwable): String {
  val projectId = getRuntimeFirebaseProjectId(context)
  return when (error) {
    is FirebaseAuthException -> when (error.errorCode) {
      "ERROR_OPERATION_NOT_ALLOWED" ->
        "This sign-in method is disabled on Firebase project '$projectId'. Verify google-services.json and reinstall."
      "ERROR_EMAIL_ALREADY_IN_USE" ->
        "An account already exists with this email. Switch to Sign In instead."
      "ERROR_INVALID_EMAIL" ->
        "Please enter a valid email address."
      "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" ->
        "Invalid email or password. Double-check your credentials or create an account."
      "ERROR_USER_NOT_FOUND" ->
        "No account found for this email. Tap 'New partner? Create Email Account'."
      "ERROR_WEAK_PASSWORD" ->
        "Password is too weak. Please use at least 6 characters."
      "ERROR_USER_DISABLED" ->
        "This partner account has been disabled. Contact FixMechanic support."
      "ERROR_TOO_MANY_REQUESTS" ->
        "Too many failed attempts. Please wait a moment and try again."
      else -> error.localizedMessage ?: "Firebase Authentication error (${error.errorCode})."
    }
    is NoCredentialException ->
      "Google Sign-In could not find a matching credential. Add a Google account to this device and ensure this APK's SHA-1 fingerprint is registered in Firebase ($projectId)."
    is GetCredentialProviderConfigurationException ->
      "Google Play Services / Credential Manager is unavailable on this device. Please use Email & Password sign-in."
    is GetCredentialException ->
      error.localizedMessage ?: "Google Sign-In failed (${error.type})."
    else -> error.localizedMessage ?: "Authentication failed. Please try again."
  }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: (isNewUser: Boolean) -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope
) {
  val auth = ensureFirebaseInitialized(context)
  if (auth?.currentUser != null) {
    onAuthSuccess(false)
    return
  }

  // Handle pending web-OAuth redirect if Activity was recreated during Google Sign-In fallback
  val pending = auth?.pendingAuthResult
  if (pending != null) {
    scope.launch {
      try {
        val res = pending.awaitResult()
        onAuthSuccess(res.additionalUserInfo?.isNewUser == true)
      } catch (_: Exception) {
        onUnauthenticated()
      }
    }
    return
  }

  val clientId = resolveWebClientId(context)
  val activity = context.findActivity()
  if (clientId == null || activity == null) {
    onUnauthenticated()
    return
  }
  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(clientId)
    .setAutoSelectEnabled(true)
    .build()
  val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
  scope.launch {
    try {
      val result = credentialManager.getCredential(activity, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        val authResult = auth?.signInWithCredential(authCredential)?.awaitResult()
        onAuthSuccess(authResult?.additionalUserInfo?.isNewUser == true)
      } else {
        onUnauthenticated()
      }
    } catch (_: Exception) {
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: (isNewUser: Boolean) -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {}
) {
  val auth = ensureFirebaseInitialized(context)
  if (auth == null) {
    onAuthError("FirebaseAuth failed to initialize. Verify google-services.json and rebuild.")
    return
  }
  val activity = context.findActivity()
  if (activity == null) {
    onAuthError("Activity context required for Google Sign-In.")
    return
  }

  scope.launch {
    val clientId = resolveWebClientId(context)

    // 1. Primary native path: Jetpack CredentialManager (GetSignInWithGoogleOption -> GetGoogleIdOption)
    var credentialManagerError: Throwable? = null
    if (clientId != null) {
      try {
        val signInButtonOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryRequest = GetCredentialRequest.Builder().addCredentialOption(signInButtonOption).build()
        val result = try {
          credentialManager.getCredential(activity, primaryRequest)
        } catch (noCred: NoCredentialException) {
          Log.w("Auth", "GetSignInWithGoogleOption returned NoCredentialException; retrying with GetGoogleIdOption", noCred)
          val fallbackOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .build()
          val fallbackRequest = GetCredentialRequest.Builder().addCredentialOption(fallbackOption).build()
          credentialManager.getCredential(activity, fallbackRequest)
        }

        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          val authResult = auth.signInWithCredential(authCredential).awaitResult()
          onAuthSuccess(authResult.additionalUserInfo?.isNewUser == true)
          return@launch
        } else {
          credentialManagerError = IllegalStateException("Unexpected credential type returned by Google Sign-In.")
        }
      } catch (e: GetCredentialCancellationException) {
        Log.w("Auth", "Google Sign-In cancelled by user", e)
        onAuthCancelled()
        return@launch
      } catch (e: GetCredentialException) {
        // E.g. NoCredentialException / SHA-1 mismatch / Play Services Credential Manager unavailable on emulator.
        // Fall through to Firebase Google OAuth web provider flow below.
        Log.w("Auth", "CredentialManager failed (${e.type}: ${e.localizedMessage}); falling back to Firebase Google OAuth", e)
        credentialManagerError = e
      } catch (e: Exception) {
        Log.e("Auth", "Firebase signInWithCredential failed on project=${getRuntimeFirebaseProjectId(context)}", e)
        onAuthError(formatAuthErrorMessage(context, e))
        return@launch
      }
    }

    // 2. Resilient fallback path: Firebase Auth Google OAuth Provider (works on emulators & regardless of debug SHA-1)
    try {
      val oauthProvider = OAuthProvider.newBuilder("google.com")
        .addCustomParameter("prompt", "select_account")
        .build()
      val pending = auth.pendingAuthResult
      val authResult = if (pending != null) {
        pending.awaitResult()
      } else {
        auth.startActivityForSignInWithProvider(activity, oauthProvider).awaitResult()
      }
      onAuthSuccess(authResult.additionalUserInfo?.isNewUser == true)
    } catch (e: FirebaseAuthException) {
      if (e.errorCode == "ERROR_WEB_CONTEXT_CANCELED" || e.localizedMessage?.contains("canceled", ignoreCase = true) == true) {
        onAuthCancelled()
      } else {
        Log.e("Auth", "Firebase Google OAuth fallback failed on project=${getRuntimeFirebaseProjectId(context)}", e)
        onAuthError(formatAuthErrorMessage(context, e))
      }
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In fallback failed", e)
      val errToReport = credentialManagerError ?: e
      onAuthError(formatAuthErrorMessage(context, errToReport))
    }
  }
}

@Composable
fun GoogleSignInButton(
  onAuthSuccess: (isNewUser: Boolean) -> Unit,
  onAuthError: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }

  Button(
    onClick = {
      isLoading = true
      onGoogleSignInClicked(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = { isNewUser ->
          isLoading = false
          onAuthSuccess(isNewUser)
        },
        onAuthError = { errorMsg ->
          isLoading = false
          onAuthError(errorMsg)
        },
        scope = coroutineScope,
        onAuthCancelled = { isLoading = false }
      )
    },
    enabled = !isLoading,
    colors = ButtonDefaults.buttonColors(containerColor = Navy900, contentColor = SurfaceWhite),
    shape = RoundedCornerShape(10.dp),
    modifier = modifier
      .fillMaxWidth()
      .height(52.dp)
      .testTag("google_sign_in_button")
  ) {
    if (isLoading) {
      CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        strokeWidth = 2.dp
      )
    } else {
      Icon(Icons.Filled.AccountCircle, contentDescription = "Google Icon", modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(10.dp))
      Text("Sign in with Google", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
  }
}

fun signOutGoogle(
  context: Context,
  credentialManager: CredentialManager,
  onSignOutComplete: () -> Unit,
  scope: CoroutineScope
) {
  scope.launch {
    withContext(NonCancellable) {
      runCatching { FirebaseAuth.getInstance().signOut() }
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.e("Auth", "Failed to clear credential state", e)
      } finally {
        onSignOutComplete()
      }
    }
  }
}
