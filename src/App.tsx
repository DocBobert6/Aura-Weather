import React, { useState, useEffect } from 'react';
import {
  MapPin,
  RefreshCw,
  Settings,
  Layers,
  Smartphone,
  Cpu,
  Sun,
  CloudRain,
  CloudLightning,
  Crosshair,
  Search,
} from 'lucide-react';
import {
  StationId,
  STATIONS,
  UserLocation,
  WeatherStation,
  CurrentObservation,
  NowcastFeed,
  HourlyForecastItem,
  DailyForecastItem,
  NwsAlert,
  PollenLevels,
  AirQuality,
  EphemerisData,
  ExpressiveColorTheme,
  WindowingMode,
} from './types/weather';
import {
  fetchLiveWeatherData,
  generateNowcast,
  reverseGeocodeLocation,
} from './services/weatherService';
import { ShaderBackdrop } from './components/ShaderBackdrop';
import { TactileGlassCard } from './components/TactileGlassCard';
import { RadarMap } from './components/RadarMap';
import { NowcastPrecipitation } from './components/NowcastPrecipitation';
import { TenDayForecast } from './components/TenDayForecast';
import { MetricsGrid } from './components/MetricsGrid';
import { PollenCard } from './components/PollenCard';
import { AirQualityCard } from './components/AirQualityCard';
import { SunMoonCard } from './components/SunMoonCard';
import { SevereAlertBanner } from './components/SevereAlertBanner';
import { WidgetsShowcase } from './components/WidgetsShowcase';
import { KotlinCodeStudio } from './components/KotlinCodeStudio';
import { WindowingControls } from './components/WindowingControls';
import { WeatherSettingsModal } from './components/WeatherSettingsModal';

