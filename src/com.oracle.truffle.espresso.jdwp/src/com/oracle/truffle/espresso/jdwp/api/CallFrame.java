/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jdwp.api;

import com.oracle.truffle.api.frame.FrameInstance;

public final class CallFrame {

    private final byte typeTag;
    private final long classId;
    private final long methodId;
    private final long codeIndex;
    private final long threadId;
    private final FrameInstance frameInstance;
    private final Object thisValue;
    private final Object[] variables;

    public CallFrame(long threadId, byte typeTag, long classId, long methodId, long codeIndex, FrameInstance frameInstance, Object thisValue, Object[] variables) {
        this.threadId = threadId;
        this.typeTag = typeTag;
        this.classId = classId;
        this.methodId = methodId;
        this.codeIndex = codeIndex;
        this.frameInstance = frameInstance;
        this.thisValue = thisValue;
        this.variables = variables;
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
        return threadId;
    }

    public FrameInstance getFrameInstance() {
        return frameInstance;
    }

    public Object getThisValue() {
        return thisValue;
    }

    public Object[] getVariables() {
        return variables;
    }
}
