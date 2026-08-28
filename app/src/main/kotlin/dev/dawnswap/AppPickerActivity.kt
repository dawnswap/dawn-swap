package dev.dawnswap

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.dawnswap.databinding.ActivityAppPickerBinding
import dev.dawnswap.databinding.ItemAppBinding

private const val EXTRA_PACKAGE = "package"

/** Lists every launchable app and returns the chosen package name. */
class AppPickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = Adapter(launchableApps()) { chosen ->
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_PACKAGE, chosen))
            finish()
        }
    }

    /**
     * Resolved through the manifest's `<queries>` element rather than QUERY_ALL_PACKAGES,
     * so the app never asks for the sensitive "see everything installed" permission.
     */
    private fun launchableApps(): List<Entry> {
        val packages = packageManager
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return packages.queryIntentActivities(launchable, 0)
            .asSequence()
            .map { it.activityInfo.packageName to it.loadLabel(packages).toString() }
            .filter { (name, _) -> name != packageName }
            .distinctBy { (name, _) -> name }
            .map { (name, label) -> Entry(name, label) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private data class Entry(val packageName: String, val label: String)

    private class Adapter(
        private val entries: List<Entry>,
        private val onPick: (String) -> Unit,
    ) : RecyclerView.Adapter<Adapter.Row>() {

        class Row(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Row(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = entries.size

        override fun onBindViewHolder(holder: Row, position: Int) {
            val entry = entries[position]
            val packages = holder.binding.root.context.packageManager

            holder.binding.appLabel.text = entry.label
            holder.binding.appIcon.setImageDrawable(
                runCatching { packages.getApplicationIcon(entry.packageName) }.getOrNull(),
            )
            holder.binding.root.setOnClickListener { onPick(entry.packageName) }
        }
    }

    /** Lets a caller launch the picker and get a package name back. */
    class Contract : ActivityResultContract<Unit, String?>() {

        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(context, AppPickerActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?): String? =
            if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(EXTRA_PACKAGE) else null
    }
}
