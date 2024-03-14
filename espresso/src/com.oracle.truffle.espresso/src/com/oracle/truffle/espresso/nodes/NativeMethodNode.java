/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
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
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.impl.Method.MethodVersion;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

/**
 * Represents a native Java method.
 */
final class NativeMethodNode extends EspressoInstrumentableRootNodeImpl {

    private final TruffleObject boundNative;
    @Child InteropLibrary executeNative;
    @CompilationFinal boolean throwsException;

    private static final DebugCounter NATIVE_METHOD_CALLS = DebugCounter.create("Native method calls");

    NativeMethodNode(TruffleObject boundNative, MethodVersion method) {
        super(method);
        this.boundNative = boundNative;
        this.executeNative = InteropLibrary.getFactory().create(boundNative);
    }

    @TruffleBoundary
    private static Object toObjectHandle(JniEnv env, Object arg) {
        assert arg instanceof StaticObject;
        return (long) env.getHandles().createLocal((StaticObject) arg);
    }

    @ExplodeLoop
    private Object[] preprocessArgs(JniEnv env, Object[] args) {
        Symbol<Type>[] parsedSignature = getMethodVersion().getMethod().getParsedSignature();
        int paramCount = Signatures.parameterCount(parsedSignature);
        Object[] nativeArgs = new Object[2 /* JNIEnv* + class or receiver */ + paramCount];

        assert !InteropLibrary.getUncached().isNull(env.getNativePointer());
        nativeArgs[0] = env.getNativePointer(); // JNIEnv*

        if (getMethodVersion().isStatic()) {
            nativeArgs[1] = toObjectHandle(env, getMethodVersion().getDeclaringKlass().mirror()); // class
        } else {
            nativeArgs[1] = toObjectHandle(env, args[0]); // receiver
        }

        int skipReceiver = getMethodVersion().isStatic() ? 0 : 1;
        for (int i = 0; i < paramCount; ++i) {
            Symbol<Type> paramType = Signatures.parameterType(parsedSignature, i);
            if (Types.isReference(paramType)) {
                nativeArgs[i + 2] = toObjectHandle(env, args[i + skipReceiver]);
            } else {
                nativeArgs[i + 2] = args[i + skipReceiver];
            }
        }
        return nativeArgs;
    }

    @Override
    void beforeInstumentation(VirtualFrame frame) {
        // no op
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final JniEnv env = getContext().getJNI();
        int nativeFrame = env.getHandles().pushFrame();
        NATIVE_METHOD_CALLS.inc();
        try {
            Object[] nativeArgs = preprocessArgs(env, frame.getArguments());
            Object result = executeNative.execute(boundNative, nativeArgs);
            return processResult(env, result);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        } finally {
            env.getHandles().popFramesIncluding(nativeFrame);
        }
    }

    private void enterThrowsException() {
        if (!throwsException) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throwsException = true;
        }
    }

    private void maybeThrowAndClearPendingException(JniEnv jniEnv) {
        EspressoException ex = jniEnv.getPendingEspressoException();
        if (ex != null) {
            enterThrowsException();
            jniEnv.clearPendingException();
            throw ex;
        }
    }

    private Object processResult(JniEnv env, Object result) {
        // JNI exception handling.
        maybeThrowAndClearPendingException(env);
        Symbol<Type> returnType = Signatures.returnType(getMethodVersion().getMethod().getParsedSignature());
        if (Types.isReference(returnType)) {
            long addr;
            if (result instanceof Long) {
                addr = (Long) result;
            } else {
                assert result instanceof TruffleObject;
                addr = NativeUtils.interopAsPointer((TruffleObject) result);
            }
            return env.getHandles().get(Math.toIntExact(addr));
        }
        assert !(returnType == Type._void) || result == StaticObject.NULL;
        return result;
    }

    @Override
    public int getBci(Frame frame) {
        return VM.EspressoStackElement.NATIVE_BCI;
    }
}
