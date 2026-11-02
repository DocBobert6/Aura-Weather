import React from 'react';
import { motion, HTMLMotionProps } from 'motion/react';
import { ExpressiveColorTheme } from '../types/weather';

interface TactileGlassCardProps extends HTMLMotionProps<'div'> {
  children: React.ReactNode;
  className?: string;
  theme?: ExpressiveColorTheme;
  interactive?: boolean;
  elevation?: 'flat' | 'standard' | 'elevated';
}

/**
 * Material 3 Expressive Card (Material You / Google Weather on Pixel style)
 * Clean tonal surfaces, 28px corners, subtle optical boundary, no aggressive neon glows.
 */
export const TactileGlassCard: React.FC<TactileGlassCardProps> = ({
  children,
  className = '',
  theme = 'night',
  interactive = false,
  elevation = 'standard',
  ...rest
}) => {
  const getThemeSurface = () => {
    switch (theme) {
      case 'storm':
        return 'bg-[#211A1B] border-rose-500/20';
      case 'sun':
        return 'bg-[#221D17] border-amber-500/15';
      case 'rain':
        return 'bg-[#162128] border-sky-500/15';
      case 'fog':
        return 'bg-[#1C2024] border-slate-500/15';
      case 'snow':
        return 'bg-[#172028] border-cyan-500/15';
      case 'night':
      default:
        return 'bg-[#191F28] border-white/[0.06]';
    }
  };

  const elevationClasses =
    elevation === 'elevated'
      ? 'shadow-md'
      : elevation === 'flat'
      ? 'bg-transparent border-transparent'
      : 'shadow-sm';

  return (
    <motion.div
      whileHover={interactive ? { y: -1 } : undefined}
      transition={{ duration: 0.15, ease: 'easeOut' }}
      className={`relative rounded-[28px] border transition-all ${getThemeSurface()} ${elevationClasses} ${className}`}
      {...rest}
    >
      {children}
    </motion.div>
  );
};
