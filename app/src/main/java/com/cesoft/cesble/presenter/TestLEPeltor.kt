package com.cesoft.cesble.presenter

import android.bluetooth.*
import android.util.Log
import com.cesoft.cesble.device.Bluetooth
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*
import kotlin.experimental.and


object TestLEPeltor : KoinComponent {

    // FE53AB12-02D0-334D-B424-0002A5D5C51B
    //- 0x01; PTT button (supported today)
    //- 0x02; SOS button
    //- 0x04; Secondary button
    //- 0x08; Previous channel button
    //- 0x10; Next channel button

    private val TAG = TestLEPeltor::class.java.simpleName
    private const val UUID_LE_SERVICE = "0000fe53-0000-1000-8000-00805f9b34fb"
    private const val UUID_LE_CHARACTERISTIC = "fe53ab12-02d0-334d-b424-0002a5d5c51b"
    private val UUID_LE_CHARACTERISTIC_NOTIFICATION = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    class ButtonEvent(characteristic: BluetoothGattCharacteristic) {//val type: Type, val on: Boolean) {
        enum class Type(val code: Byte) {
            PTT_BUTTON(0x01),
            SOS_BUTTON(0x2),
            SECOND_BUTTON(0x4),
            CHANNEL_PREV(0x8),
            CHANNEL_NEXT(0x10),
        }
        val type: Byte = characteristic.value[0]
    }

    private val bluetooth : Bluetooth by inject()
    private var bluetoothGatt: BluetoothGatt? = null

    fun start(device: BluetoothDevice) {

        //TODO: Check if bluetooth is connected...
        for(deviceClassic in bluetooth.pairedDevices!!) {
            if(deviceClassic.type == BluetoothDevice.DEVICE_TYPE_CLASSIC)
                bluetooth.connectClassic(deviceClassic)
        }

        if(device.type != BluetoothDevice.DEVICE_TYPE_LE && device.type != BluetoothDevice.DEVICE_TYPE_DUAL) {
            Log.e(TAG,"************ This device is not LE so connecting by GATT won't work ************")
        }
        try { bluetoothGatt?.disconnect() } catch(ignore: Exception){}
        bluetoothGatt?.close()
        bluetoothGatt = bluetooth.connectGatt(device, gattCallback)
        Log.e(TAG, "start--------------------------------------#services="+bluetoothGatt?.services?.size)
    }

    private fun sendButtonsStatus(characteristic: BluetoothGattCharacteristic) {
        EventBus.getDefault().post(ButtonEvent(characteristic))
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.e(TAG, "gattCallback:onCharacteristicChanged--------------------****000****------val=${characteristic.value}-----------${characteristic.uuid}")
            if(UUID_LE_CHARACTERISTIC == characteristic.uuid.toString()) {
                printButtonStatus(characteristic)
                sendButtonsStatus(characteristic)
            }
        }
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.e(TAG, "gattCallback:onConnectionStateChange------------------------------status=${Bluetooth.stateProfileToString(status)} newState=${Bluetooth.stateProfileToString(newState)}")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.e(TAG, "gattCallback: Connected to GATT server.--------------------------------------STATE_CONNECTED")
                    bluetoothGatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.e(TAG, "Disconnected from GATT server.--------------------------------STATE_DISCONNECTED "+bluetoothGatt?.discoverServices())
                }
            }
        }
        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.e(TAG, "onServicesDiscovered----------------------------------GATT_SUCCESS")
                    for(service in gatt.services) {
                        //Log.e(TAG, "onServicesDiscovered------------------0----------------service: ${service.uuid} / type: ${service.type} / instanceId: ${service.instanceId}")
//                        if(true) {
//                            Log.e(TAG, "onServicesDiscovered-----------------1-----------------****")
//                            for(characteristic in service.characteristics) {
//                                Log.e(TAG, "onServicesDiscovered----------2------------------------characteristic="+characteristic.uuid.toString())
//                            }
//                        }
                        if(UUID_LE_SERVICE == service.uuid.toString()) {
                            Log.e(TAG, "onServicesDiscovered------------------0----------------service: ${service.uuid} / type: ${service.type} / instanceId: ${service.instanceId}")
                            for(characteristic in service.characteristics) {
                                if(UUID_LE_CHARACTERISTIC == characteristic.uuid.toString()) {
                                    //characteristic.setValue()
                                    bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
                                    val descriptor = characteristic.getDescriptor(UUID_LE_CHARACTERISTIC_NOTIFICATION)
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    //descriptor.value = byteArrayOf(0x00, 0x00) //TO DISABLE
                                    val ok = bluetoothGatt!!.writeDescriptor(descriptor)
                                    Log.e(TAG, "onServicesDiscovered----------------ok=$ok---------characteristic="+characteristic.uuid.toString())
                                }
                            }
                        }
                    }
                }
                else -> {
                    Log.e(TAG, "onServicesDiscovered received: $status -----------------------------------------------")
                }
            }
        }

        private fun printButtonStatus(characteristic: BluetoothGattCharacteristic) {
            val ptt = when(characteristic.value[0] and 0x01 > 0) { true -> " 1 "; else -> " 0 "}
            val ptte = when(characteristic.value[0] and 0x02 > 0) { true -> " 1 "; else -> " 0 "}
            val ptts = when(characteristic.value[0] and 0x04 > 0) { true -> " 1 "; else -> " 0 "}
            val pttb1 = when(characteristic.value[0] and 0x08 > 0) { true -> " 1 "; else -> " 0 "}
            val pttb2 = when(characteristic.value[0] and 0x10 > 0) { true -> " 1 "; else -> " 0 "}
            val mfb = when(characteristic.value[0] and 0x20 > 0) { true -> " 1 "; else -> " 0 "}
            Log.e(TAG, "printButtonStatus----------------------------------DATA: PTT=$ptt, PTTE=$ptte, PTTS=$ptts, PTTB1=$pttb1 PTTB2=$pttb2 MFB=$mfb")
        }

        /*private fun enableNotificationButtonsState() {
            val characteristic = bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(UUID_LE_BUTTONS)
            bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
//            val descriptor: BluetoothGattDescriptor = characteristic.getDescriptor(UUID_LE_CONFIG)
//            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
//            bluetoothGatt!!.writeDescriptor(descriptor)
        }*/

        // Result of a characteristic read operation
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.e(TAG, "onCharacteristicRead----------------------------------characteristic=${characteristic.uuid}  status=$status  value=${characteristic.value}  size=${characteristic.value.size}")
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.e(TAG, "onCharacteristicRead----------------------------------GATT_SUCCESS ")
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
            Log.e(TAG, "onDescriptorRead----------------------------------"+descriptor.uuid+" : "+status)
        }
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.e(TAG, "onDescriptorWrite----------------------------------"+descriptor.uuid+" : "+status)
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

