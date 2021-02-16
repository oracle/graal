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

package com.oracle.truffle.espresso.vm;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.jni.IntrinsifiedNativeEnv;
import com.oracle.truffle.espresso.jni.JniImpl;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.jni.Pointer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.substitutions.GenerateIntrinsification;
import com.oracle.truffle.espresso.substitutions.IntrinsicSubstitutor;
import com.oracle.truffle.espresso.substitutions.JVMTICollector;

@GenerateIntrinsification(target = JvmtiImpl.class)
@SuppressWarnings("unused")
public class JVMTI extends IntrinsifiedNativeEnv {
    private static final int JVMTI_VERSION_1 = 0x30010000;
    private static final int JVMTI_VERSION_1_0 = 0x30010000;
    private static final int JVMTI_VERSION_1_1 = 0x30010100;
    private static final int JVMTI_VERSION_1_2 = 0x30010200;
    private static final int JVMTI_VERSION_9 = 0x30090000;
    private static final int JVMTI_VERSION_11 = 0x300B0000;
    /* version: 11.0. 0 */
    private static final int JVMTI_VERSION = 0x30000000 + (11 * 0x10000) + (0 * 0x100) + 0;

    private static final int JVMTI_ERROR_NONE = 0;
    private static final int JVMTI_ERROR_INVALID_THREAD = 10;
    private static final int JVMTI_ERROR_INVALID_THREAD_GROUP = 11;
    private static final int JVMTI_ERROR_INVALID_PRIORITY = 12;
    private static final int JVMTI_ERROR_THREAD_NOT_SUSPENDED = 13;
    private static final int JVMTI_ERROR_THREAD_SUSPENDED = 14;
    private static final int JVMTI_ERROR_THREAD_NOT_ALIVE = 15;
    private static final int JVMTI_ERROR_INVALID_OBJECT = 20;
    private static final int JVMTI_ERROR_INVALID_CLASS = 21;
    private static final int JVMTI_ERROR_CLASS_NOT_PREPARED = 22;
    private static final int JVMTI_ERROR_INVALID_METHODID = 23;
    private static final int JVMTI_ERROR_INVALID_LOCATION = 24;
    private static final int JVMTI_ERROR_INVALID_FIELDID = 25;
    private static final int JVMTI_ERROR_INVALID_MODULE = 26;
    private static final int JVMTI_ERROR_NO_MORE_FRAMES = 31;
    private static final int JVMTI_ERROR_OPAQUE_FRAME = 32;
    private static final int JVMTI_ERROR_TYPE_MISMATCH = 34;
    private static final int JVMTI_ERROR_INVALID_SLOT = 35;
    private static final int JVMTI_ERROR_DUPLICATE = 40;
    private static final int JVMTI_ERROR_NOT_FOUND = 41;
    private static final int JVMTI_ERROR_INVALID_MONITOR = 50;
    private static final int JVMTI_ERROR_NOT_MONITOR_OWNER = 51;
    private static final int JVMTI_ERROR_INTERRUPT = 52;
    private static final int JVMTI_ERROR_INVALID_CLASS_FORMAT = 60;
    private static final int JVMTI_ERROR_CIRCULAR_CLASS_DEFINITION = 61;
    private static final int JVMTI_ERROR_FAILS_VERIFICATION = 62;
    private static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED = 63;
    private static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED = 64;
    private static final int JVMTI_ERROR_INVALID_TYPESTATE = 65;
    private static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED = 66;
    private static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED = 67;
    private static final int JVMTI_ERROR_UNSUPPORTED_VERSION = 68;
    private static final int JVMTI_ERROR_NAMES_DONT_MATCH = 69;
    private static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED = 70;
    private static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED = 71;
    private static final int JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_ATTRIBUTE_CHANGED = 72;
    private static final int JVMTI_ERROR_UNMODIFIABLE_CLASS = 79;
    private static final int JVMTI_ERROR_UNMODIFIABLE_MODULE = 80;
    private static final int JVMTI_ERROR_NOT_AVAILABLE = 98;
    private static final int JVMTI_ERROR_MUST_POSSESS_CAPABILITY = 99;
    private static final int JVMTI_ERROR_NULL_POINTER = 100;
    private static final int JVMTI_ERROR_ABSENT_INFORMATION = 101;
    private static final int JVMTI_ERROR_INVALID_EVENT_TYPE = 102;
    private static final int JVMTI_ERROR_ILLEGAL_ARGUMENT = 103;
    private static final int JVMTI_ERROR_NATIVE_METHOD = 104;
    private static final int JVMTI_ERROR_CLASS_LOADER_UNSUPPORTED = 106;
    private static final int JVMTI_ERROR_OUT_OF_MEMORY = 110;
    private static final int JVMTI_ERROR_ACCESS_DENIED = 111;
    private static final int JVMTI_ERROR_WRONG_PHASE = 112;
    private static final int JVMTI_ERROR_INTERNAL = 113;
    private static final int JVMTI_ERROR_UNATTACHED_THREAD = 115;
    private static final int JVMTI_ERROR_INVALID_ENVIRONMENT = 116;
    private static final int JVMTI_ERROR_MAX = 116;

