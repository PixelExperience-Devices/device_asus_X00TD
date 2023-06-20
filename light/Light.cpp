/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

//#define HAL_DEBUG
#define LOG_TAG "LightService"

#include "Light.h"

#ifdef HAL_DEBUG
#include <android-base/logging.h>
#endif

namespace {
using android::hardware::light::V2_0::LightState;

static constexpr int DEFAULT_MAX_BRIGHTNESS = 255;

static uint32_t rgbToBrightness(const LightState& state) {
    uint32_t color = state.color & 0x00ffffff;
    return ((77 * ((color >> 16) & 0xff)) + (150 * ((color >> 8) & 0xff)) + (29 * (color & 0xff))) >>
           8;
}

static bool isLit(const LightState& state) {
    return (state.color & 0x00ffffff);
}

}  // anonymous namespace

namespace android {
namespace hardware {
namespace light {
namespace V2_0 {
namespace implementation {

Light::Light(std::pair<std::ofstream, uint32_t>&& lcd_backlight, std::ofstream&& red_breath,
             std::ofstream&& red_led, std::ofstream&& green_breath, std::ofstream&& green_led)
    : mLcdBacklight(std::move(lcd_backlight)),
      mRedBreath(std::move(red_breath)),
      mRedLed(std::move(red_led)),
      mGreenBreath(std::move(green_breath)),
      mGreenLed(std::move(green_led)) {
    auto attnFn(std::bind(&Light::setAttentionLight, this, std::placeholders::_1));
    auto backlightFn(std::bind(&Light::setLcdBacklight, this, std::placeholders::_1));
    auto batteryFn(std::bind(&Light::setBatteryLight, this, std::placeholders::_1));
    auto buttonsFn(
        std::bind(&Light::setButtonsBacklight, this, std::placeholders::_1));  // fake button dummy
    auto notifFn(std::bind(&Light::setNotificationLight, this, std::placeholders::_1));
    mLights.emplace(std::make_pair(Type::ATTENTION, attnFn));
    mLights.emplace(std::make_pair(Type::BACKLIGHT, backlightFn));
    mLights.emplace(std::make_pair(Type::BATTERY, batteryFn));
    mLights.emplace(std::make_pair(Type::BUTTONS, buttonsFn));  // fake button dummy
    mLights.emplace(std::make_pair(Type::NOTIFICATIONS, notifFn));
}

// Methods from ::android::hardware::light::V2_0::ILight follow.
Return<Status> Light::setLight(Type type, const LightState& state) {
    auto it = mLights.find(type);

    if (it == mLights.end()) {
        return Status::LIGHT_NOT_SUPPORTED;
    }

    it->second(state);

    return Status::SUCCESS;
}

Return<void> Light::getSupportedTypes(getSupportedTypes_cb _hidl_cb) {
    std::vector<Type> types;

    for (auto const& light : mLights) {
        types.push_back(light.first);
    }

    _hidl_cb(types);

    return Void();
}

void Light::setAttentionLight(const LightState& state) {
    std::lock_guard<std::mutex> lock(mLock);
    mAttentionState = state;
    setSpeakerBatteryLightLocked();
}

void Light::setLcdBacklight(const LightState& state) {
    std::lock_guard<std::mutex> lock(mLock);

    uint32_t brightness = rgbToBrightness(state);

    // If max panel brightness is not the default (255),
    // apply linear scaling across the accepted range.
    if (mLcdBacklight.second != DEFAULT_MAX_BRIGHTNESS) {
#ifdef HAL_DEBUG
        int old_brightness = brightness;
#endif
        brightness = brightness * mLcdBacklight.second / DEFAULT_MAX_BRIGHTNESS;
#ifdef HAL_DEBUG
        LOG(VERBOSE) << "scaling brightness " << old_brightness << " => " << brightness;
#endif
    }

    mLcdBacklight.first << brightness << std::endl;
}

void Light::setButtonsBacklight(const LightState& state) {
    // We have no buttons light management, so do nothing.
    // This function required to shut up warnings about missing functionality.
    (void)state;
}

void Light::setBatteryLight(const LightState& state) {
    std::lock_guard<std::mutex> lock(mLock);
    mBatteryState = state;
    setSpeakerBatteryLightLocked();
}

void Light::setNotificationLight(const LightState& state) {
    std::lock_guard<std::mutex> lock(mLock);
    mNotificationState = state;
    setSpeakerBatteryLightLocked();
}

void Light::setSpeakerBatteryLightLocked() {
    if (isLit(mNotificationState)) {
        setSpeakerLightLocked(mNotificationState);
    } else if (isLit(mAttentionState)) {
        setSpeakerLightLocked(mAttentionState);
    } else if (isLit(mBatteryState)) {
        setSpeakerLightLocked(mBatteryState);
    } else {
        // No active LED scenarios, turn off the LEDs
        setSpeakerLightLocked(LightState{}); // Set an empty LightState to turn off the LEDs
    }
}

void Light::setSpeakerLightLocked(const LightState& state) {
    int red, green;
    int breath;
    int onMs, offMs;
    uint32_t colorARGB = state.color;

#ifdef HAL_DEBUG
    int stateMode;
#endif

    // Disable previous active light
    mRedBreath << 0 << std::endl;
    mGreenBreath << 0 << std::endl;

    switch (state.flashMode) {
        case Flash::TIMED:
            onMs = state.flashOnMs;
            offMs = state.flashOffMs;
#ifdef HAL_DEBUG
            stateMode = 1;
#endif
            break;
        case Flash::NONE:
        default:
            onMs = 0;
            offMs = 0;
#ifdef HAL_DEBUG
            stateMode = 0;
#endif
            break;
    }

    red = (colorARGB >> 16) & 0xff;
    green = (colorARGB >> 8) & 0xff;

    if (onMs > 0 && offMs > 0)
        breath = 1;
    else
        breath = 0;

    // Use only 255(0xFF) for base colors
    if (colorARGB > 0xFF000000 && state == mBatteryState) {
        if (red >= green) {
            green = 0;
            red = 0xFF;
        } else if (!breath && red >= 0x50 && green > red) {  // try make orange
            green = 0xFF;
            red = 0x08;
        } else {
            green = 0xFF;
            red = 0;
        }
    } else if (colorARGB > 0xFF000000 && state == mNotificationState) {
        green = 0xFF;
        red = 0;
    } else {
        green = 0;
        red = 0;
    }

#ifdef HAL_DEBUG
    int ledState;
    if (state == mBatteryState)
        ledState = 1;
    else if (state == mNotificationState)
        ledState = 2;
    else
        ledState = 0;

    LOG(VERBOSE) << "Light::setSpeakerLightLocked: mode=" << stateMode << " ledState=" << ledState
                 << " colorARGB=" << colorARGB << " onMS=" << onMs << " offMS=" << offMs
                 << " breath=" << breath << " red=" << red << " green" << green;
#endif

    if (breath) {
        if (green) {
            mGreenBreath << breath << std::endl;  // green breath for notifications only
        }
        if (red) {
            mRedBreath << breath << std::endl;  // red breath for battery only
        }
    } else {
        mRedLed << red << std::endl;
        mGreenLed << green << std::endl;
    }
}

}  // namespace implementation
}  // namespace V2_0
}  // namespace light
}  // namespace hardware
}  // namespace android
