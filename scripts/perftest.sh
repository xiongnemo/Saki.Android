#!/bin/sh
# Standardized gfxinfo performance test for Saki.Android
# Usage:
#   Via adb:  adb shell sh /sdcard/perftest.sh [scenario] [output_dir]
#   On device: sh /sdcard/perftest.sh [scenario] [output_dir]
#
# Scenarios: scroll | nowplaying | swipe | dismiss | all
# Default: all, output to /sdcard/

PKG="org.hdhmc.saki"
SCENARIO="${1:-all}"
OUT="${2:-/sdcard}"

# Auto-detect screen size
SIZE=$(wm size | grep "Physical" | awk '{print $3}')
W=$(echo "$SIZE" | cut -d'x' -f1)
H=$(echo "$SIZE" | cut -d'x' -f2)

# Derived coordinates (proportional to screen)
CX=$((W / 2))                # center X
CY=$((H / 2))                # center Y
SCROLL_Y1=$((H * 75 / 100))  # scroll start (75% from top)
SCROLL_Y2=$((H * 25 / 100))  # scroll end (25% from top)
SWIPE_X1=$((W * 80 / 100))   # cover swipe start X
SWIPE_X2=$((W * 20 / 100))   # cover swipe end X
BOTTOM_Y=$((H * 90 / 100))   # near bottom (for capsule tap)
TOP_Y=$((H * 10 / 100))      # near top (for dismiss swipe)

reset_gfx() {
    dumpsys gfxinfo "$PKG" reset > /dev/null 2>&1
    sleep 0.5
}

dump_gfx() {
    local name="$1"
    dumpsys gfxinfo "$PKG" > "${OUT}/gfxinfo-${name}.txt"
    echo "Saved: ${OUT}/gfxinfo-${name}.txt"
    grep -E "Total frames|Janky frames|50th|90th|95th|99th" "${OUT}/gfxinfo-${name}.txt"
}

wait_settle() {
    sleep 1
}

# --- Scenarios ---

test_scroll() {
    echo "=== SCROLL TEST (song list, 10 swipes) ==="
    reset_gfx
    sleep 1
    # 10 identical swipes, 300ms each, 500ms pause between
    i=0
    while [ $i -lt 10 ]; do
        input swipe "$CX" "$SCROLL_Y1" "$CX" "$SCROLL_Y2" 300
        sleep 0.5
        i=$((i + 1))
    done
    # scroll back
    i=0
    while [ $i -lt 10 ]; do
        input swipe "$CX" "$SCROLL_Y2" "$CX" "$SCROLL_Y1" 300
        sleep 0.5
        i=$((i + 1))
    done
    wait_settle
    dump_gfx "scroll"
}

test_nowplaying() {
    echo "=== NOW PLAYING OPEN TEST ==="
    reset_gfx
    sleep 1
    # Tap capsule at bottom center to open Now Playing
    input tap "$CX" "$BOTTOM_Y"
    sleep 2
    wait_settle
    dump_gfx "nowplaying"
}

test_swipe() {
    echo "=== COVER ART SWIPE TEST (6 swipes) ==="
    # Assumes Now Playing is already open
    reset_gfx
    sleep 1
    # 6 horizontal swipes on cover art
    i=0
    while [ $i -lt 3 ]; do
        input swipe "$SWIPE_X1" "$CY" "$SWIPE_X2" "$CY" 250
        sleep 1
        i=$((i + 1))
    done
    i=0
    while [ $i -lt 3 ]; do
        input swipe "$SWIPE_X2" "$CY" "$SWIPE_X1" "$CY" 250
        sleep 1
        i=$((i + 1))
    done
    wait_settle
    dump_gfx "swipe"
}

test_dismiss() {
    echo "=== DISMISS NOW PLAYING TEST ==="
    # Assumes Now Playing is open
    reset_gfx
    sleep 1
    input keyevent BACK
    sleep 2
    wait_settle
    dump_gfx "dismiss"
}

# --- Run ---

case "$SCENARIO" in
    scroll)
        test_scroll
        ;;
    nowplaying)
        test_nowplaying
        ;;
    swipe)
        test_swipe
        ;;
    dismiss)
        test_dismiss
        ;;
    all)
        echo "Running all scenarios..."
        echo ""
        test_scroll
        echo ""
        test_nowplaying
        echo ""
        test_swipe
        echo ""
        test_dismiss
        echo ""
        echo "=== ALL DONE ==="
        ;;
    *)
        echo "Unknown scenario: $SCENARIO"
        echo "Usage: $0 [scroll|nowplaying|swipe|dismiss|all] [output_dir]"
        exit 1
        ;;
esac
