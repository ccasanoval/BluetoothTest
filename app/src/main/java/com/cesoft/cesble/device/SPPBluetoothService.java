package com.cesoft.cesble.device;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


@SuppressLint("NewApi")
public class SPPBluetoothService {
    // Debugging
    private static final String TAG = "Bluetooth Service";

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "Bluetooth Secure";

    // Unique UUID for this application
//    private static final UUID UUID_ANDROID_DEVICE =
//            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID UUID_OTHER_DEVICE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
//    private boolean isAndroid = SPPBluetoothState.DEVICE_ANDROID;

    // Constructor. Prepares a new BluetoothChat session
    // context : The UI Activity Context
    // handler : A Handler to send messages back to the UI Activity
    public SPPBluetoothService(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = SPPBluetoothState.STATE_NONE;
        mHandler = handler;
    }


    // Set the current state of the chat connection
    // state : An integer defining the current connection state
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(SPPBluetoothState.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    // Return the current connection state.
    synchronized int getState() {
        return mState;
    }

    // Start the chat service. Specifically start AcceptThread to begin a
    // session in listening (server) mode. Called by the Activity onResume()
    synchronized void start(/*boolean isAndroid*/) {
        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setState(SPPBluetoothState.STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(/*isAndroid*/);
            mSecureAcceptThread.start();
        }
    }

    // Start the ConnectThread to initiate a connection to a remote device
    // device : The BluetoothDevice to connect
    // secure : Socket Security type - Secure (true) , Insecure (false)
    synchronized void connect(BluetoothDevice device) {
        // Cancel any thread attempting to make a connection
        if (mState == SPPBluetoothState.STATE_CONNECTING && mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(SPPBluetoothState.STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(SPPBluetoothState.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(SPPBluetoothState.DEVICE_NAME, device.getName());
        bundle.putString(SPPBluetoothState.DEVICE_ADDRESS, device.getAddress());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(SPPBluetoothState.STATE_CONNECTED);
    }

    // Stop all threads
    synchronized void stop() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread.kill();
            mSecureAcceptThread = null;
        }
        setState(SPPBluetoothState.STATE_NONE);
    }

    // Write to the ConnectedThread in an unsynchronized manner
    // out : The bytes to write
    void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != SPPBluetoothState.STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    // Indicate that the connection attempt failed and notify the UI Activity
    private void connectionFailed() {
        // Start the service over to restart listening mode
        this.start();
    }

    // Indicate that the connection was lost and notify the UI Activity
    private void connectionLost() {
        // Start the service over to restart listening mode
        this.start();
    }

    // This thread runs while listening for incoming connections. It behaves
    // like a server-side client. It runs until a connection is accepted
    // (or until cancelled)
    private class AcceptThread extends Thread {
        // The local server socket
        private BluetoothServerSocket mmServerSocket;
        private String mSocketType;
        boolean isRunning = true;

        public AcceptThread(/*boolean isAndroid*/) {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                /*if(isAndroid)
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, UUID_ANDROID_DEVICE);
                else*/
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, UUID_OTHER_DEVICE);
            } catch (IOException e) { }
            mmServerSocket = tmp;
        }

        public void run() {
            setName("AcceptThread" + mSocketType);
            BluetoothSocket socket;

            // Listen to the server socket if we're not connected
            while (mState != SPPBluetoothState.STATE_CONNECTED && isRunning) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (this) {
                        switch (mState) {
                            case SPPBluetoothState.STATE_LISTEN:
                            case SPPBluetoothState.STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice(),
                                        mSocketType);
                                break;
                            case SPPBluetoothState.STATE_NONE:
                            case SPPBluetoothState.STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try { socket.close(); } catch (IOException ignore) { }
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        }

        void cancel() {
            try {
                mmServerSocket.close();
                mmServerSocket = null;
            } catch (IOException ignore) { }
        }

        void kill() {
            isRunning = false;
        }
    }


    // This thread runs while attempting to make an outgoing connection
    // with a device. It runs straight through
    // the connection either succeeds or fails
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                /*if(isAndroid)
                    tmp = device.createRfcommSocketToServiceRecord(UUID_ANDROID_DEVICE);
                else*/
                    tmp = device.createRfcommSocketToServiceRecord(UUID_OTHER_DEVICE);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) { }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    // This thread runs during a connection with a remote device.
    // It handles all incoming and outgoing transmissions.
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run() {
            byte[] buffer;
            ArrayList<Byte> arr_byte = new ArrayList<Byte>();

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    int data = mmInStream.read();
                    /*if(data == 0x0A) {
                        Log.e(TAG, "run-----------------------------------------0x0A");
                    }
                    else if(data == 0x0D) {
                        Log.e(TAG, "run-----------------------------------------0x0D");
                        buffer = new byte[arr_byte.size()];
                        for(int i = 0 ; i < arr_byte.size() ; i++) {
                            buffer[i] = arr_byte.get(i).byteValue();
                        }
                        // Send the obtained bytes to the UI Activity
                        mHandler.obtainMessage(SPPBluetoothState.MESSAGE_READ
                                , buffer.length, -1, buffer).sendToTarget();
                        arr_byte = new ArrayList<Byte>();
                    } else*/ {
                        Log.e(TAG, "run-----------------------------------------data="+data+"  arr_byte.size()="+arr_byte.size());
                        buffer = new byte[arr_byte.size()];
                        for(int i=0; i < buffer.length; i++)
                            buffer[i] = arr_byte.get(i);
                        Log.e(TAG, "run-----------------------------------------arr_byte="+new String(buffer));
                        arr_byte.add((byte)data);
                        String cmd = new String(buffer);
                        if(arr_byte.size() > 5) {
                            if("+PTT=P".equals(cmd)) {
                                Log.e(TAG, "run-----------------------------------------1 PTT DOWN ");
                                mHandler.obtainMessage(SPPBluetoothState.MESSAGE_READ, buffer.length, -1, buffer).sendToTarget();
                                arr_byte = new ArrayList<>();
                            }
                            else if("+PTT=R".equals(cmd)) {
                                Log.e(TAG, "run-----------------------------------------1 PTT UP "+arr_byte.toString());
                            }
                            else if("+PTTS=P".equals(cmd)) {
                                Log.e(TAG, "run-----------------------------------------Emergency DOWN");
                            }
                            else if("+PTTS=R".equals(cmd)) {
                                Log.e(TAG, "run-----------------------------------------Emergency UP");
                            }

                        }
//1 - 1 PTT            : "+PTT=P" for button going down and “+PTT=R” for button coming up.
//2 - 2 PTT            : "+PTTS=P" for button going down and “+PTTS=R” for button comingup.
//3 - Emergency btn    : "+PTTE=P" for button going down and “+PTTE=R” for button coming up.
//4 - Left Soft btn (<): "+PTTB1=P" for button going down and “+PTTB1=R” forbutton coming up.
//5 - Right Soft btn(>): "+PTTB2=P" for button going down and “+PTTB2=R” for button coming up.
//6 - Volume Up btn    : "+VGS=U" when button press is detected.
//7 - Volume Down btn  : "+VGS=D" when button press is detected

                    }
                } catch (IOException e) {
                    connectionLost();
                    // Start the service over to restart listening mode
                    SPPBluetoothService.this.start(/*isAndroid*/);
                    break;
                }
            }
        }

        // Write to the connected OutStream.
        // @param buffer  The bytes to write
        public void write(byte[] buffer) {
            try {/*
                byte[] buffer2 = new byte[buffer.length + 2];
                for(int i = 0 ; i < buffer.length ; i++)
                    buffer2[i] = buffer[i];
                buffer2[buffer2.length - 2] = 0x0A;
                buffer2[buffer2.length - 1] = 0x0D;*/
                mmOutStream.write(buffer);
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(SPPBluetoothState.MESSAGE_WRITE
                        , -1, -1, buffer).sendToTarget();
            } catch (IOException e) { }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}