#!/bin/sh
# Lists every file of a native Sketchware project as "size path", to compare the folders before/after an action.
#   adb push scripts/sketchware_project_snapshot.sh /data/local/tmp/snap.sh
#   adb shell sh /data/local/tmp/snap.sh <project id> > antes.txt     (repeat after the action, then diff)
B=/storage/emulated/0/.sketchware
for d in data/$1 mysc/$1 mysc/list/$1 resources/images/$1 resources/sounds/$1 resources/fonts/$1 resources/icons/$1 bak/$1; do
  [ -e "$B/$d" ] && find "$B/$d" -type f -exec stat -c '%s %n' {} + 2>/dev/null
done | sed "s#$B/##" | sort -k2
