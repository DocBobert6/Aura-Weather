# Aura Weather 0.3.3

A UI and font audit, and the header is navy now. The orange-red
gradient is gone: the header is solid navy like the rest of the era
screens, and the little logo box in its corner shows our sun mark
instead of the words AURA WEATHER. The big temperature is centred
properly under the moon now — it used to sit a touch to the left. The
air-quality numbers read properly too: the pollutant units said
"G/M", because the tiny µ and ³ characters draw as nothing in the
retro type; they now read "UG/M3" in plain letters, and every visible
string has been checked for characters the type cannot draw. The
14-day rows no longer clip long words — "PARTLY" and "DRIZZLE" used
to lose their last letters. The UV card says NIGHT after dark
instead of showing the daytime maximum at midnight, and in daylight
it says TODAY'S MAX so you know what the number is. The wind arrow
now points where the wind is blowing to, not where it came from. And
the air-quality gauge is an era-style segmented bar instead of a
modern semicircle, with a CURRENT HOUR label on the pollutant
numbers so the overall index and the per-pollutant pills stop
looking like they disagree. The all-clear alerts card is tighter,
so it no longer leaves half the screen empty.

## Notes

- This is an arm64-only APK (`arm64-v8a`); there is no universal build.
- The signed release APK installs straight over 0.3.2 (same signing
  key) — no uninstall, no data wipe. It will not install over a debug
  build (different signing key); uninstall the debug build first, which
  wipes app data.

---

# Aura Weather 0.3.2

A widget audit, done properly. The widgets now actually show the
Star4000 type (the font files were in the last build, but the home
screen would not use them — the text is now drawn with the type baked
in, so what you see is the retro face on all three widgets). The
hourly strip is the right height again instead of nearly twice as
tall, and its first cell shows the same temperature as the app — it
used to disagree with the main widget about right now. The radar
widget no longer goes blank when its newest picture is not ready: it
uses the most recent frame that works and says so on the label, and
if the very first picture fails it tries again two minutes later
instead of waiting a quarter of an hour. There is a new row in
Settings, "Background updates", which opens the system's
battery-exemption prompt for Aura — your tap only, nothing
automatic — so the widgets stay fresh while the app is closed.
Weather icons are fixed too: snow shows a snowflake instead of a
raindrop, fog shows a cloud with lines instead of a plain overcast
cloud, and a partly-cloudy night shows a moon with a small cloud
instead of a bare moon. The 14-day row's words and its icon now
always agree with each other.

## Notes

- This is an arm64-only APK (`arm64-v8a`); there is no universal build.
- The signed release APK installs straight over 0.3.1 (same signing
  key) — no uninstall, no data wipe. It will not install over a debug
  build (different signing key); uninstall the debug build first, which
  wipes app data.

---

# Aura Weather 0.3.1

Today's high is honest now. The hero used to show the current
temperature as the day's high whenever the real high was missing —
at 12:43 AM it read "H: 37°" when 37° was just the temperature right
then, not the day's high. It now shows "H: --" until a real high
exists, the same way the low already did. The 14-day outlook had the
same kind of problem the other way round: it labelled its first row
"TODAY" by position, and after midnight that row was still yesterday
— its daytime high long gone — while the real today sat one row down
mislabelled as tomorrow. The outlook now starts at today, so TODAY
is actually today and Monday is actually Monday.

## Notes

- This is an arm64-only APK (`arm64-v8a`); there is no universal build.
- The signed release APK installs straight over 0.3.0 (same signing
  key) — no uninstall, no data wipe. It will not install over a debug
  build (different signing key); uninstall the debug build first, which
  wipes app data.

---

# Aura Weather 0.3.0

Six-hour future radar, a lighter CRT vignette, and widgets that finally
look like the app — including a radar widget.

## IN 6H future radar

- A new IN 6H range on the radar tab, next to LIVE, IN 24H and IN 48H.
  It plays the next six hours of HRRR simulated reflectivity in
  15-minute steps (24 frames), labelled FORECAST like the other future
  ranges. Six hours is the range where the model is at its best — its
  first hour or two are its weakest, so there is no 2-hour range.

## Lighter CRT vignette

- The radar's CRT vignette is now very slight, as intended: the picture
  stays clear until 55% of the way to the corner, then darkens gently
  to the edge. Before, it darkened gradually from the centre. Scanlines,
  grille and phosphor tint are unchanged.

## Widgets rebuilt (classic RemoteViews)

- The home-screen widgets are rebuilt without Glance, so they can use
  the Star4000 faces like the rest of the app — the old widgets were
  stuck on the system font. The main widget is square (2×2) by default
  now and can still be resized up to 4×2; the hourly strip stays 4×1.
  Both still show cached data and refresh on the same hourly schedule.

