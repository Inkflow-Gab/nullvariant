package com.nullvariant.official

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityOptionsCompat

class SplashActivity : AppCompatActivity() {

    init {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var percentText: TextView
    private lateinit var splashLogo: ImageView
    private lateinit var logoPulse: ObjectAnimator
    private lateinit var progressAnimator: ValueAnimator
    private lateinit var notificationPermissionRequester: NotificationPermissionRequester
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    private val totalDuration = 5600L

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        setContentView(R.layout.activity_splash)
        Fonts.applyMedieval(this, findViewById(android.R.id.content))

        progressBar = findViewById(R.id.progressBarSplash)
        statusText = findViewById(R.id.splash_status)
        percentText = findViewById(R.id.splash_percent)
        splashLogo = findViewById(R.id.splash_logo)
        ImageLoader.load(splashLogo, R.drawable.nullvariant_logo, dp(128))
        progressBar.progress = 0

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        notificationPermissionRequester = NotificationPermissionRequester(this, permissionLauncher)
        notificationPermissionRequester.request()

        startLogoPulse()
        startProgress()

        handler.postDelayed({
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )

            startActivity(
                Intent(this, MainActivity::class.java),
                options.toBundle()
            )
            finish()
        }, totalDuration)
    }

    private fun startLogoPulse() {
        logoPulse = ObjectAnimator.ofPropertyValuesHolder(
            splashLogo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f, 1f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.88f, 1f, 0.88f)
        ).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startProgress() {
        progressAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = 5200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                progressBar.progress = value
                percentText.text = "$value%"
                statusText.text = when {
                    value < 25 -> "INITIALIZING CORE..."
                    value < 55 -> "LOADING BYPASS ENGINE..."
                    value < 85 -> "VERIFYING SIGNATURES..."
                    else -> "READY TO LAUNCH"
                }
            }
            start()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::logoPulse.isInitialized) logoPulse.cancel()
        if (::progressAnimator.isInitialized) progressAnimator.cancel()
        super.onDestroy()
    }
}
