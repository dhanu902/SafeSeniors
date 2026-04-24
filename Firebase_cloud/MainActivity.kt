//
//package com.example.falldetectionapp
//
//import android.content.Context
//import android.graphics.Color
//import android.hardware.Sensor
//import android.hardware.SensorEvent
//import android.hardware.SensorEventListener
//import android.hardware.SensorManager
//import android.os.Bundle
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import com.google.firebase.firestore.FirebaseFirestore
//import org.tensorflow.lite.Interpreter
//import java.io.FileInputStream
//import java.nio.MappedByteBuffer
//import java.nio.channels.FileChannel
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//import kotlin.math.abs
//import kotlin.math.sqrt
//
//class MainActivity : AppCompatActivity(), SensorEventListener {
//
//    private lateinit var sensorManager: SensorManager
//    private var linearAccelerometer: Sensor? = null
//    private var gyroscope: Sensor? = null
//
//    private var interpreter: Interpreter? = null
//    private lateinit var statusTextView: TextView
//    private lateinit var bufferTextView: TextView
//
//    private var latestAccel = FloatArray(3)
//    private var latestGyro = FloatArray(3)
//
//    private val timeSeriesBuffer = mutableListOf<FloatArray>()
//    private val TIMESTEPS = 160
//    private val STEP_SIZE = 20
//
//    // STATE TRACKER FOR LOGIC
//    // 0 = Fall, 1 = Idle, 2 = Motion, 3 = Step
//    private var lastUIState = 1
//
//    private val FALL_IMPACT_THRESHOLD = 8.0f
//
//    // --- FIREBASE VARIABLES ---
//    private lateinit var db: FirebaseFirestore
//    private var lastLoggedState = -1 // Tracks what was last sent to the database
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        statusTextView = findViewById(R.id.statusTextView)
//        bufferTextView = findViewById(R.id.bufferTextView)
//
//        // Initialize Firebase
//        db = FirebaseFirestore.getInstance()
//
//        try {
//            interpreter = Interpreter(loadModelFile("fall_model.tflite"))
//            statusTextView.text = "Model Loaded. Gathering data..."
//        } catch (e: Exception) {
//            statusTextView.text = "Error: Model not found."
//            statusTextView.setTextColor(Color.RED)
//            e.printStackTrace()
//        }
//
//        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
//        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
//        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
//    }
//
//    override fun onResume() {
//        super.onResume()
//        linearAccelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
//        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
//    }
//
//    override fun onPause() {
//        super.onPause()
//        sensorManager.unregisterListener(this)
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        interpreter?.close()
//    }
//
//    override fun onSensorChanged(event: SensorEvent?) {
//        if (event == null || interpreter == null) return
//
//        when (event.sensor.type) {
//            Sensor.TYPE_LINEAR_ACCELERATION -> {
//                latestAccel = event.values.clone()
//
//                val combinedFeatures = floatArrayOf(
//                    latestAccel[0], latestAccel[1], latestAccel[2],
//                    latestGyro[0], latestGyro[1], latestGyro[2]
//                )
//
//                timeSeriesBuffer.add(combinedFeatures)
//
//                if (timeSeriesBuffer.size >= TIMESTEPS) {
//
//                    if (isPhoneStill(timeSeriesBuffer)) {
//                        lastUIState = 1 // Update state tracker
//                        setUIState(1)
//                    } else {
//                        runInference(timeSeriesBuffer.take(TIMESTEPS))
//                    }
//
//                    timeSeriesBuffer.subList(0, STEP_SIZE).clear()
//                } else {
//                    bufferTextView.text = "Filling Buffer: ${timeSeriesBuffer.size}/$TIMESTEPS"
//                }
//            }
//            Sensor.TYPE_GYROSCOPE -> {
//                latestGyro = event.values.clone()
//            }
//        }
//    }
//
//    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
//
//    private fun isPhoneStill(buffer: List<FloatArray>): Boolean {
//        val recentReadings = buffer.takeLast(20)
//
//        for (reading in recentReadings) {
//            val accelSum = abs(reading[0]) + abs(reading[1]) + abs(reading[2])
//            val gyroSum = abs(reading[3]) + abs(reading[4]) + abs(reading[5])
//
//            if (accelSum > 1.0f || gyroSum > 0.5f) {
//                return false
//            }
//        }
//        return true
//    }
//
//    private fun normalizeFixed(value: Float, min: Float, max: Float): Float {
//        var scaled = (value - min) / (max - min)
//        if (scaled < 0.0f) return 0.0f
//        if (scaled > 1.0f) return 1.0f
//        return scaled
//    }
//
//    private fun runInference(sensorData: List<FloatArray>) {
//        val input = Array(1) { Array(TIMESTEPS) { FloatArray(6) } }
//        var maxPhysicalImpact = 0.0f
//
//        for (row in 0 until TIMESTEPS) {
//            val accelX = sensorData[row][0]
//            val accelY = sensorData[row][1]
//            val accelZ = sensorData[row][2]
//
//            val currentImpact = sqrt((accelX * accelX + accelY * accelY + accelZ * accelZ).toDouble()).toFloat()
//            if (currentImpact > maxPhysicalImpact) {
//                maxPhysicalImpact = currentImpact
//            }
//
//            input[0][row][0] = normalizeFixed(accelX, -20f, 20f)
//            input[0][row][1] = normalizeFixed(accelY, -20f, 20f)
//            input[0][row][2] = normalizeFixed(accelZ, -20f, 20f)
//            input[0][row][3] = normalizeFixed(sensorData[row][3], -10f, 10f)
//            input[0][row][4] = normalizeFixed(sensorData[row][4], -10f, 10f)
//            input[0][row][5] = normalizeFixed(sensorData[row][5], -10f, 10f)
//        }
//
//        val output = Array(1) { FloatArray(4) }
//
//        try {
//            interpreter?.run(input, output)
//        } catch (e: Exception) { return }
//
//        val probabilities = output[0]
//        var maxIndex = 0
//        var maxProb = probabilities[0]
//
//        for (i in 1..3) {
//            if (probabilities[i] > maxProb) {
//                maxProb = probabilities[i]
//                maxIndex = i
//            }
//        }
//
//        // ==========================================
//        //  FAILSAFE 2: THE PHYSICAL REALITY CHECK
//        // ==========================================
//        if (maxIndex == 0 && maxPhysicalImpact < FALL_IMPACT_THRESHOLD) {
//            maxIndex = 2
//        }
//
//        // ==========================================
//        //  FAILSAFE 3: YOUR STATE TRANSITION LOGIC
//        // ==========================================
//        if (maxIndex == 0 && lastUIState == 1) {
//            maxIndex = 2
//        }
//
//        lastUIState = maxIndex
//
//        runOnUiThread {
//            bufferTextView.text = String.format("Confidences:\nFall: %.2f  Idle: %.2f\nMotion: %.2f  Step: %.2f\nMax Impact: %.2f",
//                probabilities[0], probabilities[1], probabilities[2], probabilities[3], maxPhysicalImpact)
//        }
//
//        setUIState(maxIndex)
//    }
//
//    private fun setUIState(stateIndex: Int) {
//        // Map the integer to the actual string name
//        val activityName = when (stateIndex) {
//            0 -> "Fall Detected"
//            1 -> "Idle"
//            2 -> "In Motion"
//            3 -> "Stepping"
//            else -> "Unknown"
//        }
//
//        // --- FIREBASE LOGIC ---
//        // Only send data if the activity changed, or if it is a Fall
//        if (stateIndex != lastLoggedState || stateIndex == 0) {
//            lastLoggedState = stateIndex
//            saveToFirebase(activityName)
//        }
//
//        runOnUiThread {
//            when (stateIndex) {
//                0 -> {
//                    statusTextView.text = "🚨 FALL DETECTED 🚨"
//                    statusTextView.setTextColor(Color.RED)
//                }
//                1 -> {
//                    statusTextView.text = "Idle"
//                    statusTextView.setTextColor(Color.DKGRAY)
//                }
//                2 -> {
//                    statusTextView.text = "In Motion"
//                    statusTextView.setTextColor(Color.BLUE)
//                }
//                3 -> {
//                    statusTextView.text = "Stepping"
//                    statusTextView.setTextColor(Color.parseColor("#008000"))
//                }
//            }
//        }
//    }
//
//    // Helper function to push data to Firestore
//    private fun saveToFirebase(activity: String) {
//        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
//        val timestamp = sdf.format(Date())
//
//        val activityData = hashMapOf(
//            "activity" to activity,
//            "timestamp" to timestamp,
//            "deviceId" to "Android_Device_1",
//            "isAlert" to (activity == "Fall Detected")
//        )
//
//        db.collection("activity_logs")
//            .add(activityData)
//            .addOnSuccessListener { documentReference ->
//                println("✅ DocumentSnapshot added with ID: ${documentReference.id}")
//            }
//            .addOnFailureListener { e ->
//                println("❌ Error adding document: $e")
//            }
//    }
//
//    private fun loadModelFile(modelPath: String): MappedByteBuffer {
//        val fd = assets.openFd(modelPath)
//        return FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
//    }
//}


