#!/usr/bin/env bash
# waitfor.sh <file> <regex> <seconds>: wait until the file contains a line matching the regex.
deadline=$((SECONDS + $3))
while [ $SECONDS -lt $deadline ]; do
  grep -qE "$2" "$1" 2>/dev/null && exit 0
  sleep 2
done
exit 1
