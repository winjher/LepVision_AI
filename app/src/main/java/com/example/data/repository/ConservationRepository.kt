package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BreedingBatch
import com.example.data.model.ClassificationRecord
import com.example.data.model.UserPreferences
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConservationRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        return uid
    }

    fun observeClassifications(userId: String): Flow<List<ClassificationRecord>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/classifications"
        emitAll(
            db.collection("users").document(uid).collection("classifications")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ClassificationRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBatches(userId: String): Flow<List<BreedingBatch>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/batches"
        emitAll(
            db.collection("users").document(uid).collection("batches")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BreedingBatch::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePreferences(userId: String): Flow<UserPreferences> = flow {
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserPreferences::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UserPreferences(userId = uid)
                    } else {
                        UserPreferences(userId = uid)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveClassification(userId: String, record: ClassificationRecord): Result<String> {
        var docPath = "users/$userId/classifications"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("classifications").document()
            docPath = docRef.path
            docRef.set(record.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateClassificationNotes(
        userId: String,
        recordId: String,
        notes: String,
        batchName: String
    ): Result<Unit> {
        var docPath = "users/$userId/classifications/$recordId"
        return try {
            val uid = requireUserId(userId)
            require(recordId.isNotBlank()) { "Record ID cannot be blank" }
            val docRef = db.collection("users").document(uid).collection("classifications").document(recordId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "notes" to notes.trim().take(500),
                    "batchName" to batchName.trim().take(80),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteClassification(userId: String, recordId: String): Result<Unit> {
        var docPath = "users/$userId/classifications/$recordId"
        return try {
            val uid = requireUserId(userId)
            require(recordId.isNotBlank()) { "Record ID cannot be blank" }
            val docRef = db.collection("users").document(uid).collection("classifications").document(recordId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveBatch(userId: String, batch: BreedingBatch): Result<String> {
        var docPath = "users/$userId/batches"
        return try {
            val uid = requireUserId(userId)
            require(batch.name.isNotBlank()) { "Batch name is required." }
            require(batch.initialCount >= 1L) { "Initial count must be at least 1." }
            val docRef = if (batch.id.isNotBlank()) {
                db.collection("users").document(uid).collection("batches").document(batch.id)
            } else {
                db.collection("users").document(uid).collection("batches").document()
            }
            docPath = docRef.path
            if (batch.id.isBlank()) {
                docRef.set(batch.toCreateMap(uid)).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "name" to batch.name.trim().take(80),
                    "targetSpecies" to batch.targetSpecies.trim().take(100),
                    "initialCount" to batch.initialCount.coerceIn(1L, 100_000L),
                    "healthyLarvaeCount" to batch.healthyLarvaeCount.coerceIn(0L, 100_000L),
                    "diseasedLarvaeCount" to batch.diseasedLarvaeCount.coerceIn(0L, 100_000L),
                    "goodPupaeCount" to batch.goodPupaeCount.coerceIn(0L, 100_000L),
                    "defectivePupaeCount" to batch.defectivePupaeCount.coerceIn(0L, 100_000L),
                    "predictedYieldPercent" to batch.predictedYieldPercent.coerceIn(0.0, 100.0),
                    "predictedAdultCount" to batch.predictedAdultCount.coerceIn(0L, 100_000L),
                    "hostPlantHealth" to batch.hostPlantHealth,
                    "notes" to (batch.notes?.trim()?.take(500) ?: ""),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBatch(userId: String, batchId: String): Result<Unit> {
        var docPath = "users/$userId/batches/$batchId"
        return try {
            val uid = requireUserId(userId)
            require(batchId.isNotBlank()) { "Batch ID cannot be blank" }
            val docRef = db.collection("users").document(uid).collection("batches").document(batchId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun savePreferences(userId: String, prefs: UserPreferences): Result<Unit> {
        var docPath = "users/$userId/settings/preferences"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
            docPath = docRef.path
            docRef.set(prefs.toWriteMap(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun buildManifestCsv(records: List<ClassificationRecord>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val esc: (String) -> String = { v -> "\"${v.replace("\"", "\"\"")}\"" }
        val lines = mutableListOf(
            "task,label,confidence,identified,species,stage,disease,instar,defect,sex,batch,record_id,timestamp"
        )
        records.forEach { r ->
            val ts = r.createdAt?.toDate() ?: Date()
            lines.add(
                listOf(
                    r.task,
                    r.label,
                    String.format(Locale.US, "%.4f", r.confidence),
                    r.identified.toString(),
                    r.speciesLabel.orEmpty(),
                    r.stageLabel.orEmpty(),
                    r.diseaseLabel.orEmpty(),
                    r.instarLabel.orEmpty(),
                    r.defectLabel.orEmpty(),
                    r.sexLabel.orEmpty(),
                    r.batchName.orEmpty(),
                    r.id,
                    dateFormat.format(ts)
                ).joinToString(",") { esc(it) }
            )
        }
        return lines.joinToString("\n")
    }
}
