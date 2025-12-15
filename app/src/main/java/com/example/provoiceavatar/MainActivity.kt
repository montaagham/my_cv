package com.example.provoiceavatar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.provoiceavatar.databinding.ActivityMainBinding
import java.io.IOException
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioVisualizer: Visualizer? = null
    private var outputFile: String? = null
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            // Permission denied
            Log.e("ProVoiceAvatar", "Audio recording permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        outputFile = "${externalCacheDir?.absolutePath}/recorded_audio.3gpp"
        
        setupUI()
        requestAudioPermission()
    }

    private fun setupUI() {
        // Set initial mouth position to closed
        binding.imgMouth.scaleY = 0.1f
        
        // Setup the record button (FAB) touch listener
        binding.fabRecord.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startRecording()
                }
                android.view.MotionEvent.ACTION_UP -> {
                    stopRecording()
                }
            }
            true
        }

        // Setup play button
        binding.btnPlay.setOnClickListener {
            playAudio()
        }

        // Setup random style button
        binding.btnRandomStyle.setOnClickListener {
            applyRandomStyle()
        }
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(outputFile)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

                prepare()
                start()
            }
            
            Log.d("ProVoiceAvatar", "Started recording")
        } catch (e: IOException) {
            Log.e("ProVoiceAvatar", "Failed to start recording: ${e.message}")
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            Log.d("ProVoiceAvatar", "Stopped recording")
        } catch (e: Exception) {
            Log.e("ProVoiceAvatar", "Failed to stop recording: ${e.message}")
        }
    }

    private fun playAudio() {
        if (outputFile == null) return
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(outputFile!!)
                
                setOnPreparedListener { mp ->
                    // Get audio session ID for visualizer
                    val sessionId = mp.audioSessionId
                    
                    if (sessionId != AudioManager.ERROR) {
                        setupVisualizer(sessionId)
                    }
                    
                    // Apply pitch shift from slider
                    val pitch = binding.voicePitchSlider.value
                    try {
                        mp.playbackParams = mp.playbackParams.setPitch(pitch)
                    } catch (e: Exception) {
                        Log.w("ProVoiceAvatar", "Could not set pitch: ${e.message}")
                    }
                    
                    mp.start()
                }
                
                setOnCompletionListener {
                    cleanupVisualizer()
                }
                
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("ProVoiceAvatar", "Failed to play audio: ${e.message}")
        }
    }

    private fun setupVisualizer(sessionId: Int) {
        audioVisualizer = Visualizer(sessionId).apply {
            // Check if the session ID is valid
            if (audioSessionId != AudioManager.ERROR) {
                // Set capture size to smaller value for better performance
                val captureSize = if (Visualizer.getCaptureSizeRange().size >= 2) {
                    Visualizer.getCaptureSizeRange()[1] // Use the larger size for more samples
                } else {
                    Visualizer.getCaptureSizeRange()[0]
                }
                
                setCaptureSize(captureSize)
                measurementMode = Visualizer.MEASUREMENT_MODE_RMS
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                
                setOnWaveFormDataCaptureListener(object : Visualizer.OnWaveFormDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        // This callback is called frequently, so we limit updates
                        waveform?.let { data ->
                            // Calculate RMS (Root Mean Square) to get loudness
                            val rms = calculateRMS(data)
                            
                            // Update mouth scale based on loudness
                            updateMouthScale(rms)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 3) // Reduce update frequency for performance
                
                // Start capturing
                setDataCaptureEnabled(true)
            }
        }
    }

    /**
     * Calculates the Root Mean Square (RMS) of audio samples to determine loudness.
     * 
     * The mathematical process:
     * 1. Normalize each byte sample from range [0, 255] to [-1, 1]
     * 2. Square each normalized sample (removes negative values and emphasizes larger values)
     * 3. Calculate the mean of all squared values (mean square)
     * 4. Take the square root of the mean square to get RMS value
     * 5. Amplify the RMS to make lip sync more responsive
     * 6. Clamp the value to [0, 1] range for consistent scaling
     */
    private fun calculateRMS(bytes: ByteArray): Float {
        var sum = 0.0
        for (byte in bytes) {
            val sample = byte.toInt() / 128.0 // Normalize to [-1, 1]
            sum += sample * sample
        }
        val meanSquare = sum / bytes.size
        val rms = sqrt(meanSquare)
        
        // Amplify the RMS value to make lip sync more responsive
        val amplifiedRMS = (rms * 3.0).toFloat() // Increase sensitivity
        
        // Clamp the value to a reasonable range
        return amplifiedRMS.coerceAtMost(1.0f)
    }

    private fun updateMouthScale(loudness: Float) {
        // Run on UI thread to update the view
        runOnUiThread {
            // Formula: mouth.scaleY = 0.1f + (loudness * sensitivity). Max scale should be 1.0f.
            val sensitivity = 0.9f // Adjust sensitivity as needed
            val newScaleY = (0.1f + (loudness * sensitivity)).coerceAtMost(1.0f)
            val newScaleX = (1.0f + (loudness * 0.1f)).coerceAtMost(1.2f) // Slight horizontal expansion
            
            // Animate the scale changes for smoother transitions
            binding.imgMouth.animate()
                .scaleY(newScaleY)
                .scaleX(newScaleX)
                .setDuration(50) // Fast animation to keep up with audio
                .start()
        }
    }

    private fun cleanupVisualizer() {
        audioVisualizer?.apply {
            if (enabled) {
                setDataCaptureEnabled(false)
            }
            release()
        }
        audioVisualizer = null
        
        // Reset mouth to closed position with smooth animation
        runOnUiThread {
            binding.imgMouth.animate()
                .scaleY(0.1f)
                .scaleX(1.0f)
                .setDuration(200) // Slightly longer duration for reset animation
                .start()
        }
    }

    private fun applyRandomStyle() {
        // Generate random colors for skin tone
        val skinColors = listOf(
            Color.rgb(255, 204, 153), // Light skin
            Color.rgb(234, 188, 142), // Medium light skin
            Color.rgb(210, 154, 98),  // Medium skin
            Color.rgb(185, 134, 73),  // Tan skin
            Color.rgb(139, 102, 59),  // Brown skin
            Color.rgb(100, 70, 40),   // Dark skin
            Color.rgb(0, 255, 0),     // Green
            Color.rgb(0, 0, 255),     // Blue
            Color.rgb(255, 0, 255),   // Purple
            Color.rgb(255, 255, 0)    // Yellow
        )
        
        val randomSkinColor = skinColors.random()
        binding.imgFace.setColorFilter(randomSkinColor)
        
        // Also generate random hair colors
        val hairColors = listOf(
            Color.rgb(139, 69, 19),   // Brown
            Color.rgb(0, 0, 0),       // Black
            Color.rgb(210, 180, 140), // Blond
            Color.rgb(160, 82, 45),   // Reddish brown
            Color.rgb(128, 0, 128),   // Purple
            Color.rgb(255, 165, 0),   // Orange
            Color.rgb(255, 255, 255)  // White/Grey
        )
        
        val randomHairColor = hairColors.random()
        binding.imgHair.setColorFilter(randomHairColor)
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up resources
        mediaRecorder?.release()
        mediaPlayer?.release()
        audioVisualizer?.release()
    }
}