#!/bin/sh
set -e

ALPINE_VERSION="3.20"
ALPINE_RELEASE="3.20.3"
ARCH="aarch64"
ROOTFS_URL="https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION}/releases/${ARCH}/alpine-minirootfs-${ALPINE_RELEASE}-${ARCH}.tar.gz"
TEMP_DIR=$(mktemp -d)
ROOTFS_DIR="${TEMP_DIR}/rootfs"
ASSETS_DIR="$(dirname "$0")/../app/src/main/assets"
OUTPUT_FILE="${ASSETS_DIR}/rootfs-alpine.tar.zst"

cleanup() {
    rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT

mkdir -p "${ROOTFS_DIR}" "${ASSETS_DIR}"

echo "Downloading Alpine minirootfs..."
wget -q "${ROOTFS_URL}" -O "${TEMP_DIR}/alpine-rootfs.tar.gz"

echo "Extracting rootfs..."
tar -xzf "${TEMP_DIR}/alpine-rootfs.tar.gz" -C "${ROOTFS_DIR}"

echo "Setting up networking for chroot..."
cp /etc/resolv.conf "${ROOTFS_DIR}/etc/resolv.conf"

echo "Installing packages..."
chroot "${ROOTFS_DIR}" /bin/sh -c "
    apk update --no-cache
    apk add --no-cache \
        alpine-base \
        busybox \
        musl \
        apk-tools \
        openrc \
        xorg-server \
        xf86-video-fbdev \
        xinit \
        xrandr \
        mesa \
        mesa-dri-gallium \
        mesa-egl \
        mesa-gles \
        gl4es \
        openbox \
        tint2 \
        xterm \
        feh \
        dbus \
        font-dejavu \
        bash \
        coreutils \
        curl \
        wget \
        ca-certificates \
        sudo \
        nano
"

echo "Creating xuser..."
chroot "${ROOTFS_DIR}" /bin/sh -c "
    adduser -D -s /bin/bash xuser
    addgroup xuser wheel
    echo '%wheel ALL=(ALL) ALL' >> /etc/sudoers
"

echo "Creating start-desktop script..."
mkdir -p "${ROOTFS_DIR}/usr/local/bin"
cat > "${ROOTFS_DIR}/usr/local/bin/start-desktop" << 'SCRIPT'
#!/bin/sh
xsetroot -solid "#2e3436"
openbox --config-file /home/xuser/.config/openbox/rc.xml &
tint2 &
wait
SCRIPT
chmod +x "${ROOTFS_DIR}/usr/local/bin/start-desktop"

echo "Creating Openbox config..."
mkdir -p "${ROOTFS_DIR}/home/xuser/.config/openbox"
cat > "${ROOTFS_DIR}/home/xuser/.config/openbox/rc.xml" << 'CONFIG'
<?xml version="1.0" encoding="UTF-8"?>
<openbox_config xmlns="http://openbox.org/3.4/rc"
        xmlns:xi="http://www.w3.org/2001/XInclude">
  <desktops>
    <number>4</number>
    <names>
      <name>1</name>
      <name>2</name>
      <name>3</name>
      <name>4</name>
    </names>
  </desktops>
  <keyboard>
    <keybind key="W-d">
      <action name="ToggleShowDesktop"/>
    </keybind>
    <keybind key="W-Return">
      <action name="Execute">
        <command>xterm</command>
      </action>
    </keybind>
  </keyboard>
  <menu>
    <file>menu.xml</file>
  </menu>
</openbox_config>
CONFIG

cat > "${ROOTFS_DIR}/home/xuser/.config/openbox/menu.xml" << 'MENU'
<?xml version="1.0" encoding="UTF-8"?>
<openbox_menu xmlns="http://openbox.org/3.4/menu">
  <menu id="root-menu" label="Applications">
    <item label="Terminal">
      <action name="Execute"><command>xterm</command></action>
    </item>
    <item label="File Manager">
      <action name="Execute"><command>pcmanfm</command></action>
    </item>
    <separator/>
    <item label="Exit">
      <action name="Exit"/>
    </item>
  </menu>
</openbox_menu>
MENU

chroot "${ROOTFS_DIR}" /bin/sh -c "
    chown -R xuser:xuser /home/xuser
"

echo "Cleaning up..."
rm -f "${ROOTFS_DIR}/etc/resolv.conf"
chroot "${ROOTFS_DIR}" /bin/sh -c "rm -rf /var/cache/apk/*"

echo "Creating tar.zst archive..."
tar -I zstd -cf "${OUTPUT_FILE}" -C "${ROOTFS_DIR}" .

echo "Done: ${OUTPUT_FILE}"
