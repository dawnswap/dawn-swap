package dev.dawnswap

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.dawnswap.logic.Slot
import dev.dawnswap.logic.SwapDecider
import java.time.LocalDateTime

/**
 * The invisible activity that actually occupies the home-screen position.
 *
 * Your thumb lands on a spot; this is what is under it. It decides at *tap* time rather
 * than at alarm time, so a delayed alarm can leave an icon looking stale but can never
 * open the wrong app.
 */
class TrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route()
        // Finish inside onCreate - the window is never shown.
        finish()
    }

    private fun route() {
        val repository = SwapRepository(this)
        val config = repository.config()
        if (config == null) {
            openSetup()
            return
        }

        val now = LocalDateTime.now()
        val decision = SwapDecider.decide(config, repository.lastConsumed, now, requestedSlot())

        // Consumed before launching, synchronously. Two taps in quick succession then cannot
        // both find the swap armed, and being killed mid-flow costs one redirect rather than
        // leaving a swap that keeps firing all morning. It fails closed.
        decision.consumes?.let(repository::markConsumed)

        val launch = Launcher.intentFor(this, decision.target)
        if (launch == null || !open(launch)) {
            Toast.makeText(this, R.string.target_unavailable, Toast.LENGTH_LONG).show()
            openSetup()
            return
        }

        if (decision.consumes != null) {
            ShortcutController.refresh(this)
        }
    }

    private fun open(launch: Intent): Boolean = try {
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (notFound: ActivityNotFoundException) {
        false
    }

    private fun requestedSlot(): Slot {
        val name = intent?.getStringExtra(EXTRA_SLOT)
        return Slot.entries.firstOrNull { it.name == name } ?: Slot.PRIMARY
    }

    private fun openSetup() {
        startActivity(
            Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        private const val EXTRA_SLOT = "slot"

        fun intentFor(context: Context, slot: Slot): Intent =
            Intent(context, TrampolineActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_SLOT, slot.name)
    }
}
