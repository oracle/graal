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

import static com.oracle.truffle.espresso.threads.ThreadState.IN_NATIVE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.perf.DebugCounter;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.impl.Method.MethodVersion;
import com.oracle.truffle.espresso.jni.JNIHandles;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.threads.Transition;
import com.oracle.truffle.espresso.vm.VM;

/**
 * Represents a native Java method.
 */
@ExportLibrary(NodeLibrary.class)
final class NativeMethodNode extends EspressoInstrumentableRootNodeImpl {
    private final JniEnv env;
    private final TruffleObject boundNative;
    @Child InteropLibrary executeNative;
    @CompilationFinal boolean throwsException;

    private static final DebugCounter NATIVE_METHOD_CALLS = DebugCounter.create("Native method calls");

    NativeMethodNode(JniEnv env, TruffleObject boundNative, MethodVersion method) {
        super(method);
        this.env = env;
        this.boundNative = boundNative;
        this.executeNative = InteropLibrary.getFactory().create(boundNative);
    }

    @TruffleBoundary
    private static Object toObjectHandle(JNIHandles handles, Object arg) {
        assert arg instanceof StaticObject;
        return (long) handles.createLocal((StaticObject) arg);
    }

    @ExplodeLoop
    private Object[] preprocessArgs(JNIHandles handles, Object[] args) {
        Symbol<Type>[] parsedSignature = getMethodVersion().getMethod().getParsedSignature();
        int paramCount = SignatureSymbols.parameterCount(parsedSignature);
        Object[] nativeArgs = new Object[2 /* JNIEnv* + class or receiver */ + paramCount];

        assert !InteropLibrary.getUncached().isNull(env.getNativePointer());
        nativeArgs[0] = env.getNativePointer(); // JNIEnv*

        if (getMethodVersion().isStatic()) {
            nativeArgs[1] = toObjectHandle(handles, getMethodVersion().getDeclaringKlass().mirror()); // class
        } else {
            nativeArgs[1] = toObjectHandle(handles, args[0]); // receiver
        }

        int skipReceiver = getMethodVersion().isStatic() ? 0 : 1;
        for (int i = 0; i < paramCount; ++i) {
            Symbol<Type> paramType = SignatureSymbols.parameterType(parsedSignature, i);
            if (TypeSymbols.isReference(paramType)) {
                nativeArgs[i + 2] = toObjectHandle(handles, args[i + skipReceiver]);
            } else {
                nativeArgs[i + 2] = args[i + skipReceiver];
            }
        }
        return nativeArgs;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        NATIVE_METHOD_CALLS.inc();
        JNIHandles handles = getContext().getHandles();
        int nativeFrame = handles.pushFrame();
        Transition transition = Transition.transition(IN_NATIVE, this);
        try {
            Object[] nativeArgs = preprocessArgs(handles, frame.getArguments());
            Object result = executeNative.execute(boundNative, nativeArgs);
            return processResult(handles, result);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        } finally {
            transition.restore(this);
            handles.popFramesIncluding(nativeFrame);
        }
    }

    private void enterThrowsException() {
        if (!throwsException) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throwsException = true;
        }
    }

    private void maybeThrowAndClearPendingException(EspressoLanguage language) {
        EspressoException ex = language.getPendingEspressoException();
        if (ex != null) {
            enterThrowsException();
            language.clearPendingException();
            throw ex;
        }
    }

    private Object processResult(JNIHandles handles, Object result) {
        // JNI exception handling.
        maybeThrowAndClearPendingException(EspressoLanguage.get(this));
        Symbol<Type> returnType = SignatureSymbols.returnType(getMethodVersion().getMethod().getParsedSignature());
        if (TypeSymbols.isReference(returnType)) {
            long addr;
            if (result instanceof Long) {
                addr = (Long) result;
            } else {
                assert result instanceof TruffleObject;
                addr = NativeUtils.interopAsPointer((TruffleObject) result);
            }
            return handles.get(Math.toIntExact(addr));
        }
        assert !(returnType == Types._void) || result == StaticObject.NULL;
        return result;
    }

    @Override
    public int getBci(Frame frame) {
        return VM.EspressoStackElement.NATIVE_BCI;
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "MethodMayBeStatic"})
    public boolean hasScope(@SuppressWarnings("unused") Frame frame) {
        return true;
    }

    @ExportMessage
    public Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
        return new SubstitutionScope(frame.getArguments(), getMethodVersion());
    }
}
