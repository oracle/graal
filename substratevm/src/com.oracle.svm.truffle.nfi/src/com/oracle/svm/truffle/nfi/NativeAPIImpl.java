/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.c.function.CEntryPointOptions.ReturnNullPointer;
import com.oracle.svm.core.c.function.CEntryPointSetup.LeaveDetachThreadEpilogue;
import com.oracle.svm.truffle.nfi.NativeAPI.AttachCurrentThreadFunction;
import com.oracle.svm.truffle.nfi.NativeAPI.DetachCurrentThreadFunction;
import com.oracle.svm.truffle.nfi.NativeAPI.GetClosureObjectFunction;
import com.oracle.svm.truffle.nfi.NativeAPI.GetTruffleContextFunction;
import com.oracle.svm.truffle.nfi.NativeAPI.GetTruffleEnvFunction;
import com.oracle.svm.truffle.nfi.NativeAPI.IsSameObjectFunction;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleContext;
import com.oracle.svm.truffle.nfi.NativeAPI.NativeTruffleEnv;
import com.oracle.svm.truffle.nfi.NativeAPI.NewClosureRefFunction;
import com.oracle.svm.truffle.nfi.NativeAPI.NewObjectRefFunction;
import com.oracle.svm.truffle.nfi.NativeAPI.ReleaseAndReturnFunction;
import com.oracle.svm.truffle.nfi.NativeAPI.ReleaseClosureRefFunction;
import com.oracle.svm.truffle.nfi.NativeAPI.ReleaseObjectRefFunction;
import com.oracle.truffle.api.interop.TruffleObject;

/**
 * Implementation of the TruffleEnv and TruffleContext native API functions.
 */
final class NativeAPIImpl {

    static final CEntryPointLiteral<GetTruffleContextFunction> GET_TRUFFLE_CONTEXT = CEntryPointLiteral.create(NativeAPIImpl.class, "getTruffleContext", NativeTruffleEnv.class);
    static final CEntryPointLiteral<NewObjectRefFunction> NEW_OBJECT_REF = CEntryPointLiteral.create(NativeAPIImpl.class, "newObjectRef", NativeTruffleEnv.class, TruffleObjectHandle.class);
    static final CEntryPointLiteral<ReleaseObjectRefFunction> RELEASE_OBJECT_REF = CEntryPointLiteral.create(NativeAPIImpl.class, "releaseObjectRef", NativeTruffleEnv.class,
                    TruffleObjectHandle.class);
    static final CEntryPointLiteral<ReleaseAndReturnFunction> RELEASE_AND_RETURN = CEntryPointLiteral.create(NativeAPIImpl.class, "releaseAndReturn", NativeTruffleEnv.class,
                    TruffleObjectHandle.class);
    static final CEntryPointLiteral<IsSameObjectFunction> IS_SAME_OBJECT = CEntryPointLiteral.create(NativeAPIImpl.class, "isSameObject", NativeTruffleEnv.class, TruffleObjectHandle.class,
                    TruffleObjectHandle.class);
    static final CEntryPointLiteral<NewClosureRefFunction> NEW_CLOSURE_REF = CEntryPointLiteral.create(NativeAPIImpl.class, "newClosureRef", NativeTruffleEnv.class, PointerBase.class);
    static final CEntryPointLiteral<ReleaseClosureRefFunction> RELEASE_CLOSURE_REF = CEntryPointLiteral.create(NativeAPIImpl.class, "releaseClosureRef", NativeTruffleEnv.class, PointerBase.class);
    static final CEntryPointLiteral<GetClosureObjectFunction> GET_CLOSURE_OBJECT = CEntryPointLiteral.create(NativeAPIImpl.class, "getClosureObject", NativeTruffleEnv.class, PointerBase.class);

    static final CEntryPointLiteral<GetTruffleEnvFunction> GET_TRUFFLE_ENV = CEntryPointLiteral.create(NativeAPIImpl.class, "getTruffleEnv", NativeTruffleContext.class);
    static final CEntryPointLiteral<AttachCurrentThreadFunction> ATTACH_CURRENT_THREAD = CEntryPointLiteral.create(NativeAPIImpl.class, "attachCurrentThread", NativeTruffleContext.class);
    static final CEntryPointLiteral<DetachCurrentThreadFunction> DETACH_CURRENT_THREAD = CEntryPointLiteral.create(NativeAPIImpl.class, "detachCurrentThread", NativeTruffleContext.class);