export default function App() {
  // User Location State (Default: Cincinnati / NKY Metro)
  const [userLocation, setUserLocation] = useState<UserLocation>({
    name: 'Cincinnati',
    state: 'Ohio',
    lat: 39.1031,
    lon: -84.512,
    isDeviceLocation: false,
  });

  const [selectedStation, setSelectedStation] = useState<StationId>('KCVG');
  const [activeStationInfo, setActiveStationInfo] = useState<WeatherStation>(STATIONS.KCVG);
  const [windowingMode, setWindowingMode] = useState<WindowingMode>('desktop');
  const [activeTab, setActiveTab] = useState<'forecast' | 'widgets' | 'kotlin'>('forecast');
  const [isPiPActive, setIsPiPActive] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isLocating, setIsLocating] = useState<boolean>(false);
  const [isSimulatedRain, setIsSimulatedRain] = useState<boolean>(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState<boolean>(false);

  // Weather state
  const [observation, setObservation] = useState<CurrentObservation | null>(null);
  const [nowcast, setNowcast] = useState<NowcastFeed | null>(null);
  const [hourly, setHourly] = useState<HourlyForecastItem[]>([]);
  const [daily, setDaily] = useState<DailyForecastItem[]>([]);
  const [alerts, setAlerts] = useState<NwsAlert[]>([]);
  const [pollen, setPollen] = useState<PollenLevels | null>(null);
  const [airQuality, setAirQuality] = useState<AirQuality | null>(null);
  const [ephemeris, setEphemeris] = useState<EphemerisData | null>(null);

  // Material You Monet Palette State
  const [currentTheme, setCurrentTheme] = useState<ExpressiveColorTheme>('rain');

  // Load weather for location
  const loadWeather = async (loc: UserLocation, stationId?: StationId) => {
    setIsLoading(true);
    try {
      const data = await fetchLiveWeatherData(loc, stationId);
      setObservation(data.observation);
      setNowcast(data.nowcast);
      setHourly(data.hourly);
      setDaily(data.daily);
      setAlerts(data.alerts);
      setPollen(data.pollen);
      setAirQuality(data.airQuality);
      setEphemeris(data.ephemeris);
      setActiveStationInfo(data.activeStation);

      if (data.alerts.length > 0 || data.observation.conditionCategory === 'thunderstorm') {
        setCurrentTheme('storm');
      }
    } catch (err) {
      console.error('Failed to load weather:', err);
    } finally {
      setIsLoading(false);
    }
  };

  // Attempt device GPS geolocation on initial load
  const requestDeviceLocation = () => {
    if (!navigator.geolocation) return;
    setIsLocating(true);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const lat = pos.coords.latitude;
        const lon = pos.coords.longitude;
        try {
          const locName = await reverseGeocodeLocation(lat, lon);
          const newLoc: UserLocation = {
            name: locName.split(',')[0] || 'Current Location',
            state: locName.split(',')[1]?.trim() || '',
            lat,
            lon,
            isDeviceLocation: true,
          };
          setUserLocation(newLoc);
          loadWeather(newLoc, selectedStation);
        } catch {
          const fallbackLoc: UserLocation = {
            name: 'Current Location',
            lat,
            lon,
            isDeviceLocation: true,
          };
          setUserLocation(fallbackLoc);
          loadWeather(fallbackLoc, selectedStation);
        } finally {
          setIsLocating(false);
        }
      },
      (err) => {
        console.warn('Geolocation permission not granted, using default Cincinnati location:', err.message);
        setIsLocating(false);
      },
      { timeout: 8000, enableHighAccuracy: true }
    );
  };

  useEffect(() => {
    requestDeviceLocation();
    loadWeather(userLocation, selectedStation);
  }, []);

  const handleSelectLocation = (loc: UserLocation) => {
    setUserLocation(loc);
    loadWeather(loc, selectedStation);
  };

  const handleSelectStation = (stId: StationId) => {
    setSelectedStation(stId);
    setSelectedStation(stId);
    loadWeather(userLocation, stId);
  };

  const handleToggleSimulatedRain = () => {
    const next = !isSimulatedRain;
    setIsSimulatedRain(next);
    if (next) {
      setCurrentTheme('storm');
      setNowcast(generateNowcast(true, true));
      setAlerts([
        {
          id: 'NWS-TEST-SVR-01',
          event: 'Severe Thunderstorm Warning',
          headline: `Severe Thunderstorm Warning: ${userLocation.name} & Metro corridor`,
          severity: 'Severe',
          urgency: 'Immediate',
          affectedCounties: ['Boone, KY', 'Kenton, KY', 'Campbell, KY', 'Hamilton, OH'],
          effective: 'Now',
          expires: 'In 45 minutes',
          description: `A severe thunderstorm capable of producing 60 mph wind gusts and hail was detected approaching ${userLocation.name}.`,
          instruction: 'Take shelter in an interior room on the lowest floor. Avoid windows until the storm front clears.',
        },
      ]);
    } else {
      if (observation) {
        setNowcast(generateNowcast(false));
        setAlerts([]);
        setCurrentTheme('rain');
      }
    }
  };

  const handleDismissAlert = (id: string) => {
    setAlerts((prev) => prev.filter((a) => a.id !== id));
  };

  if (!observation || !nowcast || !pollen || !airQuality || !ephemeris) {
    return (
      <div className="min-h-screen bg-[#0E141B] flex flex-col items-center justify-center text-neutral-300 gap-3 font-sans">
        <RefreshCw className="w-6 h-6 text-[#A8C7FA] animate-spin" />
        <span className="text-sm font-medium">Connecting to local weather data for {userLocation.name}...</span>
      </div>
    );
  }

  return (
    <div className="relative min-h-screen text-[#E2E2E6] font-sans pb-16 selection:bg-[#A8C7FA] selection:text-[#062E6F]">
      {/* Calm Material You ambient backdrop */}
      <ShaderBackdrop
        condition={observation.conditionCategory}
        theme={currentTheme}
        isDaytime={observation.isDaytime}
      />

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 pt-4 flex flex-col gap-4">
        {/* Google Weather / Pixel Header */}
        <header className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 pb-3 border-b border-white/[0.08]">
          <div className="flex items-center justify-between sm:justify-start gap-3">
            <div className="w-10 h-10 rounded-2xl bg-[#282F3B] flex items-center justify-center shadow-sm text-[#FDD663] flex-shrink-0">
              <Sun className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setIsSettingsOpen(true)}
                  className="group flex items-center gap-1.5 text-lg font-bold text-white tracking-tight hover:text-[#A8C7FA] transition-colors"
                >
                  <span>{userLocation.name}</span>
                  {userLocation.state && <span className="text-neutral-400 font-normal">, {userLocation.state}</span>}
                  <Search className="w-3.5 h-3.5 text-neutral-400 group-hover:text-[#A8C7FA]" />
                </button>
                {userLocation.isDeviceLocation && (
                  <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-[#1A73E8]/20 text-[#A8C7FA] border border-[#1A73E8]/30 flex items-center gap-1">
                    <Crosshair className="w-2.5 h-2.5" />
                    GPS
                  </span>
                )}
              </div>
              <p className="text-xs text-neutral-400 flex items-center gap-1">
                <span>Calibrated via ASOS {activeStationInfo.code}</span>
                <span className="text-neutral-500">·</span>
                <button
                  onClick={() => setIsSettingsOpen(true)}
                  className="text-neutral-400 hover:text-white underline text-xs"
                >
                  Settings
                </button>
              </p>
            </div>
          </div>

          {/* Navigation Bar & Settings Trigger */}
          <div className="flex items-center gap-2 self-end sm:self-auto">
            <div className="flex items-center gap-1 bg-[#171D26] p-1.5 rounded-full border border-white/[0.06]">
              <button
                onClick={() => setActiveTab('forecast')}
                className={`px-4 py-1.5 rounded-full text-xs font-medium transition-all flex items-center gap-1.5 ${
                  activeTab === 'forecast'
                    ? 'bg-[#A8C7FA] text-[#062E6F] font-semibold shadow-sm'
                    : 'text-neutral-300 hover:text-white'
                }`}
              >
                <Layers className="w-3.5 h-3.5" />
                <span>Weather & Radar</span>
              </button>
              <button
                onClick={() => setActiveTab('widgets')}
                className={`px-4 py-1.5 rounded-full text-xs font-medium transition-all flex items-center gap-1.5 ${
                  activeTab === 'widgets'
                    ? 'bg-[#A8C7FA] text-[#062E6F] font-semibold shadow-sm'
                    : 'text-neutral-300 hover:text-white'
                }`}
              >
                <Smartphone className="w-3.5 h-3.5" />
                <span>Widgets</span>
              </button>
              <button
                onClick={() => setActiveTab('kotlin')}
                className={`px-4 py-1.5 rounded-full text-xs font-medium transition-all flex items-center gap-1.5 ${
                  activeTab === 'kotlin'
                    ? 'bg-[#A8C7FA] text-[#062E6F] font-semibold shadow-sm'
                    : 'text-neutral-300 hover:text-white'
                }`}
              >
                <Cpu className="w-3.5 h-3.5" />
                <span>Kotlin Code</span>
              </button>
            </div>

            <button
              onClick={() => setIsSettingsOpen(true)}
              title="Weather Settings & Stations"
              className="p-2.5 rounded-full bg-[#171D26] hover:bg-[#283241] border border-white/[0.06] text-neutral-300 hover:text-white transition-colors"
            >
              <Settings className="w-4 h-4" />
            </button>
          </div>
        </header>

        {/* Clean Controls Strip (Monet Palette & Windowing Class) */}
        <WindowingControls
          mode={windowingMode}
          onSelectMode={setWindowingMode}
          currentTheme={currentTheme}
          onSelectTheme={setCurrentTheme}
          onOpenSettings={() => setIsSettingsOpen(true)}
          activeStationCode={activeStationInfo.code}
        />

        {/* NWS Severe Alert Card */}
        <SevereAlertBanner alerts={alerts} onDismiss={handleDismissAlert} />

        {/* Tab 1: Location Weather & Full Doppler Radar */}
        {activeTab === 'forecast' && (
          <div
            className={`transition-all duration-300 ${
              windowingMode === 'phone'
                ? 'max-w-[420px] mx-auto p-4 rounded-[40px] border-4 border-neutral-700/60 bg-[#10141B] shadow-2xl'
                : 'w-full'
            }`}
          >
            {windowingMode === 'phone' && (
              <div className="flex items-center justify-between text-[11px] font-mono text-neutral-400 px-3 pb-3 mb-2 border-b border-white/5">
                <span>{observation.timestamp}</span>
                <div className="w-3 h-3 rounded-full bg-neutral-900 border border-neutral-700" />
                <div className="flex items-center gap-1">
                  <span>5G</span>
                  <span>100%</span>
                </div>
              </div>
            )}

            <div
              className={`grid gap-4 ${
                windowingMode === 'desktop'
                  ? 'grid-cols-1 lg:grid-cols-12'
                  : windowingMode === 'foldable'
                  ? 'grid-cols-1 md:grid-cols-2'
                  : 'grid-cols-1'
              }`}
            >
              {/* Left Column: Location Weather Data Stream */}
              <div
                className={`flex flex-col gap-4 ${
                  windowingMode === 'desktop' ? 'lg:col-span-7' : ''
                }`}
              >
                {/* 1. Main User Location Weather Card */}
                <TactileGlassCard theme={currentTheme} className="p-6">
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center gap-2">
                        <MapPin className="w-3.5 h-3.5 text-[#A8C7FA]" />
                        <span className="text-xs text-neutral-400 font-medium">
                          {userLocation.name} {userLocation.state ? `· ${userLocation.state}` : ''}
                        </span>
                      </div>
                      <h2 className="text-2xl font-bold text-white mt-1">
                        {userLocation.name}
                      </h2>
                    </div>

                    <div className="flex items-center gap-1.5">
                      <button
                        onClick={requestDeviceLocation}
                        disabled={isLocating}
                        title="Detect current device location"
                        className="p-2 rounded-full bg-[#282F3B] hover:bg-[#343D4C] text-neutral-300 transition-colors"
                      >
                        <Crosshair className={`w-4 h-4 ${isLocating ? 'animate-spin text-[#A8C7FA]' : ''}`} />
                      </button>
                      <button
                        onClick={() => loadWeather(userLocation, selectedStation)}
                        disabled={isLoading}
                        title="Refresh weather"
                        className="p-2 rounded-full bg-[#282F3B] hover:bg-[#343D4C] text-neutral-300 transition-colors"
                      >
                        <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin text-[#A8C7FA]' : ''}`} />
                      </button>
                    </div>
                  </div>

                  {/* Temperature & Weather Condition */}
                  <div className="my-6 flex items-center justify-between">
                    <div>
                      <div className="flex items-baseline">
                        <span className="text-7xl sm:text-8xl font-normal tracking-tight text-white font-sans">
                          {observation.temperatureF}
                        </span>
                        <span className="text-5xl font-light text-neutral-300">°</span>
                      </div>
                      <div className="text-lg font-medium text-white mt-1">
                        {observation.conditionDescription}
                      </div>
                      <div className="text-xs text-neutral-400 mt-0.5">
                        Feels like {observation.feelsLikeF}° · Dew point {observation.dewPointF}°
                      </div>
                    </div>

                    {/* Clean Google Weather Graphic */}
                    <div className="flex flex-col items-center">
                      {observation.conditionCategory === 'rain' ? (
                        <div className="w-20 h-20 rounded-full bg-[#A8C7FA]/15 flex items-center justify-center">
                          <CloudRain className="w-12 h-12 text-[#A8C7FA]" />
                        </div>
                      ) : observation.conditionCategory === 'thunderstorm' ? (
                        <div className="w-20 h-20 rounded-full bg-[#F28B82]/15 flex items-center justify-center">
                          <CloudLightning className="w-12 h-12 text-[#F28B82]" />
                        </div>
                      ) : (
                        <div className="w-20 h-20 rounded-full bg-[#FDD663]/15 flex items-center justify-center">
                          <Sun className="w-12 h-12 text-[#FDD663]" />
                        </div>
                      )}
                      {daily[0] && (
                        <span className="text-xs font-medium text-neutral-300 mt-2">
                          H: {daily[0].highF}° · L: {daily[0].lowF}°
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Location & Station Status Notice */}
                  <div className="p-3 rounded-2xl bg-[#13171F] text-xs text-neutral-400 flex items-center justify-between">
                    <span>
                      ASOS calibrated: {activeStationInfo.name} ({activeStationInfo.elevationFt} ft MSL)
                    </span>
                    <span className="text-[11px] font-mono text-neutral-400 flex-shrink-0 ml-2">
                      {observation.timestamp}
                    </span>
                  </div>
                </TactileGlassCard>

                {/* 2. Precipitation in Next Hour (Nowcast) */}
                <NowcastPrecipitation
                  nowcast={nowcast}
                  hourly={hourly}
                  theme={currentTheme}
                  onSimulateRainToggle={handleToggleSimulatedRain}
                  isSimulatedRain={isSimulatedRain}
                />

                {/* 3. Pixel Weather 2x2 Metric Cards */}
                <MetricsGrid observation={observation} theme={currentTheme} />

                {/* 4. Air Quality & Pollen Count */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
                  <AirQualityCard airQuality={airQuality} theme={currentTheme} />
                  <PollenCard pollen={pollen} theme={currentTheme} />
                </div>

                {/* 5. Sunrise & Sunset Solar Arc */}
                <SunMoonCard ephemeris={ephemeris} theme={currentTheme} />
              </div>

              {/* Right Column: Interactive Doppler Radar & 10-Day Forecast */}
              <div
                className={`flex flex-col gap-4 ${
                  windowingMode === 'desktop' ? 'lg:col-span-5' : ''
                }`}
              >
                <div className="sticky top-4 flex flex-col gap-4">
                  <div className="flex items-center justify-between px-1">
                    <div className="flex items-center gap-2">
                      <h3 className="text-sm font-semibold text-white">
                        Doppler Radar
                      </h3>
                      <span className="text-xs text-neutral-400">· Centered on {userLocation.name}</span>
                    </div>
                    <span className="text-xs text-[#A8C7FA] font-medium">
                      KILN Sweep
                    </span>
                  </div>

                  {/* Fixed Radar Map with swipeable mode bar & Google satellite */}
                  <RadarMap
                    userLocation={userLocation}
                    selectedStation={selectedStation}
                    onSelectStation={handleSelectStation}
                    isPiP={isPiPActive}
                    onTogglePiP={() => setIsPiPActive(!isPiPActive)}
                  />

                  {/* 10-Day Forecast with Google Horizontal Temperature Range Bars */}
                  <TenDayForecast daily={daily} theme={currentTheme} />
                </div>
              </div>
            </div>

            {windowingMode === 'phone' && (
              <div className="mt-6 flex justify-center">
                <div className="w-32 h-1 rounded-full bg-neutral-600" />
              </div>
            )}
          </div>
        )}

        {/* Tab 2: Pixel Material You Widgets Studio */}
        {activeTab === 'widgets' && (
          <WidgetsShowcase
            observation={observation}
            nowcast={nowcast}
            alerts={alerts}
            theme={currentTheme}
          />
        )}

        {/* Tab 3: Kotlin Architecture Studio */}
        {activeTab === 'kotlin' && <KotlinCodeStudio />}

        {/* Floating PiP Mini Radar */}
        {isPiPActive && activeTab !== 'forecast' && (
          <div className="fixed bottom-6 right-6 z-50 shadow-2xl rounded-[28px] overflow-hidden border-2 border-[#A8C7FA]">
            <RadarMap
              userLocation={userLocation}
              selectedStation={selectedStation}
              onSelectStation={handleSelectStation}
              isPiP={true}
              onTogglePiP={() => setIsPiPActive(false)}
            />
          </div>
        )}

        {/* Settings & Location Modal */}
        <WeatherSettingsModal
          isOpen={isSettingsOpen}
          onClose={() => setIsSettingsOpen(false)}
          userLocation={userLocation}
          onSelectLocation={(loc) => {
            handleSelectLocation(loc);
            setIsSettingsOpen(false);
          }}
          selectedStation={selectedStation}
          onSelectStation={handleSelectStation}
          onUseDeviceLocation={() => {
            requestDeviceLocation();
            setIsSettingsOpen(false);
          }}
          isLocating={isLocating}
        />
      </div>
    </div>
  );
}
