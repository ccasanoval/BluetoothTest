package com.cesoft.cesble.device

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.*
import android.util.Log
import org.greenrobot.eventbus.EventBus
import org.koin.core.inject
import org.koin.core.KoinComponent
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.concurrent.schedule

class Audio : KoinComponent {
    companion object {
        private val TAG = Audio::class.java.simpleName
        fun stateToString(state: Int) = when(state) {
            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> "DISCONNECTED"
            AudioManager.SCO_AUDIO_STATE_CONNECTED -> "CONNECTED!!!"
            AudioManager.SCO_AUDIO_STATE_CONNECTING -> "CONNECTING..."
            else -> "?"
        }
    }

    private val appContext: Context by inject()

    private var audioManager: AudioManager
    private var mediaRecorder: MediaRecorder
    private var player: MediaPlayer

    private var fileName: String

    private var isInitListeners = false
    private var isRecording = false
    private var isScheduledToPlay = false

    init {
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaRecorder = MediaRecorder()
        player = MediaPlayer()

        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        audioManager.isSpeakerphoneOn = false
        //audioManager.isWiredHeadsetOn = false
        //audioManager.startBluetoothSco()

        val dir = appContext.filesDir
        val freePerCent = dir.freeSpace * 100 / dir.totalSpace
        if(freePerCent < 15) {
            EventBus.getDefault().post(DiskEvent(DiskEvent.Type.DISK_AT_90, freePerCent))
        }
        fileName = dir.absolutePath + "/audiorecordtest"
        val file = File(fileName)
        if(!file.exists()) {
            try {
                //https://developer.android.com/training/data-storage/files/internal
                file.createNewFile()
            } catch(e: Exception) {
                Log.e(TAG, "init:e:", e)
            }
        }
        else {
            file.setReadable(true, false)
        }
    }

    fun playAudio() {
        Log.e(TAG, "startPlaying-------------------------------------------------------000")
        val file = File(fileName)
        if(!file.exists()) {
            Log.e(TAG, "startPlaying-------------------------------------------------------NO EXISTE ARCHIVO")
            return
        }

        Log.e(TAG, "startPlaying-------------------------------------------------------SCO=" + audioManager.isBluetoothScoAvailableOffCall)

        try {
            player.setDataSource(fileName)
            player.prepare()
            player.setOnCompletionListener {
                Log.e(TAG, "onCompletion---------------------------------------")
                stop()
            }
            player.start()
            isRecording = false
        }
        catch(e: Exception) {
            Log.e(TAG, "startPlaying-------------------------------------------------------", e)
            stop()
        }
    }

    fun startRecording() {
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            //mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.VORBIS);
            mediaRecorder.setOutputFile(fileName)
            mediaRecorder.prepare()
            mediaRecorder.start()
            isRecording = true
        }
        catch(e: IOException) {
            Log.e(TAG, "startRecording:e:", e)
        }
    }

    fun stopRecording() {
        mediaRecorder.stop()
        mediaRecorder.release()
        isRecording = false
    }

    fun stop() {
        if(player.isPlaying) {
            player.stop()
        }
    }

    fun playMusic() {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP)
        Log.e(TAG, "playMusic-------------------------------------------------------isInit=$isInitListeners isPlaying=${player.isPlaying}")
        if(player.isPlaying) {
            Log.e(TAG, "playMusic-------------------------------------------------------STOP************")
            player.stop()
            isScheduledToPlay = true
        }
        else {
            Log.e(TAG, "playMusic-------------------------------------------------------PREP************")
            prepareMusic()
        }
    }

    private fun initListeners() {
        if(isInitListeners) return
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build()
            )
            player.setOnPreparedListener {
                Log.e(TAG, "init:setOnPreparedListener-----------------PREPARE--------------------------------------")
                isInitListeners = true
                startPlayerDelayed(500)
            }
            player.setOnErrorListener { player, what, extra ->
                Log.e(TAG, "init:setOnErrorListener---------------------ERROR--------------------------------- $what, $extra")
                isInitListeners = false
                false
            }
            player.setOnCompletionListener {
                Log.e(TAG,"init:setOnCompletionListener-----------------COMPLETED--------------------------------------9")
                if(isScheduledToPlay) {
                    isScheduledToPlay = false
                    startPlayerDelayed(100)
                }
            }
        }
        catch(e: Exception) {
            Log.e(TAG, "init:e:-------------------------------------------------------", e)
            isScheduledToPlay = false
        }
    }
    private fun prepareMusic() {
        Log.e(TAG, "prepareMusic-------------------------------------------------------")
        if( ! isInitListeners) initListeners()
        val fd: AssetFileDescriptor = appContext.assets.openFd("Cochise.mp3")
        player.reset()
        player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)//setDataSource(fd.fileDescriptor)
        player.prepareAsync()
    }

    fun setBluetoothScoOn() {
        Log.e(TAG, "setBluetoothScoOn-------------------------------------------------------")
//        audioManager.mode = AudioManager.MODE_IN_CALL
//        audioManager.isBluetoothScoOn = true
//        audioManager.startBluetoothSco()
    }

    private fun startPlayerDelayed(delay: Long) {
        if(delay <= 0)
            player.start()
        else
            Timer("playMusic", false).schedule(delay) { player.start() }
//            GlobalScope.launch {
//                delay(delay)
//                player.start()
//            }
    }
}