/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleContext;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleEnv;
import com.oracle.svm.truffle.nfi.libffi.LibFFI.ffi_type;
import com.oracle.truffle.nfi.types.NativeSimpleType;

public final class NFIInitialization {

    interface InitializeNativeSimpleTypeCallback extends CFunctionPointer {
    }

    @CEntryPoint
    @CEntryPointOptions(publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void initializeNativeSimpleType(@SuppressWarnings("unused") IsolateThread thread, NativeTruffleContext ctx, CCharPointer typeName, ffi_type ffiType) {
        NativeSimpleType simpleType = NativeSimpleType.valueOf(CTypeConversion.toJavaString(typeName));
        int size = (int) ffiType.size().rawValue();
        int alignment = ffiType.alignment();

        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);
        Target_com_oracle_truffle_nfi_impl_NFIContext context = support.resolveContextHandle(ctx.contextHandle());
        context.initializeSimpleType(simpleType, size, alignment, ffiType.rawValue());
    }

    static final CEntryPointLiteral<InitializeNativeSimpleTypeCallback> INITIALIZE_NATIVE_SIMPLE_TYPE = CEntryPointLiteral.create(NFIInitialization.class, "initializeNativeSimpleType",
                    IsolateThread.class, NativeTruffleContext.class, CCharPointer.class, ffi_type.class);

    @CFunction("svm_libffi_initialize")
    private static native void svmLibFFIInitialize(IsolateThread thread, NativeTruffleContext ctx, InitializeNativeSimpleTypeCallback callback);

    public static void initializeContext(NativeTruffleContext ctx) {
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
        ctx.setIsolate(CEntryPointContext.getCurrentIsolate());

        svmLibFFIInitialize(CEntryPointContext.getCurrentIsolateThread(), ctx, INITIALIZE_NATIVE_SIMPLE_TYPE.getFunctionPointer());
    }

    public static void initializeEnv(NativeTruffleEnv env, NativeTruffleContext ctx) {
        env.setFunctions(ctx.nativeAPI());
        env.setContext(ctx);
        env.setIsolateThread(CEntryPointContext.getCurrentIsolateThread());
    }
}
