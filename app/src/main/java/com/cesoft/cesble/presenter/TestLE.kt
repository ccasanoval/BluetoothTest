package com.cesoft.cesble.presenter

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.util.Log
import com.cesoft.cesble.device.Bluetooth
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*
import kotlin.experimental.and


//https://interrupt.memfault.com/blog/bluetooth-low-energy-a-primer
//https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07
object TestLE : KoinComponent {

    private val TAG = TestLE::class.java.simpleName
    private val uuidService = UUID.fromString("127FACE1-CB21-11E5-93D0-0002A5D5C51B")
    private val uuidButton = UUID.fromString("127fbeef-cb21-11e5-93d0-0002a5d5c51b")//"127BEEF1-CB21-11E5-93D0-0002A5D5C51B")

    private val bluetooth : Bluetooth by inject()
    private var bluetoothGatt: BluetoothGatt? = null
    //private var characteristic: BluetoothGattCharacteristic? = null

    fun start(device: BluetoothDevice) {

        bluetooth.connectClassic(device)



        if(device.type != BluetoothDevice.DEVICE_TYPE_LE && device.type != BluetoothDevice.DEVICE_TYPE_DUAL) {
            Log.e(TAG,"************ This device is not LE so connecting by GATT won't work ************")
        }
        try { bluetoothGatt?.disconnect() } catch(ignore: Exception){}
        bluetoothGatt?.close()
        bluetoothGatt = bluetooth.connectGatt(device, gattCallback)
        Log.e(TAG, "start--------------------------------------#services="+bluetoothGatt?.services?.size)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.e(TAG, "gattCallback:onCharacteristicChanged--------------------***********------------------${characteristic.uuid}")
            if(characteristic.uuid.toString() == BUTTONS.toString()) {
                val PTT = characteristic.value[0] and 0x01 > 0
                val PTTE = characteristic.value[0] and 0x02 > 0
                val PTTS = characteristic.value[0] and 0x04 > 0
                val PTTB1 = characteristic.value[0] and 0x08 > 0
                val PTTB2 = characteristic.value[0] and 0x10 > 0
                val MFB = characteristic.value[0] and 0x20 > 0

                Log.e(TAG, "onCharacteristicChanged----------------------------------DATA: PTT=$PTT, PTTE=$PTTE, PTTS=$PTTS, PTTB1=$PTTB1 PTTB2=$PTTB2 MFB=$MFB")
            }
        }
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.e(TAG, "gattCallback:onConnectionStateChange------------------------------status=${Bluetooth.stateProfileToString(status)} newState=${Bluetooth.stateProfileToString(newState)}")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.e(TAG, "gattCallback: Connected to GATT server.--------------------------------------STATE_CONNECTED")
                    Log.e(TAG, "gattCallback: Attempting to start service discovery: " + bluetoothGatt?.discoverServices())
                    //bluetoothGatt?.discoverServices()
                    //bluetoothGatt?.readCharacteristic(BluetoothGattCharacteristic(UUID.fromString("127BEEF1-CB21-11E5-93D0-0002A5D5C51B")))
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
                        if(service.uuid.toString().toUpperCase(Locale.US) == uuidService.toString().toUpperCase(Locale.US)) {
                            Log.e(TAG, "onServicesDiscovered-----------------1-----------------****")
                            for(characteristic in service.characteristics) {
                                //if(characteristic.uuid.toString().toUpperCase(Locale.US) == uuidButton.toString().toUpperCase(Locale.US)) {
                                    Log.e(TAG, "onServicesDiscovered----------2------------------------characteristic="+characteristic.uuid.toString())
                                    //bluetoothGatt?.readCharacteristic(characteristic)//-----> onCharacteristicRead()

                                GlobalScope.launch(IO) {
                                    for(i in 0..10) {
                                        bluetoothGatt?.readCharacteristic(characteristic)//-----> For some reason, the first time gets 137 error
                                        delay(200)
                                    }
                                }

                                //}
                            }
                        }
                    }
                }
                else -> {
                    Log.e(TAG, "onServicesDiscovered received: $status -----------------------------------------------")
                }
            }
        }

        // Result of a characteristic read operation
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.e(TAG, "onCharacteristicRead----------------------------------characteristic=${characteristic.uuid}  status=$status  value=${characteristic.value}  size=${characteristic.value.size}")
            when(status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.e(TAG, "onCharacteristicRead----------------------------------GATT_SUCCESS ")

                    val PTT = characteristic.value[0] and 0x01 > 0
                    val PTTE = characteristic.value[0] and 0x02 > 0
                    val PTTS = characteristic.value[0] and 0x04 > 0
                    val PTTB1 = characteristic.value[0] and 0x08 > 0
                    val PTTB2 = characteristic.value[0] and 0x10 > 0
                    val MFB = characteristic.value[0] and 0x20 > 0

                    Log.e(TAG, "onCharacteristicRead----------------------------------DATA: PTT=$PTT, PTTE=$PTTE, PTTS=$PTTS, PTTB1=$PTTB1 PTTB2=$PTTB2 MFB=$MFB")

                    bluetoothGatt!!.setCharacteristicNotification(bluetoothGatt!!.getService(AINA_SERV).getCharacteristic(BUTTONS), true)
                    val descriptor: BluetoothGattDescriptor = bluetoothGatt!!.getService(AINA_SERV).getCharacteristic(BUTTONS).getDescriptor(CLIENT_CHAR_CONFIG)
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGatt!!.writeDescriptor(descriptor)


                    /*for(desc in characteristic.descriptors) {
                        Log.e(TAG, "onCharacteristicRead----------------------------------Descriptor:${desc.uuid} : val:${desc.value}")
                    }*/
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





////////////////////////////////////////////////////////////////////////////////////////////////////




    private val CLIENT_CHAR_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    private val AINA_SERV = UUID.fromString("127FACE1-CB21-11E5-93D0-0002A5D5C51B")
    private val BATT_SERV = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    private val SW_VERS = UUID.fromString("127FC0FF-CB21-11E5-93D0-0002A5D5C51B")
    private val BUTTONS = UUID.fromString("127FBEEF-CB21-11E5-93D0-0002A5D5C51B")
    private val LEDS = UUID.fromString("127FDEAD-CB21-11E5-93D0-0002A5D5C51B".toLowerCase(Locale.US))
    private val BATT_LEVEL = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    private val Aina_Chars: List<BluetoothGattCharacteristic>? = null

    private val BLEScanner: BluetoothLeScanner? = null
    private val BLEDevice: BluetoothDevice? = null
    //private val BLEGatt: BluetoothGatt? = null
    private val BLEManager: BluetoothManager? = null
    private val BLEAdapter: BluetoothAdapter? = null

    private fun setRedLed() {
        bluetoothGatt!!.getService(AINA_SERV).getCharacteristic(LEDS).value = ByteArray(1) {0x01}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(AINA_SERV).getCharacteristic(LEDS))
    }
    private fun setGreenLed() {
        bluetoothGatt!!.getService(AINA_SERV).getCharacteristic(LEDS).value = ByteArray(1) {0x02}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(AINA_SERV).getCharacteristic(LEDS))
    }
    private fun setAmberLed() {
        bluetoothGatt!!.getService(AINA_SERV).getCharacteristic(LEDS).value = ByteArray(1) {0x04}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(AINA_SERV).getCharacteristic(LEDS))
    }
    private fun setOffLed() {
        bluetoothGatt!!.getService(AINA_SERV).getCharacteristic(LEDS).value = ByteArray(1) {0x00}
        bluetoothGatt!!.writeCharacteristic(bluetoothGatt!!.getService(AINA_SERV).getCharacteristic(LEDS))
    }
}