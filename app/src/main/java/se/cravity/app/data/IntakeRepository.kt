package se.cravity.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.cravity.app.model.Intake

class IntakeRepository(private val dao: IntakeDao) {
    val intakes: Flow<List<Intake>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun all(): List<Intake> = dao.getAll().map { it.toModel() }

    /** Entries relevant to the clock and gut outlook: the last day plus anything planned. */
    suspend fun recent(nowMillis: Long): List<Intake> =
        dao.since(nowMillis - 24 * 3_600_000L).map { it.toModel() }

    suspend fun save(intake: Intake) = dao.upsert(IntakeEntity.from(intake))

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun exportJson(nowMillis: Long): String = ExportCodec.encode(all(), nowMillis)

    /** Imports by merging on id. Returns how many entries were added and updated. */
    suspend fun importJson(text: String): MergeResult {
        val (imported, skipped) = ExportCodec.decode(text)
        val result = ExportCodec.merge(all(), imported, skipped)
        dao.upsertAll(imported.map { IntakeEntity.from(it) })
        return result
    }
}
