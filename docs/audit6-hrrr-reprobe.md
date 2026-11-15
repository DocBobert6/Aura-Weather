# Fix 8 (AUDIT6) — HRRR re-probe, 2026-11-15 ~04:30 UTC

Re-probed IEM HRRR for the app's actual leads before building anything
(AUDIT6 §9 fix 8). Raw data: `~/workspace/aura-weather/hrrr-reprobe-20261115.json`
(probe script: `~/workspace/aura-weather/hrrr_reprobe.py`).

## Result: HRRR is UP — do NOT build the S3/GRIB2 route

| Lead | Layer form | Result |
|---|---|---|
| F0075 | `hrrr::REFD-F0075-0` on `/cache/tile.py/` | 77/77 HTTP 200, 17.7 s wall, median 1.01 s/tile |
| F0120 | `hrrr::REFD-F0120-0` | 77/77 HTTP 200, 19.2 s wall, median 1.12 s/tile |
| F0135 | `hrrr::REFD-F0135-0` | 77/77 HTTP 200, 18.3 s wall, median 1.07 s/tile |

The research's 503 story is not reproduced for the app's layers (same
conclusion as AUDIT6 §8.1). The future-radar emptiness in Robert's video is
explained by the audit's §2 mechanisms (sparse data + ~16 s delivery vs the
2.75 s loop step + the inverted hold), not an outage. If HRRR goes flaky
later, re-probe first; the S3/GRIB2 route stays unbuilt.

## Fix 6/7 sources verified live at the same time

- **NWS ArcGIS MRMS ImageServer** (`radar_base_reflectivity_time`):
  service info 200, `timeInfo.timeExtent` present (epoch ms), `exportImage`
  200 for the Crittenden bbox (512x512 PNG, 1,230 B — mostly transparent,
  no rain at probe time). Base:
  `https://mapservices.weather.noaa.gov/eventdriven/rest/services/radar/radar_base_reflectivity_time/ImageServer`
- **LibreWXR**: `GET https://api.librewxr.net/public/weather-maps.json` 200 —
  12 past frames + 6 nowcast frames. Tile URL pattern (RainViewer v2):
  `{host}{path}/256/{z}/{x}/{y}/{color}/{options}.png`, e.g.
  `https://api.librewxr.net/v2/radar/1794716400/256/6/16/24/2/1_1.png` → 200.
  NOTE: the tile path is `/v2/radar/{time}` from the maps JSON, NOT
  `/public/radar/{time}` — an earlier probe guess 404'd on the wrong prefix.
  Always use the newest timestamps; old frames prune (404).
