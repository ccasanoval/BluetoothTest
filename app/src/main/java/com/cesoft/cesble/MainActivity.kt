package com.cesoft.cesble

import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import android.content.Intent
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog


class MainActivity : AppCompatActivity(), MainPresenter.View {

    // implements MainPresenter.View +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    override val app: Application
        get() = application
    override lateinit var btnScanClassic: Button
    override lateinit var btnScanLowEnergy: Button
    override lateinit var btnScanStop: Button
    override lateinit var txtStatus: TextView
    override lateinit var listDevices: RecyclerView
    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        super.startActivityForResult(intent, requestCode)
    }
    override fun alert(id: Int) {
        Toast.makeText(this, getString(id), Toast.LENGTH_LONG).show()
    }
    override fun alertDialog(title: String, message: String, onDismissListener: DialogInterface.OnDismissListener) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.setOnDismissListener(onDismissListener)
        builder.show()
    }
    override fun requestPermissions2(permissions: Array<String>, requestCode: Int) {
        super.requestPermissions(permissions, requestCode)
    }

    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    private lateinit var presenter: MainPresenter

    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        list_devices.layoutManager = LinearLayoutManager(this)

        btnScanClassic = btn_start_scan_classic
        btnScanLowEnergy = btn_start_scan_le
        btnScanStop = btn_stop_scan
        txtStatus = txt_status
        listDevices = list_devices
        presenter = MainPresenter(this)
    }

    override fun onResume() {
        super.onResume()
        presenter.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        presenter.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    ///---------------------------------------------------------------------------------------------
    /// SCANNING : https://github.com/NordicSemiconductor/Android-Scanner-Compat-Library
    ///---------------------------------------------------------------------------------------------
    /*private fun startScan() {
        val settings = ScanSettings.Builder()
            .setLegacy(false)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(5000)
            .setUseHardwareBatchingIfSupported(true)
            .build()
        val filters = ArrayList<ScanFilter>()
        //filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid()).build())
        scanner.startScan(filters, settings, callbackStart)
        btn_stop_scan.isEnabled = true
        btn_start_scan.isEnabled = false
    }

    private fun stopScan() {
        scanner.stopScan(callbackStart)
        btn_stop_scan.isEnabled = false
        btn_start_scan.isEnabled = true
    }

    private val callbackStart = object: ScanCallback() {
        private val TAG = "callbackStart"
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            android.util.Log.e(TAG, "onScanResult----------callbackType=$callbackType result=$result ")
            result.scanRecord?.deviceName?.let { deviceName ->
                viewAdapter = BLEAdapter(Array(1) { deviceName })
                list_results.adapter = viewAdapter
            }
        }
        override fun onBatchScanResults(results: List<ScanResult>) {

            val a = results.filter { it.scanRecord?.deviceName != null }
            viewAdapter = BLEAdapter(Array(a.size) { i -> a[i].scanRecord!!.deviceName!!})
            list_results.adapter = viewAdapter

            //android.util.Log.e(TAG, "onBatchScanResults------${a.size}----results=$results ")
            android.util.Log.e(TAG, "onBatchScanResults-- A ----${results.size}----results=$results ")//deviceName=((?!null).)
            android.util.Log.e(TAG, "onBatchScanResults-- B ----${a.size}----results=$a ")//deviceName=((?!null).)
        }
        override fun onScanFailed(errorCode: Int) {
            android.util.Log.e(TAG, "onScanFailed----------errorCode=$errorCode")
            viewAdapter = BLEAdapter(Array(1) {"onScanFailed errorCode=$errorCode"})
            list_results.adapter = viewAdapter
        }
    }

    ///---------------------------------------------------------------------------------------------
    /// BLE : https://github.com/NordicSemiconductor/Android-BLE-Library
    ///---------------------------------------------------------------------------------------------
*/

}
