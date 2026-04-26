package com.sdkdev.sumbingcompanion.ui.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sdkdev.sumbingcompanion.core.*
import com.sdkdev.sumbingcompanion.data.SettingsRepository
import com.sdkdev.sumbingcompanion.ui.theme.SumbingTheme
import com.sdkdev.sumbingcompanion.viewmodel.MonitorViewModel
import com.sdkdev.sumbingcompanion.viewmodel.TrainingViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

class FloatingWindowService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {
    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    
    private lateinit var viewModel: MonitorViewModel
    private lateinit var trainingViewModel: TrainingViewModel
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var autoCaptureManager: AutoCaptureManager
    private lateinit var detectionEngine: DetectionEngine
    private val _viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var latestBitmap: Bitmap? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection = null
        }
    }

    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var isExpanded by mutableStateOf(false)
    private var overlayX by mutableIntStateOf(100)
    private var overlayY by mutableIntStateOf(100)
    private var currentParams: WindowManager.LayoutParams? = null

    private var contentSize = IntSize.Zero
    private var displayMetrics = DisplayMetrics()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        
        settingsRepo = SettingsRepository(this)
        detectionEngine = DetectionEngine(this)
        val dataCollectionManager = DataCollectionManager(this)
        autoCaptureManager = AutoCaptureManager(this, settingsRepo, dataCollectionManager, detectionEngine)
        
        val monitorViewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(settingsRepo) as T
            }
        }
        viewModel = ViewModelProvider(this, monitorViewModelFactory)[MonitorViewModel::class.java]
        
        val trainingViewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return TrainingViewModel(settingsRepo) as T
            }
        }
        trainingViewModel = ViewModelProvider(this, trainingViewModelFactory)[TrainingViewModel::class.java]

        setupNotification()
        
        lifecycleScope.launch {
            val savedX = settingsRepo.bubbleX.first() ?: 100f
            val savedY = settingsRepo.bubbleY.first() ?: 100f
            overlayX = savedX.toInt()
            overlayY = savedY.toInt()
            setupOverlay()
        }
        
        lifecycleScope.launch {
            autoCaptureManager.events.collect { event -> trainingViewModel.onAutoCaptureEvent(event) }
        }

        lifecycleScope.launch {
            settingsRepo.autoCaptureConfigFlow.collect { config ->
                if (config.enabled) {
                    autoCaptureManager.start(viewModel.uiState) { captureScreen() ?: latestBitmap }
                } else {
                    autoCaptureManager.stop()
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val bitmap = captureScreen()
                if (bitmap != null) {
                    val oldBitmap = latestBitmap
                    latestBitmap = bitmap
                    oldBitmap?.recycle()
                }
                val isAutoCaptureEnabled = settingsRepo.autoCaptureConfigFlow.first().enabled
                val isMonitoring = viewModel.isMonitoring.value
                if ((isMonitoring || isAutoCaptureEnabled) && latestBitmap != null) {
                    val region = settingsRepo.getRegion()
                    val result = detectionEngine.detect(latestBitmap!!, region)
                    if (result != null) {
                        withContext(Dispatchers.Main) {
                            if (isMonitoring) viewModel.onDetection(result.first, result.second)
                            else viewModel.onTestDetection(result.first, result.second)
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("projection_data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra("projection_data")
        }
        if (resultData != null) setupMediaProjection(resultData)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun setupMediaProjection(data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(android.app.Activity.RESULT_OK, data)
        mediaProjection?.registerCallback(projectionCallback, null)
        imageReader = ImageReader.newInstance(displayMetrics.widthPixels, displayMetrics.heightPixels, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay("SumbingCapture", displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null)
    }

    private fun captureScreen(): Bitmap? {
        val reader = imageReader ?: return null
        val image = try { reader.acquireLatestImage() ?: reader.acquireNextImage() } catch (e: Exception) { null } ?: return null
        try {
            val planes = image.planes
            if (planes.isEmpty()) return null
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            return if (rowPadding > 0) {
                val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                bitmap.recycle()
                cleanBitmap
            } else bitmap
        } catch (e: Exception) { return null } finally { image.close() }
    }

    private fun setupNotification() {
        val channelId = "sumbing_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Sumbing Monitor", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Sumbing Mobile Running")
            .setContentText("Monitoring screen for weather...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(1, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            
            setOnTouchListener { _, event ->
                if (isExpanded && event.action == MotionEvent.ACTION_OUTSIDE) {
                    toggleExpand(false)
                    return@setOnTouchListener true
                }
                false
            }

            setContent {
                SumbingTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    val isMonitoring by viewModel.isMonitoring.collectAsState()
                    val region by settingsRepo.regionFlow.collectAsState(CaptureRegion.DEFAULT)
                    val autoConfig by settingsRepo.autoCaptureConfigFlow.collectAsState(AutoCaptureConfig())

                    // Re-sync opacity and position to window level
                    LaunchedEffect(uiState.overlayAlpha, isExpanded) {
                        updateOverlayPosition()
                    }

                    Box(
                        modifier = Modifier
                            .wrapContentSize()
                            .onGloballyPositioned { 
                                val newSize = it.size
                                if (contentSize != newSize) {
                                    contentSize = newSize
                                    if (isExpanded) updateOverlayPosition()
                                }
                            }
                    ) {
                        if (isExpanded) {
                            MiniPanelView(
                                uiState = uiState,
                                isMonitoring = isMonitoring,
                                trainingViewModel = trainingViewModel,
                                monitorViewModel = viewModel,
                                onStartStop = { viewModel.toggleMonitoring() },
                                onReset = { viewModel.resetToIdle() },
                                onTestRead = { 
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        Toast.makeText(this@FloatingWindowService, "Membaca layar...", Toast.LENGTH_SHORT).show()
                                        withContext(Dispatchers.IO) {
                                            val bitmap = captureScreen() ?: latestBitmap
                                            if (bitmap != null) {
                                                val currentRegion = CaptureRegion(region.x, region.y, region.w, region.h)
                                                val result = detectionEngine.detect(bitmap, currentRegion, skipSmoothing = true)
                                                val preview = detectionEngine.cropRegion(bitmap, currentRegion)
                                                if (result != null) withContext(Dispatchers.Main) { viewModel.onTestDetection(result.first, result.second, preview) }
                                            }
                                        }
                                    }
                                },
                                onClose = { toggleExpand(false) },
                                onExitService = { stopSelf() }
                            )
                        } else {
                            val activeWeather = if (uiState.state == WeatherState.WEATHER) WeatherClass.fromString(uiState.lastCuaca) else WeatherClass.fromString(uiState.lastPassive)
                            BubbleView(
                                state = uiState.state,
                                weather = activeWeather,
                                countdown = uiState.countdown,
                                weatherDur = uiState.weatherDur,
                                alpha = 1.0f, // Window manager handles alpha
                                regionUncalibrated = region.isEmpty,
                                isAutoCapturing = isMonitoring && autoConfig.enabled,
                                onToggle = { toggleExpand(true) },
                                onDrag = { dx, dy ->
                                    overlayX += dx.toInt()
                                    overlayY += dy.toInt()
                                    updateOverlayPosition()
                                },
                                onDragEnd = {
                                    lifecycleScope.launch { settingsRepo.saveBubblePosition(overlayX.toFloat(), overlayY.toFloat()) }
                                }
                            )
                        }
                    }
                }
            }
        }

        currentParams = getLayoutParams()
        windowManager.addView(composeView, currentParams)
    }

    private fun getLayoutParams(): WindowManager.LayoutParams {
        val flags = if (isExpanded) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        
        var targetX = overlayX
        var targetY = overlayY
        
        if (isExpanded && contentSize.width > 0) {
            val maxX = (displayMetrics.widthPixels - contentSize.width).coerceAtLeast(0)
            val maxY = (displayMetrics.heightPixels - contentSize.height).coerceAtLeast(0)
            targetX = targetX.coerceIn(0, maxX)
            targetY = targetY.coerceIn(0, maxY)
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetX
            y = targetY
            alpha = viewModel.uiState.value.overlayAlpha // Fix opacity not working
        }
    }

    private fun updateOverlayPosition() {
        if (!::composeView.isInitialized || composeView.windowToken == null) return
        val params = getLayoutParams()
        currentParams = params
        try { windowManager.updateViewLayout(composeView, params) } catch (e: Exception) {}
    }

    private fun toggleExpand(expand: Boolean) {
        if (isExpanded == expand) return
        isExpanded = expand
        updateOverlayPosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        mediaProjection?.unregisterCallback(projectionCallback)
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        if (::composeView.isInitialized) windowManager.removeView(composeView)
        _viewModelStore.clear()
    }

    override fun onBind(intent: Intent): IBinder? { super.onBind(intent); return null }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        virtualDisplay?.release()
        imageReader?.close()
        lifecycleScope.launch {
            delay(500)
            imageReader = ImageReader.newInstance(displayMetrics.widthPixels, displayMetrics.heightPixels, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay("SumbingCapture", displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null)
        }
    }
}
