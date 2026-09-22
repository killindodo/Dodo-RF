package com.killindodo.dodo_rf.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class FeedbackManager(context: Context) {
    private var toneGen: ToneGenerator? = null
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var soundEnabled: Boolean = true
    var hapticEnabled: Boolean = true

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            Log.w("FeedbackManager", "Failed initializing ToneGenerator", e)
        }
    }

    fun triggerRxFeedback() {
        if (soundEnabled) {
            try {
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 60)
            } catch (ignored: Exception) {}
        }
        if (hapticEnabled && vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(35)
            }
        }
    }

    fun triggerTxFeedback() {
        if (soundEnabled) {
            try {
                toneGen?.startTone(ToneGenerator.TONE_CDMA_PIP, 120)
            } catch (ignored: Exception) {}
        }
        if (hapticEnabled && vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 50, 40, 70)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }

    fun triggerClick() {
        if (hapticEnabled && vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(15, 80))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(15)
            }
        }
    }

    fun triggerError() {
        if (soundEnabled) {
            try {
                toneGen?.startTone(ToneGenerator.TONE_SUP_ERROR, 150)
            } catch (ignored: Exception) {}
        }
        if (hapticEnabled && vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 60, 60, 60)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(120)
            }
        }
    }

    fun release() {
        try {
            toneGen?.release()
            toneGen = null
        } catch (ignored: Exception) {}
    }
}
