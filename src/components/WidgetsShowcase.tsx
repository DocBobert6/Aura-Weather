import React, { useState } from 'react';
import {
  CloudRain,
  Droplets,
  Wind,
  ShieldAlert,
  ArrowUpRight,
  Maximize2,
  Copy,
  Check,
  Smartphone,
} from 'lucide-react';
import {
  CurrentObservation,
  NowcastFeed,
  NwsAlert,
  ExpressiveColorTheme,
} from '../types/weather';
import { TactileGlassCard } from './TactileGlassCard';

interface WidgetsShowcaseProps {
  observation: CurrentObservation;
  nowcast: NowcastFeed;
  alerts: NwsAlert[];
  theme: ExpressiveColorTheme;
}

export const WidgetsShowcase: React.FC<WidgetsShowcaseProps> = ({
  observation,
  nowcast,
  alerts,
  theme,
}) => {
  const [copiedCode, setCopiedCode] = useState(false);
  const [activeTab, setActiveTab] = useState<'preview' | 'glanceCode'>('preview');

  const handleCopyCode = () => {
    navigator.clipboard.writeText(glanceWidgetKotlin);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const glanceWidgetKotlin = `package com.aura.weather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.aura.weather.domain.model.WeatherObservation

/**
 * Android 17/18 Material 3 Expressive 4x2 Glance Widget
 * Target: KCVG / Greater Cincinnati Metro Grid
 */
class ExpressiveGlanceWeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                GlanceCardLayout()
            }
        }
    }

    @Composable
    private fun GlanceCardLayout() {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.tactile_glass_surface))
                .cornerRadius(28.dp)
                .padding(16.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Header Row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "KCVG · NKY Airport",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "Nowcast",
                        style = TextStyle(fontSize = 11.sp, color = ColorProvider(R.color.m3_expressive_sky))
                    )
                }

                // Temp & Condition
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\${observation.tempF}°",
                        style = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                    )
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Column {
                        Text(
                            text = observation.conditionText,
                            style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        )
                        Text(
                            text = "Feels like \${observation.feelsLikeF}° · Wind \${observation.windMph} mph",
                            style = TextStyle(fontSize = 11.sp)
                        )
                    }
                }
            }
        }
    }
}`;

  return (
    <div className="flex flex-col gap-4">
      {/* Tab bar */}
      <div className="flex items-center justify-between pb-1">
        <div>
          <h3 className="text-base font-semibold text-neutral-100 flex items-center gap-2">
            <Smartphone className="w-4 h-4 text-sky-400" />
            Android 17/18 M3 Expressive Widgets
          </h3>
          <p className="text-xs text-neutral-400">
            Interactive Jetpack Glance & lockscreen widgets designed for rapid glanceability
          </p>
        </div>

        <div className="flex items-center bg-neutral-900/80 p-1 rounded-xl border border-neutral-800">
          <button
            onClick={() => setActiveTab('preview')}
            className={`px-3 py-1 text-xs font-medium rounded-lg transition-colors ${
              activeTab === 'preview'
                ? 'bg-neutral-800 text-white shadow-sm'
                : 'text-neutral-400 hover:text-white'
            }`}
          >
            Live Canvas
          </button>
          <button
            onClick={() => setActiveTab('glanceCode')}
            className={`px-3 py-1 text-xs font-medium rounded-lg transition-colors ${
              activeTab === 'glanceCode'
                ? 'bg-neutral-800 text-white shadow-sm'
                : 'text-neutral-400 hover:text-white'
            }`}
          >
            Glance Kotlin Code
          </button>
        </div>
      </div>

      {activeTab === 'preview' ? (
        <div className="grid grid-cols-1 md:grid-cols-12 gap-4">
          {/* Widget 1: 4x2 Detailed Glance Widget */}
          <div className="md:col-span-7">
            <div className="text-xs text-neutral-400 mb-2 flex items-center gap-1.5 font-medium">
              <span>Pixel Widget 4x2</span>
              <span>·</span>
              <span>Metro Glance & Raincast</span>
            </div>
            <div className="relative p-5 rounded-[28px] bg-[#1E252F] border border-white/[0.08] shadow-md transition-all">
              <div className="flex items-start justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-white">
                      {observation.stationName}
                    </span>
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-[#2A3340] text-[#A8C7FA]">
                      {observation.stationId}
                    </span>
                  </div>
                  <div className="flex items-baseline gap-2 mt-2">
                    <span className="text-4xl font-normal text-white">
                      {observation.temperatureF}°
                    </span>
                    <span className="text-xs text-neutral-400">
                      Feels {observation.feelsLikeF}°
                    </span>
                  </div>
                  <span className="text-xs font-medium text-[#A8C7FA] block mt-0.5">
                    {observation.conditionDescription}
                  </span>
                </div>

                <div className="text-right text-xs space-y-1">
                  <div className="flex items-center gap-1.5 text-neutral-300 justify-end">
                    <Wind className="w-3.5 h-3.5 text-[#A8C7FA]" />
                    <span>{observation.windSpeedMph} mph {observation.windDirectionCardinal}</span>
                  </div>
                  <div className="flex items-center gap-1.5 text-neutral-300 justify-end">
                    <Droplets className="w-3.5 h-3.5 text-[#80D5E6]" />
                    <span>{observation.humidityPercent}% RH</span>
                  </div>
                </div>
              </div>

              {/* Mini Nowcast bar in widget */}
              <div className="mt-4 pt-3 border-t border-white/5">
                <div className="flex items-center justify-between text-xs text-neutral-400 mb-1.5">
                  <span className="flex items-center gap-1 text-[#A8C7FA] font-medium">
                    <CloudRain className="w-3.5 h-3.5" />
                    Next hour rain
                  </span>
                  <span>{nowcast.willRainSoon ? 'Showers arriving' : 'Dry conditions'}</span>
                </div>
                <div className="flex items-end gap-[2px] h-7 w-full">
                  {nowcast.timeline.slice(0, 30).map((m, idx) => (
                    <div
                      key={idx}
                      style={{ height: `${Math.max(15, m.intensityPercent)}%` }}
                      className={`flex-1 rounded-t-[1px] ${
                        m.intensityPercent > 0
                          ? 'bg-[#A8C7FA]'
                          : 'bg-[#2A3340]'
                      }`}
                    />
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* Widget 2: 2x2 Compact Rain Gauge Widget */}
          <div className="md:col-span-5">
            <div className="text-xs text-neutral-400 mb-2 flex items-center gap-1.5 font-medium">
              <span>Pixel Widget 2x2</span>
              <span>·</span>
              <span>Hyper-Local Rain Gauge</span>
            </div>
            <div className="relative p-5 rounded-[28px] bg-[#1E252F] border border-white/[0.08] shadow-md flex flex-col justify-between h-[185px]">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-white">Rain intensity</span>
                <span className="w-2 h-2 rounded-full bg-emerald-400" />
              </div>

              <div className="flex items-center justify-between my-2">
                <div>
                  <div className="text-2xl font-normal text-white">
                    {nowcast.peakRateInchesPerHour > 0
                      ? `${nowcast.peakRateInchesPerHour} in/hr`
                      : '0.00 in/hr'}
                  </div>
                  <span className="text-xs text-neutral-400 block mt-0.5">
                    {nowcast.willRainSoon
                      ? `Rain in ~${nowcast.minutesUntilStart}m`
                      : 'No rain active'}
                  </span>
                </div>
                <div className="w-12 h-12 rounded-full border-2 border-[#2A3340] flex items-center justify-center bg-[#242C38]">
                  <CloudRain className="w-6 h-6 text-[#A8C7FA]" />
                </div>
              </div>

              <div className="text-xs text-neutral-400 pt-2 border-t border-white/5 flex items-center justify-between">
                <span>NWS Radar Sweep</span>
                <span className="text-neutral-300">KILN 0.5° Beam</span>
              </div>
            </div>
          </div>

          {/* Widget 3: Lockscreen Alert Pill */}
          <div className="col-span-full">
            <div className="text-xs text-neutral-400 mb-2 flex items-center gap-1.5 font-medium">
              <span>Android Lockscreen Pill</span>
              <span>·</span>
              <span>Glanceable Emergency Banner</span>
            </div>
            <div className="p-3 px-4 rounded-full bg-[#1E252F] border border-[#F28B82]/30 shadow-md flex items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="w-6 h-6 rounded-full bg-[#F28B82]/20 text-[#F28B82] flex items-center justify-center">
                  <ShieldAlert className="w-3.5 h-3.5" />
                </div>
                <div className="flex items-center gap-2 flex-wrap text-xs">
                  <span className="font-semibold text-white">
                    {alerts.length > 0 ? alerts[0].event : 'NWS Tri-State Watch'}
                  </span>
                  <span className="text-neutral-400">·</span>
                  <span className="text-neutral-300">
                    {alerts.length > 0
                      ? alerts[0].headline
                      : 'KCVG / KLUK corridors monitoring active boundary front'}
                  </span>
                </div>
              </div>

              <span className="text-xs text-[#A8C7FA] flex items-center gap-1 font-medium whitespace-nowrap">
                Open Radar <ArrowUpRight className="w-3 h-3" />
              </span>
            </div>
          </div>
        </div>
      ) : (
        <div className="relative rounded-2xl bg-neutral-950 p-4 border border-neutral-800 font-mono text-xs text-neutral-300 overflow-x-auto">
          <button
            onClick={handleCopyCode}
            className="absolute top-3 right-3 flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-neutral-800 hover:bg-neutral-700 text-neutral-200 transition-colors shadow-sm"
          >
            {copiedCode ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copiedCode ? 'Copied' : 'Copy Kotlin'}</span>
          </button>
          <pre className="text-sky-300">{glanceWidgetKotlin}</pre>
        </div>
      )}
    </div>
  );
};
