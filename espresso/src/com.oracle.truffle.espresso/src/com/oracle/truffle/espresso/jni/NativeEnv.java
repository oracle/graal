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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.substitutions.CallableFromNative;
import com.oracle.truffle.espresso.substitutions.GenerateNativeEnv;

/**
 * Subclasses of this class are implementation of native interfaces (for example, {@link JniEnv} or
 * {@link com.oracle.truffle.espresso.vm.VM}.
 *
 * Subclasses should be annotated with
 * {@link com.oracle.truffle.espresso.substitutions.GenerateNativeEnv} to produce the boilerplate
 * code used to juggle between native world and java world.
 *
 * Implementing a new native interface in Espresso requires:
 * <li>Subclassing this class and annotating it with
 * {@link com.oracle.truffle.espresso.substitutions.GenerateNativeEnv}, and implementing the native
 * interface methods, along with annotating them with the annotation given to
 * {@link GenerateNativeEnv#target()}.</li>
 * <li>Implementing the {@link #getCollector()} method.</li>
 * <li>Finally, implement the interface in native code (most likely in mokapot. See the management
 * implementation there for reference.)</li>
 */
public abstract class NativeEnv implements ContextAccess {

    private static final int LOOKUP_CALLBACK_ARGS_COUNT = 1;

    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, this.getClass());
    private final InteropLibrary uncached = InteropLibrary.getFactory().getUncached();

    private final Set<@Pointer TruffleObject> nativeClosures = Collections.newSetFromMap(new IdentityHashMap<>());
    private Map<String, CallableFromNative.Factory> methods;

    // region Exposed interface
    protected abstract List<CallableFromNative.Factory> getCollector();

    protected int lookupCallBackArgsCount() {
        return LOOKUP_CALLBACK_ARGS_COUNT;
    }

    protected NativeSignature lookupCallbackSignature() {
        return NativeSignature.create(NativeType.POINTER, NativeType.POINTER);
    }

    @SuppressWarnings("unused")
    protected void processCallBackResult(String name, CallableFromNative.Factory factory, Object... args) {
        assert args.length == lookupCallBackArgsCount();
    }

    public JNIHandles getHandles() {
        return jni().getHandles();
    }

    protected final InteropLibrary getUncached() {
        return uncached;
    }

    protected final TruffleLogger getLogger() {
        return logger;
    }

    protected JniEnv jni() {
        return getContext().getJNI();
    }

    // endregion Exposed interface

    // region Initialization helper
    protected TruffleObject initializeAndGetEnv(TruffleObject initializeFunctionPointer, Object... extraArgs) {
        return initializeAndGetEnv(false, initializeFunctionPointer, extraArgs);
    }

    @TruffleBoundary
    protected TruffleObject initializeAndGetEnv(boolean prependExtra, TruffleObject initializeFunctionPointer, Object... extraArgs) {
        // Prepare call and initialize helper structures
        Object[] newArgs = prepareInit(prependExtra, extraArgs);

        // Do call
        TruffleObject res;
        try {
            res = (TruffleObject) getUncached().execute(initializeFunctionPointer, newArgs);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        } finally {
            // Free up helper structures
            cleanupAfterInit();
        }

        return res;
    }

    private Map<String, CallableFromNative.Factory> buildMethodsMap() {
        Map<String, CallableFromNative.Factory> map = new HashMap<>();
        for (CallableFromNative.Factory method : getCollector()) {
            EspressoError.guarantee(!map.containsKey(method.methodName()), "Substitution for " + method + " already exists");
            map.put(method.methodName(), method);
        }
        return Collections.unmodifiableMap(map);
    }

    private Object[] prepareInit(boolean prependExtra, Object[] extraArgs) {
        Object[] newArgs = new Object[extraArgs.length + 1];
        int pos;
        if (prependExtra) {
            newArgs[newArgs.length - 1] = getLookupCallbackClosure();
            pos = 0;
        } else {
            newArgs[0] = getLookupCallbackClosure();
            pos = 1;
        }
        for (Object arg : extraArgs) {
            newArgs[pos] = arg;
            pos++;
        }
        // Map building + multiple lookup should be faster than multiple list lookups.
        methods = buildMethodsMap();
        return newArgs;
    }

    private void cleanupAfterInit() {
        methods = null;
    }

    private TruffleObject getLookupCallbackClosure() {
        Callback callback = new Callback(lookupCallBackArgsCount(), new Callback.Function() {
            @Override
            @TruffleBoundary
            public Object call(Object... args) {
                try {
                    String name = NativeUtils.interopPointerToString((TruffleObject) args[0]);
                    CallableFromNative.Factory factory = lookupFactory(name);
                    processCallBackResult(name, factory, args);
                    return createNativeClosureForFactory(factory, name);
                } catch (ClassCastException e) {
                    throw EspressoError.shouldNotReachHere(e);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Throwable e) {
                    throw EspressoError.shouldNotReachHere(e);
                }
            }
        });
        return getNativeAccess().createNativeClosure(callback, lookupCallbackSignature());
    }

    private CallableFromNative.Factory lookupFactory(String methodName) {
        assert methods != null;
        CompilerAsserts.neverPartOfCompilation();
        return methods.get(methodName);
    }

    @TruffleBoundary
    private TruffleObject createNativeClosureForFactory(CallableFromNative.Factory factory, String methodName) {
        // Dummy placeholder for unimplemented/unknown methods.
        if (factory == null) {
            String envName = NativeEnv.this.getClass().getSimpleName();
            getLogger().log(Level.FINER, "Fetching unknown/unimplemented {0} method: {1}", new Object[]{envName, methodName});
            @Pointer
            TruffleObject errorClosure = getNativeAccess().createNativeClosure(new Callback(0, new Callback.Function() {
                @Override
                public Object call(Object... args) {
                    CompilerDirectives.transferToInterpreter();
                    getLogger().log(Level.SEVERE, "Calling unimplemented {0} method: {1}", new Object[]{envName, methodName});
                    throw EspressoError.unimplemented(envName + " method: " + methodName);
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

    private Callback intrinsicWrapper(CallableFromNative.Factory factory) {
        int extraArg = (factory.prependEnv()) ? 1 : 0;
        return new Callback(factory.parameterCount() + extraArg, new Callback.Function() {
            @CompilerDirectives.CompilationFinal private CallableFromNative subst = null;

            @Override
            public Object call(Object... args) {
                boolean isJni = factory.prependEnv();
                try {
                    if (subst == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        subst = factory.create(getMeta());
                    }
                    return subst.invoke(NativeEnv.this, args);
                } catch (EspressoException | StackOverflowError | OutOfMemoryError e) {
                    if (isJni) {
                        // This will most likely SOE again. Nothing we can do about that
                        // unfortunately.
                        EspressoException wrappedError = (e instanceof EspressoException)
                                        ? (EspressoException) e
                                        : (e instanceof StackOverflowError)
                                                        ? getContext().getStackOverflow()
                                                        : getContext().getOutOfMemory();
                        jni().setPendingException(wrappedError);
                        return defaultValue(factory.returnType());
                    }
                    throw e;
                }
            }
        });
    }

    protected static Object defaultValue(NativeType nativeType) {
        // @formatter:off
        switch (nativeType){
            case BOOLEAN : return false;
            case BYTE    : return (byte) 0;
            case CHAR    : return (char) 0;
            case SHORT   : return (short) 0;
            case INT     : return 0;
            case LONG    : return 0L;
            case FLOAT   : return 0F;
            case DOUBLE  : return 0D;
            case POINTER : return RawPointer.nullInstance();
            case VOID    : // fall-through
                // JNI handle for the NULL object (0L) and not StaticObject.NULL directly.
            case OBJECT  : return 0L; // NULL handle
        }
        // @formatter:on
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Unexpected NativeType: " + nativeType);
    }

    // endregion Initialization helper
}
