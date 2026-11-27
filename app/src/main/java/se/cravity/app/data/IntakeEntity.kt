package se.cravity.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import se.cravity.app.model.Intake
import se.cravity.app.model.IntakeKind
import se.cravity.app.model.Tag

@Entity(tableName = "intake", indices = [Index("timeMillis")])
data class IntakeEntity(
    @PrimaryKey val id: String,
    val timeMillis: Long,
    val kind: String,
    /** Comma-separated tag keys, e.g. "breakfast,sweet". */
    val tags: String,
    val note: String,
) {
    fun toModel() = Intake(
        id = id,
        timeMillis = timeMillis,
        kind = runCatching { IntakeKind.valueOf(kind) }.getOrDefault(IntakeKind.MEAL),
        tags = tags.split(',').mapNotNull { Tag.fromKey(it) }.toSet(),
        note = note,
    )

    companion object {
        fun from(i: Intake) = IntakeEntity(
            id = i.id,
            timeMillis = i.timeMillis,
            kind = i.kind.name,
            tags = i.tags.sortedBy { it.ordinal }.joinToString(",") { it.key },
            note = i.note,
        )
    }
}
