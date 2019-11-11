package com.cesoft.cesble.device

import android.bluetooth.*
import android.content.Context
import org.koin.core.KoinComponent
import org.koin.core.inject


class Bluetooth : KoinComponent {

    companion object {
        private val TAG = Bluetooth::class.java.simpleName
    }

    private val appContext: Context by inject()

    //private val bluetoothAdapter2 = BluetoothAdapter.getDefaultAdapter()
    val adapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    val isEnabled: Boolean
        get() = adapter != null && adapter!!.isEnabled
    val isDisabled: Boolean
        get() = ! isEnabled

    val pairedDevices: MutableCollection<BluetoothDevice>?
        get() = adapter?.bondedDevices


    fun connect(device: BluetoothDevice) {
        device.connectGatt(appContext, true, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                android.util.Log.e(TAG, "connect:onConnectionStateChange-------------------------------------status=$status, newState=$newState")
                if(status == BluetoothProfile.STATE_CONNECTED)
                    android.util.Log.e(TAG, "connect:onConnectionStateChange-------------------------------------STATE_CONNECTED")
                //BluetoothAdapter.STATE_CONNECTED
            }
        })
    }


    var headset: BluetoothHeadset? = null
    var device: BluetoothDevice? = null
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            android.util.Log.e(TAG, "profileListener:onServiceConnected-------------------------------------$profile")
            if(profile == BluetoothProfile.HEADSET) {
                headset = proxy as BluetoothHeadset
                device = getConnectedHeadset()
            }
        }
        override fun onServiceDisconnected(profile: Int) {
            android.util.Log.e(TAG, "profileListener:onServiceDisconnected-------------------------------------$profile")
            if (profile == BluetoothProfile.HEADSET) {
                headset = null
            }
        }
    }
    private fun getConnectedHeadset(): BluetoothDevice? {
        headset?.let {
            val devices = it.connectedDevices
            return if(devices.isNotEmpty())
                devices[0]
            else null
        } ?: run { return null }
    }
    fun getProfileProxy() {
        adapter?.getProfileProxy(appContext, profileListener, BluetoothProfile.HEADSET)
    }

}