package com.quokkalabs.strangeplanet.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.quokkalabs.strangeplanet.data.model.BtConnectionState
import com.quokkalabs.strangeplanet.data.model.BtDeviceInfo
import com.quokkalabs.strangeplanet.data.model.BtRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 1v1 Bluetooth for the competitive merge race. Unlike Pong/Pac there is NO
 * shared simulation — each device runs its own independent board. Only three
 * things cross the wire: the host's match duration, a periodic score+done
 * heartbeat, and an explicit quit.
 */
class BluetoothMergeManager(private val context: Context) {

    companion object {
        private const val TAG = "BtMerge"
        private val MERGE_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f23456789abc")
        private const val SERVICE_NAME = "StrangePlanetMerge"

        const val MSG_START: Byte = 1   // host -> client: Int durationSeconds
        const val MSG_SCORE: Byte = 2   // either: Int score, Byte done(0/1)
        const val MSG_QUIT: Byte = 3
    }

    /** Latest score heartbeat from the peer. */
    data class PeerScore(val score: Int, val done: Boolean)

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // ---- public state flows ----

    private val _connectionState = MutableStateFlow(BtConnectionState.IDLE)
    val connectionState: StateFlow<BtConnectionState> = _connectionState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BtDeviceInfo>>(emptyList())
    val pairedDevices: StateFlow<List<BtDeviceInfo>> = _pairedDevices.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BtDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BtDeviceInfo>> = _discoveredDevices.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    /** Client: the duration the host chose (seconds), once the match begins. */
    private val _remoteStart = MutableStateFlow<Int?>(null)
    val remoteStart: StateFlow<Int?> = _remoteStart.asStateFlow()

    /** Latest peer score/done heartbeat. */
    private val _remoteScore = MutableStateFlow<PeerScore?>(null)
    val remoteScore: StateFlow<PeerScore?> = _remoteScore.asStateFlow()

    /** Peer asked to abandon the match / left. */
    private val _remoteQuit = MutableStateFlow(false)
    val remoteQuit: StateFlow<Boolean> = _remoteQuit.asStateFlow()

    var role: BtRole? = null
        private set

    // ---- internals ----

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var dataOut: DataOutputStream? = null
    private var dataIn: DataInputStream? = null

