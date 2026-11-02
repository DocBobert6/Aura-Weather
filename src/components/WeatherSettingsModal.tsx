import React, { useState } from 'react';
import {
  X,
  Settings,
  Radio,
  MapPin,
  Compass,
  Check,
  Search,
  Crosshair,
  Gauge,
  Thermometer,
  ShieldCheck,
} from 'lucide-react';
import { StationId, STATIONS, UserLocation } from '../types/weather';
import { searchLocations } from '../services/weatherService';

interface WeatherSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  userLocation: UserLocation;
  onSelectLocation: (loc: UserLocation) => void;
  selectedStation: StationId;
  onSelectStation: (id: StationId) => void;
  onUseDeviceLocation: () => void;
  isLocating: boolean;
}

export const WeatherSettingsModal: React.FC<WeatherSettingsModalProps> = ({
  isOpen,
  onClose,
  userLocation,
  onSelectLocation,
  selectedStation,
  onSelectStation,
  onUseDeviceLocation,
  isLocating,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<UserLocation[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  if (!isOpen) return null;

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    setIsSearching(true);
    const results = await searchLocations(searchQuery);
    setSearchResults(results);
    setIsSearching(false);
  };

  const popularTriStateLocations: UserLocation[] = [
    { name: 'Cincinnati', state: 'Ohio', lat: 39.1031, lon: -84.512, isDeviceLocation: false },
    { name: 'Covington', state: 'Kentucky', lat: 39.0837, lon: -84.5086, isDeviceLocation: false },
    { name: 'Florence', state: 'Kentucky', lat: 39.0067, lon: -84.6272, isDeviceLocation: false },
    { name: 'Newport', state: 'Kentucky', lat: 39.0917, lon: -84.4958, isDeviceLocation: false },
    { name: 'Mason', state: 'Ohio', lat: 39.3601, lon: -84.3099, isDeviceLocation: false },
    { name: 'Hebron (CVG)', state: 'Kentucky', lat: 39.0667, lon: -84.6972, isDeviceLocation: false },
    { name: 'Blue Ash', state: 'Ohio', lat: 39.232, lon: -84.3783, isDeviceLocation: false },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg bg-[#191F28] border border-white/[0.08] rounded-[32px] p-6 shadow-2xl overflow-hidden max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/[0.08]">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-full bg-[#282F3B] flex items-center justify-center text-[#A8C7FA]">
              <Settings className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Weather Settings & Data Sources</h2>
              <p className="text-xs text-neutral-400">Manage location and ASOS radar calibration anchors</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full hover:bg-white/10 text-neutral-300 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Content */}
        <div className="flex-1 overflow-y-auto space-y-5 py-4 scrollbar-thin">
          {/* Section 1: Location Settings */}
          <div>
            <h3 className="text-xs font-semibold uppercase tracking-wider text-neutral-400 mb-2.5 flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5 text-[#A8C7FA]" />
              Active Location
            </h3>

            {/* Current Selected Location Indicator */}
            <div className="p-3.5 rounded-2xl bg-[#202733] border border-white/5 flex items-center justify-between mb-3">
              <div>
                <span className="text-sm font-semibold text-white block">
                  {userLocation.name} {userLocation.state ? `, ${userLocation.state}` : ''}
                </span>
                <span className="text-xs text-neutral-400">
                  {userLocation.isDeviceLocation ? 'Using Device GPS coordinates' : 'Custom Selected Area'} ({userLocation.lat.toFixed(2)}°, {userLocation.lon.toFixed(2)}°)
                </span>
              </div>

              <button
                onClick={onUseDeviceLocation}
                disabled={isLocating}
                className="flex items-center gap-1 px-3 py-1.5 rounded-full bg-[#A8C7FA] text-[#062E6F] text-xs font-semibold shadow-sm hover:bg-[#8AB4F8] transition-colors"
              >
                <Crosshair className={`w-3.5 h-3.5 ${isLocating ? 'animate-spin' : ''}`} />
                <span>{isLocating ? 'Locating...' : 'Use GPS'}</span>
              </button>
            </div>

            {/* Search Location Input */}
            <form onSubmit={handleSearch} className="flex gap-2 mb-3">
              <div className="relative flex-1">
                <Search className="w-4 h-4 text-neutral-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search city, zip, or neighborhood..."
                  className="w-full pl-9 pr-3 py-2 rounded-xl bg-[#202733] border border-white/10 text-xs text-white placeholder-neutral-500 focus:outline-none focus:border-[#A8C7FA]"
                />
              </div>
              <button
                type="submit"
                className="px-3.5 py-2 rounded-xl bg-[#282F3B] hover:bg-[#343D4C] text-xs font-medium text-white transition-colors"
              >
                Search
              </button>
            </form>

            {/* Search Results */}
            {searchResults.length > 0 && (
              <div className="space-y-1 mb-3 p-2 rounded-xl bg-[#202733]">
                {searchResults.map((res, i) => (
                  <button
                    key={i}
                    onClick={() => {
                      onSelectLocation(res);
                      setSearchResults([]);
                      setSearchQuery('');
                    }}
                    className="w-full text-left p-2 rounded-lg hover:bg-white/5 text-xs text-white flex items-center justify-between"
                  >
                    <span>{res.name}, {res.state} ({res.country})</span>
                    <Check className="w-3.5 h-3.5 text-[#A8C7FA]" />
                  </button>
                ))}
              </div>
            )}

            {/* Quick Presets */}
            <div className="flex flex-wrap gap-1.5">
              {popularTriStateLocations.map((loc, idx) => (
                <button
                  key={idx}
                  onClick={() => onSelectLocation(loc)}
                  className={`px-2.5 py-1 rounded-full text-xs transition-colors ${
                    userLocation.name === loc.name
                      ? 'bg-[#A8C7FA] text-[#062E6F] font-semibold'
                      : 'bg-[#202733] text-neutral-300 hover:text-white'
                  }`}
                >
                  {loc.name}
                </button>
              ))}
            </div>
          </div>

          {/* Section 2: Regional ASOS & Radar Anchor Stations */}
          <div className="pt-4 border-t border-white/[0.08]">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-neutral-400 flex items-center gap-1.5">
                <Radio className="w-3.5 h-3.5 text-[#A8C7FA]" />
                Regional Weather Data Anchors
              </h3>
              <span className="text-[10px] text-neutral-400">Used for ASOS Telemetry & Radar sweeps</span>
            </div>

            <p className="text-xs text-neutral-400 mb-3">
              Official FAA/NOAA Automated Surface Observing System (ASOS) stations calibrated for the Ohio River Valley. Weather feeds automatically align to the station nearest your location.
            </p>

            <div className="space-y-2">
              {(Object.keys(STATIONS) as StationId[]).map((stId) => {
                const st = STATIONS[stId];
                const isSelected = selectedStation === stId;

                return (
                  <div
                    key={stId}
                    onClick={() => onSelectStation(stId)}
                    className={`p-3 rounded-2xl border cursor-pointer transition-all flex items-start justify-between ${
                      isSelected
                        ? 'bg-[#202733] border-[#A8C7FA]/50 shadow-sm'
                        : 'bg-[#191F28] border-white/5 hover:bg-[#202733]/60'
                    }`}
                  >
                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-white">{st.code}</span>
                        <span className="text-xs text-neutral-300 font-medium">· {st.name}</span>
                      </div>
                      <p className="text-[11px] text-neutral-400">{st.subhead} ({st.elevationFt} ft MSL)</p>
                      <p className="text-[10px] text-neutral-500 italic mt-0.5">{st.microclimateNote}</p>
                    </div>

                    <div className="p-1">
                      {isSelected ? (
                        <div className="w-5 h-5 rounded-full bg-[#A8C7FA] text-[#062E6F] flex items-center justify-center">
                          <Check className="w-3 h-3 stroke-[3]" />
                        </div>
                      ) : (
                        <div className="w-5 h-5 rounded-full border border-neutral-600" />
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="pt-3 border-t border-white/[0.08] flex items-center justify-between text-xs text-neutral-400">
          <span>NOAA ASOS & KILN NEXRAD Radar Connected</span>
          <button
            onClick={onClose}
            className="px-4 py-1.5 rounded-full bg-[#A8C7FA] text-[#062E6F] font-semibold hover:bg-[#8AB4F8] transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
