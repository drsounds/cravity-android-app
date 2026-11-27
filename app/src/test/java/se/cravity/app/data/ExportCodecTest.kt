package se.cravity.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import se.cravity.app.model.Intake
import se.cravity.app.model.IntakeKind
import se.cravity.app.model.Tag

class ExportCodecTest {
    private val a = Intake("a", 1_000L, IntakeKind.MEAL, setOf(Tag.BREAKFAST, Tag.SWEET), "oats \"and\" honey ✓")
    private val b = Intake("b", 2_000L, IntakeKind.SUPPLEMENT, setOf(Tag.SUPPLEMENT), "")
    private val c = Intake("c", 3_000L, IntakeKind.MEAL, setOf(Tag.FAST_FOOD, Tag.STRESS, Tag.CELEBRATION))

    @Test fun roundTripPreservesEverything() {
        val json = ExportCodec.encode(listOf(c, a, b), nowMillis = 42L)
        val (decoded, skipped) = ExportCodec.decode(json)
        assertEquals(0, skipped)
        assertEquals(listOf(a, b, c), decoded) // sorted by time on export
    }

    @Test fun exportContainsHeaderAndTagKeys() {
        val json = ExportCodec.encode(listOf(a), 42L)
        assertTrue(json.contains("\"format\": \"cravity\""))
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"exported_at\": 42"))
        assertTrue(json.contains("\"breakfast\""))
    }

    @Test fun unknownTagsAndFieldsAreIgnored() {
        val json = """
            {"format":"cravity","version":1,"exported_at":1,"extra":true,
             "entries":[{"id":"z","time":5,"kind":"MEAL","tags":["meal","martian"],"mood":"?"}]}
        """.trimIndent()
        val (decoded, skipped) = ExportCodec.decode(json)
        assertEquals(1, skipped)
        assertEquals(setOf(Tag.MEAL), decoded.single().tags)
        assertEquals("", decoded.single().note)
    }

    @Test fun rejectsGarbageOtherFormatsAndNewerVersions() {
        listOf(
            "not json",
            """{"format":"other","version":1,"exported_at":1,"entries":[]}""",
            """{"format":"cravity","version":99,"exported_at":1,"entries":[]}""",
            """{"format":"cravity","version":1,"exported_at":1,"entries":[{"id":"","time":1,"kind":"MEAL","tags":[]}]}""",
        ).forEach {
            try {
                ExportCodec.decode(it)
                fail("expected ImportException for $it")
            } catch (_: ImportException) {
            }
        }
    }

    @Test fun mergeAddsNewUpdatesChangedAndKeepsExisting() {
        val changedA = a.copy(note = "edited")
        val r = ExportCodec.merge(existing = listOf(a, b), imported = listOf(changedA, b, c))
        assertEquals(1, r.added)
        assertEquals(1, r.updated)
        assertEquals(listOf(c, b, changedA), r.merged) // newest first
    }

    @Test fun importingTheSameExportTwiceIsIdempotent() {
        val (decoded, _) = ExportCodec.decode(ExportCodec.encode(listOf(a, b, c), 1L))
        val r = ExportCodec.merge(listOf(a, b, c), decoded)
        assertEquals(0, r.added)
        assertEquals(0, r.updated)
        assertEquals(3, r.merged.size)
    }

    @Test fun entityRoundTrip() {
        assertEquals(c, IntakeEntity.from(c).toModel())
        assertEquals("fast_food,celebration,stress", IntakeEntity.from(c).tags)
    }
}
