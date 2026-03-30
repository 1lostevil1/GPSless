package com.example.gpslessclient

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gpslessclient.model.NetworkSnapshot
import com.example.gpslessclient.R

class SnapshotAdapter : RecyclerView.Adapter<SnapshotAdapter.ViewHolder>() {

    private val snapshots = mutableListOf<NetworkSnapshot>()

    fun addSnapshot(snapshot: NetworkSnapshot) {
        if (snapshots.size >= 5) {
            snapshots.clear()
        }
        snapshots.add(snapshot)
        notifyDataSetChanged()
    }

    fun clearSnapshots() {
        snapshots.clear()
        notifyDataSetChanged()
    }

    fun getSnapshots(): List<NetworkSnapshot> {
        return snapshots.toList()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_snapshot, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(snapshots[position])
    }

    override fun getItemCount(): Int = snapshots.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        private val tvWifi: TextView = itemView.findViewById(R.id.tvWifi)
        private val tvCellular: TextView = itemView.findViewById(R.id.tvCellular)
        private val tvBluetooth: TextView = itemView.findViewById(R.id.tvBluetooth)

        fun bind(snapshot: NetworkSnapshot) {
            snapshot.location?.let {
                tvLocation.text = String.format("%.6f, %.6f", it.latitude, it.longitude)
            } ?: run {
                tvLocation.text = "Нет данных"
            }

            tvWifi.text = "Wi-Fi: ${snapshot.wifiNetworks?.size ?: 0}"

            val cellularText = if (snapshot.cellularNetwork != null) "есть" else "нет"
            tvCellular.text = "Сотовая: $cellularText"

            tvBluetooth.text = "BT: ${snapshot.bluetoothDevices?.size ?: 0}"
        }
    }
}