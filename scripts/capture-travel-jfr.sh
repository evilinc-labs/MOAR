#!/usr/bin/env bash
set -euo pipefail

if [[ ${1:-} == "--help" || ${1:-} == "-h" ]]; then
    cat <<'USAGE'
Usage: scripts/capture-travel-jfr.sh [seconds] [minecraft-pid]

Start a Java Flight Recorder profile of a running Anarchy Prism instance.
Defaults to 180 seconds and auto-detects the Minecraft process. Set
MOAR_GAME_DIR for another instance, or pass its process ID explicitly.
The recording is saved beside MOAR's packet traces in config/moar/.
USAGE
    exit 0
fi

seconds=${1:-180}
if [[ ! $seconds =~ ^[0-9]+$ ]] || (( seconds < 10 || seconds > 900 )); then
    printf 'Duration must be between 10 and 900 seconds.\n' >&2
    exit 2
fi

game_dir=${MOAR_GAME_DIR:-"$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Anarchy/minecraft"}
game_dir=$(realpath "$game_dir")
if [[ ! -d $game_dir ]]; then
    printf 'Minecraft directory not found: %s\n' "$game_dir" >&2
    exit 2
fi

jcmd=${MOAR_JCMD:-"$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-delta/bin/jcmd"}
if [[ ! -x $jcmd ]]; then
    jcmd=$(command -v jcmd || true)
fi
if [[ -z $jcmd || ! -x $jcmd ]]; then
    printf 'jcmd was not found. Set MOAR_JCMD to the jcmd from the client JDK.\n' >&2
    exit 2
fi

pid=${2:-}
if [[ -z $pid ]]; then
    candidates=()
    for comm_file in /proc/[0-9]*/comm; do
        [[ -r $comm_file ]] || continue
        read -r command_name < "$comm_file" || continue
        [[ $command_name == java* ]] || continue
        candidate=${comm_file%/comm}
        candidate=${candidate#/proc/}
        process_dir=$(readlink -f "/proc/$candidate/cwd" 2>/dev/null || true)
        [[ $process_dir == "$game_dir" ]] && candidates+=("$candidate")
    done
    if (( ${#candidates[@]} != 1 )); then
        printf 'Found %d Java processes running from %s.\n' "${#candidates[@]}" "$game_dir" >&2
        printf 'Pass the Minecraft PID as the second argument if auto-detection fails.\n' >&2
        exit 2
    fi
    pid=${candidates[0]}
fi
if [[ ! $pid =~ ^[0-9]+$ ]]; then
    printf 'Minecraft PID must be a number.\n' >&2
    exit 2
fi

output_dir="$game_dir/config/moar"
mkdir -p "$output_dir"
stamp=$(date +%Y%m%d-%H%M%S)
output="$output_dir/moar-travel-$stamp.jfr"
recording="moar_travel_$stamp"

printf 'Starting %ss JFR capture for Minecraft PID %s.\n' "$seconds" "$pid"
"$jcmd" "$pid" JFR.start "name=$recording" settings=profile "duration=${seconds}s" "filename=$output"
printf 'Travel now; the recording will be written to:\n%s\n' "$output"
sleep "$((seconds + 3))"
if [[ -s $output ]]; then
    printf 'Capture complete: %s\n' "$output"
else
    printf 'Recording not found yet. Check that Minecraft is still running, then inspect:\n%s\n' "$output" >&2
    exit 1
fi
