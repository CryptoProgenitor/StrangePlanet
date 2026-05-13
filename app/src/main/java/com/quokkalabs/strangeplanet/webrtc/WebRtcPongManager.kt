package com.quokkalabs.strangeplanet.webrtc

import android.content.Context
import android.util.Log
import com.quokkalabs.strangeplanet.bluetooth.BluetoothPongManager.NetGameState
import com.quokkalabs.strangeplanet.firebase.FirebasePongManager.ClientHitEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * P2P game transport over WebRTC DataChannel.
 *
 * Two channels are created on the same PeerConnection:
 *   - "state"  (label) : unreliable + unordered  — 30 Hz game state from host
 *   - "ctrl"   (label) : reliable   + ordered    — hit events, control msgs, touch
 *
 * Signaling (SDP offer/answer + ICE candidates) is delegated back to the caller
 * via [SignalingCallback] and expected to be relayed through Firebase.
 *
 * Packet format (binary, little-endian):
 *   State packet  (type=0x01): see [encodeState] / [decodeState]
 *   Touch packet  (type=0x10): 1 byte type + 1 byte active flag + 4 bytes float x
 *   Hit packet    (type=0x20): 1 byte type + 4×float (bx,by,vx,vy) + 4 bytes int rally
 *   Control packet(type=0x30): 1 byte type + 1 byte action
 */
class WebRtcPongManager(private val context: Context) {

    companion object {
        private const val TAG = "WRtcPong"

        // Packet type tags
        const val PKT_STATE: Byte = 0x01
        const val PKT_TOUCH: Byte = 0x10
        const val PKT_HIT: Byte = 0x20
        const val PKT_CTRL: Byte = 0x30

        // State packet field count: type(1) + bx,by,bvx,bvy,hpx,cpx(6) +
        //   hs,cs,ph,hhp,chp,r,ss(7) + st_len(1,up to 32 chars) + hci,cci(2) +
        //   hsw,hsh(2) + ts(1 long=8bytes)
        // We encode st as a fixed 32-byte UTF-8 region (padded/truncated).
        // Total binary size = 1 + 6*4 + 4 + 4 + 4 + 4*4 + 4 + 4 + 32 + 4 + 4 + 4 + 4 + 8 = 113 bytes
        // Layout (all little-endian unless noted):
        //   [0]       type   = 0x01
        //   [1..4]    bx     float
        //   [5..8]    by     float
        //   [9..12]   bvx    float
        //   [13..16]  bvy    float
        //   [17..20]  hpx    float
        //   [21..24]  cpx    float
        //   [25..28]  hs     int
        //   [29..32]  cs     int
        //   [33..36]  ph     int
        //   [37..40]  hhp    float
        //   [41..44]  chp    float
        //   [45..48]  r      int
        //   [49..52]  ss     int
        //   [53..84]  st     32 bytes (UTF-8, zero-padded)
        //   [85..88]  hci    int
        //   [89..92]  cci    int
        //   [93..96]  hsw    float
        //   [97..100] hsh    float
        //   [101..108] ts    long
        private const val STATE_PACKET_SIZE = 109
        private const val ST_REGION_SIZE = 32

        private val STUN_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
    }

    interface SignalingCallback {
        fun onLocalSdp(sdp: SessionDescription)
        fun onLocalIceCandidate(candidate: IceCandidate)
        fun onDataChannelOpen()
        fun onDataChannelClose()
    }

    enum class RtcState { IDLE, CONNECTING, OPEN, CLOSED }

    // ---- Outbound flows (consumed by ViewModel) ----
    private val _remoteGameState = MutableStateFlow<NetGameState?>(null)
    val remoteGameState: StateFlow<NetGameState?> = _remoteGameState.asStateFlow()

    private val _remoteTouchX = MutableStateFlow<Float?>(null)
    val remoteTouchX: StateFlow<Float?> = _remoteTouchX.asStateFlow()

    private val _remoteClientHit = MutableStateFlow<ClientHitEvent?>(null)
    val remoteClientHit: StateFlow<ClientHitEvent?> = _remoteClientHit.asStateFlow()

    private val _remoteControl = MutableStateFlow<Byte?>(null)
    val remoteControl: StateFlow<Byte?> = _remoteControl.asStateFlow()

    private val _rtcState = MutableStateFlow(RtcState.IDLE)
    val rtcState: StateFlow<RtcState> = _rtcState.asStateFlow()

    val isOpen: Boolean get() = _rtcState.value == RtcState.OPEN

    // ---- Internal ----
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var stateChannel: DataChannel? = null   // unreliable, host→client game state
    private var ctrlChannel: DataChannel? = null    // reliable, both directions

    private var signalingCallback: SignalingCallback? = null
    private var isHost = false

