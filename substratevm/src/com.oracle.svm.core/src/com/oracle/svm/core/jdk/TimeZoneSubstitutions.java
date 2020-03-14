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

import com.oracle.svm.core.LibCHelper;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.util.VMError;
import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;

import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.option.HostedOptionKey;
//Checkstyle: stop
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import sun.security.action.GetPropertyAction;
//Checkstyle: resume

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.util.Properties;
import java.util.TimeZone;

@TargetClass(java.util.TimeZone.class)
@SuppressWarnings("unused")
final class Target_java_util_TimeZone {

    static final class TimeZoneSupport {
        final byte[] tzMappingsContent;

        TimeZoneSupport(final byte[] content) {
            this.tzMappingsContent = content;
        }

        public byte[] getTzMappingsContent() {
            return tzMappingsContent;
        }
    }

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private static volatile TimeZone defaultTimeZone;

    @Alias
    // Checkstyle: stop
    private static String GMT_ID;
    // Checkstyle: resume

    @Alias
    static native TimeZone getDefaultRef();

    @Substitute
    private static String getSystemTimeZoneID(String javaHome) {
        // in windows call the custom function
        // in unix call the normal function, can I call the jdk native function
        TimeZoneSupport timeZoneSupport = ImageSingletons.lookup(TimeZoneSupport.class);
        final byte[] content = timeZoneSupport.getTzMappingsContent();
        String tzmappings = new String(content);
        try (CTypeConversion.CCharPointerHolder tzMappingsHolder = CTypeConversion.toCString(tzmappings)) {
            CCharPointer tzMappings = tzMappingsHolder.get();
            CCharPointer tzId = LibCHelper.customFindJavaTZmd(tzMappings);
            return CTypeConversion.toJavaString(tzId);
        }
    }

    /*
     * @Alias private static native TimeZone getTimeZone(String id, boolean fallback);
     * 
     * @Alias private static native String getSystemGMTOffsetID();
     * 
     * @Substitute // Checkstyle: stop private static synchronized TimeZone setDefaultZone() { //
     * Checkstyle: resume TimeZone tz; // get the time zone ID from the system properties Properties
     * props = System.getProperties(); String zoneID = AccessController.doPrivileged( new
     * GetPropertyAction("user.timezone"));
     * 
     * // if the time zone ID is not set (yet), perform the // platform to Java time zone ID
     * mapping. if (zoneID == null || zoneID.isEmpty()) { try { // This is only different part from
     * the JDK // because native-image java.home property is null zoneID = getSystemTimeZoneID("");
     * if (zoneID == null) { zoneID = GMT_ID; } } catch (NullPointerException e) { zoneID = GMT_ID;
     * } }
     * 
     * // Get the time zone for zoneID. But not fall back to // "GMT" here. tz = getTimeZone(zoneID,
     * false);
     * 
     * if (tz == null) { // If the given zone ID is unknown in Java, try to // get the
     * GMT-offset-based time zone ID, // a.k.a. custom time zone ID (e.g., "GMT-08:00"). String
     * gmtOffsetID = getSystemGMTOffsetID(); if (gmtOffsetID != null) { zoneID = gmtOffsetID; } tz =
     * getTimeZone(zoneID, true); } assert tz != null;
     * 
     * final String id = zoneID; props.setProperty("user.timezone", id);
     * 
     * defaultTimeZone = tz; return tz; }
     */
}

// Add feature again
// Replace Java_java_util_TimeZone_getSystemTimeZoneID with and call a CFunction
// it seems like I am going to have to pull in also all function that calls
// the jdk8 and jdk 11 implementations are different
// it brings functions from libjvm

@AutomaticFeature
final class TimeZoneFeature implements Feature {
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

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {

        if (OS.getCurrent() != OS.WINDOWS) {
            return;
        }

        // read tzmappings on windows
        Path tzMappingsPath = Paths.get(System.getProperty("java.home"), "lib", "tzmappings");
        try {
            byte[] content = Files.readAllBytes(tzMappingsPath);
            ImageSingletons.add(Target_java_util_TimeZone.TimeZoneSupport.class, new Target_java_util_TimeZone.TimeZoneSupport(content));
        } catch (IOException e) {
            VMError.shouldNotReachHere("Failed to read time zone mappings. The time zone mappings should be part" +
                            "of your JDK usually found: " + tzMappingsPath.toAbsolutePath(), e);
        }
    }
}
