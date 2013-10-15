/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;

/**
 * HotSpot implementation of {@link MetaAccessProvider}.
 */
public class HotSpotMetaAccessProvider implements MetaAccessProvider {

    protected final HotSpotGraalRuntime runtime;

    public HotSpotMetaAccessProvider(HotSpotGraalRuntime runtime) {
        this.runtime = runtime;
    }

    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class parameter was null");
        }
        return HotSpotResolvedObjectType.fromClass(clazz);
    }

    public ResolvedJavaType lookupJavaType(Constant constant) {
        if (constant.getKind() != Kind.Object || constant.isNull()) {
            return null;
        }
        Object o = constant.asObject();
        return HotSpotResolvedObjectType.fromClass(o.getClass());
    }

    public Signature parseMethodDescriptor(String signature) {
        return new HotSpotSignature(signature);
    }

    public ResolvedJavaMethod lookupJavaMethod(Method reflectionMethod) {
        CompilerToVM c2vm = runtime.getCompilerToVM();
        HotSpotResolvedObjectType[] resultHolder = {null};
        long metaspaceMethod = c2vm.getMetaspaceMethod(reflectionMethod, resultHolder);
        assert metaspaceMethod != 0L;
        return resultHolder[0].createMethod(metaspaceMethod);
    }

    public ResolvedJavaMethod lookupJavaConstructor(Constructor reflectionConstructor) {
        CompilerToVM c2vm = runtime.getCompilerToVM();
        HotSpotResolvedObjectType[] resultHolder = {null};
        long metaspaceMethod = c2vm.getMetaspaceConstructor(reflectionConstructor, resultHolder);
        assert metaspaceMethod != 0L;
        return resultHolder[0].createMethod(metaspaceMethod);
    }

    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        return runtime.getCompilerToVM().getJavaField(reflectionField);
    }

    @Override
    public Constant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason) {
        final int actionShift = 0;
        final int reasonShift = 3;

        int actionValue = convertDeoptAction(action);
        int reasonValue = convertDeoptReason(reason);
        return Constant.forInt(~(((reasonValue) << reasonShift) + ((actionValue) << actionShift)));
    }

    public int convertDeoptAction(DeoptimizationAction action) {
        switch (action) {
            case None:
                return runtime.getConfig().deoptActionNone;
            case RecompileIfTooManyDeopts:
                return runtime.getConfig().deoptActionMaybeRecompile;
            case InvalidateReprofile:
                return runtime.getConfig().deoptActionReinterpret;
            case InvalidateRecompile:
                return runtime.getConfig().deoptActionMakeNotEntrant;
            case InvalidateStopCompiling:
                return runtime.getConfig().deoptActionMakeNotCompilable;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public int convertDeoptReason(DeoptimizationReason reason) {
        switch (reason) {
            case None:
                return runtime.getConfig().deoptReasonNone;
            case NullCheckException:
                return runtime.getConfig().deoptReasonNullCheck;
            case BoundsCheckException:
                return runtime.getConfig().deoptReasonRangeCheck;
            case ClassCastException:
                return runtime.getConfig().deoptReasonClassCheck;
            case ArrayStoreException:
                return runtime.getConfig().deoptReasonArrayCheck;
            case UnreachedCode:
                return runtime.getConfig().deoptReasonUnreached0;
            case TypeCheckedInliningViolated:
                return runtime.getConfig().deoptReasonTypeCheckInlining;
            case OptimizedTypeCheckViolated:
                return runtime.getConfig().deoptReasonOptimizedTypeCheck;
            case NotCompiledExceptionHandler:
                return runtime.getConfig().deoptReasonNotCompiledExceptionHandler;
            case Unresolved:
                return runtime.getConfig().deoptReasonUnresolved;
            case JavaSubroutineMismatch:
                return runtime.getConfig().deoptReasonJsrMismatch;
            case ArithmeticException:
                return runtime.getConfig().deoptReasonDiv0Check;
            case RuntimeConstraint:
                return runtime.getConfig().deoptReasonConstraint;
            case LoopLimitCheck:
                return runtime.getConfig().deoptReasonLoopLimitCheck;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
