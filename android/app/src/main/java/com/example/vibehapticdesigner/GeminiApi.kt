package com.example.vibehapticdesigner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class HapticParams(
    val granularity: Float,
    val roughness: Float,
    val state: Float,
    val hardness: Float,
    val weight: Float,
    val viscosity: Float
)

object GeminiApi {
    private const val SYSTEM_INSTRUCTION = """
あなたはプロのハプティクス（触覚）デザイナーです。ユーザーの入力した「触覚のイメージ」を分析し、以下の6つのパラメータ（0.0〜1.0の浮動小数点数）に変換して出力してください。
- granularity: 滑らか(0.0) 〜 ツブツブのインパルス(1.0)
- roughness: すべすべ(0.0) 〜 微細なザラザラノイズ(1.0)
- state: 液体・連続(0.0) 〜 固体・不連続な摩擦(1.0)
- hardness: 柔らかい(0.0) 〜 金属的に硬い(1.0)
- weight: 重い・低音(0.0) 〜 軽い・高音(1.0)
- viscosity: サラサラ(0.0) 〜 泥のようなネバネバ(1.0)
必ず上記6つのキーだけを持つJSONオブジェクトのみを返してください。
    """

    suspend fun generateHaptics(apiKey: String, prompt: String): Result<HapticParams> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey.trim()}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            // Build request JSON
            val requestBody = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", org.json.JSONArray().put(JSONObject().put("text", SYSTEM_INSTRUCTION.trim())))
                })
                put("contents", org.json.JSONArray().put(JSONObject().apply {
                    put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

            if (conn.responseCode in 200..299) {
                val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val root = JSONObject(responseStr)
                val text = root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val paramsJson = JSONObject(text)
                
                val params = HapticParams(
                    granularity = paramsJson.getDouble("granularity").toFloat(),
                    roughness = paramsJson.getDouble("roughness").toFloat(),
                    state = paramsJson.getDouble("state").toFloat(),
                    hardness = paramsJson.getDouble("hardness").toFloat(),
                    weight = paramsJson.getDouble("weight").toFloat(),
                    viscosity = paramsJson.getDouble("viscosity").toFloat()
                )
                Result.success(params)
            } else {
                val errorStr = BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() }
                Result.failure(Exception("HTTP ${conn.responseCode}: $errorStr"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
