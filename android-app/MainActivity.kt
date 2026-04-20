package com.example.falldetectionapp

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.Interpreter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
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

    // 0 = Fall, 1 = Idle, 2 = Motion, 3 = Step
    private var lastUIState = 1

    private val FALL_IMPACT_THRESHOLD = 8.0f

    // Prevent repeated backend alerts for the same fall event
    private var lastAlertTime = 0L
    private val ALERT_COOLDOWN_MS = 10000L // 10 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        bufferTextView = findViewById(R.id.bufferTextView)

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

    override fun onResume() {
        super.onResume()
        linearAccelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
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
                        lastUIState = 1
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
        val scaled = (value - min) / (max - min)
        return when {
            scaled < 0.0f -> 0.0f
            scaled > 1.0f -> 1.0f
            else -> scaled
        }
    }

    private fun runInference(sensorData: List<FloatArray>) {
        val input = Array(1) { Array(TIMESTEPS) { FloatArray(6) } }
        var maxPhysicalImpact = 0.0f

        for (row in 0 until TIMESTEPS) {
            val accelX = sensorData[row][0]
            val accelY = sensorData[row][1]
            val accelZ = sensorData[row][2]

            val currentImpact = sqrt(
                (accelX * accelX + accelY * accelY + accelZ * accelZ).toDouble()
            ).toFloat()

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
        } catch (e: Exception) {
            Log.e("TFLITE", "Inference failed: ${e.message}")
            return
        }

        val probabilities = output[0]
        var maxIndex = 0
        var maxProb = probabilities[0]

        for (i in 1..3) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i]
                maxIndex = i
            }
        }

        // Failsafe 2: physical reality check
        if (maxIndex == 0 && maxPhysicalImpact < FALL_IMPACT_THRESHOLD) {
            maxIndex = 2
        }

        // Failsafe 3: prevent sudden Idle -> Fall jump
        if (maxIndex == 0 && lastUIState == 1) {
            maxIndex = 2
        }

        lastUIState = maxIndex

        runOnUiThread {
            bufferTextView.text = String.format(
                "Confidences:\nFall: %.2f  Idle: %.2f\nMotion: %.2f  Step: %.2f\nMax Impact: %.2f",
                probabilities[0], probabilities[1], probabilities[2], probabilities[3], maxPhysicalImpact
            )
        }

        // Send alert only if final state remains Fall
        if (maxIndex == 0) {
            val now = System.currentTimeMillis()
            if (now - lastAlertTime > ALERT_COOLDOWN_MS) {
                sendFallAlert(maxProb.toDouble())
                lastAlertTime = now
            }
        }

        setUIState(maxIndex)
    }

    private fun setUIState(stateIndex: Int) {
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

    private fun sendFallAlert(confidence: Double) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            .format(Date())

        val alert = AlertRequest(
            userId = "U001",
            eventType = "Fall Detected",
            status = "Critical",
            timestamp = timestamp,
            confidenceScore = confidence
        )

        RetrofitClient.apiService.sendAlert(alert)
            .enqueue(object : Callback<Map<String, Any>> {
                override fun onResponse(
                    call: Call<Map<String, Any>>,
                    response: Response<Map<String, Any>>
                ) {
                    if (response.isSuccessful) {
                        Log.d("API", "Alert sent successfully: ${response.body()}")
                    } else {
                        Log.e("API", "Failed to send alert: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                    Log.e("API", "Network error: ${t.message}")
                }
            })
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fd = assets.openFd(modelPath)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }
}