/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings("unused")
@ExportLibrary(InteropLibrary.class)
final class EventContextObject extends AbstractContextObject {
    private final EventContext context;

    EventContextObject(EventContext context) {
        this.context = context;
    }

    @CompilerDirectives.TruffleBoundary
    static RuntimeException wrap(Object target, int arity, InteropException ex) {
        IllegalStateException ill = new IllegalStateException("Cannot invoke " + target + " with " + arity + " arguments: " + ex.getMessage());
        ill.initCause(ex);
        return ill;
    }

    static RuntimeException rethrow(RuntimeException ex, InteropLibrary interopLib) {
        if (interopLib.isException(ex)) {
            throw ex;
        }
        throw ex;
    }

    @ExportMessage
    static boolean hasMembers(EventContextObject obj) {
        return true;
    }

    @ExportMessage
    static Object getMembers(EventContextObject obj, boolean includeInternal) {
        return MEMBERS;
    }

    @ExportMessage
    @Override
    Object readMember(String member) throws UnknownIdentifierException {
        return super.readMember(member);
    }

    @ExportMessage
    static boolean isMemberReadable(EventContextObject obj, String member) {
        return MEMBERS.contains(member);
    }

    @ExportMessage
    static Object invokeMember(EventContextObject obj, String member, Object[] args) throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
        if ("returnNow".equals(member)) {
            throw InsightHookNode.returnNow(args);
        }
        if ("returnValue".equals(member)) {
            if (args.length == 0 || !(args[0] instanceof VariablesObject)) {
                return NullObject.nullCheck(null);
            }
            VariablesObject vars = (VariablesObject) args[0];
            return vars.getReturnValue();
        }
        if ("iterateFrames".equals(member)) {
            return iterateFrames(args, obj);
        }
        throw UnknownIdentifierException.create(member);
    }

    @CompilerDirectives.TruffleBoundary
    private static Object iterateFrames(Object[] args, EventContextObject obj) throws ArityException, UnsupportedTypeException {
        if (args.length == 0) {
            throw ArityException.create(0, 0, args.length);
        }
        final NodeLibrary lib = NodeLibrary.getUncached();
        final InteropLibrary iop = InteropLibrary.getUncached();
        final Object callback = args[0];
        if (!iop.isExecutable(callback)) {
            Object displayCallback = iop.toDisplayString(callback, false);
            throw UnsupportedTypeException.create(new Object[]{callback}, "Cannot execute " + displayCallback);
        }
        Object retValue = Truffle.getRuntime().iterateFrames((frameInstance) -> {
            final Node n = frameInstance.getCallNode();
            if (n == null || n.getRootNode() == null || n.getRootNode().isInternal()) {
                // skip top most record of the instrument and any internal frames
                return null;
            }
            LocationObject location = new LocationObject(n);
            final SourceSection ss = location.getInstrumentedSourceSection();
            if (ss == null || ss.getSource().isInternal()) {
                // skip internal frames
                return null;
            }
            final Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_WRITE);
            Node instrumentableNode = findInstrumentableParent(n);
            if (instrumentableNode != null && lib.hasScope(instrumentableNode, frame)) {
                try {
                    Object frameVars = new CurrentScopeView(lib.getScope(instrumentableNode, frame, false));
                    Object ret = iop.execute(callback, location, frameVars);
                    return iop.isNull(ret) ? null : ret;
                } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException ex) {
                    throw InsightException.raise(ex);
                }
            }
            return null;
        });
        return NullObject.nullCheck(retValue);
    }

    @ExportMessage
    static boolean isMemberInvocable(EventContextObject obj, String member) {
        return "returnNow".equals(member) || "returnValue".equals(member) || "iterateFrames".equals(member);
    }

    @Override
    Node getInstrumentedNode() {
        return context.getInstrumentedNode();
    }

    @Override
    SourceSection getInstrumentedSourceSection() {
        return context.getInstrumentedSourceSection();
    }

    @ExportMessage
    public Object toDisplayString(boolean allowSideEffects) {
        return toStringImpl();
    }

    @Override
    public String toString() {
        return toStringImpl();
    }

    @CompilerDirectives.TruffleBoundary
    private String toStringImpl() {
        SourceSection ss = getInstrumentedSourceSection();
        final Node n = getInstrumentedNode();
        if (ss == null || n == null) {
            return super.toString();
        }
        return n.getRootNode().getName() + " (" +
                        ss.getSource().getName() + ":" +
                        ss.getStartLine() + ":" + ss.getStartColumn() + ")";
    }

    static Node findInstrumentableParent(Node node) {
        Node p = node;
        while (p != null) {
            Node n = p;
            if (n instanceof InstrumentableNode.WrapperNode) {
                n = ((InstrumentableNode.WrapperNode) n).getDelegateNode();
            }
            if (n instanceof InstrumentableNode && ((InstrumentableNode) n).isInstrumentable()) {
                return n;
            }
            p = p.getParent();
        }
        return null;
    }
}
