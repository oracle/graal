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

import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.SubstrateUtil;

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

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25.0.3-ga/src/hotspot/share/jfr/writers/jfrJavaEventWriter.cpp#L230-L249")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25.0.3-ga/src/hotspot/share/jfr/writers/jfrJavaEventWriter.cpp#L272-L285")
    public static Target_jdk_jfr_internal_event_EventWriter newEventWriter(JfrBuffer buffer) {
        assert JfrBufferAccess.isEmpty(buffer) : "a fresh JFR buffer must be empty";

        long committedPos = buffer.getCommittedPos().rawValue();
        long maxPos = JfrBufferAccess.getDataEnd(buffer).rawValue();

        /*
         * EventWriter objects are created with dummy thread data. The actual thread data
         * needs to be filled in from uninterruptible code.
         */
        return new Target_jdk_jfr_internal_event_EventWriter(committedPos, maxPos, -1, true, false, false);
    }

    /** Update the EventWriter so that it uses the correct buffer and positions. */
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void updateBuffer(Target_jdk_jfr_internal_event_EventWriter writer, JfrBuffer buffer, int uncommittedSize, boolean valid) {
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

    /**
     * Updates the thread data cached in the Java-level {@link EventWriter}.
     * <p>
     * Event writers are notified when chunk rotation changes the JFR epoch. The next commit aborts
     * the current write attempt, and the retry reaches this method before it writes the event again.
     * Keep the thread registration and cached-field update in one uninterruptible section so the
     * epoch cannot change between resolving the current thread id and installing it in the writer.
     */
    @Uninterruptible(reason = "Prevent epoch change.")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25.0.3-ga/src/hotspot/share/jfr/writers/jfrJavaEventWriter.cpp#L225-L228")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25.0.3-ga/src/hotspot/share/jfr/writers/jfrJavaEventWriter.cpp#L251-L270")
    public static void updateThreadData(Target_jdk_jfr_internal_event_EventWriter eventWriter) {
        /*
         * The Java-level EventWriter can stay associated with a long-lived virtual thread
         * across chunk rotations. Re-register the vthread for the current epoch.
         */
        Thread currentThread = Thread.currentThread();
        long currentThreadId = Target_jdk_jfr_internal_JVM.getThreadId(currentThread);

        /*
         * EventWriter objects cache various thread-specific values. Virtual threads use the
         * EventWriter object of their carrier thread, so we need to update all cached values so
         * that they match the virtual thread.
         */
        if (eventWriter.threadID != currentThreadId) {
            eventWriter.threadID = currentThreadId;
            Target_java_lang_Thread tjlt = SubstrateUtil.cast(currentThread, Target_java_lang_Thread.class);
            eventWriter.excluded = tjlt.jfrExcluded;

            if (!eventWriter.excluded) {
                eventWriter.pinVirtualThread = JavaThreads.isVirtual(currentThread);
            }
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
