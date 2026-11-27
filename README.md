# Cravity

An Android hunger clock. Instead of counting calories you log what you eat with a few tags, and an
analog clock (in the app and as a home-screen widget) shows when hunger is likely to come back.

> **Not medical advice.** Hunger, digestion and gut outlooks in Cravity are rough heuristics for
> personal reflection. They are not a diagnosis, a nutrition plan or a substitute for a professional.

## Features

- **Hunger clock widget**, 12 h or 24 h dial (tap `12h`/`24h` on the widget to switch). A coloured
  ring shows the time ahead, one full turn of the dial from now:
  - green: satisfied, plenty still in the stomach
  - orange: hunger building
  - red: hunger imminent
- **Log a meal and the clock resets.** You can also log with a time in the past, or plan a meal
  up to 4 h ahead; planned meals appear as rings on the dial and the zones account for them.
- **Digestion estimate**: when each meal is likely to have left the stomach. The dial shows the
  latest meal's "processed by" time as a small triangle.
- **Tags instead of calories**: breakfast, meal, snack, sweet, fast food, binge, celebration,
  stress, anxiety, supplement.
- **Foodmap**: each tag adds points to a three-dimensional vector, `(sweetness, grain, fat)`.
- **Gut outlook**: the foodmap is mapped to a three-axis outlook for the coming hours.
- **Local only, with export/import** (see Privacy).

## The foodmap

| Tag         | Sweetness | Grain | Fat | Stomach load | Stress |
|-------------|----------:|------:|----:|-------------:|-------:|
| breakfast   | 1         | 2     | 1   | 0.8          |        |
| meal        | 0.5       | 2     | 2   | 1.0          |        |
| snack       | 1         | 1     | 0.5 | 0.35         |        |
| sweet       | 3         | 0.5   | 0.5 | 0.3          |        |
| fast food   | 1         | 2     | 3   | 1.2          |        |
| binge       | 2         | 2     | 2   | 1.8          |        |
| celebration | 2         | 1     | 2   | 1.3          |        |
| stress      | 1         | 0     | 1   | 0            | 1      |
| anxiety     | 0.5       | 0     | 0.5 | 0            | 1      |
| supplement  | 0         | 0     | 0   | 0.05         |        |

**Why fat is the third dimension.** The request named sweetness and grain and left the third axis
open. Fat (richness: fried food, cream, cheese, fatty meat) was chosen because it is the dietary
factor that most clearly slows how fast the stomach empties, so it ties the log directly to the
digestion estimate and the hunger clock. Fibre was considered, but it overlaps a lot with grain.

The current foodmap shown in the app sums the last 24 hours, with older entries fading linearly.

## The models (`app/src/main/java/se/cravity/app/model`)

**Digestion** (`Digestion.kt`). Gastric emptying uses the power-exponential curve common in
gastric-emptying studies: fraction left `f(t) = 2^-((t / tHalf)^1.6)`. The half time starts at
90 min for an ordinary meal and is scaled by `sqrt(load)`, up to +60 % for fat-heavy entries,
down to −25 % for sweet ones, and +20 % with stress or anxiety (clamped to 15–300 min).
A meal counts as *processed* when 10 % remains, about 4 h for an ordinary meal.

**Hunger clock** (`HungerClock.kt`). Stomach content is the sum of every entry's
`load × f(t)`. Above 0.35 is green, above 0.08 is orange, otherwise red. A new meal adds
content, which is what resets the clock.

**Gut outlook** (`GutOutlook.kt`). A 3×3 matrix maps `(sweetness, grain, fat)` to three axes:

- *sugar swing*: blood-sugar rise and the dip after (sweetness; damped by fat), peaks ~1 h
- *bloating*: fermentation as starches and sugars reach the gut (grain, sweetness), peaks ~4–5 h
- *heaviness*: fullness, sluggishness, reflux risk (fat and load), follows what is still in the stomach

Stress and anxiety scale bloating and heaviness up by 50 %. Each axis is squashed to 0–1. The app
shows the next 8 hours.

## Privacy

- The app has **no internet permission**. WorkManager's `ACCESS_NETWORK_STATE` is explicitly
  removed from the manifest.
- Data lives in a local Room database (`cravity.db`) and SharedPreferences.
- Android cloud backup and device-transfer are disabled, so the log is not copied off the device.
- **Export / import**: *Data → Export to file…* writes a JSON file through the system file picker;
  *Import from file…* merges a file back in by entry id (imported entries win, re-importing the
  same file changes nothing, unknown tags are skipped).

Export format:

```json
{
  "format": "cravity",
  "version": 1,
  "exported_at": 1764240000000,
  "entries": [
    { "id": "…uuid…", "time": 1764230000000, "kind": "MEAL", "tags": ["breakfast", "sweet"], "note": "" }
  ]
}
```

## Building

Requirements: JDK 17+, Android SDK with platform 35.

```sh
./gradlew testDebugUnitTest   # unit tests for the models and export/import
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Stack: Kotlin, Jetpack Compose (Material 3), Room, Glance app widget, WorkManager (refreshes the
widget every 15 minutes), kotlinx.serialization. Min SDK 26.

The clock is drawn once in `ui/ClockRenderer.kt` with `android.graphics`, and reused by the app
(Compose canvas) and the widget (bitmap).
