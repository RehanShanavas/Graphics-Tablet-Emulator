package com.example.graphictabletemu

import android.os.*
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.OutputStream
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private lateinit var calibrationFrame: FrameLayout
    private lateinit var burgerButton: ImageButton
    private lateinit var burgerConstraintLayout : ConstraintLayout
    private lateinit var calibrateButton: ImageButton

    private val IP_ADDRESS = "127.0.0.1"
    private val ANDROID_PORT = 7000

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    private lateinit var socketWriterThread: HandlerThread
    private lateinit var writerHandler: Handler

    private var calibrationMode: Boolean = false
    private var lastX1: Int = 0
    private var lastY1: Int = 0
    private var lastX2: Int = 0
    private var lastY2: Int = 0

    fun initializeUIBehaviour(){
        burgerConstraintLayout.visibility = View.GONE
        var visible = false
        burgerButton.setOnClickListener{
            Log.d("MainActivity", "Burger button clicked!")
            burgerConstraintLayout.visibility = View.VISIBLE
            if (!visible) {
                burgerConstraintLayout.visibility = View.VISIBLE
                visible = true
            } else {
                burgerConstraintLayout.visibility = View.GONE
                visible = false
            }
        }

        var calibrationMode = false
        calibrateButton.setOnClickListener{
            calibrationMode = !calibrationMode
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        burgerButton = findViewById(R.id.btn_burger)
        burgerConstraintLayout = findViewById(R.id.cl_burger)
        calibrationFrame = findViewById(R.id.frame_calibration)
        calibrateButton = findViewById(R.id.btn_calibrate)
        initializeUIBehaviour()

        // Start socket writer thread
        socketWriterThread = HandlerThread("SocketWriter")
        socketWriterThread.start()
        writerHandler = Handler(socketWriterThread.looper)

        // Connect socket on background thread
        Thread {
            var attempts = 0
            while (attempts < 5) {
                try {
                    Log.d("SOCKET_TEST", "Connecting to $IP_ADDRESS:$ANDROID_PORT (try ${attempts + 1})")
                    socket = Socket(IP_ADDRESS, ANDROID_PORT)
                    outputStream = socket?.getOutputStream()
                    Log.d("SOCKET_TEST", "Connection established!")
                    break
                } catch (e: Exception) {
                    Log.e("SOCKET_TEST", "Connection failed: ${e.message}")
                    Thread.sleep(500)
                    attempts++
                }
            }
            if (socket == null) {
                Log.e("SOCKET_TEST", "Could not connect after all attempts.")
            }
        }.start()
    }

    // ON TOUCH EVENT
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
            val x = event.x
            val y = event.y
            val pressure = event.pressure

            Log.d("TOUCH_EVENT", "x: $x, y: $y, pressure: $pressure")

            val axisTilt = event.getAxisValue(MotionEvent.AXIS_TILT)
            val orientation = event.orientation

            val data = "%.1f,%.1f,%.2f,%.3f,%.3f\n".format(x, y, pressure,axisTilt,orientation)

            writerHandler.post {
                try {
                    outputStream?.write(data.toByteArray())
                } catch (e: Exception) {
                    Log.e("SOCKET_TEST", "Touch send failed: ${e.message}")
                }
            }
        }
        return true
    }

    // ON HOVER EVENT
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS && event.action == MotionEvent.ACTION_HOVER_MOVE) {
            val x = event.x
            val y = event.y
            val pressure = -1.0

            val axisTilt = event.getAxisValue(MotionEvent.AXIS_TILT)
            val orientation = event.orientation

            val data = "%.1f,%.1f,%.2f,%.3f,%.3f\n".format(x, y, pressure,axisTilt,orientation)

            writerHandler.post {
                try {
                    outputStream?.write(data.toByteArray())
                } catch (e: Exception) {
                    Log.e("SOCKET_TEST", "Hover send failed: ${e.message}")
                }
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        writerHandler.removeCallbacksAndMessages(null)
        socketWriterThread.quitSafely()
        socket?.close()
    }

}