package com.multiping

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.multiping.data.CheckMode
import com.multiping.data.Host
import com.multiping.data.NetworkState
import com.multiping.service.PingService
import java.text.SimpleDateFormat
import java.util.*

class HostAdapter(
    private val appContext: Context,
    private val onEdit:      (Host) -> Unit,
    private val onDelete:    (Host) -> Unit,
    private val onToggle:    (Host, Boolean) -> Unit,
    private val onFavourite: (Host) -> Unit
) : ListAdapter<Host, HostAdapter.VH>(DIFF) {

    // Текущее состояние сети — обновляется из MainActivity
    @Volatile private var networkConnected: Boolean = true

    fun setNetworkState(state: NetworkState) {
        networkConnected = (state == NetworkState.CONNECTED)
    }

    // Handler для тиканья обратного отсчёта — один на весь адаптер
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var recyclerView: RecyclerView? = null

    private fun localizedContext(): Context {
        val prefs  = appContext.getSharedPreferences("multiping_prefs", Context.MODE_PRIVATE)
        val lang   = prefs.getString(PingService.KEY_LANGUAGE, "ru") ?: "ru"
        val locale = Locale(lang)
        val config = Configuration(appContext.resources.configuration)
        config.setLocale(locale)
        return appContext.createConfigurationContext(config)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val statusDot:  View      = view.findViewById(R.id.statusDot)
        val tvAddress:  TextView  = view.findViewById(R.id.tvAddress)
        val tvLastSeen: TextView  = view.findViewById(R.id.tvLastSeen)
        val tvLabel:    TextView  = view.findViewById(R.id.tvLabel)
        val btnFav:     ImageView = view.findViewById(R.id.btnFavourite)
        val swEnabled:  Switch    = view.findViewById(R.id.swEnabled)
        val btnEdit:    ImageView = view.findViewById(R.id.btnEdit)
        val btnDelete:  ImageView = view.findViewById(R.id.btnDelete)
        val tvMode:     TextView  = view.findViewById(R.id.tvMode)
        val tvPing:     TextView  = view.findViewById(R.id.tvPing)
        val tvAvg:      TextView  = view.findViewById(R.id.tvAvg)

        // Хост, привязанный к этому VH — нужен тику
        var boundHost: Host? = null
    }

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        recyclerView = rv
        startTicking()
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        super.onDetachedFromRecyclerView(rv)
        stopTicking()
        recyclerView = null
    }

    // ── Тик каждые 500 мс — обновляем только tvLastSeen у видимых VH ───
    private fun startTicking() {
        val runnable = object : Runnable {
            override fun run() {
                recyclerView?.let { rv ->
                    val lm = rv.layoutManager ?: return
                    val first = (lm as? androidx.recyclerview.widget.LinearLayoutManager)
                        ?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                    val last  = (lm as? androidx.recyclerview.widget.LinearLayoutManager)
                        ?.findLastVisibleItemPosition() ?: RecyclerView.NO_POSITION
                    if (first != RecyclerView.NO_POSITION) {
                        for (i in first..last) {
                            val vh = rv.findViewHolderForAdapterPosition(i) as? VH ?: continue
                            val host = vh.boundHost ?: continue
                            vh.tvLastSeen.text = buildTimeLine(host, localizedContext())
                        }
                    }
                }
                handler.postDelayed(this, 500)
            }
        }
        tickRunnable = runnable
        handler.post(runnable)
    }

    private fun stopTicking() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    // ── Форматирование строки времени ─────────────────────────────────────
    private fun buildTimeLine(host: Host, lc: Context): String {
        if (!host.isEnabled || host.lastCheckedAt <= 0L)
            return lc.getString(R.string.status_no_data)

        val lastFmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
            .format(Date(host.lastCheckedAt))

        // Нет сети — показываем только время последнего опроса, без отсчёта
        if (!networkConnected) return lastFmt

        val nextAt    = host.lastCheckedAt + host.intervalMs
        val remaining = nextAt - System.currentTimeMillis()

        return if (remaining <= 0L) {
            "$lastFmt → ⏱ …"
        } else {
            val totalSec = (remaining / 1000L).coerceAtLeast(0L)
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            val countdown = if (h > 0) "%02d:%02d:%02d".format(h, m, s)
                            else "%02d:%02d".format(m, s)
            "$lastFmt → ⏱ $countdown"
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_host, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val host = getItem(position)
        holder.boundHost = host
        val lc = localizedContext()

        // ── Строка 1: статус + адрес + время ─────────────────────────────
        holder.statusDot.setBackgroundColor(when {
            !host.isEnabled -> Color.GRAY
            host.lastStatus -> Color.GREEN
            else            -> Color.RED
        })
        holder.tvAddress.text  = host.address
        holder.tvLastSeen.text = buildTimeLine(host, lc)

        // ── Строка 2: название + иконки ───────────────────────────────────
        if (host.label.isBlank()) {
            holder.tvLabel.visibility = View.INVISIBLE
            holder.tvLabel.text = "\u00A0"
        } else {
            holder.tvLabel.visibility = View.VISIBLE
            holder.tvLabel.text = host.label
        }

        holder.btnFav.setImageResource(
            if (host.isFavourite) android.R.drawable.btn_star_big_on
            else                  android.R.drawable.btn_star_big_off
        )
        holder.btnFav.setOnClickListener { onFavourite(host) }

        holder.swEnabled.setOnCheckedChangeListener(null)
        holder.swEnabled.isChecked = host.isEnabled
        holder.swEnabled.setOnCheckedChangeListener { _, checked -> onToggle(host, checked) }

        holder.btnEdit.setOnClickListener   { onEdit(host) }
        holder.btnDelete.setOnClickListener { onDelete(host) }

        // ── Строка 3: режим + пинг + среднее ─────────────────────────────
        holder.tvMode.text = when (CheckMode.valueOf(host.checkMode)) {
            CheckMode.PING -> "PING"
            CheckMode.HTTP -> "HTTP ${host.httpMethod}"
            CheckMode.TCP  -> "TCP :${host.tcpPort}"
        }

        holder.tvPing.text = when {
            !host.isEnabled     -> lc.getString(R.string.status_off)
            host.lastPingMs < 0 -> lc.getString(R.string.status_no_data)
            host.lastStatus     -> lc.getString(R.string.ping_ms, host.lastPingMs)
            else                -> lc.getString(R.string.status_no_response)
        }

        if (host.avgWindowSec > 0 && host.avgPingMs >= 0 && host.isEnabled) {
            holder.tvAvg.visibility = View.VISIBLE
            holder.tvAvg.text = lc.getString(R.string.ping_avg, host.avgPingMs)
        } else {
            holder.tvAvg.visibility = View.GONE
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Host>() {
            override fun areItemsTheSame(a: Host, b: Host)    = a.id == b.id
            override fun areContentsTheSame(a: Host, b: Host) = a == b
        }
    }
}