package com.example.falldetectionapp

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var linearAccelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var interpreter: Interpreter? = null
    private lateinit var statusTextView: TextView
    private lateinit var bufferTextView: TextView

    private var latestAccel = FloatArray(3)
    private var latestGyro = FloatArray(3)

    private val timeSeriesBuffer = mutableListOf<FloatArray>()
    private val TIMESTEPS = 160
    private val STEP_SIZE = 20

    // STATE TRACKER FOR LOGIC
    // 0 = Fall, 1 = Idle, 2 = Motion, 3 = Step
    private var lastUIState = 1

    private val FALL_IMPACT_THRESHOLD = 8.0f

    // --- FIREBASE VARIABLES ---
    private lateinit var db: FirebaseFirestore
    private var lastLoggedState = -1 // Tracks what was last sent to the database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        bufferTextView = findViewById(R.id.bufferTextView)

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()

                // NEW: Start listening for Cloud Alerts immediately
        listenForCloudAlerts()

        // NEW: Set up what happens when the user clicks the Clear button
        clearAlertButton.setOnClickListener {
            // Release the lock
            isEmergencyAlertActive = false

            // Hide the button again
            clearAlertButton.visibility = View.GONE

            // Reset the text size and show a temporary message (the sensors will overwrite this in a split second)
            statusTextView.textSize = 18f
            statusTextView.text = "Alert Cleared. Resuming detection..."
            statusTextView.setTextColor(Color.DKGRAY)
        }

        try {
            interpreter = Interpreter(loadModelFile("fall_model.tflite"))
            statusTextView.text = "Model Loaded. Gathering data..."
        } catch (e: Exception) {
            statusTextView.text = "Error: Model not found."
            statusTextView.setTextColor(Color.RED)
            e.printStackTrace()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

// ==========================================
    //  NEW: CLOUD LISTENER FUNCTION
    // ==========================================
    private fun listenForCloudAlerts() {
        db.collection("cloud_alerts")
            .whereEqualTo("targetDeviceId", "Android_Device_1")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    println(" Listen failed: $e")
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    for (document in snapshots.documentChanges) {
                        if (document.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val message = document.document.getString("message") ?: "Unknown Alert"

                            // 1. LOCK THE SCREEN
                            isEmergencyAlertActive = true

                            // 2. SHOW THE ALERT AND THE CLEAR BUTTON
                            runOnUiThread {
                                statusTextView.text = "☁️ CLOUD ALERT:\n$message"
                                statusTextView.setTextColor(Color.MAGENTA)
                                statusTextView.textSize = 28f // Make it big and obvious

                                // Make the clear button visible
                                clearAlertButton.visibility = View.VISIBLE
                            }

                            // 3. DELETE FROM CLOUD SO IT DOESN'T RE-TRIGGER LATER
                            document.document.reference.delete()
                        }
                    }
                }
            }
    }

    
    override fun onResume() {
        super.onResume()
        linearAccelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        interpreter?.close()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || interpreter == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                latestAccel = event.values.clone()

                val combinedFeatures = floatArrayOf(
                    latestAccel[0], latestAccel[1], latestAccel[2],
                    latestGyro[0], latestGyro[1], latestGyro[2]
                )

                timeSeriesBuffer.add(combinedFeatures)

                if (timeSeriesBuffer.size >= TIMESTEPS) {

                    if (isPhoneStill(timeSeriesBuffer)) {
                        lastUIState = 1 // Update state tracker
                        setUIState(1)
                    } else {
                        runInference(timeSeriesBuffer.take(TIMESTEPS))
                    }

                    timeSeriesBuffer.subList(0, STEP_SIZE).clear()
                } else {
                    bufferTextView.text = "Filling Buffer: ${timeSeriesBuffer.size}/$TIMESTEPS"
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                latestGyro = event.values.clone()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun isPhoneStill(buffer: List<FloatArray>): Boolean {
        val recentReadings = buffer.takeLast(20)

        for (reading in recentReadings) {
            val accelSum = abs(reading[0]) + abs(reading[1]) + abs(reading[2])
            val gyroSum = abs(reading[3]) + abs(reading[4]) + abs(reading[5])

            if (accelSum > 1.0f || gyroSum > 0.5f) {
                return false
            }
        }
        return true
    }

    private fun normalizeFixed(value: Float, min: Float, max: Float): Float {
        var scaled = (value - min) / (max - min)
        if (scaled < 0.0f) return 0.0f
        if (scaled > 1.0f) return 1.0f
        return scaled
    }

    private fun runInference(sensorData: List<FloatArray>) {
        val input = Array(1) { Array(TIMESTEPS) { FloatArray(6) } }
        var maxPhysicalImpact = 0.0f

        for (row in 0 until TIMESTEPS) {
            val accelX = sensorData[row][0]
            val accelY = sensorData[row][1]
            val accelZ = sensorData[row][2]

            val currentImpact = sqrt((accelX * accelX + accelY * accelY + accelZ * accelZ).toDouble()).toFloat()
            if (currentImpact > maxPhysicalImpact) {
                maxPhysicalImpact = currentImpact
            }

            input[0][row][0] = normalizeFixed(accelX, -20f, 20f)
            input[0][row][1] = normalizeFixed(accelY, -20f, 20f)
            input[0][row][2] = normalizeFixed(accelZ, -20f, 20f)
            input[0][row][3] = normalizeFixed(sensorData[row][3], -10f, 10f)
            input[0][row][4] = normalizeFixed(sensorData[row][4], -10f, 10f)
            input[0][row][5] = normalizeFixed(sensorData[row][5], -10f, 10f)
        }

        val output = Array(1) { FloatArray(4) }

        try {
            interpreter?.run(input, output)
        } catch (e: Exception) { return }

        val probabilities = output[0]
        var maxIndex = 0
        var maxProb = probabilities[0]

        for (i in 1..3) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i]
                maxIndex = i
            }
        }

        // ==========================================
        //  FAILSAFE 2: THE PHYSICAL REALITY CHECK
        // ==========================================
        if (maxIndex == 0 && maxPhysicalImpact < FALL_IMPACT_THRESHOLD) {
            maxIndex = 2
        }

        // ==========================================
        //  FAILSAFE 3: YOUR STATE TRANSITION LOGIC
        // ==========================================
        if (maxIndex == 0 && lastUIState == 1) {
            maxIndex = 2
        }

        lastUIState = maxIndex

        runOnUiThread {
            bufferTextView.text = String.format("Confidences:\nFall: %.2f  Idle: %.2f\nMotion: %.2f  Step: %.2f\nMax Impact: %.2f",
                probabilities[0], probabilities[1], probabilities[2], probabilities[3], maxPhysicalImpact)
        }

        setUIState(maxIndex)
    }

    private fun setUIState(stateIndex: Int) {
        // Map the integer to the actual string name
        val activityName = when (stateIndex) {
            0 -> "Fall Detected"
            1 -> "Idle"
            2 -> "In Motion"
            3 -> "Stepping"
            else -> "Unknown"
        }

        
        // ONLY send data to Firebase if it is a Fall (0)
        // The lastLoggedState check ensures we don't spam the database if the fall lasts for a few frames
        if (stateIndex == 0 && lastLoggedState != 0) {
            saveToFirebase(activityName)
        }

        // Always update lastLoggedState so we know what the previous state was
        lastLoggedState = stateIndex

        runOnUiThread {
            when (stateIndex) {
                0 -> {
                    statusTextView.text = "🚨 FALL DETECTED 🚨"
                    statusTextView.setTextColor(Color.RED)
                }
                1 -> {
                    statusTextView.text = "Idle"
                    statusTextView.setTextColor(Color.DKGRAY)
                }
                2 -> {
                    statusTextView.text = "In Motion"
                    statusTextView.setTextColor(Color.BLUE)
                }
                3 -> {
                    statusTextView.text = "Stepping"
                    statusTextView.setTextColor(Color.parseColor("#008000"))
                }
            }
        }
    }

    // Helper function to push data to Firestore
    private fun saveToFirebase(activity: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        val timestamp = sdf.format(Date())

        val activityData = hashMapOf(
            "activity" to activity,
            "timestamp" to timestamp,
            "deviceId" to "Android_Device_1",
            "isAlert" to (activity == "Fall Detected")
        )

        db.collection("activity_logs")
            .add(activityData)
            .addOnSuccessListener { documentReference ->
                println("✅ DocumentSnapshot added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                println("❌ Error adding document: $e")
            }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fd = assets.openFd(modelPath)
        return FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }
}