    private final EspressoContext context;

    @CompilationFinal //
    private @Pointer TruffleObject jvmtiPtr;
    @CompilationFinal //
    private int jvmtiVersion;

    public static final class JvmtiFactory {
        private final EspressoContext context;
        private final @Pointer TruffleObject initializeJvmtiContext;
        private final @Pointer TruffleObject disposeJvmtiContext;

        private final ArrayList<JVMTI> created = new ArrayList<>();

        public JvmtiFactory(EspressoContext context, TruffleObject mokapotLibrary) {
            this.context = context;
            try {
                this.initializeJvmtiContext = NativeLibrary.lookupAndBind(mokapotLibrary,
                                "initializeJvmtiContext", "(env, (pointer): pointer, sint32): pointer");

                this.disposeJvmtiContext = NativeLibrary.lookupAndBind(mokapotLibrary,
                                "disposeJvmtiContext",
                                "(env, pointer, sint32): void");
            } catch (UnknownIdentifierException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        public TruffleObject create(int version) {
            if (!isSupportedJvmtiVersion(version)) {
                return RawPointer.nullInstance();
            }
            JVMTI jvmti = new JVMTI(context, initializeJvmtiContext, version);
            created.add(jvmti);
            return jvmti.jvmtiPtr;
        }

        public void dispose() {
            for (JVMTI jvmti : created) {
                jvmti.dispose(disposeJvmtiContext);
            }
        }
    }

    public JVMTI(EspressoContext context, TruffleObject initializeJvmtiContext, int version) {
        this.context = context;
        try {
            jvmtiPtr = (TruffleObject) getUncached().execute(initializeJvmtiContext, getLookupCallback(), version);
            jvmtiVersion = version;
            assert getUncached().isPointer(jvmtiPtr);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        assert jvmtiPtr != null && !getUncached().isNull(jvmtiPtr);
    }

    public static boolean isSupportedJvmtiVersion(int version) {
        return version == JVMTI_VERSION || version == JVMTI_VERSION_1_1;
    }

    public void dispose(TruffleObject disposeJvmtiContext) {
        if (jvmtiPtr != null) {
            try {
                getUncached().execute(disposeJvmtiContext, jvmtiPtr, jvmtiVersion);
                this.jvmtiPtr = null;
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

    @JvmtiImpl
    @JniImpl
    public short Allocate(long byteCount, @Pointer TruffleObject memPtr) {
        if (byteCount < 0) {
            return JVMTI_ERROR_ILLEGAL_ARGUMENT;
        }
        LongBuffer resultPointer = directByteBuffer(memPtr, 1, JavaKind.Long).asLongBuffer();
        if (byteCount == 0) {
            resultPointer.put(interopAsPointer(RawPointer.nullInstance()));
        } else {
            TruffleObject alloc = getContext().getJNI().malloc(byteCount);
            if (getUncached().isNull(alloc)) {
                return JVMTI_ERROR_OUT_OF_MEMORY;
            }
            resultPointer.put(interopAsPointer(alloc));
        }
        return JVMTI_ERROR_NONE;
    }

    @JvmtiImpl
    public byte Deallocate(@Pointer TruffleObject memPtr) {
        if (!getUncached().isNull(memPtr)) {
            getContext().getJNI().free(memPtr);
        }
        return JVMTI_ERROR_NONE;
    }

    @JvmtiImpl
    public byte GetVersionNumber(@Pointer TruffleObject versionPtr) {
        IntBuffer buf = directByteBuffer(versionPtr, 1, JavaKind.Int).asIntBuffer();
        buf.put(jvmtiVersion);
        return JVMTI_ERROR_NONE;
    }
}
