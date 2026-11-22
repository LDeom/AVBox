package com.github.avbox.osc.player.controller

import android.app.Activity
import android.media.AudioManager

sealed interface SystemEffect {
    data class Brightness(val value: Float) : SystemEffect
    data class Volume(val index: Int) : SystemEffect
}

class SystemEffectDispatcher(
    private val activity: () -> Activity?,
    private val audio: () -> AudioManager?,
) {
    fun dispatch(effect: SystemEffect): Boolean = runCatching {
        when (effect) {
            is SystemEffect.Brightness -> applyBrightness(effect.value)
            is SystemEffect.Volume -> applyVolume(effect.index)
        }
    }.onFailure {
        com.github.avbox.osc.util.AppLog.e("echo-gesture-system-effect-failed: " + effect.javaClass.simpleName + " " + it.javaClass.simpleName)
    }.getOrDefault(false)

    private fun applyBrightness(value: Float): Boolean {
        val window = activity()?.window ?: return false
        val attrs = window.attributes
        attrs.screenBrightness = value
        window.attributes = attrs
        return true
    }

    private fun applyVolume(index: Int): Boolean {
        val manager = audio() ?: return false
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, index, AudioManager.FLAG_SHOW_UI)
        return true
    }
}
