# Architecture

## Overview

Linlator is a 4-layer stack that runs native Linux desktop applications inside
a PRoot container on Android, rendering their GUI via a Java-based X11 server
that targets a GLSurfaceView.

```
┌──────────────────────────────────────────────────────────┐
│ Layer 1: Android App (Kotlin / Jetpack Compose)          │
│  UI, container lifecycle, settings, input handling       │
├──────────────────────────────────────────────────────────┤
│ Layer 2: JNI Bridge (C/C++)                              │
│  Launches PRoot, sets up namespaces, manages processes   │
├──────────────────────────────────────────────────────────┤
│ Layer 3: PRoot Container (Alpine/Debian rootfs)          │
│  Userland chroot with --link2symlink isolation           │
│  Runs Openbox, tint2, and target applications            │
├──────────────────────────────────────────────────────────┤
│ Layer 4: Android / Linux Kernel                          │
│  SurfaceFlinger, binder, driver stack                    │
└──────────────────────────────────────────────────────────┘
```

## Inspiration from Winlator

Linlator builds on concepts proven by Winlator, an Android app that runs Windows
software via Wine + Box86/64 + PRoot:

### Reused patterns

| Concept | Winlator | Linlator |
|---|---|---|
| Java X11 server | `XServer.java` renders Windows → GLSurfaceView | Same approach for Linux → GLSurfaceView |
| Container packaging | `.tzst` compressed rootfs images | `.tzst` compressed Alpine/Debian rootfs |
| Per-container config | JSON file with env vars, bind mounts, GPU flags | JSON file with env vars, bind mounts, GPU flags |
| UDP IPC for input | Input events relayed via UDP to XServer | Same UDP IPC mechanism for touch/mouse |
| Touchpad overlay | Gesture → mouse event translation | Identical overlay pattern |
| Container lifecycle | create → start → pause → resume → stop | Same lifecycle state machine |

### What is specific to Linlator

| Feature | Detail |
|---|---|
| Alpine/Debian rootfs | Native Linux userland with apk/apt package management |
| Desktop environment | Openbox + tint2 panel, app launcher |
| Zink GPU acceleration | Mesa Zink translates OpenGL → Vulkan → Android |
| GL4ES GPU acceleration | Translates OpenGL 1.x/2.x → OpenGL ES |
| VirGL GPU acceleration | VirGL renderer for virtualized GPU |
| Package manager integration | Run apk/apt directly inside container |
| Android storage integration | Bind-mounts `/sdcard` and app data directories |

## PRoot Isolation

PRoot provides rootless chroot using ptrace. Linlator invokes PRoot with:

```
proot \
  --link2symlink \
  --sysvipc \
  --change-id=0:0 \
  --rootfs=<extracted_rootfs> \
  --bind=/data/data/com.linlator/files/xserver/X0:/tmp/.X11-unix/X0 \
  --bind=/sdcard:/mnt/sdcard \
  --bind=/data/data/com.linlator/files/home:/home/xuser \
  --bind=/proc:/proc \
  --bind=/sys:/sys \
  --bind=/dev:/dev \
  --bind=/data/data/com.linlator/cache:/var/cache \
  /bin/sh -c "su - xuser -c /home/xuser/.xinitrc"
```

Key flags:
- `--link2symlink` — converts hard links to symlinks, avoiding link count issues
- `--sysvipc` — enables System V IPC emulation (required by X11)
- `--change-id=0:0` — runs as fake root inside the container
- `--bind` — maps host paths into the container namespace

## X11 Server Architecture

The X11 server runs as a Java service on the Android side. It listens on a Unix
domain socket at `/data/data/com.linlator/files/xserver/X0`.

```
┌────────────────────────────────┐
│        Linux Application        │
│  libX11 → xcb → Unix socket    │
└──────────┬─────────────────────┘
           │ X11 protocol (requests, events, drawables)
           ▼
┌────────────────────────────────┐
│   Java XServer (XServer.java)  │
│  ┌──────────────────────────┐  │
│  │ UnixSocketListener       │  │
│  │ epoll-based event loop   │  │
│  └──────────┬───────────────┘  │
│             │ parsed X11 ops   │
│  ┌──────────▼───────────────┐  │
│  │ X11ProtocolHandler       │  │
│  │ Core + MIT-SHM + Render  │  │
│  └──────────┬───────────────┘  │
│             │ GL draw commands │
│  ┌──────────▼───────────────┐  │
│  │ GLESRenderer             │  │
│  │ OpenGL ES 3.x draw calls │  │
│  └──────────┬───────────────┘  │
└─────────────┼──────────────────┘
              │ eglSwapBuffers
     ┌────────▼────────┐
     │  GLSurfaceView   │
     │  (Android View)  │
     └────────┬────────┘
              │
     ┌────────▼────────┐
     │  SurfaceFlinger  │
     │  (HWC composer)  │
     └─────────────────┘
```

The XServer component is split into:
- `XServer.java` — socket listener, client accept, event loop
- `X11ProtocolHandler.java` — parses X11 protocol requests and maintains
  window/GC/pixmap state
- `GLESRenderer.java` — translates draw calls to OpenGL ES primitives
- `InputBridge.java` — receives UDP touch/mouse events, feeds them to X11
  clients as Core Input events

## Container Lifecycle

```
┌─────┐    ┌────────┐    ┌───────┐    ┌────────┐    ┌──────┐
│ IDLE │───→│ CREATE │───→│ START │───→│ PAUSE  │───→│ STOP │
└─────┘    └────────┘    └───┬───┘    └───┬────┘    └──────┘
                             │             │
                             │  SIGSTOP    │  SIGCONT
                             └─────────────┘
                                   │
                              ┌────▼────┐
                              │ RESUME  │
                              └─────────┘
```

