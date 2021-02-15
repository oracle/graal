/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.jni;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.substitutions.GenerateIntrinsification;
import com.oracle.truffle.espresso.substitutions.IntrinsicSubstitutor;
import com.oracle.truffle.espresso.substitutions.JniEnvCollector;

/**
 * Subclasses of this class are implementation of native interfaces (for example, {@link JniEnv} or
 * {@link com.oracle.truffle.espresso.vm.VM}.
 *
 * Subclasses should be annotated with
 * {@link com.oracle.truffle.espresso.substitutions.GenerateIntrinsification} to produce the
 * boilerplate code used to juggle between native world and java world.
 *
 * Implementing a new native interface in Espresso requires:
 * <li>Subclassing this class and annotating it with
 * {@link com.oracle.truffle.espresso.substitutions.GenerateIntrinsification}, and implementing the
 * native interface methods, along with annotating them with the annotation given to
 * {@link GenerateIntrinsification#target()}.</li>
 * <li>Implementing the {@link #getCollector()} method, by calling into the corresponding
 * Collector.getCollector() method (/ex: {@link JniEnvCollector#getCollector()}</li>
 * <li>Finally, implement the interface in native code (most likely in mokapot. See the management
 * implementation there for reference.)</li>
 */
public abstract class IntrinsifiedNativeEnv extends NativeEnv implements ContextAccess {

    protected static final int LOOKUP_CALLBACK_ARGS_COUNT = 1;

    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, JniEnv.class);
    private final InteropLibrary uncached = InteropLibrary.getFactory().getUncached();

    private Map<String, IntrinsicSubstitutor.Factory> methods = buildMethodsMap();

    protected abstract List<IntrinsicSubstitutor.Factory> getCollector();

    private Map<String, IntrinsicSubstitutor.Factory> buildMethodsMap() {
        Map<String, IntrinsicSubstitutor.Factory> map = new HashMap<>();
        for (IntrinsicSubstitutor.Factory method : getCollector()) {
            assert !map.containsKey(method.methodName()) : "Substitution for " + method + " already exists";
            map.put(method.methodName(), method);
        }
        return Collections.unmodifiableMap(map);
    }

    private final Callback lookupCallback = new Callback(LOOKUP_CALLBACK_ARGS_COUNT, new Callback.Function() {
        @Override
        public Object call(Object... args) {
            try {
                String name = NativeUtils.interopPointerToString((TruffleObject) args[0]);
                return lookupIntrinsic(name);
            } catch (ClassCastException e) {
                throw EspressoError.shouldNotReachHere(e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    });

    protected final Callback getLookupCallback() {
        return lookupCallback;
    }

    protected final InteropLibrary getUncached() {
        return uncached;
    }

    protected final TruffleLogger getLogger() {
        return logger;
    }

    private JniEnv jni() {
        return getContext().getJNI();
    }

    public JNIHandles getHandles() {
        return jni().getHandles();
    }

    @TruffleBoundary
    private TruffleObject lookupIntrinsic(String methodName) {
        IntrinsicSubstitutor.Factory factory = methods.get(methodName);
        // Dummy placeholder for unimplemented/unknown methods.
        if (factory == null) {
            getLogger().log(Level.FINER, "Fetching unknown/unimplemented JNI method: {0}", methodName);
            @Pointer
            TruffleObject errorClosure = getNativeAccess().createNativeClosure(new Callback(0, new Callback.Function() {
                @Override
                public Object call(Object... args) {
                    CompilerDirectives.transferToInterpreter();
                    getLogger().log(Level.SEVERE, "Calling unimplemented JNI method: {0}", methodName);
                    throw EspressoError.unimplemented("JNI method: " + methodName);
                }
            }), NativeSignature.create(NativeType.VOID));
            nativeClosures.add(errorClosure);
            return errorClosure;
        }

        NativeSignature signature = factory.jniNativeSignature();
        Callback target = intrinsicWrapper(factory);
        @Pointer
        TruffleObject nativeClosure = getNativeAccess().createNativeClosure(target, signature);
        nativeClosures.add(nativeClosure);
        return nativeClosure;

    }

    private Callback intrinsicWrapper(IntrinsicSubstitutor.Factory factory) {
        int extraArg = (factory.isJni()) ? 1 : 0;
        return new Callback(factory.parameterCount() + extraArg, new Callback.Function() {
            @CompilerDirectives.CompilationFinal private IntrinsicSubstitutor subst = null;

            @Override
            public Object call(Object... args) {
                boolean isJni = factory.isJni();
                try {
                    if (subst == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        subst = factory.create(getMeta());
                    }
                    return subst.invoke(this, args);
                } catch (EspressoException | StackOverflowError | OutOfMemoryError e) {
                    if (isJni) {
                        // This will most likely SOE again. Nothing we can do about that
                        // unfortunately.
                        EspressoException wrappedError = (e instanceof EspressoException)
                                        ? (EspressoException) e
                                        : (e instanceof StackOverflowError)
                                                        ? getContext().getStackOverflow()
                                                        : getContext().getOutOfMemory();
                        jni().getThreadLocalPendingException().set(wrappedError.getExceptionObject());
                        return defaultValue(factory.returnType());
                    }
                    throw e;
                }
            }
        });
    }
}
