package com.doubao.helper

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.doubao.helper.service.FloatingWindowService
import com.doubao.helper.util.PermissionChecker
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvRecordAudioStatus: TextView
    private lateinit var btnStartHelper: MaterialButton
    private lateinit var seekBarFontSize: SeekBar
    private lateinit var tvFontSizeLabel: TextView
    private lateinit var seekBarStandbyTimeout: SeekBar
    private lateinit var tvStandbyTimeoutLabel: TextView
    private lateinit var etWakeupWord: TextInputEditText
    private lateinit var btnSaveWakeupWord: MaterialButton
    private lateinit var tvMicConfigStatus: TextView
    private lateinit var btnSelectMicButton: MaterialButton

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
    }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermissionStatus()
        if (!granted) {
            Toast.makeText(this, "录音权限被拒绝，唤醒词功能将不可用", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        setContentView(R.layout.activity_main)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvRecordAudioStatus = findViewById(R.id.tvRecordAudioStatus)
        btnStartHelper = findViewById(R.id.btnStartHelper)
        seekBarFontSize = findViewById(R.id.seekBarFontSize)
        tvFontSizeLabel = findViewById(R.id.tvFontSizeLabel)
        seekBarStandbyTimeout = findViewById(R.id.seekBarStandbyTimeout)
        tvStandbyTimeoutLabel = findViewById(R.id.tvStandbyTimeoutLabel)
        etWakeupWord = findViewById(R.id.etWakeupWord)
        btnSaveWakeupWord = findViewById(R.id.btnSaveWakeupWord)
        tvMicConfigStatus = findViewById(R.id.tvMicConfigStatus)
        btnSelectMicButton = findViewById(R.id.btnSelectMicButton)

        findViewById<MaterialButton>(R.id.btnOverlayPermission).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<MaterialButton>(R.id.btnAccessibilityPermission).setOnClickListener {
            requestAccessibilityPermission()
        }

        findViewById<MaterialButton>(R.id.btnRecordAudioPermission).setOnClickListener {
            requestRecordAudioPermission()
        }

        btnStartHelper.setOnClickListener {
            startHelper()
        }

        // 字体大小配置
        val repository = (application as App).chatRepository
        val currentSize = repository.getFontSize(this)
        updateFontSizeLabel(currentSize)
        seekBarFontSize.progress = ((currentSize - 10f) * 10).toInt()

        seekBarFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sizeSp = 10f + progress / 10f
                updateFontSizeLabel(sizeSp)
                if (fromUser) {
                    repository.setFontSize(this@MainActivity, sizeSp)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 待机超时配置
        val currentTimeout = repository.getStandbyTimeout(this)
        updateStandbyTimeoutLabel(currentTimeout)
        seekBarStandbyTimeout.progress = currentTimeout

        seekBarStandbyTimeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateStandbyTimeoutLabel(progress)
                if (fromUser) {
                    repository.setStandbyTimeout(this@MainActivity, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 唤醒词配置
        val currentWakeupWord = repository.getWakeupWord(this)
        etWakeupWord.setText(currentWakeupWord)

        btnSaveWakeupWord.setOnClickListener {
            val word = etWakeupWord.text.toString().trim()
            if (word.isEmpty()) {
                Toast.makeText(this, "唤醒词不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            repository.setWakeupWord(this, word)
            Toast.makeText(this, "唤醒词已保存: $word", Toast.LENGTH_SHORT).show()
        }

        // 麦克风按钮配置
        updateMicConfigStatus()
        btnSelectMicButton.setOnClickListener {
            // 启动助手服务（如果还没启动），然后进入麦克风按钮选区模式
            val app = application as App
            app.selectionModeType = "mic"
            // 先启动服务
            val serviceIntent = Intent(this, FloatingWindowService::class.java)
            startForegroundService(serviceIntent)
            // 提示用户切换到豆包 App 后在悬浮球菜单里选择
            tvMicConfigStatus.text = "已启动助手，请点击悬浮球展开面板，点「选区调试」选择麦克风按钮"
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateMicConfigStatus()

        // 如果还没有录音权限，自动请求一次
        if (!PermissionChecker.hasRecordAudioPermission(this)) {
            requestRecordAudioPermission()
        }
    }

    private fun updateFontSizeLabel(sizeSp: Float) {
        tvFontSizeLabel.text = "浮窗字体大小: ${String.format("%.0f", sizeSp)}sp"
    }

    private fun updateStandbyTimeoutLabel(minutes: Int) {
        tvStandbyTimeoutLabel.text = "待机超时: ${minutes}分钟"
    }

    private fun updateMicConfigStatus() {
        val app = application as App
        val config = app.callButtonConfig
        tvMicConfigStatus.text = if (config != null) {
            "通话按钮: 挂断(${config.hangUpX},${config.hangUpY}) 拨通(${config.callX},${config.callY})"
        } else {
            "通话按钮: 未配置"
        }
    }

    private fun updatePermissionStatus() {
        val hasOverlay = PermissionChecker.hasOverlayPermission(this)
        val hasAccessibility = PermissionChecker.hasAccessibilityPermission(this)
        val hasRecordAudio = PermissionChecker.hasRecordAudioPermission(this)

        tvOverlayStatus.text = getString(
            if (hasOverlay) R.string.permission_granted else R.string.permission_not_granted
        )
        tvAccessibilityStatus.text = getString(
            if (hasAccessibility) R.string.permission_granted else R.string.permission_not_granted
        )
        tvRecordAudioStatus.text = getString(
            if (hasRecordAudio) R.string.permission_granted else R.string.permission_not_granted
        )

        // 更新录音权限按钮文字
        val btnRecordAudio = findViewById<MaterialButton>(R.id.btnRecordAudioPermission)
        if (hasRecordAudio) {
            btnRecordAudio.text = getString(R.string.permission_granted)
            btnRecordAudio.isEnabled = false
        } else {
            btnRecordAudio.text = getString(R.string.permission_request)
            btnRecordAudio.isEnabled = true
        }

        // 启动按钮只需悬浮窗+无障碍（录音是可选的）
        btnStartHelper.isEnabled = hasOverlay && hasAccessibility
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun requestRecordAudioPermission() {
        if (PermissionChecker.hasRecordAudioPermission(this)) return
        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startHelper() {
        val intent = Intent(this, FloatingWindowService::class.java)
        startForegroundService(intent)
        moveTaskToBack(true)
    }
}
