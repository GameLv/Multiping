package com.multiping

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.multiping.data.CheckMode
import com.multiping.data.Host

class AddHostBottomSheet : BottomSheetDialogFragment() {

    var onSave: ((Host) -> Unit)? = null
    private var editHost: Host? = null
    private var vm: MainViewModel? = null

    companion object {
        fun newInstance(host: Host? = null, vm: MainViewModel? = null) =
            AddHostBottomSheet().apply { editHost = host; this.vm = vm }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_add_host, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvTitle       = view.findViewById<TextView>(R.id.tvSheetTitle)
        val etAddress     = view.findViewById<EditText>(R.id.etAddress)
        val etLabel       = view.findViewById<EditText>(R.id.etLabel)
        val etInterval    = view.findViewById<EditText>(R.id.etInterval)
        val etTimeout     = view.findViewById<EditText>(R.id.etTimeout)
        val etAvgWindow   = view.findViewById<EditText>(R.id.etAvgWindow)
        val rgCheckMode   = view.findViewById<RadioGroup>(R.id.rgCheckMode)
        val rowHttp       = view.findViewById<View>(R.id.rowHttp)
        val spinnerMethod = view.findViewById<Spinner>(R.id.spinnerMethod)
        val rowTcp        = view.findViewById<View>(R.id.rowTcp)
        val etTcpPort     = view.findViewById<EditText>(R.id.etTcpPort)
        val btnSave       = view.findViewById<Button>(R.id.btnSave)
        val btnCancel     = view.findViewById<Button>(R.id.btnCancel)

        tvTitle.text = if (editHost == null) getString(R.string.sheet_title_add)
                       else getString(R.string.sheet_title_edit)

        // Spinner HTTP методов
        ArrayAdapter.createFromResource(
            requireContext(), R.array.http_methods,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerMethod.adapter = adapter
        }

        fun applyMode(mode: CheckMode) {
            rowHttp.visibility = if (mode == CheckMode.HTTP) View.VISIBLE else View.GONE
            rowTcp.visibility  = if (mode == CheckMode.TCP)  View.VISIBLE else View.GONE
        }

        rgCheckMode.setOnCheckedChangeListener { _, checkedId ->
            applyMode(when (checkedId) {
                R.id.rbHttp -> CheckMode.HTTP
                R.id.rbTcp  -> CheckMode.TCP
                else        -> CheckMode.PING
            })
        }

        // Заполнение при редактировании
        val h = editHost
        if (h != null) {
            etAddress.setText(h.address)
            etLabel.setText(h.label)
            etInterval.setText((h.intervalMs / 1000).toString())
            etTimeout.setText((h.timeoutMs / 1000).toString())
            etAvgWindow.setText(h.avgWindowSec.toString())
            etTcpPort.setText(h.tcpPort.toString())

            val mode = CheckMode.valueOf(h.checkMode)
            rgCheckMode.check(when (mode) {
                CheckMode.HTTP -> R.id.rbHttp
                CheckMode.TCP  -> R.id.rbTcp
                CheckMode.PING -> R.id.rbPing
            })
            applyMode(mode)

            val methods = resources.getStringArray(R.array.http_methods)
            spinnerMethod.setSelection(methods.indexOf(h.httpMethod).coerceAtLeast(0))
        } else {
            // Новый хост — defaults из настроек
            vm?.let { model ->
                etInterval.setText(model.defaultInterval().toString())
                etTimeout.setText(model.defaultTimeout().toString())
                etAvgWindow.setText(model.defaultAvgWindow().toString())
                val defMode = model.defaultCheckMode()
                rgCheckMode.check(when (defMode) {
                    CheckMode.HTTP -> R.id.rbHttp
                    CheckMode.TCP  -> R.id.rbTcp
                    CheckMode.PING -> R.id.rbPing
                })
                applyMode(defMode)
            } ?: run {
                rgCheckMode.check(R.id.rbPing)
                applyMode(CheckMode.PING)
            }
        }

        btnSave.setOnClickListener {
            val address = etAddress.text.toString().trim()
            if (address.isBlank()) {
                etAddress.error = requireContext().getString(R.string.error_address_empty)
                return@setOnClickListener
            }

            val selectedMode = when (rgCheckMode.checkedRadioButtonId) {
                R.id.rbHttp -> CheckMode.HTTP
                R.id.rbTcp  -> CheckMode.TCP
                else        -> CheckMode.PING
            }

            val tcpPort = etTcpPort.text.toString().toIntOrNull()?.coerceIn(1, 65535) ?: 80

            val intervalSec = etInterval.text.toString().toLongOrNull() ?: 5L
            val timeoutSec  = etTimeout.text.toString().toIntOrNull() ?: 3
            val avgWindow   = etAvgWindow.text.toString().toIntOrNull() ?: 60

            val host = (editHost ?: Host(address = "")).copy(
                address      = address,
                label        = etLabel.text.toString().trim(),
                intervalMs   = intervalSec.coerceIn(1, 3600) * 1000L,
                timeoutMs    = timeoutSec.coerceIn(1, 60) * 1000,
                avgWindowSec = avgWindow.coerceIn(0, 3600),
                checkMode    = selectedMode.name,
                httpMethod   = spinnerMethod.selectedItem?.toString() ?: "GET",
                tcpPort      = tcpPort
            )
            onSave?.invoke(host)
            dismiss()
        }

        btnCancel.setOnClickListener { dismiss() }
    }
}
