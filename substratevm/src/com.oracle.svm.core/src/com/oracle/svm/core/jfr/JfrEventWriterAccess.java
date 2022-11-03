/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;

/**
 * Used to access the Java event writer class, see {@code jdk.jfr.internal.EventWriter}.
 */
public final class JfrEventWriterAccess {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final Field startPosition;
    private static final Field startPositionAddress;
    private static final Field currentPosition;
    private static final Field maxPosition;
    private static final Field valid;
    static {
        Class<?> declaringClass = getEventWriterClass();
        startPosition = ReflectionUtil.lookupField(declaringClass, "startPosition");
        startPositionAddress = ReflectionUtil.lookupField(declaringClass, "startPositionAddress");
        currentPosition = ReflectionUtil.lookupField(declaringClass, "currentPosition");
        maxPosition = ReflectionUtil.lookupField(declaringClass, "maxPosition");
        valid = ReflectionUtil.lookupField(declaringClass, "valid");
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Class<?> getEventWriterClass() {
        String className;
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            className = "jdk.jfr.internal.event.EventWriter";
        } else {
            className = "jdk.jfr.internal.EventWriter";
        }
        return ReflectionUtil.lookupClass(false, className);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private JfrEventWriterAccess() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setStartPosition(Target_jdk_jfr_internal_EventWriter writer, long value) {
        UNSAFE.putLong(writer, UNSAFE.objectFieldOffset(startPosition), value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setStartPositionAddress(Target_jdk_jfr_internal_EventWriter writer, long value) {
        UNSAFE.putLong(writer, UNSAFE.objectFieldOffset(startPositionAddress), value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setCurrentPosition(Target_jdk_jfr_internal_EventWriter writer, long value) {
        UNSAFE.putLong(writer, UNSAFE.objectFieldOffset(currentPosition), value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setMaxPosition(Target_jdk_jfr_internal_EventWriter writer, long value) {
        UNSAFE.putLong(writer, UNSAFE.objectFieldOffset(maxPosition), value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setValid(Target_jdk_jfr_internal_EventWriter writer, boolean value) {
        UNSAFE.putBooleanVolatile(writer, UNSAFE.objectFieldOffset(valid), value);
    }
}