    // ---- Init / teardown ----

    fun init(signalingCallback: SignalingCallback) {
        this.signalingCallback = signalingCallback
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    fun cleanup() {
        stateChannel?.close()
        ctrlChannel?.close()
        peerConnection?.close()
        stateChannel = null
        ctrlChannel = null
        peerConnection = null
        factory?.dispose()
        factory = null
        signalingCallback = null
        _rtcState.value = RtcState.CLOSED
        _remoteGameState.value = null
        _remoteTouchX.value = null
        _remoteClientHit.value = null
        _remoteControl.value = null
    }

    // ---- Offer/Answer ----

    /** Host creates the PeerConnection + DataChannels, then generates an offer. */
    fun createOffer() {
        isHost = true
        val pc = buildPeerConnection() ?: return

        val stInit = DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0  // unreliable
            negotiated = true
            id = 0
        }
        val ctInit = DataChannel.Init().apply {
            ordered = true
            negotiated = true
            id = 1
        }
        stateChannel = pc.createDataChannel("state", stInit)
        ctrlChannel = pc.createDataChannel("ctrl", ctInit)

        stateChannel?.registerObserver(makeChannelObserver("state"))
        ctrlChannel?.registerObserver(makeChannelObserver("ctrl"))

        pc.createOffer(makeSdpObserver { sdp ->
            pc.setLocalDescription(makeSdpObserver { }, sdp)
            signalingCallback?.onLocalSdp(sdp)
        }, MediaConstraints())
    }

    /** Client receives the offer and generates an answer. */
    fun receiveOffer(sdp: SessionDescription) {
        isHost = false
        val pc = buildPeerConnection() ?: return

        // Client opens the negotiated channels by the same IDs
        val stInit = DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0
            negotiated = true
            id = 0
        }
        val ctInit = DataChannel.Init().apply {
            ordered = true
            negotiated = true
            id = 1
        }
        stateChannel = pc.createDataChannel("state", stInit)
        ctrlChannel = pc.createDataChannel("ctrl", ctInit)

        stateChannel?.registerObserver(makeChannelObserver("state"))
        ctrlChannel?.registerObserver(makeChannelObserver("ctrl"))

