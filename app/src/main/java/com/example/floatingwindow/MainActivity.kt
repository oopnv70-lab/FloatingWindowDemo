package com.example.floatingwindow

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.floatingwindow.databinding.ActivityMainBinding

/**
 * 主界面：负责检查/申请悬浮窗权限，并启动悬浮窗前台服务。
 *
 * 「软件打开就会有悬浮窗」的流程：
 *  1. onResume 中检查权限；
 *  2. 若未授权 -> 弹提示，用户点击后跳系统设置；
 *  3. 已授权 -> 直接启动 FloatingWindowService，显示悬浮窗。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRequest.setOnClickListener {
            if (OverlayPermission.canDrawOverlays(this)) {
                startFloatingWindow()
            } else {
                OverlayPermission.requestOverlayPermission(this)
            }
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, FloatingWindowService::class.java))
            Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
            updateUi()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        // 回到前台后若已授权，自动弹出悬浮窗
        if (OverlayPermission.canDrawOverlays(this)) {
            startFloatingWindow()
        }
    }

    /**
     * 启动悬浮窗前台服务。
     * Android 8.0+ 必须用 startForegroundService，否则后台启动会抛异常。
     */
    private fun startFloatingWindow() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateUi() {
        val granted = OverlayPermission.canDrawOverlays(this)
        binding.tvStatus.text = if (granted) {
            "悬浮窗权限：已授予 ✅"
        } else {
            "悬浮窗权限：未授予 ❌\n点击下方按钮前往系统设置开启"
        }
        binding.btnRequest.text = if (granted) "显示 / 重启悬浮窗" else "申请悬浮窗权限"
    }
}