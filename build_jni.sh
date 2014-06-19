#!/bin/sh

export ANDROID_NDK_ROOT=/opt/android-ndk-r9b

rm -rf src/speex_aec
mkdir -p src/speex_aec

swig -java -package speex_aec -includeall -verbose -outdir src/speex_aec -c++ \
-I/usr/local/include -I/System/Library/Frameworks/JavaVM.framework/Headers \
-I./jni -o jni/speex_aec_jni.cpp speex_aec_interface.i

$ANDROID_NDK_ROOT/ndk-build TARGET_PLATFORM=android-9 V=1




