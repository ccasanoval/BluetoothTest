package com.cesoft.cesble.presenter

import android.bluetooth.*
import android.util.Log
import com.cesoft.cesble.device.Bluetooth
import com.cesoft.cesble.device.ConnectedDevice
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*
import kotlin.experimental.and


//https://interrupt.memfault.com/blog/bluetooth-low-energy-a-primer
//https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07
object TestLE : KoinComponent {

    private val TAG = TestLE::class.java.simpleName
    private val UUID_LE_SERVICE = UUID.fromString("127FACE1-CB21-11E5-93D0-0002A5D5C51B")
    private val UUID_LE_BUTTONS = UUID.fromString("127FBEEF-CB21-11E5-93D0-0002A5D5C51B")
    private val UUID_LE_CONFIG  = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    class ButtonEvent(characteristic: BluetoothGattCharacteristic) {//val type: Type, val on: Boolean) {
        enum class Type(val code: Byte) {
            PTT(0x01),
            PTTE(0x2),
            PTTS(0x4),
            PTTB1(0x8),
            PTTB2(0x10),
            MFB(0x20),
        }
        val type: Byte = characteristic.value[0]
    }

    private val bluetooth : Bluetooth by inject()
    private var bluetoothGatt: BluetoothGatt? = null

    fun pairing(device: BluetoothDevice) {
        device.createBond()
    }

    fun start(device: BluetoothDevice) {

        //TODO: Check if bluetooth is connected...
        for(deviceClassic in bluetooth.pairedDevices!!) {
            if(deviceClassic.type == BluetoothDevice.DEVICE_TYPE_CLASSIC)
                bluetooth.connectClassic(deviceClassic)
        }
        //if(ConnectedDevice.device )

        if(device.type != BluetoothDevice.DEVICE_TYPE_LE && device.type != BluetoothDevice.DEVICE_TYPE_DUAL) {
            Log.e(TAG,"************ This device is not LE so connecting by GATT won't work ************")
        }
        try { bluetoothGatt?.disconnect() } catch(ignore: Exception){}
        try { bluetoothGatt?.close() } catch(ignore: Exception){}
        bluetoothGatt = bluetooth.connectGatt(device, gattCallback)
        //bluetoothGatt = device.connectGatt(appContext, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.e(TAG, "start--------------------------------------#services="+bluetoothGatt?.services?.size)
    }

    private fun sendButtonsStatus(characteristic: BluetoothGattCharacteristic) {
        EventBus.getDefault().post(ButtonEvent(characteristic))
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if(characteristic.uuid.toString() == UUID_LE_BUTTONS.toString()) {
                Log.e(TAG, "onCharacteristicChanged-------------------------------UUID_LE_BUTTONS ${characteristic.value} / "+characteristic.getStringValue(0))

                for(desc in characteristic.descriptors) {
                    Log.e(TAG, "onCharacteristicChanged-------------------------------des: ${desc.value} ")
                }

                printButtonStatus(characteristic)
                sendButtonsStatus(characteristic)
            }
            else
                Log.e(TAG, "gattCallback:onCharacteristicChanged--------------------***********------------------${characteristic.uuid}")
        }
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.e(TAG, "gattCallback:onConnectionStateChange------------------------------status=${Bluetooth.stateProfileToString(status)} newState=${Bluetooth.stateProfileToString(newState)}  device=${gatt.device}")
            ConnectedDevice.leDevice = gatt.device
            ConnectedDevice.leStatus = newState
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
                    //Log.e(TAG, "onServicesDiscovered----------------------------------GATT_SUCCESS")
                    for(service in gatt.services) {
                        Log.e(TAG, "onServicesDiscovered------------------0----------------service: ${service.uuid} / type: ${service.type} / instanceId: ${service.instanceId}")
                        if(service.uuid.toString() == UUID_LE_SERVICE.toString()) {
                            Log.e(TAG, "onServicesDiscovered-----------------1-----------------****")
                            for(characteristic in service.characteristics) {
                                if(characteristic.uuid.toString() == UUID_LE_BUTTONS.toString()) {
                                    Log.e(TAG, "onServicesDiscovered----------2------------------------characteristic="+characteristic.uuid.toString())
                                    enableNotificationButtonsState()
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

            when {
                characteristic.value[0] and 0x01 > 0 -> setRedLed()
                characteristic.value[0] and 0x02 > 0 -> setGreenLed()
                characteristic.value[0] and 0x04 > 0 -> setBlueLed()
                characteristic.value[0] and 0x08 > 0 -> setRedGreenLed()
                characteristic.value[0] and 0x10 > 0 -> setRedBlueLed()
                characteristic.value[0] and 0x20 > 0 -> setWhiteLed()
                else -> setOffLed()
            }
        }

        private fun enableNotificationButtonsState() {
            val characteristic = bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(UUID_LE_BUTTONS)
            bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
            val descriptor: BluetoothGattDescriptor = characteristic.getDescriptor(UUID_LE_CONFIG)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt!!.writeDescriptor(descriptor)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.e(TAG, "onCharacteristicRead----------------------------------characteristic=${characteristic.uuid}  status=$status  value=${characteristic.value}  size=${characteristic.value.size}")
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


    ////////////////////////////////////////////////////////////////////////////////////////////////////


    private val LEDS = UUID.fromString("127FDEAD-CB21-11E5-93D0-0002A5D5C51B")

    private fun setRedLed() {
        bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS).value = ByteArray(1) {0x01}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS))
    }
    private fun setGreenLed() {
        bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS).value = ByteArray(1) {0x02}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS))
    }
    private fun setBlueLed() {
        bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS).value = ByteArray(1) {0x04}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS))

    }
    private fun setRedGreenLed() {
        bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS).value = ByteArray(1) {0x03}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS))
    }
    private fun setRedBlueLed() {
        bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS).value = ByteArray(1) {0x05}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS))
    }
    private fun setGreenBlueLed() {
        bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS).value = ByteArray(1) {0x06}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS))
    }
    private fun setWhiteLed() {
        bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS).value = ByteArray(1) {0x07}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS))
    }
    private fun setOffLed() {
        bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS).value = ByteArray(1) {0x00}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(UUID_LE_SERVICE).getCharacteristic(LEDS))
    }
}