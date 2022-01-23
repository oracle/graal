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
package com.oracle.svm.jfr;

import java.lang.reflect.Field;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.util.ReflectionUtil;

import jdk.jfr.internal.EventWriter;
import sun.misc.Unsafe;

/**
 * Used to access the Java event writer class, see {@link jdk.jfr.internal.EventWriter}.
 */
public final class JfrEventWriterAccess {
    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    private static final Field startPosition = ReflectionUtil.lookupField(EventWriter.class, "startPosition");
    private static final Field startPositionAddress = ReflectionUtil.lookupField(EventWriter.class, "startPositionAddress");
    private static final Field currentPosition = ReflectionUtil.lookupField(EventWriter.class, "currentPosition");
    private static final Field maxPosition = ReflectionUtil.lookupField(EventWriter.class, "maxPosition");
    private static final Field valid = ReflectionUtil.lookupField(EventWriter.class, "valid");

    @Platforms(Platform.HOSTED_ONLY.class)
    private JfrEventWriterAccess() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setStartPosition(EventWriter writer, long value) {
        UNSAFE.putLong(writer, UNSAFE.objectFieldOffset(startPosition), value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setStartPositionAddress(EventWriter writer, long value) {
        UNSAFE.putLong(writer, UNSAFE.objectFieldOffset(startPositionAddress), value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setCurrentPosition(EventWriter writer, long value) {
        UNSAFE.putLong(writer, UNSAFE.objectFieldOffset(currentPosition), value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setMaxPosition(EventWriter writer, long value) {
        UNSAFE.putLong(writer, UNSAFE.objectFieldOffset(maxPosition), value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setValid(EventWriter writer, boolean value) {
        UNSAFE.putBooleanVolatile(writer, UNSAFE.objectFieldOffset(valid), value);
    }
}
