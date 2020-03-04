/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.object.DebugCounter;

public final class NativeRootNode extends EspressoMethodNode {

    private final TruffleObject boundNative;
    private final boolean isJni;
    @Child InteropLibrary executeNative;
    private final int prependParams;

    private static final DebugCounter NATIVE_METHOD_CALLS = DebugCounter.create("Native method calls");

    public NativeRootNode(TruffleObject boundNative, Method method, boolean isJni) {
        super(method.getMethodVersion());
        this.boundNative = boundNative;
        this.executeNative = InteropLibrary.getFactory().create(boundNative);
        this.isJni = isJni;
        this.prependParams = (isJni ? 1 : 0); // JNIEnv* env
    }

    private static Object javaToNative(JniEnv env, Object arg, Symbol<Type> espressoType) {
        if (Type._boolean.equals(espressoType)) {
            assert arg instanceof Boolean;
            return ((boolean) arg) ? (byte) 1 : (byte) 0;
        } else if (Type._char.equals(espressoType)) {
            assert arg instanceof Character;
            return (short) (char) arg;
        } else {
            if (!Types.isPrimitive(espressoType)) {
                assert arg instanceof StaticObject;
                return (/* @Word */ long) env.getHandles().createLocal((StaticObject) arg);
            }
            return arg;
        }
    }

    @ExplodeLoop
    private Object[] preprocessArgs(JniEnv env, Object[] args) {
        int paramCount = Signatures.parameterCount(getMethod().getParsedSignature(), false);
        Object[] unpacked = new Object[prependParams + paramCount + 1 /* class or receiver */];
        int argIndex = 0;
        if (isJni) {
            unpacked[argIndex++] = javaToNative(env, env.getNativePointer(), Type._long);
        }
        if (getMethod().isStatic()) {
            unpacked[argIndex++] = javaToNative(env, getMethod().getDeclaringKlass().mirror(), Type.java_lang_Class); // class
        } else {
            unpacked[argIndex++] = javaToNative(env, args[0], Type.java_lang_Object); // receiver
        }
        int skipReceiver = getMethod().isStatic() ? 0 : 1;
        for (int i = 0; i < paramCount; ++i) {
            Symbol<Type> paramType = Signatures.parameterType(getMethod().getParsedSignature(), i);
            unpacked[argIndex++] = javaToNative(env, args[i + skipReceiver], paramType);
        }
        return unpacked;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final JniEnv env = getContext().getJNI();

        int nativeFrame = env.getHandles().pushFrame();
        try {
            NATIVE_METHOD_CALLS.inc();
            Object[] unpackedArgs = preprocessArgs(env, frame.getArguments());
            Object result = executeNative.execute(boundNative, unpackedArgs);
            return processResult(env, result);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere(e);
        } finally {
            env.getHandles().popFramesIncluding(nativeFrame);
        }
    }

    @TruffleBoundary
    private void logOut(Object[] argsWithEnv, Object result) {
        System.err.println("Return from native " + getMethod() + Arrays.toString(argsWithEnv) + " -> " + result);
    }

    @TruffleBoundary
    private void logIn(Object[] argsWithEnv) {
        System.err.println("Calling native " + getMethod() + Arrays.toString(argsWithEnv));
    }

    @TruffleBoundary
    private static void maybeThrowAndClearPendingException(JniEnv jniEnv) {
        StaticObject ex = jniEnv.getPendingException();
        if (ex != null) {
            jniEnv.clearPendingException();
            throw Meta.throwException(ex);
        }
    }

    protected Object processResult(JniEnv env, Object result) {
        assert env.getNativePointer() != 0;

        // JNI exception handling.
        maybeThrowAndClearPendingException(env);
        Symbol<Type> returnType = Signatures.returnType(getMethod().getParsedSignature());
        if (!Types.isPrimitive(returnType)) {
            // Reference
            return env.getHandles().get(Math.toIntExact((long) result));
        }
        switch (getMethod().getReturnKind()) {
            case Boolean:
                return ((byte) result != 0);
            case Char:
                return (char) (short) result;
            case Object:
                if (result instanceof TruffleObject) {
                    if (InteropLibrary.getFactory().getUncached().isNull(result)) {
                        return StaticObject.NULL;
                    }
                }
                return result;
            case Void:
                return StaticObject.NULL;
        }
        return result;
    }
}
