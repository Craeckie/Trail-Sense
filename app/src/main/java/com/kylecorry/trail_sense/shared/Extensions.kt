package com.kylecorry.trail_sense.shared

import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kylecorry.andromeda.core.units.Coordinate
import com.kylecorry.andromeda.core.units.Distance
import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.andromeda.location.IGPS
import com.kylecorry.trail_sense.MainActivity
import com.kylecorry.trail_sense.R
import com.kylecorry.trailsensecore.domain.geo.PathPoint
import com.kylecorry.trailsensecore.domain.pixels.PixelLine
import com.kylecorry.trailsensecore.domain.pixels.PixelLineStyle

fun Distance.times(value: Float): Distance {
    return Distance(distance * value, units)
}

fun Fragment.requireMainActivity(): MainActivity {
    return requireActivity() as MainActivity
}

fun Fragment.requireBottomNavigation(): BottomNavigationView {
    return requireActivity().findViewById(R.id.bottom_navigation)
}

fun List<Coordinate>.toPixelLines(
    @ColorInt color: Int,
    style: PixelLineStyle,
    toPixelCoordinate: (coordinate: Coordinate) -> PixelCoordinate
): List<PixelLine> {
    val lines = mutableListOf<PixelLine>()
    val pixelWaypoints = map {
        toPixelCoordinate(it)
    }
    for (i in 1 until pixelWaypoints.size) {
        val line = PixelLine(
            pixelWaypoints[i - 1],
            pixelWaypoints[i],
            color,
            255,
            style
        )
        lines.add(line)
    }
    return lines
}

fun IGPS.getPathPoint(pathId: Long): PathPoint {
    return PathPoint(
        -1,
        pathId,
        location,
        altitude,
        time
    )
}