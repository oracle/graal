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

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.jdwp.impl.JDWPLogger;

import java.util.Iterator;

public final class CallFrame {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    private final byte typeTag;
    private final long classId;
    private final long methodId;
    private final long codeIndex;
    private final long threadId;
    private final Frame frame;
    private final RootNode rootNode;
    private final TruffleInstrument.Env env;
    private final DebugStackFrame debugStackFrame;

    public CallFrame(long threadId, byte typeTag, long classId, long methodId, long codeIndex, Frame frame, RootNode rootNode,
                    TruffleInstrument.Env env, DebugStackFrame debugStackFrame) {
        this.threadId = threadId;
        this.typeTag = typeTag;
        this.classId = classId;
        this.methodId = methodId;
        this.codeIndex = codeIndex;
        this.frame = frame;
        this.rootNode = rootNode;
        this.env = env;
        this.debugStackFrame = debugStackFrame;
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

    public Frame getFrame() {
        return frame;
    }

    public RootNode getRootNode() {
        return rootNode;
    }

    public Object getThisValue() {
        Scope scope = getScope();
        return scope != null ? scope.getReceiver() : null;
    }

    public Object getVariable(String identifier) {
        Scope scope = getScope();
        if (scope == null) {
            return null;
        }
        try {
            return INTEROP.readMember(scope.getVariables(), identifier);
        } catch (Exception e) {
            JDWPLogger.log("Unable to read member %s from variables", JDWPLogger.LogLevel.ALL, identifier);
            return null;
        }
    }

    public void setVariable(Object value, String identifier) {
        Scope scope = getScope();
        if (scope == null) {
            return;
        }
        try {
            INTEROP.writeMember(scope.getVariables(), identifier, value);
        } catch (Exception e) {
            JDWPLogger.log("Unable to write member %s from variables", JDWPLogger.LogLevel.ALL, identifier);
        }
    }

    public DebugStackFrame getDebugStackFrame() {
        return debugStackFrame;
    }

    private Scope getScope() {
        Iterator<Scope> it = env.findLocalScopes(rootNode, getFrame()).iterator();
        return it.hasNext() ? it.next() : null;
    }
}
