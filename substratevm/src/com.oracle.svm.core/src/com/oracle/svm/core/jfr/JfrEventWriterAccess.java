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
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;

/**
 * Used to access the Java event writer class, see {@code jdk.jfr.internal.EventWriter}.
 */
public final class JfrEventWriterAccess {
    private static final Unsafe U = Unsafe.getUnsafe();
    /*
     * The fields "startPosition" and "startPositionAddress" in the JDK class EventWriter refer to
     * the committed position and not to the start of the buffer.
     */
    private static final Field COMMITTED_POSITION_FIELD = ReflectionUtil.lookupField(getEventWriterClass(), "startPosition");
    private static final Field COMMITTED_POSITION_ADDRESS_FIELD = ReflectionUtil.lookupField(getEventWriterClass(), "startPositionAddress");
    private static final Field CURRENT_POSITION_FIELD = ReflectionUtil.lookupField(getEventWriterClass(), "currentPosition");
    private static final Field MAX_POSITION_FIELD = ReflectionUtil.lookupField(getEventWriterClass(), "maxPosition");
    private static final Field VALID_FIELD = ReflectionUtil.lookupField(getEventWriterClass(), "valid");

    @Platforms(Platform.HOSTED_ONLY.class)
    private JfrEventWriterAccess() {
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

    public static Target_jdk_jfr_internal_EventWriter newEventWriter(JfrBuffer buffer, boolean isCurrentThreadExcluded) {
        assert JfrBufferAccess.isEmpty(buffer) : "a fresh JFR buffer must be empty";

        long committedPos = buffer.getCommittedPos().rawValue();
        long maxPos = JfrBufferAccess.getDataEnd(buffer).rawValue();
        long addressOfCommittedPos = JfrBufferAccess.getAddressOfCommittedPos(buffer).rawValue();
        long jfrThreadId = SubstrateJVM.getCurrentThreadId();
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            return new Target_jdk_jfr_internal_EventWriter(committedPos, maxPos, addressOfCommittedPos, jfrThreadId, true, isCurrentThreadExcluded);
        } else {
            return new Target_jdk_jfr_internal_EventWriter(committedPos, maxPos, addressOfCommittedPos, jfrThreadId, true);
        }
    }

    /** Update the EventWriter so that it uses the correct buffer and positions. */
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void update(Target_jdk_jfr_internal_EventWriter writer, JfrBuffer buffer, int uncommittedSize, boolean valid) {
        assert SubstrateJVM.getThreadLocal().getJavaBuffer() == buffer;
        assert JfrBufferAccess.verify(buffer);

        Pointer committedPos = buffer.getCommittedPos();
        Pointer addressOfCommittedPos = JfrBufferAccess.getAddressOfCommittedPos(buffer);
        Pointer currentPos = committedPos.add(uncommittedSize);
        Pointer maxPos = JfrBufferAccess.getDataEnd(buffer);

        U.putLong(writer, U.objectFieldOffset(COMMITTED_POSITION_FIELD), committedPos.rawValue());
        U.putLong(writer, U.objectFieldOffset(COMMITTED_POSITION_ADDRESS_FIELD), addressOfCommittedPos.rawValue());
        U.putLong(writer, U.objectFieldOffset(CURRENT_POSITION_FIELD), currentPos.rawValue());
        U.putLong(writer, U.objectFieldOffset(MAX_POSITION_FIELD), maxPos.rawValue());
        if (!valid) {
            markAsInvalid(writer);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void markAsInvalid(Target_jdk_jfr_internal_EventWriter writer) {
        /* The VM should never write true (only the JDK code may do that). */
        U.putBooleanVolatile(writer, U.objectFieldOffset(VALID_FIELD), false);
    }
}
