package com.cesoft.cesble.adapter

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cesoft.cesble.R

class BTDeviceAdapter(private val dataSet: ArrayList<BluetoothDevice>,
                      private val onClickListener: View.OnClickListener,
                      private val onCreateContextMenuListener: View.OnCreateContextMenuListener)
    : RecyclerView.Adapter<BTViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BTViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.ble_adapter_layout, parent, false) as TextView
        return BTViewHolder(textView)
    }

    override fun onBindViewHolder(holder: BTViewHolder, position: Int) {

        //http://domoticx.com/bluetooth-class-of-device-lijst-cod/
        //bluetoothDevice.bluetoothClass.deviceClass == AUDIO_VIDEO_WEARABLE_HEADSET
        val headSet = if(dataSet[position].bluetoothClass.toString() == "240404") "*" else " "
        val btType = when(dataSet[position].type) {
            DEVICE_TYPE_LE          -> "  LE > "
            DEVICE_TYPE_DUAL        -> "Dual > "
            DEVICE_TYPE_CLASSIC     -> "Clss > "
            else                    -> "   ? > "
        }
        holder.textView.text = headSet + btType + dataSet[position].name
        holder.textView.tag = position
        holder.textView.setOnClickListener(onClickListener)
        holder.textView.setOnCreateContextMenuListener(onCreateContextMenuListener)
    }

    override fun getItemCount() = dataSet.size

    fun getItemAt(i: Int): BluetoothDevice = dataSet[i]

    fun add(item: BluetoothDevice) {
        dataSet.add(item)
    }
}