# ProVoiceAvatar Android App

## Overview
ProVoiceAvatar is a single-activity Android app that allows users to record audio and have a cartoon character lip-sync in real-time based on the audio volume (amplitude), while also allowing pitch shifting.

## Tech Stack
- Language: Kotlin
- UI: XML (ConstraintLayout, Material Components)
- Features: ViewBinding, MediaRecorder, MediaPlayer, Audio Visualizer

## Features
1. **Real-time Lip Sync**: Uses Android's Visualizer API to analyze audio and animate the avatar's mouth based on volume
2. **Voice Pitch Shifting**: Allows real-time adjustment of playback pitch
3. **Interactive Recording**: Hold-to-record functionality
4. **Avatar Styling**: Random character styling with different skin and hair colors

## Architecture

### Layout (activity_main.xml)
- **Dark Theme**: Background color #121212
- **Avatar Container**: MaterialCardView with rounded corners containing:
  - imgFace (Base face)
  - imgEyes (Eyes)
  - imgMouth (Mouth - starts with scaleY="0.1")
  - imgHair (Hair)
- **Controls**:
  - Material Slider for voice pitch (0.5 to 2.0)
  - Floating Action Button for "Hold to Record"
  - Standard Button for "Play"
  - Button for "Random Style"

### Logic (MainActivity.kt)
- **ViewBinding**: Enabled and used throughout
- **Permissions**: Runtime RECORD_AUDIO permission request
- **Recording**: MediaRecorder saves to externalCacheDir
- **Playback & Pitch**: MediaPlayer with dynamic pitch adjustment
- **Lip Sync**: Visualizer class with RMS calculation for accurate loudness detection

## Mathematical Explanation: Root Mean Square (RMS) in Audio Processing

The core of the real-time lip sync functionality relies on calculating the Root Mean Square (RMS) of audio samples to determine loudness. Here's the mathematical process:

### 1. Sample Normalization
Each byte sample from the audio stream is normalized from the range [0, 255] to [-1, 1]:
```
sample = byte_value / 128.0
```

### 2. Squaring Samples
Each normalized sample is squared to remove negative values and emphasize larger amplitude values:
```
squared_sample = sample * sample
```

### 3. Mean Calculation
Calculate the average of all squared values to get the mean square:
```
mean_square = Σ(squared_samples) / N
```

### 4. Root Mean Square
Take the square root of the mean square to get the RMS value:
```
RMS = √(mean_square)
```

### 5. Sensitivity Adjustment
Amplify the RMS value to make the lip sync more responsive:
```
amplified_rms = RMS * sensitivity_factor
```

### 6. Clamping
Ensure the value stays within a reasonable range [0, 1]:
```
final_loudness = min(max(amplified_rms, 0.0), 1.0)
```

### 7. Mouth Scale Mapping
Map the loudness to the mouth scale using the formula:
```
mouth.scaleY = base_scale + (loudness * sensitivity)
mouth.scaleX = base_scale + (loudness * horizontal_sensitivity)
```

## Dependencies Used
```gradle
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.10.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
```

## Permissions
- `RECORD_AUDIO`: Required for audio recording
- `WRITE_EXTERNAL_STORAGE`: For saving recordings
- `READ_EXTERNAL_STORAGE`: For accessing saved recordings

## Key Classes
- `MediaRecorder`: Handles audio recording
- `MediaPlayer`: Handles audio playback with pitch control
- `Visualizer`: Provides real-time audio waveform data
- `ViewBinding`: Type-safe view access