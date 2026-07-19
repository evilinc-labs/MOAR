#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <built-jar> <installed-jar>" >&2
    exit 2
fi

source_jar=$1
target_jar=$2
target_dir=$(dirname "$target_jar")
staged_jar="$target_dir/.moar-deploy-$$.jar"

cleanup() {
    rm -f "$staged_jar"
}
trap cleanup EXIT

test -f "$source_jar"
test -d "$target_dir"
install -m 0644 "$source_jar" "$staged_jar"
unzip -tqq "$staged_jar"
mv -f "$staged_jar" "$target_jar"
trap - EXIT

sha256sum "$target_jar"
