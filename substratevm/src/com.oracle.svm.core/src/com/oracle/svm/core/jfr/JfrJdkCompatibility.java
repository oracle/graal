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
package com.oracle.svm.core.jfr;

import java.time.Duration;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK21OrEarlier;
import com.oracle.svm.core.jdk.JDK22OrLater;

import jdk.jfr.Recording;

/**
 * Compatibility class to handle incompatible changes between JDK 21 and JDK 22. Once support for
 * JDKs prior to 22 is dropped, these the methods can be called directly and the substitutions can
 * go away.
 */
@SuppressWarnings("unused")

final class JfrJdkCompatibility {
    private JfrJdkCompatibility() {
    }

    public static String makeFilename(Recording recording) {
        if (JavaVersionUtil.JAVA_SPEC >= 22) {
            return Target_jdk_jfr_internal_JVMSupport.makeFilename(recording);
        } else {
            return Target_jdk_jfr_internal_Utils.makeFilename(recording);
        }
    }

    public static String formatTimespan(Duration dValue, String separation) {
        if (JavaVersionUtil.JAVA_SPEC >= 22) {
            return Target_jdk_jfr_internal_util_ValueFormatter.formatTimespan(dValue, separation);
        } else {
            return Target_jdk_jfr_internal_Utils.formatTimespan(dValue, separation);
        }
    }
}

@TargetClass(className = "jdk.jfr.internal.Utils", onlyWith = {JDK21OrEarlier.class, HasJfrSupport.class})
final class Target_jdk_jfr_internal_Utils {
    @Alias
    public static native String makeFilename(Recording recording);

    @Alias
    public static native String formatTimespan(Duration dValue, String separation);
}

@TargetClass(className = "jdk.jfr.internal.JVMSupport", onlyWith = {JDK22OrLater.class, HasJfrSupport.class})
final class Target_jdk_jfr_internal_JVMSupport {
    @Alias
    public static native String makeFilename(Recording recording);
}

@TargetClass(className = "jdk.jfr.internal.util.ValueFormatter", onlyWith = {JDK22OrLater.class, HasJfrSupport.class})
final class Target_jdk_jfr_internal_util_ValueFormatter {
    @Alias
    public static native String formatTimespan(Duration dValue, String separation);
}