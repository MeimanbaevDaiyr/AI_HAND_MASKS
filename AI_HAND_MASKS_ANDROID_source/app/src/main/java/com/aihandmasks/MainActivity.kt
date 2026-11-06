package com.aihandmasks

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var overlay: EffectsView
    private lateinit var status: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private var faceLandmarker: FaceLandmarker? = null
    private var handLandmarker: HandLandmarker? = null
    @Volatile private var mode = 1
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else status.text = "Разреши доступ к камере"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
             View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        val root = FrameLayout(this)
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        overlay = EffectsView(this)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 14)
            setBackgroundColor(0x99000000.toInt())
        }
        status = TextView(this).apply {
            text = "AI HAND MASKS"
            setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
        }
        panel.addView(status)
        val row1 = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val row2 = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val labels = listOf("NEON", "CYBER", "FIRE", "LIGHTNING", "MAGIC", "SNAKE", "GESTURE")
        labels.forEachIndexed { i, label ->
            val b = Button(this).apply {
                text = label
                textSize = 9f
                isAllCaps = false
                setPadding(3, 0, 3, 0)
                setOnClickListener {
                    mode = i + 1
                    overlay.mode = mode
                    status.text = "AI HAND MASKS • $label"
                }
            }
            (if (i < 4) row1 else row2).addView(b,
                LinearLayout.LayoutParams(0, 48, 1f))
        }
        panel.addView(row1); panel.addView(row2)
        val p = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        root.addView(panel, p)
        setContentView(root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        try {
            val base = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task").build()
            faceLandmarker = FaceLandmarker.createFromOptions(this,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(base).setRunningMode(RunningMode.IMAGE)
                    .setNumFaces(1).build())

            val handBase = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task").build()
            handLandmarker = HandLandmarker.createFromOptions(this,
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(handBase).setRunningMode(RunningMode.IMAGE)
                    .setNumHands(2).build())
        } catch (e: Exception) {
            status.text = "Не удалось загрузить модели: ${e.message}"
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val previewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(preview.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                try {
                    val bitmap = image.toBitmap()
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    val faces = faceLandmarker?.detect(mpImage)
                    val hands = handLandmarker?.detect(mpImage)
                    val facePoints = faces?.faceLandmarks()?.firstOrNull()
                        ?.map { PointF(it.x(), it.y()) } ?: emptyList()
                    val handPoints = hands?.landmarks()?.map { list ->
                        list.map { PointF(it.x(), it.y()) }
                    } ?: emptyList()
                    runOnUiThread { overlay.update(facePoints, handPoints) }
                    bitmap.recycle()
                } catch (_: Exception) {
                } finally { image.close() }
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA,
                    previewUseCase, analysis)
            } catch (e: Exception) {
                status.text = "Ошибка камеры: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        faceLandmarker?.close()
        handLandmarker?.close()
    }
}

class EffectsView(context: android.content.Context) : View(context) {
    @Volatile var mode = 1
    private var face: List<PointF> = emptyList()
    private var hands: List<List<PointF>> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snake = mutableListOf<PointF>()
    private var food = PointF(.5f, .5f)
    private var score = 0

    fun update(f: List<PointF>, h: List<List<PointF>>) {
        face = f; hands = h
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sx = width.toFloat(); val sy = height.toFloat()
        fun xy(p: PointF) = PointF(p.x * sx, p.y * sy)
        val palette = when (mode) {
            1 -> Color.CYAN
            2 -> Color.MAGENTA
            3 -> Color.rgb(255, 100, 0)
            4 -> Color.YELLOW
            5 -> Color.rgb(190, 70, 255)
            else -> Color.GREEN
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (mode == 2) 3f else 2f
        paint.color = palette
        paint.setShadowLayer(12f, 0f, 0f, palette)

        // Face mesh approximation: connect adjacent detected face landmarks.
        if (mode in 1..5 && face.size > 300) {
            val stride = if (mode == 2) 3 else 5
            for (i in 0 until face.size - stride step stride) {
                val a = xy(face[i]); val b = xy(face[i + stride])
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            }
            for (index in listOf(33, 263, 159, 386)) if (index < face.size) {
                val p = xy(face[index]); paint.style = Paint.Style.FILL
                canvas.drawCircle(p.x, p.y, 7f, paint); paint.style = Paint.Style.STROKE
            }
        }
        hands.forEach { hand ->
            if (hand.size >= 21) {
                val links = listOf(0 to 1,1 to 2,2 to 3,3 to 4,0 to 5,5 to 6,6 to 7,7 to 8,
                    5 to 9,9 to 10,10 to 11,11 to 12,9 to 13,13 to 14,14 to 15,15 to 16,
                    13 to 17,17 to 18,18 to 19,19 to 20,0 to 17)
                paint.color = palette
                links.forEach { (a,b) ->
                    val p1=xy(hand[a]); val p2=xy(hand[b])
                    canvas.drawLine(p1.x,p1.y,p2.x,p2.y,paint)
                }
                paint.style=Paint.Style.FILL
                hand.forEach { val p=xy(it); canvas.drawCircle(p.x,p.y,4f,paint) }
                paint.style=Paint.Style.STROKE
                if (mode == 3 || mode == 4) {
                    val tip=xy(hand[8])
                    paint.color = if (mode == 3) Color.RED else Color.WHITE
                    canvas.drawCircle(tip.x,tip.y,16f,paint)
                }
            }
        }
        paint.clearShadowLayer()
        if (mode == 6) drawSnake(canvas, sx, sy)
        if (mode == 7 && hands.isNotEmpty() && hands[0].size > 8) {
            val tip=xy(hands[0][8])
            paint.color=Color.GREEN; paint.strokeWidth=5f
            canvas.drawCircle(tip.x,tip.y,18f,paint)
            if (hands[0].size > 4) {
                val thumb=xy(hands[0][4])
                canvas.drawLine(tip.x,tip.y,thumb.x,thumb.y,paint)
            }
        }
    }

    private fun drawSnake(c: Canvas, w: Float, h: Float) {
        if (snake.isEmpty()) for (i in 0 until 24) snake.add(PointF(.5f-i*.008f,.5f))
        if (hands.isNotEmpty() && hands[0].size > 8) {
            val t=hands[0][8]
            val head=snake.first()
            val dx=t.x-head.x; val dy=t.y-head.y
            val d=sqrt(dx*dx+dy*dy)
            if (d > .004f) snake.add(0, PointF((head.x+dx/d*.008f).coerceIn(.02f,.98f),
                (head.y+dy/d*.008f).coerceIn(.05f,.9f)))
            while (snake.size > 24 + score*5) snake.removeAt(snake.lastIndex)
        }
        val f=PointF(food.x*w,food.y*h)
        paint.color=Color.YELLOW; paint.style=Paint.Style.FILL
        c.drawCircle(f.x,f.y,12f,paint)
        if (snake.isNotEmpty()) {
            val head=snake.first()
            if (hypot(head.x-food.x,head.y-food.y)<.035f) {
                score++; food=PointF(.1f+Math.random().toFloat()*.8f,.1f+Math.random().toFloat()*.75f)
            }
        }
        snake.forEachIndexed { i,p ->
            paint.color=if(i==0) Color.GREEN else Color.rgb(0,180,80)
            c.drawCircle(p.x*w,p.y*h,if(i==0) 13f else 9f,paint)
        }
        paint.color=Color.WHITE; paint.textSize=28f
        c.drawText("SCORE: $score",24f,45f,paint)
    }
}
