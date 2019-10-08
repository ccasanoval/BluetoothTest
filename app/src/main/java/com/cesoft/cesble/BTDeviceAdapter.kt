package com.cesoft.cesble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BTDeviceAdapter(private val dataSet: ArrayList<BluetoothDevice>, private val onClickListener: View.OnClickListener)
    : RecyclerView.Adapter<BTDeviceAdapter.ViewHolder>() {

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.ble_adapter_layout, parent, false) as TextView
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        //http://domoticx.com/bluetooth-class-of-device-lijst-cod/
        //bluetoothDevice.bluetoothClass.deviceClass == AUDIO_VIDEO_WEARABLE_HEADSET
        val headSet = if(dataSet[position].bluetoothClass.deviceClass == 240404) "*" else " "
        val pre = when {
            dataSet[position].type == DEVICE_TYPE_LE        -> "  LE > "
            dataSet[position].type == DEVICE_TYPE_DUAL      -> "Dual > "
            dataSet[position].type == DEVICE_TYPE_CLASSIC   -> "Clss > "
            else                                            -> "  ?  > "
        }
        holder.textView.text = headSet + pre + dataSet[position].name
        holder.textView.tag = position
        holder.textView.setOnClickListener(onClickListener)
    }

    override fun getItemCount() = dataSet.size

    fun getItemAt(i: Int): BluetoothDevice = dataSet[i]

    fun add(item: BluetoothDevice) {
        dataSet.add(item)
    }
}