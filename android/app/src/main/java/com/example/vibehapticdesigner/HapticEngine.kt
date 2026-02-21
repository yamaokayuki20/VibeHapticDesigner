package com.example.vibehapticdesigner

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.HapticGenerator
import android.util.Log
import kotlin.math.*

class HapticEngine(context: Context) {
    private var audioTrack: AudioTrack? = null
    private var hapticGenerator: HapticGenerator? = null
    private var isPlaying = false
    private var thread: Thread? = null

    // Debug tracking
    var debugText = "Initializing Engine..."
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
    private var phase = 0.0

    // Const
    private val sampleRate = 48000
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    init {
        initAudioTrack(context)
    }

    private fun initAudioTrack(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // USAGE_MEDIA is the most reliable for Pixel 8 audio-coupled haptics
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setHapticChannelsMuted(false) // CRITICAL for Android 12+ Audio-Haptics
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                debugText = "AudioTrack Init Failed! State: ${audioTrack?.state}"
                return
            }

            // Force media volume to max to ensure haptic signal is strong enough
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            
            // Attach Pixel 8 HD Haptics (HapticGenerator) to this specific audio session
            if (HapticGenerator.isAvailable() && audioTrack != null) {
                hapticGenerator = HapticGenerator.create(audioTrack!!.audioSessionId)
                if (hapticGenerator != null) {
                    hapticGenerator!!.enabled = true
                    debugText = "HapticGenerator Attached! (AudioTrack OK)"
                } else {
                    debugText = "HapticGenerator Supported but Creation Failed"
                }
            } else {
                debugText = "HapticGenerator NOT Supported on this OS/Device."
            }
        } catch (e: Exception) {
            debugText = "Exception: ${e.message}"
        }
    }

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
        // map velocity to 0..5 approx
        targetVelocity = min(dist * 5.0f, 5.0f)
    }
    fun onDragEnd() {
        targetVelocity = 0f
    }

    fun start() {
        if (isPlaying || audioTrack?.state != AudioTrack.STATE_INITIALIZED) return
        isPlaying = true
        audioTrack?.play()

        thread = Thread {
            val shortBuffer = ShortArray(bufferSize / 2)
            while (isPlaying) {
                // Smooth velocity (runs at buffer rate)
                smoothedVelocity += (targetVelocity - smoothedVelocity) * 0.1f
                targetVelocity *= 0.8f // Auto decay if no move events

                val currentVel = smoothedVelocity
                
                // Keep the track awake with a tiny amount of noise even when still
                val isMoving = currentVel > 0.01f
                val movementGain = if (isMoving) min(currentVel * 2.0f, 1.0f) else 0.05f

                for (i in shortBuffer.indices) {
                    val baseFreq = 20.0 + (weight * 160.0)
                    val drag = if (viscosity > 0) noise(phase * 0.1) * viscosity * 0.8 else 0.0

                    // Fast scroll speed for actual movement, slow drift when still
                    val scrollSpeed = if (isMoving) max(currentVel.toDouble(), 0.1) else 0.01
                    phase += (baseFreq * scrollSpeed * (1.0 - drag)) / sampleRate

                    var hapticVal = 0.0
                    var amp = 1.0
                    var freq = 1.0
                    var maxAmp = 0.0

                    for (oct in 0..4) {
                        hapticVal += noise(phase * freq) * amp
                        maxAmp += amp
                        amp *= roughness
                        freq *= 2.1
                    }
                    hapticVal /= maxAmp

                    if (granularity > 0) {
                        val absVal = abs(hapticVal)
                        val sign = sign(hapticVal)
                        val power = 1.0 + (granularity * 30.0)
                        hapticVal = sign * absVal.pow(power)
                        hapticVal *= (1.0 + granularity * 1.5)
                    }

                    if (state > 0) {
                        val steps = 1.0 + (1.0 - state) * 15.0
                        hapticVal = round(hapticVal * steps) / steps
                    }

                    if (hardness > 0) {
                        val threshold = 1.0 - (hardness * 0.7)
                        if (hapticVal > threshold) hapticVal = threshold - (hapticVal - threshold)
                        else if (hapticVal < -threshold) hapticVal = -threshold - (hapticVal + threshold)
                        hapticVal *= (1.0 + hardness * 1.2)
                    }

                    hapticVal = max(-1.0, min(1.0, hapticVal))

                    val finalGain = (0.5 + ((1.0 - weight) * 0.5)) * movementGain
                    
                    // Add a tiny baseline noise to prevent complete digital silence (helps keep Audio/Haptic session alive)
                    val baselineNoise = (hash(phase * 100.0) * 0.05).toFloat()
                    val mixedVal = hapticVal.toFloat() * finalGain + baselineNoise
                    
                    // Amplify heavily for HapticGenerator (needs loud signals to convert to vibration)
                    // We multiply by 2.5 to guarantee it hits the upper peaks where HapticGenerator actually triggers.
                    val outVal = (mixedVal * Short.MAX_VALUE * 2.5f).toInt()

                    shortBuffer[i] = outVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                audioTrack?.write(shortBuffer, 0, shortBuffer.size)
            }
        }
        thread?.start()
    }

    fun stop() {
        isPlaying = false
        thread?.join(500)
        audioTrack?.stop()
        audioTrack?.flush()
    }

    fun release() {
        stop()
        hapticGenerator?.release()
        audioTrack?.release()
    }

    // Hash and Noise (Simple 1D Value Noise)
    private fun hash(n: Double): Double {
        return (sin(n) * 43758.5453123) % 1.0
    }
    private fun noise(x: Double): Double {
        val p = floor(x)
        val f = x - p
        val u = f * f * (3.0 - 2.0 * f)
        return (hash(p) * (1.0 - u) + hash(p + 1.0) * u) * 2.0 - 1.0
    }
}
