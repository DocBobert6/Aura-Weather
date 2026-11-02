import React, { useState } from 'react';
import { AlertTriangle, ChevronDown, ChevronUp, ShieldAlert, X } from 'lucide-react';
import { NwsAlert } from '../types/weather';

interface SevereAlertBannerProps {
  alerts: NwsAlert[];
  onDismiss?: (id: string) => void;
}

export const SevereAlertBanner: React.FC<SevereAlertBannerProps> = ({ alerts, onDismiss }) => {
  const [expandedId, setExpandedId] = useState<string | null>(alerts.length > 0 ? alerts[0].id : null);

  if (!alerts || alerts.length === 0) return null;

  return (
    <div className="flex flex-col gap-2.5 my-2">
      {alerts.map((alert) => {
        const isExpanded = expandedId === alert.id;

        return (
          <div
            key={alert.id}
            className="rounded-[24px] bg-[#341B1C] border border-[#F28B82]/30 p-4 text-[#F2B8B5] shadow-md transition-all"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-start gap-3">
                <div className="p-1.5 rounded-full bg-[#F28B82]/20 text-[#F28B82] mt-0.5">
                  <ShieldAlert className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-[11px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-[#F28B82] text-[#601410]">
                      Active NWS Warning
                    </span>
                    <span className="text-xs text-[#E6C0BE]">
                      Expires: {alert.expires}
                    </span>
                  </div>
                  <h4 className="text-sm font-semibold text-white mt-1">
                    {alert.headline}
                  </h4>
                </div>
              </div>

              <div className="flex items-center gap-1">
                <button
                  onClick={() => setExpandedId(isExpanded ? null : alert.id)}
                  className="p-1.5 rounded-full hover:bg-white/10 text-neutral-300 transition-colors"
                  aria-label={isExpanded ? 'Collapse' : 'Expand'}
                >
                  {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                </button>
                {onDismiss && (
                  <button
                    onClick={() => onDismiss(alert.id)}
                    className="p-1.5 rounded-full hover:bg-white/10 text-neutral-300 transition-colors"
                    aria-label="Dismiss"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
              </div>
            </div>

            {/* Expandable Details */}
            {isExpanded && (
              <div className="mt-3 pt-3 border-t border-white/10 text-xs space-y-2 text-[#E6C0BE]">
                <p className="leading-relaxed">{alert.description}</p>
                <div className="p-2.5 rounded-xl bg-black/25 text-[#F2B8B5]">
                  <strong className="block text-white mb-0.5">Safety Instructions:</strong>
                  {alert.instruction}
                </div>
                <div className="text-[11px] text-neutral-400">
                  Affected: {alert.affectedCounties.join(', ')}
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};
