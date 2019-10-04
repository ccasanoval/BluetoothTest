package com.cesoft.cesble

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BTLEDeviceAdapter(private val dataSet: ArrayList<ScanResult>, private val onClickListener: View.OnClickListener)
    : RecyclerView.Adapter<BTLEDeviceAdapter.ViewHolder>() {

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.ble_adapter_layout, parent, false) as TextView
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = dataSet[position].scanRecord?.deviceName
android.util.Log.e("TAG", "onBindViewHolder------------------------------------------------------"+dataSet[position].scanRecord?.deviceName+", "+dataSet[position].device.name)
        holder.textView.tag = position
        holder.textView.setOnClickListener(onClickListener)
    }

    override fun getItemCount() = dataSet.size

    fun getItemAt(i: Int): ScanResult = dataSet[i]

    fun add(item: ScanResult) {
        dataSet.add(item)
    }
}