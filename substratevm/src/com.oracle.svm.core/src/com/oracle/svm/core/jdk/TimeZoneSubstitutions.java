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
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TimeZone;

@Platforms(InternalPlatform.PLATFORM_JNI.class)
@TargetClass(java.util.TimeZone.class)
@SuppressWarnings("unused")
final class Target_java_util_TimeZone {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private static volatile TimeZone defaultTimeZone;

    @Substitute
    private static String getSystemTimeZoneID(String javaHome) {
        String tzmappings = "";
        if (OS.getCurrent() == OS.WINDOWS) {
            TimeZoneSupport timeZoneSupport = ImageSingletons.lookup(TimeZoneSupport.class);
            byte[] content = timeZoneSupport.getTzMappingsContent();
            tzmappings = new String(content);
        }
        try (CTypeConversion.CCharPointerHolder tzMappingsHolder = CTypeConversion.toCString(tzmappings)) {
            CCharPointer tzMappings = tzMappingsHolder.get();
            CCharPointer tzId = LibCHelper.customFindJavaTZmd(tzMappings);
            return CTypeConversion.toJavaString(tzId);
        }
    }
}

final class TimeZoneSupport {
    final byte[] tzMappingsContent;

    TimeZoneSupport(final byte[] content) {
        this.tzMappingsContent = content;
    }

    public byte[] getTzMappingsContent() {
        return tzMappingsContent;
    }

}

@AutomaticFeature
@Platforms(InternalPlatform.PLATFORM_JNI.class)
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

    private static byte[] cleanCR(byte[] buffer) {
        byte[] scratch = new byte[buffer.length];
        int copied = 0;
        for (byte b : buffer) {
            if (b == 13) {
                continue;
            }
            scratch[copied++] = b;
        }
        byte[] content = new byte[copied];
        System.arraycopy(scratch, 0, content, 0, copied);
        return content;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {

        if (OS.getCurrent() != OS.WINDOWS) {
            ImageSingletons.add(TimeZoneSupport.class, new TimeZoneSupport(null));
            return;
        }

        // read tzmappings on windows
        Path tzMappingsPath = Paths.get(System.getProperty("java.home"), "lib", "tzmappings");
        try {
            byte[] buffer = Files.readAllBytes(tzMappingsPath);
            // tzmappings has windows line endings on windows??
            byte[] content = cleanCR(buffer);
            ImageSingletons.add(TimeZoneSupport.class, new TimeZoneSupport(content));
        } catch (IOException e) {
            VMError.shouldNotReachHere("Failed to read time zone mappings. The time zone mappings should be part" +
                            "of your JDK usually found: " + tzMappingsPath.toAbsolutePath(), e);
        }
    }
}
