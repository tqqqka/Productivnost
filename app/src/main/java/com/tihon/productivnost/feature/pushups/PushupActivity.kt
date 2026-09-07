package com.tihon.productivnost.feature.pushups

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Size
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.service.Sfx
import com.tihon.productivnost.ui.components.GhostButton
import com.tihon.productivnost.ui.components.PrimaryButton
import com.tihon.productivnost.ui.components.ProgressRing
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.ui.theme.ProductivnostTheme
import com.tihon.productivnost.util.Apps
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Продление приложения отжиманиями: фронтальная камера считает повторения,
 * каждое даёт 2.5 минуты (значение настраивается).
 */
class PushupActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PKG = "pkg"

        fun intent(context: Context, pkg: String?): Intent =
            Intent(context, PushupActivity::class.java)
                .putExtra(EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val target = intent.getStringExtra(EXTRA_PKG)
        setContent {
            ProductivnostTheme {
                PushupScreen(
                    targetPackage = target,
                    onDone = { reps ->
                        applyReward(target, reps)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun applyReward(pkg: String?, reps: Int) {
        if (reps <= 0) return
        val perRep = Store.settings.value.minutesPerPushup
        val ms = (reps * perRep * 60_000.0).toLong()
        Store.updateUsage { it.copy(pushupsToday = it.pushupsToday + reps) }
        if (pkg != null) {
            Store.addBonus(pkg, ms)
            runCatching {
                packageManager.getLaunchIntentForPackage(pkg)?.let {
                    startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun PushupScreen(
    targetPackage: String?,
    onDone: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    var reps by remember { mutableIntStateOf(0) }
    var depth by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    var popped by remember { mutableStateOf(false) }

    val perRep = Store.settings.value.minutesPerPushup
    val label = remember(targetPackage) { targetPackage?.let { Apps.label(context, it) } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101816), Color(0xFF060A09)))),
    ) {
        if (granted) {
            CameraLayer(
                lifecycleOwner = lifecycleOwner,
                onRep = { r -> reps = r; Sfx.play(context, Sfx.GROWN, 0.35f); Sfx.vibrate(context, longArrayOf(0, 22)) },
                onFrame = { d, v -> depth = d; visible = v },
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xE6070B0A), 0.42f to Color(0xB3070B0A), 1f to Color(0xF2070B0A),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (label != null) "Продление · $label" else "Отжимания",
                style = MaterialTheme.typography.labelMedium,
                color = P.textFaint,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Одно отжимание = $perRep мин",
                style = MaterialTheme.typography.bodyMedium,
                color = P.textDim,
            )

            Spacer(Modifier.weight(1f))

            LaunchedEffect(reps) {
                if (reps > 0) {
                    popped = true
                    kotlinx.coroutines.delay(200)
                    popped = false
                }
            }
            val scale by animateFloatAsState(
                if (popped) 1.16f else 1f,
                spring(Spring.DampingRatioMediumBouncy), label = "pop",
            )
            ProgressRing(
                progress = depth,
                modifier = Modifier.size(230.dp),
                color = P.leaf,
                stroke = 10.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$reps",
                        style = MaterialTheme.typography.displayLarge,
                        color = P.text,
                        modifier = Modifier.scale(scale),
                    )
                    Text(
                        "+${((reps * perRep * 10).roundToInt() / 10.0)} мин",
                        style = MaterialTheme.typography.titleMedium,
                        color = P.leaf,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            AnimatedVisibility(!visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
                Text(
                    if (granted) "Положи телефон перед собой так, чтобы в кадр попали плечи и руки"
                    else "Нужен доступ к камере",
                    style = MaterialTheme.typography.bodyMedium,
                    color = P.textFaint,
                    textAlign = TextAlign.Center,
                )
            }
            AnimatedVisibility(visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(P.leaf),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Вижу тебя", style = MaterialTheme.typography.bodyMedium, color = P.textDim)
                }
            }

            Spacer(Modifier.weight(1f))

            PrimaryButton(
                if (reps > 0) "Готово · +${((reps * perRep * 10).roundToInt() / 10.0)} мин" else "Готово",
                Modifier.fillMaxWidth(),
                onClick = { onDone(reps) },
            )
            Spacer(Modifier.height(10.dp))
            GhostButton("Отмена", Modifier.fillMaxWidth(), onClick = onCancel)
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraLayer(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onRep: (Int) -> Unit,
    onFrame: (Float, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val counter = remember { PushupCounter() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build(),
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { detector.close() }
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.55f),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(Size(480, 640))
                        .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        val media = proxy.image
                        if (media == null) {
                            proxy.close()
                        } else {
                            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            detector.process(image)
                                .addOnSuccessListener { pose ->
                                    val now = System.currentTimeMillis()
                                    val counted = counter.accept(pose, now)
                                    onFrame(counter.depth, counter.visible)
                                    if (counted) onRep(counter.reps)
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview, analysis,
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
