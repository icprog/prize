ifeq ($(strip $(PRIZE_CAMERA_APP)),DC)
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := mediatek-framework

LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_MODULE := com.mediatek.camera.ext

LOCAL_CERTIFICATE := platform

include $(BUILD_STATIC_JAVA_LIBRARY)
endif
