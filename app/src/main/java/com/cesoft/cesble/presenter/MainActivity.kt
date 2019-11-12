package com.cesoft.cesble.presenter

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Bundle

import android.view.Menu
import android.view.MenuItem
import android.content.Intent
import android.util.Log
import android.widget.Toast
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.view.ContextMenu

import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.floatingactionbutton.FloatingActionButton

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*

import com.cesoft.cesble.R
import android.view.ContextMenu.ContextMenuInfo



class MainActivity : AppCompatActivity(), MainPresenter.View {

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

    // implements MainPresenter.View +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    override val app: Application
        get() = application
    override val ctx: Context
        get() = this
    override lateinit var btnScanClassic: Button
    override lateinit var btnScanLowEnergy: Button
    override lateinit var btnPaired: Button
    override lateinit var btnScanStop: FloatingActionButton
    override lateinit var txtStatus: TextView
    override lateinit var listDevices: RecyclerView
    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        super.startActivityForResult(intent, requestCode)
    }
    override fun alert(id: Int) {
        Toast.makeText(this, getString(id), Toast.LENGTH_LONG).show()
    }
    override fun alertDialog(title: Int, message: Int, listener: MainPresenter.YesNoListener?) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        listener?.let {
            builder.setPositiveButton(android.R.string.yes) { _, _ -> listener.onYes() }
            builder.setNegativeButton(android.R.string.no) { _, _ -> listener.onNo() }
            builder.setOnDismissListener { listener.onNo() }
        }
        builder.show()
    }
    override fun requestPermissions2(permissions: Array<String>, requestCode: Int) {
        super.requestPermissions(permissions, requestCode)
    }
    override val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

Log.e(TAG, "broadcastReceiver:onReceive-----------------------------------------------------action=$action")

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val prevState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                    BluetoothDevice.ERROR
                )
Log.e(TAG, "broadcastReceiver:onReceive-----------------------------------------------------state=$state prevState=$prevState")//BOND_BONDED = 12;BOND_BONDING = 11;NONE=10

                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    Log.e(TAG, "broadcastReceiver:onReceive-----------------------------------------------------Paired")
                } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED) {
                    Log.e(TAG, "broadcastReceiver:onReceive-----------------------------------------------------Unpaired")
                }
            }
        }
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
        btnPaired = btn_paired
        btnScanStop = fab_stop_scanning
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
        when (item.itemId) {
            //R.id.action_settings -> true
            //R.id.action_aina_spp -> ainaSppTest()
            //R.id.action_aina_ble -> ainaBleTest()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        presenter.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


/*
    //----------------------------------------------------------------------------------------------
    private fun ainaBleTest() {
        presenter.ainBleTest()
    }



    //----------------------------------------------------------------------------------------------
    //SPP
    private fun ainaSppTest() {
        presenter.setupSpp()
        //val intent = Intent(applicationContext, DeviceList::class.java)
        //startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            presenter.onActivityResult(requestCode, data)
        }
    }*/
}
