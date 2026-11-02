import React, { useState } from 'react';
import { CloudRain, Clock, Droplets, Sun } from 'lucide-react';
import { NowcastFeed, HourlyForecastItem, ExpressiveColorTheme } from '../types/weather';
import { TactileGlassCard } from './TactileGlassCard';

interface NowcastPrecipitationProps {
  nowcast: NowcastFeed;
  hourly: HourlyForecastItem[];
  theme: ExpressiveColorTheme;
  onSimulateRainToggle?: () => void;
  isSimulatedRain?: boolean;
}

export const NowcastPrecipitation: React.FC<NowcastPrecipitationProps> = ({
  nowcast,
  hourly,
  theme,
  onSimulateRainToggle,
  isSimulatedRain = false,
}) => {
  const [hoveredMinute, setHoveredMinute] = useState<number | null>(null);

  const activeMinuteData =
    hoveredMinute !== null ? nowcast.timeline[hoveredMinute] : null;

  return (
    <div className="flex flex-col gap-4">
      {/* Google Weather Style: Precipitation in Next Hour Card */}
      <TactileGlassCard theme={theme} className="p-5">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <Droplets className="w-4 h-4 text-[#A8C7FA]" />
            <h3 className="text-sm font-medium text-white">
              Precipitation in next hour
            </h3>
          </div>

          {onSimulateRainToggle && (
            <button
              onClick={onSimulateRainToggle}
              className={`px-3 py-1 rounded-full text-xs font-medium transition-all ${
                isSimulatedRain
                  ? 'bg-[#F28B82]/20 text-[#F28B82] border border-[#F28B82]/40'
                  : 'bg-[#282F3B] text-neutral-300 hover:text-white'
              }`}
            >
              {isSimulatedRain ? 'Reset to Clear' : 'Simulate Rain'}
            </button>
          )}
        </div>

        {/* Narrative Summary */}
        <p className="text-xs text-neutral-300 mb-4">
          {nowcast.summary}
        </p>

        {/* 60-Minute Histogram with Light/Heavy Thresholds */}
        <div className="relative pt-3 pb-1">
          {activeMinuteData && (
            <div className="absolute -top-4 left-1/2 -translate-x-1/2 bg-[#282F3B] text-[#A8C7FA] text-[11px] font-mono px-2 py-0.5 rounded-md shadow-md pointer-events-none">
              +{activeMinuteData.minute}m: {activeMinuteData.rateInchesPerHour.toFixed(2)} in/hr ({activeMinuteData.probabilityPercent}%)
            </div>
          )}

          {/* Reference lines */}
          <div className="absolute inset-x-0 top-6 h-[1px] border-b border-white/5 pointer-events-none" />
          <div className="absolute right-0 top-5 text-[9px] text-neutral-500 font-mono">Heavy</div>

          <div className="flex items-end gap-[3px] h-16 w-full px-1">
            {nowcast.timeline.map((item) => {
              const heightPercent = Math.max(6, item.intensityPercent);
              const isHovered = hoveredMinute === item.minute;
              const hasPrecip = item.intensityPercent > 0;

              return (
                <div
                  key={item.minute}
                  onMouseEnter={() => setHoveredMinute(item.minute)}
                  onMouseLeave={() => setHoveredMinute(null)}
                  className="flex-1 flex flex-col items-center justify-end h-full cursor-pointer"
                >
                  <div
                    style={{ height: `${heightPercent}%` }}
                    className={`w-full rounded-t-sm transition-all duration-150 ${
                      hasPrecip
                        ? 'bg-[#A8C7FA]'
                        : 'bg-[#2B323D]'
                    } ${isHovered ? 'brightness-125' : ''}`}
                  />
                </div>
              );
            })}
          </div>

          {/* Time markers */}
          <div className="flex items-center justify-between text-[11px] text-neutral-400 mt-2 font-mono">
            <span>Now</span>
            <span>15 min</span>
            <span>30 min</span>
            <span>45 min</span>
            <span>60 min</span>
          </div>
        </div>
      </TactileGlassCard>

      {/* Google Weather Style: Hourly Forecast Row */}
      <TactileGlassCard theme={theme} className="p-5">
        <h3 className="text-sm font-medium text-white mb-3">
          Hourly forecast
        </h3>

        <div className="flex gap-2.5 overflow-x-auto pb-2 scrollbar-thin">
          {hourly.map((h, idx) => (
            <div
              key={idx}
              className={`flex flex-col items-center justify-between min-w-[68px] p-2.5 rounded-2xl transition-all ${
                idx === 0
                  ? 'bg-[#2A3340] text-white'
                  : 'bg-[#1C222B] text-neutral-300'
              }`}
            >
              <span className="text-xs font-medium">{h.time}</span>
              
              <div className="my-2.5">
                {h.conditionCategory === 'rain' || h.conditionCategory === 'thunderstorm' ? (
                  <CloudRain className="w-5 h-5 text-[#A8C7FA]" />
                ) : h.conditionCategory === 'clear' ? (
                  <Sun className="w-5 h-5 text-[#FDD663]" />
                ) : (
                  <span className="w-5 h-4 rounded-full bg-[#8E9199] inline-block" />
                )}
              </div>

              <span className="text-sm font-semibold">{h.tempF}°</span>

              <div className="mt-1 flex items-center gap-0.5 text-[10px] text-[#A8C7FA]">
                <Droplets className="w-2.5 h-2.5" />
                <span>{h.popPercent}%</span>
              </div>
            </div>
          ))}
        </div>
      </TactileGlassCard>
    </div>
  );
};
