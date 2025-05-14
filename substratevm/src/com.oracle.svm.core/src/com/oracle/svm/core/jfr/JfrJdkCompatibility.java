/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.jfr.Recording;
import jdk.jfr.internal.JVMSupport;

/**
 * Compatibility class to handle incompatible changes between JDK 21 and JDK 22. Once support for
 * JDKs prior to 22 is dropped, these the methods can be called directly and the substitutions can
 * go away.
 */
@SuppressWarnings("unused")
public final class JfrJdkCompatibility {
    private JfrJdkCompatibility() {
    }

    public static String makeFilename(Recording recording) {
        return Target_jdk_jfr_internal_JVMSupport.makeFilename(recording);
    }

    public static String formatTimespan(Duration dValue, String separation) {
        return Target_jdk_jfr_internal_util_ValueFormatter.formatTimespan(dValue, separation);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void createNativeJFR() {
        try {
            Method createJFR = ReflectionUtil.lookupMethod(JVMSupport.class, "createJFR");
            createJFR.invoke(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}

@TargetClass(className = "jdk.jfr.internal.JVMSupport", onlyWith = HasJfrSupport.class)
final class Target_jdk_jfr_internal_JVMSupport {
    @Substitute
    public static String makeFilename(Recording recording) {
        return JfrFilenameUtil.makeFilename(recording);
    }
}

@TargetClass(className = "jdk.jfr.internal.util.ValueFormatter", onlyWith = HasJfrSupport.class)
final class Target_jdk_jfr_internal_util_ValueFormatter {
    @Alias
    public static native String formatTimespan(Duration dValue, String separation);

    @Alias
    public static native String formatDateTime(LocalDateTime time);
}

final class JfrFilenameUtil {
    public static String makeFilename(Recording recording) {
        long pid = ProcessProperties.getProcessID();
        String date = getFormatDateTime();
        String idText = recording == null ? "" : "-id-" + recording.getId();
        String imageName = SubstrateOptions.Name.getValue();
        return imageName + "-pid-" + pid + idText + "-" + date + ".jfr";
    }

    private static String getFormatDateTime() {
        LocalDateTime now = LocalDateTime.now();
        return Target_jdk_jfr_internal_util_ValueFormatter.formatDateTime(now);
    }
}
