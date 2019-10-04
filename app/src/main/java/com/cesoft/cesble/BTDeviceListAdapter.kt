package com.cesoft.cesble

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BTDeviceListAdapter(private val dataSet: ArrayList<BluetoothDevice>, private val onClickListener: View.OnClickListener)
    : RecyclerView.Adapter<BTDeviceListAdapter.ViewHolder>() {

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.ble_adapter_layout, parent, false) as TextView
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = dataSet[position].name
//android.util.Log.e("TAG", "onBindViewHolder------------------------------------------------------"+dataSet[position].name)
        holder.textView.tag = position
        holder.textView.setOnClickListener(onClickListener)
    }

    override fun getItemCount() = dataSet.size

    fun getItemAt(i: Int): BluetoothDevice = dataSet[i]

    fun add(item: BluetoothDevice) {
        dataSet.add(item)
    }
}