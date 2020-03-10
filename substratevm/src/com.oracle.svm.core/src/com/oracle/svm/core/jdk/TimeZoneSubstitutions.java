/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;

import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.option.HostedOptionKey;
//Checkstyle: stop
import sun.security.action.GetPropertyAction;
//Checkstyle: resume

import java.security.AccessController;
import java.util.Properties;
import java.util.TimeZone;

@TargetClass(java.util.TimeZone.class)
@SuppressWarnings("unused")
final class Target_java_util_TimeZone {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private static volatile TimeZone defaultTimeZone;

    @Alias
    // Checkstyle: stop
    private static String GMT_ID;
    // Checkstyle: resume

    @Alias
    static native TimeZone getDefaultRef();

    @Alias
    static native String getSystemTimeZoneID(String javaHome);

    @Alias
    private static native TimeZone getTimeZone(String id, boolean fallback);

    @Alias
    private static native String getSystemGMTOffsetID();

    @SuppressWarnings("checkstyle:IllegalToken")
    @Substitute
    // Checkstyle: stop
    private static synchronized TimeZone setDefaultZone() {
        // Checkstyle: resume
        TimeZone tz;
        // get the time zone ID from the system properties
        Properties props = System.getProperties();
        String zoneID = AccessController.doPrivileged(
                        new GetPropertyAction("user.timezone"));

        // if the time zone ID is not set (yet), perform the
        // platform to Java time zone ID mapping.
        if (zoneID == null || zoneID.isEmpty()) {
            try {
                // This is only different part from the JDK
                // because native-image java.home property is null
                zoneID = getSystemTimeZoneID("");
                if (zoneID == null) {
                    zoneID = GMT_ID;
                }
            } catch (NullPointerException e) {
                zoneID = GMT_ID;
            }
        }

        // Get the time zone for zoneID. But not fall back to
        // "GMT" here.
        tz = getTimeZone(zoneID, false);

        if (tz == null) {
            // If the given zone ID is unknown in Java, try to
            // get the GMT-offset-based time zone ID,
            // a.k.a. custom time zone ID (e.g., "GMT-08:00").
            String gmtOffsetID = getSystemGMTOffsetID();
            if (gmtOffsetID != null) {
                zoneID = gmtOffsetID;
            }
            tz = getTimeZone(zoneID, true);
        }
        assert tz != null;

        final String id = zoneID;
        props.setProperty("user.timezone", id);

        defaultTimeZone = tz;
        return tz;
    }
}

public class TimeZoneSubstitutions {
    static class Options {
        @Option(help = "When true, all time zones will be pre-initialized in the image.")//
        public static final HostedOptionKey<Boolean> IncludeAllTimeZones = new HostedOptionKey<Boolean>(false) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                printWarning("-H:IncludeAllTimeZones and -H:IncludeTimeZones are deprecated");
            }
        };

        @Option(help = "The time zones, in addition to the default zone of the host, that will be pre-initialized in the image.")//
        public static final HostedOptionKey<String> IncludeTimeZones = new HostedOptionKey<String>("") {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                printWarning("-H:IncludeAllTimeZones and -H:IncludeTimeZones are deprecated");
            }
        };

        private static void printWarning(String warning) {
            // Checkstyle: stop
            System.err.println(warning);
            // Checkstyle: resume
        }
    }
}
