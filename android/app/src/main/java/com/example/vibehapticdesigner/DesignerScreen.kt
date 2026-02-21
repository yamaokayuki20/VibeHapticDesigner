package com.example.vibehapticdesigner

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DesignerScreen(hapticEngine: HapticEngine) {
    var isPlaying by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var aiStatus by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Parameters
    var granularity by remember { mutableFloatStateOf(0f) }
    var roughness by remember { mutableFloatStateOf(0.2f) }
    var state by remember { mutableFloatStateOf(0f) }
    var hardness by remember { mutableFloatStateOf(0f) }
    var weight by remember { mutableFloatStateOf(0.5f) }
    var viscosity by remember { mutableFloatStateOf(0.0f) }

    var debugText by remember { mutableStateOf(hapticEngine.debugText) }

    DisposableEffect(hapticEngine) {
        hapticEngine.onDebugUpdate = { newText ->
            debugText = newText
        }
        onDispose {
            hapticEngine.onDebugUpdate = null
        }
    }

    // Sync parameters to engine
    LaunchedEffect(granularity, roughness, state, hardness, weight, viscosity) {
        hapticEngine.updateParams(
            granularity = granularity,
            roughness = roughness,
            state = state,
            hardness = hardness,
            weight = weight,
            viscosity = viscosity
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("インタラクティブ 触覚デザインツール", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))

        // AI Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2A4B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🤖 AI 質感ジェネレーター", color = Color(0xFF66B2FF), fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("例: ザラザラしたアスファルト, 氷の上を滑る感覚") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (apiKey.isBlank() || prompt.isBlank()) {
                            aiStatus = "⚠ APIキーとプロンプトを入力してください"
                            return@Button
                        }
                        isGenerating = true
                        aiStatus = "⏳ 生成中..."
                        coroutineScope.launch {
                            val result = GeminiApi.generateHaptics(apiKey, prompt)
                            isGenerating = false
                            if (result.isSuccess) {
                                val params = result.getOrNull()
                                if (params != null) {
                                    granularity = params.granularity.coerceIn(0f, 1f)
                                    roughness = params.roughness.coerceIn(0f, 1f)
                                    state = params.state.coerceIn(0f, 1f)
                                    hardness = params.hardness.coerceIn(0f, 1f)
                                    weight = params.weight.coerceIn(0f, 1f)
                                    viscosity = params.viscosity.coerceIn(0f, 1f)
                                    aiStatus = "✅ 質感を適用しました"
                                }
                            } else {
                                aiStatus = "❌ エラー: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating
                ) {
                    Text("AIで質感を生成")
                }
                if (aiStatus.isNotEmpty()) {
                    Text(aiStatus, fontSize = 12.sp, color = if (aiStatus.startsWith("✅")) Color.Green else Color.Red)
                }
            }
        }

        // Drag Pad
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
        ) {
            val padWidth = maxWidth.value
            val padHeight = maxHeight.value
            val puckSize = 80f
            val density = LocalDensity.current.density

            var offsetX by remember { mutableFloatStateOf(padWidth / 2f - puckSize / 2f) }
            var offsetY by remember { mutableFloatStateOf(padHeight / 2f - puckSize / 2f) }
            var isDragging by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .offset(x = offsetX.dp, y = offsetY.dp)
                    .size(puckSize.dp)
                    .clip(CircleShape)
                    .background(if (isDragging) Color(0xFFFF5722) else Color(0xFF0070CC))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { 
                                isDragging = true
                                hapticEngine.onDragStart()
                            },
                            onDragEnd = { 
                                isDragging = false
                                hapticEngine.onDragEnd()
                            },
                            onDragCancel = { 
                                isDragging = false
                                hapticEngine.onDragEnd()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                
                                val dragXdp = dragAmount.x / density
                                val dragYdp = dragAmount.y / density

                                offsetX = (offsetX + dragXdp).coerceIn(0f, padWidth - puckSize)
                                offsetY = (offsetY + dragYdp).coerceIn(0f, padHeight - puckSize)
                                
                                // Velocity calculation for engine
                                val vX = dragAmount.x / change.uptimeMillis
                                val vY = dragAmount.y / change.uptimeMillis
                                hapticEngine.onDragMove(vX, vY)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Drag Me!", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Sliders
        HapticSlider("粒感 (滑らか 0 - ツブツブ 1)", granularity) { granularity = it }
        HapticSlider("すべすべ (0) - ザラザラ (1)", roughness) { roughness = it }
        HapticSlider("液体 (0) - 固体 (1)", state) { state = it }
        HapticSlider("柔らかい (0) - 硬い (1)", hardness) { hardness = it }
        HapticSlider("重い (0) - 軽い (1)", weight) { weight = it }
        HapticSlider("サラサラ (0) - ネバネバ (1)", viscosity) { viscosity = it }

        Button(
            onClick = {
                isPlaying = !isPlaying
                if (isPlaying) hapticEngine.start() else hapticEngine.stop()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isPlaying) Color(0xFFE53935) else Color(0xFF0070CC))
        ) {
            Text(if (isPlaying) "Stop / 停止" else "Play / エンジン起動", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "DEBUG: $debugText",
            color = Color.Yellow,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun HapticSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color(0xFF66B2FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f
        )
    }
}
