package com.cesoft.cesble.adapter

import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cesoft.cesble.R

class BTLEDeviceAdapter(private val dataSet: List<ScanResult>, private val onClickListener: View.OnClickListener)
    : RecyclerView.Adapter<BTViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BTViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.ble_adapter_layout, parent, false) as TextView
        return BTViewHolder(textView)
    }

    override fun onBindViewHolder(holder: BTViewHolder, position: Int) {
        val headSet = if(dataSet[position].device.bluetoothClass.deviceClass.toString() == "240404") "*" else " "
        val btType = "LE > "
        holder.textView.text = headSet + btType + dataSet[position].scanRecord?.deviceName
android.util.Log.e("TAG", "onBindViewHolder------------------------------------------------------"+dataSet[position].scanRecord?.deviceName+", "+dataSet[position].device.name)
        holder.textView.tag = position
        holder.textView.setOnClickListener(onClickListener)
    }

    override fun getItemCount() = dataSet.size

    fun getItemAt(i: Int): ScanResult = dataSet[i]

//    fun add(item: ScanResult) {
//        dataSet.add(item)
//    }
}