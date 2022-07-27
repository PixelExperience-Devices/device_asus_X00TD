//
// Copyright (C) 2022 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
//

#define LOG_TAG "vendor.lineage.touch@1.0-service.X00TD"

#include <android-base/logging.h>
#include <hidl/HidlTransportSupport.h>

#include "TouchscreenGesture.h"

using ::android::OK;
using ::android::sp;

using ::vendor::lineage::touch::V1_0::ITouchscreenGesture;
using ::vendor::lineage::touch::V1_0::implementation::TouchscreenGesture;

int main() {
    sp<ITouchscreenGesture> gestureService = new TouchscreenGesture();

    android::hardware::configureRpcThreadpool(1, true /*callerWillJoin*/);

    if (gestureService->registerAsService() != OK) {
        LOG(ERROR) << "Cannot register touchscreen gesture HAL service.";
        return 1;
    }

    LOG(INFO) << "Touchscreen HAL service ready.";

    android::hardware::joinRpcThreadpool();

    LOG(ERROR) << "Touchscreen HAL service failed to join thread pool.";
    return 1;
}