/*
2020-05-21 09:49:17.711 11513-11513/? E/MainPresenter: pairedContextMenuListener----tag=0-----------------------device=00:15:F3:2E:01:AA
2020-05-21 09:49:20.039 11513-11513/? E/Bluetooth: connectGatt------------1-------------------------address=00:15:F3:2E:01:AA, name=WS PROTAC XPI, type=DUAL
2020-05-21 09:49:20.047 11513-11513/? E/TestLEPeltor: start--------------------------------------#services=0
2020-05-21 09:49:54.147 11513-11513/? E/MainPresenter: onBroadcastReceiver----------------------------------------------------------------
2020-05-21 09:49:54.148 11513-11513/? E/MainPresenter: onEvent:ClassicEvent----------------------------------------------------------------event:com.cesoft.cesble.device.ConnectedDevice$ClassicEvent@31a62dd
2020-05-21 09:49:54.150 11513-11513/? E/MainPresenter: ACTION_CONNECTION_STATE_CHANGED----------------------------------------------------- DISCONNECTED
2020-05-21 09:50:02.420 11513-11513/? E/MainPresenter: onBroadcastReceiver----------------------------------------------------------------
2020-05-21 09:50:02.420 11513-11513/? E/MainPresenter: onEvent:ClassicEvent----------------------------------------------------------------event:com.cesoft.cesble.device.ConnectedDevice$ClassicEvent@499e52
2020-05-21 09:50:02.425 11513-11513/? E/MainPresenter: ACTION_CONNECTION_STATE_CHANGED----------------------------------------------------- CONNECTED
2020-05-21 09:50:04.666 11513-11526/? E/TestLEPeltor: gattCallback:onConnectionStateChange------------------------------status=DISCONNECTED newState=CONNECTED
2020-05-21 09:50:04.666 11513-11526/? E/TestLEPeltor: gattCallback: Connected to GATT server.--------------------------------------STATE_CONNECTED

2020-05-21 09:50:04.678 11513-11525/? E/TestLEPeltor: onServicesDiscovered------------------0----------------service: 00001801-0000-1000-8000-00805f9b34fb / type: 0 / instanceId: 1
2020-05-21 09:50:04.678 11513-11525/? E/TestLEPeltor: onServicesDiscovered----------2------------------------characteristic=00002a05-0000-1000-8000-00805f9b34fb

2020-05-21 09:50:04.682 11513-11525/? E/TestLEPeltor: onServicesDiscovered------------------0----------------service: 00001800-0000-1000-8000-00805f9b34fb / type: 0 / instanceId: 5
2020-05-21 09:50:04.682 11513-11525/? E/TestLEPeltor: onServicesDiscovered----------2------------------------characteristic=00002a00-0000-1000-8000-00805f9b34fb
2020-05-21 09:50:04.685 11513-11525/? E/TestLEPeltor: onServicesDiscovered----------2------------------------characteristic=00002a01-0000-1000-8000-00805f9b34fb

2020-05-21 09:50:04.689 11513-11525/? E/TestLEPeltor: onServicesDiscovered------------------0----------------service: 0000fe53-0000-1000-8000-00805f9b34fb / type: 0 / instanceId: 10
2020-05-21 09:50:04.689 11513-11525/? E/TestLEPeltor: onServicesDiscovered----------2------------------------characteristic=fe53aa11-02d0-334d-b424-0002a5d5c51b
2020-05-21 09:50:04.692 11513-11525/? E/TestLEPeltor: onServicesDiscovered----------2------------------------characteristic=fe53aa12-02d0-334d-b424-0002a5d5c51b
2020-05-21 09:50:04.694 11513-11525/? E/TestLEPeltor: onServicesDiscovered----------2------------------------characteristic=fe53ab11-02d0-334d-b424-0002a5d5c51b
2020-05-21 09:50:04.699 11513-11525/? E/TestLEPeltor: onServicesDiscovered----------2------------------------characteristic=fe53ab12-02d0-334d-b424-0002a5d5c51b
*/