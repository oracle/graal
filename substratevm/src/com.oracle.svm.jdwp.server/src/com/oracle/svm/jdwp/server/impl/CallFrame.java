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
package com.oracle.svm.jdwp.server.impl;

import com.oracle.svm.jdwp.bridge.FrameId;
import com.oracle.svm.jdwp.bridge.StackFrame;

public final class CallFrame {

    private final long frameId;
    private final ThreadRef thread;
    private final long classId;
    private final byte typeTag;
    private final long methodId;
    private final long codeIndex;

    public static CallFrame fromStackFrame(ThreadRef threadRef, StackFrame stackFrame) {
        return new CallFrame(
                        threadRef,
                        stackFrame.classId(),
                        stackFrame.typeTag(),
                        stackFrame.methodId(),
                        stackFrame.bci(),
                        stackFrame.frameDepth());
    }

    CallFrame(ThreadRef thread, long classId, byte typeTag, long methodId, long bci, int frameDepth) {
        this.frameId = FrameId.createFrameId(thread.getFrameGeneration(), frameDepth);
        this.thread = thread;
        this.classId = classId;
        this.typeTag = typeTag;
        this.methodId = methodId;
        this.codeIndex = bci;
    }

    public long getFrameId() {
        return frameId;
    }

    public byte getTypeTag() {
        return typeTag;
    }

    public long getClassId() {
        return classId;
    }

    public long getMethodId() {
        return methodId;
    }

    public long getCodeIndex() {
        return codeIndex;
    }

    public long getThreadId() {
        return thread.getThreadId();
    }
}
