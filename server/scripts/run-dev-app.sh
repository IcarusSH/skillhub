#!/usr/bin/env bash

set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROFILE="${SPRING_PROFILES_ACTIVE:-local}"

cd "$SERVER_DIR"

# Source project-root .env if present so OAuth2 client-id / client-secret
# and other credentials reach the Java process. The .env file header
# documents this as part of the `make dev-all` flow, but Makefile does
# not source it on the dev-all path. We do it here (and only here, so the
# secrets do not leak into make's own process tree) before exec'ing java.
REPO_ROOT="$(cd "$SERVER_DIR/.." && pwd)"
if [[ -f "$REPO_ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env"
  set +a
fi

./mvnw -pl skillhub-app -am clean package -DskipTests >/dev/null

APP_JAR="$(find skillhub-app/target -maxdepth 1 -type f -name 'skillhub-app-*.jar' ! -name '*.original' | head -n 1)"
if [[ -z "$APP_JAR" ]]; then
  echo "Could not locate packaged skillhub-app jar under skillhub-app/target" >&2
  exit 1
fi

exec "${JAVA_BIN:-java}" -jar "$APP_JAR" --spring.profiles.active="$PROFILE" "$@"
