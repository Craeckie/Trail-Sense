package com.kylecorry.trail_sense.astronomy.ui.fields.providers

import com.kylecorry.andromeda.core.units.Coordinate
import com.kylecorry.trail_sense.astronomy.domain.AstronomyService
import com.kylecorry.trail_sense.astronomy.ui.fields.AstroField
import com.kylecorry.trail_sense.astronomy.ui.fields.MeteorShowerField
import java.time.LocalDate

class MeteorShowerProvider : AstroFieldProvider {
    override fun getFields(date: LocalDate, location: Coordinate): List<AstroField> {
        val astronomyService = AstronomyService()
        val meteorShower = astronomyService.getMeteorShower(location, date) ?: return emptyList()
        return listOf(MeteorShowerField(date, meteorShower))
    }
}