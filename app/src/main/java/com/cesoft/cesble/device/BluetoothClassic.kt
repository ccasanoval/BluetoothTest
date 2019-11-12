package com.cesoft.cesble.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.koin.core.KoinComponent
import org.koin.core.inject


//https://www.thedroidsonroids.com/blog/bluetooth-classic-vs-bluetooth-low-energy-on-android-hints-implementation-steps
class BluetoothClassic : KoinComponent {

    companion object {
        private val TAG = BluetoothClassic::class.java.simpleName
    }

    private val appContext: Context by inject()
    private val bluetooth : Bluetooth by inject()

    private var currentScanned = 0
    private val dataSet = ArrayList<BluetoothDevice>()

    interface Callback {
        fun onDeviceFound(bluetoothDevice: BluetoothDevice)
        fun onDiscoveryFinished()
    }

    fun startScan(callback: Callback) {

        currentScanned = 0

        val scanningBroadcastReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (BluetoothDevice.ACTION_FOUND == intent.action) {
                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!

                    val type = when(device.type) {
                        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                        BluetoothDevice.DEVICE_TYPE_LE -> "LowEnrgy"
                        BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                        else -> " ? "
                    }

                    android.util.Log.e(TAG, "onDeviceFound---------------------------------------------------$device "
                                +" : name="+device.name
                                +" : address="+device.address
                                +" : class="+device.bluetoothClass
                                +" : type="+type
                                +" : "+device.bluetoothClass.deviceClass       //AUDIO_VIDEO_WEARABLE_HEADSET
                                +" : "+device.bluetoothClass.majorDeviceClass  //1024 = 0x400
                                )

                    if(device.name == null || device.name.isEmpty())return
                    if(dataSet.contains(device))return

                    if(currentScanned == 0) {
                        dataSet.clear()
                        dataSet.add(device)
                    }
                    else {
                        dataSet.add(device)
                    }
                    currentScanned++

                    //TODO: return one by one or all together
                    callback.onDeviceFound(device)
                }
                else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == intent.action) {
                    android.util.Log.e(TAG, "onDiscoveryFinished---------------------------------------------------")
                    context.unregisterReceiver(this)
                    callback.onDiscoveryFinished()
                }
            }
        }

        val scanningItentFilter = IntentFilter()
        scanningItentFilter.addAction(BluetoothDevice.ACTION_FOUND)
        scanningItentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        appContext.registerReceiver(scanningBroadcastReceiver, scanningItentFilter)

        bluetooth.adapter?.startDiscovery()
    }

    fun stopScan() {
        bluetooth.adapter?.cancelDiscovery()
    }
}