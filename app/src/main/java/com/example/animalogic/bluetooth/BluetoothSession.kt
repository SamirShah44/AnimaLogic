package com.example.animalogic.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Note, used multiple sources and will provide them here and above any segment that I used a source for.
 * The sources here will be just the site if I use it more than once.
 * Sources in the code will be specific pages from those sites.
 *
 * https://www.bluetooth.com/specifications/assigned-numbers/
 * https://devzone.nordicsemi.com/
 * https://developer.android.com/
 * https://punchthrough.com/android-ble-guide/
 * https://novelbits.io/maximum-data-bluetooth-advertising-packet-ble/
 *
 * Another note, all of my comments are basically just conjecture about what I'm reading online. If
 * one of these sources says x = y, I just have to accept it because I have no prior understanding of
 * how Bluetooth works or is supposed to work. So, that is to say, if an assertion of mine in a comment
 * is wrong, the code associated with it is also wrong... so hopefully not.
 */

// ----- Identifiers and Permissions -----

// Custom UUIDs for our own game service (same idea as the sensor service in class).
// Both phones run this app, so both sides already know them.
val SERVICE_UUID: UUID = UUID.fromString("a2c40001-1e5b-4b7d-8c33-5d6e7f809a10")

/** Host -> guest. The guest subscribes to it, the host sends notifications. */
val HOST_TO_GUEST_UUID: UUID = UUID.fromString("a2c40002-1e5b-4b7d-8c33-5d6e7f809a10")

/** Guest -> host. The guest writes to it. */
val GUEST_TO_HOST_UUID: UUID = UUID.fromString("a2c40003-1e5b-4b7d-8c33-5d6e7f809a10")

/** Standard "Client Characteristic Configuration Descriptor" (0x2902), the same one as in class. */
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

// 0xFFFF is the company id the Bluetooth SIG reserves for testing. We use it to carry the host's
// username in the advertisement so the guest can show "FastHippo123" instead of an address.
// Source: https://www.bluetooth.com/specifications/assigned-numbers/ (Company Identifiers),
//   and also quoted in https://devzone.nordicsemi.com/f/nordic-q-a/24449/advertising-manufacturing-specific-data-without-company-id
private const val MANUFACTURER_ID = 0xFFFF

private const val TAG = "BluetoothSession"
private const val SCAN_TIMEOUT_MS = 10_000L
private const val CONNECT_TIMEOUT_MS = 15_000L

// Android 12 (API 31) split Bluetooth into SCAN / ADVERTISE / CONNECT runtime permissions;
// Android 11 and older use the old BLUETOOTH / BLUETOOTH_ADMIN + location permissions instead.
// Source: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
/** Runtime permissions to request (RequestMultiplePermissions) before calling [BluetoothSession.host]. */
val HOST_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray() // Android 11 and older: the manifest entries are enough for hosting
    }

/** Runtime permissions to request before calling [BluetoothSession.scan]. */
val GUEST_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) // Android 11 and older scan with location
    }

// ----- State exposed to the UI -----

enum class Role { HOST, GUEST }

enum class LinkState {
    IDLE,        // nothing running (check SessionState.message for why)
    ADVERTISING, // host: waiting for a guest
    SCANNING,    // guest: looking for hosts
    CONNECTING,  // guest: connecting to the chosen host
    SETTING_UP,  // connected, subscribing and exchanging names
    CONNECTED,   // ready: names exchanged, versions match, game messages can flow
}

data class FoundHost(val address: String, val name: String, val rssi: Int)

data class SessionState(
    val link: LinkState = LinkState.IDLE,
    val hosts: List<FoundHost> = emptyList(),
    val peerName: String? = null,
    val message: String? = null,
)

