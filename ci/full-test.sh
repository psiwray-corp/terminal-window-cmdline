#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

gradle_args=(--no-daemon --stacktrace fullTest)

if [[ "$(uname -s)" == Linux* ]]; then
    if ! command -v xvfb-run >/dev/null 2>&1; then
        echo "error: xvfb-run is required for the full test suite." >&2
        echo "Install Xvfb and Mesa (see ci/install-gl-deps.sh) and retry." >&2
        exit 1
    fi

    export LIBGL_ALWAYS_SOFTWARE=1
    exec xvfb-run -a -s "-screen 0 1280x1024x24" ./gradlew "${gradle_args[@]}"
fi

exec ./gradlew "${gradle_args[@]}"
