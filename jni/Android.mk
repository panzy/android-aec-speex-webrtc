LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libspeex
LOCAL_SRC_FILES := ../../speex-1.2rc1/android/libs/$(TARGET_ARCH_ABI)/libspeex.so
include $(PREBUILT_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE   := speex_aec
LOCAL_C_INCLUDES := $(LOCAL_PATH) \
	/home/panzy/workspace/webrtc \
	/home/panzy/workspace/speex-1.2rc1/include
LOCAL_CFLAGS := -O3 
LOCAL_CPPFLAGS :=$(LOCAL_CFLAGS)
###

LOCAL_SRC_FILES := speex_aec.cpp  \
speex_aec_jni.cpp

LOCAL_SHARED_LIBRARIES := libwebrtc_audio_preprocessing libspeex

LOCAL_LDLIBS := -llog -lOpenSLES

include $(BUILD_SHARED_LIBRARY)


