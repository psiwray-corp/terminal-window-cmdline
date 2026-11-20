#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

bash ci/check-docs.sh
bash ci/check-jdk.sh

exec ./gradlew --no-daemon --stacktrace --console=plain \
    spotlessCheck \
    build \
    dokkaGenerate
