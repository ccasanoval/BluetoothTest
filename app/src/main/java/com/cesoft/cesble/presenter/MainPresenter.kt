package com.cesoft.cesble.presenter

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import android.view.ContextMenu
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import com.cesoft.cesble.R
import com.cesoft.cesble.adapter.BTDeviceAdapter
import com.cesoft.cesble.adapter.BTLEDeviceAdapter
import com.cesoft.cesble.adapter.BTViewHolder
import com.cesoft.cesble.device.Audio
import com.cesoft.cesble.device.Bluetooth
import com.cesoft.cesble.device.BluetoothClassic
import com.cesoft.cesble.device.BluetoothLE
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.koin.core.KoinComponent
import org.koin.core.inject


//TODO: https://developer.android.com/reference/android/bluetooth/BluetoothHeadset

//TODO: comprobar si es headset y permitir el pareamiento y el uso...

//https://blog.usejournal.com/improve-recyclerview-performance-ede5cec6c5bf
class MainPresenter(private val view: View) : ViewModel(), KoinComponent {

    companion object {
        private val TAG = MainPresenter::class.java.simpleName
        private const val REQUEST_ENABLE_BT = 6968
        private const val PERMISSION_REQUEST_LOCATION = 6969
        private const val PERMISSION_REQUEST_AUDIO_RECORD = 6970
    }

    interface View {
        val btnScanClassic: Button
        val btnScanLowEnergy: Button
        val btnPaired: Button
        val btnScanStop: FloatingActionButton
        val txtStatus: TextView
        val listDevices: RecyclerView
        //val broadcastReceiver: BroadcastReceiver
        fun startActivityForResult(intent: Intent, requestCode: Int)
        fun requestPermissions2(permissions: Array<String>, requestCode: Int)
        fun alert(id: Int)
        fun alertDialog(title: Any, message: Any, listener: YesNoListener?)
    }

    interface YesNoListener {
        fun onYes()
        fun onNo()
    }

    private val app: Application by inject()
    private val bluetooth: Bluetooth by inject()
    private val bluetoothClassic: BluetoothClassic by inject()
    private val bluetoothLE: BluetoothLE by inject()
    private val audio: Audio by inject()

    private lateinit var viewAdapter: RecyclerView.Adapter<BTViewHolder>
    // if <uses-feature android:name="android.hardware.bluetooth_le" android:required=" F A L S E "/>
    private var isLowEnergyEnabled: Boolean = false
    private var isScanning = false

    private val textScanning = app.getString(R.string.state_scanning)
    private val textConnecting = app.getString(R.string.state_connecting)
    private val textDisconnecting = app.getString(R.string.state_disconnecting)
    private val textConnected = app.getString(R.string.state_connected)
    private val textDisconnected = app.getString(R.string.state_disconnected)
    private val textTurningOff = app.getString(R.string.state_turning_off)
    private val textTurningOn = app.getString(R.string.state_turning_on)
    private val textPaired = app.getString(R.string.bluetooth_paired_ok)
    private val textPairedError = app.getString(R.string.bluetooth_paired_error)



