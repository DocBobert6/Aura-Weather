/**
 * Domain entity types for Northern Kentucky & Greater Cincinnati Weather App
 * Target stations: KCVG, KLUK, KILN (Radar Grid)
 */

export type StationId = 'KCVG' | 'KLUK' | 'COVINGTON' | 'KILN';

export interface WeatherStation {
  id: StationId;
  name: string;
  code: string;
  subhead: string;
  lat: number;
  lon: number;
  elevationFt: number;
  county: string;
  microclimateNote: string;
}

export const STATIONS: Record<StationId, WeatherStation> = {
  KCVG: {
    id: 'KCVG',
    name: 'Cincinnati / N. Kentucky Int’l',
    code: 'KCVG',
    subhead: 'Hebron, Boone County, KY',
    lat: 39.0489,
    lon: -84.6678,
    elevationFt: 890,
    county: 'Boone County, KY',
    microclimateNote: 'Plateau elevation (~890ft) experiences elevated wind gusts and earlier snow transitions than the river valley.',
  },
  KLUK: {
    id: 'KLUK',
    name: 'Lunken Municipal Airport',
    code: 'KLUK',
    subhead: 'East End, Hamilton County, OH',
    lat: 39.1033,
    lon: -84.4186,
    elevationFt: 483,
    county: 'Hamilton County, OH',
    microclimateNote: 'Ohio & Little Miami River basin convergence creates frequent overnight radiation fog and thermal inversions.',
  },
  COVINGTON: {
    id: 'COVINGTON',
    name: 'Covington Riverfront',
    code: 'COVINGTON',
    subhead: 'Kenton / Campbell County, KY',
    lat: 39.0837,
    lon: -84.5086,
    elevationFt: 510,
    county: 'Kenton County, KY',
    microclimateNote: 'Dense urban riverfront with Roebling suspension bridge channel winds and Ohio River humidity buffer.',
  },
  KILN: {
    id: 'KILN',
    name: 'Wilmington NEXRAD Station',
    code: 'KILN',
    subhead: 'Wilmington, Clinton County, OH',
    lat: 39.4200,
    lon: -83.8200,
    elevationFt: 1040,
    county: 'Clinton County, OH',
    microclimateNote: 'Primary WSR-88D Doppler radar installation sweeping the entire Tri-State & Ohio Valley corridor.',
  },
};

export interface UserLocation {
  name: string;
  state?: string;
  country?: string;
  lat: number;
  lon: number;
  isDeviceLocation: boolean;
}

export type WeatherConditionCategory =
  | 'thunderstorm'
  | 'rain'
  | 'snow'
  | 'fog'
  | 'clear'
  | 'cloudy'
  | 'wind';

export interface CurrentObservation {
  stationId: StationId;
  stationName: string;
  timestamp: string;
  temperatureF: number;
  feelsLikeF: number;
  dewPointF: number;
  humidityPercent: number;
  windSpeedMph: number;
  windGustMph: number;
  windDirectionDeg: number;
  windDirectionCardinal: string;
  barometricPressureInHg: number;
  pressureTrend: 'rising' | 'steady' | 'falling';
  visibilityMiles: number;
  uvIndex: number;
  cloudCeilingFt: number | null;
  conditionDescription: string;
  conditionCategory: WeatherConditionCategory;
  isDaytime: boolean;
}

export interface MinutePrecipitation {
  minute: number; // 0 to 59
  intensityPercent: number; // 0 to 100
  rateInchesPerHour: number;
  probabilityPercent: number;
}

export interface NowcastFeed {
  summary: string;
  peakMinute: number | null;
  peakRateInchesPerHour: number;
  willRainSoon: boolean;
  minutesUntilStart: number | null;
  minutesUntilEnd: number | null;
  timeline: MinutePrecipitation[];
}

export interface HourlyForecastItem {
  time: string; // "1 PM", "2 PM", etc.
  fullTimestamp: string;
  tempF: number;
  feelsLikeF: number;
  popPercent: number; // Probability of precipitation
  conditionCategory: WeatherConditionCategory;
  conditionText: string;
  windSpeedMph: number;
}

export interface DailyForecastItem {
  dayOfWeek: string; // "Mon", "Tue", etc.
  dateStr: string;
  highF: number;
  lowF: number;
  popPercent: number;
  conditionCategory: WeatherConditionCategory;
  conditionText: string;
  summary: string;
}

export interface NwsAlert {
  id: string;
  event: string;
  headline: string;
  severity: 'Extreme' | 'Severe' | 'Moderate' | 'Minor';
  urgency: 'Immediate' | 'Expected' | 'Future' | 'Past';
  affectedCounties: string[];
  effective: string;
  expires: string;
  description: string;
  instruction: string;
}

export interface PollenLevels {
  overallRisk: 'Low' | 'Moderate' | 'High' | 'Very High';
  treeIndex: number; // 0 - 12
  treeDominant: string;
  grassIndex: number; // 0 - 12
  grassDominant: string;
  weedIndex: number; // 0 - 12
  weedDominant: string;
  regionalSamplingStation: string;
}

export interface AirQuality {
  aqi: number;
  category: 'Good' | 'Moderate' | 'Unhealthy for Sensitive Groups' | 'Unhealthy' | 'Very Unhealthy' | 'Hazardous';
  pm25: number; // ug/m3
  ozone: number; // ppb
  dominantPollutant: string;
  healthAdvice: string;
  reportingStation: string;
}

export interface EphemerisData {
  sunrise: string;
  sunset: string;
  solarNoon: string;
  goldenHourMorning: string;
  goldenHourEvening: string;
  blueHourMorning: string;
  blueHourEvening: string;
  dayLengthHours: number;
  sunElevationDeg: number;
  sunAzimuthDeg: number;
  moonPhase: 'New Moon' | 'Waxing Crescent' | 'First Quarter' | 'Waxing Gibbous' | 'Full Moon' | 'Waning Gibbous' | 'Last Quarter' | 'Waning Crescent';
  moonIlluminationPercent: number;
  moonrise: string;
  moonset: string;
}

export type ExpressiveColorTheme =
  | 'storm'
  | 'rain'
  | 'sun'
  | 'fog'
  | 'snow'
  | 'night';

export interface DynamicColorPalette {
  theme: ExpressiveColorTheme;
  primary: string;
  primaryGlow: string;
  onPrimary: string;
  surface: string;
  surfaceVariant: string;
  surfaceBorder: string;
  accent: string;
  backgroundGradient: string;
}

export type WindowingMode = 'phone' | 'foldable' | 'desktop' | 'pip';
