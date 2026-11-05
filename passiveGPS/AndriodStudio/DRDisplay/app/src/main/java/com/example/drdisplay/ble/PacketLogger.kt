package com.example.drdisplay.ble

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.io.BufferedWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class PacketEntry(val time: String, val payload: String, val valid: Boolean)
data class PacketLogState(
    val recording: Boolean = false,
    val total: Long = 0,
    val recent: List<PacketEntry> = emptyList(),
    val filePath: String? = null,
    val message: String = ""
)

data class NavigationSnapshot(
    val gpsLat: Double?, val gpsLon: Double?, val gpsAccuracy: Float?, val gpsElapsedNs: Long?,
    val navLat: Double?, val navLon: Double?, val homeLat: Double?, val homeLon: Double?
)

/** Receipt timestamps describe the phone callback, not sensor sampling time. */
class PacketLogger(private val context: Context) {
    val state = mutableStateOf(PacketLogState())
    @Volatile var navigationSnapshot = NavigationSnapshot(null, null, null, null, null, null, null, null)
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(8192))
    private val lock = Any()
    private var recording = false
    private var writer: BufferedWriter? = null
    private val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "belt_logs")
    private var latestFile: File? = directory.listFiles()?.filter { it.isFile && it.name.startsWith("belt_") && it.extension == "csv" }?.maxByOrNull { it.name }
    private var statusMessage = ""
    private var count = 0L
    private val recent = ArrayDeque<PacketEntry>()
    private val nameFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.of("UTC"))

    init { synchronized(lock) { publish() } }

    private fun publish(message: String? = null) {
        if (message != null) statusMessage = message
        val snapshot = PacketLogState(recording, count, recent.toList(), latestFile?.absolutePath, statusMessage)
        main.post { state.value = snapshot }
    }

    fun start() = synchronized(lock) {
        if (recording) return@synchronized
        try {
            check(directory.exists() || directory.mkdirs()) { "Cannot create log directory" }
            val file = File(directory, "belt_${nameFormat.format(Instant.now())}.csv")
            val newWriter = file.bufferedWriter(Charsets.UTF_8)
            newWriter.write("phone_utc,phone_unix_ms,phone_elapsed_realtime_ns,characteristic_uuid,byte_count,raw_hex,raw_utf8,parse_status,sequence,roll_deg,pitch_deg,yaw_deg,velocity_x,velocity_y,velocity_z,gps_lat,gps_lon,gps_accuracy_m,gps_fix_elapsed_ns,nav_lat_before_packet,nav_lon_before_packet,home_lat,home_lon,test_version,foot_id,foot_sequence,foot_sample_us,test_page,still,value_1_name,value_1,value_2_name,value_2\n")
            newWriter.flush()
            writer = newWriter
            latestFile = file
            recording = true
            publish("Recording")
        } catch (e: Exception) {
            publish("Could not start log: ${e.message}")
        }
    }

    fun stop() = synchronized(lock) {
        if (!recording) return@synchronized
        recording = false
        val closing = writer
        writer = null
        try {
            worker.execute {
                try {
                    closing?.close()
                    synchronized(lock) { if (!recording) publish("Log saved") }
                } catch (e: Exception) {
                    synchronized(lock) { publish("Log close failed: ${e.message}") }
                }
            }
            publish("Finishing log…")
        } catch (e: Exception) {
            try { closing?.close() } catch (_: Exception) {}
            publish("Log incomplete: ${e.message}")
        }
    }

    fun record(value: ByteArray, uuid: String) {
        val epoch = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()
        val text = value.toString(Charsets.UTF_8)
        val fields = text.trim().split(",")
        val valid = fields.size == 7 && fields[0].trim().toLongOrNull() != null &&
            fields.drop(1).all { it.trim().toFloatOrNull()?.isFinite() == true }
        val test = decodeFootTestPacket(value)
        val time = Instant.ofEpochMilli(epoch).toString()
        val hex = value.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val columns = if (valid) fields.map { quote(it.trim()) } else List(7) { "" }
        val nav = navigationSnapshot
        val positions = listOf(nav.gpsLat, nav.gpsLon, nav.gpsAccuracy, nav.gpsElapsedNs, nav.navLat, nav.navLon, nav.homeLat, nav.homeLon).map { it?.toString() ?: "" }
        val testColumns = test?.let { listOf("1", it.foot, it.sequence.toString(), it.sampleMicros.toString(), it.page.toString(), it.still.toString(), it.name1, it.value1.toString(), it.name2, it.value2.toString()) } ?: List(10) { "" }
        val row = listOf(time, epoch.toString(), elapsed.toString(), uuid, value.size.toString(), hex, quote(if (test == null) text else ""),
            if (test != null) "foot_test_v1" else if (valid) "valid" else "invalid").plus(columns).plus(positions).plus(testColumns).joinToString(",") + "\n"
        synchronized(lock) {
            count++
            recent.addFirst(PacketEntry(time, test?.let { "${it.foot} #${it.sequence} p${it.page}: ${it.name1}=${it.value1}, ${it.name2}=${it.value2}" } ?: text, valid || test != null))
            if (recent.size > 50) recent.removeLast()
            val target = if (recording) writer else null
            if (target != null) {
                try {
                    if (worker.queue.remainingCapacity() <= 1) throw IllegalStateException("Log queue full")
                    worker.execute {
                        try {
                            target.write(row)
                            target.flush()
                        } catch (e: Exception) {
                            synchronized(lock) {
                                if (writer === target) {
                                    recording = false
                                    writer = null
                                    try { target.close() } catch (_: Exception) {}
                                    publish("Log write failed: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    recording = false
                    writer = null
                    try { worker.execute { target.close() } } catch (_: Exception) {}
                    publish("Log queue full; recording stopped. Data may be incomplete.")
                    return
                }
            }
            publish()
        }
    }

    fun export(uri: Uri) {
        val source = synchronized(lock) { latestFile } ?: return
        if (state.value.recording) {
            synchronized(lock) { publish("Stop recording before exporting") }
            return
        }
        try {
            worker.execute {
                try {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")
                    output.use { out -> source.inputStream().use { it.copyTo(out) } }
                    synchronized(lock) { publish("Export complete") }
                } catch (e: Exception) {
                    synchronized(lock) { publish("Export failed: ${e.message}") }
                }
            }
        } catch (e: Exception) {
            synchronized(lock) { publish("Export failed: ${e.message}") }
        }
    }
}