    //----------------------------------------------------------------------------------------------
    // INIT
    //----------------------------------------------------------------------------------------------
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onBroadcastReceiver(intent)
        }
    }
    init {
        if(checkPermissionsForBluetoothLowEnergy())
            turnOnBluetooth()

        isLowEnergyEnabled =
            app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if (!isLowEnergyEnabled) {
            view.alert(R.string.bluetooth_low_energy_not_supported)
            Log.e(TAG, "INIT------------------------------------bluetooth_low_energy_not_supported")
        }

        //onBroadcastReceiver
        app.registerReceiver(broadcastReceiver,IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        app.registerReceiver(broadcastReceiver,IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED))
        app.registerReceiver(broadcastReceiver,IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
        app.registerReceiver(broadcastReceiver,IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        app.registerReceiver(broadcastReceiver,IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED))
        app.registerReceiver(broadcastReceiver,IntentFilter(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        app.registerReceiver(broadcastReceiver,IntentFilter(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE))
        app.registerReceiver(broadcastReceiver,IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))

        app.registerReceiver(broadcastReceiver,IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        app.registerReceiver(broadcastReceiver,IntentFilter(AudioManager.ACTION_HEADSET_PLUG))
        app.registerReceiver(broadcastReceiver,IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            app.registerReceiver(broadcastReceiver,IntentFilter(AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED))

        enableAllUI()

        view.btnScanClassic.setOnClickListener {
            if (bluetooth.isEnabled)
                startScanClassic()
            else
                view.alert(R.string.bluetooth_disabled)
        }
        view.btnScanLowEnergy.setOnClickListener {
            if (bluetooth.isEnabled)
                startScanLE()
            else
                view.alert(R.string.bluetooth_disabled)
        }
        view.btnPaired.setOnClickListener {
            if (bluetooth.isEnabled)
                showPairedDevices()
            else
                view.alert(R.string.bluetooth_disabled)
        }
        view.btnScanStop.setOnClickListener {
            if (bluetooth.isEnabled) {
                Log.e(TAG,"btnScanStop.setOnClickListener: Stop Scan---------------------------------------------")
                bluetoothClassic.stopScan()
                bluetoothLE.stopScan()
                enableAllUI()
            } else
                view.alert(R.string.bluetooth_disabled)
        }
    }

    fun onDestroy() {
        app.unregisterReceiver(broadcastReceiver)
    }


    //----------------------------------------------------------------------------------------------
    // ADAPTER CLICK LISTENERS
    //----------------------------------------------------------------------------------------------
    private val pairedClickListener = android.view.View.OnClickListener {
        /*val adapter = viewAdapter as BTDeviceAdapter
        val device = adapter.getItemAt(it.tag as Int)
        Log.e(TAG,"pairedClickListener--------------tag=${it.tag}---------------------------device=$device")
        if(device.type != BluetoothDevice.DEVICE_TYPE_LE && device.type != BluetoothDevice.DEVICE_TYPE_DUAL) {
            view.alert(R.string.bluetooth_low_energy_not)
            Log.e(TAG,"This device is not LE so connecting by GATT won't work")
        }
        bluetooth.connectGatt(device)
        bluetooth.getProfileProxy()*/
    }
    private val classicClickListener = android.view.View.OnClickListener {
        val adapter = viewAdapter as BTDeviceAdapter
        val device = adapter.getItemAt(it.tag as Int)
        Log.e(TAG, "classicClickListener----${it.tag}-------------------------$device")
        askToPair(device)
    }
    private val lowEnergyClickListener = android.view.View.OnClickListener {
        val adapter = viewAdapter as BTLEDeviceAdapter
        Log.e(TAG,"lowEnergyClickListener----${it.tag}-----------------------" + adapter.getItemAt(it.tag as Int))
    }

    //----------------------------------------------------------------------------------------------
    // ADAPTER CONTEXT MENU LISTENERS
    //----------------------------------------------------------------------------------------------
    private val pairedContextMenuListener =
        android.view.View.OnCreateContextMenuListener { contextMenu: ContextMenu, view: android.view.View, contextMenuInfo: ContextMenu.ContextMenuInfo? ->
            val adapter = viewAdapter as BTDeviceAdapter
            val device = adapter.getItemAt(view.tag as Int)
            Log.e(TAG,"pairedContextMenuListener----tag=${view.tag}-----------------------device=$device")
            contextMenu.setHeaderTitle("Test on Paired Devices")
            //              groupId,   itemId,   order,       title
            contextMenu.add(0, view.id, 0, "SPP Test").setOnMenuItemClickListener {
                TestSPP(view.context).start(device)
                true
            }
            contextMenu.add(0, view.id, 0, "Connect Classic").setOnMenuItemClickListener {
                bluetooth.connectClassic(device)
                true
            }
            contextMenu.add(0, view.id, 0, "LE Test").setOnMenuItemClickListener {
                TestLE.start(device)
                true
            }
            contextMenu.add(0, view.id, 0, "Connect Gatt").setOnMenuItemClickListener {
                bluetooth.connectGatt(device)
                true
            }
        }
    private val classicContextMenuListener =
        android.view.View.OnCreateContextMenuListener { contextMenu: ContextMenu, view: android.view.View, contextMenuInfo: ContextMenu.ContextMenuInfo? ->
            val adapter = viewAdapter as BTDeviceAdapter
            Log.e(TAG,"classicContextMenuListener----${view.tag}-----------------------" + adapter.getItemAt(view.tag as Int))
            contextMenu.setHeaderTitle("Test on BT Devices")
            contextMenu.add(0, view.id, 0, "SPP").setOnMenuItemClickListener {
                TestSPP(view.context).start(adapter.getItemAt(view.tag as Int))
                true
            }//groupId, itemId, order, title
            contextMenu.add(0, view.id, 0, "LE").setOnMenuItemClickListener {
                TestLE.start(adapter.getItemAt(view.tag as Int))
                true
            }
        }
    private val leContextMenuListener =
        android.view.View.OnCreateContextMenuListener { contextMenu: ContextMenu, view: android.view.View, contextMenuInfo: ContextMenu.ContextMenuInfo? ->
            val adapter = viewAdapter as BTLEDeviceAdapter
            Log.e(TAG,"leContextMenuListener----${view.tag}-----------------------" + adapter.getItemAt(view.tag as Int))
            contextMenu.setHeaderTitle("Test on LE BT Devices")
            contextMenu.add(0, view.id, 0, "SPP").setOnMenuItemClickListener {
                TestSPP(view.context).start(adapter.getItemAt(view.tag as Int).device)
                true
            }//groupId, itemId, order, title
            contextMenu.add(0, view.id, 0, "LE").setOnMenuItemClickListener {
                TestLE.start(adapter.getItemAt(view.tag as Int).device)
                true
            }
        }

    fun onResume() {
        turnOnBluetooth()
    }

    private fun turnOnBluetooth() {
        if (bluetooth.isDisabled) {
            disableAllUI()
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            view.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else if (!isScanning) {
            enableAllUI()
        }
    }

    //----------------------------------------------------------------------------------------------
    /// Bluetooth
    fun switchBT() {
        //if(checkPermissionsForBluetoothLowEnergy())
            bluetooth.switchOnOf()
    }
    fun resetBT() {
        if(checkPermissionsForBluetoothLowEnergy())
            bluetooth.reset()
    }
    /// Audio
    fun playMusic() {
        if(checkPermissionsForAudioRecording())
            audio.playMusic()
    }
    fun startRecordingAudio() {
        if(checkPermissionsForAudioRecording())
            audio.startRecording()
    }
    fun stopRecordingAudio() {
        if(checkPermissionsForAudioRecording())
            audio.stopRecording()
    }
    fun playAudio() {
        if(checkPermissionsForAudioRecording())
            audio.playAudio()
    }
    fun stopSound() {
        if(checkPermissionsForAudioRecording())
            audio.stop()
    }

    //----------------------------------------------------------------------------------------------
    fun onBroadcastReceiver(intent: Intent) {
        when (val action = intent.action) {

            /// AudioManager ///
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                Log.e(TAG,"ACTION_AUDIO_BECOMING_NOISY-----------------------------------------------------")
            }
            AudioManager.ACTION_HEADSET_PLUG -> {
                Log.e(TAG,"ACTION_HEADSET_PLUG -----------------------------------------------------")
            }
            AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                val stateOld = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                Log.e(TAG,"ACTION_SCO_AUDIO_STATE_UPDATED-----------------------------------------------------STATE: ${Audio.stateToString(stateOld)} --> ${Audio.stateToString(state)}")
                if(state == AudioManager.SCO_AUDIO_STATE_CONNECTED)
                    audio.setBluetoothScoOn()
            }

            /// BluetoothAdapter ///
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR)
                when(state) {
                    BluetoothAdapter.STATE_CONNECTING -> view.txtStatus.text = textConnecting
                    BluetoothAdapter.STATE_DISCONNECTING -> view.txtStatus.text = textDisconnecting
                    BluetoothAdapter.STATE_CONNECTED -> view.txtStatus.text = textConnected
                    BluetoothAdapter.STATE_DISCONNECTED -> view.txtStatus.text = textDisconnected
                }
                Log.e(TAG,"ACTION_CONNECTION_STATE_CHANGED----------------------------------------------------- ${Bluetooth.stateAdapterToString(state)}")
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when(state) {
                    BluetoothAdapter.STATE_ON -> {
                        enableAllUI()
                        view.txtStatus.text = "On"
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        disableAllUI()
                        view.txtStatus.text = "Off"
                    }
                    BluetoothAdapter.STATE_CONNECTING -> view.txtStatus.text = textConnecting
                    BluetoothAdapter.STATE_DISCONNECTING -> view.txtStatus.text = textDisconnecting
                    BluetoothAdapter.STATE_CONNECTED -> view.txtStatus.text = textConnected
                    BluetoothAdapter.STATE_DISCONNECTED -> view.txtStatus.text = textDisconnected
                    BluetoothAdapter.STATE_TURNING_OFF -> view.txtStatus.text = textTurningOff
                    BluetoothAdapter.STATE_TURNING_ON -> view.txtStatus.text = textTurningOn
                }
                Log.e(TAG,"ACTION_STATE_CHANGED----------------------------------------------------- ${Bluetooth.stateAdapterToString(state)}")
            }
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                Log.e(TAG,"ACTION_BOND_STATE_CHANGED--------------------------------------------------state=$state prevState=$prevState")
                //BOND_BONDED = 12;BOND_BONDING = 11;NONE=10
                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    Log.e(TAG,"broadcastReceiver:onReceive-----------------------------------------------------Paired")
                } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED) {
                    Log.e(TAG,"broadcastReceiver:onReceive-----------------------------------------------------Unpaired")
                }
            }
            else ->
                Log.e(TAG,"onBroadcastReceiver : action=$action")
        }

    }

    //----------------------------------------------------------------------------------------------
    // UI
    private fun disableAllUI() {
        view.btnScanClassic.isEnabled = false
        view.btnScanLowEnergy.isEnabled = false
        view.btnPaired.isEnabled = false
        view.btnScanStop.isEnabled = false
    }

    private fun enableAllUI() {
        view.btnScanClassic.isEnabled = true
        view.btnScanLowEnergy.isEnabled = true
        view.btnPaired.isEnabled = true
        view.btnScanStop.isEnabled = false
        //Log.e(TAG,"enableAllUI-------------------------------------------------------------------------------------")
    }

    @Synchronized
    private fun stopClassicScanUI() {
        view.btnScanClassic.isEnabled = true
        //view.btnScanLowEnergy.isEnabled = isLowEnergyEnabled
        //view.btnPaired.isEnabled = true
        view.btnScanStop.isEnabled = false
        //
        view.txtStatus.text = ""
        Log.e(TAG,"stopClassicScanUI-------------------------------------------------------------------------------------")
    }

    private fun startClassicScanUI() {
        view.btnScanClassic.isEnabled = false
        //view.btnScanLowEnergy.isEnabled = true
        //view.btnPaired.isEnabled = true
        view.btnScanStop.isEnabled = true
        //
        view.txtStatus.text = textScanning
        Log.e(TAG,"startClassicScanUI-------------------------------------------------------------------------------------")
    }

    private fun startScanLEUI() {
        //view.btnScanClassic.isEnabled = true
        view.btnScanLowEnergy.isEnabled = false
        //view.btnPaired.isEnabled = true
        view.btnScanStop.isEnabled = true
        //
        view.txtStatus.text = textScanning
        Log.e(TAG,"startScanLEUI-------------------------------------------------------------------------------------")
    }


    //----------------------------------------------------------------------------------------------
    // CLASSIC SCAN
    //----------------------------------------------------------------------------------------------
    private fun startScanClassic() {
        enableAllUI()
        bluetoothLE.stopScan()

        var isNewScanClassic = true
        bluetoothClassic.startScan(object : BluetoothClassic.Callback {
            override fun onDeviceFound(bluetoothDevice: BluetoothDevice) {

                //if(::viewAdapter.isInitialized && viewAdapter is BTDeviceAdapter) {
                if (isNewScanClassic) {
                    viewAdapter = BTDeviceAdapter(
                        arrayListOf(bluetoothDevice),
                        classicClickListener,
                        classicContextMenuListener
                    )
                    isNewScanClassic = false
                } else if (::viewAdapter.isInitialized) {
                    (viewAdapter as BTDeviceAdapter).add(bluetoothDevice)
                    viewAdapter.notifyDataSetChanged()
                }
                view.listDevices.adapter = viewAdapter
            }

            override fun onDiscoveryFinished() {
                stopClassicScanUI()
            }
        })

        startClassicScanUI()
        isScanning = true
    }


    //----------------------------------------------------------------------------------------------
    // LOW ENERGY SCAN
    //----------------------------------------------------------------------------------------------
    private fun startScanLE() {
        bluetoothClassic.stopScan()
        bluetoothLE.startScan(callback)
        startScanLEUI()
        isScanning = true
    }
    private val callback = object : BluetoothLE.Callback {
        override fun onBatchScanResults(results: List<ScanResult>) {
            viewAdapter = BTLEDeviceAdapter(results, lowEnergyClickListener, leContextMenuListener)
            view.listDevices.adapter = viewAdapter
        }

        override fun onScanFailed(errorCode: Int) {
            view.txtStatus.text = "onScanFailed errorCode=$errorCode"
            view.listDevices.adapter = viewAdapter
        }
    }


    //----------------------------------------------------------------------------------------------
    // PAIRED DEVICES
    //----------------------------------------------------------------------------------------------
    private fun showPairedDevices() {
        bluetoothClassic.stopScan()
        bluetoothLE.stopScan()
        bluetooth.pairedDevices?.let {
            val dataSet = ArrayList<BluetoothDevice>(it)//TODO: simplify
            viewAdapter = BTDeviceAdapter(dataSet, pairedClickListener, pairedContextMenuListener)
            view.listDevices.adapter = viewAdapter
            for(device in dataSet) {
                Log.e(TAG, "showPairedDevices------------------------------------------------${device.name} - ${device.address}")
            }
        }
    }
    private fun askToPair(device: BluetoothDevice) {
        Log.e(TAG, "askToPair------------------------------------------------${device.name} / ${device.address}")
        val tle = app.resources.getString(R.string.bluetooth_pairing_tle)
        val msg = String.format(app.resources.getString(R.string.bluetooth_pairing_msg), device.name)
        view.alertDialog(tle, msg,
            object : YesNoListener {
                override fun onNo() {
                    view.txtStatus.text = ""
                }
                override fun onYes() {
                    if(device.createBond())
                        view.txtStatus.text = textPaired
                    else
                        view.txtStatus.text = textPairedError
                }
            })
    }


    //----------------------------------------------------------------------------------------------
    // PERMISSIONS : BT Low Energy needs location access permission to scan for LE devices
    //----------------------------------------------------------------------------------------------
    private fun checkPermissionsForBluetoothLowEnergy() : Boolean {
        if(app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            view.alertDialog(
                R.string.location_permission_tle,
                R.string.location_permission_msg,
                object : YesNoListener {
                    override fun onNo() {
                        view.alert(R.string.location_permission_err)
                    }
                    override fun onYes() {
                        view.requestPermissions2(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            PERMISSION_REQUEST_LOCATION
                        )
                    }

                })
            return false
        }
        else
            return true
    }
    private fun checkPermissionsForAudioRecording() : Boolean {
        val permission1 = Manifest.permission.RECORD_AUDIO
//        val permission2 = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if(app.checkSelfPermission(permission1) != PackageManager.PERMISSION_GRANTED) {
//            || app.checkSelfPermission(permission2) != PackageManager.PERMISSION_GRANTED) {
            view.alertDialog(
                R.string.audiorecord_permission_tle,
                R.string.audiorecord_permission_msg,
                object : YesNoListener {
                    override fun onNo() {
                        view.alert(R.string.audiorecord_permission_err)
                    }
                    override fun onYes() {
                        view.requestPermissions2(
                            arrayOf(permission1),//, permission2
                            PERMISSION_REQUEST_AUDIO_RECORD
                        )
                    }

                })
            return false
        }
        else
            return true
    }
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_LOCATION -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    view.alert(R.string.location_permission_err)
                }
                return
            }
            PERMISSION_REQUEST_AUDIO_RECORD -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    view.alert(R.string.audiorecord_permission_err)
                }
                return
            }
        }
    }

}
