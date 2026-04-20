1D CNN MODEL that processes time-series sensor data from:
Accelerometer (acc)
Gyroscope (gyro)

Originally:960 features per sample

reshaped it into:

(160 timesteps, 6 features)
 Meaning:
160 time steps (sequence length)

6 sensor channels:

acc_x, acc_y, acc_z

gyro_x, gyro_y, gyro_z

This is why CNN works — it detects patterns over time

Test Accuracy ≈ 90.3%

converted into TensorFlow Lite format for deployment on an Android-based edge device.

