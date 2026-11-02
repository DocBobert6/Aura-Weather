import React from 'react';
import { Sun, Sunrise, Sunset, Moon } from 'lucide-react';
import { EphemerisData, ExpressiveColorTheme } from '../types/weather';
import { TactileGlassCard } from './TactileGlassCard';

interface SunMoonCardProps {
  ephemeris: EphemerisData;
  theme: ExpressiveColorTheme;
}

export const SunMoonCard: React.FC<SunMoonCardProps> = ({ ephemeris, theme }) => {
  return (
    <TactileGlassCard theme={theme} className="p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-medium text-white flex items-center gap-2">
          <Sunrise className="w-4 h-4 text-[#FDD663]" />
          Sunrise & sunset
        </h3>
        <span className="text-xs text-neutral-400">
          {ephemeris.dayLengthHours}h daylight
        </span>
      </div>

      {/* Google Weather Curved Solar Arc */}
      <div className="relative py-4 px-2 flex flex-col items-center">
        {/* SVG curved dotted arc */}
        <svg viewBox="0 0 240 70" className="w-full max-w-[280px] overflow-visible">
          {/* Horizon line */}
          <line x1="10" y1="65" x2="230" y2="65" stroke="rgba(255,255,255,0.12)" strokeWidth="1.5" />
          {/* Solar trajectory curve */}
          <path
            d="M 20 65 Q 120 -5 220 65"
            fill="none"
            stroke="rgba(253, 214, 99, 0.35)"
            strokeWidth="2"
            strokeDasharray="4 4"
          />
          {/* Sun glowing position indicator on arc */}
          <circle cx="145" cy="18" r="7" fill="#FDD663" className="shadow-lg" />
          <circle cx="145" cy="18" r="14" fill="rgba(253, 214, 99, 0.25)" />
        </svg>

        {/* Sunrise / Sunset Times */}
        <div className="w-full flex items-center justify-between mt-2 pt-2 border-t border-white/5 text-xs">
          <div>
            <span className="text-neutral-400 block text-[11px]">Sunrise</span>
            <span className="font-semibold text-white">{ephemeris.sunrise}</span>
          </div>

          <div className="text-center">
            <span className="text-neutral-400 block text-[11px]">Moon Phase</span>
            <span className="font-semibold text-white">{ephemeris.moonPhase}</span>
          </div>

          <div className="text-right">
            <span className="text-neutral-400 block text-[11px]">Sunset</span>
            <span className="font-semibold text-white">{ephemeris.sunset}</span>
          </div>
        </div>
      </div>
    </TactileGlassCard>
  );
};