    private static Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext lookupContext(NativeTruffleContext context) {
        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);
        return support.resolveContextHandle(context.contextHandle());
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = EnterNativeTruffleEnvPrologue.class, publishAs = Publish.NotPublished)
    static NativeTruffleContext getTruffleContext(NativeTruffleEnv env) {
        return env.context();
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = EnterNativeTruffleEnvPrologue.class, publishAs = Publish.NotPublished)
    static TruffleObjectHandle newObjectRef(@SuppressWarnings("unused") NativeTruffleEnv env, TruffleObjectHandle handle) {
        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);
        Object object = support.resolveHandle(handle);
        return support.createGlobalHandle(object);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = EnterNativeTruffleEnvPrologue.class, publishAs = Publish.NotPublished)
    static void releaseObjectRef(@SuppressWarnings("unused") NativeTruffleEnv env, TruffleObjectHandle handle) {
        ImageSingletons.lookup(TruffleNFISupport.class).destroyGlobalHandle(handle);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = EnterNativeTruffleEnvPrologue.class, publishAs = Publish.NotPublished)
    static TruffleObjectHandle releaseAndReturn(@SuppressWarnings("unused") NativeTruffleEnv env, TruffleObjectHandle handle) {
        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);
        Object object = support.resolveHandle(handle);
        support.destroyGlobalHandle(handle);
        return TruffleNFISupport.createLocalHandle(object);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = EnterNativeTruffleEnvPrologue.class, publishAs = Publish.NotPublished)
    static int isSameObject(@SuppressWarnings("unused") NativeTruffleEnv env, TruffleObjectHandle handle1, TruffleObjectHandle handle2) {
        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);
        Object object1 = support.resolveHandle(handle1);
        Object object2 = support.resolveHandle(handle2);
        return object1 == object2 ? 1 : 0;
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = EnterNativeTruffleEnvPrologue.class, publishAs = Publish.NotPublished)
    static void newClosureRef(NativeTruffleEnv env, PointerBase closure) {
        Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext context = lookupContext(env.context());
        context.newClosureRef(closure.rawValue());
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = EnterNativeTruffleEnvPrologue.class, publishAs = Publish.NotPublished)
    static void releaseClosureRef(NativeTruffleEnv env, PointerBase closure) {
        Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext context = lookupContext(env.context());
        context.releaseClosureRef(closure.rawValue());
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = EnterNativeTruffleEnvPrologue.class, publishAs = Publish.NotPublished)
    static TruffleObjectHandle getClosureObject(NativeTruffleEnv env, PointerBase closure) {
        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);
        Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext context = lookupContext(env.context());
        TruffleObject ret = context.getClosureObject(closure.rawValue());
        return support.createGlobalHandle(ret);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = GetTruffleEnvPrologue.class, prologueBailout = ReturnNullPointer.class, publishAs = Publish.NotPublished)
    static NativeTruffleEnv getTruffleEnv(NativeTruffleContext context) {
        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);
        Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext ctx = support.resolveContextHandle(context.contextHandle());
        return WordFactory.pointer(ctx.getNativeEnv());
    }

    static class GetTruffleEnvPrologue implements CEntryPointOptions.Prologue {
        @Uninterruptible(reason = "prologue")
        static int enter(NativeTruffleContext context) {
            return CEntryPointActions.enterIsolate(context.isolate());
        }
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = AttachCurrentThreadPrologue.class, prologueBailout = ReturnNullPointer.class, publishAs = Publish.NotPublished)
    static NativeTruffleEnv attachCurrentThread(NativeTruffleContext context) {
        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);
        Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext ctx = support.resolveContextHandle(context.contextHandle());
        if (ctx.attachThread()) {
            return WordFactory.pointer(ctx.getNativeEnv());
        } else {
            return WordFactory.nullPointer();
        }
    }

    static class AttachCurrentThreadPrologue implements CEntryPointOptions.Prologue {
        @Uninterruptible(reason = "prologue")
        static int enter(NativeTruffleContext context) {
            return CEntryPointActions.enterAttachThread(context.isolate(), true);
        }
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(prologue = EnterNativeTruffleContextPrologue.class, epilogue = LeaveDetachThreadEpilogue.class, publishAs = Publish.NotPublished)
    static void detachCurrentThread(@SuppressWarnings("unused") NativeTruffleContext context) {
        TruffleNFISupport support = ImageSingletons.lookup(TruffleNFISupport.class);
        Target_com_oracle_truffle_nfi_backend_libffi_LibFFIContext ctx = support.resolveContextHandle(context.contextHandle());
        ctx.detachThread();
    }

    static class EnterNativeTruffleContextPrologue implements CEntryPointOptions.Prologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString("Thread failed to enter the isolate of the truffle context.");

        @Uninterruptible(reason = "prologue")
        static void enter(NativeTruffleContext context) {
            int code = CEntryPointActions.enterIsolate(context.isolate());
            if (code != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }

    static class EnterNativeTruffleEnvPrologue implements CEntryPointOptions.Prologue {
        private static final CGlobalData<CCharPointer> errorMessage = CGlobalDataFactory.createCString("Thread failed to enter its existing context.");

        @Uninterruptible(reason = "prologue")
        static void enter(NativeTruffleEnv env) {
            int code = CEntryPointActions.enter(env.isolateThread());
            if (code != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(code, errorMessage.get());
            }
        }
    }
}