/**
 * One Bluetooth LE link between two phones running AnimaLogic.
 *
 * Roles:
 *  - HOST  = BLE peripheral + GATT server. Advertises and waits for one guest.
 *  - GUEST = BLE central + GATT client. Scans and connects (this is the code from class).
 *        so, you can kinda think from the guest's perspective that they're connecting to the sensor
 *        from the Bluetooth class slideshow. That's like one of the only things relating to the slideshow lol
 *
 * Host -> guest messages go out as notifications on [HOST_TO_GUEST_UUID];
 * guest -> host messages are writes to [GUEST_TO_HOST_UUID].
 *
 * Setup order: guest connects -> discovers services -> subscribes (CCCD write) -> sends Hello
 * -> host answers Hello -> both sides are CONNECTED -> success, hopefully
 *
 * Threading: Bluetooth callbacks arrive on background threads, so each one immediately posts to
 * the main thread and all logic runs there. Call the public functions from the main thread.
 * The UI only needs [state] and [incoming].
 * Source: https://developer.android.com/reference/android/bluetooth/BluetoothDevice#connectGatt
 *              Without a Handler, callbacks run on an unspecified background thread.
 *         https://punchthrough.com/android-ble-guide/ (recommends driving GATT from the main thread)
 *
 * Permissions: the screen must be granted [HOST_PERMISSIONS] / [GUEST_PERMISSIONS] first. Every
 * Bluetooth call here also catches SecurityException in case they are revoked mid-session,
 * which is why the lint check is suppressed.
 * Source: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
 */
