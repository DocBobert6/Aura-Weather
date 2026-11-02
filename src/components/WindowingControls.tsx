import React from 'react';
import {
  Smartphone,
  Tablet,
  Monitor,
  Settings,
  Sparkles,
} from 'lucide-react';
import { WindowingMode, ExpressiveColorTheme } from '../types/weather';

interface WindowingControlsProps {
  mode: WindowingMode;
  onSelectMode: (mode: WindowingMode) => void;
  currentTheme: ExpressiveColorTheme;
  onSelectTheme: (theme: ExpressiveColorTheme) => void;
  onOpenSettings: () => void;
  activeStationCode: string;
}

export const WindowingControls: React.FC<WindowingControlsProps> = ({
  mode,
  onSelectMode,
  currentTheme,
  onSelectTheme,
  onOpenSettings,
  activeStationCode,
}) => {
  const monetPalettes: { id: ExpressiveColorTheme; label: string; colorDot: string }[] = [
    { id: 'rain', label: 'Sky Blue', colorDot: 'bg-[#A8C7FA]' },
    { id: 'fog', label: 'Sage', colorDot: 'bg-[#A8DAB5]' },
    { id: 'sun', label: 'Peach', colorDot: 'bg-[#FFB77B]' },
    { id: 'night', label: 'Lavender', colorDot: 'bg-[#D0BCFF]' },
    { id: 'storm', label: 'Severe', colorDot: 'bg-[#F28B82]' },
  ];

  return (
    <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-2.5 p-3 rounded-[24px] bg-[#171D26] border border-white/[0.06] shadow-sm">
      {/* Monet Color Palette Chips */}
      <div className="flex items-center gap-1 overflow-x-auto pb-1 sm:pb-0 scrollbar-none">
        <span className="text-[11px] text-neutral-400 pl-1 pr-1 font-medium flex items-center gap-1 flex-shrink-0">
          <Sparkles className="w-3 h-3 text-[#A8C7FA]" />
          <span>Monet Theme:</span>
        </span>
        {monetPalettes.map((p) => {
          const isSelected = currentTheme === p.id;
          return (
            <button
              key={p.id}
              onClick={() => onSelectTheme(p.id)}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium transition-all ${
                isSelected
                  ? 'bg-[#2A3340] text-white shadow-sm'
                  : 'text-neutral-400 hover:text-neutral-200'
              }`}
            >
              <span className={`w-2.5 h-2.5 rounded-full ${p.colorDot}`} />
              <span>{p.label}</span>
            </button>
          );
        })}
      </div>

      <div className="flex items-center justify-between sm:justify-end gap-2">
        {/* Radar & Station Anchor Indicator Pill */}
        <button
          onClick={onOpenSettings}
          title="Configure Radar & Data Anchor Stations"
          className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#1F2631] hover:bg-[#283241] border border-white/5 text-xs text-neutral-300 transition-colors"
        >
          <span className="w-2 h-2 rounded-full bg-emerald-400" />
          <span>Anchor: {activeStationCode}</span>
          <Settings className="w-3.5 h-3.5 text-neutral-400 ml-0.5" />
        </button>

        {/* Device Window Class Segmented Button */}
        <div className="flex items-center bg-[#1F2631] p-1 rounded-full border border-white/5">
          <button
            onClick={() => onSelectMode('phone')}
            title="Pixel Phone View"
            className={`px-2.5 py-1 rounded-full text-xs font-medium transition-colors flex items-center gap-1 ${
              mode === 'phone'
                ? 'bg-[#A8C7FA] text-[#062E6F] font-semibold'
                : 'text-neutral-400 hover:text-white'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span className="hidden md:inline">Phone</span>
          </button>
          <button
            onClick={() => onSelectMode('foldable')}
            title="Foldable / Split View"
            className={`px-2.5 py-1 rounded-full text-xs font-medium transition-colors flex items-center gap-1 ${
              mode === 'foldable'
                ? 'bg-[#A8C7FA] text-[#062E6F] font-semibold'
                : 'text-neutral-400 hover:text-white'
            }`}
          >
            <Tablet className="w-3.5 h-3.5" />
            <span className="hidden md:inline">Split</span>
          </button>
          <button
            onClick={() => onSelectMode('desktop')}
            title="Expanded Desktop"
            className={`px-2.5 py-1 rounded-full text-xs font-medium transition-colors flex items-center gap-1 ${
              mode === 'desktop'
                ? 'bg-[#A8C7FA] text-[#062E6F] font-semibold'
                : 'text-neutral-400 hover:text-white'
            }`}
          >
            <Monitor className="w-3.5 h-3.5" />
            <span className="hidden md:inline">Full</span>
          </button>
        </div>
      </div>
    </div>
  );
};
