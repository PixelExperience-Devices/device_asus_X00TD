//
// Copyright (C) 2022 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
//

#define LOG_TAG "TouchscreenGestureService"

#include <unordered_map>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <fstream>
#include "TouchscreenGesture.h"

namespace {
typedef struct {
    int32_t keycode;
    const char* name;
} GestureInfo;

// id -> info
const std::unordered_map<int32_t, GestureInfo> kGestureInfoMap = {
    {0, {0x2ec, "Gesture W"}},
    {1, {0x2ed, "Gesture e"}},
    {2, {0x2ee, "Gesture S"}},
    {3, {0x2ef, "Gesture Z"}},
    {4, {0x2f0, "Gesture C"}},
    {5, {0x2f1, "Gesture V"}},
    {6, {0x2f6, "Gesture Swipe Up"}},
};
}  // anonymous namespace

namespace vendor {
namespace lineage {
namespace touch {
namespace V1_0 {
namespace implementation {

static constexpr const char* kGestureNodePath =
    "/proc/tpd_gesture";

Return<void> TouchscreenGesture::getSupportedGestures(getSupportedGestures_cb resultCb) {
    std::vector<Gesture> gestures;

    for (const auto& entry : kGestureInfoMap) {
        gestures.push_back({entry.first, entry.second.name, entry.second.keycode});
    }
    resultCb(gestures);

    return Void();
}

Return<bool> TouchscreenGesture::setGestureEnabled(
    const ::vendor::lineage::touch::V1_0::Gesture& gesture, bool enabled) {
    const auto entry = kGestureInfoMap.find(gesture.id);
    if (entry == kGestureInfoMap.end()) {
        return false;
    }

    std::ofstream file(kGestureNodePath);
    file << (enabled ? "1" : "0");
    LOG(DEBUG) << "Wrote file " << kGestureNodePath << " fail " << file.fail();
    return !file.fail();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace touch
}  // namespace lineage
}  // namespace vendor
