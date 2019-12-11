LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := image-convert
LOCAL_SRC_FILES := ImageConverter.cpp YuvToByte.cpp
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)