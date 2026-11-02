import React, { useEffect, useRef, useState, useCallback } from 'react';
import L from 'leaflet';
import {
  Play,
  Pause,
  Layers,
  MapPin,
  Minimize2,
  Maximize2,
  Radio,
  Clock,
  CloudRain,
  Eye,
  SkipBack,
  SkipForward,
  Navigation,
} from 'lucide-react';
import { StationId, STATIONS, UserLocation } from '../types/weather';

interface RadarMapProps {
  userLocation: UserLocation;
  selectedStation: StationId;
  onSelectStation: (id: StationId) => void;
  isPiP?: boolean;
  onTogglePiP?: () => void;
  className?: string;
}

export type RadarMode = 'live' | 'loop2h' | 'loop24h' | 'satellite';
export type BaseMapType = 'dark' | 'street' | 'satellite';

interface RadarFrame {
  id: string;
  label: string;
  timeFormatted: string;
  tileUrl: string;
}

export const RadarMap: React.FC<RadarMapProps> = ({
  userLocation,
  selectedStation,
  onSelectStation,
  isPiP = false,
  onTogglePiP,
  className = '',
}) => {
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const baseTileLayerRef = useRef<L.TileLayer | null>(null);
  const activeTileLayersRef = useRef<Record<string, L.TileLayer>>({});
  const currentActiveUrlRef = useRef<string>('');
  const userMarkerRef = useRef<L.Marker | null>(null);

  const [radarMode, setRadarMode] = useState<RadarMode>('live');
  const [baseMap, setBaseMap] = useState<BaseMapType>('dark');
  const [frames, setFrames] = useState<RadarFrame[]>([]);
  const [currentFrameIndex, setCurrentFrameIndex] = useState<number>(0);
  const [isPlaying, setIsPlaying] = useState<boolean>(true);

  // 1. Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    // Center on user location
    const map = L.map(mapContainerRef.current, {
      center: [userLocation.lat, userLocation.lon],
      zoom: isPiP ? 8 : 10,
      minZoom: 5,
      maxZoom: 18,
      zoomControl: !isPiP,
      attributionControl: false,
    });

    // Clean OpenStreetMap base layer (no Esri tiles, no zoom limits, no watermarks)
    const baseLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      className: 'osm-dark-base',
    }).addTo(map);
    baseTileLayerRef.current = baseLayer;

    // Add User Location pulsing blue dot
    const userMarkerHtml = `
      <div class="relative flex items-center justify-center">
        <div class="absolute w-6 h-6 rounded-full bg-[#A8C7FA]/40 animate-ping"></div>
        <div class="w-4 h-4 rounded-full bg-[#1A73E8] border-2 border-white shadow-lg"></div>
      </div>
    `;
    const userIcon = L.divIcon({
      html: userMarkerHtml,
      className: 'user-loc-pin',
      iconSize: [20, 20],
      iconAnchor: [10, 10],
    });
    userMarkerRef.current = L.marker([userLocation.lat, userLocation.lon], {
      icon: userIcon,
      zIndexOffset: 1000,
    }).addTo(map);

    // Add Regional Radar & ASOS Anchor Markers
    (Object.keys(STATIONS) as StationId[]).forEach((stId) => {
      const st = STATIONS[stId];
      const isRadar = stId === 'KILN';

      const markerHtml = `
        <div class="flex items-center gap-1 px-2 py-0.5 rounded-full shadow text-[10px] font-medium bg-[#1E232B]/90 text-neutral-300 border border-white/10 cursor-pointer hover:scale-105 transition-transform">
          ${isRadar ? '<span class="w-1.5 h-1.5 rounded-full bg-amber-400"></span>' : ''}
          <span>${st.code}</span>
        </div>
      `;
      const icon = L.divIcon({
        html: markerHtml,
        className: 'station-pin',
        iconSize: [50, 24],
        iconAnchor: [25, 12],
      });
      const marker = L.marker([st.lat, st.lon], { icon }).addTo(map);
      marker.on('click', () => onSelectStation(stId));
    });

    mapInstanceRef.current = map;

    return () => {
      map.remove();
      mapInstanceRef.current = null;
    };
  }, []);

  // Update user marker and center map when userLocation changes
  useEffect(() => {
    if (!mapInstanceRef.current) return;
    if (userMarkerRef.current) {
      userMarkerRef.current.setLatLng([userLocation.lat, userLocation.lon]);
    }
    mapInstanceRef.current.flyTo([userLocation.lat, userLocation.lon], isPiP ? 8 : 10, {
      duration: 1.0,
      easeLinearity: 0.25,
    });
  }, [userLocation, isPiP]);

  // 2. Base Map Switcher: Dark OSM, Standard OSM, Google Satellite (Zero Esri tiles)
  useEffect(() => {
    if (!mapInstanceRef.current) return;
    if (baseTileLayerRef.current) {
      mapInstanceRef.current.removeLayer(baseTileLayerRef.current);
    }

    let url = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
    let className = 'osm-dark-base';

    if (baseMap === 'street') {
      className = '';
    } else if (baseMap === 'satellite') {
      // Google Hybrid Satellite tiles (works at all zoom levels, 0 watermarks)
      url = 'https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}';
      className = '';
    }

    const layer = L.tileLayer(url, { maxZoom: 19, className }).addTo(mapInstanceRef.current);
    baseTileLayerRef.current = layer;
    layer.bringToBack();
  }, [baseMap]);

  // 3. Build radar frames based on mode
  useEffect(() => {
    let active = true;

    const buildFrames = async () => {
      if (radarMode === 'live') {
        const liveFrames: RadarFrame[] = [
          {
            id: 'kiln-live-sweep',
            label: 'Live Sweep',
            timeFormatted: 'Live KILN Sweep',
            tileUrl: 'https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/ridge::KILN-N0Q-0/{z}/{x}/{y}.png',
          },
        ];
        if (active) {
          setFrames(liveFrames);
          setCurrentFrameIndex(0);
        }
      } else if (radarMode === 'satellite') {
        const satFrames: RadarFrame[] = [
          {
            id: 'goes-east-satellite',
            label: 'GOES-East IR',
            timeFormatted: 'Live Infrared Cloud Tops',
            tileUrl: 'https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/goes-ir-4km-900913/{z}/{x}/{y}.png',
          },
        ];
        if (active) {
          setFrames(satFrames);
          setCurrentFrameIndex(0);
        }
      } else if (radarMode === 'loop2h') {
        try {
          const res = await fetch('https://api.rainviewer.com/public/weather-maps.json');
          if (!res.ok) throw new Error('RainViewer offline');
          const data = await res.json();
          if (!active) return;

          const host = data.host || 'https://tilecache.rainviewer.com';
          const pastFrames = data.radar?.past || [];

          if (pastFrames.length > 0) {
            const list: RadarFrame[] = pastFrames.map((f: any, idx: number) => {
              const d = new Date(f.time * 1000);
              const isLatest = idx === pastFrames.length - 1;
              return {
                id: `rv-${f.time}`,
                label: isLatest ? 'Latest' : `-${(pastFrames.length - 1 - idx) * 10}m`,
                timeFormatted: d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }),
                tileUrl: `${host}${f.path}/256/{z}/{x}/{y}/2/1_1.png`,
              };
            });
            setFrames(list);
            setCurrentFrameIndex(list.length - 1);
          }
        } catch (e) {
          // Fallback to IEM 2h steps
          const steps = ['m60m', 'm50m', 'm40m', 'm30m', 'm20m', 'm10m', 'm05m'];
          const fallbackList: RadarFrame[] = steps.map((s, idx) => ({
            id: `iem-step-${s}`,
            label: idx === steps.length - 1 ? 'Live' : `-${(steps.length - 1 - idx) * 10}m`,
            timeFormatted: idx === steps.length - 1 ? 'Live' : `-${(steps.length - 1 - idx) * 10} min`,
            tileUrl: `https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-${s}-900913/{z}/{x}/{y}.png`,
          }));
          setFrames(fallbackList);
          setCurrentFrameIndex(fallbackList.length - 1);
        }
      } else if (radarMode === 'loop24h') {
        const hourly24: RadarFrame[] = [];
        const now = new Date();

        for (let h = 23; h >= 0; h--) {
          const frameDate = new Date(now.getTime() - h * 60 * 60 * 1000);
          const y = frameDate.getUTCFullYear();
          const m = String(frameDate.getUTCMonth() + 1).padStart(2, '0');
          const d = String(frameDate.getUTCDate()).padStart(2, '0');
          const hr = String(frameDate.getUTCHours()).padStart(2, '0');
          const timeCode = `${y}${m}${d}${hr}00`;

          const localTime = frameDate.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
          const label = h === 0 ? 'Now' : `-${h}h`;

          hourly24.push({
            id: `iem-24h-${timeCode}`,
            label,
            timeFormatted: `${label} (${localTime})`,
            tileUrl: h === 0
              ? 'https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/ridge::USCOMP-N0Q-0/{z}/{x}/{y}.png'
              : `https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/ridge::USCOMP-N0Q-${timeCode}/{z}/{x}/{y}.png`,
          });
        }

        if (active) {
          setFrames(hourly24);
          setCurrentFrameIndex(hourly24.length - 1);
        }
      }
    };

    buildFrames();

    return () => {
      active = false;
    };
  }, [radarMode]);

  // 4. Smooth Layer Transition Engine
  const showFrameTile = useCallback((targetUrl: string) => {
    if (!mapInstanceRef.current || !targetUrl) return;
    const map = mapInstanceRef.current;

    let targetLayer = activeTileLayersRef.current[targetUrl];
    if (!targetLayer) {
      targetLayer = L.tileLayer(targetUrl, {
        opacity: 0,
        className: 'radar-overlay-tile',
        zIndex: 50,
      }).addTo(map);
      activeTileLayersRef.current[targetUrl] = targetLayer;
    }

    targetLayer.setOpacity(0.85);
    targetLayer.bringToFront();

    const prevUrl = currentActiveUrlRef.current;
    if (prevUrl && prevUrl !== targetUrl) {
      const prevLayer = activeTileLayersRef.current[prevUrl];
      if (prevLayer) {
        prevLayer.setOpacity(0);
      }
    }

    currentActiveUrlRef.current = targetUrl;

    const keys = Object.keys(activeTileLayersRef.current);
    if (keys.length > 25) {
      keys.slice(0, keys.length - 20).forEach((oldKey) => {
        if (oldKey !== targetUrl) {
          const l = activeTileLayersRef.current[oldKey];
          if (l) map.removeLayer(l);
          delete activeTileLayersRef.current[oldKey];
        }
      });
    }
  }, []);

  useEffect(() => {
    if (frames.length === 0) return;
    const frame = frames[currentFrameIndex];
    if (frame) {
      showFrameTile(frame.tileUrl);
    }
  }, [frames, currentFrameIndex, showFrameTile]);

  // 5. Animation Timer
  useEffect(() => {
    if (radarMode === 'live' || radarMode === 'satellite' || !isPlaying || frames.length <= 1) return;

    const timer = setInterval(() => {
      setCurrentFrameIndex((prev) => (prev + 1) % frames.length);
    }, 750);

    return () => clearInterval(timer);
  }, [radarMode, isPlaying, frames.length]);

  const currentFrame = frames[currentFrameIndex];

  return (
    <div className="flex flex-col gap-2 w-full">
      {/* Dedicated Material 3 Radar Mode Selector Bar (Full width, never clipped, swipeable) */}
      <div className="flex items-center justify-between gap-2 overflow-x-auto pb-1 scrollbar-none">
        <div className="flex items-center gap-1.5 p-1 rounded-full bg-[#1C212A] border border-white/[0.08] flex-shrink-0">
          <button
            onClick={() => {
              setRadarMode('live');
              setIsPlaying(false);
            }}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-all whitespace-nowrap min-h-[36px] ${
              radarMode === 'live'
                ? 'bg-[#A8C7FA] text-[#062E6F] shadow-sm'
                : 'text-neutral-300 hover:text-white'
            }`}
          >
            Live Doppler
          </button>
          <button
            onClick={() => {
              setRadarMode('loop2h');
              setIsPlaying(true);
            }}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-all whitespace-nowrap min-h-[36px] ${
              radarMode === 'loop2h'
                ? 'bg-[#A8C7FA] text-[#062E6F] shadow-sm'
                : 'text-neutral-300 hover:text-white'
            }`}
          >
            2h Loop
          </button>
          <button
            onClick={() => {
              setRadarMode('loop24h');
              setIsPlaying(true);
            }}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-all whitespace-nowrap min-h-[36px] ${
              radarMode === 'loop24h'
                ? 'bg-[#A8C7FA] text-[#062E6F] shadow-sm'
                : 'text-neutral-300 hover:text-white'
            }`}
          >
            24h Radar
          </button>
          <button
            onClick={() => {
              setRadarMode('satellite');
              setIsPlaying(false);
            }}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-all whitespace-nowrap min-h-[36px] ${
              radarMode === 'satellite'
                ? 'bg-[#A8C7FA] text-[#062E6F] shadow-sm'
                : 'text-neutral-300 hover:text-white'
            }`}
          >
            Satellite (IR)
          </button>
        </div>

        {/* Map Type Switcher (Dark / Street / Google Satellite) */}
        <div className="flex items-center gap-1 p-1 rounded-full bg-[#1C212A] border border-white/[0.08] flex-shrink-0">
          <button
            onClick={() =>
              setBaseMap(baseMap === 'dark' ? 'street' : baseMap === 'street' ? 'satellite' : 'dark')
            }
            title={`Basemap: ${baseMap.toUpperCase()}`}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium text-neutral-300 hover:text-white"
          >
            <Layers className="w-3.5 h-3.5 text-[#A8C7FA]" />
            <span className="capitalize">{baseMap}</span>
          </button>

          {onTogglePiP && (
            <button
              onClick={onTogglePiP}
              title={isPiP ? 'Expand' : 'PiP Mode'}
              className="p-2 rounded-full text-neutral-300 hover:text-white"
            >
              {isPiP ? <Maximize2 className="w-3.5 h-3.5" /> : <Minimize2 className="w-3.5 h-3.5" />}
            </button>
          )}
        </div>
      </div>

      {/* Main Map Canvas Card */}
      <div
        className={`relative flex flex-col rounded-[28px] overflow-hidden border border-neutral-800 bg-[#14181F] shadow-lg transition-all ${className} ${
          isPiP ? 'h-64 w-80' : 'h-[440px] w-full'
        }`}
      >
        {/* Top Status Pill */}
        <div className="absolute top-3 inset-x-3 z-[1000] flex items-center justify-between pointer-events-none">
          <div className="pointer-events-auto flex items-center gap-2 bg-[#1C212A]/90 backdrop-blur-md px-3.5 py-1.5 rounded-full border border-white/10 shadow-md">
            <Radio className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
            <span className="text-xs font-medium text-white">
              {radarMode === 'satellite' ? 'GOES-East Satellite' : 'NOAA NEXRAD Radar Grid'}
            </span>
            <span className="text-neutral-500 text-xs">·</span>
            <span className="text-[11px] font-mono text-[#A8C7FA]">
              {currentFrame ? currentFrame.timeFormatted : 'Live'}
            </span>
          </div>

          {/* Quick Recentering Button to User Location */}
          <button
            onClick={() => {
              if (mapInstanceRef.current) {
                mapInstanceRef.current.flyTo([userLocation.lat, userLocation.lon], 11, { duration: 1.0 });
              }
            }}
            className="pointer-events-auto flex items-center gap-1.5 bg-[#1C212A]/90 backdrop-blur-md px-3 py-1.5 rounded-full border border-white/10 text-xs font-medium text-white shadow-md hover:bg-[#2A323D] transition-colors"
          >
            <Navigation className="w-3.5 h-3.5 text-[#A8C7FA]" />
            <span className="hidden sm:inline">My Location</span>
          </button>
        </div>

        {/* Leaflet DOM Node */}
        <div ref={mapContainerRef} className="flex-1 w-full h-full z-10" />

        {/* Bottom Playback & Scrubber Controls */}
        {!isPiP && (
          <div className="absolute bottom-3 inset-x-3 z-[1000] flex flex-col gap-2 bg-[#1C212A]/95 backdrop-blur-md p-3 rounded-[22px] border border-white/10 shadow-xl">
            {(radarMode === 'loop2h' || radarMode === 'loop24h') && (
              <div className="flex items-center gap-2 px-1">
                <button
                  onClick={() => setIsPlaying(!isPlaying)}
                  className="w-8 h-8 rounded-full bg-[#A8C7FA] hover:bg-[#8AB4F8] text-[#062E6F] flex items-center justify-center transition-colors shadow-sm flex-shrink-0"
                  aria-label={isPlaying ? 'Pause' : 'Play'}
                >
                  {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4 ml-0.5" />}
                </button>

                <button
                  onClick={() => {
                    setIsPlaying(false);
                    setCurrentFrameIndex((prev) => (prev > 0 ? prev - 1 : frames.length - 1));
                  }}
                  className="p-1 text-neutral-400 hover:text-white transition-colors"
                  title="Previous frame"
                >
                  <SkipBack className="w-3.5 h-3.5" />
                </button>

                <input
                  type="range"
                  min={0}
                  max={Math.max(0, frames.length - 1)}
                  value={currentFrameIndex}
                  onChange={(e) => {
                    setCurrentFrameIndex(parseInt(e.target.value, 10));
                    setIsPlaying(false);
                  }}
                  className="flex-1 h-1.5 bg-neutral-700 rounded-lg appearance-none cursor-pointer accent-[#A8C7FA]"
                />

                <button
                  onClick={() => {
                    setIsPlaying(false);
                    setCurrentFrameIndex((prev) => (prev + 1) % frames.length);
                  }}
                  className="p-1 text-neutral-400 hover:text-white transition-colors"
                  title="Next frame"
                >
                  <SkipForward className="w-3.5 h-3.5" />
                </button>

                <span className="text-[11px] font-mono text-[#A8C7FA] min-w-[75px] text-right font-medium">
                  {currentFrame ? currentFrame.timeFormatted : ''}
                </span>
              </div>
            )}

            {/* Legend Scale */}
            <div className="flex items-center justify-between pt-1 border-t border-white/5 text-[10px] text-neutral-400">
              <span className="flex items-center gap-1">
                <span className="w-2 h-2 rounded-full bg-[#4682B4]" /> Light
              </span>
              <span className="flex items-center gap-1">
                <span className="w-2 h-2 rounded-full bg-[#32CD32]" /> Moderate
              </span>
              <span className="flex items-center gap-1">
                <span className="w-2 h-2 rounded-full bg-[#FFD700]" /> Heavy
              </span>
              <span className="flex items-center gap-1">
                <span className="w-2 h-2 rounded-full bg-[#FF4500]" /> Storm
              </span>
              <span className="flex items-center gap-1">
                <span className="w-2 h-2 rounded-full bg-[#9932CC]" /> Hail
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