        pc.setRemoteDescription(makeSdpObserver { }, sdp)
        pc.createAnswer(makeSdpObserver { answer ->
            pc.setLocalDescription(makeSdpObserver { }, answer)
            signalingCallback?.onLocalSdp(answer)
        }, MediaConstraints())
    }

    /** Host receives the client's answer. */
    fun receiveAnswer(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(makeSdpObserver { }, sdp)
    }

    /** Both sides add remote ICE candidates as they arrive from signaling. */
    fun addRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    // ---- Send (host) ----

    fun sendGameState(state: NetGameState, wallTimeMs: Long) {
        if (!isOpen) return
        val ch = stateChannel ?: return
        val buf = encodeState(state, wallTimeMs)
        ch.send(DataChannel.Buffer(buf, true))
    }

    // ---- Send (client) ----

    fun sendTouch(normalizedX: Float?) {
        if (!isOpen) return
        val ch = ctrlChannel ?: return
        val buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(PKT_TOUCH)
        buf.put(if (normalizedX != null) 1.toByte() else 0.toByte())
        buf.putFloat(normalizedX ?: 0f)
        buf.flip()
        ch.send(DataChannel.Buffer(buf, true))
    }

    fun sendClientHit(bx: Float, by: Float, vx: Float, vy: Float, rally: Int) {
        if (!isOpen) return
        val ch = ctrlChannel ?: return
        val buf = ByteBuffer.allocate(21).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(PKT_HIT)
        buf.putFloat(bx); buf.putFloat(by)
        buf.putFloat(vx); buf.putFloat(vy)
        buf.putInt(rally)
        buf.flip()
        ch.send(DataChannel.Buffer(buf, true))
    }

    // ---- Send (both) ----

    fun sendControl(action: Byte) {
        if (!isOpen) return
        val ch = ctrlChannel ?: return
        val buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(PKT_CTRL)
        buf.put(action)
        buf.flip()
        ch.send(DataChannel.Buffer(buf, true))
    }

    fun clearClientHit() {
        _remoteClientHit.value = null
    }

    fun clearControl() {
        _remoteControl.value = null
    }

    // ---- Private helpers ----

    private fun buildPeerConnection(): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(STUN_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        _rtcState.value = RtcState.CONNECTING
        val pc = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE: $s")
                when (s) {
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        _rtcState.value = RtcState.CLOSED
                        signalingCallback?.onDataChannelClose()
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(c: IceCandidate?) {
                c?.let { signalingCallback?.onLocalIceCandidate(it) }
            }
            override fun onIceCandidatesRemoved(cs: Array<out IceCandidate>?) {}
            override fun onAddStream(s: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(s: org.webrtc.MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
        })
        peerConnection = pc
        return pc
    }

    private fun makeChannelObserver(name: String) = object : DataChannel.Observer {
        override fun onBufferedAmountChange(amount: Long) {}
        override fun onStateChange() {
            val state = if (name == "ctrl") ctrlChannel?.state() else stateChannel?.state()
            Log.d(TAG, "DataChannel[$name] state: $state")
            // Both channels must be open before we signal ready
            val stOk = stateChannel?.state() == DataChannel.State.OPEN
            val ctOk = ctrlChannel?.state() == DataChannel.State.OPEN
            if (stOk && ctOk && _rtcState.value != RtcState.OPEN) {
                _rtcState.value = RtcState.OPEN
                signalingCallback?.onDataChannelOpen()
            }
        }
        override fun onMessage(buf: DataChannel.Buffer?) {
            buf ?: return
            val data = buf.data.order(ByteOrder.LITTLE_ENDIAN)
            if (!data.hasRemaining()) return
            when (data.get()) {
                PKT_STATE -> _remoteGameState.value = decodeState(data)
                PKT_TOUCH -> {
                    val active = data.get() == 1.toByte()
                    val x = data.float
                    _remoteTouchX.value = if (active) x else null
                }
                PKT_HIT -> {
                    _remoteClientHit.value = ClientHitEvent(
                        bx = data.float, by = data.float,
                        vx = data.float, vy = data.float,
                        rally = data.int,
                    )
                }
                PKT_CTRL -> _remoteControl.value = data.get()
                else -> Log.w(TAG, "Unknown packet type")
            }
        }
    }

    private fun makeSdpObserver(onSuccess: (SessionDescription) -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) { sdp?.let(onSuccess) }
        override fun onSetSuccess() {}
        override fun onCreateFailure(s: String?) { Log.e(TAG, "SDP create failure: $s") }
        override fun onSetFailure(s: String?) { Log.e(TAG, "SDP set failure: $s") }
    }

    private fun encodeState(s: NetGameState, wallTimeMs: Long): ByteBuffer {
        val stBytes = s.sayingText.toByteArray(Charsets.UTF_8).copyOf(ST_REGION_SIZE)
        val size = 1 + 6 * 4 + 7 * 4 + ST_REGION_SIZE + 2 * 4 + 2 * 4 + 8
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(PKT_STATE)
        buf.putFloat(s.ballX); buf.putFloat(s.ballY)
        buf.putFloat(s.ballVx); buf.putFloat(s.ballVy)
        buf.putFloat(s.hostPaddleX); buf.putFloat(s.clientPaddleX)
        buf.putInt(s.hostScore); buf.putInt(s.clientScore)
        buf.putInt(s.phaseOrdinal)
        buf.putFloat(s.hostHitPulse); buf.putFloat(s.clientHitPulse)
        buf.putInt(s.rally); buf.putInt(s.sayingSide)
        buf.put(stBytes)
        buf.putInt(s.hostCreatureIdx); buf.putInt(s.clientCreatureIdx)
        buf.putFloat(s.hostScreenWidth); buf.putFloat(s.hostScreenHeight)
        buf.putLong(wallTimeMs)
        buf.flip()
        return buf
    }

    private fun decodeState(buf: ByteBuffer): NetGameState? {
        return try {
            val bx = buf.float; val by = buf.float
            val bvx = buf.float; val bvy = buf.float
            val hpx = buf.float; val cpx = buf.float
            val hs = buf.int; val cs = buf.int
            val ph = buf.int
            val hhp = buf.float; val chp = buf.float
            val r = buf.int; val ss = buf.int
            val stBytes = ByteArray(ST_REGION_SIZE).also { buf.get(it) }
            val st = String(stBytes, Charsets.UTF_8).trimEnd(' ')
            val hci = buf.int; val cci = buf.int
            val hsw = buf.float; val hsh = buf.float
            val ts = buf.long
            NetGameState(
                ballX = bx, ballY = by,
                ballVx = bvx, ballVy = bvy,
                hostPaddleX = hpx, clientPaddleX = cpx,
                hostScore = hs, clientScore = cs,
                phaseOrdinal = ph,
                hostHitPulse = hhp, clientHitPulse = chp,
                rally = r, sayingSide = ss, sayingText = st,
                hostCreatureIdx = hci, clientCreatureIdx = cci,
                hostScreenWidth = hsw, hostScreenHeight = hsh,
                serverTimestamp = ts,
            )
        } catch (e: Exception) {
            Log.e(TAG, "decodeState failed", e)
            null
        }
    }
}