@SuppressLint("MissingPermission")
class BluetoothSession(context: Context, private val username: String) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val manager: BluetoothManager? = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = manager?.adapter

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    // A buffered channel (not a SharedFlow) so a message that arrives a moment before the screen
    // starts collecting is kept instead of dropped. A SharedFlow with no subscribers and no replay
    // does not keep emitted values; a channel buffers them until someone receives it.
    // Source: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-shared-flow/
    //   and https://kotlinlang.org/docs/channels.html#buffered-channels
    private val _incoming = Channel<Message>(Channel.BUFFERED)

    /**
     * Game messages from the opponent (Start, Ready, Go, Finished). Hello is handled in here.
     * Collect this from ONE place only: each message is delivered to a single collector.
     */
    val incoming: Flow<Message> = _incoming.receiveAsFlow()

    private var role: Role? = null

    // host side
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var guestDevice: BluetoothDevice? = null
    private var hostToGuest: BluetoothGattCharacteristic? = null
    private var guestSubscribed = false
    private val notifyQueue = ArrayDeque<ByteArray>()
    private var notifyBusy = false

    // guest side
    private var scanner: BluetoothLeScanner? = null
    private val found = mutableMapOf<String, BluetoothDevice>()
    private var gatt: BluetoothGatt? = null
    private var guestToHost: BluetoothGattCharacteristic? = null
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeBusy = false

    private val scanTimeout = Runnable {
        stopScan()
        _state.update {
            if (it.link != LinkState.SCANNING) it
            else it.copy(
                link = LinkState.IDLE,
                message = if (it.hosts.isEmpty()) "No players found nearby" else null,
            )
        }
    }

    private val connectTimeout = Runnable {
        if (_state.value.link != LinkState.CONNECTED) fail("Connection timed out")
    }

    // ----- Public API -----

    fun isBluetoothOn(): Boolean =
        try { adapter?.isEnabled == true } catch (e: SecurityException) { false }

    /** Host: publish the game service, advertise, and wait for one guest. */
    fun host() {
        teardown()
        clearInbox()
        val ble = readyAdapter() ?: return
        if (ble.bluetoothLeAdvertiser == null) return fail("This phone cannot host a game")
        role = Role.HOST
        try {
            val server = manager?.openGattServer(appContext, serverCallback)
                ?: return fail("Could not open the Bluetooth server")
            gattServer = server

            val down = BluetoothGattCharacteristic(
                HOST_TO_GUEST_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, 0
            )
            down.addDescriptor(
                BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
            val up = BluetoothGattCharacteristic(
                GUEST_TO_HOST_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(down)
            service.addCharacteristic(up)
            hostToGuest = down

            // Advertising starts in onServiceAdded, once the service is really published
            // Source: https://developer.android.com/reference/android/bluetooth/BluetoothGattServer#addService(android.bluetooth.BluetoothGattService)
            if (!server.addService(service)) return fail("Could not publish the game service")
            _state.value = SessionState(link = LinkState.ADVERTISING)
        } catch (e: SecurityException) {
            fail("Nearby devices permission is missing")
        }
    }

    /** Guest: scan for hosts for a few seconds. Results appear in [state].hosts. */
    fun scan() {
        teardown()
        clearInbox()
        val ble = readyAdapter() ?: return
        val leScanner = ble.bluetoothLeScanner ?: return fail("Bluetooth scanner is unavailable")
        role = Role.GUEST
        found.clear()
        scanner = leScanner
        _state.value = SessionState(link = LinkState.SCANNING)
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            leScanner.startScan(listOf(filter), settings, scanCallback)
            main.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
        } catch (e: SecurityException) {
            fail("Nearby devices permission is missing")
        }
    }

    /** Guest: connect to a host from [SessionState.hosts]. */
    fun connect(address: String) {
        if (gatt != null) return // already connecting (double tap)
        val device = found[address] ?: return
        stopScan()
        role = Role.GUEST
        _state.update { it.copy(link = LinkState.CONNECTING, message = null) }
        try {
            // deprecated but i dont care, still works for now
            gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        } catch (e: SecurityException) {
            fail("Nearby devices permission is missing")
        }
    }

    /** Send a game message. Returns false if the link isn't ready. */
    fun send(message: Message): Boolean {
        if (_state.value.link != LinkState.CONNECTED) return false
        return enqueue(message)
    }

    /** Leave the session. The opponent sees a disconnect. Call when the screen is left. */
    fun close() {
        teardown()
        found.clear()
        _state.value = SessionState()
    }

    // Android has no internal queue: starting a second GATT operation before the first one's
    // callback arrives fails, so each side keeps its own queue and sends one packet at a time.
    // Source: https://punchthrough.com/android-ble-guide/ ("Queue operations and execute them serially")

    private fun enqueue(message: Message): Boolean {
        val bytes = message.encode()
        when (role) {
            Role.HOST -> { notifyQueue.addLast(bytes); pumpNotify() }
            Role.GUEST -> { writeQueue.addLast(bytes); pumpWrite() }
            null -> return false
        }
        return true
    }

    // ----- Sending -----

    // host -> guest: wait for onNotificationSent before sending the next one
    // Source: https://developer.android.com/reference/android/bluetooth/BluetoothGattServerCallback#onNotificationSent(android.bluetooth.BluetoothDevice,%20int)
    private fun pumpNotify() {
        if (notifyBusy || !guestSubscribed) return
        val server = gattServer ?: return
        val device = guestDevice ?: return
        val characteristic = hostToGuest ?: return
        val bytes = notifyQueue.removeFirstOrNull() ?: return
        notifyBusy = true
        val started = try {
            if (Build.VERSION.SDK_INT >= 33) {
                server.notifyCharacteristicChanged(device, characteristic, false, bytes) ==
                        BluetoothStatusCodes.SUCCESS
            } else {
                // Android 12 and older: the bytes are stored on the characteristic first (the
                // version that takes the bytes directly was added in API 33)
                // Source: https://developer.android.com/reference/android/bluetooth/BluetoothGattServer#notifyCharacteristicChanged
                @Suppress("DEPRECATION")
                characteristic.value = bytes
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, characteristic, false)
            }
        } catch (e: SecurityException) {
            false
        }
        if (!started) {
            notifyBusy = false
            fail("Could not send to opponent")
        }
    }

    // guest -> host: wait for onCharacteristicWrite before sending the next one
    // Source: https://punchthrough.com/android-ble-guide/ and https://developer.android.com/reference/android/bluetooth/BluetoothGatt#writeCharacteristic
    private fun pumpWrite() {
        if (writeBusy) return
        val client = gatt ?: return
        val characteristic = guestToHost ?: return
        val bytes = writeQueue.removeFirstOrNull() ?: return
        writeBusy = true
        val started = try {
            if (Build.VERSION.SDK_INT >= 33) {
                client.writeCharacteristic(
                    characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                characteristic.value = bytes
                @Suppress("DEPRECATION")
                client.writeCharacteristic(characteristic)
            }
        } catch (e: SecurityException) {
            false
        }
        if (!started) {
            writeBusy = false
            fail("Could not send to opponent")
        }
    }

    // ----- Receiving -----

    private fun handleBytes(bytes: ByteArray) {
        val message = Message.decode(bytes)
        if (message == null) {
            Log.w(TAG, "Dropped malformed packet (${bytes.size} bytes)")
            return
        }
        if (message is Message.Hello) {
            onHello(message)
        } else if (_state.value.link == LinkState.CONNECTED) {
            _incoming.trySend(message)
        }
    }

    // Both sides send Hello once: the guest first, then the host answers. Also checks that the
    // two phones speak the same protocol version.
    private fun onHello(hello: Message.Hello) {
        if (hello.version != PROTOCOL_VERSION) {
            fail("Opponent has a different version of the app")
            return
        }
        if (_state.value.link == LinkState.CONNECTED) return // duplicate Hello
        if (role == Role.HOST) enqueue(Message.Hello(PROTOCOL_VERSION, username))
        main.removeCallbacks(connectTimeout)
        _state.update { it.copy(link = LinkState.CONNECTED, peerName = hello.username, message = null) }
    }

    private fun clearInbox() {
        while (_incoming.tryReceive().isSuccess) { /* clear leftovers from an earlier match */ }
    }

    // ----- Host stuff: Advertising & GATT server -----

    private fun startAdvertising() {
        val leAdvertiser = adapter?.bluetoothLeAdvertiser ?: return fail("This phone cannot host a game")
        advertiser = leAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        // The advertisement carries our service UUID (what the guest's scan filter matches)...
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        // ... and the scan response carries the username. An advertisement is only 31 bytes and
        // the 128-bit UUID uses most of it, so the name has to go in the second packet I guess.
        // Source: https://novelbits.io/maximum-data-bluetooth-advertising-packet-ble/
        val nameBytes = username.toByteArray(Charsets.UTF_8).let { it.copyOf(minOf(it.size, 20)) }
        val scanResponse = AdvertiseData.Builder()
            .addManufacturerData(MANUFACTURER_ID, nameBytes)
            .build()
        try {
            leAdvertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (e: SecurityException) {
            fail("Nearby devices permission is missing")
        }
    }

    private fun stopAdvertising() {
        val adv = advertiser ?: return
        advertiser = null
        try { adv.stopAdvertising(advertiseCallback) } catch (e: RuntimeException) { Log.w(TAG, "stopAdvertising failed", e) }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            main.post { if (gattServer != null) fail("Could not start advertising (code $errorCode)") }
        }
    }

    /** The first phone that subscribes to our characteristic becomes the opponent. */
    private fun adoptGuest(device: BluetoothDevice) {
        guestDevice = device
        try {
            // Recommended by Nordic's Android BLE developer: call server.connect() after a central
            // connects, so Android knows this app is using the link. Otherwise, the system may drop
            // a connection that no app has claimed. (Stopping advertising itself should not disconnect?)
            // Source: https://devzone.nordicsemi.com/f/nordic-q-a/41123/bluetoothleadvertiser-stopadvertising-causes-device-to-disconnect
            gattServer?.connect(device, false)
        } catch (e: SecurityException) {
            Log.w(TAG, "server connect failed", e)
        }
        stopAdvertising()
        _state.update { it.copy(link = LinkState.SETTING_UP) }
        main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
    }

    private fun respond(device: BluetoothDevice, requestId: Int, responseNeeded: Boolean, offset: Int, status: Int) {
        if (!responseNeeded) return
        try {
            gattServer?.sendResponse(device, requestId, status, offset, null)
        } catch (e: SecurityException) {
            Log.w(TAG, "sendResponse failed", e)
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            main.post {
                if (gattServer == null) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) startAdvertising()
                else fail("Could not publish the game service (status $status)")
            }
        }

        // This fires for ANY device that connects to this phone (even a smartwatch), so a new
        // connection alone does not make someone our guest. See onDescriptorWriteRequest.
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            main.post {
                if (gattServer == null) return@post
                if (newState == BluetoothProfile.STATE_DISCONNECTED && device == guestDevice) {
                    fail("Opponent disconnected")
                }
            }
        }

        // The guest enabling notifications = writing 01 00 to the CCCD of HOST_TO_GUEST.
        // This is the host-side view of the CCCD write from class.
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            main.post {
                if (gattServer == null) return@post
                val ours = descriptor.uuid == CCCD_UUID &&
                        descriptor.characteristic.uuid == HOST_TO_GUEST_UUID
                val someoneElse = guestDevice != null && device != guestDevice
                if (!ours || someoneElse) {
                    respond(device, requestId, responseNeeded, offset, BluetoothGatt.GATT_FAILURE)
                    return@post
                }
                val enabling = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                if (guestDevice == null && enabling) adoptGuest(device)
                if (device == guestDevice) guestSubscribed = enabling
                respond(device, requestId, responseNeeded, offset, BluetoothGatt.GATT_SUCCESS)
                pumpNotify()
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            main.post {
                if (gattServer == null) return@post
                if (device != guestDevice || characteristic.uuid != GUEST_TO_HOST_UUID) {
                    respond(device, requestId, responseNeeded, offset, BluetoothGatt.GATT_FAILURE)
                    return@post
                }
                respond(device, requestId, responseNeeded, offset, BluetoothGatt.GATT_SUCCESS)
                handleBytes(value)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            main.post {
                if (gattServer == null || device != guestDevice) return@post
                notifyBusy = false
                if (status == BluetoothGatt.GATT_SUCCESS) pumpNotify()
                else fail("Could not send to opponent (status $status)")
            }
        }
    }

    // ----- Guest stuff: scan & GATT client -----

    private fun stopScan() {
        main.removeCallbacks(scanTimeout)
        val s = scanner ?: return
        scanner = null
        try { s.stopScan(scanCallback) } catch (e: RuntimeException) { Log.w(TAG, "stopScan failed", e) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post {
                if (scanner == null) return@post
                try {
                    val address = result.device.address
                    val name = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID)
                        ?.toString(Charsets.UTF_8) ?: "AnimaLogic player"
                    found[address] = result.device
                    // one entry per host, refreshed on every advertisement
                    _state.update { s ->
                        s.copy(hosts = (s.hosts.filter { it.address != address } + FoundHost(address, name, result.rssi))
                            .sortedBy { it.name })
                    }
                } catch (e: SecurityException) {
                    fail("Nearby devices permission is missing")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post { if (scanner != null) fail("Scan failed (code $errorCode)") }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g !== gatt) { closeQuietly(g); return@post } // late callback from an old session
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    _state.update { it.copy(link = LinkState.SETTING_UP) }
                    try {
                        if (!g.discoverServices()) fail("Could not read the host's services")
                    } catch (e: SecurityException) {
                        fail("Nearby devices permission is missing")
                    }
                } else {
                    // disconnected, or the attempt failed (status 133 = GATT_ERROR, the usual "try again" error)
                    // Source: https://punchthrough.com/android-ble-guide/ ("Handling Common Error Scenarios While Connecting")
                    val wasPlaying = _state.value.link == LinkState.CONNECTED
                    fail(if (wasPlaying) "Opponent disconnected" else "Could not connect, try again (status $status)")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g !== gatt) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) return@post fail("Service discovery failed (status $status)")
                val service = g.getService(SERVICE_UUID)
                val down = service?.getCharacteristic(HOST_TO_GUEST_UUID)
                val up = service?.getCharacteristic(GUEST_TO_HOST_UUID)
                val cccd = down?.getDescriptor(CCCD_UUID)
                if (down == null || up == null || cccd == null) return@post fail("That device is not an AnimaLogic host")
                guestToHost = up
                try {
                    // Two parts, like in class: tell Android we want callbacks, then tell the host.
                    if (!g.setCharacteristicNotification(down, true)) return@post fail("Could not subscribe to the host")
                    val started = if (Build.VERSION.SDK_INT >= 33) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                                BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                    if (!started) fail("Could not subscribe to the host")
                } catch (e: SecurityException) {
                    fail("Nearby devices permission is missing")
                }
            }
        }

        // Subscription confirmed -> introduce ourselves. The host's Hello reply makes us CONNECTED.
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post {
                if (g !== gatt || descriptor.uuid != CCCD_UUID) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) return@post fail("Subscribing failed (status $status)")
                enqueue(Message.Hello(PROTOCOL_VERSION, username))
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            main.post {
                if (g !== gatt) return@post
                writeBusy = false
                if (status == BluetoothGatt.GATT_SUCCESS) pumpWrite()
                else fail("Could not send to opponent (status $status)")
            }
        }

        // Android 13+ calls this version (the one from class)
        // Source: https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback#onCharacteristicChanged
        override fun onCharacteristicChanged(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
        ) {
            onNotification(g, characteristic.uuid, value)
        }

        // Android 12 and older call this one, where the bytes live on the characteristic.
        // It was deprecated because the characteristic's value can change before you read it,
        // which is why it is copied immediately below.
        // Source: https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback#onCharacteristicChanged
        //   (the @deprecated note: the new overload "is memory safe by providing the characteristic value at the time of notification")
        @Deprecated("Used by Android 12 and older")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value?.clone() ?: return // copy now: the next packet overwrites it
            onNotification(g, characteristic.uuid, value)
        }
    }

    private fun onNotification(g: BluetoothGatt, uuid: UUID, value: ByteArray) {
        main.post {
            if (g === gatt && uuid == HOST_TO_GUEST_UUID) handleBytes(value)
        }
    }

    private fun closeQuietly(g: BluetoothGatt) {
        try { g.close() } catch (e: RuntimeException) { Log.w(TAG, "close failed", e) }
    }

    // ----- Shared Helpers -----

    private fun readyAdapter(): BluetoothAdapter? {
        val ble = adapter
        if (ble == null) { fail("Bluetooth is not available on this phone"); return null }
        val on = try { ble.isEnabled } catch (e: SecurityException) { false }
        if (!on) { fail("Turn Bluetooth on to play"); return null }
        return ble
    }

    /** Stop everything and release all Bluetooth objects (the "disconnect, then close()" step from class). */
    private fun teardown() {
        stopScan()
        stopAdvertising()
        main.removeCallbacks(connectTimeout)

        val server = gattServer
        val guest = guestDevice
        gattServer = null; guestDevice = null; hostToGuest = null; guestSubscribed = false
        try {
            if (guest != null) server?.cancelConnection(guest)
            server?.close()
        } catch (e: RuntimeException) { Log.w(TAG, "server cleanup failed", e) }

        val client = gatt
        gatt = null; guestToHost = null // null first so late callbacks are ignored
        try {
            client?.disconnect()
            client?.close()
        } catch (e: RuntimeException) { Log.w(TAG, "client cleanup failed", e) }

        notifyQueue.clear(); notifyBusy = false
        writeQueue.clear(); writeBusy = false
        role = null
    }

    /** Tear down and tell the UI why. */
    private fun fail(message: String) {
        Log.w(TAG, message)
        teardown()
        _state.value = SessionState(message = message)
    }
}
