/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.object.DebugCounter;

import static com.oracle.truffle.api.nodes.Node.*;

public abstract class NativeRootNode extends RootNode implements LinkedNode {

    private final TruffleObject boundNative;

    @CompilationFinal //
    private Method originalMethod;

    @Child Node execute = Message.EXECUTE.createNode();

    @Child Node isNullNode = Message.IS_NULL.createNode();

    public final static DebugCounter nativeCalls = DebugCounter.create("Native calls");

    public NativeRootNode(TruffleLanguage<?> language, TruffleObject boundNative) {
        this(language, boundNative, null);
    }

    public NativeRootNode(TruffleLanguage<?> language, TruffleObject boundNative, Method originalMethod) {
        super(language);
        this.boundNative = boundNative;
        this.originalMethod = originalMethod;
    }

    public Object[] preprocessArgs(Object[] args) {
        Meta.Klass[] params = getOriginalMethod().getParameterTypes();
        // TODO(peterssen): Static method does not get the clazz in the arguments,
        int argIndex = getOriginalMethod().isStatic() ? 0 : 1;
        for (int i = 0; i < params.length; ++i) {
            if (args[argIndex] == null) {
                args[argIndex] = StaticObject.NULL;
            }
            if (args[argIndex] instanceof Boolean) {
                if (params[i].kind() == JavaKind.Boolean) {
                    args[argIndex] = (boolean) args[argIndex] ? (byte) 1 : (byte) 0;
                }
            }
            ++argIndex;
        }
        return args;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (originalMethod == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere();
        }
        try {
            nativeCalls.inc();
            // TODO(peterssen): Inject JNIEnv properly, without copying.
            // The frame.getArguments().length must match the arity of the native method, which is
            // constant.
            // Having a constant length would help PEA to skip the copying.
            Object[] argsWithEnv = preprocessArgs(frame.getArguments());
            // System.err.println("Calling native " + originalMethod.getName() +
            // Arrays.toString(argsWithEnv));
            Object result = ForeignAccess.sendExecute(execute, boundNative, argsWithEnv);
            // System.err.println("Return from native " + originalMethod.getName() +
            // Arrays.toString(argsWithEnv) + " -> " + result);
            return processResult(result);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public Object processResult(Object result) {
        JniEnv jniEnv = EspressoLanguage.getCurrentContext().getJNI();
        assert jniEnv.getNativePointer() != 0;

        // JNI exception handling.
        StaticObject ex = jniEnv.getThreadLocalPendingException().get();
        if (ex != null) {
            jniEnv.getThreadLocalPendingException().clear();
            throw new EspressoException(ex);
        }

        switch (getOriginalMethod().getReturnType().kind()) {
            case Boolean:
                return ((byte) result != 0);
            case Byte:
                return (byte) result;
            case Char:
                return (char) result;
            case Short:
                return (short) result;
            case Object:
                if (result instanceof TruffleObject) {
                    if (ForeignAccess.sendIsNull(isNullNode, (TruffleObject) result)) {
                        return StaticObject.NULL;
                    }
                }
                return result;
        }
        // System.err.println("Return native " + originalMethod.getName() + " -> " + result);
        return result;
    }

    protected static Object[] prepend1(Object first, Object... args) {
        Object[] newArgs = new Object[args.length + 1];
        System.arraycopy(args, 0, newArgs, 1, args.length);
        newArgs[0] = first;
        return newArgs;
    }

    protected static Object[] prepend2(Object first, Object second, Object... args) {
        Object[] newArgs = new Object[args.length + 2];
        System.arraycopy(args, 0, newArgs, 2, args.length);
        newArgs[0] = first;
        newArgs[1] = second;
        return newArgs;
    }

    @Override
    public Method getOriginalMethod() {
        return originalMethod;
    }

    public void setOriginalMethod(Method originalMethod) {
        this.originalMethod = originalMethod;
    }

    public TruffleObject getBoundNative() {
        return boundNative;
    }
}
