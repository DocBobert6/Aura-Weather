import React, { useState } from 'react';
import {
  Code2,
  FileCode,
  FolderTree,
  Copy,
  Check,
  Cpu,
  Layers,
  Sparkles,
  Download,
} from 'lucide-react';

export const KotlinCodeStudio: React.FC = () => {
  const [activeFile, setActiveFile] = useState<string>('project_structure');
  const [copied, setCopied] = useState<boolean>(false);

  const files: Record<string, { title: string; lang: string; category: string; content: string }> = {
    project_structure: {
      title: 'Project Structure (Clean Architecture)',
      lang: 'text',
      category: 'Architecture',
      content: `app/
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/aura/weather/
│   │   │   ├── AuraWeatherApp.kt                  // Application entry & Hilt initialization
│   │   │   │
│   │   │   ├── data/                              // DATA LAYER
│   │   │   │   ├── remote/
│   │   │   │   │   ├── NwsWeatherApi.kt           // Retrofit interface for api.weather.gov
│   │   │   │   │   ├── OpenMeteoApi.kt            // 60-min nowcast & high-res micro-grid
│   │   │   │   │   └── RainViewerApi.kt           // NEXRAD KILN radar tile endpoints
│   │   │   │   ├── local/
│   │   │   │   │   ├── WeatherDatabase.kt         // Room DB for offline fallback
│   │   │   │   │   └── entity/WeatherEntity.kt
│   │   │   │   └── repository/
│   │   │   │       └── WeatherRepositoryImpl.kt   // Cached fallback & Flow emission
│   │   │   │
│   │   │   ├── domain/                            // DOMAIN LAYER (Pure Kotlin)
│   │   │   │   ├── model/
│   │   │   │   │   ├── LocalWeatherCondition.kt   // KCVG / KLUK domain entity
│   │   │   │   │   ├── NwsAlertPayload.kt         // Severe warnings & polygon geofence
│   │   │   │   │   ├── MinuteNowcast.kt           // 60-minute rain intensity array
│   │   │   │   │   ├── OhioValleyEphemeris.kt     // Solar / lunar ephemeris model
│   │   │   │   │   └── ExpressiveThemeState.kt    // Dynamic condition-based color token
│   │   │   │   ├── repository/
│   │   │   │   │   └── WeatherRepository.kt       // Interface contract
│   │   │   │   └── usecase/
│   │   │   │       ├── GetCurrentWeatherUseCase.kt
│   │   │   │       ├── ObserveNowcastStreamUseCase.kt
│   │   │   │       └── GetActiveAlertsUseCase.kt
│   │   │   │
│   │   │   ├── di/                                // DEPENDENCY INJECTION (Hilt)
│   │   │   │   ├── NetworkModule.kt               // OkHttp client + Cache Interceptor
│   │   │   │   └── RepositoryModule.kt
│   │   │   │
│   │   │   ├── ui/                                // UI LAYER (Jetpack Compose M3 Expressive)
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Color.kt                   // Dynamic condition palette tokens
│   │   │   │   │   ├── Type.kt                    // Material 3 Expressive variable typography
│   │   │   │   │   └── ExpressiveTheme.kt
│   │   │   │   ├── components/
│   │   │   │   │   ├── TactileGlassCard.kt        // AGSL-powered progressive blur card
│   │   │   │   │   ├── MinutePrecipitationBar.kt  // 60-minute interactive rain histogram
│   │   │   │   │   ├── NexradRadarOverlay.kt      // KILN live sweep with MapLibre/Compose
│   │   │   │   │   └── SevereAlertBanner.kt       // Collapsible NWS emergency card
│   │   │   │   ├── windowing/
│   │   │   │   │   ├── WeatherAdaptiveLayout.kt   // Unrestricted multi-pane resizing (Foldable/Desktop)
│   │   │   │   │   └── FloatingRadarBubble.kt     // PiP / Bubble mode for active weather
│   │   │   │   ├── screens/
│   │   │   │   │   ├── WeatherDashboardScreen.kt
│   │   │   │   │   └── WeatherViewModel.kt        // StateFlow<WeatherUiState>
│   │   │   │   └── widget/
│   │   │   │       ├── CompactRainGaugeWidget.kt  // 2x2 Glance Widget
│   │   │   │       └── DetailedGlanceWidget.kt    // 4x2 Glance Widget
│   │   │   │
│   │   │   └── shaders/                           // AGSL GRAPHICS (Android 17/18)
│   │   │       ├── TactileGlassShader.agsl        // Optical refraction & progressive blur
│   │   │       └── WeatherParticlesShader.agsl    // Rain streaks & lightning bloom
│   │   │
│   │   └── res/
│   └── build.gradle.kts                           // Gradle dependencies & compiler options`,
    },

    domain_entities: {
      title: 'LocalWeatherCondition.kt (Domain Entities)',
      lang: 'kotlin',
      category: 'Domain Layer',
      content: `package com.aura.weather.domain.model

import java.time.Instant

/**
 * Core domain representation of weather observations tailored to
 * Northern Kentucky and Greater Cincinnati microclimates.
 */
enum class OhioValleyStation(
    val callSign: String,
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val elevationFeet: Int
) {
    KCVG("KCVG", "Cincinnati/N. Kentucky Int'l", 39.0489, -84.6678, 890),
    KLUK("KLUK", "Lunken Municipal Airport", 39.1033, -84.4186, 483),
    COVINGTON("COV", "Covington Riverfront", 39.0837, -84.5086, 510),
    KILN("KILN", "Wilmington NEXRAD Doppler", 39.4200, -83.8200, 1040)
}

enum class ConditionCategory {
    THUNDERSTORM, RAIN, SNOW, FOG, CLEAR, CLOUDY, WIND
}

data class LocalWeatherCondition(
    val station: OhioValleyStation,
    val recordedAt: Instant,
    val temperatureFahrenheit: Double,
    val feelsLikeFahrenheit: Double,
    val dewPointFahrenheit: Double,
    val relativeHumidityPercent: Int,
    val windSpeedMph: Double,
    val windGustMph: Double,
    val windDirectionDegrees: Int,
    val windCardinalDirection: String,
    val barometricPressureInHg: Double,
    val pressureTrend: PressureTrend,
    val visibilityMiles: Double,
    val uvIndex: Int,
    val cloudCeilingFeet: Int?,
    val conditionDescription: String,
    val conditionCategory: ConditionCategory,
    val isDaytime: Boolean
)

enum class PressureTrend {
    RISING, STEADY, FALLING
}

/**
 * 60-Minute Hyper-Local Nowcast Minute-by-Minute Forecast
 */
data class MinuteNowcast(
    val summary: String,
    val peakMinute: Int?,
    val peakRateInchesPerHour: Double,
    val willRainSoon: Boolean,
    val minutesUntilStart: Int?,
    val minutesUntilEnd: Int?,
    val minuteBuckets: List<MinutePrecipitationBucket>
)

data class MinutePrecipitationBucket(
    val minuteIndex: Int, // 0..59
    val intensityPercent: Int, // 0..100
    val rateInchesPerHour: Double,
    val probabilityPercent: Int
)

/**
 * Real-time NWS Push Alert Payload
 */
data class NwsAlertPayload(
    val id: String,
    val event: String,
    val headline: String,
    val severity: AlertSeverity,
    val urgency: String,
    val affectedCounties: List<String>,
    val effective: Instant,
    val expires: Instant,
    val description: String,
    val instruction: String,
    val polygonCoordinates: List<Pair<Double, Double>>
)

enum class AlertSeverity {
    EXTREME, SEVERE, MODERATE, MINOR
}`,
    },

    compose_glass_card: {
      title: 'TactileGlassCard.kt (Jetpack Compose)',
      lang: 'kotlin',
      category: 'UI Layer',
      content: `package com.aura.weather.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.weather.domain.model.ConditionCategory

/**
 * Material 3 Expressive Tactile Glassmorphic Card
 * Targets Android 17/18 progressive blur & AGSL optical highlights.
 */
@Composable
fun TactileGlassCard(
    conditionCategory: ConditionCategory,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Fluid spring physics on press gestures
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "GlassCardSpringScale"
    )

    // Dynamic condition-based specular stroke and surface tint
    val strokeColor = when (conditionCategory) {
        ConditionCategory.THUNDERSTORM -> Color(0xFFF43F5E).copy(alpha = 0.35f)
        ConditionCategory.RAIN -> Color(0xFF38BDF8).copy(alpha = 0.28f)
        ConditionCategory.FOG -> Color(0xFF94A3B8).copy(alpha = 0.24f)
        ConditionCategory.CLEAR -> Color(0xFFF59E0B).copy(alpha = 0.28f)
        ConditionCategory.SNOW -> Color(0xFFE0F2FE).copy(alpha = 0.35f)
        ConditionCategory.CLOUDY, ConditionCategory.WIND -> Color(0xFF818CF8).copy(alpha = 0.22f)
    }

    val glassBackgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1E293B).copy(alpha = 0.65f),
            Color(0xFF0F172A).copy(alpha = 0.78f)
        )
    )

    val specularHighlight = Brush.horizontalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.25f),
            Color.Transparent,
            Color.White.copy(alpha = 0.08f)
        )
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // Hardware progressive blur via RenderEffect on Android 12+ / 17
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = RenderEffect
                        .createBlurEffect(24f, 24f, android.graphics.Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            }
            .shadow(elevation = elevation, shape = RoundedCornerShape(cornerRadius), ambientColor = strokeColor)
            .clip(RoundedCornerShape(cornerRadius))
            .background(glassBackgroundGradient)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(strokeColor, strokeColor.copy(alpha = 0.1f))),
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(18.dp)
    ) {
        // Specular top highlight line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(specularHighlight)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}`,
    },

    agsl_shader: {
      title: 'TactileGlassShader.agsl (Android 17/18 Shader)',
      lang: 'glsl',
      category: 'Graphics / AGSL',
      content: `// AGSL RuntimeShader for Tactile Glassmorphism in Android 17/18
// Simulates progressive optical refraction, frosted glass noise, and dynamic color dispersion

uniform float2 uResolution;
uniform float  uTime;
uniform float4 uAccentColor;
uniform float  uBlurProgress;
uniform shader uContent;

// Simplex pseudo-noise
float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    
    // Sample underlying composited content with progressive blur offset
    float offset = (1.0 - uv.y) * 4.0 * uBlurProgress;
    half4 baseColor = uContent.eval(fragCoord);
    half4 blurred = (
        uContent.eval(fragCoord + float2(-offset, -offset)) +
        uContent.eval(fragCoord + float2(offset, -offset)) +
        uContent.eval(fragCoord + float2(-offset, offset)) +
        uContent.eval(fragCoord + float2(offset, offset))
    ) * 0.25;

    // Specular highlight along top glass bevel
    float topBevel = smoothstep(0.0, 0.03, uv.y) * (1.0 - smoothstep(0.03, 0.08, uv.y));
    half4 specular = half4(1.0, 1.0, 1.0, 1.0) * topBevel * 0.4;

    // Frosted glass micro-noise
    float noise = (hash(fragCoord + uTime) - 0.5) * 0.035;

    // Ambient condition tint blending
    half4 tintedSurface = mix(blurred, uAccentColor, 0.18) + half4(noise);
    
    return clamp(tintedSurface + specular, 0.0, 1.0);
}`,
    },

    repository_impl: {
      title: 'WeatherRepositoryImpl.kt (Offline Fallback)',
      lang: 'kotlin',
      category: 'Data Layer',
      content: `package com.aura.weather.data.repository

import com.aura.weather.data.local.WeatherDao
import com.aura.weather.data.remote.NwsWeatherApi
import com.aura.weather.data.remote.OpenMeteoApi
import com.aura.weather.domain.model.*
import com.aura.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val nwsApi: NwsWeatherApi,
    private val openMeteoApi: OpenMeteoApi,
    private val weatherDao: WeatherDao
) : WeatherRepository {

    override fun observeStationWeather(station: OhioValleyStation): Flow<Resource<LocalWeatherCondition>> = flow {
        emit(Resource.Loading)

        // 1. First emit cached baseline from Room database (Immediate UI display)
        val cached = weatherDao.getLatestObservation(station.callSign)
        if (cached != null) {
            emit(Resource.Success(cached.toDomainModel()))
        }

        // 2. Fetch fresh real-time observation from NWS / Open-Meteo
        try {
            val observationDto = nwsApi.getStationObservation(station.callSign)
            val domainCondition = observationDto.toDomain(station)

            // Cache into Room
            weatherDao.insertObservation(domainCondition.toEntity())

            emit(Resource.Success(domainCondition))
        } catch (e: IOException) {
            // Network failure: preserve cached data with offline warning
            if (cached != null) {
                emit(Resource.OfflineCache(cached.toDomainModel(), message = "Serving cached NKY observation"))
            } else {
                emit(Resource.Error(e.message ?: "Failed to reach NWS KCVG service"))
            }
        }
    }

    override suspend fun get60MinuteNowcast(lat: Double, lon: Double): MinuteNowcast {
        return openMeteoApi.getMinutelyPrecipitation(lat, lon).toDomainNowcast()
    }
}`,
    },
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(files[activeFile].content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="flex flex-col gap-4">
      {/* Studio Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-5 rounded-3xl bg-neutral-900/60 border border-neutral-800 backdrop-blur-xl">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-indigo-500/15 flex items-center justify-center text-indigo-400 border border-indigo-500/30">
            <Cpu className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-white flex items-center gap-2">
              Kotlin & Jetpack Compose Architecture Studio
              <span className="text-[10px] font-mono uppercase px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                Android 17/18 Target
              </span>
            </h2>
            <p className="text-xs text-neutral-400">
              Clean Architecture (Data → Domain → UI) · AGSL Shaders · MVVM StateFlow · Retrofit & Hilt
            </p>
          </div>
        </div>

        <button
          onClick={handleCopy}
          className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-lg shadow-indigo-600/30 transition-all self-start sm:self-auto"
        >
          {copied ? <Check className="w-4 h-4 text-emerald-300" /> : <Copy className="w-4 h-4" />}
          <span>{copied ? 'Copied File' : 'Copy Active File'}</span>
        </button>
      </div>

      {/* Editor & File Browser Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* File Navigator Sidebar */}
        <div className="lg:col-span-4 flex flex-col gap-2">
          <span className="text-[11px] font-mono text-neutral-400 uppercase tracking-wider px-2">
            Project Files
          </span>
          <div className="space-y-1.5">
            {Object.entries(files).map(([key, f]) => {
              const isActive = activeFile === key;
              return (
                <button
                  key={key}
                  onClick={() => setActiveFile(key)}
                  className={`w-full text-left p-3 rounded-2xl border transition-all flex items-center justify-between ${
                    isActive
                      ? 'bg-neutral-800/90 border-indigo-500/50 shadow-md text-white'
                      : 'bg-neutral-900/40 border-neutral-800/60 text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800/40'
                  }`}
                >
                  <div className="flex items-center gap-2.5 truncate">
                    <FileCode className={`w-4 h-4 flex-shrink-0 ${isActive ? 'text-indigo-400' : 'text-neutral-500'}`} />
                    <span className="text-xs font-medium truncate">{f.title.split(' ')[0]}</span>
                  </div>
                  <span className="text-[10px] font-mono text-neutral-500 px-1.5 py-0.5 rounded bg-neutral-900">
                    {f.category}
                  </span>
                </button>
              );
            })}
          </div>

          {/* Architecture Checklist Card */}
          <div className="mt-2 p-4 rounded-2xl bg-neutral-950/60 border border-neutral-800/80 text-xs space-y-2">
            <span className="font-semibold text-neutral-200 block text-xs">Architectural Invariants:</span>
            <ul className="space-y-1.5 text-neutral-400 text-[11px]">
              <li className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                Pure Kotlin Domain Entities (Zero Android dependencies)
              </li>
              <li className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                AGSL RuntimeShader hardware-accelerated progressive blur
              </li>
              <li className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                Retrofit + Room cached offline fallback for NWS KCVG
              </li>
              <li className="flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                Jetpack Glance 4x2 & 2x2 Material 3 Widgets
              </li>
            </ul>
          </div>
        </div>

        {/* Code Content Viewer */}
        <div className="lg:col-span-8 flex flex-col rounded-3xl bg-neutral-950 border border-neutral-800 shadow-2xl overflow-hidden">
          {/* Editor Header Bar */}
          <div className="flex items-center justify-between px-4 py-3 bg-neutral-900/80 border-b border-neutral-800">
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 rounded-full bg-rose-500/80" />
              <span className="w-3 h-3 rounded-full bg-amber-500/80" />
              <span className="w-3 h-3 rounded-full bg-emerald-500/80" />
              <span className="ml-2 text-xs font-mono text-neutral-300">
                {files[activeFile].title}
              </span>
            </div>
            <span className="text-[11px] font-mono text-neutral-500 uppercase">
              {files[activeFile].lang}
            </span>
          </div>

          {/* Editor Body */}
          <div className="p-5 font-mono text-xs text-neutral-300 overflow-x-auto max-h-[640px] leading-relaxed select-text">
            <pre className="text-neutral-200">
              <code>{files[activeFile].content}</code>
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
};
