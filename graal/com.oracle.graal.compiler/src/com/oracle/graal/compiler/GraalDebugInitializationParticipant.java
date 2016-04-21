/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Params;
import com.oracle.graal.debug.DebugInitializationParticipant;
import com.oracle.graal.debug.GraalDebugConfig;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.serviceprovider.ServiceProvider;

/**
 * A service provider that may modify the initialization of {@link Debug} based on the values
 * specified for various {@link GraalDebugConfig} options.
 */
@ServiceProvider(DebugInitializationParticipant.class)
public class GraalDebugInitializationParticipant implements DebugInitializationParticipant {

    @Override
    public void apply(Params params) {
        if (GraalDebugConfig.areDebugScopePatternsEnabled()) {
            params.enable = true;
        }
        if ("".equals(GraalDebugConfig.Options.Count.getValue())) {
            params.enableUnscopedCounters = true;
        }
        if ("".equals(GraalDebugConfig.Options.MethodMeter.getValue())) {
            params.enableUnscopedMethodMetrics = true;
            // mm requires full debugging support
            params.enable = true;
        }
        if ("".equals(GraalDebugConfig.Options.Time.getValue())) {
            params.enableUnscopedTimers = true;
        }
        if ("".equals(GraalDebugConfig.Options.TrackMemUse.getValue())) {
            params.enableUnscopedMemUseTrackers = true;
        }
        // unscoped metrics/timers should respect method filter semantics
        if (!params.enable && (params.enableUnscopedMemUseTrackers || params.enableUnscopedMethodMetrics || params.enableUnscopedCounters || params.enableUnscopedTimers) &&
                        GraalDebugConfig.isNotEmpty(GraalDebugConfig.Options.MethodFilter)) {
            params.enable = true;
            params.enabledMethodFilter = true;
        }

        if (!params.enableUnscopedMethodMetrics && GraalDebugConfig.Options.MethodMeter.getValue() != null) {
            // mm requires full debugging support
            params.enable = true;
        }

        if (GraalDebugConfig.isMethodMetricsDebugValueInterceptionEnabled()) {
            if (!params.enable) {
                TTY.println("WARNING: MethodMeter is disabled but MethodMeterInterceptDebugValues is enabled. Ignoring MethodMeter and MethodMeterInterceptDebugValues.");
            } else {
                parseMethodMetricsDebugValueInterception(params);
            }
        }
    }

    private static void parseMethodMetricsDebugValueInterception(Params params) {
        String[] flags = GraalDebugConfig.Options.MethodMeterInterceptDebugValues.getValue().split(":");
        if (flags.length == 3) {
            params.interceptMeter = flags[0].equals("X");
            params.interceptTime = flags[1].equals("X");
            params.interceptMem = flags[2].equals("X");
        } else {
            TTY.println("WARNING: Ignoring MethodMeterInterceptDebugValues as the supplied argument does not conform to the format X|O:X|O:X|O.");
            GraalDebugConfig.Options.MethodMeterInterceptDebugValues.setValue(null);
        }
    }
}