### Phases

1. **CREATE**
   - Extract `.tzst` rootfs archive to container directory
   - Apply overlay files from `rootfs/overlay/` (Openbox config, .xinitrc,
     linlator-utils.sh, profile scripts)
   - Generate container config JSON with env vars and bind mount list
   - Download/verify rootfs checksum from bundled hash

2. **START**
   - Build PRoot command line with all bind mounts
   - Set environment: `DISPLAY=:0`, `HOME=/home/xuser`,
     `LD_LIBRARY_PATH=/usr/lib/mesa-zink:/usr/lib/gl4es`
   - Launch PRoot process with `.xinitrc` as the init script
   - Wait for X11 Unix socket to become available (poll with timeout)
   - Connect Java XServer to socket, begin event loop

3. **PAUSE**
   - Send `SIGSTOP` to PRoot process group
   - Free X11 surfaces, pause GLSurfaceView rendering pipeline

4. **RESUME**
   - Send `SIGCONT` to PRoot process group
   - Reconnect X11 surfaces, resume GLSurfaceView

5. **STOP**
   - Send `SIGTERM` → wait 3s → `SIGKILL` to PRoot process group
   - Close X11 socket
   - Clean up temp files
   - Return to IDLE state

## Graphics Pipeline

```
┌──────────────────────────────────────────────┐
│        Native Linux App (GLX/EGL)             │
│  Calls OpenGL 1.x-4.x via Mesa               │
└──────────────────┬───────────────────────────┘
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
┌──────────────┐   ┌──────────────────┐
│ Mesa Zink    │   │ GL4ES / VirGL    │
│ (GL→Vulkan)  │   │ (GL→GLES)        │
└──────┬───────┘   └────────┬─────────┘
       │                     │
       └──────────┬──────────┘
                  ▼
┌────────────────────────────────┐
│         X11 Protocol           │
│  (libX11 → xcb → Unix socket) │
└──────────────────┬─────────────┘
                   │
                  ▼
┌────────────────────────────────┐
│      Java XServer (Android)   │
│  Parses X11, renders via GLES │
└──────────────────┬─────────────┘
                   │ eglSwapBuffers
                  ▼
┌────────────────────────────────┐
│         GLSurfaceView          │
│  OpenGL ES 3.x framebuffer    │
└──────────────────┬─────────────┘
                   │
                  ▼
┌────────────────────────────────┐
│         SurfaceFlinger         │
│  Android compositor / HWC     │
└──────────────────┬─────────────┘
                   │
                  ▼
┌────────────────────────────────┐
│      Android / Linux Kernel    │
│  DRM/KMS, GPU driver (KGSL)   │
└────────────────────────────────┘
```

Key points:
- Native Linux applications link against Mesa which produces X11 draw calls
- Zink routes OpenGL through Vulkan, then back to OpenGL ES on the Android side
- GL4ES translates OpenGL 1.x/2.x directly to OpenGL ES
- The X11 server bridges the gap between native X11 and Android's graphics stack
- No XWayland or Wayland translation layer is involved

## Component Reference

| Component | Path | Description |
|---|---|---|
| Main Activity | `app/src/main/java/com/linlator/ui/MainActivity.kt` | App entry point, Compose UI host |
| Container List | `app/src/main/java/com/linlator/ui/ContainerList.kt` | Container list screen |
| Container Config | `app/src/main/java/com/linlator/ui/ContainerConfig.kt` | Container settings screen |
| Container Manager | `app/src/main/java/com/linlator/container/ContainerManager.kt` | Lifecycle state machine |
| Container Config Model | `app/src/main/java/com/linlator/container/ContainerConfig.kt` | JSON config model |
| Container Process | `app/src/main/java/com/linlator/container/ContainerProcess.kt` | PRoot process management |
| XServer | `app/src/main/java/com/linlator/xserver/XServer.java` | Unix socket listener, event loop |
| X11 Protocol Handler | `app/src/main/java/com/linlator/xserver/X11ProtocolHandler.java` | X11 request parsing |
| GLES Renderer | `app/src/main/java/com/linlator/xserver/GLESRenderer.java` | OpenGL ES draw commands |
| Input Bridge | `app/src/main/java/com/linlator/input/InputBridge.java` | UDP input receiver |
| Touchpad Handler | `app/src/main/java/com/linlator/input/TouchpadHandler.kt` | Gesture → mouse translation |
| JNI Bridge | `app/src/main/java/com/linlator/jni/JniBridge.kt` | Native method declarations |
| Native Launcher | `app/src/main/jni/launcher.cpp` | PRoot command builder, exec |
| Rootfs Builder | `rootfs/build-alpine.sh` | Alpine rootfs archive builder |
| Overlay Utils | `rootfs/overlay/usr/local/bin/linlator-utils.sh` | Container environment setup |
| Openbox Config | `rootfs/overlay/home/xuser/.config/openbox/rc.xml` | Window manager settings |
| Openbox Menu | `rootfs/overlay/home/xuser/.config/openbox/menu.xml` | Right-click application menu |
| Xinit Script | `rootfs/overlay/home/xuser/.xinitrc` | X11 session startup |
| Profile Script | `rootfs/overlay/etc/profile.d/linlator.sh` | System-wide env vars |
| Build Script (root) | `build.gradle.kts` | Root Gradle build configuration |
| Build Script (app) | `app/build.gradle.kts` | App module Gradle build |
| Settings | `settings.gradle.kts` | Gradle project settings |
| Properties | `gradle.properties` | Gradle properties (SDK/NDK paths) |
| CMake | `native/CMakeLists.txt` | Native C/C++ build definition |
