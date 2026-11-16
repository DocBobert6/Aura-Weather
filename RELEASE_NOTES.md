# Aura Weather 0.2.1

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
- The signed release APK installs straight over 0.2.0 (same signing
  key) — no uninstall, no data wipe. It will not install over a debug
  build (different signing key); uninstall the debug build first.

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
