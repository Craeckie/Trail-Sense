package com.kylecorry.trail_sense.tools.flashlight.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.core.time.Timer
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.torch.Torch
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentToolFlashlightBinding
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.tools.flashlight.domain.FlashlightState
import com.kylecorry.trail_sense.tools.flashlight.infrastructure.FlashlightHandler

class FragmentToolFlashlight : BoundFragment<FragmentToolFlashlightBinding>() {

    private var flashlightState = FlashlightState.Off
    private val flashlight by lazy { FlashlightHandler.getInstance(requireContext()) }
    private val intervalometer = Timer {
        update()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val hasFlashlight = Torch.isAvailable(requireContext())
        binding.flashlightBtn.isVisible = hasFlashlight
        binding.strobeBtn.isVisible = hasFlashlight
        binding.sosBtn.isVisible = hasFlashlight
        binding.flashlightBtn.setOnClickListener {
            if (flashlight.getState() == FlashlightState.On) {
                flashlight.set(FlashlightState.Off)
            } else {
                flashlight.set(FlashlightState.On)
            }
        }
        binding.sosBtn.setOnClickListener {
            if (flashlight.getState() == FlashlightState.SOS) {
                flashlight.set(FlashlightState.Off)
            } else {
                flashlight.set(FlashlightState.SOS)
            }
        }

        binding.screenFlashlightBtn.setOnClickListener {
            flashlight.set(FlashlightState.Off)
            findNavController().navigate(R.id.action_flashlight_to_screen_flashlight)
        }

        binding.strobeBtn.setOnClickListener {
            if (flashlight.getState() == FlashlightState.Strobe) {
                flashlight.set(FlashlightState.Off)
            } else {
                CustomUiUtils.disclaimer(
                    requireContext(),
                    getString(R.string.strobe_warning_title),
                    getString(R.string.strobe_warning_content),
                    getString(R.string.pref_fine_with_strobe),
                    considerShownIfCancelled = false,
                ) { cancelled ->
                    if (!cancelled) {
                        flashlight.set(FlashlightState.Strobe)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        flashlightState = flashlight.getState()
        updateFlashlightUI()
        intervalometer.interval(20)
    }

    override fun onPause() {
        super.onPause()
        intervalometer.stop()
    }

    private fun updateFlashlightUI() {
        binding.flashlightBtn.setState(flashlightState == FlashlightState.On)
        binding.sosBtn.setState(flashlightState == FlashlightState.SOS)
        binding.strobeBtn.setState(flashlightState == FlashlightState.Strobe)
    }


    private fun update() {
        flashlightState = flashlight.getState()
        updateFlashlightUI()
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentToolFlashlightBinding {
        return FragmentToolFlashlightBinding.inflate(layoutInflater, container, false)
    }

}