    @Volatile
    private var running = false
    private var receiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { d ->
                        val name = try {
                            d.name
                        } catch (_: SecurityException) {
                            null
                        } ?: "Unknown Being"
                        val info = BtDeviceInfo(name, d.address)
                        val cur = _discoveredDevices.value
                        if (cur.none { it.address == info.address }) {
                            _discoveredDevices.value = cur + info
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (_connectionState.value == BtConnectionState.SCANNING) {
                        _connectionState.value = BtConnectionState.IDLE
                    }
                }
            }
        }
    }

    // ---- queries ----

    val isAvailable: Boolean get() = bluetoothAdapter != null
    val isEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    // ---- public API ----

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        _pairedDevices.value = try {
            bluetoothAdapter?.bondedDevices
                ?.map { BtDeviceInfo(it.name ?: "Unknown", it.address) }
                ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startHosting() {
        val adapter = bluetoothAdapter ?: return
        stopAll()
        role = BtRole.HOST
        _connectionState.value = BtConnectionState.HOSTING
        running = true

        Thread {
            try {
                serverSocket =
                    adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, MERGE_UUID)
                val s = serverSocket?.accept()
                if (s != null && running) {
                    socket = s
                    try {
                        serverSocket?.close()
                    } catch (_: IOException) {
                    }
                    serverSocket = null
                    _connectedDeviceName.value = try {
                        s.remoteDevice?.name
                    } catch (_: SecurityException) {
                        null
                    } ?: "Unknown Being"
                    _connectedDeviceAddress.value = try {
                        s.remoteDevice?.address
                    } catch (_: SecurityException) {
                        null
                    }
                    setupStreams(s)
                    _connectionState.value = BtConnectionState.CONNECTED
                    startReadLoop()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Accept failed", e)
                if (running) _connectionState.value = BtConnectionState.IDLE
            }
        }.apply { isDaemon = true; start() }
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        val adapter = bluetoothAdapter ?: return
        _discoveredDevices.value = emptyList()
        _connectionState.value = BtConnectionState.SCANNING

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)
        receiverRegistered = true
        adapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (_: SecurityException) {
        }
        unregisterReceiver()
        if (_connectionState.value == BtConnectionState.SCANNING) {
            _connectionState.value = BtConnectionState.IDLE
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String) {
        val adapter = bluetoothAdapter ?: return
        try {
            adapter.cancelDiscovery()
        } catch (_: SecurityException) {
        }
        unregisterReceiver()
        stopAll()
        role = BtRole.CLIENT
        _connectionState.value = BtConnectionState.CONNECTING
        running = true

        Thread {
            try {
                val device = adapter.getRemoteDevice(address)
                val s = device.createRfcommSocketToServiceRecord(MERGE_UUID)
                s.connect()
                if (running) {
                    socket = s
                    _connectedDeviceName.value = try {
                        device.name
                    } catch (_: SecurityException) {
                        null
                    } ?: "Unknown Being"
                    _connectedDeviceAddress.value = address
                    setupStreams(s)
                    _connectionState.value = BtConnectionState.CONNECTED
                    startReadLoop()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Connect failed", e)
                if (running) _connectionState.value = BtConnectionState.IDLE
            }
        }.apply { isDaemon = true; start() }
    }

    // ---- data send ----

    fun sendStart(durationSeconds: Int) {
        val out = dataOut ?: return
        try {
            synchronized(out) {
                out.writeByte(MSG_START.toInt())
                out.writeInt(durationSeconds)
                out.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send start failed", e)
            handleDisconnect()
        }
    }

    fun sendScore(score: Int, done: Boolean) {
        val out = dataOut ?: return
        try {
            synchronized(out) {
                out.writeByte(MSG_SCORE.toInt())
                out.writeInt(score)
                out.writeByte(if (done) 1 else 0)
                out.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send score failed", e)
            handleDisconnect()
        }
    }

    fun sendQuit() {
        val out = dataOut ?: return
        try {
            synchronized(out) {
                out.writeByte(MSG_QUIT.toInt())
                out.flush()
            }
        } catch (_: IOException) {
        }
    }

    fun clearRemoteStart() {
        _remoteStart.value = null
    }

    fun clearRemoteQuit() {
        _remoteQuit.value = false
    }

    // ---- lifecycle ----

    fun stopAll() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
        socket = null
        dataOut = null
        dataIn = null
        _remoteStart.value = null
        _remoteScore.value = null
        _remoteQuit.value = false
        _connectedDeviceName.value = null
        _connectedDeviceAddress.value = null
    }

    fun cleanup() {
        unregisterReceiver()
        stopAll()
        role = null
        _connectionState.value = BtConnectionState.IDLE
    }

    // ---- private helpers ----

    private fun setupStreams(s: BluetoothSocket) {
        dataOut = DataOutputStream(s.outputStream.buffered())
        dataIn = DataInputStream(s.inputStream.buffered())
    }

    private fun startReadLoop() {
        Thread {
            val input = dataIn ?: return@Thread
            try {
                while (running) {
                    when (input.readByte()) {
                        MSG_START -> {
                            _remoteStart.value = input.readInt()
                        }

                        MSG_SCORE -> {
                            val score = input.readInt()
                            val done = input.readByte() != 0.toByte()
                            _remoteScore.value = PeerScore(score, done)
                        }

                        MSG_QUIT -> {
                            _remoteQuit.value = true
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Read loop ended", e)
                if (running) {
                    _remoteQuit.value = true
                    _connectionState.value = BtConnectionState.IDLE
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun handleDisconnect() {
        running = false
        _remoteQuit.value = true
        _connectionState.value = BtConnectionState.IDLE
    }

    private fun unregisterReceiver() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }
}
