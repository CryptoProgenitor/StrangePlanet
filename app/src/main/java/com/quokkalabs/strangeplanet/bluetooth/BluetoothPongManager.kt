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
import com.quokkalabs.strangeplanet.data.model.GamePhase
import com.quokkalabs.strangeplanet.data.model.GameSide
import com.quokkalabs.strangeplanet.data.model.PongGameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

class BluetoothPongManager(private val context: Context) {

    companion object {
        private const val TAG = "BtPong"
        private val PONG_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        private const val SERVICE_NAME = "StrangePlanetPong"

        const val MSG_STATE: Byte = 1
        const val MSG_TOUCH: Byte = 2
        const val MSG_CONTROL: Byte = 3
        const val CTRL_TAP_START: Byte = 1
        const val CTRL_QUIT: Byte = 2
        const val CTRL_PAUSE: Byte = 3
    }

    /** Normalized game state sent over the wire. */
    data class NetGameState(
        val ballX: Float,
        val ballY: Float,
        val hostPaddleX: Float,
        val clientPaddleX: Float,
        val hostScore: Int,
        val clientScore: Int,
        val phaseOrdinal: Int,
        val hostHitPulse: Float,
        val clientHitPulse: Float,
        val rally: Int,
        val sayingSide: Int,   // -1 = none, 0 = host scored, 1 = client scored
        val sayingText: String,
        val hostCreatureIdx: Int = 0,
        val clientCreatureIdx: Int = 0,
        val ballVx: Float = 0f,  // normalised by screenWidth  (online dead-reckoning)
        val ballVy: Float = 0f,  // normalised by screenHeight (online dead-reckoning)
        val hostScreenWidth: Float = 0f,   // host physical px — client uses these to reconstruct velocity
        val hostScreenHeight: Float = 0f,  // so mismatched screen sizes don't skew dead-reckoning
    )

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

    /** For host: latest normalized touch-X from the remote client. */
    private val _remoteTouchX = MutableStateFlow<Float?>(null)
    val remoteTouchX: StateFlow<Float?> = _remoteTouchX.asStateFlow()

    /** For client: latest game state snapshot from the host. */
    private val _remoteGameState = MutableStateFlow<NetGameState?>(null)
    val remoteGameState: StateFlow<NetGameState?> = _remoteGameState.asStateFlow()

    /** For host: control commands from client (e.g. tap-to-start). */
    private val _remoteControl = MutableStateFlow<Byte?>(null)
    val remoteControl: StateFlow<Byte?> = _remoteControl.asStateFlow()

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
                    adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, PONG_UUID)
                Log.d(TAG, "Hosting, waiting for connection...")
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
                val s = device.createRfcommSocketToServiceRecord(PONG_UUID)
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

    fun sendGameState(state: PongGameState, hostCreatureIdx: Int = 0, clientCreatureIdx: Int = 0) {
        val out = dataOut ?: return
        val sw = state.screenWidth
        val sh = state.screenHeight
        if (sw <= 0f || sh <= 0f) return

        try {
            synchronized(out) {
                out.writeByte(MSG_STATE.toInt())
                out.writeFloat(state.ballX / sw)
                out.writeFloat(state.ballY / sh)
                out.writeFloat(state.playerPaddleX / sw) // host paddle
                out.writeFloat(state.aiPaddleX / sw)     // client paddle
                out.writeInt(state.playerScore)           // host score
                out.writeInt(state.aiScore)               // client score
                out.writeInt(state.phase.ordinal)
                out.writeFloat(state.playerHitPulse)
                out.writeFloat(state.aiHitPulse)
                out.writeInt(state.rally)
                val saying = state.activeSaying
                if (saying != null) {
                    out.writeInt(if (saying.first == GameSide.PLAYER) 0 else 1)
                    out.writeUTF(saying.second)
                } else {
                    out.writeInt(-1)
                    out.writeUTF("")
                }
                out.writeByte(hostCreatureIdx)
                out.writeByte(clientCreatureIdx)
                out.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send state failed", e)
            handleDisconnect()
        }
    }

    fun sendTouch(normalizedX: Float?) {
        val out = dataOut ?: return
        try {
            synchronized(out) {
                out.writeByte(MSG_TOUCH.toInt())
                out.writeByte(if (normalizedX != null) 1 else 0)
                out.writeFloat(normalizedX ?: 0f)
                out.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send touch failed", e)
            handleDisconnect()
        }
    }

    fun sendControl(action: Byte) {
        val out = dataOut ?: return
        try {
            synchronized(out) {
                out.writeByte(MSG_CONTROL.toInt())
                out.writeByte(action.toInt())
                out.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send control failed", e)
            handleDisconnect()
        }
    }

    fun clearControl() {
        _remoteControl.value = null
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
        _remoteTouchX.value = null
        _remoteGameState.value = null
        _remoteControl.value = null
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
                        MSG_STATE -> {
                            _remoteGameState.value = NetGameState(
                                ballX = input.readFloat(),
                                ballY = input.readFloat(),
                                hostPaddleX = input.readFloat(),
                                clientPaddleX = input.readFloat(),
                                hostScore = input.readInt(),
                                clientScore = input.readInt(),
                                phaseOrdinal = input.readInt(),
                                hostHitPulse = input.readFloat(),
                                clientHitPulse = input.readFloat(),
                                rally = input.readInt(),
                                sayingSide = input.readInt(),
                                sayingText = input.readUTF(),
                                hostCreatureIdx = input.readByte().toInt(),
                                clientCreatureIdx = input.readByte().toInt(),
                            )
                        }

                        MSG_TOUCH -> {
                            val hasTouch = input.readByte() != 0.toByte()
                            val x = input.readFloat()
                            _remoteTouchX.value = if (hasTouch) x else null
                        }

                        MSG_CONTROL -> {
                            _remoteControl.value = input.readByte()
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Read loop ended", e)
                if (running) _connectionState.value = BtConnectionState.IDLE
            }
        }.apply { isDaemon = true; start() }
    }

    private fun handleDisconnect() {
        running = false
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
