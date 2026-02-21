package com.example.vibehapticdesigner

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.*

class HapticEngine(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var isPlaying = false
    private var thread: Thread? = null

    // Params
    private var granularity = 0f
    private var roughness = 0.2f
    private var state = 0f
    private var hardness = 0f
    private var weight = 0f
    private var viscosity = 0f

    // Physics
    private var smoothedVelocity = 0f
    private var targetVelocity = 0f
    private var lastVibrationTime = 0L

    fun updateParams(granularity: Float, roughness: Float, state: Float, hardness: Float, weight: Float, viscosity: Float) {
        this.granularity = granularity
        this.roughness = roughness
        this.state = state
        this.hardness = hardness
        this.weight = weight
        this.viscosity = viscosity
    }

    fun onDragStart() {}
    fun onDragMove(vX: Float, vY: Float) {
        val dist = sqrt(vX * vX + vY * vY)
        targetVelocity = min(dist * 5.0f, 5.0f)
    }
    fun onDragEnd() {
        targetVelocity = 0f
    }

    fun start() {
        if (isPlaying) return
        isPlaying = true

        thread = Thread {
            while (isPlaying) {
                // Smooth velocity
                smoothedVelocity += (targetVelocity - smoothedVelocity) * 0.1f
                targetVelocity *= 0.8f // Auto decay if no move events

                val currentVel = smoothedVelocity
                
                // Only vibrate if moving fast enough
                if (currentVel > 0.05f) {
                    val now = System.currentTimeMillis()
                    
                    // Interval between haptic pulses depends on weight and velocity
                    // High weight = slower pulses (deep rumbles), fast velocity = faster pulses
                    val baseIntervalMs = 20.0 + (weight * 60.0)
                    val intervalMs = max(baseIntervalMs / currentVel, 8.0).toLong()

                    if (now - lastVibrationTime > intervalMs) {
                        playTextureHaptic(currentVel)
                        lastVibrationTime = now
                    }
                }

                Thread.sleep(8) // Physics poll rate ~120Hz
            }
        }
        thread?.start()
    }

    private fun playTextureHaptic(velocity: Float) {
        if (!vibrator.hasVibrator()) return

        // Calculate Amplitude scale (0.0 to 1.0)
        var amplitudeScale = min(velocity * 0.8f + roughness * 0.5f, 1.0f)
        
        // Weight makes it feel heavier/stronger
        amplitudeScale = min(amplitudeScale * (0.5f + weight * 0.5f), 1.0f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_THUD)) {
            // Advanced Composition Haptics for Pixel 8
            val composition = VibrationEffect.startComposition()
            
            // Determine primary primitive
            val primaryPrimitive = when {
                hardness > 0.7f -> VibrationEffect.Composition.PRIMITIVE_CLICK // Hard, sharp
                state > 0.5f -> VibrationEffect.Composition.PRIMITIVE_TICK     // Solid, discontinuous
                weight > 0.6f -> VibrationEffect.Composition.PRIMITIVE_THUD    // Heavy, low freq
                else -> VibrationEffect.Composition.PRIMITIVE_TICK             // Default
            }

            // Primitive scale mapping
            val primitiveScale = amplitudeScale
            
            composition.addPrimitive(primaryPrimitive, primitiveScale)

            // Add secondary primitive for granularity/roughness
            if (granularity > 0.3f || roughness > 0.5f) {
                val secScale = (granularity * 0.5f + roughness * 0.5f) * primitiveScale
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, min(secScale, 1.0f), 5) // 5ms delay
            }

            vibrator.vibrate(composition.compose())
            
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Fallback Amplitude Control
            val ampInt = (amplitudeScale * 255).toInt().coerceIn(1, 255)
            val duration = (10 + viscosity * 20).toLong() // Viscosity makes vibration longer/muddier
            vibrator.vibrate(VibrationEffect.createOneShot(duration, ampInt))
        } else {
            // Legacy
            @Suppress("DEPRECATION")
            vibrator.vibrate(15)
        }
    }

    fun stop() {
        isPlaying = false
        thread?.join(500)
        vibrator.cancel()
    }

    fun release() {
        stop()
    }
}
