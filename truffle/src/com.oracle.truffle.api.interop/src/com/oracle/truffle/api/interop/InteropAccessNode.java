/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropAccessNodeFactory.CachedNodeGen;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;

@SuppressWarnings("deprecation")
abstract class InteropAccessNode extends Node {

    static final int ARG0_RECEIVER = 0;
    protected static final int CACHE_SIZE = 8;
    protected final Message message;

    protected InteropAccessNode(Message message) {
        this.message = message;
    }

    @SuppressWarnings("unused")
    public final Object execute(TruffleObject receiver) throws InteropException {
        return checkInteropType(executeImpl(receiver, new Object[]{receiver}));
    }

    public final Object executeOrFalse(TruffleObject receiver) throws InteropException {
        try {
            return checkInteropType(executeImplInterop(receiver, new Object[]{receiver}));
        } catch (UnsupportedMessageException ex) {
            enterDefaultUnsupported();
            return false;
        }
    }

    protected void enterDefaultUnsupported() {
    }

    @SuppressWarnings("unused")
    public final Object execute(TruffleObject receiver, Object[] arguments) throws InteropException {
        return checkInteropType(executeImpl(receiver, insertArg1(arguments, receiver)));
    }

    @SuppressWarnings("unused")
    public final Object execute(TruffleObject receiver, Object arg0) throws InteropException {
        return checkInteropType(executeImpl(receiver, new Object[]{receiver, checkInteropType(arg0)}));
    }

    @SuppressWarnings("unused")
    public final Object execute(TruffleObject receiver, Object arg0, Object arg1) throws InteropException {
        return checkInteropType(executeImpl(receiver, new Object[]{receiver, checkInteropType(arg0), checkInteropType(arg1)}));
    }

    @SuppressWarnings("unused")
    public final Object execute(TruffleObject receiver, Object arg0, Object[] arguments) throws InteropException {
        return checkInteropType(executeImpl(receiver, insertArg2(arguments, receiver, arg0)));
    }

    @Deprecated
    public final Object executeOld(TruffleObject receiver, Object[] arguments) {
        return checkInteropType(executeImpl(receiver, insertArg1(arguments, receiver)));
    }

    private Object[] insertArg1(Object[] arguments, Object arg0) {
        int length = profileLength(arguments.length);
        Object[] newArguments = new Object[length + 1];
        newArguments[0] = checkInteropType(arg0);
        arraycopy(arguments, 0, newArguments, 1, length);
        return newArguments;
    }

    private Object[] insertArg2(Object[] arguments, Object arg0, Object arg1) {
        int length = profileLength(arguments.length);
        Object[] newArguments = new Object[length + 2];
        newArguments[0] = checkInteropType(arg0);
        newArguments[1] = checkInteropType(arg1);
        arraycopy(arguments, 0, newArguments, 2, length);
        return newArguments;
    }

