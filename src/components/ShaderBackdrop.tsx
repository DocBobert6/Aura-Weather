import React from 'react';
import { WeatherConditionCategory, ExpressiveColorTheme } from '../types/weather';

interface ShaderBackdropProps {
  condition: WeatherConditionCategory;
  theme: ExpressiveColorTheme;
  isDaytime: boolean;
}

/**
 * Material You Dynamic Ambient Backdrop
 * Subtle organic atmosphere matching Pixel OS guidelines.
 */
export const ShaderBackdrop: React.FC<ShaderBackdropProps> = ({
  condition,
  theme,
  isDaytime,
}) => {
  const getGradient = () => {
    switch (theme) {
      case 'storm':
        return 'bg-gradient-to-b from-[#181216] via-[#10141A] to-[#0A0D12]';
      case 'rain':
        return 'bg-gradient-to-b from-[#101D28] via-[#0E151E] to-[#090D13]';
      case 'sun':
        return isDaytime
          ? 'bg-gradient-to-b from-[#1E1912] via-[#14141A] to-[#0A0D12]'
          : 'bg-gradient-to-b from-[#16141D] via-[#0F1218] to-[#080B10]';
      case 'fog':
        return 'bg-gradient-to-b from-[#181C22] via-[#11141A] to-[#0B0D11]';
      case 'snow':
        return 'bg-gradient-to-b from-[#121A22] via-[#0D131A] to-[#070A0F]';
      case 'night':
      default:
        return 'bg-gradient-to-b from-[#121620] via-[#0E1219] to-[#080B10]';
    }
  };

  return (
    <div className={`fixed inset-0 pointer-events-none z-0 transition-colors duration-700 ${getGradient()}`}>
      {/* Subtle organic radial vignette */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,_rgba(255,255,255,0.03)_0%,_transparent_70%)]" />
    </div>
  );
};
