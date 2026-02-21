package com.example.vibehapticdesigner

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
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

    // Debug tracking
    var debugText = "Using Reliable Vibrator API"
        private set(value) {
            field = value
            onDebugUpdate?.invoke(value)
            Log.d("HapticEngine", value)
        }
    var onDebugUpdate: ((String) -> Unit)? = null

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
        // With raw pixel deltas, dist is usually 1.0 to 100.0.
        // Map it to a reasonable velocity scale for Vibrator
        targetVelocity = min(dist * 0.1f, 5.0f)
    }
    fun onDragEnd() {
        targetVelocity = 0f
    }

    // Hash and Noise for procedural texture synthesis
    private fun fract(x: Double): Double = x - floor(x)

    private fun hash(n: Double): Double {
        return fract(sin(n) * 43758.5453123)
    }
    private fun noise(x: Double): Double {
        val p = floor(x)
        val f = x - p
        val u = f * f * (3.0 - 2.0 * f)
        return (hash(p) * (1.0 - u) + hash(p + 1.0) * u) * 2.0 - 1.0
    }

    fun start() {
        if (isPlaying) return
        isPlaying = true

        thread = Thread {
            var phase = 0.0
            
            while (isPlaying) {
                val loopStartTime = System.currentTimeMillis()
                
                // Smooth velocity
                smoothedVelocity += (targetVelocity - smoothedVelocity) * 0.1f
                targetVelocity *= 0.8f // Auto decay if no move events

                val currentVel = smoothedVelocity
                
                // Only generate texture if moving
                if (currentVel > 0.05f) {
                    
                    // Parameters to frequency/interval and amplitude mapping
                    val drag = if (viscosity > 0) noise(phase * 0.1) * viscosity * 0.8 else 0.0
                    val scrollSpeed = max(currentVel.toDouble(), 0.02)
                    
                    // How fast the "texture bumps" arrive (spatial frequency)
                    // High viscosity (liquid) or Low Roughness (smooth cloth) needs very high frequency to blend into a continuous hum
                    val baseFreq = if (viscosity > 0.4f || roughness < 0.3f) {
                        50.0 + (viscosity * 100.0) + (weight * 20.0) // Smooth continuous
                    } else {
                        10.0 + (weight * 40.0) + (roughness * 20.0) // Distinct granular bumps
                    }
                    
                    // Advance virtual phase over the texture
                    val phaseDelta = (baseFreq * scrollSpeed * (1.0 - drag)) / 120.0 // Assuming higher ~120Hz poll rate for smoothness
                    phase += phaseDelta

                    // If we crossed an integer boundary, we hit a "bump" in the texture
                    if (floor(phase) > floor(phase - phaseDelta)) {
                        
                        // Modulate amplitude based on noise to make it feel organic, not a perfect buzz
                        // Liquid/Smooth has less noise modulation than rough/granular
                        val noiseFactor = if (viscosity > 0.4f) 0.1 else if (roughness < 0.3f) 0.05 else 1.0
                        val bumpVal = if (noiseFactor < 1.0) 1.0 else abs(noise(phase * (1.0 + granularity * 5.0)))
                        
                        // Scale amplitude
                        val amplitudeScale = min((currentVel * 0.5f) + (bumpVal.toFloat() * roughness * noiseFactor.toFloat()), 1.0f)

                        playTextureBump(amplitudeScale)
                    }
                }

                // Sleep to maintain ~120Hz logic loop (about 8ms) for super smooth high-freq textures
                val elapsed = System.currentTimeMillis() - loopStartTime
                val sleepTime = 8L - elapsed
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime)
                }
            }
        }
        thread?.start()
    }

    private fun playTextureBump(amplitudeScale: Float) {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 
                VibrationEffect.Composition.PRIMITIVE_TICK, 
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            )) {
                
            val composition = VibrationEffect.startComposition()
            
            // Choose primary primitive based on state/hardness/weight/viscosity
            val primaryPrimitive = when {
                viscosity > 0.4f -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK // Muddy/Viscous liquid: dull low tick
                roughness < 0.3f && state < 0.5f -> VibrationEffect.Composition.PRIMITIVE_TICK // Smooth soft cloth
                hardness > 0.6f && state < 0.3f -> VibrationEffect.Composition.PRIMITIVE_CLICK // Hard solid: sharp click
                weight > 0.6f -> VibrationEffect.Composition.PRIMITIVE_THUD    // Heavy: deep thud
                else -> VibrationEffect.Composition.PRIMITIVE_TICK             // Default texture: light tick
            }

            // Primitive scale mapping (0.0 to 1.0)
            composition.addPrimitive(primaryPrimitive, amplitudeScale)

            // Add secondary primitive for high granularity/roughness (crispy texture)
            // But NOT for viscous/liquids which should remain smooth
            if (granularity > 0.5f && viscosity < 0.4f) {
                // adding a tiny delayed click makes it feel "sandpapery" or granular
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, amplitudeScale * 0.5f, 5) 
            } else if (viscosity > 0.8f) {
                // extreme liquid: double low tick for "slosh" feel
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, amplitudeScale * 0.3f, 8) 
            }

            vibrator.vibrate(composition.compose())
            
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Fallback for non-Pixel8 or older devices
            val ampInt = (amplitudeScale * 255).toInt().coerceIn(1, 255)
            val duration = if (viscosity > 0.5f) 15L else 5L // Sticky feels longer, Hard feels shorter
            vibrator.vibrate(VibrationEffect.createOneShot(duration, ampInt))
        } else {
            // Legacy
            @Suppress("DEPRECATION")
            vibrator.vibrate(5)
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
