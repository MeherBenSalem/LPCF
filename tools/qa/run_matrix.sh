#!/usr/bin/env bash
# Runs lpcf_checks.py on a fresh server for each build, with real vanilla clients under Xvfb:
#   tools/qa/run_matrix.sh [platform:version ...]     (default: the full matrix below)
# Needs build/libs/LPCF-<VERSION>.jar (./gradlew shadowJar). Results go to $QA_HOME/results/<platform>-<version>.txt
# with the server and client logs next to them; screenshots to $QA_HOME/shots/<platform>-<version>/.
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
export QA_HOME=${QA_HOME:-/tmp/lpcf-qa}
export DISPLAY=${DISPLAY:-:1}
QA="$ROOT/tools/qa"
BUILDS=${*:-"paper:1.20.1 paper:1.20.4 paper:1.21.1 paper:1.21.11 paper:26.1.2 paper:26.2 paper:26.3 purpur:1.21.1 purpur:26.3 folia:26.2 folia:26.3"}
mkdir -p "$QA_HOME/results"

if ! xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
  Xvfb "$DISPLAY" -screen 0 1920x1080x24 > "$QA_HOME/xvfb.log" 2>&1 &
  sleep 2
fi

for build in $BUILDS; do
  platform=${build%%:*}; version=${build#*:}
  out="$QA_HOME/results/$platform-$version.txt"
  dir=$(python3 "$QA/server.py" dir "$platform" "$version")
  python3 "$QA/mcclient.py" stop Builder; python3 "$QA/mcclient.py" stop Alex
  for p in paper purpur folia; do for v in 1.20.1 1.20.4 1.21.1 1.21.11 26.1.2 26.2 26.3; do python3 "$QA/server.py" stop $p $v; done; done
  sleep 8
  if ! python3 "$QA/server.py" prepare "$platform" "$version" --legacy-config > "$out.prepare" 2>&1; then
    echo "$platform $version: PREPARE FAILED" | tee "$out"; continue
  fi
  python3 "$QA/server.py" start "$platform" "$version" > /dev/null
  if ! "$QA/waitfor.sh" "$dir/server.log" 'Done \(|Exception in server tick|Failed to start' 600 \
      || ! grep -q 'Done (' "$dir/server.log"; then
    echo "$platform $version: SERVER DID NOT START" | tee "$out"; continue
  fi
  builder_log="$QA_HOME/results/$platform-$version-builder.log"
  alex_log="$QA_HOME/results/$platform-$version-alex.log"
  python3 "$QA/mcclient.py" launch "$version" Builder 127.0.0.1:25565 --log "$builder_log" > /dev/null
  if ! "$QA/waitfor.sh" "$dir/server.log" 'Builder joined the game' 600; then
    echo "$platform $version: CLIENT DID NOT JOIN" | tee "$out"
  else
    sleep 10
    python3 "$QA/lpcf_checks.py" --platform "$platform" --version "$version" --builder-log "$builder_log" \
      --alex-log "$alex_log" --shots "$QA_HOME/shots/$platform-$version" > "$out" 2>&1
  fi
  cat "$dir/build.txt" >> "$out"
  cp "$dir/server.log" "$QA_HOME/results/$platform-$version-server.log"
  echo "$platform $version: $(grep -E '^[0-9]+ checks' "$out" || tail -n 1 "$out")"
done
python3 "$QA/mcclient.py" stop Builder; python3 "$QA/mcclient.py" stop Alex
for p in paper purpur folia; do for v in 1.20.1 1.20.4 1.21.1 1.21.11 26.1.2 26.2 26.3; do python3 "$QA/server.py" stop $p $v; done; done
