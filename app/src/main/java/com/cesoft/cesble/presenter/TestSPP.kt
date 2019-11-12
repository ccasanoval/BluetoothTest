package com.cesoft.cesble.presenter

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.cesoft.cesble.device.SPPBluetooth
import com.cesoft.cesble.device.SPPBluetoothState

class TestSPP(context: Context) {
    companion object {
        private val TAG = TestSPP::class.java.simpleName
    }

    private var bluetoothSPP: SPPBluetooth = SPPBluetooth(context)

    fun stop() {
        bluetoothSPP.stopService()
    }

    fun start(device: BluetoothDevice) {
        if( ! bluetoothSPP.isBluetoothAvailable) {
            Log.e(TAG, "SPP: isBluetoothAvailable == false ---------------------------------------")
            return
        }

        if( ! bluetoothSPP.isBluetoothEnabled) {
            bluetoothSPP.enable()
        }

        bluetoothSPP.setBluetoothConnectionListener(object : SPPBluetooth.BluetoothConnectionListener {
            override fun onDeviceConnected(name: String, address: String) {
                Log.e(TAG, "SPP: onDeviceConnected --------------------------------------------***")
            }
            override fun onDeviceDisconnected() {
                Log.e(TAG, "SPP: onDeviceDisconnected -----------------------------------------***")
            }

            override fun onDeviceConnectionFailed() {
                Log.e(TAG, "SPP: onDeviceConnectionFailed -------------------------------------***")
            }
        })

        bluetoothSPP.setBluetoothStateListener { state ->
            when (state) {
                SPPBluetoothState.STATE_CONNECTED         // Do something when successfully connected
                -> {
                    Log.e(TAG, "SPP: onServiceStateChanged ---------------------------------------STATE_CONNECTED")
                    Log.e(TAG, "SPP: onServiceStateChanged ---------------------------------------STATE_CONNECTED: "
                                +bluetoothSPP.isServiceAvailable+" : "
                                +bluetoothSPP.connectedDeviceAddress+" : "
                                +bluetoothSPP.serviceState+" : "//STATE_CONNECTED==3
                    )

                    //bluetoothSPP.send("", false)

                    /*Log.e(TAG, "SPP: ---------------------------------------pairedDeviceAddress.size="+bluetoothSPP.pairedDeviceAddress.size)
                    if(bluetoothSPP.pairedDeviceAddress.isNotEmpty()) {
                        for(device in bluetoothSPP.pairedDeviceAddress) {
                            Log.e(TAG, "SPP: ---------------------------------------device=$device")
                            bluetoothSPP.connect(device.toString())
                            break
                        }
                        //bt.connect(bt.pairedDeviceAddress[0]) --> Exception
                    }*/
                }
                SPPBluetoothState.STATE_CONNECTING -> {  // Do something while connecting
                    Log.e(TAG,"SPP: onServiceStateChanged ---------------------------------------STATE_CONNECTING")
                }
                SPPBluetoothState.STATE_LISTEN     -> { // Do something when device is waiting for connection
                    Log.e(TAG,"SPP: onServiceStateChanged ---------------------------------------STATE_LISTEN")
                }
                SPPBluetoothState.STATE_NONE       -> {// Do something when device don't have any connection
                    Log.e(TAG, "SPP: onServiceStateChanged ---------------------------------------STATE_NONE")
                }
            }
        }

        bluetoothSPP.setOnDataReceivedListener { data, message ->
            Log.e(TAG, "SPP:DataReceived: -------------********************************-------------data=$data  message=$message")
        }

        bluetoothSPP.setupService()
        bluetoothSPP.startService()


        Log.e(TAG, "SPP: CONNECTING---------------------------------------device=$device")
        bluetoothSPP.connect(device.toString())

        /*Log.e(TAG, "SPP: ---------------------------------------pairedDeviceAddress.size="+bluetoothSPP.pairedDeviceAddress.size)
        if(bluetoothSPP.pairedDeviceAddress.isNotEmpty()) {
            for(device in bluetoothSPP.pairedDeviceAddress) {
                Log.e(TAG, "SPP: CONNECTING---------------------------------------device=$device")
                bluetoothSPP.connect(device.toString())
                break
            }
            //bt.connect(bt.pairedDeviceAddress[0]) --> Exception
        }*/
    }

    /*fun onActivityResult(requestCode: Int, data: Intent?) {
        if (requestCode == SPPBluetoothState.REQUEST_CONNECT_DEVICE) {
            Log.e(TAG, "SPP:  onActivityResult ---------------------------------------REQUEST_CONNECT_DEVICE "+data.toString())
            bluetoothSPP.connect(data)
        }
        else if (requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            Log.e(TAG, "SPP:  onActivityResult ---------------------------------------REQUEST_ENABLE_BT"+data.toString())
            bluetoothSPP.setupService()
            bluetoothSPP.startService(false)//BluetoothState.DEVICE_OTHER)
            setupSpp()
        }
    }*/
}