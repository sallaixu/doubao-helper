package com.doubao.helper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.doubao.helper.service.FloatingWindowService
import com.doubao.helper.util.PermissionChecker
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnStartHelper: MaterialButton

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnStartHelper = findViewById(R.id.btnStartHelper)

        findViewById<MaterialButton>(R.id.btnOverlayPermission).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<MaterialButton>(R.id.btnAccessibilityPermission).setOnClickListener {
            requestAccessibilityPermission()
        }

        btnStartHelper.setOnClickListener {
            startHelper()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val hasOverlay = PermissionChecker.hasOverlayPermission(this)
        val hasAccessibility = PermissionChecker.hasAccessibilityPermission(this)

        tvOverlayStatus.text = getString(
            if (hasOverlay) R.string.permission_granted else R.string.permission_not_granted
        )
        tvAccessibilityStatus.text = getString(
            if (hasAccessibility) R.string.permission_granted else R.string.permission_not_granted
        )

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

    private fun startHelper() {
        val intent = Intent(this, FloatingWindowService::class.java)
        startForegroundService(intent)
        moveTaskToBack(true)
    }
}
