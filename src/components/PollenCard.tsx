import React from 'react';
import { Flower2, Trees, Leaf } from 'lucide-react';
import { PollenLevels, ExpressiveColorTheme } from '../types/weather';
import { TactileGlassCard } from './TactileGlassCard';

interface PollenCardProps {
  pollen: PollenLevels;
  theme: ExpressiveColorTheme;
}

export const PollenCard: React.FC<PollenCardProps> = ({ pollen, theme }) => {
  const getPollenColor = (val: number) => {
    if (val < 3) return 'bg-[#81C995]';
    if (val < 7) return 'bg-[#FDD663]';
    return 'bg-[#FFB77B]';
  };

  return (
    <TactileGlassCard theme={theme} className="p-5 flex flex-col justify-between">
      <div>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-medium text-white flex items-center gap-2">
            <Flower2 className="w-4 h-4 text-[#81C995]" />
            Pollen & allergens
          </h3>
          <span className="text-xs font-medium px-2.5 py-0.5 rounded-full bg-[#282F3B] text-neutral-300">
            {pollen.overallRisk} risk
          </span>
        </div>

        <div className="space-y-2.5 my-3">
          {/* Tree Pollen */}
          <div>
            <div className="flex items-center justify-between text-xs mb-1 text-neutral-300">
              <span className="flex items-center gap-1.5">
                <Trees className="w-3.5 h-3.5 text-neutral-400" /> Tree Pollen
              </span>
              <span>{pollen.treeIndex.toFixed(1)} / 12</span>
            </div>
            <div className="h-1.5 w-full bg-[#282F3B] rounded-full overflow-hidden">
              <div
                style={{ width: `${(pollen.treeIndex / 12) * 100}%` }}
                className={`h-full rounded-full ${getPollenColor(pollen.treeIndex)}`}
              />
            </div>
          </div>

          {/* Grass Pollen */}
          <div>
            <div className="flex items-center justify-between text-xs mb-1 text-neutral-300">
              <span className="flex items-center gap-1.5">
                <Leaf className="w-3.5 h-3.5 text-neutral-400" /> Grass (KY Bluegrass)
              </span>
              <span>{pollen.grassIndex.toFixed(1)} / 12</span>
            </div>
            <div className="h-1.5 w-full bg-[#282F3B] rounded-full overflow-hidden">
              <div
                style={{ width: `${(pollen.grassIndex / 12) * 100}%` }}
                className={`h-full rounded-full ${getPollenColor(pollen.grassIndex)}`}
              />
            </div>
          </div>

          {/* Weed Pollen */}
          <div>
            <div className="flex items-center justify-between text-xs mb-1 text-neutral-300">
              <span className="flex items-center gap-1.5">
                <Flower2 className="w-3.5 h-3.5 text-neutral-400" /> Weed (Ragweed)
              </span>
              <span>{pollen.weedIndex.toFixed(1)} / 12</span>
            </div>
            <div className="h-1.5 w-full bg-[#282F3B] rounded-full overflow-hidden">
              <div
                style={{ width: `${(pollen.weedIndex / 12) * 100}%` }}
                className={`h-full rounded-full ${getPollenColor(pollen.weedIndex)}`}
              />
            </div>
          </div>
        </div>
      </div>

      <div className="text-[11px] text-neutral-400 pt-2 border-t border-white/5 flex items-center justify-between">
        <span>Station</span>
        <span className="truncate max-w-[200px]">Cincinnati Children’s Network</span>
      </div>
    </TactileGlassCard>
  );
};
