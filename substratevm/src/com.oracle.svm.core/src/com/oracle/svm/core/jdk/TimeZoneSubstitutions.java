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
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TimeZone;

/**
 * The following classes aim to provide full support for time zones for native-image. This
 * substitution is necessary due to the reliance on JAVA_HOME in the JDK.
 *
 * In summary in the JDK time zone data is extracted from the underlying platform via native
 * methods, later the JDK code processes that data to create time zone objects exposed via JAVA
 * APIs.
 *
 * Luckily JAVA_HOME is only really necessary for the Windows operating system. Posix operating
 * systems rely on system calls independent of JAVA_HOME (except for null checks done in the JNI
 * function wrapping the native time zone JDK functions). Thus for Posix operating systems this
 * implementation, simply, by-passes the null check(in native image JAVA_HOME is null) and calls
 * into the original native functions by substituting the JNI method
 * TimeZone.getSystemTimeZoneID(String).
 *
 * In Windows, the JRE contains a special file called tzmappings, (see
 * <a href="https://docs.oracle.com/javase/9/troubleshoot/time-zone-settings-jre.htm#JSTGD359">time
 * zones in the jre</a>), representing the mapping between Windows and Java time zones. The
 * tzmappings file is read and parsed in the native JDK code. Thus for windows,
 * {@link TimeZoneFeature} reads such file and stores it in the image heap at build-time. At
 * run-time the contents of the file are passed to a custom implementation of the JDK time zones
 * logic. The only difference in the custom implementation is reading and parsing time zone mappings
 * from a buffer as opposed to a file.
 */
@TargetClass(java.util.TimeZone.class)
@SuppressWarnings("unused")
final class Target_java_util_TimeZone {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) private static TimeZone defaultTimeZone;

    @Substitute
    private static String getSystemTimeZoneID(String javaHome) {
        CCharPointer tzMappingsPtr = WordFactory.nullPointer();
        int contentLen = 0;
        PinnedObject pinnedContent = null;
        try {
            if (ImageSingletons.contains(TimeZoneSupport.class)) {
                byte[] content = ImageSingletons.lookup(TimeZoneSupport.class).getTzMappingsContent();
                contentLen = content.length;
                pinnedContent = PinnedObject.create(content);
                tzMappingsPtr = pinnedContent.addressOfArrayElement(0);
            }
            CCharPointer tzId = LibCHelper.SVM_FindJavaTZmd(tzMappingsPtr, contentLen);
            return CTypeConversion.toJavaString(tzId);
        } finally {
            if (pinnedContent != null) {
                pinnedContent.close();
            }
        }
    }
}

/**
 * Holds time zone mapping data.
 */
final class TimeZoneSupport {
    final byte[] tzMappingsContent;

    TimeZoneSupport(final byte[] content) {
        this.tzMappingsContent = content;
    }

    public byte[] getTzMappingsContent() {
        return tzMappingsContent;
    }
}

/**
 * Reads time zone mappings data and stores in the image heap, if necessary.
 */
@AutomaticFeature
final class TimeZoneFeature implements Feature {
    static class Options {
        @Option(help = "When true, all time zones will be pre-initialized in the image.")//
        public static final HostedOptionKey<Boolean> IncludeAllTimeZones = new HostedOptionKey<Boolean>(false) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                printWarning();
            }
        };

        @Option(help = "The time zones, in addition to the default zone of the host, that will be pre-initialized in the image.")//
        public static final HostedOptionKey<String> IncludeTimeZones = new HostedOptionKey<String>("") {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                printWarning();
            }
        };

        private static void printWarning() {
            // Checkstyle: stop
            System.err.println("-H:IncludeAllTimeZones and -H:IncludeTimeZones are now deprecated. Native-image includes all timezones" +
                            " by default.");
            // Checkstyle: resume
        }
    }

    private static byte[] cleanCR(byte[] buffer) {
        byte[] scratch = new byte[buffer.length];
        int copied = 0;
        for (byte b : buffer) {
            if (b == '\r') {
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

class TimeZoneSubstitutions {
}
