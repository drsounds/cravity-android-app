package se.cravity.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.cravity.app.model.Intake
import se.cravity.app.model.IntakeKind
import se.cravity.app.model.Tag

@Serializable
data class ExportFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    @SerialName("exported_at") val exportedAt: Long,
    val entries: List<ExportEntry>,
) {
    companion object {
        const val FORMAT = "cravity"
        const val VERSION = 1
    }
}

@Serializable
data class ExportEntry(
    val id: String,
    @SerialName("time") val timeMillis: Long,
    val kind: String,
    val tags: List<String>,
    val note: String = "",
)

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Result of merging an import into existing data. */
data class MergeResult(val merged: List<Intake>, val added: Int, val updated: Int, val skippedTags: Int)

/** JSON export/import. Pure Kotlin, no Android dependencies, so it is unit-testable. */
object ExportCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(intakes: List<Intake>, nowMillis: Long): String =
        json.encodeToString(
            ExportFile.serializer(),
            ExportFile(
                exportedAt = nowMillis,
                entries = intakes.sortedBy { it.timeMillis }.map {
                    ExportEntry(it.id, it.timeMillis, it.kind.name, it.tags.sortedBy { t -> t.ordinal }.map { t -> t.key }, it.note)
                },
            ),
        )

    /**
     * Parses an export file. Unknown tags are dropped (and counted) rather than failing,
     * so files from newer versions still import.
     */
    fun decode(text: String): Pair<List<Intake>, Int> {
        val file = try {
            json.decodeFromString(ExportFile.serializer(), text)
        } catch (e: Exception) {
            throw ImportException("Not a valid Cravity export file", e)
        }
        if (file.format != ExportFile.FORMAT) throw ImportException("Not a Cravity export file")
        if (file.version > ExportFile.VERSION) throw ImportException("Export is from a newer version of Cravity")
        var skipped = 0
        val intakes = file.entries.map { e ->
            if (e.id.isBlank()) throw ImportException("Entry without id")
            val tags = e.tags.mapNotNull { k -> Tag.fromKey(k).also { if (it == null) skipped++ } }.toSet()
            Intake(
                id = e.id,
                timeMillis = e.timeMillis,
                kind = runCatching { IntakeKind.valueOf(e.kind) }.getOrDefault(IntakeKind.MEAL),
                tags = tags,
                note = e.note,
            )
        }
        return intakes to skipped
    }

    /** Merges imported entries into existing ones by id; imported entries win on conflict. */
    fun merge(existing: List<Intake>, imported: List<Intake>, skippedTags: Int = 0): MergeResult {
        val byId = existing.associateBy { it.id }.toMutableMap()
        var added = 0
        var updated = 0
        for (i in imported) {
            val old = byId[i.id]
            when {
                old == null -> added++
                old != i -> updated++
            }
            byId[i.id] = i
        }
        return MergeResult(byId.values.sortedByDescending { it.timeMillis }, added, updated, skippedTags)
    }
}
