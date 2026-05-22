package com.care.firebase

import com.care.data.ActivityEvent
import com.care.data.ActivityEventRepository
import com.care.data.CarePreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class SyncResult(
    val attempted: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val message: String = ""
)

class FirebaseSyncRepository(
    private val eventRepository: ActivityEventRepository,
    private val prefs: CarePreferences
) {
    val currentUserId: String?
        get() = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()

    suspend fun signIn(email: String, password: String): Result<String> = runCatching {
        val uid = FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await().user?.uid
            ?: error("Firebase did not return a signed-in user.")
        prefs.parentUserId = uid
        registerDevice(uid)
        uid
    }

    suspend fun createAccount(email: String, password: String): Result<String> = runCatching {
        val uid = FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).await().user?.uid
            ?: error("Firebase did not return a new user.")
        prefs.parentUserId = uid
        registerDevice(uid)
        uid
    }

    fun signOut() {
        runCatching { FirebaseAuth.getInstance().signOut() }
        prefs.parentUserId = null
    }

    suspend fun registerDevice(parentUserId: String = requireParentId()) {
        val data = mapOf(
            "deviceId" to prefs.deviceId,
            "deviceName" to prefs.deviceName,
            "model" to android.os.Build.MODEL,
            "manufacturer" to android.os.Build.MANUFACTURER,
            "lastSeenAt" to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(parentUserId)
            .collection("devices")
            .document(prefs.deviceId)
            .set(data)
            .await()
    }

    suspend fun syncPending(limit: Int = 100): SyncResult {
        if (!prefs.firebaseSyncEnabled) return SyncResult(message = "Firebase sync is disabled.")
        val parentUserId = currentUserId ?: prefs.parentUserId ?: return SyncResult(message = "Sign in to sync.")
        val events = eventRepository.pendingSyncEvents(limit)
        if (events.isEmpty()) return SyncResult(message = "No pending events.")

        var succeeded = 0
        var failed = 0
        val eventCollection = FirebaseFirestore.getInstance()
            .collection("users")
            .document(parentUserId)
            .collection("devices")
            .document(prefs.deviceId)
            .collection("events")

        for (event in events) {
            try {
                eventCollection.document(event.id.toString()).set(event.toFirestoreMap()).await()
                eventRepository.markSynced(event.id)
                succeeded++
            } catch (_: Exception) {
                eventRepository.markFailed(event.id)
                failed++
            }
        }

        return SyncResult(
            attempted = events.size,
            succeeded = succeeded,
            failed = failed,
            message = "Synced $succeeded of ${events.size} events."
        )
    }

    suspend fun deleteCloudDataForDevice(): Result<Unit> = runCatching {
        val parentUserId = requireParentId()
        val firestore = FirebaseFirestore.getInstance()
        val events = firestore.collection("users")
            .document(parentUserId)
            .collection("devices")
            .document(prefs.deviceId)
            .collection("events")
            .limit(500)
            .get()
            .await()

        val batch = firestore.batch()
        events.documents.forEach { batch.delete(it.reference) }
        batch.delete(
            firestore.collection("users")
                .document(parentUserId)
                .collection("devices")
                .document(prefs.deviceId)
        )
        batch.commit().await()
    }

    private fun requireParentId(): String =
        currentUserId ?: prefs.parentUserId ?: error("No parent account is signed in.")

    private fun ActivityEvent.toFirestoreMap(): Map<String, Any?> = mapOf(
        "eventType" to eventType.name,
        "packageName" to packageName,
        "details" to details,
        "timestamp" to timestamp,
        "startTimestamp" to startTimestamp,
        "endTimestamp" to endTimestamp,
        "deviceId" to deviceId,
        "createdAt" to createdAt,
        "syncedAt" to System.currentTimeMillis()
    )
}
