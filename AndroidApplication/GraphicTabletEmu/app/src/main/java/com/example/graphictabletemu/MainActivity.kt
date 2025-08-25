package com.example.graphictabletemu

import android.annotation.SuppressLint
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.example.graphictabletemu.Datastore.DataKeyValueStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private val IP_ADDRESS = "127.0.0.1"
    private val ANDROID_PORT = 7000

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    private lateinit var socketWriterThread: HandlerThread
    private lateinit var writerHandler: Handler

    private lateinit var store: DataKeyValueStore

    var isCalibrating = false
    var aspectRatioValue : Float = 1280f/720
    var scaleValue : Int = 100
    var aspectRatioPrevious : Float = 1280f/720
    var scalePrevious : Int = 100
    var ignoreFingerValue: Boolean = false

    private lateinit var btnScreen : Button
    private lateinit var btnCalibrate : Button
    private lateinit var btnSettings : Button
    private lateinit var mainFrame : FrameLayout
    private lateinit var frameScreen : FrameLayout
    private lateinit var frameCalibrate : FrameLayout
    private var frameEmulated: FrameLayout? = null

    private fun initializeUIBehaviour(){
        var currentActiveButton : Button? = null
        var calibrate_buttons_frame : LinearLayout = findViewById(R.id.calibrate_buttons_frame)
        var btnCalibrateRevert : Button = findViewById(R.id.btn_calibrate_revert)
        var btnCalibrateSave : Button = findViewById(R.id.btn_calibrate_save)
        var edittextCalibrateAspectRatio : EditText = findViewById(R.id.edittext_calibrate_aspect_ratio)
        var edittextCalibrateScale : EditText = findViewById(R.id.edittext_calibrate_scale)
        var textCalibrateAspectRatio : TextView = findViewById(R.id.text_calibrate_aspect_ratio)
        var textCalibrateScale : TextView = findViewById(R.id.text_calibrate_scale)

        fun setCalibrateAspectRatioText(aspectRatioString: String) {
            val aspectRatioFloat = aspectRatioString.toFloatOrNull()
            var formattedRatio = "INVALID INPUT"
            if (aspectRatioFloat != null) {
                formattedRatio = String.format("%.2f", aspectRatioFloat)
            }
            val aspectRatioText = "ASPECT RATIO : $formattedRatio [ enter as : WIDTH HEIGHT ]"
            textCalibrateAspectRatio.text = aspectRatioText


        }

        fun setCalibrateScaleText(scaleString : String){
            var scaleStringValue = scaleString
            if (scaleString.length > 3){
                scaleStringValue = scaleString.substring(0,3)
            }
            val scaleText = "SCALE : $scaleString [ enter as : integer between 1 to 100 ]"
            textCalibrateScale.setText(scaleText)
        }

        fun activateButton(button: Button){
            // Change button color
            if (currentActiveButton != null && currentActiveButton != button) {
                currentActiveButton?.backgroundTintList = null
                currentActiveButton?.setTextColor(getColor(R.color.primary))
            }
            button.backgroundTintList = getColorStateList(R.color.primary)
            button.setTextColor(getColor(R.color.black))
            currentActiveButton = button

            // Button behaviour
            if (button == btnScreen){
                frameCalibrate.setVisibility(View.GONE)
                frameScreen.setVisibility(View.VISIBLE)
            }
            else if (button == btnCalibrate){
                frameScreen.setVisibility(View.GONE)
                frameCalibrate.setVisibility(View.VISIBLE)


                // Handle calibrate button click
                isCalibrating = true
                calibrate_buttons_frame.setVisibility(View.VISIBLE)
                setCalibrateAspectRatioText(aspectRatioValue.toString())
                setCalibrateScaleText(scaleValue.toString())
            }
            else if (button == btnSettings){
                frameScreen.setVisibility(View.GONE)
                frameCalibrate.setVisibility(View.GONE)
            }
        }
        activateButton(btnScreen)

        btnScreen.setOnClickListener {
            activateButton(btnScreen)
        }

        btnCalibrate.setOnClickListener {
            activateButton(btnCalibrate)
        }
        fun setFrameCalibrate(aspectRatio : Float, scale : Int, saveToDatastore : Boolean){
            Log.d("TEST", scale.toString())

            if (saveToDatastore){
                lifecycleScope.launch {
                    store.setAspectRatio(aspectRatio)
                    store.setFrameScale(scale)
                }
            }

            val baseWidth = mainFrame.width
            val baseHeight = mainFrame.height

            // Scale by percentage
            var targetWidth = baseWidth * scale / 100
            var targetHeight = baseHeight * scale / 100


            // Apply aspect ratio adjustment
            val currentRatio = targetWidth.toFloat() / targetHeight.toFloat()

            if (currentRatio > aspectRatio) {
                // Too wide, reduce width
                targetWidth = (targetHeight * aspectRatio).toInt()
            } else {
                // Too tall, reduce height
                targetHeight = (targetWidth / aspectRatio).toInt()
            }

            val layoutParams = FrameLayout.LayoutParams(targetWidth, targetHeight)
            layoutParams.gravity = Gravity.CENTER  // center in parent
            frameEmulated?.layoutParams = layoutParams
        }

        btnCalibrateSave.setOnClickListener {
            isCalibrating = false
            setFrameCalibrate(aspectRatioValue,scaleValue,true)
            calibrate_buttons_frame.setVisibility(View.GONE)
            activateButton(btnScreen)
        }
        btnCalibrateRevert.setOnClickListener {
            isCalibrating = false
            aspectRatioValue = aspectRatioPrevious
            scaleValue = scalePrevious
            setFrameCalibrate(aspectRatioPrevious,scalePrevious,false)
            calibrate_buttons_frame.setVisibility(View.GONE)
            activateButton(btnScreen)
        }

        fun handleAspectRatio(text : String){
            val aspectRatio = text.split(" ")
            if (aspectRatio.size == 2) {
                // check if both values are numbers
                val width = aspectRatio[0].toFloatOrNull()
                val height = aspectRatio[1].toFloatOrNull()
                if (width != null && height != null) {
                    aspectRatioValue = (width / height)
                    edittextCalibrateAspectRatio.setText(text)
                    setFrameCalibrate(aspectRatioValue,scaleValue,false)
                    setCalibrateAspectRatioText(aspectRatioValue.toString())
                }
                else {
                    edittextCalibrateAspectRatio.setText("")
                    setCalibrateAspectRatioText("INVALID INPUT")
                }
            }
            else {
                edittextCalibrateAspectRatio.setText("")
                setCalibrateAspectRatioText("INVALID INPUT")
            }
        }
        edittextCalibrateAspectRatio.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && isCalibrating) {
                val text = edittextCalibrateAspectRatio.text.toString()
                handleAspectRatio(text)
            }
        }
        edittextCalibrateAspectRatio.setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                val text = edittextCalibrateAspectRatio.text.toString()
                handleAspectRatio(text)
                true
            } else {
                false
            }

        }

        fun handleScale(text : String){
            val scale = text.toIntOrNull()
            if (scale != null && scale > 0 && scale <= 100) {
                scaleValue = scale
                edittextCalibrateScale.setText(text)
                setFrameCalibrate(aspectRatioValue,scaleValue,false)
                setCalibrateScaleText(scaleValue.toString())
            }
            else {
                edittextCalibrateScale.setText("")
                setCalibrateScaleText("INVALID INPUT")
            }
        }
        edittextCalibrateScale.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && isCalibrating) {
                val text = edittextCalibrateScale.text.toString()
                handleScale(text)
            }
        }
        edittextCalibrateScale.setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                val text = edittextCalibrateScale.text.toString()
                handleScale(text)
                true
            } else {
                false
            }
        }

        mainFrame.post{
            setFrameCalibrate(aspectRatioValue,scaleValue,false)

            lifecycleScope.launch {
                store.aspectRatio.collectLatest { ratio ->
                    if (ratio != null) {
                        aspectRatioValue = ratio
                        aspectRatioPrevious = ratio
                        setFrameCalibrate(aspectRatioValue,scaleValue,false)
                    }
                }
            }
            lifecycleScope.launch {
                store.frameScale.collectLatest { scale ->
                    if (scale != null) {
                        scaleValue = scale
                        scalePrevious = scale
                        setFrameCalibrate(aspectRatioValue,scaleValue,false)
                    }
                }
            }
            lifecycleScope.launch {
                store.ignoreFinger.collectLatest { ignore ->
                    if (ignore != null) {
                        ignoreFingerValue = ignore
                        setFrameCalibrate(aspectRatioValue,scaleValue,false)
                    }
                }
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        btnScreen = findViewById(R.id.btn_screen)
        btnCalibrate = findViewById(R.id.btn_calibrate)
        btnSettings = findViewById(R.id.btn_settings)
        mainFrame = findViewById(R.id.main_frame)
        frameScreen = findViewById(R.id.frame_screen)
        frameCalibrate = findViewById(R.id.frame_calibrate)
        frameEmulated = findViewById(R.id.emulated_screen)

        // read datastore values
        store = DataKeyValueStore(this)

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
            if (frameEmulated == null) {
                return false
            }

            val rawX = event.x
            val rawY = event.y

            // check if x and y within emulated_frame
            val frameLocation = IntArray(2)
            frameEmulated!!.getLocationOnScreen(frameLocation)
            val frameLeft = frameLocation[0]
            val frameTop = frameLocation[1]
            val frameWidth = frameEmulated!!.width.toFloat()
            val frameHeight = frameEmulated!!.height.toFloat()

            val localX = rawX - frameLeft
            val localY = rawY - frameTop

            if (localX in 0f..frameWidth && localY in 0f..frameHeight) {
                val fractionX = localX / frameWidth
                val fractionY = localY / frameHeight
                val pressure = event.pressure
                val axisTilt = event.getAxisValue(MotionEvent.AXIS_TILT)
                val orientation = event.orientation

                val data = "%.4f,%.4f,%.2f,%.3f,%.3f\n".format(
                    fractionX,
                    fractionY,
                    pressure,
                    axisTilt,
                    orientation
                )

                writerHandler.post {
                    try {
                        outputStream?.write(data.toByteArray())
                    } catch (e: Exception) {
                        Log.e("SOCKET_TEST", "Touch send failed: ${e.message}")
                    }
                }
            }
        }
        return true
    }

    // ON HOVER EVENT
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS && event.action == MotionEvent.ACTION_HOVER_MOVE) {
            if (frameEmulated == null) {
                return false
            }

            val rawX = event.x
            val rawY = event.y

            // check if x and y within emulated_frame
            val frameLocation = IntArray(2)
            frameEmulated!!.getLocationOnScreen(frameLocation)
            val frameLeft = frameLocation[0]
            val frameTop = frameLocation[1]
            val frameWidth = frameEmulated!!.width.toFloat()
            val frameHeight = frameEmulated!!.height.toFloat()

            val localX = rawX - frameLeft
            val localY = rawY - frameTop

            if (localX in 0f..frameWidth && localY in 0f..frameHeight) {
                val fractionX = localX / frameWidth
                val fractionY = localY / frameHeight
                val pressure = -1.0
                val axisTilt = event.getAxisValue(MotionEvent.AXIS_TILT)
                val orientation = event.orientation

                val data = "%.4f,%.4f,%.2f,%.3f,%.3f\n".format(
                    fractionX,
                    fractionY,
                    pressure,
                    axisTilt,
                    orientation
                )

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
        return super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        writerHandler.removeCallbacksAndMessages(null)
        socketWriterThread.quitSafely()
        socket?.close()
    }

}