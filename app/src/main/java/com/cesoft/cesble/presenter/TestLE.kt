package com.cesoft.cesble.presenter

import android.bluetooth.*
import android.util.Log
import com.cesoft.cesble.device.Bluetooth
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

//https://interrupt.memfault.com/blog/bluetooth-low-energy-a-primer
//https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07
object TestLE : KoinComponent {

    private val TAG = TestLE::class.java.simpleName

    private val bluetooth : Bluetooth by inject()
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    fun start(device: BluetoothDevice) {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = bluetooth.connect(device, gattCallback)
        Log.e(TAG, "start--------------------------------------#services="+bluetoothGatt?.services?.size)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.e(TAG, "onCharacteristicChanged--------------------***********------------------$characteristic")
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.e(TAG, "onConnectionStateChange--------------------------------------status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.e(TAG, "Connected to GATT server.--------------------------------------STATE_CONNECTED")
                    Log.e(TAG, "Attempting to start service discovery: " + bluetoothGatt?.discoverServices())


                    gatt.discoverServices()


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
            Log.e(TAG, "onServicesDiscovered----------------------------------")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> Log.e(TAG, "onServicesDiscovered----------------------------------GATT_SUCCESS")
                else -> Log.e(TAG, "onServicesDiscovered received: $status -----------------------------------------------")
            }
        }

        // Result of a characteristic read operation
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.e(TAG, "onCharacteristicRead----------------------------------")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.e(TAG, "onCharacteristicRead----------------------------------GATT_SUCCESS")
                }
            }
        }

        override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            Log.e(TAG, "onPhyUpdate----------------------------------")
        }
        override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            Log.e(TAG, "onPhyUpdate----------------------------------")
        }
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.e(TAG, "onCharacteristicWrite----------------------------------")
        }
        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.e(TAG, "onDescriptorRead----------------------------------")
        }
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.e(TAG, "onDescriptorWrite----------------------------------")
        }
        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
            Log.e(TAG, "onReliableWriteCompleted----------------------------------")
        }
        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            Log.e(TAG, "onReadRemoteRssi----------------------------------")
        }
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.e(TAG, "onMtuChanged----------------------------------")
        }
    }


}