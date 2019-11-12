package com.cesoft.cesble.presenter

import android.bluetooth.*
import android.content.Context
import android.util.Log
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*


class TestLE : KoinComponent {

    companion object {
        private val TAG = TestLE::class.java.simpleName
    }

    private val context : Context by inject()
    //private val bluetooth : Bluetooth by inject()
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    fun start(device: BluetoothDevice) {
        /*bluetooth.pairedDevices?.let { pairedDevices ->
            if( ! pairedDevices.isEmpty()) {
                val device = pairedDevices.first()
                Log.e(TAG, "ainBleTest--------------------------------------${device.name} / ${device.address}")
                //device.fetchUuidsWithSdp()
                val bg: BluetoothGatt = device.connectGatt(
                    context,
                    true,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE)
                for(service in bg.services) {
                    Log.e(TAG, "ainBleTest:service:------------------------------------${service.uuid}")
                }
            }
        }*/
        val bg: BluetoothGatt = device.connectGatt(
            context,
            true,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE)
        Log.e(TAG, "onCharacteristicChanged--------------------***********------------------#services="+bg.services.size)

    }
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.e(TAG, "onCharacteristicChanged--------------------***********------------------$characteristic")
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.e(TAG, "onConnectionStateChange--------------------------------------status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.e(TAG, "Connected to GATT server.--------------------------------------STATE_CONNECTED")
                    Log.e(TAG, "Attempting to start service discovery: " + bluetoothGatt?.discoverServices())

                    bluetoothGatt?.setCharacteristicNotification(characteristic, true)
                    val uuid: UUID = UUID.fromString("127FACE1-CB21-11E5-93D0-0002A5D5C51B")
                    val descriptor = characteristic?.getDescriptor(uuid)?.apply {
                        value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    }
                    bluetoothGatt?.writeDescriptor(descriptor)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.e(TAG, "Disconnected from GATT server.--------------------------------STATE_DISCONNECTED "+bluetoothGatt?.discoverServices())
                }
            }
        }
        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> Log.e(TAG, "onServicesDiscovered----------------------------------GATT_SUCCESS")
                else -> Log.e(TAG, "onServicesDiscovered received: $status -----------------------------------------------")
            }
        }

        // Result of a characteristic read operation
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.e(TAG, "onCharacteristicRead----------------------------------GATT_SUCCESS")
                }
            }
        }
    }


}