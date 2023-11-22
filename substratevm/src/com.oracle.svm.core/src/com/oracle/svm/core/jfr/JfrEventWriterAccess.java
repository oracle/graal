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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;
import jdk.jfr.internal.event.EventWriter;

/**
 * Used to access the Java event writer class, see {@link EventWriter}.
 */
public final class JfrEventWriterAccess {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final Field VALID_FIELD = ReflectionUtil.lookupField(EventWriter.class, "valid");

    @Platforms(Platform.HOSTED_ONLY.class)
    private JfrEventWriterAccess() {
    }

    public static Target_jdk_jfr_internal_event_EventWriter newEventWriter(JfrBuffer buffer, boolean isCurrentThreadExcluded) {
        assert JfrBufferAccess.isEmpty(buffer) : "a fresh JFR buffer must be empty";

        long committedPos = buffer.getCommittedPos().rawValue();
        long maxPos = JfrBufferAccess.getDataEnd(buffer).rawValue();
        long jfrThreadId = SubstrateJVM.getCurrentThreadId();
        return new Target_jdk_jfr_internal_event_EventWriter(committedPos, maxPos, jfrThreadId, true, isCurrentThreadExcluded);
    }

    /** Update the EventWriter so that it uses the correct buffer and positions. */
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void update(Target_jdk_jfr_internal_event_EventWriter writer, JfrBuffer buffer, int uncommittedSize, boolean valid) {
        assert SubstrateJVM.getThreadLocal().getJavaBuffer() == buffer;
        assert JfrBufferAccess.verify(buffer);

        Pointer committedPos = buffer.getCommittedPos();
        Pointer currentPos = committedPos.add(uncommittedSize);
        Pointer maxPos = JfrBufferAccess.getDataEnd(buffer);

        /*
         * The field "startPosition" in the JDK class EventWriter refers to the committed position
         * and not to the start of the buffer.
         */
        writer.startPosition = committedPos.rawValue();
        writer.currentPosition = currentPos.rawValue();
        writer.maxPosition = maxPos.rawValue();
        if (!valid) {
            markAsInvalid(writer);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void markAsInvalid(Target_jdk_jfr_internal_event_EventWriter writer) {
        /*
         * The VM should never write true (only the JDK code may do that).
         *
         * The field EventWriter.valid is not declared volatile, so we use Unsafe to do a volatile
         * store.
         */
        U.putBooleanVolatile(writer, U.objectFieldOffset(VALID_FIELD), false);
    }
}
