package com.example.gpstracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileWriter

class MainActivity : ComponentActivity() {
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private lateinit var bitmap: Bitmap
    private lateinit var canvas: Canvas
    private lateinit var linePaint: Paint
    private lateinit var pointPaint: Paint

    private var minX = Float.MAX_VALUE
    private var maxX = Float.MIN_VALUE
    private var minY = Float.MAX_VALUE
    private var maxY = Float.MIN_VALUE
    private var isFirstPoint = true
    private var isDataLoaded = false

    private val gpsData = mutableListOf<GpsData>()

    var currentBitmap by mutableStateOf<Bitmap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bitmap = Bitmap.createBitmap(2000, 3555, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)

        linePaint = Paint().apply {
            color = Color.RED
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        pointPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 10f
            isAntiAlias = true
        }

        canvas.drawColor(Color.WHITE)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handleLocationUpdate(location)
            }

            override fun onProviderDisabled(provider: String) {
                Log.d("GPS Tracking", "Provider disabled: $provider")
            }

            override fun onProviderEnabled(provider: String) {
                Log.d("GPS Tracking", "Provider enabled: $provider")
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                Log.d("GPS Tracking", "Provider status changed: $provider, status: $status")
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            startLocationUpdates()
        }

        // Min/Max-Werte initialisieren
        minX = Float.MAX_VALUE
        maxX = Float.MIN_VALUE
        minY = Float.MAX_VALUE
        maxY = Float.MIN_VALUE
        isFirstPoint = true
        isDataLoaded = false

        setContent {
            GpstrackingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GPSViewScreen(this@MainActivity)
                }
            }
        }


        if (gpsData.isNotEmpty()) {
            minX = gpsData.minOf { it.longitude.toFloat() }
            maxX = gpsData.maxOf { it.longitude.toFloat() }
            minY = gpsData.minOf { it.latitude.toFloat() }
            maxY = gpsData.maxOf { it.latitude.toFloat() }
            isDataLoaded = true
        }

        redrawCanvas(bitmap.width.toFloat(), bitmap.height.toFloat())
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Lifecycle", "onDestroy called")
        locationManager.removeUpdates(locationListener)
    }

    override fun onPause() {
        super.onPause()
        Log.d("Lifecycle", "onPause called")
        saveGpsDataToCsv()
    }

    override fun onResume() {
        super.onResume()
        loadGpsDataFromCsv()
    }

    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0,
                0f,
                locationListener
            )
        } catch (e: SecurityException) {
            Log.e("GPS Tracking", "Security exception: ${e.message}")
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val x = location.longitude.toFloat()
        val y = location.latitude.toFloat()

        if (!isDataLoaded) {
            if (isFirstPoint) {
                minX = x
                maxX = x
                minY = y
                maxY = y
                isFirstPoint = false
            } else {
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
            }
        } else {
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        gpsData.add(GpsData(timestamp, location.longitude, location.latitude, location.altitude))

        redrawCanvas(bitmap.width.toFloat(), bitmap.height.toFloat())
    }

    private fun redrawCanvas(canvasWidth: Float, canvasHeight: Float) {
        canvas.drawColor(Color.WHITE)

        val width = canvasWidth // Verwende canvasWidth statt bitmap.width
        val height = canvasHeight // Verwende canvasHeight statt bitmap.height
        val padding = 50f

        val rangeX = (maxX - minX).coerceAtLeast(0.0001f)
        val rangeY = (maxY - minY).coerceAtLeast(0.0001f)

        val scaleX = (width - 2 * padding) / rangeX
        val scaleY = (height - 2 * padding) / rangeY

        val scale = minOf(scaleX, scaleY)

        val offsetX = (width - (rangeX * scale)) / 2
        val offsetY = (height - (rangeY * scale)) / 2

        var isFirst = true
        var lastX = 0f
        var lastY = 0f

        for (data in gpsData) {
            val currentX = offsetX + (data.longitude.toFloat() - minX) * scale
            val currentY = height - (offsetY + (data.latitude.toFloat() - minY) * scale)

            if (isFirst) {
                isFirst = false
            } else {
                canvas.drawLine(lastX, lastY, currentX, currentY, linePaint)
            }
            canvas.drawCircle(currentX, currentY, 10f, pointPaint)

            lastX = currentX
            lastY = currentY
        }

        currentBitmap = bitmap.config?.let { bitmap.copy(it, true) }
    }

    private fun saveGpsDataToCsv() {
        Log.d("GPS Tracking", "Saving GPS data to CSV")
        val csvWriter: FileWriter
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, "gps_data.txt")
        csvWriter = FileWriter(file)
        csvWriter.append("time, long, lat, alt\n")
        for (data in gpsData) {
            Log.d("GPS Tracking", "Saving data: ${data.timestamp}, ${data.longitude}, ${data.latitude}, ${data.altitude}")
            csvWriter.append("${data.timestamp}, ${data.longitude}, ${data.latitude}, ${data.altitude}\n")
        }
        csvWriter.flush()
        csvWriter.close()
        Log.d("GPS Tracking", "Saved GPS data to ${file.absolutePath}")
    }

    fun loadGpsDataFromCsv() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, "gps_data.txt")
        if (!file.exists()) {
            Log.e("GPS Tracking", "CSV file does not exist")
            return
        }
        Log.d("GPS Tracking", "Loading CSV data from ${file.absolutePath}")
        val lines = file.readLines()
        gpsData.clear()
        Log.d("GPS Tracking", "Loaded ${lines.size} lines from CSV")
        for (line in lines.subList(1, lines.size)) {
            val parts = line.split(",")
            if (parts.size == 4) {
                val timestamp = parts[0]
                val longitude = parts[1].toDouble()
                val latitude = parts[2].toDouble()
                val altitude = parts[3].toDouble()
                gpsData.add(GpsData(timestamp, longitude, latitude, altitude))
            }
        }

        // Min/Max-Werte aktualisieren und isDataLoaded setzen
        if (gpsData.isNotEmpty()) {
            minX = gpsData.minOf { it.longitude }.toFloat()
            maxX = gpsData.maxOf { it.longitude }.toFloat()
            minY = gpsData.minOf { it.latitude }.toFloat()
            maxY = gpsData.maxOf { it.latitude }.toFloat()
            isDataLoaded = true
            redrawCanvas(bitmap.width.toFloat(), bitmap.height.toFloat()) // Canvas neu zeichnen
        }
    }

    fun resetData() {
        gpsData.clear()
        isFirstPoint = true
        minX = Float.MAX_VALUE
        maxX = Float.MIN_VALUE
        minY = Float.MAX_VALUE
        maxY = Float.MIN_VALUE
        isDataLoaded = false // isDataLoaded auf false setzen
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "gps_data.csv")
        if (file.exists()) {
            file.delete()
        }
        redrawCanvas(bitmap.width.toFloat(), bitmap.height.toFloat())
    }

    data class GpsData(
        val timestamp: String,
        val longitude: Double,
        val latitude: Double,
        val altitude: Double
    )

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
    }
}

@Composable
fun GPSViewScreen(mainActivity: MainActivity) {
    GPSView(mainActivity.currentBitmap, mainActivity)

    LaunchedEffect(Unit) {
        mainActivity.loadGpsDataFromCsv()
    }
}

@Composable
fun GPSView(bitmap: Bitmap?, mainActivity: MainActivity) {
    val rememberedBitmap = remember { mutableStateOf(bitmap) }

    LaunchedEffect(bitmap) {
        rememberedBitmap.value = bitmap
    }

    Column(modifier = Modifier.fillMaxSize()) {
        rememberedBitmap.value?.let { currentBitmap ->
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        setImageBitmap(currentBitmap)
                        scaleType = ImageView.ScaleType.FIT_XY
                    }
                },
                update = { imageView ->
                    imageView.setImageBitmap(currentBitmap)
                },
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = {
                CoroutineScope(Dispatchers.Main).launch {
                    mainActivity.resetData()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Reset")
        }
    }
}

@Composable
fun GpstrackingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}