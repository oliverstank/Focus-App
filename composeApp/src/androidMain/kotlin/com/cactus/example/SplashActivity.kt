package com.cactus.example

import android.animation.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Splash Activity with Nothing-style dot matrix animation
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var animationView: NothingAnimationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        animationView = NothingAnimationView(this)
        setContentView(animationView)

        // Start animation, navigate to MainActivity when done
        animationView.startAnimation {
            navigateToMainActivity()
        }
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

/**
 * Nothing-style splash animation view
 */
class NothingAnimationView(context: Context) : View(context) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val dots = mutableListOf<Dot>()
    private var animationProgress = 0f
    private var logoAlpha = 0f

    private val dotColor = Color.WHITE
    private val backgroundColor = Color.BLACK

    // Smooth cubic curve for wave animation
    private val ease = PathInterpolator(0.25f, 0f, 0.1f, 1f)

    data class Dot(
        val x: Float,
        val y: Float,
        val delay: Float,
        val radius: Float,
        val jitter: Float,
        var alpha: Float = 0f,
        var scale: Float = 0f
    )

    init { setBackgroundColor(backgroundColor) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initializeDots()
    }

    private fun initializeDots() {
        dots.clear()
        val spacing = 38f
        val rows = (height / spacing).toInt() + 3
        val cols = (width / spacing).toInt() + 3
        val cx = width / 2f
        val cy = height / 2f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * spacing - spacing
                val y = row * spacing - spacing
                val dist = hypot(x - cx, y - cy)
                val delay = dist / width * 0.55f
                dots += Dot(
                    x = x,
                    y = y,
                    delay = delay,
                    radius = 2.8f + Random.nextFloat() * 2.3f,
                    jitter = Random.nextFloat() * 0.15f
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (dot in dots) {
            if (dot.alpha <= 0f) continue
            val r = dot.radius * dot.scale
            dotPaint.color = dotColor
            dotPaint.alpha = (dot.alpha * 255).toInt()
            canvas.drawCircle(dot.x, dot.y, r, dotPaint)
            if (dot.scale > 0.6f) {
                glowPaint.color = dotColor
                glowPaint.alpha = ((dot.alpha * 0.22f) * 255).toInt()
                canvas.drawCircle(dot.x, dot.y, r * 2.3f, glowPaint)
            }
        }
    }

    /**
     * Play animation once, then call onComplete
     */
    fun startAnimation(onComplete: () -> Unit) {
            val dotMatrixAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200
                interpolator = ease
                addUpdateListener {
                    animationProgress = it.animatedValue as Float
                    updateDots()
                    invalidate()
                }
            }

            val fadeOutAll = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 300
                startDelay = 0 // remove extra delay
                addUpdateListener { a ->
                    val f = a.animatedValue as Float
                    logoAlpha = f
                    for (dot in dots) dot.alpha *= f
                    invalidate()
                }
            }

            // Play sequentially: dot wave → fade out
            val animatorSet = AnimatorSet()
            animatorSet.playSequentially(dotMatrixAnimator, fadeOutAll)
            animatorSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete() // call immediately, no postDelayed
                }
            })
            animatorSet.start()
        }


    private fun updateDots() {
        for (dot in dots) {
            val t = ((animationProgress - dot.delay) / (1f - dot.delay)).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val e = ease.getInterpolation(t)
            dot.scale = if (e < 0.5f) e * 2f else 1f - (e - 0.5f) * 0.3f
            dot.alpha = (e * (1f - dot.jitter)).coerceIn(0f, 1f)
        }
    }
}
