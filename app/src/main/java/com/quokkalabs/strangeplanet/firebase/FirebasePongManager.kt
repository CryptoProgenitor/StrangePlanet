package com.quokkalabs.strangeplanet.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.quokkalabs.strangeplanet.bluetooth.BluetoothPongManager
import com.quokkalabs.strangeplanet.data.model.BtRole
import com.quokkalabs.strangeplanet.data.model.GameSide
import com.quokkalabs.strangeplanet.data.model.OnlineConnectionState
import com.quokkalabs.strangeplanet.data.model.PongGameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirebasePongManager {

    companion object {
        private const val TAG = "FbPong"
        private const val ROOMS_PATH = "rooms"
        private const val CODE_LENGTH = 4
        private const val SEND_INTERVAL_MS = 33L // ~30 Hz
    }

    private val database = FirebaseDatabase.getInstance()
    private var roomRef: DatabaseReference? = null
    private var stateListener: ValueEventListener? = null
    private var touchListener: ValueEventListener? = null
    private var controlListener: ValueEventListener? = null
    private var joinedListener: ValueEventListener? = null
    private var roomExistsListener: ValueEventListener? = null
    private var clientHitListener: ValueEventListener? = null

    // ---- Public state flows ----

    private val _connectionState = MutableStateFlow(OnlineConnectionState.IDLE)
    val connectionState: StateFlow<OnlineConnectionState> = _connectionState.asStateFlow()

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    private val _remoteTouchX = MutableStateFlow<Float?>(null)
    val remoteTouchX: StateFlow<Float?> = _remoteTouchX.asStateFlow()

    private val _remoteGameState =
        MutableStateFlow<BluetoothPongManager.NetGameState?>(null)
    val remoteGameState: StateFlow<BluetoothPongManager.NetGameState?> =
        _remoteGameState.asStateFlow()

    private val _remoteControl = MutableStateFlow<Byte?>(null)
    val remoteControl: StateFlow<Byte?> = _remoteControl.asStateFlow()

    /** Claimed hit event sent by the client to the host for authoritative adoption. */
    data class ClientHitEvent(
        val bx: Float,    // normalised ball x in host frame
        val by: Float,    // normalised ball y in host frame
        val vx: Float,    // normalised post-hit velocity x in host frame
        val vy: Float,    // normalised post-hit velocity y in host frame
        val rally: Int,   // rally count before this hit (for matching on host)
    )

    private val _remoteClientHit = MutableStateFlow<ClientHitEvent?>(null)
    val remoteClientHit: StateFlow<ClientHitEvent?> = _remoteClientHit.asStateFlow()

    private val _connectedPlayerName = MutableStateFlow<String?>(null)
    val connectedPlayerName: StateFlow<String?> = _connectedPlayerName.asStateFlow()

    var role: BtRole? = null
        private set

    val isConnected: Boolean
        get() = _connectionState.value == OnlineConnectionState.CONNECTED

    // ---- Internals ----

    private var lastSendTime = 0L

    // ---- Public API ----

    fun createRoom() {
        cleanup()
        role = BtRole.HOST
        _connectionState.value = OnlineConnectionState.CREATING

        val code = generateRoomCode()
        _roomCode.value = code

        val roomData = mapOf(
            "hostId" to System.currentTimeMillis().toString(),
            "clientJoined" to false,
            "createdAt" to ServerValue.TIMESTAMP,
        )

        val ref = database.getReference("$ROOMS_PATH/$code")
        roomRef = ref

        ref.setValue(roomData)
            .addOnSuccessListener {
                Log.d(TAG, "Room created: $code")
                _connectionState.value = OnlineConnectionState.WAITING_FOR_PLAYER
                // Auto-delete room when host disconnects
                ref.onDisconnect().removeValue()
                listenForClientJoin(ref)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create room", e)
                _connectionState.value = OnlineConnectionState.IDLE
                _roomCode.value = null
            }
    }

    fun joinRoom(code: String) {
        cleanup()
        role = BtRole.CLIENT
        _connectionState.value = OnlineConnectionState.JOINING
        val upperCode = code.uppercase().trim()
        _roomCode.value = upperCode

        val ref = database.getReference("$ROOMS_PATH/$upperCode")
        roomRef = ref

        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists() && snapshot.child("hostId").value != null) {
                val alreadyJoined =
                    snapshot.child("clientJoined").getValue(Boolean::class.java) ?: false
                if (alreadyJoined) {
                    Log.e(TAG, "Room already has a client")
                    _connectionState.value = OnlineConnectionState.IDLE
                    _roomCode.value = null
                    return@addOnSuccessListener
                }
                ref.child("clientJoined").setValue(true)
                // Auto-clear client flag on disconnect
                ref.child("clientJoined").onDisconnect().setValue(false)
                _connectionState.value = OnlineConnectionState.CONNECTED
                _connectedPlayerName.value = "Host Being"
                listenForGameState(ref)
                listenForControl(ref, "host")
                listenForRoomDeletion(ref)
            } else {
                Log.e(TAG, "Room not found: $upperCode")
                _connectionState.value = OnlineConnectionState.IDLE
                _roomCode.value = null
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to join room", e)
            _connectionState.value = OnlineConnectionState.IDLE
            _roomCode.value = null
        }
    }

    // ---- Data send (host) ----

    fun sendGameState(
        state: PongGameState,
        hostCreatureIdx: Int = 0,
        clientCreatureIdx: Int = 0,
    ) {
        val ref = roomRef ?: return
        val now = System.currentTimeMillis()
        if (now - lastSendTime < SEND_INTERVAL_MS) return
        lastSendTime = now

        val sw = state.screenWidth
        val sh = state.screenHeight
        if (sw <= 0f || sh <= 0f) return

        val saying = state.activeSaying
        val stateMap = mapOf(
            "bx" to (state.ballX / sw).toDouble(),
            "by" to (state.ballY / sh).toDouble(),
            "bvx" to (state.ballVx / sw).toDouble(),
            "bvy" to (state.ballVy / sh).toDouble(),
            "hpx" to (state.playerPaddleX / sw).toDouble(),
            "cpx" to (state.aiPaddleX / sw).toDouble(),
            "hs" to state.playerScore,
            "cs" to state.aiScore,
            "ph" to state.phase.ordinal,
            "hhp" to state.playerHitPulse.toDouble(),
            "chp" to state.aiHitPulse.toDouble(),
            "r" to state.rally,
            "ss" to if (saying != null) {
                if (saying.first == GameSide.PLAYER) 0 else 1
            } else {
                -1
            },
            "st" to (saying?.second ?: ""),
            "hci" to hostCreatureIdx,
            "cci" to clientCreatureIdx,
            "hsw" to sw.toDouble(),
            "hsh" to sh.toDouble(),
        )

        ref.child("state").setValue(stateMap)
    }

    // ---- Data send (client) ----

    fun sendTouch(normalizedX: Float?) {
        val ref = roomRef ?: return
        val touchMap = mapOf(
            "x" to (normalizedX?.toDouble() ?: 0.0),
            "a" to (normalizedX != null),
        )
        ref.child("clientTouch").setValue(touchMap)
    }

    /** Client → host: claim a local hit so the host can adopt it if it missed. */
    fun sendClientHit(bx: Float, by: Float, vx: Float, vy: Float, rally: Int) {
        val ref = roomRef ?: return
        ref.child("clientHit").setValue(
            mapOf(
                "bx" to bx.toDouble(),
                "by" to by.toDouble(),
                "vx" to vx.toDouble(),
                "vy" to vy.toDouble(),
                "r" to rally,
            ),
        )
    }

    fun clearClientHit() {
        _remoteClientHit.value = null
    }

    // ---- Control (both sides) ----

    fun sendControl(action: Byte) {
        val ref = roomRef ?: return
        val from = if (role == BtRole.HOST) "host" else "client"
        val controlMap = mapOf(
            "action" to action.toInt(),
            "from" to from,
            "ts" to ServerValue.TIMESTAMP,
        )
        ref.child("control").setValue(controlMap)
    }

    fun clearControl() {
        _remoteControl.value = null
    }

    // ---- Lifecycle ----

    fun cleanup() {
        removeAllListeners()

        // If host, delete the room
        if (role == BtRole.HOST) {
            roomRef?.removeValue()
        }

        roomRef = null
        role = null
        _connectionState.value = OnlineConnectionState.IDLE
        _roomCode.value = null
        _remoteTouchX.value = null
        _remoteGameState.value = null
        _remoteControl.value = null
        _remoteClientHit.value = null
        _connectedPlayerName.value = null
        lastSendTime = 0L
    }

    // ---- Private helpers ----

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ" // no I or O to avoid confusion
        return (1..CODE_LENGTH).map { chars.random() }.joinToString("")
    }

    private fun removeAllListeners() {
        stateListener?.let { roomRef?.child("state")?.removeEventListener(it) }
        touchListener?.let { roomRef?.child("clientTouch")?.removeEventListener(it) }
        controlListener?.let { roomRef?.child("control")?.removeEventListener(it) }
        joinedListener?.let { roomRef?.child("clientJoined")?.removeEventListener(it) }
        roomExistsListener?.let { roomRef?.removeEventListener(it) }
        clientHitListener?.let { roomRef?.child("clientHit")?.removeEventListener(it) }
        stateListener = null
        touchListener = null
        controlListener = null
        joinedListener = null
        roomExistsListener = null
        clientHitListener = null
    }

    /** Host: listen for a client joining the room. */
    private fun listenForClientJoin(ref: DatabaseReference) {
        joinedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val joined = snapshot.getValue(Boolean::class.java) ?: false
                if (joined && _connectionState.value == OnlineConnectionState.WAITING_FOR_PLAYER) {
                    _connectionState.value = OnlineConnectionState.CONNECTED
                    _connectedPlayerName.value = "Distant Being"
                    listenForClientTouch(ref)
                    listenForControl(ref, "client")
                    listenForClientHit(ref)
                } else if (!joined && _connectionState.value == OnlineConnectionState.CONNECTED) {
                    // Client disconnected
                    _connectionState.value = OnlineConnectionState.WAITING_FOR_PLAYER
                    _connectedPlayerName.value = null
                    _remoteTouchX.value = null
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Join listener cancelled", error.toException())
            }
        }
        ref.child("clientJoined").addValueEventListener(joinedListener!!)
    }

    /** Host: listen for client touch input. */
    private fun listenForClientTouch(ref: DatabaseReference) {
        if (touchListener != null) return // already listening
        touchListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val active = snapshot.child("a").getValue(Boolean::class.java) ?: false
                val x = snapshot.child("x").getValue(Double::class.java)?.toFloat()
                _remoteTouchX.value = if (active && x != null) x else null
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Touch listener cancelled", error.toException())
            }
        }
        ref.child("clientTouch").addValueEventListener(touchListener!!)
    }

    /** Client: listen for game state from host. */
    private fun listenForGameState(ref: DatabaseReference) {
        stateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                try {
                    _remoteGameState.value = BluetoothPongManager.NetGameState(
                        ballX = snapshot.child("bx").getValue(Double::class.java)
                            ?.toFloat() ?: 0f,
                        ballY = snapshot.child("by").getValue(Double::class.java)
                            ?.toFloat() ?: 0f,
                        ballVx = snapshot.child("bvx").getValue(Double::class.java)
                            ?.toFloat() ?: 0f,
                        ballVy = snapshot.child("bvy").getValue(Double::class.java)
                            ?.toFloat() ?: 0f,
                        hostPaddleX = snapshot.child("hpx").getValue(Double::class.java)
                            ?.toFloat() ?: 0f,
                        clientPaddleX = snapshot.child("cpx").getValue(Double::class.java)
                            ?.toFloat() ?: 0f,
                        hostScore = snapshot.child("hs").getValue(Long::class.java)
                            ?.toInt() ?: 0,
                        clientScore = snapshot.child("cs").getValue(Long::class.java)
                            ?.toInt() ?: 0,
                        phaseOrdinal = snapshot.child("ph").getValue(Long::class.java)
                            ?.toInt() ?: 0,
                        hostHitPulse = snapshot.child("hhp").getValue(Double::class.java)
                            ?.toFloat() ?: 0f,
                        clientHitPulse = snapshot.child("chp").getValue(Double::class.java)
                            ?.toFloat() ?: 0f,
                        rally = snapshot.child("r").getValue(Long::class.java)
                            ?.toInt() ?: 0,
                        sayingSide = snapshot.child("ss").getValue(Long::class.java)
                            ?.toInt() ?: -1,
                        sayingText = snapshot.child("st").getValue(String::class.java)
                            ?: "",
                        hostCreatureIdx = snapshot.child("hci").getValue(Long::class.java)
                            ?.toInt() ?: 0,
                        clientCreatureIdx = snapshot.child("cci").getValue(Long::class.java)
                            ?.toInt() ?: 0,
                        hostScreenWidth = snapshot.child("hsw").getValue(Double::class.java)
                            ?.toFloat() ?: 0f,
                        hostScreenHeight = snapshot.child("hsh").getValue(Double::class.java)
                            ?.toFloat() ?: 0f,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse game state", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "State listener cancelled", error.toException())
            }
        }
        ref.child("state").addValueEventListener(stateListener!!)
    }

    /** Listen for control messages from the other side. */
    private fun listenForControl(ref: DatabaseReference, fromOther: String) {
        if (controlListener != null) return
        controlListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val from =
                    snapshot.child("from").getValue(String::class.java) ?: return
                if (from == fromOther) {
                    val action =
                        snapshot.child("action").getValue(Long::class.java)?.toInt()
                            ?: return
                    _remoteControl.value = action.toByte()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Control listener cancelled", error.toException())
            }
        }
        ref.child("control").addValueEventListener(controlListener!!)
    }

    /** Host: receive hit claims from the client for ghost-paddle elimination. */
    private fun listenForClientHit(ref: DatabaseReference) {
        if (clientHitListener != null) return
        clientHitListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                try {
                    _remoteClientHit.value = ClientHitEvent(
                        bx = snapshot.child("bx").getValue(Double::class.java)?.toFloat() ?: return,
                        by = snapshot.child("by").getValue(Double::class.java)?.toFloat() ?: return,
                        vx = snapshot.child("vx").getValue(Double::class.java)?.toFloat() ?: return,
                        vy = snapshot.child("vy").getValue(Double::class.java)?.toFloat() ?: return,
                        rally = snapshot.child("r").getValue(Long::class.java)?.toInt() ?: return,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse client hit", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Client hit listener cancelled", error.toException())
            }
        }
        ref.child("clientHit").addValueEventListener(clientHitListener!!)
    }

    /** Client: detect if the host deleted the room (disconnected). */
    private fun listenForRoomDeletion(ref: DatabaseReference) {
        roomExistsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() &&
                    _connectionState.value == OnlineConnectionState.CONNECTED
                ) {
                    Log.d(TAG, "Room deleted by host")
                    _connectionState.value = OnlineConnectionState.IDLE
                    _connectedPlayerName.value = null
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Room exists listener cancelled", error.toException())
            }
        }
        ref.child("hostId").addValueEventListener(roomExistsListener!!)
    }
}
