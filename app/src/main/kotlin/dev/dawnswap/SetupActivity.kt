package dev.dawnswap

import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dev.dawnswap.databinding.ActivitySetupBinding
import dev.dawnswap.logic.LaunchTarget
import dev.dawnswap.logic.MINUTES_PER_HOUR
import dev.dawnswap.logic.Slot
import dev.dawnswap.logic.SwapDecider
import dev.dawnswap.logic.SwapWindow
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Pick the two apps, pick the window, put the shortcut on the home screen. */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var repository: SwapRepository

    private val pickReal = registerForActivityResult(AppPickerActivity.Contract()) { chosen ->
        chosen?.let(LaunchTarget::app)?.let {
            repository.real = it
            onConfigChanged()
        }
    }

    private val pickDecoy = registerForActivityResult(AppPickerActivity.Contract()) { chosen ->
        chosen?.let(LaunchTarget::app)?.let {
            repository.decoy = it
            onConfigChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = SwapRepository(this)

        binding.pickRealButton.setOnClickListener { pickReal.launch(Unit) }
        binding.pickDecoyButton.setOnClickListener { pickDecoy.launch(Unit) }
        binding.addToHomeButton.setOnClickListener { addToHome() }
        binding.tryNowButton.setOnClickListener { tryNow() }

        binding.startTimeButton.setOnClickListener {
            pickTime(repository.window.startMinute) { minute ->
                updateWindow(startMinute = minute)
            }
        }
        binding.endTimeButton.setOnClickListener {
            pickTime(repository.window.endMinute) { minute ->
                updateWindow(endMinute = minute)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onPause() {
        super.onPause()
        applyTypedUrl(reportErrors = false)
    }

    // --- Rendering ---------------------------------------------------------------------

    private fun render() {
        val window = repository.window

        binding.realSummary.text = repository.real?.let { Launcher.labelFor(this, it) }
            ?: getString(R.string.not_set)
        binding.decoySummary.text = repository.decoy?.let { Launcher.labelFor(this, it) }
            ?: getString(R.string.not_set)

        binding.startTimeButton.text = format(window.startMinute)
        binding.endTimeButton.text = format(window.endMinute)

        val decoyIsWeb = repository.decoy is LaunchTarget.Web
        binding.replacementKind.setOnCheckedChangeListener(null)
        binding.replacementKind.check(
            if (decoyIsWeb) R.id.replacementUrlRadio else R.id.replacementAppRadio,
        )
        binding.replacementKind.setOnCheckedChangeListener { _, _ -> renderReplacementKind() }

        (repository.decoy as? LaunchTarget.Web)?.let { web ->
            if (binding.urlInput.text?.toString() != web.url) binding.urlInput.setText(web.url)
        }

        binding.enabledSwitch.bind(repository.enabled) { checked ->
            repository.enabled = checked
            onConfigChanged()
        }
        binding.swapModeSwitch.bind(repository.swapMode) { checked ->
            repository.swapMode = checked
            onConfigChanged()
        }

        renderReplacementKind()
        binding.statusText.text = status()
    }

    private fun renderReplacementKind() {
        val webChosen = binding.replacementUrlRadio.isChecked
        binding.urlLayout.isVisible(webChosen)
        binding.pickDecoyButton.isVisible(!webChosen)
        binding.decoySummary.isVisible(!webChosen)
    }

    private fun status(): String {
        val config = repository.config() ?: return getString(R.string.finish_setup_first)
        if (!config.enabled) return getString(R.string.status_off)

        val now = LocalDateTime.now()
        val decision = SwapDecider.decide(config, repository.lastConsumed, now, Slot.PRIMARY)

        return when {
            decision.armed -> getString(R.string.status_armed)
            config.window.contains(now) -> getString(R.string.status_used)
            else -> getString(R.string.status_waiting)
        }
    }

    // --- Actions -----------------------------------------------------------------------

    private fun addToHome() {
        if (!readyToPin()) return

        if (!ShortcutController.isPinningSupported(this)) {
            toast(R.string.pinning_unsupported)
            return
        }

        ShortcutController.activeSlots(this).forEach { ShortcutController.requestPin(this, it) }
        ArmScheduler.schedule(this)
        toast(R.string.pin_requested)
    }

    private fun tryNow() {
        if (!readyToPin()) return
        startActivity(TrampolineActivity.intentFor(this, Slot.PRIMARY))
    }

    private fun readyToPin(): Boolean {
        applyTypedUrl(reportErrors = true)
        if (repository.config() != null) return true

        toast(R.string.finish_setup_first)
        return false
    }

    /** Commits whatever is typed in the URL field, since it has no explicit save button. */
    private fun applyTypedUrl(reportErrors: Boolean) {
        if (!binding.replacementUrlRadio.isChecked) return

        val typed = binding.urlInput.text?.toString().orEmpty().trim()
        if (typed.isEmpty()) return

        val web = LaunchTarget.web(typed)
        when {
            web == null -> if (reportErrors) toast(R.string.invalid_url)
            web != repository.decoy -> {
                repository.decoy = web
                onConfigChanged()
            }
        }
    }

    private fun updateWindow(
        startMinute: Int = repository.window.startMinute,
        endMinute: Int = repository.window.endMinute,
    ) {
        repository.window = SwapWindow(startMinute, endMinute)
        onConfigChanged()
    }

    private fun onConfigChanged() {
        ArmScheduler.schedule(this)
        ShortcutController.refresh(this)
        render()
    }

    // --- Helpers -----------------------------------------------------------------------

    private fun pickTime(currentMinute: Int, onPicked: (Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(
                if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H,
            )
            .setHour(currentMinute / MINUTES_PER_HOUR)
            .setMinute(currentMinute % MINUTES_PER_HOUR)
            .build()

        picker.addOnPositiveButtonClickListener {
            onPicked(picker.hour * MINUTES_PER_HOUR + picker.minute)
        }
        picker.show(supportFragmentManager, "window-time")
    }

    private fun format(minuteOfDay: Int): String =
        LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
            .format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun toast(messageId: Int) =
        Toast.makeText(this, messageId, Toast.LENGTH_LONG).show()

    /** Sets state without the listener firing back and re-entering [render]. */
    private fun MaterialSwitch.bind(checked: Boolean, onChange: (Boolean) -> Unit) {
        setOnCheckedChangeListener(null)
        isChecked = checked
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }

    private fun View.isVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }
}
