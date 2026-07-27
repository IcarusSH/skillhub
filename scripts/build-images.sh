#!/usr/bin/env bash
#
# scripts/build-images.sh - one-shot build for all SkillHub service images.
#
# Usage:
#   scripts/build-images.sh [version] [--push] [--platform <plat>] [--registry <reg>]
#
# Examples:
#   scripts/build-images.sh
#     # VERSION=$(git rev-parse --short HEAD), IMAGE_REGISTRY=skillhub.local,
#     # images tagged skillhub.local/{server,web,scanner}:<sha>  (load into local docker).
#
#   scripts/build-images.sh 0.2.0 --registry registry.cn-hangzhou.aliyuncs.com/skill_hub
#     # Tag = 0.2.0, push target = Aliyun ACR personal namespace.
#
#   scripts/build-images.sh 0.2.0 --push
#     # Build + push to the default registry (skillhub.local). Push requires --registry
#     # to be a writable registry.
#
# The script invokes `docker buildx build --target <svc>` against the unified
# Dockerfile at the repository root. Build context is the repository root, so
# run this from the repo root or any subdirectory.
#
# Side effects:
#   - Creates (or reuses) a buildx builder named "skillhub" for caching.
#   - Loads images into the local docker daemon by default (no --push).
#   - Tags every image with both "<registry>/<svc>:<version>" and
#     "<registry>/<svc>:latest" for convenience.

set -euo pipefail

# Resolve repo root from this script's location, regardless of CWD.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

VERSION=""
PUSH=0
PLATFORM="linux/amd64"
REGISTRY="${IMAGE_REGISTRY:-skillhub.local}"
BUILDER_NAME="skillhub"
SERVICES=(server web scanner)

usage() {
    cat <<EOF
Usage: $0 [version] [--push] [--platform <plat>] [--registry <reg>] [--services <csv>]

Arguments:
  version                Image tag. Default: short git SHA, or "dev" if no git.
  --push                 Push images to the registry instead of loading locally.
  --platform <plat>      Target platform (default: linux/amd64). Repeat for multi-arch.
  --registry <reg>       Image registry/namespace prefix (default: skillhub.local).
                         Examples:
                           skillhub.local
                           ghcr.io/iflytek
                           registry.cn-hangzhou.aliyuncs.com/skill_hub
  --services <csv>       Comma-separated subset of services to build
                         (server,web,scanner). Default: all three.

Environment overrides:
  IMAGE_REGISTRY         Same as --registry, takes lower precedence than CLI.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --push)
            PUSH=1
            shift
            ;;
        --platform)
            [[ $# -ge 2 ]] || { echo "Missing value for --platform" >&2; exit 1; }
            PLATFORM="$2"
            shift 2
            ;;
        --registry)
            [[ $# -ge 2 ]] || { echo "Missing value for --registry" >&2; exit 1; }
            REGISTRY="$2"
            shift 2
            ;;
        --services)
            [[ $# -ge 2 ]] || { echo "Missing value for --services" >&2; exit 1; }
            IFS=',' read -r -a SERVICES <<< "$2"
            for s in "${SERVICES[@]}"; do
                case "$s" in
                    server|web|scanner) ;;
                    *)
                        echo "Unknown service '$s' (expected server|web|scanner)" >&2
                        exit 1
                        ;;
                esac
            done
            shift 2
            ;;
        --*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
        *)
            if [[ -z "$VERSION" ]]; then
                VERSION="$1"
            else
                echo "Unexpected positional argument: $1" >&2
                usage >&2
                exit 1
            fi
            shift
            ;;
    esac
done

if [[ -z "$VERSION" ]]; then
    if VERSION="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null)"; then
        :
    else
        VERSION="dev"
    fi
fi

# Validate registry: must be non-empty and contain only safe characters.
# Allows: 'skillhub.local', 'ghcr.io/iflytek', 'registry.cn-hangzhou.aliyuncs.com/skill_hub',
# 'localhost:5000'.
if [[ -z "$REGISTRY" ]] || ! [[ "$REGISTRY" =~ ^[A-Za-z0-9._:-]+(/[A-Za-z0-9._:-]+)*$ ]]; then
    echo "Registry '$REGISTRY' is not a valid registry/namespace (expected e.g. 'skillhub.local', 'ghcr.io/iflytek', or 'localhost:5000')." >&2
    exit 1
fi

cd "$REPO_ROOT"

# Ensure a buildx builder exists for cross-platform / caching.
if ! docker buildx inspect "$BUILDER_NAME" >/dev/null 2>&1; then
    echo "==> Creating buildx builder '$BUILDER_NAME'"
    docker buildx create --name "$BUILDER_NAME" --use >/dev/null
fi

build_one() {
    local svc="$1"
    local tag_version="$REGISTRY/$svc:$VERSION"
    local tag_latest="$REGISTRY/$svc:latest"
    local load_or_push=()

    if [[ "$PUSH" -eq 1 ]]; then
        load_or_push=(--push)
    else
        load_or_push=(--load)
    fi

    echo "================================================================"
    echo "==> Building $svc -> $tag_version (and :latest)"
    echo "================================================================"

    # shellcheck disable=SC2086
    docker buildx build \
        --builder "$BUILDER_NAME" \
        --platform "$PLATFORM" \
        --target "$svc" \
        --build-arg "VERSION=$VERSION" \
        -t "$tag_version" \
        -t "$tag_latest" \
        "${load_or_push[@]}" \
        .
}

SERVICES=(server web scanner)
for svc in "${SERVICES[@]}"; do
    build_one "$svc"
done

echo
echo "==> Done."
if [[ "$PUSH" -eq 1 ]]; then
    echo "    Pushed to: $REGISTRY/{server,web,scanner}:$VERSION (+ :latest)"
else
    echo "    Loaded into local docker. Inspect with:"
    echo "      docker images | grep '$REGISTRY/'"
    echo
    echo "    To push later, run:"
    echo "      $0 $VERSION --push --registry $REGISTRY"
fi