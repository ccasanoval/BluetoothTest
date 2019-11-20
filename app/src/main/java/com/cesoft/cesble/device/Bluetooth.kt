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
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.ServiceListener


class Bluetooth : KoinComponent {

    companion object {
        private val TAG = Bluetooth::class.java.simpleName

        fun stateToString(state: Int) = when(state) {
            BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
            BluetoothAdapter.STATE_OFF -> "STATE_OFF"
            BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
            BluetoothAdapter.STATE_ON -> "STATE_ON"
            BluetoothAdapter.STATE_DISCONNECTING -> "DISCONNECTING"
            BluetoothAdapter.STATE_DISCONNECTED -> "DISCONNECTED"
            BluetoothAdapter.STATE_CONNECTING -> "CONNECTING"
            BluetoothAdapter.STATE_CONNECTED -> "CONNECTED"
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


    //----------------------------------------------------------------------------------------------
    // CONNECTION
    //----------------------------------------------------------------------------------------------

    private var socket: BluetoothSocket? = null
    fun connect(device: BluetoothDevice) {
        socket?.let {
            if(it.isConnected) {
                Log.e(TAG, "connect-----------------------1--------------Already connected!")
                return
            }
        }
        adapter?.let {
            //if(it.isEnabled) {
            val profiles = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
            for(profileId in profiles) {
                if(it.getProfileConnectionState(profileId) == BluetoothProfile.STATE_CONNECTED) {
                    Log.e(TAG, "connect----------------2---------------------Already connected!  profileId=$profileId")//HEADSET = 1, A2DP = 2
                    return
                }
            }
        }

        GlobalScope.launch {
            //device.fetchUuidsWithSdp()
            for(uuid in device.uuids) {
                try {
                    Log.e(TAG, "connect-------------------------------------connecting to uuid=$uuid")
                    if(socket == null) {
                        //socket = device.createRfcommSocketToServiceRecord(uuid.uuid)
                        socket = device.createInsecureRfcommSocketToServiceRecord(uuid.uuid)
                        delay(250)//!!!
                        socket?.connect()
                    }
                    break
                } catch (e: Exception) {
                    socket = null
                    Log.e(TAG, "connect:e:-------------------------------------$e")
                }
                Log.e(TAG, "connect:-------------------------------------NO LUCK")
            }
            Log.e(TAG, "connect:-------------------------------------999")
        }
    }

    fun connectGatt(device: BluetoothDevice,
                gattCallback: BluetoothGattCallback,
                transport: Int=BluetoothDevice.TRANSPORT_LE): BluetoothGatt {

Log.e(TAG, "connect-------------------------------------address=${device.address}, name=${device.name}, type=${device.type}")
//DEVICE_TYPE_CLASSIC = 1       //DEVICE_TYPE_LE = 2        //DEVICE_TYPE_DUAL = 3

            return device.connectGatt(appContext, false, gattCallback, transport)
    }
    fun connectGatt(device: BluetoothDevice): BluetoothGatt  {
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

    var headset: BluetoothHeadset? = null
    var device: BluetoothDevice? = null
    private val profileListener = object : ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.e(TAG, "profileListener:onServiceConnected-------------------------------------$profile")
            if(profile == BluetoothProfile.HEADSET) {
                headset = proxy as BluetoothHeadset
                device = getConnectedHeadset()
                Log.e(TAG, "profileListener:onServiceConnected-------------------------------------HEADSET: $headset")
                Log.e(TAG, "profileListener:onServiceConnected-------------------------------------HEADSET DEV: $device  audioOn="+headset?.isAudioConnected(device))

                headset?.startVoiceRecognition(device)
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
        val b = adapter?.getProfileProxy(appContext, profileListener, BluetoothProfile.HEADSET)
        Log.e(TAG, "getProfileProxy-------------------------------------$b")
    }



/*
    private var audioManager: AudioManager? = null
    private var player = MediaPlayer()
    private var isSoundPlaying = false
    private var isSoundPrepared = false

    fun stopSound() {
        player.stop()
    }
    fun playSound() {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP)
        Log.e(TAG, "playSound-------------------------------------------------------isSoundPrepared=$isSoundPrepared isSoundPlaying=$isSoundPlaying ")
        if(isSoundPlaying) {
            Log.e(TAG, "playSound-------------------------------------------------------STOP************")
            player.stop()
            playSound()
        }
        else if(isSoundPrepared) {
            Log.e(TAG, "playSound-------------------------------------------------------PLAY************")
            player.start()
        }
        else {
            Log.e(TAG, "playSound-------------------------------------------------------PREPARE************")
            isSoundPrepared = true
            audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isBluetoothScoOn = true
            audioManager?.startBluetoothSco()


            val assetManager = appContext.assets
            val fd: AssetFileDescriptor
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                )

                player.setOnPreparedListener {
                    Log.e(TAG, "player.setOnPreparedListener-----------------PREPARE--------------------------------------0")
                    it.start()
                    isSoundPlaying = true
                }
                player.setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "player.setOnErrorListener---------------------ERROR--------------------------------- $what, $extra")
                    isSoundPlaying = false
                    false
                }
                player.setOnCompletionListener {
                    Log.e(TAG,"player.setOnCompletionListener-----------------COMPLETED--------------------------------------9")
                    isSoundPlaying = false
                }

                fd = assetManager.openFd("Cochise.mp3")
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                //player.setDataSource(fd.fileDescriptor)
                player.prepareAsync()
            }
            catch(e: Exception) {
                Log.e(TAG, "playSound:e:-------------------------------------------------------", e)
            }
        }
    }*/

}