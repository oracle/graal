/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.jdwp.impl.JDWPLogger;

public final class CallFrame {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();
    public static final Object INVALID_VALUE = new Object();

    private final byte typeTag;
    private final long classId;
    private final MethodRef method;
    private final long methodId;
    private final long codeIndex;
    private final long threadId;
    private final Frame frame;
    private final RootNode rootNode;
    private final DebugStackFrame debugStackFrame;
    private final DebugScope debugScope;
    private final JDWPContext context;
    private Object scope;

    public CallFrame(long threadId, byte typeTag, long classId, MethodRef method, long methodId, long codeIndex, Frame frame, RootNode rootNode,
                    DebugStackFrame debugStackFrame, JDWPContext context) {
        this.threadId = threadId;
        this.typeTag = typeTag;
        this.classId = classId;
        this.method = method;
        this.methodId = methodId;
        this.codeIndex = method != null && method.isObsolete() ? -1 : codeIndex;
        this.frame = frame;
        this.rootNode = rootNode;
        this.debugStackFrame = debugStackFrame;
        this.debugScope = debugStackFrame != null ? debugStackFrame.getScope() : null;
        this.context = context;
    }

    public CallFrame(long threadId, byte typeTag, long classId, long methodId, long codeIndex) {
        this(threadId, typeTag, classId, null, methodId, codeIndex, null, null, null, null);
    }

    public byte getTypeTag() {
        return typeTag;
    }

    public long getClassId() {
        return classId;
    }

    public MethodRef getMethod() {
        return method;
    }

    public long getMethodId() {
        if (method == null) {
            return methodId;
        }
        return method.isObsolete() ? 0 : methodId;
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
        Object theScope = getScope();
        try {
            return theScope != null ? INTEROP.readMember(theScope, "this") : null;
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            JDWPLogger.log("Unable to read 'this' value from method: %s", JDWPLogger.LogLevel.ALL, getMethod());
            return INVALID_VALUE;
        }
    }

    public Object getVariable(String identifier) throws InteropException {
        Object theScope = getScope();
        return theScope != null ? INTEROP.readMember(theScope, identifier) : null;
    }

    public void setVariable(Object value, String identifier) {
        Object theScope = getScope();
        if (theScope == null) {
            return;
        }
        try {
            INTEROP.writeMember(theScope, identifier, value);
        } catch (Exception e) {
            JDWPLogger.log("Unable to write member %s from variables", JDWPLogger.LogLevel.ALL, identifier);
        }
    }

    public DebugStackFrame getDebugStackFrame() {
        return debugStackFrame;
    }

    private Object getScope() {
        if (scope != null) {
            return scope;
        }
        Node rawNode = debugStackFrame.getRawNode(context.getLanguageClass());

        if (rawNode == null) {
            JDWPLogger.log("Unable to get raw node for root %s", JDWPLogger.LogLevel.ALL, rootNode);
        } else {
            if (!(rawNode instanceof InstrumentableNode)) {
                rawNode = context.getInstrumentableNode(rawNode);
            }
            try {
                scope = NodeLibrary.getUncached().getScope(rawNode, frame, true);
            } catch (UnsupportedMessageException e) {
                JDWPLogger.log("Unable to get scope for %s", JDWPLogger.LogLevel.ALL, rawNode.getClass());
            }
        }
        return scope;
    }

    public DebugValue asDebugValue(Object returnValue) {
        assert debugScope != null;
        return debugScope.convertRawValue(context.getLanguageClass(), returnValue);
    }
}
