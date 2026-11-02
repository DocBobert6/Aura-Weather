import React from 'react';
import { Calendar, CloudRain, Sun, Cloud, CloudLightning } from 'lucide-react';
import { DailyForecastItem, ExpressiveColorTheme } from '../types/weather';
import { TactileGlassCard } from './TactileGlassCard';

interface TenDayForecastProps {
  daily: DailyForecastItem[];
  theme: ExpressiveColorTheme;
}

export const TenDayForecast: React.FC<TenDayForecastProps> = ({ daily, theme }) => {
  // Find global min and max across all days to scale the temperature bars
  const allLows = daily.map((d) => d.lowF);
  const allHighs = daily.map((d) => d.highF);
  const minTemp = Math.min(...allLows, 35);
  const maxTemp = Math.max(...allHighs, 80);
  const tempRange = Math.max(1, maxTemp - minTemp);

  const getConditionIcon = (cat: string) => {
    switch (cat) {
      case 'thunderstorm':
        return <CloudLightning className="w-4 h-4 text-[#F28B82]" />;
      case 'rain':
        return <CloudRain className="w-4 h-4 text-[#A8C7FA]" />;
      case 'clear':
        return <Sun className="w-4 h-4 text-[#FDD663]" />;
      default:
        return <Cloud className="w-4 h-4 text-[#BDC1C6]" />;
    }
  };

  return (
    <TactileGlassCard theme={theme} className="p-5">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Calendar className="w-4 h-4 text-[#A8C7FA]" />
          <h3 className="text-sm font-medium text-white">
            10-day forecast
          </h3>
        </div>
        <span className="text-xs text-neutral-400">NWS Wilmington Grid</span>
      </div>

      <div className="space-y-3">
        {daily.map((item, idx) => {
          // Calculate bar offset and width percentages
          const leftPercent = ((item.lowF - minTemp) / tempRange) * 100;
          const barWidthPercent = Math.max(15, ((item.highF - item.lowF) / tempRange) * 100);

          return (
            <div
              key={idx}
              className="flex items-center gap-3 py-1.5 text-xs text-neutral-200 border-b border-white/[0.04] last:border-0"
            >
              {/* Day label */}
              <div className="w-14 font-medium text-white">
                {idx === 0 ? 'Today' : item.dayOfWeek}
              </div>

              {/* Weather icon & rain % */}
              <div className="flex items-center gap-1.5 w-16">
                {getConditionIcon(item.conditionCategory)}
                {item.popPercent > 10 ? (
                  <span className="text-[11px] font-medium text-[#A8C7FA]">
                    {item.popPercent}%
                  </span>
                ) : (
                  <span className="text-[11px] text-transparent">--</span>
                )}
              </div>

              {/* Low temp */}
              <div className="w-8 text-right font-medium text-neutral-400">
                {item.lowF}°
              </div>

              {/* Iconic Google Weather Horizontal Temperature Gradient Bar */}
              <div className="flex-1 relative h-2 bg-[#282F3B] rounded-full overflow-hidden">
                <div
                  className="absolute h-full rounded-full bg-gradient-to-r from-[#A8C7FA] to-[#FFB77B]"
                  style={{
                    left: `${Math.max(0, Math.min(80, leftPercent))}%`,
                    width: `${Math.min(100 - leftPercent, barWidthPercent)}%`,
                  }}
                />
              </div>

              {/* High temp */}
              <div className="w-8 text-right font-semibold text-white">
                {item.highF}°
              </div>
            </div>
          );
        })}
      </div>
    </TactileGlassCard>
  );
};
