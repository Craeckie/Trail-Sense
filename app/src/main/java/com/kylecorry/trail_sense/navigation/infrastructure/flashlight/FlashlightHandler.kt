package com.kylecorry.trail_sense.navigation.infrastructure.flashlight

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.getSystemService
import com.kylecorry.trail_sense.navigation.domain.FlashlightState
import com.kylecorry.trailsensecore.infrastructure.flashlight.Flashlight


class FlashlightHandler(private val context: Context) : IFlashlightHandler {

    private val torchCallback: TorchCallback

    init {
        torchCallback = object : TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                try {
                    super.onTorchModeChanged(cameraId, enabled)
                    if (!enabled && FlashlightService.isOn(context)) {
                        off()
                    }

                    if (enabled && !FlashlightService.isOn(context) && !SosService.isOn(context)){
                        on()
                    }
                } catch (e: Exception){
                    // Ignore
                }
            }
        }
        initialize()
    }

    override fun initialize() {
        registerTorchCallback()
    }

    override fun release() {
        unregisterTorchCallback()
        off()
    }

    override fun on() {
        SosService.stop(context)
        FlashlightService.start(context)
    }

    override fun off() {
        SosService.stop(context)
        FlashlightService.stop(context)
    }

    override fun sos() {
        SosService.start(context)
        FlashlightService.stop(context)
    }

    override fun set(state: FlashlightState) {
        when (state) {
            FlashlightState.Off -> off()
            FlashlightState.On -> on()
            FlashlightState.SOS -> sos()
        }
    }

    override fun getState(): FlashlightState {
        return when {
            FlashlightService.isOn(context) -> FlashlightState.On
            SosService.isOn(context) -> FlashlightState.SOS
            else -> FlashlightState.Off
        }
    }

    override fun getNextState(currentState: FlashlightState?): FlashlightState {
        return when (currentState ?: getState()) {
            FlashlightState.On -> FlashlightState.SOS
            FlashlightState.SOS -> FlashlightState.Off
            FlashlightState.Off -> FlashlightState.On
        }
    }

    override fun isAvailable(): Boolean {
        return Flashlight.hasFlashlight(context)
    }

    private fun registerTorchCallback(){
        try {
            context.getSystemService<CameraManager>()?.registerTorchCallback(
                torchCallback, Handler(
                    Looper.getMainLooper()
                )
            )
        } catch (e: Exception){

        }
    }

    private fun unregisterTorchCallback(){
        try {
            context.getSystemService<CameraManager>()?.unregisterTorchCallback(torchCallback)
        } catch (e: Exception){

        }
    }

}