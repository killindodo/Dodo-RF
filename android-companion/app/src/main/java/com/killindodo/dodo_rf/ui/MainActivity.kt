package com.killindodo.dodo_rf.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.killindodo.dodo_rf.R
import com.killindodo.dodo_rf.databinding.ActivityMainBinding
import com.killindodo.dodo_rf.databinding.DialogCustomFreqBinding
import com.killindodo.dodo_rf.databinding.DialogJsonPayloadBinding
import com.killindodo.dodo_rf.databinding.DialogManualSignalBinding
import com.killindodo.dodo_rf.databinding.DialogSaveSignalBinding
import com.killindodo.dodo_rf.databinding.DialogSettingsBinding
import com.killindodo.dodo_rf.model.CapturedSignal
import com.killindodo.dodo_rf.network.WifiBindState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    private lateinit var capturedAdapter: CapturedSignalAdapter
    private lateinit var storedAdapter: StoredSignalAdapter

    private var currentTab: TabMode = TabMode.SNIFFER

    enum class TabMode {
        SNIFFER,
        VAULT
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.wifiBinder.bindToCurrentWifi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupRecyclerViews()
        setupListeners()
        observeViewModel()
        checkPermissions()
    }

    private fun setupRecyclerViews() {
        capturedAdapter = CapturedSignalAdapter { signal ->
            showSaveDialog(signal.code, signal.frequency, signal.bits, signal.protocol)
        }
        binding.rvCaptured.adapter = capturedAdapter

        storedAdapter = StoredSignalAdapter(
            onReplayClick = { signal ->
                viewModel.replaySignal(signal)
            },
            onDeleteClick = { signal ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Remote")
                    .setMessage("Remove \"${signal.name}\" from Dodo-RF vault?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteStoredSignal(signal.id)
                    }
                    .show()
            }
        )
        binding.rvStored.adapter = storedAdapter
    }

    private fun setupListeners() {
        // Tab switching
        binding.tabSniffer.setOnClickListener {
            switchTab(TabMode.SNIFFER)
        }
        binding.tabVault.setOnClickListener {
            switchTab(TabMode.VAULT)
            viewModel.refreshStoredSignals()
        }

        // Connection Pill click -> Re-bind or test connection
        binding.layoutConnectionPill.setOnClickListener {
            viewModel.wifiBinder.bindToCurrentWifi()
            viewModel.refreshStoredSignals()
            Toast.makeText(this, "Checking Dodo-RF AP binding...", Toast.LENGTH_SHORT).show()
        }

        binding.btnBindWifiPrompt.setOnClickListener {
            viewModel.triggerAutoConnect()
        }

        // Settings Dialog
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // Frequency Quick Tuning Buttons
        binding.btnFreq315.setOnClickListener { viewModel.tuneFrequency(315.00) }
        binding.btnFreq433.setOnClickListener { viewModel.tuneFrequency(433.92) }
        binding.btnFreq868.setOnClickListener { viewModel.tuneFrequency(868.30) }
        binding.btnFreq915.setOnClickListener { viewModel.tuneFrequency(915.00) }
        binding.btnFreqCustom.setOnClickListener { showCustomFreqDialog() }

        // Sniffer controls
        binding.btnPauseSniffer.setOnClickListener {
            val nextState = !viewModel.isSnifferActive.value
            viewModel.toggleSniffer(nextState)
            binding.btnPauseSniffer.text = if (nextState) "Pause" else "Resume"
        }

        binding.btnClearSniffer.setOnClickListener {
            viewModel.clearCapturedSignals()
        }

        // Vault controls
        binding.btnAddManualSignal.setOnClickListener {
            showManualSignalDialog()
        }

        binding.btnRefreshVault.setOnClickListener {
            viewModel.refreshStoredSignals()
        }
    }

    private fun switchTab(mode: TabMode) {
        currentTab = mode
        if (mode == TabMode.SNIFFER) {
            binding.tabSniffer.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_green_dim))
            binding.tabSniffer.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
            binding.tabSniffer.strokeColor = ContextCompat.getColorStateList(this, R.color.neon_green)
            binding.tabSniffer.strokeWidth = 3

            binding.tabVault.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_card))
            binding.tabVault.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.tabVault.strokeColor = ContextCompat.getColorStateList(this, R.color.border_subtle)
            binding.tabVault.strokeWidth = 2

            binding.layoutSnifferBar.visibility = View.VISIBLE
            binding.layoutVaultBar.visibility = View.GONE
            binding.containerSniffer.visibility = View.VISIBLE
            binding.containerVault.visibility = View.GONE
        } else {
            binding.tabVault.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_cyan_dim))
            binding.tabVault.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
            binding.tabVault.strokeColor = ContextCompat.getColorStateList(this, R.color.neon_cyan)
            binding.tabVault.strokeWidth = 3

            binding.tabSniffer.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_card))
            binding.tabSniffer.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.tabSniffer.strokeColor = ContextCompat.getColorStateList(this, R.color.border_subtle)
            binding.tabSniffer.strokeWidth = 2

            binding.layoutSnifferBar.visibility = View.GONE
            binding.layoutVaultBar.visibility = View.VISIBLE
            binding.containerSniffer.visibility = View.GONE
            binding.containerVault.visibility = View.VISIBLE
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Connection Status
                launch {
                    viewModel.connectionStatus.collectLatest { status ->
                        when (status) {
                            ConnectionStatus.CONNECTED -> {
                                binding.dotStatus.backgroundTintList =
                                    ContextCompat.getColorStateList(this@MainActivity, R.color.neon_green)
                                val lat = viewModel.latencyMs.value
                                binding.tvConnectionStatus.text = if (lat != null) "Online (${lat}ms)" else "Online"
                                binding.tvConnectionStatus.setTextColor(
                                    ContextCompat.getColor(this@MainActivity, R.color.neon_green)
                                )
                                binding.btnBindWifiPrompt.visibility = View.GONE
                            }
                            ConnectionStatus.CONNECTING -> {
                                binding.dotStatus.backgroundTintList =
                                    ContextCompat.getColorStateList(this@MainActivity, R.color.warn_amber)
                                binding.tvConnectionStatus.text = "Binding AP..."
                                binding.tvConnectionStatus.setTextColor(
                                    ContextCompat.getColor(this@MainActivity, R.color.warn_amber)
                                )
                            }
                            ConnectionStatus.DISCONNECTED -> {
                                binding.dotStatus.backgroundTintList =
                                    ContextCompat.getColorStateList(this@MainActivity, R.color.alert_red)
                                binding.tvConnectionStatus.text = "Offline"
                                binding.tvConnectionStatus.setTextColor(
                                    ContextCompat.getColor(this@MainActivity, R.color.alert_red)
                                )
                                binding.btnBindWifiPrompt.visibility = View.VISIBLE
                            }
                        }
                    }
                }

                // Current Frequency
                launch {
                    viewModel.currentFreq.collectLatest { freq ->
                        val freqText = String.format(Locale.US, "%.2f MHz", freq)
                        binding.tvActiveFreq.text = freqText

                        // Highlight matching quick button
                        updateFreqButtonsHighlight(freq)
                    }
                }

                // Transmitting banner
                launch {
                    viewModel.isTransmitting.collectLatest { isTx ->
                        binding.bannerTransmitting.visibility = if (isTx) View.VISIBLE else View.GONE
                    }
                }

                // Captured Signals
                launch {
                    viewModel.capturedSignals.collectLatest { list ->
                        capturedAdapter.submitList(list)
                        binding.emptySnifferState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        binding.tvInterceptCount.text = "Intercepted: ${list.size} packets"
                    }
                }

                // Stored Signals
                launch {
                    viewModel.storedSignals.collectLatest { list ->
                        storedAdapter.submitList(list)
                        binding.emptyVaultState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        binding.tvVaultCount.text = "Stored Remotes: ${list.size}"
                    }
                }

                // UI Events
                launch {
                    viewModel.uiEvents.collectLatest { event ->
                        when (event) {
                            is UiEvent.ShowToast -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            }
                            is UiEvent.ShowSnackbar -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG).show()
                            }
                            is UiEvent.PromptSave -> {
                                showSaveDialog(event.code, event.freq, event.bits, event.protocol)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateFreqButtonsHighlight(freq: Double) {
        val cyan = ContextCompat.getColor(this, R.color.neon_cyan)
        val textSec = ContextCompat.getColor(this, R.color.text_primary)

        binding.btnFreq315.setTextColor(if (kotlin.math.abs(freq - 315.00) < 0.05) cyan else textSec)
        binding.btnFreq433.setTextColor(if (kotlin.math.abs(freq - 433.92) < 0.05) cyan else textSec)
        binding.btnFreq868.setTextColor(if (kotlin.math.abs(freq - 868.30) < 0.05) cyan else textSec)
        binding.btnFreq915.setTextColor(if (kotlin.math.abs(freq - 915.00) < 0.05) cyan else textSec)
    }

    private fun showSaveDialog(code: String, freq: Double, bits: Int, protocol: Int) {
        val dialogBinding = DialogSaveSignalBinding.inflate(LayoutInflater.from(this))
        dialogBinding.tvDialogDetails.text = String.format(
            Locale.US,
            "Code: %s | %.2f MHz | %d-bit | Prot: %d",
            code,
            freq,
            bits,
            protocol
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSaveConfirm.setOnClickListener {
            val name = dialogBinding.etSignalName.text.toString().trim()
            if (name.isEmpty()) {
                dialogBinding.tilSignalName.error = "Please enter a name"
                return@setOnClickListener
            }
            viewModel.saveSignal(name, code, freq)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showCustomFreqDialog() {
        val dialogBinding = DialogCustomFreqBinding.inflate(LayoutInflater.from(this))
        dialogBinding.etCustomFreq.setText(String.format(Locale.US, "%.2f", viewModel.currentFreq.value))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelFreq.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnApplyFreq.setOnClickListener {
            val input = dialogBinding.etCustomFreq.text.toString().trim()
            val mhz = input.toDoubleOrNull()
            if (mhz == null || mhz < 300.0 || mhz > 928.0) {
                dialogBinding.tilCustomFreq.error = "Enter valid freq between 300 & 928 MHz"
                return@setOnClickListener
            }
            viewModel.tuneFrequency(mhz)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showManualSignalDialog() {
        val dialogBinding = DialogManualSignalBinding.inflate(LayoutInflater.from(this))
        dialogBinding.etManualFreq.setText(String.format(Locale.US, "%.2f", viewModel.currentFreq.value))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelManual.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSaveManual.setOnClickListener {
            val name = dialogBinding.etManualName.text.toString().trim()
            val code = dialogBinding.etManualCode.text.toString().trim()
            val freq = dialogBinding.etManualFreq.text.toString().trim().toDoubleOrNull() ?: 433.92

            if (name.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "Name and code are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val formattedCode = if (!code.startsWith("0x", ignoreCase = true)) "0x$code" else code
            viewModel.saveSignal(name, formattedCode, freq)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSettingsDialog() {
        val dialogBinding = DialogSettingsBinding.inflate(LayoutInflater.from(this))
        dialogBinding.etIp.setText(viewModel.targetHost)
        dialogBinding.switchSound.isChecked = viewModel.feedbackManager.soundEnabled
        dialogBinding.switchHaptics.isChecked = viewModel.feedbackManager.hapticEnabled

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            viewModel.feedbackManager.soundEnabled = isChecked
        }

        dialogBinding.switchHaptics.setOnCheckedChangeListener { _, isChecked ->
            viewModel.feedbackManager.hapticEnabled = isChecked
        }

        dialogBinding.btnExportVault.setOnClickListener {
            val json = viewModel.exportSignalsJson()
            showJsonDialog("Exported Vault JSON", json, isImport = false)
        }

        dialogBinding.btnImportVault.setOnClickListener {
            showJsonDialog("Import Signals JSON", "[]", isImport = true)
        }

        dialogBinding.btnApplySettings.setOnClickListener {
            val ip = dialogBinding.etIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                viewModel.setTargetHost(ip)
            }
            dialog.dismiss()
            Toast.makeText(this, "Settings updated", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showJsonDialog(title: String, initialJson: String, isImport: Boolean) {
        val dialogBinding = DialogJsonPayloadBinding.inflate(LayoutInflater.from(this))
        dialogBinding.tvJsonDialogTitle.text = title
        dialogBinding.etJson.setText(initialJson)
        dialogBinding.btnActionJson.text = if (isImport) "Import Database" else "Copy to Clipboard"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelJson.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnActionJson.setOnClickListener {
            if (isImport) {
                val inputJson = dialogBinding.etJson.text.toString().trim()
                if (inputJson.isNotEmpty()) {
                    viewModel.importSignalsJson(inputJson)
                    dialog.dismiss()
                }
            } else {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Dodo-RF Vault", dialogBinding.etJson.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.wifiBinder.bindToCurrentWifi()
        }
    }
}
