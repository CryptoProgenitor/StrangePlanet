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
import com.quokkalabs.strangeplanet.data.model.PacDir
import com.quokkalabs.strangeplanet.data.model.PacGameState
import com.quokkalabs.strangeplanet.data.model.SeekerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Local two-being Bluetooth transport for Sustenance Pursuit.
 *
 * Mirrors [BluetoothPongManager]. The host runs the authoritative maze and
 * sends a one-off [MSG_INIT] (static walls/stars/socks + level header) at every
 * level/respawn, then a compact [MSG_TICK] per broadcast carrying only the
 * dynamic entities and the *deltas* of consumed stars/socks. The client returns
 * its steered-seeker direction ([MSG_DIR]), lobby seeker pick ([MSG_PICK]) and
 * control commands ([MSG_CONTROL]). Keeping the maze off the per-tick wire is
 * what makes 30 Hz comfortable over RFCOMM.
 */
class BluetoothPacManager(private val context: Context) {

    companion object {
        private const val TAG = "BtPac"
        private val PAC_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f23456789012")
        private const val SERVICE_NAME = "StrangePlanetPac"

        const val MSG_INIT: Byte = 1
        const val MSG_TICK: Byte = 2
        const val MSG_DIR: Byte = 3
        const val MSG_CONTROL: Byte = 4
        const val MSG_PICK: Byte = 5

        const val CTRL_START: Byte = 1
        const val CTRL_QUIT: Byte = 2
    }

    /** Static maze + level header. Resent at every level start / respawn. */
    data class NetInit(
        val cols: Int,
        val rows: Int,
        val tileSize: Float,
        val originX: Float,
        val originY: Float,
        val screenWidth: Float,
        val screenHeight: Float,
        val level: Int,
        val lives: Int,
        val score: Int,
        val highScore: Int,
        val controlledSeekerOrdinal: Int,
        val walls: Set<Int>,
        val pellets: Set<Int>,
        val socks: Set<Int>,
        val beingCol: Int,
        val beingRow: Int,
        val seekers: List<SeekerWire>,
        val phaseOrdinal: Int,
    )

    data class SeekerWire(
        val typeOrdinal: Int,
        val col: Int,
        val row: Int,
        val progress: Float,
        val dirOrdinal: Int,
        val modeOrdinal: Int,
        val penTimer: Int,
    )

    /** Per-broadcast dynamic snapshot + consumed-tile deltas. */
    data class NetTick(
        val beingCol: Int,
        val beingRow: Int,
        val beingProgress: Float,
        val beingDirOrdinal: Int,
        val seekers: List<SeekerWire>,
        val score: Int,
        val lives: Int,
        val level: Int,
        val phaseOrdinal: Int,
        val frightenedTick: Int,
        val eatenPellets: List<Int>,
        val eatenSocks: List<Int>,
        val saying: String?,
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

    /** Host: latest direction the adversary being is holding for its seeker. */
    private val _remoteSeekerDir = MutableStateFlow<PacDir?>(null)
    val remoteSeekerDir: StateFlow<PacDir?> = _remoteSeekerDir.asStateFlow()

    /** Host: the seeker the adversary chose in the lobby. */
    private val _remoteSeekerPick = MutableStateFlow<SeekerType?>(null)
    val remoteSeekerPick: StateFlow<SeekerType?> = _remoteSeekerPick.asStateFlow()

    /** Host: control commands from the client (quit). */
    private val _remoteControl = MutableStateFlow<Byte?>(null)
    val remoteControl: StateFlow<Byte?> = _remoteControl.asStateFlow()

    /** Client: latest static maze/level header from the host. */
    private val _remoteInit = MutableStateFlow<NetInit?>(null)
    val remoteInit: StateFlow<NetInit?> = _remoteInit.asStateFlow()

    /** Client: latest dynamic snapshot from the host. */
    private val _remoteTick = MutableStateFlow<NetTick?>(null)
    val remoteTick: StateFlow<NetTick?> = _remoteTick.asStateFlow()

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
                    adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, PAC_UUID)
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
                val s = device.createRfcommSocketToServiceRecord(PAC_UUID)
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

    // ---- data send (host) ----

