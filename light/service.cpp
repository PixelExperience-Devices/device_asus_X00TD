/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "android.hardware.light@2.0-service.X00TD"

#include <android-base/logging.h>
#include <hidl/HidlTransportSupport.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <utils/Errors.h>

#include "Light.h"

// libhwbinder:
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;

// Generated HIDL files
using android::hardware::light::V2_0::ILight;
using android::hardware::light::V2_0::implementation::Light;

// LCD
const static std::string kLcdBacklightPath = "/sys/class/leds/lcd-backlight/brightness";
const static std::string kLcdMaxBacklightPath = "/sys/class/leds/lcd-backlight/max_brightness";

// Red led
const static std::string kRedBreathPath = "/sys/class/leds/red/breath";
const static std::string kRedLedPath = "/sys/class/leds/red/brightness";

// Green led
const static std::string kGreenBreathPath = "/sys/class/leds/green/breath";
const static std::string kGreenLedPath = "/sys/class/leds/green/brightness";

int main() {
    uint32_t lcdMaxBrightness = 255;

    std::ofstream lcdBacklight(kLcdBacklightPath);
    if (!lcdBacklight) {
        LOG(ERROR) << "Failed to open " << kLcdBacklightPath << ", error=" << errno << " ("
                   << strerror(errno) << ")";
        return -errno;
    }

    std::ifstream lcdMaxBacklight(kLcdMaxBacklightPath);
    if (!lcdMaxBacklight) {
        LOG(ERROR) << "Failed to open " << kLcdMaxBacklightPath << ", error=" << errno << " ("
                   << strerror(errno) << ")";
        return -errno;
    } else {
        lcdMaxBacklight >> lcdMaxBrightness;
    }

    std::ofstream redBreath(kRedBreathPath);
    if (!redBreath) {
        LOG(ERROR) << "Failed to open " << kRedBreathPath << ", error=" << errno << " ("
                   << strerror(errno) << ")";
        return -errno;
    }

    std::ofstream redLed(kRedLedPath);
    if (!redLed) {
        LOG(ERROR) << "Failed to open " << kRedLedPath << ", error=" << errno << " ("
                   << strerror(errno) << ")";
        return -errno;
    }

    std::ofstream greenBreath(kGreenBreathPath);
    if (!greenBreath) {
        LOG(ERROR) << "Failed to open " << kGreenBreathPath << ", error=" << errno << " ("
                   << strerror(errno) << ")";
        return -errno;
    }

    std::ofstream greenLed(kGreenLedPath);
    if (!greenLed) {
        LOG(ERROR) << "Failed to open " << kGreenLedPath << ", error=" << errno << " ("
                   << strerror(errno) << ")";
        return -errno;
    }

    android::sp<ILight> service =
        new Light({std::move(lcdBacklight), lcdMaxBrightness}, std::move(redBreath),
                  std::move(redLed), std::move(greenBreath), std::move(greenLed));

    configureRpcThreadpool(1, true);

    android::status_t status = service->registerAsService();

    if (status != android::OK) {
        LOG(ERROR) << "Cannot register Light HAL service";
        return 1;
    }

    LOG(INFO) << "Light HAL Ready.";
    joinRpcThreadpool();
    // Under normal cases, execution will not reach this line.
    LOG(ERROR) << "Light HAL failed to join thread pool.";
    return 1;
}
