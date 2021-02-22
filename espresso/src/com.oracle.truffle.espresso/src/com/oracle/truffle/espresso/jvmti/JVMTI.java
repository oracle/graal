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

package com.oracle.truffle.espresso.jvmti;

import static com.oracle.truffle.espresso.jvmti.JvmtiErrorCodes.JVMTI_OK;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.jni.IntrinsifiedNativeEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.substitutions.GenerateIntrinsification;
import com.oracle.truffle.espresso.substitutions.GenerateIntrinsification.PrependEnv;
import com.oracle.truffle.espresso.substitutions.IntrinsicSubstitutor;
import com.oracle.truffle.espresso.substitutions.JVMTICollector;

@GenerateIntrinsification(target = JvmtiImpl.class)
@PrependEnv
@SuppressWarnings("unused")
public final class JVMTI extends IntrinsifiedNativeEnv {

    private final EspressoContext context;

    @CompilationFinal //
    private @Pointer TruffleObject jvmtiEnvPtr;
    @CompilationFinal //
    private int jvmtiVersion;

    private TruffleObject envLocalStorage = RawPointer.nullInstance();

    public static final class JvmtiFactory {
        private final EspressoContext context;
        private final @Pointer TruffleObject initializeJvmtiContext;
        private final @Pointer TruffleObject disposeJvmtiContext;

        private final ArrayList<JVMTI> created = new ArrayList<>();

        public JvmtiFactory(EspressoContext context, TruffleObject mokapotLibrary) {
            this.context = context;
            this.initializeJvmtiContext = context.getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "initializeJvmtiContext",
                            NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.INT));

            this.disposeJvmtiContext = context.getNativeAccess().lookupAndBindSymbol(mokapotLibrary,
                            "disposeJvmtiContext",
                            NativeSignature.create(NativeType.VOID, NativeType.POINTER, NativeType.INT, NativeType.POINTER));
        }

        public synchronized TruffleObject create(int version) {
            if (!isSupportedJvmtiVersion(version)) {
                return null;
            }
            JVMTI jvmti = new JVMTI(context, initializeJvmtiContext, version);
            created.add(jvmti);
            return jvmti.jvmtiEnvPtr;
        }

        public synchronized void dispose() {
            for (JVMTI jvmti : created) {
                jvmti.dispose(disposeJvmtiContext);
            }
            created.clear();
        }

        private synchronized void dispose(JVMTI env) {
            if (created.contains(env)) {
                env.dispose(disposeJvmtiContext);
                created.remove(env);
            }
        }
    }

    private JVMTI(EspressoContext context, TruffleObject initializeJvmtiContext, int version) {
        this.context = context;
        jvmtiEnvPtr = initializeAndGetEnv(initializeJvmtiContext, version);
        jvmtiVersion = version;
        assert getUncached().isPointer(jvmtiEnvPtr);
        assert jvmtiEnvPtr != null && !getUncached().isNull(jvmtiEnvPtr);
    }

    public static boolean isJvmtiVersion(int version) {
        return JvmtiVersion.isJvmtiVersion(version);
    }

    private static boolean isSupportedJvmtiVersion(int version) {
        return JvmtiVersion.isSupportedJvmtiVersion(version);
    }

    private void dispose(TruffleObject disposeJvmtiContext) {
        if (jvmtiEnvPtr != null) {
            try {
                getUncached().execute(disposeJvmtiContext, jvmtiEnvPtr, jvmtiVersion);
                this.jvmtiEnvPtr = null;
                this.jvmtiVersion = 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere("Cannot dispose Espresso jvmti (mokapot).");
            }
        }
    }

    @Override
    protected List<IntrinsicSubstitutor.Factory> getCollector() {
        return JVMTICollector.getCollector();
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    // Checkstyle: stop method name check

    @JvmtiImpl
    public int Allocate(long byteCount, @Pointer TruffleObject memPtr) {
        if (byteCount < 0) {
            return JvmtiErrorCodes.JVMTI_ERROR_ILLEGAL_ARGUMENT;
        }
        TruffleObject alloc;
        if (byteCount == 0) {
            alloc = RawPointer.nullInstance();
        } else {
            alloc = getNativeAccess().allocateMemory(byteCount);
            if (getUncached().isNull(alloc)) {
                return JvmtiErrorCodes.JVMTI_ERROR_OUT_OF_MEMORY;
            }
        }
        NativeUtils.writeToPointerPointer(getUncached(), memPtr, alloc);
        return JVMTI_OK;
    }

    @JvmtiImpl
    public int Deallocate(@Pointer TruffleObject memPtr) {
        if (!getUncached().isNull(memPtr)) {
            getNativeAccess().freeMemory(memPtr);
        }
        return JVMTI_OK;
    }

    @JvmtiImpl
    public int DisposeEnvironment() {
        getContext().getVM().getJvmti().dispose(this);
        return JVMTI_OK;
    }

    @JvmtiImpl
    public int SetEnvironmentLocalStorage(@Pointer TruffleObject data) {
        envLocalStorage = data;
        return JVMTI_OK;
    }

    @JvmtiImpl
    public int GetEnvironmentLocalStorage(@Pointer TruffleObject dataPtr) {
        NativeUtils.writeToPointerPointer(getUncached(), dataPtr, envLocalStorage);
        return JVMTI_OK;
    }

    @JvmtiImpl
    public static int GetPotentialCapabilities(@Pointer TruffleObject cap) {
        // For the time being, advertise no capability.
        return JVMTI_OK;
    }

    @JvmtiImpl
    public int GetVersionNumber(@Pointer TruffleObject versionPtr) {
        NativeUtils.writeToIntPointer(getUncached(), versionPtr, jvmtiVersion);
        return JVMTI_OK;
    }

    // Checkstyle: resume method name check
}
