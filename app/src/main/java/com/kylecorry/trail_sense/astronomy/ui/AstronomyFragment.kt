package com.kylecorry.trail_sense.astronomy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.capitalizeWords
import com.kylecorry.andromeda.core.time.Timer
import com.kylecorry.andromeda.core.time.roundNearestMinute
import com.kylecorry.andromeda.core.units.Coordinate
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.list.ListView
import com.kylecorry.andromeda.location.IGPS
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.andromeda.preferences.Preferences
import com.kylecorry.trail_sense.MainActivity
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.astronomy.domain.AstronomyEvent
import com.kylecorry.trail_sense.astronomy.domain.AstronomyService
import com.kylecorry.trail_sense.astronomy.ui.fields.AstroField
import com.kylecorry.trail_sense.astronomy.ui.fields.providers.*
import com.kylecorry.trail_sense.databinding.ActivityAstronomyBinding
import com.kylecorry.trail_sense.databinding.ListItemAstronomyDetailBinding
import com.kylecorry.trail_sense.quickactions.LowPowerQuickAction
import com.kylecorry.trail_sense.shared.*
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.shared.sensors.overrides.CachedGPS
import com.kylecorry.trail_sense.shared.sensors.overrides.OverrideGPS
import com.kylecorry.trail_sense.shared.views.QuickActionNone
import com.kylecorry.trail_sense.shared.views.UserError
import com.kylecorry.trail_sense.tools.flashlight.ui.QuickActionFlashlight
import com.kylecorry.trail_sense.tools.whistle.ui.QuickActionWhistle
import com.kylecorry.trail_sense.tools.whitenoise.ui.QuickActionWhiteNoise
import com.kylecorry.trailsensecore.domain.astronomy.SunTimesMode
import com.kylecorry.trailsensecore.domain.astronomy.moon.MoonTruePhase
import com.kylecorry.trailsensecore.domain.geo.GeoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class AstronomyFragment : BoundFragment<ActivityAstronomyBinding>() {

    private lateinit var gps: IGPS

    private lateinit var detailList: ListView<AstroField>
    private lateinit var chart: AstroChart

    private lateinit var displayDate: LocalDate

    private lateinit var sunTimesMode: SunTimesMode

    private val sensorService by lazy { SensorService(requireContext()) }
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val cache by lazy { Preferences(requireContext()) }
    private val astronomyService = AstronomyService()
    private val geoService = GeoService()
    private val formatService by lazy { FormatService(requireContext()) }

    private var leftQuickAction: QuickActionButton? = null
    private var rightQuickAction: QuickActionButton? = null

    private var lastAstronomyEventSearch: AstronomyEvent? = null

    private var gpsErrorShown = false

    private val intervalometer = Timer {
        updateUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        leftQuickAction =
            getQuickActionButton(prefs.astronomy.leftQuickAction, binding.astronomyLeftQuickAction)
        leftQuickAction?.onCreate()

        rightQuickAction = getQuickActionButton(
            prefs.astronomy.rightQuickAction,
            binding.astronomyRightQuickAction
        )
        rightQuickAction?.onCreate()

        val recyclerView = binding.astronomyDetailList
        detailList =
            ListView(recyclerView, R.layout.list_item_astronomy_detail) { itemView, field ->
                val itemBinding = ListItemAstronomyDetailBinding.bind(itemView)
                field.display(itemBinding)
            }


        chart = AstroChart(binding.sunMoonChart)

        binding.datePicker.setOnClickListener {
            Pickers.date(requireContext(), displayDate) {
                if (it != null) {
                    displayDate = it
                    updateUI()
                }
            }
        }

        binding.datePicker.setOnLongClickListener {
            val options = listOf(
                AstronomyEvent.FullMoon,
                AstronomyEvent.NewMoon,
                AstronomyEvent.QuarterMoon,
                AstronomyEvent.MeteorShower,
                AstronomyEvent.LunarEclipse
            )
            Pickers.item(
                requireContext(),
                getString(R.string.find_next_occurrence),
                listOf(
                    getString(R.string.full_moon),
                    getString(R.string.new_moon),
                    getString(R.string.quarter_moon),
                    getString(R.string.meteor_shower),
                    getString(R.string.lunar_eclipse)
                ).map { it.capitalizeWords() },
                options.indexOf(lastAstronomyEventSearch)
            ) {
                if (it != null) {
                    val search = options[it]
                    lastAstronomyEventSearch = search
                    displayDate = astronomyService.findNextEvent(
                        search,
                        gps.location,
                        displayDate
                    ) ?: displayDate
                    updateUI()
                }
            }
            true
        }

        binding.nextDate.setOnClickListener {
            displayDate = displayDate.plusDays(1)
            updateUI()
        }

        binding.prevDate.setOnClickListener {
            displayDate = displayDate.minusDays(1)
            updateUI()
        }

        gps = sensorService.getGPS()

        sunTimesMode = prefs.astronomy.sunTimesMode

        binding.sunPosition.setOnClickListener {
            openDetailsDialog()
        }

        binding.moonPosition.setOnClickListener {
            openDetailsDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        leftQuickAction?.onDestroy()
        rightQuickAction?.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        leftQuickAction?.onResume()
        rightQuickAction?.onResume()
        displayDate = LocalDate.now()
        requestLocationUpdate()
        intervalometer.interval(Duration.ofMinutes(1), Duration.ofMillis(200))
        updateUI()

        if (cache.getBoolean("cache_tap_sun_moon_shown") != true) {
            cache.putBoolean("cache_tap_sun_moon_shown", true)
            Alerts.toast(requireContext(), getString(R.string.tap_sun_moon_hint))
        }

    }

    override fun onPause() {
        super.onPause()
        if (lifecycleScope.isActive) {
            lifecycleScope.cancel()
        }
        leftQuickAction?.onPause()
        rightQuickAction?.onPause()
        gps.stop(this::onLocationUpdate)
        intervalometer.stop()
        gpsErrorShown = false
    }

    private fun requestLocationUpdate() {
        if (gps.hasValidReading) {
            onLocationUpdate()
        } else {
            gps.start(this::onLocationUpdate)
        }
    }

    private fun onLocationUpdate(): Boolean {
        updateUI()
        return false
    }

    private fun getDeclination(): Float {
        return if (!prefs.useAutoDeclination) {
            prefs.declinationOverride
        } else {
            geoService.getDeclination(gps.location, gps.altitude)
        }
    }

    private fun updateUI() {
        if (!isBound) {
            return
        }
        runInBackground {
            withContext(Dispatchers.Main) {
                detectAndShowGPSError()
                binding.date.text = getDateString(displayDate)
            }

            updateSunUI()
            updateMoonUI()
            updateAstronomyChart()
            updateAstronomyDetails()
        }
    }

    private suspend fun updateMoonUI() {
        if (!isBound) {
            return
        }

        val moonPhase = withContext(Dispatchers.Default) {
            astronomyService.getCurrentMoonPhase()
        }

        withContext(Dispatchers.Main) {
            binding.moonPosition.setImageResource(getMoonImage(moonPhase.phase))
        }
    }

    private suspend fun updateAstronomyChart() {
        if (!isBound) {
            return
        }

        val moonAltitudes: List<Pair<LocalDateTime, Float>>
        val sunAltitudes: List<Pair<LocalDateTime, Float>>
        val startHour: Float

        withContext(Dispatchers.Default) {
            if (displayDate == LocalDate.now() && prefs.astronomy.centerSunAndMoon) {
                val startTime = LocalDateTime.now().roundNearestMinute(10).minusHours(12)
                startHour = startTime.hour + startTime.minute / 60f

                moonAltitudes = astronomyService.getCenteredMoonAltitudes(
                    gps.location,
                    LocalDateTime.now()
                )
                sunAltitudes = astronomyService.getCenteredSunAltitudes(
                    gps.location,
                    LocalDateTime.now()
                )
            } else {
                startHour = 0f
                moonAltitudes = astronomyService.getMoonAltitudes(gps.location, displayDate)
                sunAltitudes = astronomyService.getSunAltitudes(gps.location, displayDate)
            }
        }

        withContext(Dispatchers.Main) {
            chart.plot(
                listOf(
                    AstroChart.AstroChartDataset(
                        moonAltitudes,
                        resources.getColor(R.color.white, null)
                    ),
                    AstroChart.AstroChartDataset(
                        sunAltitudes,
                        resources.getColor(R.color.sun, null)
                    )
                ),
                startHour
            )

            if (displayDate == LocalDate.now()) {
                val current =
                    moonAltitudes.minByOrNull {
                        Duration.between(LocalDateTime.now(), it.first).abs()
                    }
                val currentIdx = moonAltitudes.indexOf(current)
                val point = chart.getPoint(0, currentIdx)
                binding.moonPosition.x = point.first - binding.moonPosition.width / 2f
                binding.moonPosition.y = point.second - binding.moonPosition.height / 2f

                val point2 = chart.getPoint(1, currentIdx)
                binding.sunPosition.x = point2.first - binding.sunPosition.width / 2f
                binding.sunPosition.y = point2.second - binding.sunPosition.height / 2f

                if (binding.moonPosition.height != 0) {
                    binding.moonPosition.visibility = View.VISIBLE
                }

                if (binding.sunPosition.height != 0) {
                    binding.sunPosition.visibility = View.VISIBLE
                }

            } else {
                binding.sunPosition.visibility = View.INVISIBLE
                binding.moonPosition.visibility = View.INVISIBLE
            }
        }
    }

    private suspend fun updateSunUI() {
        if (!isBound) {
            return
        }

        displayTimeUntilNextSunEvent()
    }

    private fun openDetailsDialog() {
        // TODO: Improve the UI of this
        runInBackground {
            withContext(Dispatchers.Default) {
                val moonAltitude =
                    astronomyService.getMoonAltitude(gps.location)
                val sunAltitude =
                    astronomyService.getSunAltitude(gps.location)

                val declination =
                    if (!prefs.navigation.useTrueNorth) getDeclination() else 0f

                val sunAzimuth =
                    astronomyService.getSunAzimuth(gps.location).withDeclination(-declination).value
                val moonAzimuth =
                    astronomyService.getMoonAzimuth(gps.location)
                        .withDeclination(-declination).value

                withContext(Dispatchers.Main) {
                    if (context != null) {
                        Alerts.dialog(
                            requireContext(),
                            getString(R.string.sun_and_moon),
                            getString(
                                R.string.sun_and_moon_position_details,
                                getString(R.string.degree_format, sunAltitude),
                                getString(R.string.degree_format, sunAzimuth),
                                getString(R.string.degree_format, moonAltitude),
                                getString(R.string.degree_format, moonAzimuth)
                            ),
                            cancelText = null
                        )
                    }
                }
            }
        }
    }

    private suspend fun updateAstronomyDetails() {
        if (!isBound) {
            return
        }

        val fields = mutableListOf<AstroField>()

        withContext(Dispatchers.Default) {

            val fieldProvider = Group(
                SunMoonTimesProvider(prefs.astronomy.showNoon),
                Section(DaylightProvider(sunTimesMode)),
                Conditional(Section(CivilTimesProvider())) {
                    prefs.astronomy.showCivilTimes
                },
                Conditional(Section(NauticalTimesProvider())) {
                    prefs.astronomy.showNauticalTimes
                },
                Conditional(Section(AstronomicalSunTimesProvider())) {
                    prefs.astronomy.showAstronomicalTimes
                },
                Section(
                    Group(
                        MoonPhaseProvider(),
                        Conditional(MeteorShowerProvider()) { prefs.astronomy.showMeteorShowers },
                        Conditional(LunarEclipseProvider()) { prefs.astronomy.showLunarEclipses }
                    )
                )
            )

            fields.addAll(fieldProvider.getFields(displayDate, gps.location))
        }

        withContext(Dispatchers.Main) {
            detailList.setData(fields)
        }

    }


    private fun getMoonImage(phase: MoonTruePhase): Int {
        return MoonPhaseImageMapper().getPhaseImage(phase)
    }

    private suspend fun displayTimeUntilNextSunEvent() {
        val currentTime = LocalDateTime.now()

        var nextSunrise: LocalDateTime?
        var nextSunset: LocalDateTime?
        withContext(Dispatchers.Default) {
            nextSunrise = astronomyService.getNextSunrise(gps.location, sunTimesMode)
            nextSunset = astronomyService.getNextSunset(gps.location, sunTimesMode)
        }

        withContext(Dispatchers.Main) {
            if (nextSunrise != null && (nextSunset == null || nextSunrise?.isBefore(nextSunset) == true)) {
                binding.remainingTime.text =
                    formatService.formatDuration(Duration.between(currentTime, nextSunrise))
                binding.remainingTimeLbl.text = getString(
                    R.string.until_sun_time, getSunriseWording()
                )
            } else if (nextSunset != null) {
                binding.remainingTime.text =
                    formatService.formatDuration(Duration.between(currentTime, nextSunset))
                binding.remainingTimeLbl.text = getString(
                    R.string.until_sun_time, getSunsetWording()
                )
            } else if (astronomyService.isSunUp(gps.location)) {
                binding.remainingTime.text = getString(R.string.sun_up_no_set)
                binding.remainingTimeLbl.text = getString(R.string.sun_does_not_set)
            } else {
                binding.remainingTime.text = getString(R.string.sun_down_no_set)
                binding.remainingTimeLbl.text = getString(R.string.sun_does_not_rise)
            }
        }
    }

    private fun getDateString(date: LocalDate): String {
        return formatService.formatRelativeDate(date)
    }

    private fun getSunsetWording(): String {
        return when (sunTimesMode) {
            SunTimesMode.Actual -> getString(R.string.sunset_label)
            SunTimesMode.Civil -> getString(R.string.sun_civil)
            SunTimesMode.Nautical -> getString(R.string.sun_nautical)
            SunTimesMode.Astronomical -> getString(R.string.sun_astronomical)
        }
    }

    private fun getSunriseWording(): String {
        return when (sunTimesMode) {
            SunTimesMode.Actual -> getString(R.string.sunrise_label)
            SunTimesMode.Civil -> getString(R.string.sun_civil)
            SunTimesMode.Nautical -> getString(R.string.sun_nautical)
            SunTimesMode.Astronomical -> getString(R.string.sun_astronomical)
        }
    }

    private fun detectAndShowGPSError() {
        if (gpsErrorShown) {
            return
        }

        if (gps is OverrideGPS && gps.location == Coordinate.zero) {
            val activity = requireActivity() as MainActivity
            val navController = findNavController()
            val error = UserError(
                USER_ERROR_GPS_NOT_SET,
                getString(R.string.location_not_set),
                R.drawable.satellite,
                getString(R.string.set)
            ) {
                activity.errorBanner.dismiss(USER_ERROR_GPS_NOT_SET)
                navController.navigate(R.id.calibrateGPSFragment)
            }
            activity.errorBanner.report(error)
            gpsErrorShown = true
        } else if (gps is CachedGPS && gps.location == Coordinate.zero) {
            val error = UserError(
                USER_ERROR_NO_GPS,
                getString(R.string.location_disabled),
                R.drawable.satellite
            )
            (requireActivity() as MainActivity).errorBanner.report(error)
            gpsErrorShown = true
        }
    }

    private fun getQuickActionButton(
        type: QuickActionType,
        button: FloatingActionButton
    ): QuickActionButton {
        return when (type) {
            QuickActionType.Whistle -> QuickActionWhistle(button, this)
            QuickActionType.Flashlight -> QuickActionFlashlight(button, this)
            QuickActionType.WhiteNoise -> QuickActionWhiteNoise(button, this)
            QuickActionType.LowPowerMode -> LowPowerQuickAction(button, this)
            else -> QuickActionNone(button, this)
        }
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): ActivityAstronomyBinding {
        return ActivityAstronomyBinding.inflate(layoutInflater, container, false)
    }

}
