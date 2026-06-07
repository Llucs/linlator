# Linlator

Linux-on-Android container runtime. No root, no VM, no extra kernel.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                  Android App                         │
│           (Kotlin / Jetpack Compose UI)              │
├─────────────────────────────────────────────────────┤
│                Container Manager                     │
│     (create / start / pause / resume / stop)         │
├─────────────────────────────────────────────────────┤
│                     PRoot                            │
│   --link2symlink --sysvipc --change-id=0:0           │
│   bind mounts for Android storage & X11 socket       │
├─────────────────────────────────────────────────────┤
│             Alpine / Debian rootfs                   │
│   Desktop environment (Openbox + tint2)              │
│   GPU: Zink / GL4ES / VirGL over X11                │
├─────────────────────────────────────────────────────┤
│                  XServer (Java)                      │
│   Unix socket → epoll → GLSurfaceView (OpenGL ES)   │
├─────────────────────────────────────────────────────┤
│                    SurfaceFlinger                    │
│              Android / Linux Kernel                  │
└─────────────────────────────────────────────────────┘
```

## Comparison

| Feature          | Winlator                              | Linlator                                  |
|------------------|---------------------------------------|-------------------------------------------|
| Guest OS         | Windows (via Wine + Box86/64)         | Linux (Alpine/Debian rootfs)              |
| Binary format    | PE (Windows executables)              | ELF (Linux executables)                   |
| Graphics layer   | DXVK/VKD3D → Vulkan → Wine → X11      | Native → Mesa Zink/GL4ES → X11            |
| GPU backends     | Vulkan (Mesa Turnip, VirGL)           | OpenGL ES (Zink, GL4ES, VirGL)            |
| System emulation | Box86/64 for x86 → ARM translation    | PRoot only (no emulation needed)          |
| Package mgmt     | Manual .exe installers                | apk/apt inside container                  |
| Storage          | Container-scoped bind mounts          | Android storage bind mounts               |
| Root required    | No                                    | No                                        |

## Project Structure

```
linlator/
├── app/                          # Android application module
│   ├── src/main/
│   │   ├── java/com/linlator/    # Kotlin source
│   │   │   ├── ui/               # Jetpack Compose UI
│   │   │   ├── container/        # Container lifecycle
│   │   │   ├── xserver/          # Java X11 server
│   │   │   ├── input/            # Touch/mouse input
│   │   │   └── jni/              # JNI bridge
│   │   └── jni/                  # C/C++ JNI sources
│   └── build.gradle.kts
├── rootfs/
│   ├── build-alpine.sh           # Alpine rootfs builder
│   ├── overlay/                  # Files overlaid into containers
│   │   ├── etc/profile.d/linlator.sh
│   │   ├── home/xuser/
│   │   │   ├── .xinitrc
│   │   │   └── .config/openbox/
│   │   │       ├── rc.xml
│   │   │       └── menu.xml
│   │   └── usr/local/bin/
│   │       └── linlator-utils.sh
│   └── rootfs-skeleton/         # Base rootfs directory layout
├── native/                       # Native C/C++ code
│   ├── CMakeLists.txt
│   └── src/
├── ARCHITECTURE.md
├── LICENSE
├── README.md
├── build.gradle.kts              # Root project build
├── settings.gradle.kts
└── gradle.properties
```

## Build

```sh
./rootfs/build-alpine.sh && ./gradlew assembleDebug
```

## Dependencies

- Android SDK 35
- NDK 27
- CMake 3.22+

## License

MIT

<https://github.com/Llucs/linlator>
