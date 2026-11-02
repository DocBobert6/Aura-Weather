import React from 'react';
import {
  Compass,
  Wind,
  Droplets,
  Gauge,
  Sun,
  ArrowUp,
  ArrowDown,
  Minus,
} from 'lucide-react';
import { CurrentObservation, ExpressiveColorTheme } from '../types/weather';
import { TactileGlassCard } from './TactileGlassCard';

interface MetricsGridProps {
  observation: CurrentObservation;
  theme: ExpressiveColorTheme;
}

export const MetricsGrid: React.FC<MetricsGridProps> = ({ observation, theme }) => {
  const getPressureIcon = () => {
    if (observation.pressureTrend === 'rising') {
      return <ArrowUp className="w-3.5 h-3.5 text-emerald-400" />;
    }
    if (observation.pressureTrend === 'falling') {
      return <ArrowDown className="w-3.5 h-3.5 text-rose-400" />;
    }
    return <Minus className="w-3.5 h-3.5 text-neutral-400" />;
  };

  const getUvLevel = (uv: number) => {
    if (uv <= 2) return { label: 'Low', desc: 'No protection required' };
    if (uv <= 5) return { label: 'Moderate', desc: 'Seek shade during midday' };
    if (uv <= 7) return { label: 'High', desc: 'Wear sunscreen SPF 30+' };
    return { label: 'Very High', desc: 'Extra precautions needed' };
  };

  const uvInfo = getUvLevel(observation.uvIndex);

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-3.5">
      {/* 1. Wind */}
      <TactileGlassCard theme={theme} className="p-4 flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400 mb-2">
          <span className="text-xs font-medium">Wind</span>
          <Wind className="w-4 h-4 text-[#A8C7FA]" />
        </div>
        <div>
          <div className="flex items-baseline gap-1.5">
            <span className="text-3xl font-normal text-white">
              {observation.windSpeedMph}
            </span>
            <span className="text-xs text-neutral-400">mph</span>
            <span className="text-xs text-neutral-300 font-medium ml-auto">
              {observation.windDirectionCardinal}
            </span>
          </div>
          <div className="text-[11px] text-neutral-400 mt-2 pt-2 border-t border-white/5 flex items-center justify-between">
            <span>Gusts</span>
            <span className="text-neutral-200">{observation.windGustMph} mph</span>
          </div>
        </div>
      </TactileGlassCard>

      {/* 2. Humidity & Dew point */}
      <TactileGlassCard theme={theme} className="p-4 flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400 mb-2">
          <span className="text-xs font-medium">Humidity</span>
          <Droplets className="w-4 h-4 text-[#80D5E6]" />
        </div>
        <div>
          <div className="flex items-baseline gap-1.5">
            <span className="text-3xl font-normal text-white">
              {observation.humidityPercent}%
            </span>
            <span className="text-xs text-neutral-400">RH</span>
          </div>
          <div className="text-[11px] text-neutral-400 mt-2 pt-2 border-t border-white/5 flex items-center justify-between">
            <span>The dew point is</span>
            <span className="text-neutral-200">{observation.dewPointF}°</span>
          </div>
        </div>
      </TactileGlassCard>

      {/* 3. UV Index */}
      <TactileGlassCard theme={theme} className="p-4 flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400 mb-2">
          <span className="text-xs font-medium">UV index</span>
          <Sun className="w-4 h-4 text-[#FDD663]" />
        </div>
        <div>
          <div className="flex items-baseline gap-1.5">
            <span className="text-3xl font-normal text-white">
              {observation.uvIndex}
            </span>
            <span className="text-xs font-medium text-[#FDD663] ml-1">
              {uvInfo.label}
            </span>
          </div>
          <div className="text-[11px] text-neutral-400 mt-2 pt-2 border-t border-white/5 truncate">
            {uvInfo.desc}
          </div>
        </div>
      </TactileGlassCard>

      {/* 4. Pressure */}
      <TactileGlassCard theme={theme} className="p-4 flex flex-col justify-between">
        <div className="flex items-center justify-between text-neutral-400 mb-2">
          <span className="text-xs font-medium">Pressure</span>
          <Gauge className="w-4 h-4 text-[#FFB77B]" />
        </div>
        <div>
          <div className="flex items-baseline gap-1.5">
            <span className="text-2xl font-normal text-white">
              {observation.barometricPressureInHg.toFixed(2)}
            </span>
            <span className="text-xs text-neutral-400">inHg</span>
            <span className="flex items-center gap-0.5 text-xs text-neutral-300 ml-auto">
              {getPressureIcon()}
            </span>
          </div>
          <div className="text-[11px] text-neutral-400 mt-2 pt-2 border-t border-white/5 flex items-center justify-between">
            <span>Station</span>
            <span className="text-neutral-200">{observation.stationId}</span>
          </div>
        </div>
      </TactileGlassCard>
    </div>
  );
};
