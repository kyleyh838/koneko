#!/bin/bash

if [ -z "$ANDROID_HOME" ]; then
  if [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  elif [ -d "$HOME/.local/lib/android/sdk" ]; then
    export ANDROID_HOME="$HOME/.local/lib/android/sdk"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
  fi
fi

# NDK 28+ is required: the NaiveProxy outbound pulls in Cronet, whose
# prebuilt lib was compiled with a Clang that emits AArch64 PAC relocations
# (R_AARCH64_AUTH_ABS64, type 315); LLD only learned AUTH relocations in LLVM 19# (NDK 28), so NDK 25/27 cannot link them.
_NDK="$ANDROID_HOME/ndk/28.2.13676358"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_HOME/ndk/27.2.12479018"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_HOME/ndk/25.0.8775105"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_NDK_HOME"
[ -f "$_NDK/source.properties" ] || _NDK="$NDK"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_HOME/ndk-bundle"

if [ ! -f "$_NDK/source.properties" ]; then
  echo "Error: NDK not found."
  exit 1
fi

export ANDROID_NDK_HOME=$_NDK
export NDK=$_NDK