    fun sendInit(state: PacGameState) {
        val out = dataOut ?: return
        try {
            synchronized(out) {
                out.writeByte(MSG_INIT.toInt())
                out.writeInt(state.cols)
                out.writeInt(state.rows)
                out.writeFloat(state.tileSize)
                out.writeFloat(state.originX)
                out.writeFloat(state.originY)
                out.writeFloat(state.screenWidth)
                out.writeFloat(state.screenHeight)
                out.writeInt(state.level)
                out.writeInt(state.lives)
                out.writeInt(state.score)
                out.writeInt(state.highScore)
                out.writeInt(state.controlledSeekerType?.ordinal ?: -1)
                writeInts(out, state.walls)
                writeInts(out, state.pellets)
                writeInts(out, state.socks)
                out.writeInt(state.being.col)
                out.writeInt(state.being.row)
                writeSeekers(out, state.seekers)
                out.writeInt(state.phase.ordinal)
                out.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send init failed", e)
            handleDisconnect()
        }
    }

    fun sendTick(
        state: PacGameState,
        eatenPellets: Collection<Int>,
        eatenSocks: Collection<Int>,
    ) {
        val out = dataOut ?: return
        try {
            synchronized(out) {
                out.writeByte(MSG_TICK.toInt())
                out.writeInt(state.being.col)
                out.writeInt(state.being.row)
                out.writeFloat(state.being.progress)
                out.writeInt(state.being.dir.ordinal)
                writeSeekers(out, state.seekers)
                out.writeInt(state.score)
                out.writeInt(state.lives)
                out.writeInt(state.level)
                out.writeInt(state.phase.ordinal)
                out.writeInt(state.frightenedTick)
                writeInts(out, eatenPellets)
                writeInts(out, eatenSocks)
                val saying = state.activeSaying
                out.writeByte(if (saying != null) 1 else 0)
                out.writeUTF(saying ?: "")
                out.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send tick failed", e)
            handleDisconnect()
        }
    }

    // ---- data send (client) ----

    fun sendDir(dir: PacDir) {
        val out = dataOut ?: return
        try {
            synchronized(out) {
                out.writeByte(MSG_DIR.toInt())
                out.writeByte(dir.ordinal)
                out.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send dir failed", e)
            handleDisconnect()
        }
    }

    fun sendPick(type: SeekerType) {
        val out = dataOut ?: return
        try {
            synchronized(out) {
                out.writeByte(MSG_PICK.toInt())
                out.writeByte(type.ordinal)
                out.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Send pick failed", e)
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
        _remoteSeekerDir.value = null
        _remoteSeekerPick.value = null
        _remoteControl.value = null
        _remoteInit.value = null
        _remoteTick.value = null
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

    private fun writeInts(out: DataOutputStream, values: Collection<Int>) {
        out.writeInt(values.size)
        values.forEach { out.writeInt(it) }
    }

    private fun readInts(input: DataInputStream): List<Int> {
        val n = input.readInt()
        return ArrayList<Int>(n).apply { repeat(n) { add(input.readInt()) } }
    }

    private fun writeSeekers(
        out: DataOutputStream,
        seekers: List<com.quokkalabs.strangeplanet.data.model.SeekerEntity>,
    ) {
        out.writeInt(seekers.size)
        seekers.forEach { s ->
            out.writeByte(s.type.ordinal)
            out.writeInt(s.col)
            out.writeInt(s.row)
            out.writeFloat(s.progress)
            out.writeByte(s.dir.ordinal)
            out.writeByte(s.mode.ordinal)
            out.writeInt(s.penTimer)
        }
    }

    private fun readSeekers(input: DataInputStream): List<SeekerWire> {
        val n = input.readInt()
        return ArrayList<SeekerWire>(n).apply {
            repeat(n) {
                add(
                    SeekerWire(
                        typeOrdinal = input.readByte().toInt(),
                        col = input.readInt(),
                        row = input.readInt(),
                        progress = input.readFloat(),
                        dirOrdinal = input.readByte().toInt(),
                        modeOrdinal = input.readByte().toInt(),
                        penTimer = input.readInt(),
                    ),
                )
            }
        }
    }

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
                        MSG_INIT -> {
                            val cols = input.readInt()
                            val rows = input.readInt()
                            val tileSize = input.readFloat()
                            val originX = input.readFloat()
                            val originY = input.readFloat()
                            val sw = input.readFloat()
                            val sh = input.readFloat()
                            val level = input.readInt()
                            val lives = input.readInt()
                            val score = input.readInt()
                            val highScore = input.readInt()
                            val ctrl = input.readInt()
                            val walls = readInts(input).toSet()
                            val pellets = readInts(input).toSet()
                            val socks = readInts(input).toSet()
                            val bc = input.readInt()
                            val br = input.readInt()
                            val seekers = readSeekers(input)
                            val phase = input.readInt()
                            _remoteInit.value = NetInit(
                                cols, rows, tileSize, originX, originY, sw, sh,
                                level, lives, score, highScore, ctrl,
                                walls, pellets, socks, bc, br, seekers, phase,
                            )
                        }

                        MSG_TICK -> {
                            val bc = input.readInt()
                            val br = input.readInt()
                            val bp = input.readFloat()
                            val bd = input.readInt()
                            val seekers = readSeekers(input)
                            val score = input.readInt()
                            val lives = input.readInt()
                            val level = input.readInt()
                            val phase = input.readInt()
                            val fright = input.readInt()
                            val eatenP = readInts(input)
                            val eatenS = readInts(input)
                            val hasSaying = input.readByte() != 0.toByte()
                            val sayingText = input.readUTF()
                            _remoteTick.value = NetTick(
                                bc, br, bp, bd, seekers, score, lives, level,
                                phase, fright, eatenP, eatenS,
                                if (hasSaying) sayingText else null,
                            )
                        }

                        MSG_DIR -> {
                            val ord = input.readByte().toInt()
                            _remoteSeekerDir.value =
                                PacDir.entries.getOrNull(ord) ?: PacDir.NONE
                        }

                        MSG_PICK -> {
                            val ord = input.readByte().toInt()
                            _remoteSeekerPick.value = SeekerType.entries.getOrNull(ord)
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
