package com.jlxc.amapvoiceassistant

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.getEndpointConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getModelConfig
import kotlin.concurrent.thread

private const val TAG = "AMapVoiceAssistant"
private const val REQ_RECORD_AUDIO = 200

class MainActivity : Activity() {
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var manualInput: EditText
    private lateinit var recordButton: Button
    private lateinit var navButton: Button

    private var recognizer: OnlineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @Volatile
    private var isRecording = false

    @Volatile
    private var latestText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        ensureAudioPermission()

        thread(start = true) {
            initOfflineRecognizer()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 28, 48, 28)
            setBackgroundColor(Color.rgb(16, 24, 32))
        }

        titleText = TextView(this).apply {
            text = "离线语音导航助手"
            setTextColor(Color.WHITE)
            textSize = 34f
            gravity = Gravity.CENTER
        }
        root.addView(titleText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        statusText = TextView(this).apply {
            text = "正在初始化离线识别模型，请稍等..."
            setTextColor(Color.rgb(160, 230, 225))
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 10)
        }
        root.addView(statusText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        resultText = TextView(this).apply {
            text = "可以说：导航到萍乡北站 / 我要去润达国际"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }
        root.addView(resultText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        manualInput = EditText(this).apply {
            hint = "也可以手动输入目的地"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 22f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(135, 155, 165))
            setPadding(20, 10, 20, 10)
        }
        root.addView(manualInput, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 0)
        }

        recordButton = Button(this).apply {
            text = "开始离线识别"
            textSize = 22f
            isEnabled = false
            setOnClickListener {
                if (isRecording) stopRecordingAndNavigate() else startRecording()
            }
        }
        row.addView(recordButton, LinearLayout.LayoutParams(0, 72, 1f))

        navButton = Button(this).apply {
            text = "手动导航"
            textSize = 22f
            setOnClickListener {
                val dest = cleanDestination(manualInput.text.toString())
                if (dest.isBlank()) {
                    showStatus("请先输入目的地")
                } else {
                    navigateToKeyword(dest)
                }
            }
        }
        val navParams = LinearLayout.LayoutParams(0, 72, 1f)
        navParams.setMargins(20, 0, 0, 0)
        row.addView(navButton, navParams)

        root.addView(row, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val note = TextView(this).apply {
            text = "本版使用 sherpa-onnx Paraformer 中英双语流式模型，本地离线识别。"
            setTextColor(Color.rgb(130, 150, 160))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 0)
        }
        root.addView(note, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        setContentView(root)
    }

    private fun initOfflineRecognizer() {
        try {
            showStatusOnUi("正在加载 Paraformer 离线模型...")

            val modelType = 5 // sherpa-onnx-streaming-paraformer-bilingual-zh-en
            val modelConfig = getModelConfig(modelType)
                ?: throw IllegalStateException("无法获取 sherpa-onnx model config type=$modelType")

            val config = OnlineRecognizerConfig(
                featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
                modelConfig = modelConfig,
                endpointConfig = getEndpointConfig(),
                enableEndpoint = true,
            )

            recognizer = OnlineRecognizer(
                assetManager = application.assets,
                config = config,
            )

            runOnUiThread {
                recordButton.isEnabled = true
                showStatus("模型加载完成。点击按钮后说目的地，再点击停止。")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "initOfflineRecognizer failed", e)
            runOnUiThread {
                recordButton.isEnabled = false
                showStatus("离线模型加载失败：" + (e.message ?: e.javaClass.simpleName))
                Toast.makeText(this, "模型加载失败，请查看 logcat", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun ensureAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ensureAudioPermission()
            return
        }

        val rec = recognizer
        if (rec == null) {
            showStatus("模型还没加载完成")
            return
        }

        if (!initMicrophone()) {
            showStatus("麦克风初始化失败")
            return
        }

        latestText = ""
        manualInput.setText("")
        resultText.text = "正在听..."
        showStatus("正在离线识别，说完后再点一次停止")
        recordButton.text = "停止并导航"

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = thread(start = true) {
            processSamples(rec)
        }
    }

    private fun stopRecordingAndNavigate() {
        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null
        recordButton.text = "开始离线识别"

        val dest = cleanDestination(latestText)
        runOnUiThread {
            manualInput.setText(dest)
            if (dest.isBlank()) {
                showStatus("没有提取到目的地，可以手动输入")
            } else {
                showStatus("识别目的地：$dest，正在调起高德")
                navigateToKeyword(dest)
            }
        }
    }

    private fun processSamples(recognizer: OnlineRecognizer) {
        val stream = recognizer.createStream()
        val intervalSeconds = 0.1
        val bufferSize = (intervalSeconds * sampleRateInHz).toInt()
        val buffer = ShortArray(bufferSize)

        try {
            while (isRecording) {
                val ret = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (ret > 0) {
                    val samples = FloatArray(ret) { i -> buffer[i] / 32768.0f }
                    stream.acceptWaveform(samples, sampleRate = sampleRateInHz)

                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }

                    val isEndpoint = recognizer.isEndpoint(stream)
                    var text = recognizer.getResult(stream).text

                    // Paraformer 流式模型在端点处补一点尾部静音，避免最后一个字漏识别
                    if (isEndpoint && recognizer.config.modelConfig.paraformer.encoder.isNotBlank()) {
                        val tailPaddings = FloatArray((0.8 * sampleRateInHz).toInt())
                        stream.acceptWaveform(tailPaddings, sampleRate = sampleRateInHz)
                        while (recognizer.isReady(stream)) {
                            recognizer.decode(stream)
                        }
                        text = recognizer.getResult(stream).text
                    }

                    if (text.isNotBlank()) {
                        latestText = text
                        runOnUiThread {
                            resultText.text = "识别：$text"
                        }
                    }

                    if (isEndpoint) {
                        recognizer.reset(stream)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "processSamples failed", e)
            runOnUiThread {
                showStatus("识别过程出错：" + (e.message ?: e.javaClass.simpleName))
            }
        } finally {
            stream.release()
        }
    }

    private fun initMicrophone(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ensureAudioPermission()
            return false
        }

        val minBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        if (minBytes <= 0) return false

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            minBytes * 2
        )

        return audioRecord?.state == AudioRecord.STATE_INITIALIZED
    }

    private fun cleanDestination(raw: String?): String {
        if (raw == null) return ""

        var s = raw.trim()

        val removeWords = arrayOf(
            "小初音", "初音", "高德地图", "高德",
            "请帮我", "帮我", "麻烦",
            "导航到", "导航去", "导航", "带我去", "我要去", "我想去",
            "开车去", "去一下", "我去", "去"
        )

        for (word in removeWords) {
            s = s.replace(word, "")
        }

        return s.replace("。", "")
            .replace("，", "")
            .replace(",", "")
            .replace(".", "")
            .replace("！", "")
            .replace("!", "")
            .replace("？", "")
            .replace("?", "")
            .replace(" ", "")
            .trim()
    }

    private fun navigateToKeyword(destination: String) {
        val dest = destination.trim()
        if (dest.isBlank()) {
            showStatus("目的地为空")
            return
        }

        val uri = "androidauto://keywordNavi" +
            "?sourceApplication=${Uri.encode("高德地图")}" +
            "&keywords=${Uri.encode(dest)}" +
            "&style=2"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse(uri)
        }

        try {
            startActivity(intent)
            showStatus("已发送到高德车机版：$dest")
        } catch (e: ActivityNotFoundException) {
            showStatus("没有找到可处理 androidauto:// 的高德车机版")
            Toast.makeText(this, "未找到高德车机版", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            showStatus("调起高德失败：" + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun showStatus(text: String) {
        statusText.text = text
    }

    private fun showStatusOnUi(text: String) {
        runOnUiThread { showStatus(text) }
    }

    override fun onDestroy() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null

        try {
            recognizer?.release()
        } catch (_: Throwable) {
        }
        recognizer = null

        super.onDestroy()
    }
}
