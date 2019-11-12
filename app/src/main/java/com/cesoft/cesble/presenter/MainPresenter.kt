package com.cesoft.cesble.presenter

import android.Manifest
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.ViewModel
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.content.IntentFilter
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.util.Log
import android.view.ContextMenu
import com.cesoft.cesble.adapter.BTDeviceAdapter
import com.cesoft.cesble.adapter.BTLEDeviceAdapter
import com.cesoft.cesble.R
import com.cesoft.cesble.adapter.BTViewHolder
import com.cesoft.cesble.device.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import kotlin.collections.ArrayList


//TODO: https://developer.android.com/reference/android/bluetooth/BluetoothHeadset

//TODO: comprobar si es headset y permitir el pareamiento y el uso...

//https://blog.usejournal.com/improve-recyclerview-performance-ede5cec6c5bf
class MainPresenter(private val view: View) : ViewModel(), KoinComponent {

    companion object {
        private val TAG = MainPresenter::class.java.simpleName
        private const val PERMISSION_REQUEST_LOCATION = 6969
        private const val REQUEST_ENABLE_BT = 6968
    }

    interface View {
        val app: Application
        val ctx: Context
        val btnScanClassic: Button
        val btnScanLowEnergy: Button
        val btnPaired: Button
        val btnScanStop: FloatingActionButton
        val txtStatus: TextView
        val listDevices: RecyclerView
        val broadcastReceiver: BroadcastReceiver
        fun startActivityForResult(intent: Intent, requestCode: Int)
        fun requestPermissions2(permissions: Array<String>, requestCode: Int)
        fun alert(id: Int)
        fun alertDialog(title: Int, message: Int, listener: YesNoListener?)
    }
    interface YesNoListener {
        fun onYes()
        fun onNo()
    }

    private val bluetooth : Bluetooth by inject()
    private val bluetoothClassic : BluetoothClassic by inject()
    private val bluetoothLE : BluetoothLE by inject()


    // if <uses-feature android:name="android.hardware.bluetooth_le" android:required=" F A L S E "/>
    private var isLowEnergyEnabled: Boolean = false
    private var isScanning = false
    private val textScanning = view.app.getString(R.string.bluetooth_scanning)
    private val textPaired = view.app.getString(R.string.bluetooth_paired_ok)
    private val textPairedError = view.app.getString(R.string.bluetooth_paired_error)

    private lateinit var viewAdapter: RecyclerView.Adapter<BTViewHolder>


    //----------------------------------------------------------------------------------------------
    // ADAPTER CLICK LISTENERS
    //----------------------------------------------------------------------------------------------
    private val pairedClickListener = android.view.View.OnClickListener {
        val adapter =  viewAdapter as BTDeviceAdapter
        val device = adapter.getItemAt(it.tag as Int)
        Log.e(TAG, "pairedClickListener--------------tag=${it.tag}---------------------------device=$device")

        bluetooth.connect(device)

        listenBluetoothProfileHeadset()

        bluetooth.getProfileProxy()
    }
    private val classicClickListener = android.view.View.OnClickListener {
        val adapter =  viewAdapter as BTDeviceAdapter
        val device = adapter.getItemAt(it.tag as Int)
        Log.e(TAG, "classicClickListener----${it.tag}-------------------------$device")
        askToPair(device)
    }
    private val lowEnergyClickListener = android.view.View.OnClickListener {
        val adapter =  viewAdapter as BTLEDeviceAdapter
        Log.e(TAG, "lowEnergyClickListener----${it.tag}-----------------------"+adapter.getItemAt(it.tag as Int))
    }

    //----------------------------------------------------------------------------------------------
    // ADAPTER CONTEXT MENU LISTENERS
    //----------------------------------------------------------------------------------------------
    private val pairedContextMenuListener = android.view.View.OnCreateContextMenuListener {
            contextMenu: ContextMenu, view: android.view.View, contextMenuInfo: ContextMenu.ContextMenuInfo? ->
        val adapter =  viewAdapter as BTDeviceAdapter
        Log.e(TAG, "lowEnergyContextMenuListener----${view.tag}-----------------------"+adapter.getItemAt(view.tag as Int))
        contextMenu.setHeaderTitle("Test on Paired Devices")
        contextMenu.add(0, view.id, 0, "SPP").setOnMenuItemClickListener {
            TestSPP(view.context).start(adapter.getItemAt(view.tag as Int))
            true
        }//groupId, itemId, order, title
        contextMenu.add(0, view.id, 0, "LE").setOnMenuItemClickListener {
            TestLE().start(adapter.getItemAt(view.tag as Int))
            true
        }
    }
    private val classicContextMenuListener = android.view.View.OnCreateContextMenuListener {
            contextMenu: ContextMenu, view: android.view.View, contextMenuInfo: ContextMenu.ContextMenuInfo? ->
        val adapter =  viewAdapter as BTDeviceAdapter
        Log.e(TAG, "lowEnergyContextMenuListener----${view.tag}-----------------------"+adapter.getItemAt(view.tag as Int))
        contextMenu.setHeaderTitle("Test on BT Devices")
        contextMenu.add(0, view.id, 0, "SPP").setOnMenuItemClickListener {
            TestSPP(view.context).start(adapter.getItemAt(view.tag as Int))
            true
        }//groupId, itemId, order, title
        contextMenu.add(0, view.id, 0, "LE").setOnMenuItemClickListener {
            TestLE().start(adapter.getItemAt(view.tag as Int))
            true
        }
    }
    private val leContextMenuListener = android.view.View.OnCreateContextMenuListener {
            contextMenu: ContextMenu, view: android.view.View, contextMenuInfo: ContextMenu.ContextMenuInfo? ->
        val adapter =  viewAdapter as BTLEDeviceAdapter
        Log.e(TAG, "lowEnergyContextMenuListener----${view.tag}-----------------------"+adapter.getItemAt(view.tag as Int))
        contextMenu.setHeaderTitle("Test on LE BT Devices")
        contextMenu.add(0, view.id, 0, "SPP").setOnMenuItemClickListener {
            TestSPP(view.context).start(adapter.getItemAt(view.tag as Int).device)
            true
        }//groupId, itemId, order, title
        contextMenu.add(0, view.id, 0, "LE").setOnMenuItemClickListener {
            TestLE().start(adapter.getItemAt(view.tag as Int).device)
            true
        }
    }


