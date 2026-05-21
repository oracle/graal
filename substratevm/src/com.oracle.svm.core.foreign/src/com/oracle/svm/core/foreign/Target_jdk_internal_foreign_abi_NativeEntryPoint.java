/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import java.lang.invoke.MethodType;

import org.graalvm.nativeimage.MissingForeignRegistrationError;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;

import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.NativeEntryPoint;
import jdk.internal.foreign.abi.VMStorage;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Packs the address of a {@code com.oracle.svm.hosted.foreign.DowncallStub} with some extra
 * information.
 */
@TargetClass(value = NativeEntryPoint.class, onlyWith = ForeignAPIPredicates.Enabled.class)
@Substitute
public final class Target_jdk_internal_foreign_abi_NativeEntryPoint {

    @Substitute //
    @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.Custom, declClass = MethodTypeTransformer.class) //
    private MethodType methodType;

    @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.Custom, declClass = DowncallAddressTransformer.class) //
    final CFunctionPointer downcallStubPointer;

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    final int captureMask;

    @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.Custom, declClass = DowncallInvokerAddressTransformer.class) //
    final CFunctionPointer downcallInvokerPointer;

    Target_jdk_internal_foreign_abi_NativeEntryPoint(MethodType methodType, CFunctionPointer downcallStubPointer, int captureMask) {
        this.methodType = methodType;
        this.downcallStubPointer = downcallStubPointer;
        this.downcallInvokerPointer = ForeignFunctionsRuntime.singleton().getDowncallStubInvokerPointer(methodType);
        this.captureMask = captureMask;
    }

    @Substitute
    public static Target_jdk_internal_foreign_abi_NativeEntryPoint make(ABIDescriptor abi,
                    VMStorage[] argMoves, VMStorage[] returnMoves,
                    MethodType methodType,
                    boolean needsReturnBuffer,
                    int capturedStateMask,
                    boolean needsTransition,
                    @SuppressWarnings("unused") boolean usingAddressPairs) {
        if (capturedStateMask != 0) {
            AbiUtils.singleton().checkLibrarySupport();
        }

        /*
         * A VMStorage may be null only when the Linker.Option.critical(allowHeapAccess=true) option
         * is passed. (see
         * jdk.internal.foreign.abi.x64.sysv.CallArranger.UnboxBindingCalculator.getBindings). It is
         * an implementation detail but this method is called by JDK code which cannot be changed to
         * pass the value of allowHeapAccess as well. If the FunctionDescriptor does not contain any
         * AddressLayout, then allowHeapAccess will always be false. We ensure this is the case by
         * construction in the NativeEntryPointInfo.make function.
         */
        boolean allowHeapAccess = false;
        for (VMStorage argMove : argMoves) {
            if (argMove == null) {
                allowHeapAccess = true;
                break;
            }
        }
        return NativeEntryPointInfo.makeEntryPoint(abi, argMoves, returnMoves, methodType, needsReturnBuffer, capturedStateMask, needsTransition, allowHeapAccess);
    }

    @Substitute
    public MethodType type() {
        return methodType;
    }

    static final class MethodTypeTransformer implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            assert receiver.getClass() == NativeEntryPoint.class;
            return ((NativeEntryPoint) receiver).type();
        }
    }

    static final class DowncallAddressTransformer implements FieldValueTransformer {
        private final ForeignFunctionsRuntime foreignFunctionsRuntime = ForeignFunctionsRuntime.singleton();

        @Override
        public Object transform(Object receiver, Object originalValue) {
            assert receiver.getClass() == NativeEntryPoint.class;
            try {
                JavaConstant nativeEntryPoint = GuestAccess.get().getSnippetReflection().forObject(receiver);
                NativeEntryPointInfo nativeEntryPointInfo = NativeEntryPointHelper.extractNativeEntryPointInfo(nativeEntryPoint);
                VMError.guarantee(nativeEntryPointInfo != null, "Cannot extract info for NativeEntryPoint because it is not in NEP_CACHE");
                return foreignFunctionsRuntime.ensureDowncallStubCreated(nativeEntryPointInfo);
            } catch (MissingForeignRegistrationError e) {
                throw rethrowMissingForeignRegistrationError(e);
            }
        }

        static UserException rethrowMissingForeignRegistrationError(MissingForeignRegistrationError e) {
            /*
             * MissingForeignRegistrationError is a LinkageError, which are deliberately ignored in
             * the ImageHeapScanner when reading a hosted field value. Therefore, explicitly catch
             * and rethrow with UserError.
             */
            throw UserError.abort(e, "Missing downcall stub registration");
        }
    }

    static final class DowncallInvokerAddressTransformer implements FieldValueTransformer {
        private final ForeignFunctionsRuntime foreignFunctionsRuntime = ForeignFunctionsRuntime.singleton();

        @Override
        public Object transform(Object receiver, Object originalValue) {
            assert receiver.getClass() == NativeEntryPoint.class;
            try {
                return foreignFunctionsRuntime.ensureDowncallStubInvokerCreated(((NativeEntryPoint) receiver).type());
            } catch (MissingForeignRegistrationError e) {
                throw DowncallAddressTransformer.rethrowMissingForeignRegistrationError(e);
            }
        }
    }
}
