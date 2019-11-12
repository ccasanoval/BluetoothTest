package com.cesoft.cesble.device

import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    fun switchOnOf() {
        if(isEnabled)
            adapter?.disable()
        else
            adapter?.enable()
    }
    fun reset() {
        if(isEnabled) {
            adapter?.disable()
            GlobalScope.launch(Dispatchers.IO) {
                delay(10000)
                adapter?.enable()
            }
        }
        else
            adapter?.enable()
    }

    val pairedDevices: MutableCollection<BluetoothDevice>?
        get() = adapter?.bondedDevices


    fun connect(device: BluetoothDevice,
                gattCallback: BluetoothGattCallback,
                transport: Int=BluetoothDevice.TRANSPORT_LE): BluetoothGatt {

Log.e(TAG, "connect-------------------------------------address=${device.address}, name=${device.name}, type=${device.type}")
//DEVICE_TYPE_CLASSIC = 1       //DEVICE_TYPE_LE = 2        //DEVICE_TYPE_DUAL = 3

            return device.connectGatt(appContext, false, gattCallback, transport)
    }
    fun connect(device: BluetoothDevice): BluetoothGatt  {
        return device.connectGatt(appContext, true,
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    super.onConnectionStateChange(gatt, status, newState)
                    Log.e(TAG, "connect:onConnectionStateChange-------------------------------------status=$status, newState=$newState")
                    if(status == BluetoothProfile.STATE_CONNECTED)
                        Log.e(TAG, "connect:onConnectionStateChange-------------------------------------STATE_CONNECTED")
                }
            })
    }

    /*fun listenBluetoothProfileHeadset() {
        adapter?.getProfileProxy(appContext, object: BluetoothProfile.ServiceListener {
            override fun onServiceDisconnected(profile: Int) {
                Log.e(TAG, "onServiceDisconnected------------------------------------------------profile=$profile")
            }
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                //isHeadsetConnected = proxy!!.connectedDevices.size > 0
                Log.e(TAG, "onServiceConnected------------------------------------------------profile=$profile #connectedDevices=${proxy!!.connectedDevices.size}")
            }

        }, BluetoothProfile.HEADSET)
        //app.registerReceiver(view.broadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED))
    }*/


    var headset: BluetoothHeadset? = null
    var device: BluetoothDevice? = null
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.e(TAG, "profileListener:onServiceConnected-------------------------------------$profile")
            if(profile == BluetoothProfile.HEADSET) {
                headset = proxy as BluetoothHeadset
                device = getConnectedHeadset()
                Log.e(TAG, "profileListener:onServiceConnected-------------------------------------HEADSET: $device")
            }
        }
        override fun onServiceDisconnected(profile: Int) {
            Log.e(TAG, "profileListener:onServiceDisconnected-------------------------------------$profile")
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