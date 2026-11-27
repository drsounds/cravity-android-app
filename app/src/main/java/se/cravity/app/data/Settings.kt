package se.cravity.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import se.cravity.app.model.ClockMode

/** Tiny local preference store for the clock mode, shared by the app and the widget. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cravity_prefs", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(read())
    val mode: StateFlow<ClockMode> = _mode

    private fun read(): ClockMode =
        runCatching { ClockMode.valueOf(prefs.getString(KEY_MODE, null) ?: "H12") }.getOrDefault(ClockMode.H12)

    fun current(): ClockMode = read()

    fun setMode(mode: ClockMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    fun toggle(): ClockMode {
        val next = if (read() == ClockMode.H12) ClockMode.H24 else ClockMode.H12
        setMode(next)
        return next
    }

    companion object {
        private const val KEY_MODE = "clock_mode"
    }
}
