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
package com.oracle.svm.interpreter.debug;

import com.oracle.svm.interpreter.metadata.MetadataUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.nativeimage.ImageSingletons;

public interface DebuggerEvents {

    /**
     * Enable/disable events in a specific thread or globally. <b>An event is generated for a
     * particular thread if it is enabled either at the thread or global levels.</b>
     *
     * @param thread thread where the event is enabled/disabled, or{@code null} to specify global
     *            scope (all threads)
     * @param eventKind one of the events in {@link EventKind}
     * @param enable true/false to enable/disable the event
     */
    void setEventEnabled(Thread thread, EventKind eventKind, boolean enable);

    /**
     * Checks if an event is enabled for a specified thread or globally. <b>An event is generated
     * for a particular thread if it is enabled either at the thread or global levels.</b>
     *
     * @param thread where the event is enabled/disabled, or{@code null} to specify global * scope
     *            (all threads)
     * @param eventKind one of the events in {@link EventKind}
     */
    boolean isEventEnabled(Thread thread, EventKind eventKind);

    /**
     * Enable/disable a breakpoint on the specified method and bytecode index.
     *
     * @throws IllegalArgumentException if the method doesn't have bytecodes, or the bytecode index
     *             is out of range, or if the bytecode index is not a valid b
     */
    void toggleBreakpoint(ResolvedJavaMethod method, int bci, boolean enable);

    void toggleMethodEnterEvent(ResolvedJavaType clazz, boolean enable);

    void toggleMethodExitEvent(ResolvedJavaType clazz, boolean enable);

    /**
     * Sets the {@link SteppingControl stepping information} associated with a thread. This enables
     * stepping for the specified thread, enabling stepping is not enough, the stepping events must
     * be enabled e.g. {@code Debugger.setEventEnabled(GLOBAL|threadId, EventKind.SINGLE_STEP, true}
     *
     * For line-stepping, the {@link SteppingControl#setStartingLocation(Location) starting location
     * can be set}, otherwise, any location will raise the stepping event.
     */
    void setSteppingFromLocation(Thread thread, int depth, int size, Location location);

    default void setStepping(Thread thread, int depth, int size) {
        setSteppingFromLocation(thread, depth, size, null);
    }

    /**
     * Removes the {@link SteppingControl stepping information} associated with a thread. This
     * cancels stepping for the current thread.
     */
    void clearStepping(Thread thread);

    void setEventHandler(EventHandler eventHandler);

    EventHandler getEventHandler();

    /**
     * Returns the {@link SteppingControl stepping information} associated with a thread.
     */
    SteppingControl getSteppingControl(Thread thread);

    static DebuggerEvents singleton() {
        return MetadataUtil.requireNonNull(ImageSingletons.lookup(DebuggerEvents.class));
    }
}
