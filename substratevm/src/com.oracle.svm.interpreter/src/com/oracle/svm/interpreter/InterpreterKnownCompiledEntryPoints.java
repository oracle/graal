/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import com.oracle.svm.core.graal.code.StubCallingConvention;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.InvalidMethodPointerHandler;
import com.oracle.svm.core.MethodRefHolder;
import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.espresso.shared.meta.ErrorType;
import com.oracle.svm.guest.staging.jdk.InternalVMMethod;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedPrimitiveType;
import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.Disallowed;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;

/**
 * Registry for compiled entry points that are known to the interpreter.
 * <p>
 * Most interpreted methods either keep bytecodes so they can execute in the interpreter, or carry a
 * native entry point so the interpreter can call already compiled code. This class keeps the small
 * set of special compiled entry points that do not come from ordinary user methods:
 * <ul>
 * <li>a sentinel handler used when an AOT method was not compiled, and
 * <li>throwing stubs used as dispatch targets for method-selection failures that must raise a JVM
 * linkage error.
 * </ul>
 * The stubs are registered as image roots during analysis so the interpreter can reference their
 * {@link MethodRefHolder}s at run time.
 */
@InternalVMMethod
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
public class InterpreterKnownCompiledEntryPoints {
    private final MethodRefHolder methodNotCompiledFtnPtr;

    private final PreparedSignature stubSignature;
    private final MethodRefHolder throwIllegalAccessError;
    private final MethodRefHolder throwIncompatibleClassChangeError;
    private final MethodRefHolder throwAbstractMethodError;

    @Platforms(Platform.HOSTED_ONLY.class)
    InterpreterKnownCompiledEntryPoints(FeatureImpl.BeforeAnalysisAccessImpl access, AnalysisMetaAccess metaAccess) {
        AnalysisMethod notCompiledHandler = metaAccess.lookupJavaMethod(InvalidMethodPointerHandler.METHOD_POINTER_NOT_COMPILED_HANDLER_METHOD);
        methodNotCompiledFtnPtr = new MethodRefHolder(InterpreterResolvedJavaMethod.createMethodRef(notCompiledHandler));

        AnalysisMethod iaeStub = metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(InterpreterKnownCompiledEntryPoints.class, "throwIllegalAccessErrorStub"));
        AnalysisMethod icceStub = metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(InterpreterKnownCompiledEntryPoints.class, "throwIncompatibleClassChangeErrorStub"));
        AnalysisMethod ameStub = metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(InterpreterKnownCompiledEntryPoints.class, "throwAbstractMethodErrorStub"));

        throwIllegalAccessError = new MethodRefHolder(InterpreterResolvedJavaMethod.createMethodRef(iaeStub));
        throwIncompatibleClassChangeError = new MethodRefHolder(InterpreterResolvedJavaMethod.createMethodRef(icceStub));
        throwAbstractMethodError = new MethodRefHolder(InterpreterResolvedJavaMethod.createMethodRef(ameStub));

        access.registerAsRoot(iaeStub, false, "Needed for interpreting non-public method selection for invokeinterface");
        access.registerAsRoot(icceStub, false, "Needed for interpreting resolution failure");
        access.registerAsRoot(ameStub, false, "Needed for interpreting abstract methods.");

        this.stubSignature = InterpreterSupportImpl.singleton().prepareSignature(
                        InterpreterUnresolvedSignature.create(InterpreterResolvedPrimitiveType.fromKind(JavaKind.Void), new JavaType[0]),
                        false,
                        /*- Should not need resolution */ null);
    }

    public static InterpreterKnownCompiledEntryPoints singleton() {
        return ImageSingletons.lookup(InterpreterKnownCompiledEntryPoints.class);
    }

    /**
     * Returns the sentinel native entry point for AOT methods that analysis decided not to compile.
     * The interpreter treats this pointer as "not callable" and reports a preservation/reachability
     * error instead of trying to jump to it.
     */
    public static <T extends CFunctionPointer> T getMethodNotCompiledHandler() {
        T ptr = singleton().methodNotCompiledFtnPtr.getFunctionPointer();
        assert ptr.rawValue() != 0;
        return ptr;
    }

    /**
     * The common no-argument, void-return signature used by the throwing stubs in this class.
     */
    public static PreparedSignature getStubSignature() {
        return singleton().stubSignature;
    }

    /**
     * Entry point that throws {@link IllegalAccessError}. Used for interface dispatch-table entries whose
     * method selection succeeds symbolically but selects a non-public method.
     */
    public static MethodRefHolder getThrowIllegalAccessErrorStub() {
        return singleton().throwIllegalAccessError;
    }

    /**
     * Entry point that throws {@link IncompatibleClassChangeError}. Used for dispatch-table entries
     * that represent incompatible method-selection results, ie: entries for which there were multiple maximally-specific interface methods.
     */
    public static MethodRefHolder getThrowIncompatibleClassChangeErrorStub() {
        return singleton().throwIncompatibleClassChangeError;
    }

    /**
     * Entry point that throws {@link AbstractMethodError}. Used when dispatch selects an abstract
     * method.
     */
    public static MethodRefHolder getThrowAbstractMethodErrorStub() {
        return singleton().throwAbstractMethodError;
    }

    /**
     * Maps resolver failure categories to the corresponding throwing stub.
     */
    public static MethodRefHolder forErrorType(ErrorType errorType) {
        return switch (errorType) {
            case AbstractMethodError -> getThrowAbstractMethodErrorStub();
            case IncompatibleClassChangeError -> getThrowIncompatibleClassChangeErrorStub();
            case IllegalAccessError -> getThrowIllegalAccessErrorStub();
            default -> throw VMError.shouldNotReachHere("No known stub for error type: " + errorType);
        };
    }

    @SuppressWarnings("unused")
    @StubCallingConvention
    private static void throwIllegalAccessErrorStub() {
        throw new IllegalAccessError();
    }

    @SuppressWarnings("unused")
    @StubCallingConvention
    private static void throwIncompatibleClassChangeErrorStub() {
        throw new IncompatibleClassChangeError();
    }

    @SuppressWarnings("unused")
    @StubCallingConvention
    private static void throwAbstractMethodErrorStub() {
        throw new AbstractMethodError();
    }
}