    private static void arraycopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        for (int i = 0; i < length; i++) {
            dest[destPos + i] = checkInteropType(src[srcPos + i]);
        }
    }

    static Object checkInteropType(Object obj) {
        assert checkInteropTypeImpl(obj);
        return obj;
    }

    private static boolean checkInteropTypeImpl(Object obj) {
        if (AssertUtils.isInteropValue(obj)) {
            return true;
        }
        CompilerDirectives.transferToInterpreter();
        Class<?> clazz = obj != null ? obj.getClass() : null;
        return yieldAnError(clazz);
    }

    private static boolean yieldAnError(Class<?> clazz) {
        CompilerDirectives.transferToInterpreter();
        StringBuilder sb = new StringBuilder();
        sb.append(clazz == null ? "null" : clazz.getName());
        sb.append(" isn't allowed Truffle interop type!\n");
        if (clazz == null) {
            throw new NullPointerException(sb.toString());
        } else {
            throw new ClassCastException(sb.toString());
        }
    }

    @TruffleBoundary
    protected CallTarget createGenericMessageTarget(TruffleObject receiver) {
        assert assertHandlesItself(receiver);
        return createMessageTarget(receiver.getForeignAccess());
    }

    private static boolean assertHandlesItself(TruffleObject receiver) {
        if (receiver.getForeignAccess() != null) {
            // to exercise the assertion in createCanHandleInlinedCallNode:
            createCanHandleInlinedCallNode(receiver.getForeignAccess(), receiver);
        }
        return true;
    }

    protected final CallTarget createMessageTarget(ForeignAccess fa) {
        CallTarget ct = null;
        if (fa != null) {
            ct = fa.access(message);
        }
        if (ct == null) {
            return Truffle.getRuntime().createCallTarget(new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    throw UnsupportedMessageException.raise(message);
                }
            });
        }
        return ct;
    }

    protected static boolean acceptCached(TruffleObject receiver, ForeignAccess foreignAccess, DirectCallNode canHandleCall) {
        if (canHandleCall != null) {
            return (boolean) canHandleCall.call(new Object[]{receiver});
        } else if (foreignAccess != null) {
            return foreignAccess.canHandle(receiver);
        } else {
            return false;
        }
    }

    protected static DirectCallNode createInlinedCallNode(CallTarget target) {
        if (target == null) {
            return null;
        }
        DirectCallNode callNode = DirectCallNode.create(target);
        callNode.forceInlining();
        return callNode;
    }

    @TruffleBoundary
    protected static DirectCallNode createCanHandleInlinedCallNode(ForeignAccess access, TruffleObject receiver) {
        if (access != null) {
            DirectCallNode callNode = createInlinedCallNode(access.checkLanguage());
            assert acceptCached(receiver, access, callNode) : "foreign access for " + receiver.getClass() + " (" + access + ") does not handle its own objects";
            return callNode;
        } else {
            return null;
        }
    }

    protected abstract int profileLength(int length);

    // Only to declare that it can throw InteropException
    @SuppressWarnings("unused")
    private Object executeImplInterop(TruffleObject receiver, Object[] arguments) throws InteropException {
        return executeImpl(receiver, arguments);
    }

    protected abstract Object executeImpl(TruffleObject receiver, Object[] arguments);

    public static InteropAccessNode create(Message message) {
        return CachedNodeGen.create(message);
    }

    public static InteropAccessNode getUncached(Message message) {
        InteropAccessNode node = message.uncached;
        if (node == null) {
            // TODO do this eagerly
            message.uncached = node = new InteropAccessNode.Uncached(message);
        }
        return node;
    }

    static final class Uncached extends InteropAccessNode {

        Uncached(Message message) {
            super(message);
        }

        @Override
        @TruffleBoundary
        protected int profileLength(int length) {
            return length;
        }

        @Override
        @TruffleBoundary
        protected Object executeImpl(TruffleObject receiver, Object[] arguments) {
            return createGenericMessageTarget(receiver).call(arguments);
        }

    }

    abstract static class CachedNode extends InteropAccessNode {

        static final int ARG0_RECEIVER = 0;

        protected static final int CACHE_SIZE = 8;
        @CompilationFinal private int previousLength = -2;
        private final BranchProfile profileDefaultUnsupported = BranchProfile.create();

        protected CachedNode(Message message) {
            super(message);
        }

        @Override
        protected void enterDefaultUnsupported() {
            profileDefaultUnsupported.enter();
        }

        @Override
        protected int profileLength(int length) {
            int returnLength = length;
            if (previousLength != -1) {
                if (previousLength == length) {
                    returnLength = previousLength;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    if (previousLength == -2) {
                        previousLength = length;
                    } else {
                        previousLength = -1;
                    }
                }
            }
            return returnLength;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "acceptCached(receiver, foreignAccess, canHandleCall)", limit = "CACHE_SIZE")
        protected static Object doCached(TruffleObject receiver, Object[] arguments,
                        @Cached("receiver.getForeignAccess()") ForeignAccess foreignAccess,
                        @Cached("createInlinedCallNode(createMessageTarget(foreignAccess))") DirectCallNode sendMessageCall,
                        @Cached("createCanHandleInlinedCallNode(foreignAccess, receiver)") DirectCallNode canHandleCall) {
            return sendMessageCall.call(arguments);
        }

        @Specialization
        protected Object doGeneric(TruffleObject receiver, Object[] arguments,
                        @Cached("create()") IndirectCallNode indirectCall) {
            return indirectCall.call(createGenericMessageTarget(receiver), arguments);
        }

    }
}
