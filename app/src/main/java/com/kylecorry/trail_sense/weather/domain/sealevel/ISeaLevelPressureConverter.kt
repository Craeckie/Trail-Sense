package com.kylecorry.trail_sense.weather.domain.sealevel

import com.kylecorry.trailsensecore.domain.weather.PressureAltitudeReading
import com.kylecorry.trailsensecore.domain.weather.PressureReading

interface ISeaLevelPressureConverter {

    fun convert(readings: List<PressureAltitudeReading>, interpolateAltitudeChanges: Boolean = false, errors: List<Float?> = listOf()): List<PressureReading>

}