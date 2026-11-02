import React from 'react';
import { Wind } from 'lucide-react';
import { AirQuality, ExpressiveColorTheme } from '../types/weather';
import { TactileGlassCard } from './TactileGlassCard';

interface AirQualityCardProps {
  airQuality: AirQuality;
  theme: ExpressiveColorTheme;
}

export const AirQualityCard: React.FC<AirQualityCardProps> = ({ airQuality, theme }) => {
  const getAqiColor = (aqi: number) => {
    if (aqi <= 50) return { bg: 'bg-[#81C995]', text: 'text-[#81C995]', label: 'Good' };
    if (aqi <= 100) return { bg: 'bg-[#FDD663]', text: 'text-[#FDD663]', label: 'Moderate' };
    if (aqi <= 150) return { bg: 'bg-[#FFB77B]', text: 'text-[#FFB77B]', label: 'Unhealthy for sensitive groups' };
    return { bg: 'bg-[#F28B82]', text: 'text-[#F28B82]', label: 'Unhealthy' };
  };

  const aqiInfo = getAqiColor(airQuality.aqi);

  return (
    <TactileGlassCard theme={theme} className="p-5 flex flex-col justify-between">
      <div>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-medium text-white flex items-center gap-2">
            <Wind className="w-4 h-4 text-[#80D5E6]" />
            Air quality
          </h3>
          <span className={`text-xs font-medium px-2.5 py-0.5 rounded-full bg-[#282F3B] ${aqiInfo.text}`}>
            {aqiInfo.label}
          </span>
        </div>

        <div className="my-2">
          <div className="flex items-baseline gap-2">
            <span className="text-4xl font-normal text-white">
              {airQuality.aqi}
            </span>
            <span className="text-xs text-neutral-400">AQI</span>
          </div>
          <p className="text-xs text-neutral-300 mt-1">
            {airQuality.healthAdvice}
          </p>
        </div>

        {/* Clean Google AQI color spectrum bar */}
        <div className="h-1.5 w-full bg-[#282F3B] rounded-full overflow-hidden flex my-3">
          <div className="h-full w-1/4 bg-[#81C995]" />
          <div className="h-full w-1/4 bg-[#FDD663]" />
          <div className="h-full w-1/4 bg-[#FFB77B]" />
          <div className="h-full w-1/4 bg-[#F28B82]" />
        </div>
      </div>

      <div className="text-[11px] text-neutral-400 pt-2 border-t border-white/5 flex items-center justify-between">
        <span>PM2.5: {airQuality.pm25} µg/m³</span>
        <span>Ozone: {airQuality.ozone} ppb</span>
      </div>
    </TactileGlassCard>
  );
};
