package com.kylecorry.trail_sense.astronomy.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.astronomy.domain.AstronomyService
import com.kylecorry.trail_sense.databinding.ActivityAstronomyBinding
import com.kylecorry.trail_sense.shared.*
import com.kylecorry.trailsensecore.infrastructure.sensors.gps.IGPS
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trailsensecore.domain.astronomy.SunTimesMode
import com.kylecorry.trailsensecore.domain.astronomy.moon.MoonTruePhase
import com.kylecorry.trailsensecore.domain.astronomy.tides.Tide
import com.kylecorry.trailsensecore.infrastructure.persistence.Cache
import com.kylecorry.trailsensecore.infrastructure.system.UiUtils
import com.kylecorry.trailsensecore.infrastructure.sensors.declination.IDeclinationProvider
import com.kylecorry.trailsensecore.infrastructure.time.Intervalometer
import com.kylecorry.trailsensecore.infrastructure.view.ListView
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class AstronomyFragment : Fragment() {

    private lateinit var gps: IGPS
    private lateinit var declinationProvider: IDeclinationProvider

    private var _binding: ActivityAstronomyBinding? = null
    private val binding get() = _binding!!
    private lateinit var detailList: ListView<AstroDetail>
    private lateinit var chart: AstroChart

    private lateinit var displayDate: LocalDate

    private lateinit var sunTimesMode: SunTimesMode

    @Inject lateinit var sensorService: SensorService
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val cache by lazy { Cache(requireContext()) }
    private val astronomyService = AstronomyService()

    private val intervalometer = Intervalometer {
        updateUI()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = binding.astronomyDetailList
        detailList =
            ListView(recyclerView, R.layout.list_item_astronomy_detail) { itemView, detail ->
                val nameText: TextView = itemView.findViewById(R.id.astronomy_detail_name)
                val valueText: TextView = itemView.findViewById(R.id.astronomy_detail_value)
                val iconView: ImageView = itemView.findViewById(R.id.astronomy_detail_icon)

                if (detail.name == null) {
                    nameText.text = ""
                    valueText.text = ""
                    iconView.visibility = View.INVISIBLE
                    return@ListView
                }

                nameText.text = detail.name
                valueText.text = detail.value
                iconView.setImageResource(detail.icon)
                iconView.visibility = View.VISIBLE
                if (detail.tint != null) {
                    iconView.imageTintList =
                        ColorStateList.valueOf(resources.getColor(detail.tint, null))
                } else {
                    iconView.imageTintList =
                        ColorStateList.valueOf(UiUtils.androidTextColorSecondary(requireContext()))
                }
            }


        chart = AstroChart(binding.sunMoonChart)

        binding.prevDate.setOnClickListener {
            displayDate = displayDate.minusDays(1)
            updateUI()
        }

        binding.nextDate.setOnClickListener {
            displayDate = displayDate.plusDays(1)
            updateUI()
        }

        gps = sensorService.getGPS()
        declinationProvider = sensorService.getDeclinationProvider()

        sunTimesMode = prefs.astronomy.sunTimesMode

        binding.sunPosition.setOnClickListener {
            openDetailsDialog()
        }

        binding.moonPosition.setOnClickListener {
            openDetailsDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ActivityAstronomyBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        displayDate = LocalDate.now()
        requestLocationUpdate()
        if (!declinationProvider.hasValidReading) {
            declinationProvider.start(this::onDeclinationUpdate)
        }
        intervalometer.interval(Duration.ofMinutes(1))
        updateUI()

        if (cache.getBoolean("cache_tap_sun_moon_shown") != true) {
            cache.putBoolean("cache_tap_sun_moon_shown", true)
            UiUtils.shortToast(requireContext(), getString(R.string.tap_sun_moon_hint))
        }

    }

    override fun onPause() {
        super.onPause()
        gps.stop(this::onLocationUpdate)
        declinationProvider.stop(this::onDeclinationUpdate)
        intervalometer.stop()
    }

    private fun requestLocationUpdate() {
        if (gps.hasValidReading) {
            onLocationUpdate()
        } else {
            gps.start(this::onLocationUpdate)
        }
    }

    private fun onDeclinationUpdate(): Boolean {
        updateUI()
        return false
    }

    private fun onLocationUpdate(): Boolean {
        updateUI()
        return false
    }

    private fun updateUI() {
        if (context == null) {
            return
        }
        binding.date.text = getDateString(displayDate)
        updateSunUI()
        updateMoonUI()
        updateAstronomyChart()
        updateAstronomyDetails()
    }

    private fun updateMoonUI() {
        if (context == null) {
            return
        }

        val moonPhase = astronomyService.getCurrentMoonPhase()
        binding.moonPosition.setImageResource(getMoonImage(moonPhase.phase))
    }

    private fun updateAstronomyChart() {
        if (context == null) {
            return
        }

        val moonAltitudes: List<Pair<LocalDateTime, Float>>
        val sunAltitudes: List<Pair<LocalDateTime, Float>>
        val startHour: Float

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

        chart.plot(
            listOf(
                AstroChart.AstroChartDataset(
                    moonAltitudes,
                    resources.getColor(R.color.white, null)
                ),
                AstroChart.AstroChartDataset(
                    sunAltitudes,
                    resources.getColor(R.color.colorPrimary, null)
                )
            ),
            startHour
        )

        if (displayDate == LocalDate.now()) {
            val current =
                moonAltitudes.minByOrNull { Duration.between(LocalDateTime.now(), it.first).abs() }
            val currentIdx = moonAltitudes.indexOf(current)
            val point = chart.getPoint(1, currentIdx)
            binding.moonPosition.x = point.first - binding.moonPosition.width / 2f
            binding.moonPosition.y = point.second - binding.moonPosition.height / 2f

            val point2 = chart.getPoint(2, currentIdx)
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

    private fun updateSunUI() {
        if (context == null) {
            return
        }

        displayTimeUntilNextSunEvent()
    }

    private fun openDetailsDialog() {
        // Altitude and azimuth
        val moonAltitude =
            astronomyService.getMoonAltitude(gps.location)
        val sunAltitude =
            astronomyService.getSunAltitude(gps.location)

        val declination =
            if (!prefs.navigation.useTrueNorth) declinationProvider.declination else 0f

        val sunAzimuth =
            astronomyService.getSunAzimuth(gps.location).withDeclination(-declination).value
        val moonAzimuth =
            astronomyService.getMoonAzimuth(gps.location).withDeclination(-declination).value

        UiUtils.alert(
            requireContext(), getString(R.string.sun_and_moon), getString(
                R.string.sun_and_moon_position_details,
                getString(R.string.degree_format, sunAltitude),
                getString(R.string.degree_format, sunAzimuth),
                getString(R.string.degree_format, moonAltitude),
                getString(R.string.degree_format, moonAzimuth)
            )
        )
    }

    private fun updateAstronomyDetails() {
        if (context == null) {
            return
        }

        // Rise / set times
        val sunTimes = astronomyService.getSunTimes(gps.location, SunTimesMode.Actual, displayDate)
        val duskDawn = astronomyService.getSunTimes(gps.location, SunTimesMode.Civil, displayDate)
        val moonTimes = astronomyService.getMoonTimes(gps.location, displayDate)
        val solarNoon = astronomyService.getSolarNoon(gps.location, displayDate)
        val lunarNoon = astronomyService.getLunarNoon(gps.location, displayDate)

        // Sun and moon times
        val details = listOf(
            Pair(
                Pair(
                    R.drawable.sunrise to R.color.colorPrimary,
                    getString(R.string.sunrise_label)
                ), sunTimes.rise?.toLocalDateTime()
            ),
            Pair(
                Pair(
                    R.drawable.sunset to R.color.colorPrimary,
                    getString(R.string.sunset_label)
                ), sunTimes.set?.toLocalDateTime()
            ),
            // TODO: Get moon icons
            Pair(
                Pair(R.drawable.moonrise to null, getString(R.string.moon_rise)),
                moonTimes.rise?.toLocalDateTime()
            ),
            Pair(
                Pair(R.drawable.moonset to null, getString(R.string.moon_set)),
                moonTimes.set?.toLocalDateTime()
            ),
            // TODO: Get solar/lunar noon images
            Pair(
                Pair(R.drawable.sun to R.color.colorPrimary, getString(R.string.solar_noon)),
                solarNoon
            ),
            Pair(
                Pair(R.drawable.moon_full to null, getString(R.string.lunar_noon)),
                lunarNoon
            )
        ).filterNot { it.second == null }.sortedBy { it.second?.toLocalTime() }.map {
            AstroDetail(
                it.first.first.first,
                it.first.second,
                getTimeString(it.second),
                it.first.first.second
            )
        }.toMutableList()

        val duskDawnDetails = listOf(
            Pair(
                Pair(
                    Pair(R.drawable.sunrise, R.color.colorPrimary),
                    getString(R.string.sun_dawn)
                ),
                duskDawn.rise?.toLocalDateTime()
            ),
            Pair(
                Pair(
                    Pair(R.drawable.sunset, R.color.colorPrimary),
                    getString(R.string.sun_dusk)
                ),
                duskDawn.set?.toLocalDateTime()
            )
        ).filterNot { it.second == null }.sortedBy { it.second?.toLocalTime() }.map {
            AstroDetail(
                it.first.first.first,
                it.first.second,
                getTimeString(it.second),
                it.first.first.second
            )
        }

        // Add dusk and dawn
        if (duskDawnDetails.isNotEmpty()) {
            details.add(AstroDetail.spacer())
            details.addAll(duskDawnDetails)
        }

        details.add(AstroDetail.spacer())

        // Add the moon phase
        if (displayDate == LocalDate.now()) {
            // Moon phase
            val moonPhase = astronomyService.getCurrentMoonPhase()

            details.add(
                AstroDetail(
                    getMoonImage(moonPhase.phase),
                    getString(R.string.moon_phase),
                    getMoonPhaseString(moonPhase.phase)
                )
            )
            details.add(
                AstroDetail(
                    R.drawable.illumination,
                    getString(R.string.moon_illumination),
                    getString(R.string.percent_format, moonPhase.illumination.roundToInt())
                )
            )
        } else {
            val moonPhase = astronomyService.getMoonPhase(displayDate)
            details.add(
                AstroDetail(
                    getMoonImage(moonPhase.phase),
                    getString(R.string.moon_phase),
                    getMoonPhaseString(moonPhase.phase)
                )
            )
            details.add(
                AstroDetail(
                    R.drawable.illumination,
                    getString(R.string.moon_illumination),
                    getString(R.string.percent_format, moonPhase.illumination.roundToInt())
                )
            )
        }

        if (prefs.experimentalEnabled) {
            details.add(AstroDetail.spacer())
            val tide = astronomyService.getTides(displayDate)
            details.add(
                AstroDetail(
                    R.drawable.tides,
                    getString(R.string.tidal_range),
                    getTideString(tide),
                    R.color.colorAccent
                )
            )
        }

        detailList.setData(details)
    }

    private fun getTideString(tide: Tide): String {
        return when (tide) {
            Tide.Neap -> getString(R.string.tide_neap)
            Tide.Spring -> getString(R.string.tide_spring)
            Tide.Normal -> getString(R.string.tide_normal)
        }
    }

    private fun getMoonImage(phase: MoonTruePhase): Int {
        return when (phase) {
            MoonTruePhase.FirstQuarter -> R.drawable.moon_first_quarter
            MoonTruePhase.Full -> R.drawable.moon_full
            MoonTruePhase.ThirdQuarter -> R.drawable.moon_last_quarter
            MoonTruePhase.New -> R.drawable.moon_new
            MoonTruePhase.WaningCrescent -> R.drawable.moon_waning_crescent
            MoonTruePhase.WaningGibbous -> R.drawable.moon_waning_gibbous
            MoonTruePhase.WaxingCrescent -> R.drawable.moon_waxing_crescent
            MoonTruePhase.WaxingGibbous -> R.drawable.moon_waxing_gibbous
        }
    }

    private fun getMoonPhaseString(phase: MoonTruePhase): String {
        return getString(
            when (phase) {
                MoonTruePhase.FirstQuarter -> R.string.first_quarter
                MoonTruePhase.Full -> R.string.full_moon
                MoonTruePhase.ThirdQuarter -> R.string.third_quarter
                MoonTruePhase.New -> R.string.new_moon
                MoonTruePhase.WaningCrescent -> R.string.waning_crescent
                MoonTruePhase.WaningGibbous -> R.string.waning_gibbous
                MoonTruePhase.WaxingCrescent -> R.string.waxing_crescent
                MoonTruePhase.WaxingGibbous -> R.string.waxing_gibbous
            }
        )
    }

    private fun displayTimeUntilNextSunEvent() {
        val currentTime = LocalDateTime.now()
        val nextSunrise = astronomyService.getNextSunrise(gps.location, sunTimesMode)
        val nextSunset = astronomyService.getNextSunset(gps.location, sunTimesMode)

        if (nextSunrise != null && (nextSunset == null || nextSunrise.isBefore(nextSunset))) {
            binding.remainingTime.text = Duration.between(currentTime, nextSunrise).formatHM()
            binding.remainingTimeLbl.text = getString(
                R.string.until_sun_time, getSunriseWording()
            )
        } else if (nextSunset != null) {
            binding.remainingTime.text = Duration.between(currentTime, nextSunset).formatHM()
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

    private fun getTimeString(time: LocalDateTime?): String {
        return time?.toDisplayFormat(requireContext()) ?: "-"
    }

    private fun getDateString(date: LocalDate): String {
        val now = LocalDate.now()

        return when (date) {
            now -> {
                getString(R.string.today)
            }
            now.plusDays(1) -> {
                getString(R.string.tomorrow)
            }
            now.minusDays(1) -> {
                getString(R.string.yesterday)
            }
            else -> {
                DateUtils.formatDateTime(
                    requireContext(),
                    date.atStartOfDay().toEpochMillis(),
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_RELATIVE
                )
            }
        }
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

    data class AstroDetail(
        val icon: Int,
        val name: String?,
        val value: String,
        val tint: Int? = null
    ) {
        companion object {
            fun spacer(): AstroDetail {
                return AstroDetail(0, null, "")
            }
        }
    }

}
