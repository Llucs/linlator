#!/bin/sh

. /usr/local/bin/linlator-utils.sh

xsetroot -solid "#2e3440"

openbox --config-file $HOME/.config/openbox/rc.xml --startup /usr/share/openbox/autostart &

tint2 &

x-terminal-emulator &

wait
