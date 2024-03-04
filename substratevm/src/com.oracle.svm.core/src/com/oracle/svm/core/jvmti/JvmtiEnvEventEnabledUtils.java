/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.jvmti;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jvmti.headers.JvmtiEvent;
import com.oracle.svm.core.jvmti.headers.JvmtiEventCallbacks;

public final class JvmtiEnvEventEnabledUtils {

    public static final int JVMTI_MIN_EVENT_TYPE_VAL = 50;
    public static final int JVMTI_MAX_EVENT_TYPE_VAL = 88;
    public static final int JVMTI_NB_EVENTS = JVMTI_MAX_EVENT_TYPE_VAL - JVMTI_MIN_EVENT_TYPE_VAL;

    private static final long NO_EVENTS_ENABLED = 0L;
    // TODO dprcci check if it is the actual value
    private static final long INITIAL_ENABLED_EVENTS = NO_EVENTS_ENABLED;

    @Platforms(Platform.HOSTED_ONLY.class)
    private JvmtiEnvEventEnabledUtils() {
    }

    public static boolean isInValidEventRange(JvmtiEvent event) {
        return event.getCValue() >= JvmtiEnvEventEnabledUtils.JVMTI_MIN_EVENT_TYPE_VAL ||
                        event.getCValue() <= JvmtiEnvEventEnabledUtils.JVMTI_MAX_EVENT_TYPE_VAL;
    }

    public static int getEventEnumIndex(JvmtiEvent event) {
        return event.getCValue() - JVMTI_MIN_EVENT_TYPE_VAL;
    }

    public static int getBitForEvent(JvmtiEvent event) {
        return 1 << getEventEnumIndex(event);
    }

    public static void setUserEventEnabled(JvmtiEnvEventEnabled envEventEnabled, JvmtiEvent event, boolean enabled) {

        long enabledBits = envEventEnabled.getEventUserEnabled();
        int mask = getBitForEvent(event);
        if (enabled) {
            enabledBits |= mask;
        } else {
            enabledBits &= ~mask;
        }
        envEventEnabled.setEventUserEnabled(enabledBits);
    }

    public static boolean isUserEventEnabled(JvmtiEnvEventEnabled envEventEnabled, JvmtiEvent event) {
        return (getBitForEvent(event) & envEventEnabled.getEventUserEnabled()) != 0;
    }

    public static void clearUserEvents(JvmtiEnvEventEnabled envEventEnabled) {
        envEventEnabled.setEventUserEnabled(NO_EVENTS_ENABLED);
    }

    // TODO @dprcci expand (might not be useful anyways since memory is calloc'ed)
    public static void initialize(JvmtiEnvEventEnabled envEventEnabled) {
        envEventEnabled.setEventUserEnabled(INITIAL_ENABLED_EVENTS);
    }

    public static void setEventCallbacksEnabled(JvmtiEnvEventEnabled envEventEnabled, JvmtiEventCallbacks callbacks) {
        envEventEnabled.setCallbackEnabled(JvmtiEventCallbacksUtil.convertCallbacksToBitVector(callbacks));
    }

    // TODO @dprcci implement in Hotspot this is used to set the event_enabled. Current
    // implementation works by checking user_event_enabled and not event_enable
    public static void recomputeEnabled() {
    }
}
