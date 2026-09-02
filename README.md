<p align="center">
  <img src="KitsuneBoot.jpg" alt="Kitsune Boot Logo" width="160" style="border-radius: 20%;" />
</p>

<h1 align="center">Kitsune Boot 🦊</h1>

<p align="center">
  <strong>Modern Boot Animation and Root Module Creator & Converter for Android</strong>
</p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg?style=flat-square" alt="License: GPL v3" /></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android-green.svg?style=flat-square" alt="Platform" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0-purple.svg?style=flat-square" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4.svg?style=flat-square" alt="Jetpack Compose" /></a>
  <img src="https://img.shields.io/badge/Min%20SDK-29%20(Android%2010%2B)-orange.svg?style=flat-square" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target%20SDK-37-brightgreen.svg?style=flat-square" alt="Target SDK" />
</p>

---

## 📖 Overview

**Kitsune Boot** is an intuitive, high-performance native Android application designed to convert any video file into customized Android boot animations (`bootanimation.zip`). In addition to generating conventional zip archives, the app packages ready-to-flash modules for modern root managers such as **Magisk**, **KernelSU**, and **APatch**.

Built with **Jetpack Compose** following **Material Design 3** guidelines, Kitsune Boot features advanced capabilities such as GPU hardware-accelerated decoding, Out of Memory (OOM) risk prevention, existing zip inspection, and fine-grained control over resolution and frame rates.

---

## ✨ Key Features

### 🎬 Video Processing & Editing
- **Multi-Format Support:** Import videos in `MP4`, `MKV`, `WebM`, and other Android-compatible formats.
- **Precision Trimming:** Define exact start and end timestamps to export only the desired segment.
- **Video Rotation:** Adjust video orientation with support for 0°, 90°, 180°, and 270° rotation.
- **Automatic FPS Detection:** Accurately reads the original frame rate of the file with custom adjustment options (1 to 120 FPS).

### 📐 Resolution & Display Adjustments
- **Popular Presets:** Select common screen resolutions with a single tap (`FHD+ 1080x2400`, `2K+ 1440x3200`, `FHD 1080x1920`, `HD 720x1280`, `Square 1080x1080`).
- **Automatic Screen Detection:** Automatically detect and apply your device's native display resolution.
- **Frame vs. Canvas Resolution Control:** Scale down frame extraction (from 10% to 100% or manual pixel dimensions) to save storage and RAM without altering the canvas display size defined in `desc.txt`.
- **Scaling Modes:** Choose between **Fit Center** (with letterboxing), **Center Crop** (full-bleed crop), and **Stretch**.

### ⚡ Extreme Performance & Hardware Acceleration
- **GPU / MediaCodec Pipeline:** Ultra-fast decoding and rendering utilizing hardware acceleration via `MediaCodec` and EGL/OpenGL surfaces.
- **Intelligent Fallback:** Automatic multithreaded CPU parallel decoding fallback if hardware acceleration encounters video codec limitations.

### 🔁 Multi-Part Animations
- **`part0` and `part1` Partitions:** Create animations with a single intro playback followed by a continuous loop.
- **Loop & Pause Controls:** Configure repetitions and pause delay frames for each part independently.
- **Partition Types:** Support for Android boot animation instruction types (`p` for standard interruptible playback and `c` for complete cycle playback).

### 🎨 Image Formats & Background Colors
- **PNG & JPEG Formats:** Export in lossless **PNG** for visual fidelity or **JPEG** with dynamic compression quality control.
- **Color Picker:** Set the background (letterboxing) color using quick swatches or any custom **HEX** code.

### 📦 Root Modules (Magisk / KernelSU / APatch)
- **Instant Packaging:** Generate a flashable `.zip` module structured with appropriate installation scripts for a clean system boot animation replacement.
- **Custom Metadata:** Configure module name, author, and version before export.

### 🔍 Boot Animation Inspector & Preview
- **ZIP Inspector:** Open any existing `bootanimation.zip` file to inspect `desc.txt` parameters (resolution, FPS, folder structure) and import settings with a single tap.
- **Live Preview:** View an animated sample of the generated frames before full processing.

### 🛡️ OOM (Out of Memory) Protection
- **Real-Time Estimation:** Dynamic calculation of total frame count and approximate final archive size.
- **Risk Alert:** Preventive warning if the estimated size exceeds the 90 MB safety limit, preventing *bootloops* caused by excessive memory consumption during Android boot.

---

## 🚀 How to Use

1. **Choose Video:** Tap *Choose Video File* and select a video from your gallery or storage.
2. **Trim & Orient:** Use the range slider to set start and end points, and adjust rotation if needed.
3. **Configure Resolution:** Select a preset matching your device screen or use the auto-detect button.
4. **Set Parameters:** Choose target FPS, image format (PNG/JPEG), and background color.
5. **(Optional) Multi-Part & Root Module:**
   - Enable *Multi-Part Animation* to set intro and loop sections.
   - Check *Magisk / KernelSU Root Module* to create a flashable zip for Magisk/KernelSU/APatch.
6. **Generate:** Tap *Generate bootanimation.zip* (or *Generate Magisk Module*) and choose where to save the file.

---

## 🛠️ Technologies & Architecture

The project is built following modern Android best practices:

- **Language:** [Kotlin](https://kotlinlang.org/) 2.0+
- **Declarative UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with **Material Design 3**
- **Architecture:** MVVM with Kotlin Coroutines and `StateFlow`
- **Media Processing:** `MediaCodec`, `MediaMetadataRetriever`, EGL / OpenGL ES
- **File Management:** Android Storage Access Framework (SAF) and zero-allocation streaming ZIP packaging
- **Testing:** JUnit 4, AndroidX Test Runner, and Espresso

---

## 💻 Building from Source

### Prerequisites
- **Android Studio** (Ladybug / Meerkat version or higher)
- **JDK 21**
- **Android SDK** (API Level 37)

### Command-Line Build (Gradle)

Clone the repository:
```bash
git clone https://github.com/Yervant7/KitsuneBoot.git
cd KitsuneBoot
```

Build **Debug** APK:
```bash
./gradlew assembleDebug
```

Run unit tests:
```bash
./gradlew test
```

Build optimized **Release** APK:
```bash
./gradlew assembleRelease
```

---

## 🎨 Icon & Copyright Notice

> [!NOTE]
> **Tribute / Fan Art:**  
> The character illustrated in the application icon is a personal artistic tribute to the character **HSIN** (*心*) from the game **Wuthering Waves**, created by the author as a fan of the game.  
> 
> This project is free and open-source software, having no official affiliation, sponsorship, endorsement, or commercial association with the game **Wuthering Waves** or its developer and publisher, **Kuro Games**. All copyrights, trademarks, and intellectual properties of the game belong to their respective owners.

---

## 📄 License

This project is distributed under the **GNU General Public License v3.0** (GPL-3.0-or-later).

```text
Kitsune Boot - Boot Animation Maker for Android
Copyright (C) 2026 Yervant

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

See the [LICENSE](LICENSE) file for the full license text.
