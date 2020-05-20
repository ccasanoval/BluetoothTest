package com.cesoft.cesble.device

import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*


class Bluetooth : KoinComponent {

    companion object {
        private val TAG = Bluetooth::class.java.simpleName

        fun stateAdapterToString(state: Int) = when(state) {
            BluetoothAdapter.STATE_TURNING_OFF -> "TURNING OFF..."
            BluetoothAdapter.STATE_OFF -> "STATE OFF"
            BluetoothAdapter.STATE_TURNING_ON -> "TURNING ON..."
            BluetoothAdapter.STATE_ON -> "STATE ON"
            BluetoothAdapter.STATE_DISCONNECTING -> "DISCONNECTING..."
            BluetoothAdapter.STATE_DISCONNECTED -> "DISCONNECTED"
            BluetoothAdapter.STATE_CONNECTING -> "CONNECTING..."
            BluetoothAdapter.STATE_CONNECTED -> "CONNECTED"
            else -> "?"
        }

        fun stateProfileToString(state: Int) = when(state) {
            BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
            BluetoothProfile.STATE_CONNECTING -> "CONNECTING..."
            BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
            BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING..."
            else -> "?"
        }

        fun typeToString(type: Int) = when(type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
            BluetoothDevice.DEVICE_TYPE_LE -> "LE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
            else -> "?"
        }

        fun connectionTypeToString(type: Int) = when(type) {
            BluetoothSocket.TYPE_RFCOMM -> "RFCOMM"
            BluetoothSocket.TYPE_SCO -> "SCO"
            BluetoothSocket.TYPE_L2CAP -> "L2CAP"
            else -> "?"
        }

        fun profileToString(profile: Int) = when(profile) {
            BluetoothProfile.A2DP -> "A2DP"
            BluetoothProfile.HEADSET -> "HEADSET"
            else -> "?"
        }
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
        Log.e(TAG, "switchOnOf------------------------------------------------2")
        if(isEnabled)
            adapter?.disable()
        else
            adapter?.enable()
    }
    fun reset() {
        Log.e(TAG, "reset------------------------------------------------2")
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


    //----------------------------------------------------------------------------------------------
    // CONNECTION
    //----------------------------------------------------------------------------------------------

    private fun isConnected() : Boolean {
        Log.e(TAG, "isConnected------------------------------------------------")
        socket?.let { socket ->
            if(socket.isConnected) {
                Log.e(TAG, "connect-----------------------1--------------Already connected! ${connectionTypeToString(socket.connectionType)}")
                adapter?.let { adapter ->
                    //if(it.isEnabled) {
                    val profiles = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
                    for(profileId in profiles) {
                        if(adapter.getProfileConnectionState(profileId) == BluetoothProfile.STATE_CONNECTED) {
                            Log.e(TAG, "connect----------------2---------------------Already connected!  profileId=${profileToString(profileId)}")
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private var socket: BluetoothSocket? = null
    fun connectClassic(device: BluetoothDevice) {
        Log.e(TAG, "connectClassic------------------------------------------------")
        if(isConnected()) return
        GlobalScope.launch(IO) {
            //device.fetchUuidsWithSdp()
            Log.e(TAG, "connect-------------------------------------device=$device / uuids=${device.uuids != null} / ${device.type}")
            if(device.uuids == null) {
                device.fetchUuidsWithSdp()
            }
            else
            for(uuid in device.uuids) {
//                val uuid0 = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")//AINA PTT Voice Responder Classic
                val uuid1 = UUID.fromString("00001108-0000-1000-8000-00805f9b34fb")
//                val uuid2 = UUID.fromString("0000111e-0000-1000-8000-00805f9b34fb")
//                val uuid3 = UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb")
//                val uuid4 = UUID.fromString("0000110e-0000-1000-8000-00805f9b34fb")
//                val uuid5 = UUID.fromString("00000000-0000-1000-8000-00805f9b34fb")

                if(uuid.uuid != uuid1) {
                    Log.e(TAG, "connect-------------------------------------service not wanted: uuid=$uuid")
                    continue
                }
                try {
                    Log.e(TAG, "connect-------------------------------------connecting to uuid=${uuid.uuid}")
                    //if(socket == null) {
                        val socket = device.createRfcommSocketToServiceRecord(uuid.uuid)
                        //socket = device.createInsecureRfcommSocketToServiceRecord(uuid.uuid)
                        //socket = device.createL2capChannel(uuid.uuid)//Android Q
                        //delay(250)//!!!
                        //Thread.sleep(500)
                        delay(500)//!!!
                        socket?.connect()
                        delay(500)//!!!
                        //socket?.outputStream?.write(0x0a)
                        val available = socket?.inputStream?.available() ?: 0
                    Log.e(TAG, "connect:------------------------------available=$available")
                        if(available > 0) {
                            val bytes = socket?.inputStream?.readBytes()
                            Log.e(TAG, "connect:------------------------------available=$available / val="+bytes?.toString())
                        }
                        delay(500)//!!!
                        Log.e(TAG, "connect:------------**************************------------------------socket=${socket.isConnected}")

                    if(socket.isConnected) {
                        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        audioManager.startBluetoothSco()
                        Log.e(TAG, "connect:------------------------------isBluetoothScoAvailableOffCall=${audioManager.isBluetoothScoAvailableOffCall}")
                    }

                    ConnectedDevice.classicDevice = device

                    //}
                } catch (e: Exception) {
                    socket = null
                    Log.e(TAG, "connect:e:-------------------------------------------------------------------$e")
                }
                //break
            }
            Log.e(TAG, "connect:-------------------------------------999")
        }
    }

    fun connectGatt(device: BluetoothDevice,
                    gattCallback: BluetoothGattCallback,
                    transport: Int=BluetoothDevice.TRANSPORT_LE): BluetoothGatt {
Log.e(TAG, "connectGatt------------1-------------------------address=${device.address}, name=${device.name}, type=${typeToString(device.type)}")
        return device.connectGatt(appContext, true, gattCallback, transport)
    }

    fun connectGatt(device: BluetoothDevice): BluetoothGatt  {
Log.e(TAG, "connectGatt-------------2------------------------address=${device.address}, name=${device.name}, type=${typeToString(device.type)}")
        return device.connectGatt(appContext, true,
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    super.onConnectionStateChange(gatt, status, newState)
                    Log.e(TAG, "connect:onConnectionStateChange-------------------------------------status=${stateProfileToString(status)}, newState=${stateProfileToString(newState)}")
                    ConnectedDevice.leDevice = gatt.device
                    ConnectedDevice.leStatus = newState
                    if(newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.e(TAG, "connect:onConnectionStateChange-------------------------------------STATE_CONNECTED : "+gatt.device.name)

                        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        audioManager.startBluetoothSco()

                        gatt.discoverServices()

                        for(service in gatt.services) {
                            Log.e(TAG, "connect:onConnectionStateChange-------------------------------------Service="+service.uuid+"  type="+service.type)
                        }
                        val service = gatt.getService(UUID.fromString("127FACE1-CB21-11E5-93D0-0002A5D5C51B"))
                        Log.e(TAG, "connect:onConnectionStateChange----------------***---------------------Service=$service")
                    }
                }
            }, BluetoothDevice.TRANSPORT_LE)
    }
}