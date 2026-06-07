export DISPLAY=:0
export HOME=/home/xuser
export SHELL=/bin/sh
export USER=xuser
export XDG_RUNTIME_DIR=/tmp/runtime-xuser
export XDG_CONFIG_HOME=$HOME/.config

if [ -d /usr/lib/mesa-zink ]; then
    export LD_LIBRARY_PATH=/usr/lib/mesa-zink:$LD_LIBRARY_PATH
    export MESA_LOADER_DRIVER_OVERRIDE=zink
    export ZINK_DESCRIPTORS=lazy
fi

if [ -d /usr/lib/gl4es ]; then
    export LD_LIBRARY_PATH=/usr/lib/gl4es:$LD_LIBRARY_PATH
    export GL4ES_USEES2=1
    export GL4ES_LIBGL_ES2=libGLESv2.so
fi

if [ -d /usr/lib/virgl ]; then
    export LD_LIBRARY_PATH=/usr/lib/virgl:$LD_LIBRARY_PATH
fi

export LIBGL_ALWAYS_SOFTWARE=0
export GALLIUM_DRIVER=zink
