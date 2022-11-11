/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.AMD64;
import org.graalvm.nativeimage.Platform.WINDOWS;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleContext;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleEnv;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_type;

public final class NFIInitialization {

    // Checkstyle: stop
    // keep global names consistent with native naming
    private static final CGlobalData<ffi_type> ffi_type_pointer = CGlobalDataFactory.forSymbol("ffi_type_pointer");
    private static final CGlobalData<ffi_type> ffi_type_void = CGlobalDataFactory.forSymbol("ffi_type_void");
    private static final CGlobalData<ffi_type> ffi_type_uint8 = CGlobalDataFactory.forSymbol("ffi_type_uint8");
    private static final CGlobalData<ffi_type> ffi_type_sint8 = CGlobalDataFactory.forSymbol("ffi_type_sint8");
    private static final CGlobalData<ffi_type> ffi_type_uint16 = CGlobalDataFactory.forSymbol("ffi_type_uint16");
    private static final CGlobalData<ffi_type> ffi_type_sint16 = CGlobalDataFactory.forSymbol("ffi_type_sint16");
    private static final CGlobalData<ffi_type> ffi_type_uint32 = CGlobalDataFactory.forSymbol("ffi_type_uint32");
    private static final CGlobalData<ffi_type> ffi_type_sint32 = CGlobalDataFactory.forSymbol("ffi_type_sint32");
    private static final CGlobalData<ffi_type> ffi_type_uint64 = CGlobalDataFactory.forSymbol("ffi_type_uint64");
    private static final CGlobalData<ffi_type> ffi_type_sint64 = CGlobalDataFactory.forSymbol("ffi_type_sint64");
    private static final CGlobalData<ffi_type> ffi_type_float = CGlobalDataFactory.forSymbol("ffi_type_float");
    private static final CGlobalData<ffi_type> ffi_type_double = CGlobalDataFactory.forSymbol("ffi_type_double");
    private static final CGlobalData<ffi_type> ffi_type_longdouble = CGlobalDataFactory.forSymbol("ffi_type_longdouble");
    // Checkstyle: resume

    interface InitializeNativeSimpleTypeCallback extends CFunctionPointer {
    }

    private static void initializeNativeSimpleType(Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType simpleType,
                    ffi_type ffiType) {
        int size = (int) ffiType.size().rawValue();
        int alignment = ffiType.alignment();
        context.initializeSimpleType(simpleType, size, alignment, ffiType.rawValue());
    }

    static void initializeSimpleTypes(Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext context) {
        // it's important to initialize POINTER first, since the primitive array types depend on it
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.POINTER, ffi_type_pointer.get());

        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.VOID, ffi_type_void.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.UINT8, ffi_type_uint8.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.SINT8, ffi_type_sint8.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.UINT16, ffi_type_uint16.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.SINT16, ffi_type_sint16.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.UINT32, ffi_type_uint32.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.SINT32, ffi_type_sint32.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.UINT64, ffi_type_uint64.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.SINT64, ffi_type_sint64.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.FLOAT, ffi_type_float.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.DOUBLE, ffi_type_double.get());
        if (Platform.includedIn(AMD64.class) && !Platform.includedIn(WINDOWS.class)) {
            /*
             * On Windows, our LibFFI is compiled with the Visual Studio compiler, and that does not
             * support FP80, it treats `long double` as double precision only.
             */
            initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.FP80, ffi_type_longdouble.get());
        }

        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.STRING, ffi_type_pointer.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.OBJECT, ffi_type_pointer.get());
        initializeNativeSimpleType(context, Target_com_oracle_truffle_nfi_backend_spi_types_NativeSimpleType.NULLABLE, ffi_type_pointer.get());
    }

    static void initializeContext(NativeTruffleContext ctx) {
        ctx.nativeAPI().setGetTruffleContextFunction(NativeAPIImpl.GET_TRUFFLE_CONTEXT.getFunctionPointer());
        ctx.nativeAPI().setNewObjectRefFunction(NativeAPIImpl.NEW_OBJECT_REF.getFunctionPointer());
        ctx.nativeAPI().setReleaseObjectRefFunction(NativeAPIImpl.RELEASE_OBJECT_REF.getFunctionPointer());
        ctx.nativeAPI().setReleaseAndReturnFunction(NativeAPIImpl.RELEASE_AND_RETURN.getFunctionPointer());
        ctx.nativeAPI().setIsSameObjectFunction(NativeAPIImpl.IS_SAME_OBJECT.getFunctionPointer());
        ctx.nativeAPI().setNewClosureRefFunction(NativeAPIImpl.NEW_CLOSURE_REF.getFunctionPointer());
        ctx.nativeAPI().setReleaseClosureRefFunction(NativeAPIImpl.RELEASE_CLOSURE_REF.getFunctionPointer());
        ctx.nativeAPI().setGetClosureObjectFunction(NativeAPIImpl.GET_CLOSURE_OBJECT.getFunctionPointer());

        ctx.threadAPI().setGetTruffleEnvFunction(NativeAPIImpl.GET_TRUFFLE_ENV.getFunctionPointer());
        ctx.threadAPI().setAttachCurrentThreadFunction(NativeAPIImpl.ATTACH_CURRENT_THREAD.getFunctionPointer());
        ctx.threadAPI().setDetachCurrentThreadFunction(NativeAPIImpl.DETACH_CURRENT_THREAD.getFunctionPointer());

        ctx.setFunctions(ctx.threadAPI());
        ctx.setIsolate(CurrentIsolate.getIsolate());
    }

    static void initializeEnv(NativeTruffleEnv env, NativeTruffleContext ctx) {
        env.setFunctions(ctx.nativeAPI());
        env.setContext(ctx);
        env.setIsolateThread(CurrentIsolate.getCurrentThread());
    }
}
