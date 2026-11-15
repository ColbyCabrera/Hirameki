// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (C) 2019 The Android Open Source Project

/*
 *  This file incorporates code under the following license:
 *
 *     Copyright (C) 2019 The Android Open Source Project
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *     https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/os/VibrationAttributes.java;drc=6f9cf7514746523065939f370106cc770f39c2a6
 */

package com.ichi2.compat

import androidx.annotation.IntDef

/**
 * Vibration usage filter value to match all usages.
 */
const val USAGE_FILTER_MATCH_ALL = -1

/**
 * Vibration usage class value to use when the vibration usage class is unknown.
 */
const val USAGE_CLASS_UNKNOWN = 0x0

/**
 * Vibration usage class value to use when the vibration is initiated to catch user's
 * attention, such as alarm, ringtone, and notification vibrations.
 */
const val USAGE_CLASS_ALARM = 0x1

/**
 * Vibration usage class value to use when the vibration is initiated as a response to user's
 * actions, such as emulation of physical effects, and texting feedback vibration.
 */
const val USAGE_CLASS_FEEDBACK = 0x2

/**
 * Vibration usage class value to use when the vibration is part of media, such as music, movie,
 * soundtrack, game or animations.
 */
const val USAGE_CLASS_MEDIA = 0x3

/**
 * Mask for vibration usage class value.
 */
const val USAGE_CLASS_MASK = 0xF

/**
 * Usage value to use when usage is unknown.
 */
const val USAGE_UNKNOWN = 0x0 or USAGE_CLASS_UNKNOWN

/**
 * Usage value to use for alarm vibrations.
 */
const val USAGE_ALARM = 0x10 or USAGE_CLASS_ALARM

/**
 * Usage value to use for ringtone vibrations.
 */
const val USAGE_RINGTONE = 0x20 or USAGE_CLASS_ALARM

/**
 * Usage value to use for notification vibrations.
 */
const val USAGE_NOTIFICATION = 0x30 or USAGE_CLASS_ALARM

/**
 * Usage value to use for vibrations which mean a request to enter/end a
 * communication with the user, such as a voice prompt.
 */
const val USAGE_COMMUNICATION_REQUEST = 0x40 or USAGE_CLASS_ALARM

/**
 * Usage value to use for touch vibrations.
 *
 * Most typical haptic feedback should be classed as *touch* feedback. Examples
 * include vibrations for tap, long press, drag and scroll.
 */
const val USAGE_TOUCH = 0x10 or USAGE_CLASS_FEEDBACK

/**
 * Usage value to use for vibrations which emulate physical hardware reactions,
 * such as edge squeeze.
 *
 *
 * Note that normal screen-touch feedback "click" effects would typically be
 * classed as [USAGE_TOUCH], and that on-screen "physical" animations
 * like bouncing would be [USAGE_MEDIA].
 */
const val USAGE_PHYSICAL_EMULATION = 0x20 or USAGE_CLASS_FEEDBACK

/**
 * Usage value to use for vibrations which provide a feedback for hardware
 * component interaction, such as a fingerprint sensor.
 */
const val USAGE_HARDWARE_FEEDBACK = 0x30 or USAGE_CLASS_FEEDBACK

/**
 * Usage value to use for accessibility vibrations, such as with a screen reader.
 */
const val USAGE_ACCESSIBILITY = 0x40 or USAGE_CLASS_FEEDBACK

/**
 * Usage value to use for input method editor (IME) haptic feedback.
 */
const val USAGE_IME_FEEDBACK = 0x50 or USAGE_CLASS_FEEDBACK

/**
 * Usage value to use for media vibrations, such as music, movie, soundtrack, animations, games,
 * or any interactive media that isn't for touch feedback specifically.
 */
const val USAGE_MEDIA = 0x10 or USAGE_CLASS_MEDIA

@IntDef(
    value = [
        USAGE_UNKNOWN,
        USAGE_ACCESSIBILITY,
        USAGE_ALARM,
        USAGE_COMMUNICATION_REQUEST,
        USAGE_HARDWARE_FEEDBACK,
        USAGE_MEDIA,
        USAGE_NOTIFICATION,
        USAGE_PHYSICAL_EMULATION,
        USAGE_RINGTONE,
        USAGE_TOUCH,
        USAGE_IME_FEEDBACK,
    ],
)
@Retention(AnnotationRetention.SOURCE)
annotation class VibrationUsage
