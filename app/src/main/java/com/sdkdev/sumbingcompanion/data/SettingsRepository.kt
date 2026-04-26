package com.sdkdev.sumbingcompanion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sdkdev.sumbingcompanion.core.AutoCaptureConfig
import com.sdkdev.sumbingcompanion.core.CaptureRegion
import com.sdkdev.sumbingcompanion.core.WeatherState
import kotlinx.coroutines.flow.*

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val BUBBLE_X = floatPreferencesKey("bubble_x")
        val BUBBLE_Y = floatPreferencesKey("bubble_y")
        val COOLDOWN_DURATION = longPreferencesKey("cooldown_duration")
        
        val REGION_X = intPreferencesKey("region_x")
        val REGION_Y = intPreferencesKey("region_y")
        val REGION_W = intPreferencesKey("region_w")
        val REGION_H = intPreferencesKey("region_h")

        val AUTO_CAPTURE_ENABLED = booleanPreferencesKey("auto_capture_enabled")
        val AUTO_CAPTURE_INTERVAL = intPreferencesKey("auto_capture_interval")
        val AUTO_CAPTURE_MAX_PER_CLASS = intPreferencesKey("auto_capture_max_per_class")
        val AUTO_CAPTURE_STATES = stringSetPreferencesKey("auto_capture_states")
        
        val OVERLAY_ALPHA = floatPreferencesKey("overlay_alpha")
    }

    val overlayAlpha: Flow<Float> = context.dataStore.data.map { it[OVERLAY_ALPHA] ?: 1.0f }

    val bubbleX: Flow<Float?> = context.dataStore.data.map { it[BUBBLE_X] }
    val bubbleY: Flow<Float?> = context.dataStore.data.map { it[BUBBLE_Y] }
    val cooldownDuration: Flow<Long> = context.dataStore.data.map { it[COOLDOWN_DURATION] ?: (10 * 60 * 1000L) }

    val regionFlow: Flow<CaptureRegion> = context.dataStore.data.map {
        CaptureRegion(
            x = it[REGION_X] ?: 0,
            y = it[REGION_Y] ?: 0,
            w = it[REGION_W] ?: 0,
            h = it[REGION_H] ?: 0
        )
    }

    val autoCaptureConfigFlow: Flow<AutoCaptureConfig> = context.dataStore.data.map {
        AutoCaptureConfig(
            enabled = it[AUTO_CAPTURE_ENABLED] ?: false,
            intervalSec = it[AUTO_CAPTURE_INTERVAL] ?: 5,
            maxPerClass = it[AUTO_CAPTURE_MAX_PER_CLASS] ?: 200,
            captureOnStates = it[AUTO_CAPTURE_STATES]?.mapNotNull { state ->
                try { WeatherState.valueOf(state) } catch (e: Exception) { null }
            }?.toSet() ?: setOf(WeatherState.WEATHER, WeatherState.IDLE)
        )
    }

    suspend fun getRegion(): CaptureRegion = regionFlow.first()

    suspend fun saveRegion(region: CaptureRegion) {
        context.dataStore.edit {
            it[REGION_X] = region.x
            it[REGION_Y] = region.y
            it[REGION_W] = region.w
            it[REGION_H] = region.h
        }
    }

    suspend fun saveAutoCaptureConfig(config: AutoCaptureConfig) {
        context.dataStore.edit {
            it[AUTO_CAPTURE_ENABLED] = config.enabled
            it[AUTO_CAPTURE_INTERVAL] = config.intervalSec
            it[AUTO_CAPTURE_MAX_PER_CLASS] = config.maxPerClass
            it[AUTO_CAPTURE_STATES] = config.captureOnStates.map { state -> state.name }.toSet()
        }
    }

    suspend fun saveBubblePosition(x: Float, y: Float) {
        context.dataStore.edit {
            it[BUBBLE_X] = x
            it[BUBBLE_Y] = y
        }
    }

    suspend fun saveCooldownDuration(duration: Long) {
        context.dataStore.edit {
            it[COOLDOWN_DURATION] = duration
        }
    }

    suspend fun saveOverlayAlpha(alpha: Float) {
        context.dataStore.edit {
            it[OVERLAY_ALPHA] = alpha
        }
    }

    // Temporary session state (not persisted)
    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    fun setCalibrating(active: Boolean) {
        _isCalibrating.value = active
    }
}
