package com.example.floatingwindow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * 悬浮窗前台服务。
 *
 * 设计要点（跨版本兼容）：
 *  - 窗口类型：API 26+ 使用 TYPE_APPLICATION_OVERLAY；低版本回退 TYPE_PHONE。
 *  - Android 10+ 悬浮窗默认不可获取焦点，勾选 FLAG_NOT_FOCUSABLE 避免输入法/焦点异常。
 *  - 通过前台服务常驻，规避 Android 12+ 后台启动限制导致被系统回收。
 *  - 支持手指拖动移动悬浮窗位置。
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务被系统杀死后尝试重建悬浮窗
        if (floatingView == null) {
            showFloatingWindow()
        }
        return START_STICKY
    }

    /**
     * 创建并添加悬浮窗。
     */
    private fun showFloatingWindow() {
        if (floatingView != null) return
        if (!OverlayPermission.canDrawOverlays(this)) {
            Toast.makeText(this, "未获得悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_window, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            // FLAG_NOT_FOCUSABLE：不抢焦点，避免输入法与焦点问题（Android 10+ 必需）
            // FLAG_LAYOUT_NO_LIMITS：允许超出屏幕边界的布局
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 300
        }

        // 拖动逻辑
        floatingView?.setOnTouchListener(createDragListener())

        // 关闭按钮
        floatingView?.findViewById<View>(R.id.btnClose)?.setOnClickListener {
            stopSelf()
        }

        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            // 部分 ROM（MIUI/ColorOS）即使有权限也可能拦截，做兜底
            Toast.makeText(this, "悬浮窗创建失败: ${e.message}", Toast.LENGTH_LONG).show()
            floatingView = null
            stopSelf()
        }
    }

    /**
     * 根据 SDK 版本返回合适的窗口类型。
     *  - API 26 (Android 8.0) 起：TYPE_APPLICATION_OVERLAY（推荐）
     *  - 更低版本：TYPE_PHONE（已废弃但仍可用）
     */
    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    /**
     * 拖动监听：按下记录初始坐标，移动时更新 windowManager 布局参数。
     */
    private fun createDragListener(): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    // 超过阈值才判定为拖动，避免误触
                    if (!isDragging && (abs(dx) > 8 || abs(dy) > 8)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        floatingView?.let {
                            windowManager.updateViewLayout(it, layoutParams)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging
                else -> false
            }
        }
    }

    /**
     * 前台服务常驻通知（Android 8.0+ 需通知渠道）。
     */
    private fun startForegroundNotification() {
        val channelId = "floating_window_channel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持悬浮窗运行"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("悬浮窗运行中")
            .setContentText("点击返回应用")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+：声明前台服务类型
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        floatingView = null
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}