    init {
        checkPermissionsForBluetoothLowEnergy()

        isLowEnergyEnabled = view.app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if( ! isLowEnergyEnabled)
            view.alert(R.string.bluetooth_low_energy_not_supported)

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
                Log.e(TAG, "btnScanStop.setOnClickListener: Stop Scan---------------------------------------------")
                bluetoothClassic.stopScan()
                bluetoothLE.stopScan()
                enableAllUI()
            }
            else
                view.alert(R.string.bluetooth_disabled)
        }

    }

    fun onResume() {
        turnOnBluetooth()
    }

    private fun turnOnBluetooth() {
        if(bluetooth.isDisabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            view.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
        else if( ! isScanning) {
            view.btnScanClassic.isEnabled = true
        }
    }

    //----------------------------------------------------------------------------------------------
    // UI
//    private fun disableAllUI() {
//        view.btnScanClassic.isEnabled = false
//        view.btnScanLowEnergy.isEnabled = false
//        view.btnPaired.isEnabled = false
//        view.btnScanStop.isEnabled = false
//    }
    private fun enableAllUI() {
        view.btnScanClassic.isEnabled = true
        view.btnScanLowEnergy.isEnabled = true
        view.btnPaired.isEnabled = true
        view.btnScanStop.isEnabled = false
Log.e(TAG, "enableAllUI-------------------------------------------------------------------------------------")
    }
    @Synchronized
    private fun stopClassicScanUI() {
        view.btnScanClassic.isEnabled = true
        //view.btnScanLowEnergy.isEnabled = isLowEnergyEnabled
        //view.btnPaired.isEnabled = true
        view.btnScanStop.isEnabled = false
        //
        view.txtStatus.text = ""
Log.e(TAG, "stopClassicScanUI-------------------------------------------------------------------------------------")
    }
    private fun startClassicScanUI() {
        view.btnScanClassic.isEnabled = false
        //view.btnScanLowEnergy.isEnabled = true
        //view.btnPaired.isEnabled = true
        view.btnScanStop.isEnabled = true
        //
        view.txtStatus.text = textScanning
Log.e(TAG, "startClassicScanUI-------------------------------------------------------------------------------------")
    }
    private fun startScanLEUI() {
        //view.btnScanClassic.isEnabled = true
        view.btnScanLowEnergy.isEnabled = false
        //view.btnPaired.isEnabled = true
        view.btnScanStop.isEnabled = true
        //
        view.txtStatus.text = textScanning
Log.e(TAG, "startScanLEUI-------------------------------------------------------------------------------------")
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
                if(isNewScanClassic) {
                    viewAdapter = BTDeviceAdapter(arrayListOf(bluetoothDevice),
                        classicClickListener,
                        classicContextMenuListener)
                    isNewScanClassic = false
                }
                else if(::viewAdapter.isInitialized) {
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
    // Callback
    private val callback = object: BluetoothLE.Callback {
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
        }
    }
    private fun askToPair(device: BluetoothDevice) {
        //TODO: ask user if wants to pair the device first?
        Log.e(TAG, "askToPair------------------------------------------------$device")
        view.alertDialog(R.string.bluetooth_pairing_tle, R.string.bluetooth_pairing_msg, object : YesNoListener {
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
    private fun checkPermissionsForBluetoothLowEnergy() {
        Log.e("Main", "checkPermissionsForBLE---------------------------------------------------")
        if(view.app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            view.alertDialog(
                R.string.location_permission_tle,
                R.string.location_permission_msg,
                object : YesNoListener {
                    override fun onNo() {
                        view.alert(R.string.location_permission_err)
                    }
                    override fun onYes() {
                        view.requestPermissions2(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
                    }

                })
        }
        else
            turnOnBluetooth()
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_LOCATION -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    view.alert(R.string.location_permission_err)
//                    view.alertDialog("Functionality limited","Since location access has not been granted, this app will not be able to discover beacons when in the background.",DialogInterface.OnDismissListener { })
                }
                return
            }
        }
    }



    //----------------------------------------------------------------------------------------------
    // HEADSET
    //----------------------------------------------------------------------------------------------
    private fun listenBluetoothProfileHeadset() {
        val bluetoothManager = view.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter.getProfileProxy(view.app, object: BluetoothProfile.ServiceListener {
            override fun onServiceDisconnected(profile: Int) {
                Log.e(TAG, "onServiceDisconnected------------------------------------------------profile=$profile")
            }
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                //isHeadsetConnected = proxy!!.connectedDevices.size > 0
                Log.e(TAG, "onServiceConnected------------------------------------------------profile=$profile N=${proxy!!.connectedDevices.size}")
            }

        }, BluetoothProfile.HEADSET)

        view.app.registerReceiver(view.broadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED))
    }


}
