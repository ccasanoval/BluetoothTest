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
import android.media.AudioManager
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.ServiceListener
import android.media.MediaPlayer
import android.content.res.AssetFileDescriptor
import android.media.ToneGenerator
import android.media.AudioAttributes



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


    //----------------------------------------------------------------------------------------------
    // CONNECTION
    //----------------------------------------------------------------------------------------------

    private var socket: BluetoothSocket? = null
    fun connect(device: BluetoothDevice) {
        socket?.let {
            if(it.isConnected) {
                Log.e(TAG, "connect-------------------------------------Already connected!")
                return
            }
        }
        GlobalScope.launch {
            //device.fetchUuidsWithSdp()
            for(uuid in device.uuids) {
                try {
                    Log.e(TAG, "connect-------------------------------------connecting to uuid=$uuid")
                    //val socket = device.createRfcommSocketToServiceRecord(uuid.uuid)
                    if(socket == null) {
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
    }

    /*fun playSound() {
        //Sounds both on headset and phone
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP)
        prepareSound()

        if(isPlaying) {
            Log.e(TAG, "playSound-------------------------------------------------------STOP************")
            player.stop()
            isPlaying = false
        }
        else {
            Log.e(TAG, "playSound-------------------------------------------------------START************")

            mAudioManager?.mode = AudioManager.MODE_NORMAL
            mAudioManager?.isBluetoothScoOn = true
            mAudioManager?.startBluetoothSco()

            //val music = MediaPlayer.create(appContext, com.cesoft.cesble.R.raw.Cochise)
            val assetManager = appContext.assets
            val fd: AssetFileDescriptor
            try {
                player.stop()

                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                )

                player.setOnPreparedListener {
                    Log.e(TAG, "player.setOnPreparedListener-------------------------------------------------------0")
                    it.start()
                }
                player.setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "player.setOnErrorListener------------------------------------------------------- $what, $extra")
                    false
                }
                player.setOnCompletionListener {
                    Log.e(TAG, "player.setOnCompletionListener-------------------------------------------------------9")
                }

                fd = assetManager.openFd("Cochise.mp3")
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                //player.setDataSource(fd.fileDescriptor)
                player.prepareAsync()
//                player.prepare()
//                player.start()
                isPlaying = true
            } catch (e: Exception) {
                Log.e(TAG, "playSound-------------------------------------------------------", e)
            }
        }
    }*/



////TODO: TEST
/*
    private var mBtAdapter: BluetoothAdapter? = null
    private var mA2dpService: BluetoothA2dp? = null



    var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(ctx: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "receive intent for action : " + action!!)
            if (action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                val state =
                    intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_DISCONNECTED)
                if (state == BluetoothA2dp.STATE_CONNECTED) {
                    setIsA2dpReady(true)
                    playMusic()
                } else if (state == BluetoothA2dp.STATE_DISCONNECTED) {
                    setIsA2dpReady(false)
                }
            } else if (action == BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED) {
                val state =
                    intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_NOT_PLAYING)
                if (state == BluetoothA2dp.STATE_PLAYING) {
                    Log.d(TAG, "A2DP start playing")
                    Toast.makeText(appContext, "A2dp is playing", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "A2DP stop playing")
                    Toast.makeText(appContext, "A2dp is stopped", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    private var mIsA2dpReady = false
    fun setIsA2dpReady(ready: Boolean) {
        mIsA2dpReady = ready
        Toast.makeText(appContext, "A2DP ready ? " + if (ready) "true" else "false", Toast.LENGTH_SHORT).show()
    }

    private val mA2dpListener = object : ServiceListener {

        override fun onServiceConnected(profile: Int, a2dp: BluetoothProfile) {
            Log.d(TAG, "a2dp service connected. profile = $profile")
            if (profile == BluetoothProfile.A2DP) {
                mA2dpService = a2dp as BluetoothA2dp
                if (mAudioManager!!.isBluetoothA2dpOn) {
                    setIsA2dpReady(true)
                    playMusic()
                } else {
                    Log.d(TAG, "bluetooth a2dp is not on while service connected")
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            setIsA2dpReady(false)
        }
    }
*/

//    private fun releaseMediaPlayer() {
//        mPlayer?.release()
//        mPlayer = null
//    }

/*private fun playMusic() {
        mPlayer = MediaPlayer()
        val music = MediaPlayer.create(appContext, com.cesoft.cesble.R.raw.beep);
        val assetManager = appContext.assets
        val fd: AssetFileDescriptor
        try {
            fd = assetManager.openFd("Radioactive.mp3")
            mPlayer.setDataSource(fd.fileDescriptor)
            mPlayer.prepare()
            Log.d(TAG, "start play music")
            mPlayer.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    public void playSound(Context context) throws IllegalArgumentException,
    SecurityException,
    IllegalStateException,
    IOException {

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        MediaPlayer mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setDataSource(context, soundUri);
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            // Uncomment the following line if you aim to play it repeatedly
            // mMediaPlayer.setLooping(true);
            mMediaPlayer.prepare();
            mMediaPlayer.start();
        }
    }

    I found another answer:

    try {
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
        r.play();
    } catch (Exception e) {
        e.printStackTrace();
    }*/
}