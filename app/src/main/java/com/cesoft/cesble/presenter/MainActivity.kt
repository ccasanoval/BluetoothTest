package com.cesoft.cesble.presenter

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cesoft.cesble.R
import com.cesoft.cesble.device.DiskEvent
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.experimental.and


class MainActivity : AppCompatActivity(), MainPresenter.View {

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }

    // implements MainPresenter.View +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
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
    private fun alert(msg: String, vararg params: Any) {
        if(params.isNotEmpty())
            Toast.makeText(this, msg.format(params), Toast.LENGTH_LONG).show()
        else
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
    override fun alertDialog(title: Any, message: Any, listener: MainPresenter.YesNoListener?) {
        val builder = AlertDialog.Builder(this)
        if(title is Int)            builder.setTitle(title)
        else if(title is String)    builder.setTitle(title)
        if(message is Int)          builder.setMessage(message)
        else if(message is String)  builder.setMessage(message)
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
Log.e(TAG, "onCreate----------------------------------------------------------------")
    }
    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }

    override fun onStart() {
        super.onStart()
Log.e(TAG, "onStart----------------------------------------------------------------")
        presenter.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        presenter.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG, "onResume----------------------------------------------------------------")
        presenter.onResume()
    }

    private var menu: Menu? = null
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_bt_onoff -> presenter.switchBT()
            R.id.action_bt_reset -> presenter.resetBT()
            R.id.action_play_music -> presenter.playMusic()
            R.id.action_audio_record -> {
                presenter.startRecordingAudio()
            }
            R.id.action_audio_record_stop -> {
                presenter.stopRecordingAudio()
            }
            R.id.action_audio_play -> presenter.playAudio()
            R.id.action_sound_stop -> presenter.stopSound()
            //R.id.action_peltor -> presenter.peltorTest()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        presenter.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDiskEvent(event: DiskEvent) {
        when(event.type) {
            DiskEvent.Type.DISK_AT_90 ->
                alert("Disk space is decreasing and scarce now! Free space: ${event.freeSpace}%")
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onButtonEvent(event: TestLE.ButtonEvent) {
        var text = ""
        if(event.type and TestLE.ButtonEvent.Type.PTT.code > 0) {
            text += " PTT On "
        }
        if(event.type and TestLE.ButtonEvent.Type.PTTB1.code > 0) {
            text += "PTTB1 On"
        }
        if(event.type and TestLE.ButtonEvent.Type.PTTB2.code > 0) {
            text += "PTTB2 On"
        }
        if(event.type and TestLE.ButtonEvent.Type.PTTE.code > 0) {
            text += "PTTE On"
        }
        if(event.type and TestLE.ButtonEvent.Type.PTTS.code > 0) {
            text += "PTTS On"
        }
        if(event.type and TestLE.ButtonEvent.Type.MFB.code > 0) {
            text += "MFB On"
        }
        txtStatus.text = text
    }
}
