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
package com.oracle.svm.jdwp.bridge;

public interface JDWPBridge {

    int UNKNOWN_BCI = -1;
    int UNINITIALIZED_LINE_NUMBER = -2;

    /* special thread id */
    long GLOBAL = 0;

    /**
     * Enable/disable events in a specific thread or globally. <b>An event is generated for a
     * particular thread if it is enabled either at the thread or global levels.</b>
     *
     * @param threadId threadId of application side, or GLOBAL to specify global scope
     * @param eventKind one of the events in {@link EventKind}
     * @param enable true/false to enable/disable the event
     */
    void setEventEnabled(long threadId, int eventKind, boolean enable);

    /**
     * Checks if an event is enabled for a specified thread or globally. <b>An event is generated
     * for a particular thread if it is enabled either at the thread or global levels.</b>
     *
     * @param threadId threadId of application side, or GLOBAL to specify global scope
     * @param eventKind one of the events in {@link EventKind}
     */
    boolean isEventEnabled(long threadId, int eventKind);

    /**
     * Enable/disable a breakpoint on the specified method and bytecode index.
     *
     * @throws IllegalArgumentException if the method doesn't have bytecodes, or the bytecode index
     *             is out of range, or if the bytecode index is not a valid b
     */
    void toggleBreakpoint(long methodId, int bci, boolean enable);

    void toggleMethodEnterEvent(long clazzId, boolean enable);

    void toggleMethodExitEvent(long clazzId, boolean enable);

    /**
     * Sets the stepping information associated with a thread. This enables stepping for the
     * specified thread, enabling stepping is not enough, the stepping events must be enabled e.g.
     * {@code Debugger.setEventEnabled(GLOBAL|threadId, EventKind.SINGLE_STEP, true}
     *
     * For line-stepping, the starting location can be set, otherwise, any location will raise the
     * stepping event.
     */
    void setSteppingFromLocation(long threadId, int depth, int size, long methodId, int bci, int lineNumber);

    default void setStepping(long threadId, int depth, int size) {
        setSteppingFromLocation(threadId, depth, size, 0, UNKNOWN_BCI, UNINITIALIZED_LINE_NUMBER);
    }

    /**
     * Removes the stepping information associated with a thread. This cancels stepping for the
     * current thread.
     */
    void clearStepping(long threadId);

    Packet dispatch(Packet packet) throws JDWPException;

    /**
     * Returns the ThreadStatus constant.
     *
     * @throws JDWPException when the {@code threadId} does not represent a {@code Thread}.
     */
    int getThreadStatus(long threadId) throws JDWPException;

    long threadSuspend(long threadId);

    long threadResume(long suspendId);

    long[] vmSuspend(long[] ignoredThreadIds);

    void vmResume(long[] ignoredThreadIds);

    /**
     * Request to send notifications about thread start/death.
     *
     * @param start <code>true</code> for thread start, <code>false</code> for thread death.
     * @param enable <code>true</code> to enable the notifications, <code>false</code> to disable.
     */
    void setThreadRequest(boolean start, boolean enable);

    String getSystemProperty(String key);

    long typeRefIndexToId(int typeRefIndex);

    long fieldRefIndexToId(int fieldRefIndex);

    long methodRefIndexToId(int methodRefIndex);

    int typeRefIdToIndex(long typeRefId);

    int fieldRefIdToIndex(long fieldRefId);

    int methodRefIdToIndex(long methodRefId);

    String currentWorkingDirectory();

    int[] typeStatus(long... typeIds);

    StackFrame[] getThreadFrames(long threadId);

    boolean isCurrentThreadVirtual();

    long getCurrentThis();
}
