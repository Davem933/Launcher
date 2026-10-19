#!/usr/bin/env bash
# Fáze 0 — rychlé ruční měření výkonu přes adb, bez instrumentace.
#
#   scripts/perf/baseline.sh [package] [runs]
#     package  com.example.carlauncher (debug, default) | com.example.carlauncher.benchmark
#     runs     počet studených startů (default 10)
#
# Měří: studený start (am start -W → TotalTime ≈ TTID), plynulost swipe mezi stránkami
# a posunu mapy (dumpsys gfxinfo), paměť (dumpsys meminfo). Výstup jde na stdout.
set -euo pipefail

PKG="${1:-com.example.carlauncher}"
RUNS="${2:-10}"
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
ACT="$PKG/com.example.carlauncher.MainActivity"
sh_() { "$ADB" shell "$@" | tr -d '\r'; }

[ "$("$ADB" get-state 2>/dev/null)" = "device" ] || { echo "Žádné zařízení v adb" >&2; exit 1; }
sh_ pm path "$PKG" >/dev/null || { echo "$PKG není nainstalovaný" >&2; exit 1; }
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION READ_CALENDAR; do
  sh_ pm grant "$PKG" "android.permission.$p" 2>/dev/null || true
done

echo "== Zařízení: $(sh_ getprop ro.product.model), Android $(sh_ getprop ro.build.version.release)"
echo "== Balíček:  $PKG ($(sh_ dumpsys package "$PKG" | grep -m1 versionName | xargs))"
if sh_ dumpsys package "$PKG" | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  echo "== Debuggable: ANO (debug build — Compose je v něm výrazně pomalejší než v release)"
else
  echo "== Debuggable: ne"
fi

# Uspaný / zamčený displej nic nekreslí → gfxinfo by hlásil 0 snímků. Odemkne jen zámek bez PINu.
sh_ input keyevent KEYCODE_WAKEUP; sleep 1; sh_ wm dismiss-keyguard; sleep 1
if sh_ dumpsys window | grep -q 'isKeyguardShowing=true'; then
  echo "Tablet je zamčený (PIN/heslo) — odemkni ho ručně a spusť znovu" >&2; exit 1
fi

# --- 1) Studený start ---------------------------------------------------------------
# Před force-stop přepneme do Nastavení: pokud je appka výchozí HOME a je v popředí,
# systém by ji po zabití hned spustil znovu a start by nebyl studený.
echo
echo "== Studený start ($RUNS×), TotalTime v ms:"
times=()
for i in $(seq 1 "$RUNS"); do
  sh_ am start -W -a android.settings.SETTINGS >/dev/null
  sleep 1
  sh_ am force-stop "$PKG"
  sleep 1
  t=$(sh_ am start -W -n "$ACT" | awk -F': ' '/TotalTime/ {print $2}')
  printf '  #%-2s %s\n' "$i" "${t:-?}"
  # První start po instalaci občas TotalTime nevrátí — do statistiky jen platné hodnoty.
  if [ -n "$t" ] && [ "$t" -gt 0 ]; then times+=("$t"); fi
  sleep 3
done
printf '%s\n' "${times[@]}" | sort -n | awk '
  { a[NR]=$1; s+=$1 }
  END { printf "  n=%d | min %d | medián %d | max %d | průměr %.0f\n", NR, a[1], a[int((NR+1)/2)], a[NR], s/NR }'

# --- 2) Plynulost -------------------------------------------------------------------
W=$(sh_ wm size | awk -F'[ x]' '/size/ {w=$(NF-1); h=$NF} END {print (w>h?w:h)}')
H=$(sh_ wm size | awk -F'[ x]' '/size/ {w=$(NF-1); h=$NF} END {print (w>h?h:w)}')
gfx() {
  sh_ dumpsys gfxinfo "$PKG" | grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number Slow UI thread|Number Slow bitmap|Number Slow issue draw' \
    | head -10 | sed 's/^/  /'
}
sleep 5   # nech mapu načíst styl a dlaždice
echo
echo "== Swipe mezi stránkami (4×2 tahy, pravý sloupec):"
sh_ dumpsys gfxinfo "$PKG" reset >/dev/null
y=$((H * 45 / 100))
for _ in 1 2; do
  sh_ input swipe $((W*90/100)) $y $((W*10/100)) $y 250; sleep 1
  sh_ input swipe $((W*90/100)) $y $((W*10/100)) $y 250; sleep 1
  sh_ input swipe $((W*10/100)) $y $((W*90/100)) $y 250; sleep 1
  sh_ input swipe $((W*10/100)) $y $((W*90/100)) $y 250; sleep 1
done
gfx

echo
echo "== Posun mapy (8 tahů):"
sh_ dumpsys gfxinfo "$PKG" reset >/dev/null
cy=$((H * 40 / 100))
for _ in 1 2; do
  sh_ input swipe $((W*50/100)) $cy $((W*15/100)) $cy 400; sleep 1
  sh_ input swipe $((W*15/100)) $cy $((W*50/100)) $cy 400; sleep 1
  sh_ input swipe $((W*32/100)) $((H*20/100)) $((W*32/100)) $((H*65/100)) 400; sleep 1
  sh_ input swipe $((W*32/100)) $((H*65/100)) $((W*32/100)) $((H*20/100)) 400; sleep 1
done
gfx

# --- 3) Paměť -----------------------------------------------------------------------
echo
echo "== Paměť (dumpsys meminfo, kB):"
sh_ dumpsys meminfo "$PKG" | grep -E '^ *(Java Heap|Native Heap|Code|Graphics|TOTAL PSS|TOTAL RSS|TOTAL:)' | sed 's/^/  /'
