/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

abstract class InteropAccessNode extends Node {

    static final int ARG0_RECEIVER = 0;

    protected static final int CACHE_SIZE = 8;
    protected final Message message;
    @CompilationFinal private int previousLength = -2;
    private final BranchProfile profileDefaultUnsupported = BranchProfile.create();

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
            profileDefaultUnsupported.enter();
            return false;
        }
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
        if (obj instanceof TruffleObject) {
            return true;
        }
        if (obj == null) {
            CompilerDirectives.transferToInterpreter();
            return yieldAnError(null);
        }
        Class<?> clazz = obj.getClass();
        if (clazz == Byte.class ||
                        clazz == Short.class ||
                        clazz == Integer.class ||
                        clazz == Long.class ||
                        clazz == Float.class ||
                        clazz == Double.class ||
                        clazz == Character.class ||
                        clazz == Boolean.class ||
                        clazz == String.class) {
            return true;
        } else {
            CompilerDirectives.transferToInterpreter();
            return yieldAnError(obj.getClass());
        }
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

    private int profileLength(int length) {
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

    // Only to declare that it can throw InteropException
    @SuppressWarnings("unused")
    private Object executeImplInterop(TruffleObject receiver, Object[] arguments) throws InteropException {
        return executeImpl(receiver, arguments);
    }

    protected abstract Object executeImpl(TruffleObject receiver, Object[] arguments);

    @SuppressWarnings("unused")
    @Specialization(guards = "acceptCached(receiver, foreignAccess, canHandleCall)", limit = "CACHE_SIZE")
    protected static Object doCached(TruffleObject receiver, Object[] arguments,
                    @Cached("receiver.getForeignAccess()") ForeignAccess foreignAccess,
                    @Cached("createInlinedCallNode(createMessageTarget(foreignAccess))") DirectCallNode sendMessageCall,
                    @Cached("createInlinedCallNode(createCanHandleTarget(foreignAccess))") DirectCallNode canHandleCall) {
        return sendMessageCall.call(arguments);
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

    @Specialization
    protected Object doGeneric(TruffleObject receiver, Object[] arguments, @Cached("create()") IndirectCallNode indirectCall) {
        return indirectCall.call(createGenericMessageTarget(receiver), arguments);
    }

    @TruffleBoundary
    protected CallTarget createCanHandleTarget(ForeignAccess access) {
        return access != null ? access.checkLanguage() : null;
    }

    @TruffleBoundary
    protected CallTarget createGenericMessageTarget(TruffleObject receiver) {
        return createMessageTarget(receiver.getForeignAccess());
    }

    protected CallTarget createMessageTarget(ForeignAccess fa) {
        CallTarget ct = null;
        if (fa != null) {
            ct = fa.access(message);
        }
        if (ct == null) {
            throw UnsupportedMessageException.raise(message);
        }
        return ct;
    }

    public static InteropAccessNode create(Message message) {
        return InteropAccessNodeGen.create(message);
    }

}
