package com.example.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GoogleSyncUiState(
    val promptHandled: Boolean = false,
    val isSignedIn: Boolean = false,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val syncEnabled: Boolean = false,
    val statusMessage: String? = null,
    val configAvailable: Boolean = false
)

object GoogleSyncManager {
    private const val PREFS_NAME = "zerobook_google_sync"
    private const val KEY_PROMPT_HANDLED = "prompt_handled"
    private const val KEY_DEVICE_ID = "device_id"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val snapshotAdapter = moshi.adapter(AppSyncSnapshot::class.java)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var listenerRegistration: ListenerRegistration? = null
    private var lastPushedStamp: Long = 0L
    private var ignoreNextUpload = false

    fun buildSignInIntent(context: Context): Intent? {
        val client = buildGoogleSignInClient(context) ?: return null
        return client.signInIntent
    }

    fun buildInitialUiState(context: Context): GoogleSyncUiState {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return GoogleSyncUiState(
            promptHandled = prefs(context).getBoolean(KEY_PROMPT_HANDLED, false),
            isSignedIn = account != null,
            accountName = account?.displayName,
            accountEmail = account?.email,
            syncEnabled = account != null && hasFirebaseConfiguration(context),
            configAvailable = hasFirebaseConfiguration(context)
        )
    }

    fun markPromptHandled(context: Context) {
        prefs(context).edit().putBoolean(KEY_PROMPT_HANDLED, true).apply()
    }

    fun resetPromptState(context: Context) {
        prefs(context).edit().putBoolean(KEY_PROMPT_HANDLED, false).apply()
    }

    fun hasFirebaseConfiguration(context: Context): Boolean {
        val googleAppId = context.resources.getIdentifier("google_app_id", "string", context.packageName)
        val projectId = context.resources.getIdentifier("project_id", "string", context.packageName)
        val webClientId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return googleAppId != 0 && projectId != 0 && webClientId != 0
    }

    suspend fun completeGoogleSignIn(
        context: Context,
        account: GoogleSignInAccount
    ): Result<GoogleSyncUiState> {
        markPromptHandled(context)
        if (!hasFirebaseConfiguration(context)) {
            return Result.success(
                GoogleSyncUiState(
                    promptHandled = true,
                    isSignedIn = true,
                    accountName = account.displayName,
                    accountEmail = account.email,
                    syncEnabled = false,
                    statusMessage = "Google account connected, but Firebase sync is not configured in this build.",
                    configAvailable = false
                )
            )
        }

        if (FirebaseApp.getApps(context).isEmpty()) {
            return Result.failure(IllegalStateException("Firebase is not initialized for this build."))
        }

        val idToken = account.idToken
            ?: return Result.failure(IllegalStateException("Google Sign-In token was not available."))
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        return suspendCancellableCoroutine { continuation ->
            FirebaseAuth.getInstance()
                .signInWithCredential(credential)
                .addOnSuccessListener {
                    continuation.resume(
                        Result.success(
                            GoogleSyncUiState(
                                promptHandled = true,
                                isSignedIn = true,
                                accountName = account.displayName,
                                accountEmail = account.email,
                                syncEnabled = true,
                                statusMessage = "Sync is active for this account.",
                                configAvailable = true
                            )
                        ),
                        onCancellation = null
                    )
                }
                .addOnFailureListener { error ->
                    continuation.resume(Result.failure(error), onCancellation = null)
                }
        }
    }

    fun startRealtimeSync(
        context: Context,
        financialYear: String,
        onRemoteSnapshot: suspend (AppSyncSnapshot) -> Unit
    ) {
        listenerRegistration?.remove()
        if (!hasFirebaseConfiguration(context)) {
            return
        }

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val firestore = FirebaseFirestore.getInstance()
        val deviceId = getDeviceId(context)
        listenerRegistration = firestore
            .collection("zerobookSync")
            .document(user.uid)
            .collection("financialYears")
            .document(financialYear)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    return@addSnapshotListener
                }
                val sourceDeviceId = snapshot.getString("sourceDeviceId")
                val updatedAt = snapshot.getLong("updatedAt") ?: 0L
                if (sourceDeviceId == deviceId && updatedAt == lastPushedStamp) {
                    return@addSnapshotListener
                }
                val payload = snapshot.getString("payload") ?: return@addSnapshotListener
                val decoded = runCatching { snapshotAdapter.fromJson(payload) }.getOrNull() ?: return@addSnapshotListener
                ignoreNextUpload = true
                syncScope.launch {
                    onRemoteSnapshot(decoded)
                }
            }
    }

    fun stopRealtimeSync() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    suspend fun pushSnapshot(
        context: Context,
        financialYear: String,
        snapshot: AppSyncSnapshot
    ): Result<Unit> {
        if (ignoreNextUpload) {
            ignoreNextUpload = false
            return Result.success(Unit)
        }
        if (!hasFirebaseConfiguration(context)) {
            return Result.success(Unit)
        }

        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success(Unit)
        val firestore = FirebaseFirestore.getInstance()
        val payload = snapshotAdapter.toJson(snapshot)
        val updatedAt = System.currentTimeMillis()
        lastPushedStamp = updatedAt

        return suspendCancellableCoroutine { continuation ->
            firestore.collection("zerobookSync")
                .document(user.uid)
                .collection("financialYears")
                .document(financialYear)
                .set(
                    mapOf(
                        "financialYear" to financialYear,
                        "payload" to payload,
                        "updatedAt" to updatedAt,
                        "sourceDeviceId" to getDeviceId(context)
                    )
                )
                .addOnSuccessListener {
                    continuation.resume(Result.success(Unit), onCancellation = null)
                }
                .addOnFailureListener { error ->
                    continuation.resume(Result.failure(error), onCancellation = null)
                }
        }
    }

    suspend fun signOut(context: Context): Result<GoogleSyncUiState> {
        stopRealtimeSync()
        return suspendCancellableCoroutine { continuation ->
            buildGoogleSignInClient(context)?.signOut()?.addOnCompleteListener {
                FirebaseAuth.getInstance().signOut()
                continuation.resume(
                    Result.success(
                        GoogleSyncUiState(
                            promptHandled = true,
                            isSignedIn = false,
                            syncEnabled = false,
                            statusMessage = "Google account logged out.",
                            configAvailable = hasFirebaseConfiguration(context)
                        )
                    ),
                    onCancellation = null
                )
            } ?: continuation.resume(
                Result.success(
                    GoogleSyncUiState(
                        promptHandled = true,
                        isSignedIn = false,
                        syncEnabled = false,
                        statusMessage = "Google account logged out.",
                        configAvailable = hasFirebaseConfiguration(context)
                    )
                ),
                onCancellation = null
            )
        }
    }

    private fun buildGoogleSignInClient(context: Context): GoogleSignInClient? {
        val webClientId = resolveString(context, "default_web_client_id")
        val optionsBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()

        if (!webClientId.isNullOrBlank()) {
            optionsBuilder.requestIdToken(webClientId)
        }

        return GoogleSignIn.getClient(context, optionsBuilder.build())
    }

    private fun resolveString(context: Context, name: String): String? {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }

    private fun getDeviceId(context: Context): String {
        val existing = prefs(context).getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val newId = java.util.UUID.randomUUID().toString()
        prefs(context).edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
