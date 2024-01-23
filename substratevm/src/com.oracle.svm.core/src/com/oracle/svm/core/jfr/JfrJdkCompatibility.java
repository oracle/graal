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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK21OrEarlier;
import com.oracle.svm.core.jdk.JDK22OrEarlier;
import com.oracle.svm.core.jdk.JDK22OrLater;
import com.oracle.svm.core.jdk.JDK23OrLater;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.jfr.Recording;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.JVMSupport;
import jdk.jfr.internal.PlatformRecording;
import jdk.jfr.internal.SecuritySupport;

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

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void createNativeJFR() {
        try {
            if (JavaVersionUtil.JAVA_SPEC >= 22) {
                Method createJFR = ReflectionUtil.lookupMethod(JVMSupport.class, "createJFR");
                createJFR.invoke(null);
            } else {
                Method createNativeJFR = ReflectionUtil.lookupMethod(JVM.class, "createNativeJFR");
                createNativeJFR.invoke(getJVMOrNull());
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void retransformClasses(Class<?>[] classes) {
        try {
            // call JVM.retransformClasses(classes)
            Method retransformClasses = ReflectionUtil.lookupMethod(JVM.class, "retransformClasses", Class[].class);
            retransformClasses.invoke(getJVMOrNull(), (Object) classes);
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    /**
     * Gets a {@link JVM} object or {@code null} in case of JDK 22+, where the methods of
     * {@link JVM} are static (JDK-8310661).
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static JVM getJVMOrNull() throws IllegalAccessException, InvocationTargetException {
        if (JavaVersionUtil.JAVA_SPEC >= 22) {
            return null;
        } else {
            Method getJVM = ReflectionUtil.lookupMethod(JVM.class, "getJVM");
            return (JVM) getJVM.invoke(null);
        }
    }

    public static void setDumpDirectory(PlatformRecording platformRecording, SecuritySupport.SafePath directory) {
        Target_jdk_jfr_internal_PlatformRecording pr = SubstrateUtil.cast(platformRecording, Target_jdk_jfr_internal_PlatformRecording.class);
        if (JavaVersionUtil.JAVA_SPEC >= 23) {
            pr.setDumpDirectory(directory);
        } else {
            pr.setDumpOnExitDirectory(directory);
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

@TargetClass(className = "jdk.jfr.internal.PlatformRecording")
final class Target_jdk_jfr_internal_PlatformRecording {
    @Alias
    @TargetElement(onlyWith = JDK23OrLater.class)
    public native void setDumpDirectory(SecuritySupport.SafePath directory);

    @Alias
    @TargetElement(onlyWith = JDK22OrEarlier.class)
    public native void setDumpOnExitDirectory(SecuritySupport.SafePath directory);
}
