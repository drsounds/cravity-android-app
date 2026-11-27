package se.cravity.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.cravity.app.cravity
import se.cravity.app.data.ImportException
import se.cravity.app.model.ClockFace
import se.cravity.app.model.ClockMode
import se.cravity.app.model.Foodmap
import se.cravity.app.model.GutForecastPoint
import se.cravity.app.model.GutModel
import se.cravity.app.model.Intake
import se.cravity.app.model.IntakeKind
import se.cravity.app.model.Tag
import se.cravity.app.widget.HungerClockWidget
import java.util.UUID

data class UiState(
    val now: Long,
    val mode: ClockMode,
    val intakes: List<Intake>,
    val face: ClockFace,
    val headline: String,
    val digestion: String?,
    val foodmap: Foodmap,
    val forecast: List<GutForecastPoint>,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.cravity.repository
    private val settings = app.cravity.settings

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val state: StateFlow<UiState?> = combine(repo.intakes, settings.mode, ticker) { intakes, mode, now ->
        UiState(
            now = now,
            mode = mode,
            intakes = intakes,
            face = ClockFace.build(intakes, now, mode),
            headline = StatusText.headline(intakes, now),
            digestion = StatusText.digestion(intakes, now),
            foodmap = GutModel.recentFoodmap(intakes, now),
            forecast = GutModel.forecast(intakes, now, hours = 8),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMode(mode: ClockMode) {
        settings.setMode(mode)
        refreshWidget()
    }

    fun log(kind: IntakeKind, tags: Set<Tag>, offsetMinutes: Int, note: String) {
        viewModelScope.launch {
            val time = System.currentTimeMillis() + offsetMinutes * 60_000L
            repo.save(Intake(UUID.randomUUID().toString(), time, kind, tags, note.trim()))
            HungerClockWidget.refresh(getApplication())
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repo.delete(id)
            HungerClockWidget.refresh(getApplication())
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val text = repo.exportJson(System.currentTimeMillis())
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            _messages.emit(if (result.isSuccess) "Exported" else "Export failed: ${result.exceptionOrNull()?.message}")
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val msg = try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                }
                val r = repo.importJson(text)
                HungerClockWidget.refresh(getApplication())
                buildString {
                    append("Imported: ${r.added} new, ${r.updated} updated")
                    if (r.skippedTags > 0) append(", ${r.skippedTags} unknown tags ignored")
                }
            } catch (e: ImportException) {
                "Import failed: ${e.message}"
            } catch (e: Exception) {
                "Import failed: could not read file"
            }
            _messages.emit(msg)
        }
    }

    private fun refreshWidget() {
        viewModelScope.launch { HungerClockWidget.refresh(getApplication()) }
    }
}
