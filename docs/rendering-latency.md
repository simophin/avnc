# Remote-screen rendering latency

The renderer now keeps desktop and cursor textures between redraws. It allocates
texture storage on first use, resize, or GL context recreation, and uses
`glTexSubImage2D` for subsequent pixel changes. Pointer movement, zoom, and pan
reuse the existing desktop texture when the server has not changed its pixels.
Cursor movement reuses its texture until the shape changes.

The receiver accumulates a bounding rectangle for each RFB update. After decoding,
it publishes the completed buffer by swapping two CPU buffers under a short lock,
merges pending damage, and immediately requests rendering. It then restores the
other buffer's changed pixels so the next incremental update or CopyRect has the
correct baseline. That copy can overlap GL reading the immutable published buffer.
This avoids reading framebuffer memory while the decoder writes it, including
Tight's direct writes. Full-width regions upload directly;
other regions use a reusable packed buffer because GLES 2 has no unpack row
length. Initial upload and context recreation always upload the entire snapshot.

A 1920×1080 desktop previously transferred 8,294,400 bytes on every redraw.
An unchanged redraw now transfers zero desktop pixels; an isolated 32×32 update
transfers 4,096 bytes. Widely separated changes can produce a large bounding box.

The synchronization tradeoff is an additional CPU framebuffer snapshot (about
7.9 MiB at 1080p), plus a reusable packed buffer that can approach another
framebuffer in size. Full-screen updates still copy and upload the whole image;
this work primarily improves small updates and local cursor/viewport interaction.

## Measurement

Measured on 2026-10-09 using the connected device identified as `samsung-a52:5555`.
Its actual model is Samsung SM-A325F, running Android 13, with a Mali-G52 MC2 GPU.
Both builds were arm64 debug builds installed as `com.gaurav.avnc.renderbench`
using a temporary Gradle init script, preserving the existing installed debug app.

The fake RFB server runs on the device over loopback. It disables Nagle's
algorithm and prepares raw pixel payloads before starting the send timestamp.
Server queue wait and payload generation are excluded from send-to-GPU timings.
Both compared builds used the same final server and benchmark code.

`RenderingTest.renderingLatency` draws the production texture and shaders into a
1920×1080 EGL pbuffer and calls `glFinish`. Each workload has ten warmup frames
and sixty measured frames. Upload/draw timings start after decoding and publishing
the update. Server-write-to-GPU timings include transport, decoding, publishing,
upload, drawing, and GPU completion.

`ScreenLatencyTest` runs the actual activity, receiver thread, Renderer, and
GLSurfaceView. It alternates colors in a 32×32 rectangle and then in full-screen
updates, checking a pixel inside each update with PixelCopy. Each workload records
thirty updates after five warmups.
These timings include buffer submission and PixelCopy observation overhead;
they do not measure physical display scanout, remote-host RTT, or input latency.
The screen must be awake and unlocked. The test keeps its activity's screen on.

The baseline used the renderer and native code from `7a2e856`, with a temporary
Kotlin adapter accepting the new `force` argument and returning success. The
adapter still invokes the original unconditional native texture uploads. The
optimized sources were restored before final validation. Later buffer publication
swaps were evaluated with the same baseline APK and final test APK.

The final optimized implementation is `34ffc7e`. The following are single-device
runs, not statistically controlled estimates. GPU clocks, CPU load, garbage
collection, PixelCopy polling, and display scheduling can affect the numbers.
The baseline presentation run followed the final optimized run; GPU baseline
measurements were collected earlier with the same final server implementation.

| Measurement | Baseline median / p95 (ms) | Optimized median / p95 (ms) |
| --- | ---: | ---: |
| Unchanged redraw: upload + draw + GPU | 10.445 / 11.703 | 5.161 / 6.081 |
| 32×32 update: upload + draw + GPU | 14.880 / 15.395 | 7.133 / 7.752 |
| Full-screen update: upload + draw + GPU | 8.711 / 11.788 | 11.100 / 14.057 |
| 32×32 update: server write → GPU, serial harness | 18.900 / 25.811 | 11.965 / 16.474 |
| Full-screen update: server write → GPU, serial harness | 26.961 / 75.362 | 47.030 / 85.914 |
| 32×32 update: server write → actual viewer surface | 34.045 / 40.681 | 12.138 / 29.576 |
| Full-screen update: server write → actual viewer surface | 105.673 / 113.255 | 72.337 / 110.821 |

The actual viewer's measured median improved about 64% for small updates and 32%
for full-screen updates. Full-screen p95 presentation changed little. Full-frame
GPU work became slower in the offscreen benchmark, and its serial send-to-GPU
measurement also waits for decoder baseline restoration before starting GL.
The real receiver and GL threads can overlap that restoration with rendering.
These results support lower viewer latency on this device, not a universal FPS
or full-frame throughput improvement.

Final validation: arm64 debug app and instrumentation builds succeeded; all 39
selected device tests passed in one run. The selection was `RenderingTest`,
`ScreenLatencyTest`, `VncClientTest`, `FrameStateTest`, and the activity tests
`normalViewMode` and `noVideoMode`. An earlier run had two UI failures when the
phone was asleep; both passed after waking it, and the final run passed cleanly.
The optimized isolated app was restored after measuring the baseline.

## Reproduce

Initialize the repository's native submodules and use the Android SDK/NDK
specified in the project. With an awake, unlocked Android device connected:

```sh
ANDROID_SERIAL=<device> ./gradlew connectedDebugAndroidTest \
  -Pandroid.injected.build.abi=arm64-v8a \
  -Pandroid.testInstrumentationRunner=androidx.test.runner.AndroidJUnitRunner \
  -Pandroid.testInstrumentationRunnerArguments.class=com.gaurav.avnc.vnc.RenderingTest,com.gaurav.avnc.ui.vnc.ScreenLatencyTest
adb -s <device> logcat -d -s RenderingLatency:I
```

Use the ABI matching the device. PixelCopy testing requires Android 7 or newer;
the GLES tests support the app's minimum Android version. The tests use a local
server and need no remote VNC installation or credentials. If an existing debug
app has a different signing key, use an isolated application ID rather than
uninstalling it. The temporary init script used for this measurement was:

```groovy
allprojects {
    afterEvaluate {
        if (plugins.hasPlugin('com.android.application')) {
            android.buildTypes.debug.applicationIdSuffix = '.renderbench'
        }
    }
}
```

Pass its filename with Gradle's `-I` option.

Regression coverage includes nonzero-offset partial rectangles, coalesced updates,
unchanged redraws, framebuffer resizing, CopyRect, Tight fill and literal pixels,
cursor shape changes at the same and different sizes, GL context recreation,
connection/authentication, frame geometry, and normal/paused video modes.
