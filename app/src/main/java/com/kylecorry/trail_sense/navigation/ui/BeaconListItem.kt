package com.kylecorry.trail_sense.navigation.ui

import android.view.View
import android.widget.PopupMenu
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.ListItemBeaconBinding
import com.kylecorry.trail_sense.navigation.domain.NavigationService
import com.kylecorry.trail_sense.navigation.infrastructure.database.BeaconRepo
import com.kylecorry.trail_sense.navigation.infrastructure.share.BeaconCopy
import com.kylecorry.trail_sense.navigation.infrastructure.share.BeaconGeoSender
import com.kylecorry.trail_sense.navigation.infrastructure.share.BeaconSharesheet
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trailsensecore.domain.geo.Coordinate
import com.kylecorry.trailsensecore.domain.navigation.Beacon
import com.kylecorry.trailsensecore.infrastructure.persistence.Clipboard
import com.kylecorry.trailsensecore.infrastructure.system.UiUtils

class BeaconListItem(
    private val view: View,
    private val beacon: Beacon,
    private val repo: BeaconRepo,
    myLocation: Coordinate
) {

    var onNavigate: () -> Unit = {}
    var onDeleted: () -> Unit = {}
    var onEdit: () -> Unit = {}

    private val navigationService = NavigationService()
    private val formatservice by lazy { FormatService(view.context) }
    private val prefs by lazy { UserPreferences(view.context) }

    init {
        val binding = ListItemBeaconBinding.bind(view)

        binding.beaconName.text = beacon.name
        binding.beaconImage.setImageResource(R.drawable.ic_location)
        var beaconVisibility = beacon.visible
        val distance = navigationService.navigate(beacon.coordinate, myLocation, 0f).distance
        binding.beaconSummary.text = formatservice.formatLargeDistance(distance)
        if (!prefs.navigation.showMultipleBeacons) {
            binding.visibleBtn.visibility = View.GONE
        } else {
            binding.visibleBtn.visibility = View.VISIBLE
        }
        if (beaconVisibility) {
            binding.visibleBtn.setImageResource(R.drawable.ic_visible)
        } else {
            binding.visibleBtn.setImageResource(R.drawable.ic_not_visible)
        }

        binding.visibleBtn.setOnClickListener {
            val newBeacon = beacon.copy(visible = !beaconVisibility)
            repo.add(newBeacon)
            beaconVisibility = newBeacon.visible
            if (beaconVisibility) {
                binding.visibleBtn.setImageResource(R.drawable.ic_visible)
            } else {
                binding.visibleBtn.setImageResource(R.drawable.ic_not_visible)
            }
        }

        view.setOnClickListener {
            onNavigate()
        }


        val menuListener = PopupMenu.OnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_send -> {
                    val sender = BeaconSharesheet(view.context)
                    sender.send(beacon)
                }
                R.id.action_copy -> {
                    val sender = BeaconCopy(view.context, Clipboard(view.context), prefs)
                    sender.send(beacon)
                }
                R.id.action_map -> {
                    val sender = BeaconGeoSender(view.context)
                    sender.send(beacon)
                }
                R.id.action_edit_beacon -> {
                    onEdit()
                }
                R.id.action_delete_beacon -> {
                    UiUtils.alertWithCancel(
                        view.context,
                        view.context.getString(R.string.delete_beacon),
                        beacon.name,
                        view.context.getString(R.string.dialog_ok),
                        view.context.getString(R.string.dialog_cancel)
                    ) { cancelled ->
                        if (!cancelled) {
                            repo.delete(beacon)
                            onDeleted()
                        }
                    }
                }
            }
            true
        }

        binding.beaconMenuBtn.setOnClickListener {
            val popup = PopupMenu(it.context, it)
            val inflater = popup.menuInflater
            inflater.inflate(R.menu.beacon_item_menu, popup.menu)
            popup.setOnMenuItemClickListener(menuListener)
            popup.show()
        }
    }

}