package com.doubao.helper.wakeup

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 唤醒词检测器：使用 sherpa-onnx KeywordSpotter 持续监听麦克风。
 * 完全对齐 hiai 项目的实现方式。
 */
class WakeupWordDetector(private val context: Context) {

    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRunning = false
    private var thread: Thread? = null

    /** 唤醒词，默认"小豆小豆" */
    var wakeupWord: String = "小豆小豆"

    /** 检测到唤醒词时触发 */
    var onWakeup: (() -> Unit)? = null

    /** 启动失败时触发 */
    var onError: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "WakeupWord"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "kws-model"
        private const val ENCODER_MODEL = "encoder-epoch-13-avg-2-chunk-16-left-64.onnx"
        private const val DECODER_MODEL = "decoder-epoch-13-avg-2-chunk-16-left-64.onnx"
        private const val JOINER_MODEL = "joiner-epoch-13-avg-2-chunk-16-left-64.onnx"
        private const val TOKENS_FILE = "tokens.txt"
    }

    /**
     * 初始化检测器
     */
    fun init(): Boolean {
        try {
            val modelPath = copyModelFiles()
            if (modelPath == null) {
                Log.e(TAG, "Failed to copy model files")
                return false
            }

            val keywordsPath = generateKeywordsFile()
            if (keywordsPath == null) {
                Log.e(TAG, "Failed to generate keywords file")
                return false
            }

            // 验证 token
            if (!validateKeywords(modelPath, keywordsPath)) {
                Log.e(TAG, "Keyword token validation failed")
                return false
            }

            // 完全对齐 hiai 项目的配置
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(
                    featureDim = 80
                ),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$modelPath/$ENCODER_MODEL",
                        decoder = "$modelPath/$DECODER_MODEL",
                        joiner = "$modelPath/$JOINER_MODEL"
                    ),
                    tokens = "$modelPath/$TOKENS_FILE",
                    modelType = "zipformer2"
                ),
                keywordsFile = keywordsPath,
                keywordsScore = 1.5f,
                keywordsThreshold = 0.15f,
                numTrailingBlanks = 2
            )

            spotter = KeywordSpotter(config = config)
            Log.i(TAG, "WakeupWordDetector initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WakeupWordDetector", e)
            return false
        }
    }

    fun startListening() {
        if (spotter == null) {
            // 先初始化
            if (!init()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError?.invoke("唤醒词检测器初始化失败")
                }
                return
            }
        }

        if (isRunning) {
            Log.w(TAG, "Already running")
            return
        }

        try {
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, AudioFormat.ENCODING_PCM_16BIT)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord = null
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError?.invoke("录音初始化失败，请检查录音权限")
                }
                return
            }

            // 创建检测流（与 hiai 一致）
            stream = spotter!!.createStream()

            audioRecord?.startRecording()
            isRunning = true

            thread = Thread {
                detectionLoop()
            }
            thread?.start()

            Log.i(TAG, "WakeupWordDetector started, word: $wakeupWord")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WakeupWordDetector", e)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onError?.invoke("唤醒词检测启动失败: ${e.message}")
            }
        }
    }

    fun stopListening() {
        isRunning = false

        thread?.interrupt()
        thread = null

        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null

        // 重置流（与 hiai 一致）
        stream?.let { spotter?.reset(it) }

        Log.i(TAG, "WakeupWordDetector stopped")
    }

    fun release() {
        stopListening()
        stream?.release()
        stream = null
        spotter?.release()
        spotter = null
        Log.i(TAG, "WakeupWordDetector released")
    }

    private fun detectionLoop() {
        val samplesPerRead = SAMPLE_RATE / 10  // 1600 samples per 100ms
        val buffer = ShortArray(samplesPerRead)

        while (isRunning) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read <= 0) continue

            val samples = FloatArray(read) { i ->
                buffer[i] / 32768.0f
            }

            stream?.let { s ->
                s.acceptWaveform(samples, SAMPLE_RATE)

                // 循环 decode 直到不 ready（与 hiai 一致）
                while (spotter?.isReady(s) == true) {
                    spotter?.decode(s)
                }

                // 检查是否检测到关键词
                val result = spotter?.getResult(s)
                if (result != null && result.keyword.isNotEmpty()) {
                    Log.i(TAG, "🎉 Detected keyword: ${result.keyword}")

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onWakeup?.invoke()
                    }

                    // 重置流以检测下一个关键词（与 hiai 一致）
                    spotter?.reset(s)
                }
            }
        }
    }

    private fun validateKeywords(modelPath: String, keywordsPath: String): Boolean {
        val tokensFile = File(modelPath, TOKENS_FILE)
        if (!tokensFile.exists()) return false

        val validTokens = tokensFile.readLines()
            .mapNotNull { line ->
                val parts = line.trim().split(" ")
                if (parts.isNotEmpty()) parts[0] else null
            }
            .toSet()

        val keywordsContent = File(keywordsPath).readText().trim()
        val atIndex = keywordsContent.lastIndexOf("@")
        if (atIndex < 0) return false

        val tokenPart = keywordsContent.substring(0, atIndex).trim()
        val tokens = tokenPart.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        for (token in tokens) {
            if (token !in validTokens) {
                Log.e(TAG, "Token \"$token\" not in tokens.txt")
                return false
            }
        }

        Log.i(TAG, "Keyword token validation passed: ${tokens.size} tokens")
        return true
    }

    private fun copyModelFiles(): String? {
        val modelDir = File(context.filesDir, MODEL_DIR)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        try {
            for (fileName in listOf(ENCODER_MODEL, DECODER_MODEL, JOINER_MODEL, TOKENS_FILE)) {
                val destFile = File(modelDir, fileName)
                // 始终复制最新版本（模型可能更新了）
                context.assets.open("$MODEL_DIR/$fileName").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return modelDir.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy model files", e)
            return null
        }
    }

    private fun generateKeywordsFile(): String? {
        try {
            val keywordTokens = PinyinTokenizer.toKeywordTokens(wakeupWord)
            if (keywordTokens.isEmpty()) {
                Log.e(TAG, "Cannot convert wakeup word to pinyin tokens: $wakeupWord")
                return null
            }

            val keywordsFile = File(context.filesDir, "keywords.txt")
            keywordsFile.writeText("$keywordTokens\n")
            Log.i(TAG, "Keywords file content: $keywordTokens")
            return keywordsFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate keywords file", e)
            return null
        }
    }
}