## Radar widget

- A new 4×2 radar widget: a still radar picture for your place, with
  the CRT look baked in, refreshed every 15 minutes. Each refresh steps
  through now, +15 minutes and +30 minutes, and the label says which
  frame you are looking at ("LIVE NOW · 8:00 PM", "IN 15M · 8:15 PM").
  Tap it to open the radar tab. There are no controls on the widget
  itself — it is a picture, not a player — and if a refresh fails it
  keeps showing the last picture, labelled stale.
- The 15-minute refresh is for this widget only. The other widgets
  still refresh hourly, and nothing else in the app runs on a schedule.

## Notes

- This is an arm64-only APK (`arm64-v8a`); there is no universal build.
- The signed release APK installs straight over 0.2.0 (same signing
  key) — no uninstall, no data wipe. It will not install over a debug
  build (different signing key); uninstall the debug build first, which
  wipes app data.

---

# Aura Weather 0.2.0

You can now add a place by searching for it, and remove places you no
longer need.

## Place search

- In Settings, under Saved Places, ADD now opens a search box. Type a
  city, a city and state (like "Crittenden, KY"), or a zip code, and
  pick the right place from the list.
- A zip code is looked up first on Zippopotam.us, with Open-Meteo as
  the backup if that fails. City names are searched on Open-Meteo.
- Entering latitude and longitude by hand is still there — it is the
  "ENTER COORDINATES" button in the search box.

## Remove a location

- Every saved place now has a remove button in Settings. Tap it and
  the place is gone right away, along with its saved weather.
- If you remove the place you are currently looking at, another saved
  place is selected instead — or the app goes back to the empty state
  if that was the last one.
- Current Location cannot be removed: it is your phone's GPS location,
  and it comes back every time you tap "use current location".

## Notes

- This is an arm64-only APK (`arm64-v8a`); there is no universal build.
- The signed release APK will not install over a debug build (different
  signing key). Uninstall the debug build first — that wipes app data,
  including places and settings, which need re-adding afterwards.

---

# Aura Weather 0.1.1

Fixes the first-launch crash in 0.1.0. That release crashed every time it
was opened: after the first forecast loaded, the app tried to save it to
its offline cache and crashed, so it never got past the first load. The
cause was the release build's code shrinker (R8) renaming the
weather-condition names — ClearDay, Rain, and the rest — that Moshi reads
when saving the forecast cache. The build now tells R8 to leave those
names alone, and to leave the forecast-cache models' fields alone too, so
the cache save works and the app stays open.

## Notes

- This is an arm64-only APK (`arm64-v8a`); there is no universal build.
- The signed release APK will not install over a debug build (different
  signing key). Uninstall the debug build first — that wipes app data,
  including places and settings, which need re-adding afterwards.

---

# Aura Weather 0.1.0

First release. The radar tab is rebuilt from the ground up, the retro look
gets its finishing touch, and the in-app updater goes live with this release.

## Rebuilt radar

- New MapLibre Native engine (Vulkan) replaces the old tile screen entirely.
- Live 10-frame composite radar loop (~50 minutes of history). MRMS is the
  primary source, with LibreWXR tiles and IEM NEXRAD tiles as fallbacks; the
  loop stays source-locked until RETRY.
- Satellite layers: NASA GIBS GOES-East ABI — Visible GeoColor and Clean
  Infrared, one WMS image per visit with a WMTS fallback.
- Future radar: IN 24H and IN 48H ranges show HRRR simulated reflectivity,
  labelled FORECAST and never mixed with the live loop. Future 72h is not
  offered — HRRR caps at 48h.
- Play/pause, frame scrub with timestamps, 150 ms crossfades, and a
  source + data-cost panel naming the loop's locked source with the actual
  bytes fetched.

## 90s CRT filter

- The radar map gets a period-CRT treatment: scanlines, aperture grille,
  warm phosphor tint and vignette — no glow, no curvature, and it never
  touches the radar pixels themselves.
- On by default; Settings has a CRT FILTER toggle to turn it off.

## Fixes

- Radar tab bottom chrome no longer floats mid-screen; it sits at the
  bottom of the screen where it belongs.
- Pinch-out no longer triggers an unwanted auto-zoom on the radar map.

## Notes

- This is an arm64-only APK (`arm64-v8a`); there is no universal build.
- The signed release APK will not install over a debug build (different
  signing key). Uninstall the debug build first — that wipes app data,
  including places and settings, which need re-adding afterwards.
- The in-app updater (Settings → APP UPDATES → CHECK FOR UPDATES) now
  finds this release; it installs only a release whose SHA-256 digest
  matches the one GitHub reports and whose signing certificate matches
  the installed app.
