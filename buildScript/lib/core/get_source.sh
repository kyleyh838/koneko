#!/bin/bash
set -e

source "buildScript/init/env.sh"
ENV_NB4A=1
source "buildScript/lib/core/get_source_env.sh"
REPO_ROOT="$(pwd)"
pushd ..

####

if [ ! -d "sing-box" ]; then
  git clone --no-checkout "$SING_BOX_REPO"
fi
pushd sing-box
git checkout "$COMMIT_SING_BOX"
PATCHES_DIR="$REPO_ROOT/buildScript/lib/core/patches"
for patch in "$PATCHES_DIR"/*.patch; do
  if git apply --check "$patch" >/dev/null 2>&1; then
    git apply "$patch"
  elif git apply --check --reverse "$patch" >/dev/null 2>&1; then
    echo "patch already applied, skipping: $(basename "$patch")"
  else
    echo "error: patch does not apply: $patch" >&2
    exit 1
  fi
done
popd

####

if [ ! -d "libneko" ]; then
  git clone --no-checkout https://github.com/MatsuriDayo/libneko.git
fi
pushd libneko
git checkout "$COMMIT_LIBNEKO"
popd

####

popd
