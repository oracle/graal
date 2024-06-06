/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.jdwp.impl.DebuggerController;

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
    private final Node currentNode;
    private final RootNode rootNode;
    private final DebugStackFrame debugStackFrame;
    private final DebugScope debugScope;
    private final JDWPContext context;
    private final DebuggerController controller;
    private Object scope;

    public CallFrame(long threadId, byte typeTag, long classId, MethodRef method, long methodId, long codeIndex, Frame frame, Node currentNode, RootNode rootNode,
                    DebugStackFrame debugStackFrame, JDWPContext context, DebuggerController controller) {
        this.threadId = threadId;
        this.typeTag = typeTag;
        this.classId = classId;
        this.method = method;
        this.methodId = methodId;
        this.codeIndex = method != null && method.isObsolete() ? -1 : codeIndex;
        this.frame = frame;
        this.currentNode = currentNode;
        this.rootNode = rootNode;
        this.debugStackFrame = debugStackFrame;
        this.debugScope = debugStackFrame != null ? debugStackFrame.getScope() : null;
        this.context = context;
        this.controller = controller;
    }

    public CallFrame(long threadId, byte typeTag, long classId, long methodId, long codeIndex) {
        this(threadId, typeTag, classId, null, methodId, codeIndex, null, null, null, null, null, null);
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
        if (theScope == null) {
            return null;
        }
        try {
            // See com.oracle.truffle.espresso.EspressoScope.createVariables
            if (INTEROP.isMemberReadable(theScope, "this")) {
                return INTEROP.readMember(theScope, "this");
            }
            return INTEROP.readMember(theScope, "0");
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            if (controller != null) {
                controller.warning(() -> "Unable to read 'this' value from method: " + getMethod() + " with currentNode: " + currentNode.getClass());
            }
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
            if (controller != null) {
                controller.warning(() -> "Unable to write member " + identifier + " from variables");
            }
        }
    }

    public DebugStackFrame getDebugStackFrame() {
        return debugStackFrame;
    }

    private Object getScope() {
        if (scope != null) {
            return scope;
        }
        // look for instrumentable node that should have scope
        Node node = InstrumentableNode.findInstrumentableParent(currentNode);
        if (node != null && NodeLibrary.getUncached().hasScope(node, frame)) {
            try {
                scope = NodeLibrary.getUncached().getScope(node, frame, true);
            } catch (UnsupportedMessageException e) {
                if (controller != null) {
                    controller.warning(() -> "Unable to get scope for " + currentNode.getClass());
                }
            }
        } else {
            if (controller != null) {
                controller.warning(() -> "Unable to get scope for " + currentNode.getClass());
            }
        }
        return scope;
    }

    public DebugValue asDebugValue(Object returnValue) {
        assert debugScope != null;
        return debugScope.convertRawValue(context.getLanguageClass(), returnValue);
    }
}
