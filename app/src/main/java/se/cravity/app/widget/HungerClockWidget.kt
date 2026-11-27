package se.cravity.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import se.cravity.app.MainActivity
import se.cravity.app.cravity
import se.cravity.app.model.ClockFace
import se.cravity.app.model.ClockMode
import se.cravity.app.ui.ClockRenderer
import se.cravity.app.ui.StatusText

class HungerClockWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.cravity
        val now = System.currentTimeMillis()
        val intakes = app.repository.recent(now)
        val mode = app.settings.current()
        val face = ClockFace.build(intakes, now, mode)
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bitmap = ClockRenderer.bitmap(face, 420, dark)
        val status = StatusText.headline(intakes, now)
        val fg = if (dark) Color(0xFFECECEC) else Color(0xFF222222)
        val bg = if (dark) Color(0xEE1E1E1E) else Color(0xEEFAFAFA)

        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(bg).padding(6.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = status,
                    modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                )
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status,
                        style = TextStyle(color = ColorProvider(fg), fontSize = 12.sp),
                        maxLines = 2,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = if (mode == ClockMode.H12) "12h" else "24h",
                        style = TextStyle(color = ColorProvider(fg), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.padding(6.dp).clickable(actionRunCallback<ToggleModeAction>()),
                    )
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = HungerClockWidget().updateAll(context)
    }
}

class ToggleModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.cravity.settings.toggle()
        HungerClockWidget().updateAll(context)
    }
}

class HungerClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HungerClockWidget()
}
