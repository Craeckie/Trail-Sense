package com.kylecorry.trail_sense.settings

import android.os.Bundle
import androidx.preference.*
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.astronomy.infrastructure.receivers.SunsetAlarmReceiver
import com.kylecorry.trail_sense.shared.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AstronomySettingsFragment : PreferenceFragmentCompat() {

    @Inject lateinit var prefs: UserPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.astronomy_preferences, rootKey)

        preferenceScreen.findPreference<ListPreference>(getString(R.string.pref_sunset_alert_time))
            ?.setOnPreferenceClickListener { _ ->
                context?.apply {
                    sendBroadcast(SunsetAlarmReceiver.intent(this))
                }
                true
            }


    }

}