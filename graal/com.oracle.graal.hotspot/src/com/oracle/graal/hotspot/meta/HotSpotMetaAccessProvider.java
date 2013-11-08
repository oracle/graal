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

    private static final int ACTION_SHIFT = 0;
    private static final int ACTION_MASK = 0x07;
    private static final int REASON_SHIFT = 3;
    private static final int REASON_MASK = 0x1f;
    private static final int DEBUG_SHIFT = 8;
    private static final int DEBUG_MASK = 0xffff;

    @Override
    public Constant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, short speculationId) {
        int actionValue = convertDeoptAction(action);
        int reasonValue = convertDeoptReason(reason);
        int speculationValue = speculationId & DEBUG_MASK;
        Constant c = Constant.forInt(~((speculationValue << DEBUG_SHIFT) | (reasonValue << REASON_SHIFT) | (actionValue << ACTION_SHIFT)));
        assert c.asInt() < 0;
        return c;
    }

    public DeoptimizationReason decodeDeoptReason(Constant constant) {
        int reasonValue = ((~constant.asInt()) >> REASON_SHIFT) & REASON_MASK;
        DeoptimizationReason reason = convertDeoptReason(reasonValue);
        return reason;
    }

    public DeoptimizationAction decodeDeoptAction(Constant constant) {
        int actionValue = ((~constant.asInt()) >> ACTION_SHIFT) & ACTION_MASK;
        DeoptimizationAction action = convertDeoptAction(actionValue);
        return action;
    }

    public short decodeSpeculationId(Constant constant) {
        return (short) (((~constant.asInt()) >> DEBUG_SHIFT) & DEBUG_MASK);
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

    public DeoptimizationAction convertDeoptAction(int action) {
        if (action == runtime.getConfig().deoptActionNone) {
            return DeoptimizationAction.None;
        } else if (action == runtime.getConfig().deoptActionMaybeRecompile) {
            return DeoptimizationAction.RecompileIfTooManyDeopts;
        } else if (action == runtime.getConfig().deoptActionReinterpret) {
            return DeoptimizationAction.InvalidateReprofile;
        } else if (action == runtime.getConfig().deoptActionMakeNotEntrant) {
            return DeoptimizationAction.InvalidateRecompile;
        } else if (action == runtime.getConfig().deoptActionMakeNotCompilable) {
            return DeoptimizationAction.InvalidateStopCompiling;
        } else {
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

    public DeoptimizationReason convertDeoptReason(int reason) {
        if (reason == runtime.getConfig().deoptReasonNone) {
            return DeoptimizationReason.None;
        } else if (reason == runtime.getConfig().deoptReasonNullCheck) {
            return DeoptimizationReason.NullCheckException;
        } else if (reason == runtime.getConfig().deoptReasonRangeCheck) {
            return DeoptimizationReason.BoundsCheckException;
        } else if (reason == runtime.getConfig().deoptReasonClassCheck) {
            return DeoptimizationReason.ClassCastException;
        } else if (reason == runtime.getConfig().deoptReasonArrayCheck) {
            return DeoptimizationReason.ArrayStoreException;
        } else if (reason == runtime.getConfig().deoptReasonUnreached0) {
            return DeoptimizationReason.UnreachedCode;
        } else if (reason == runtime.getConfig().deoptReasonTypeCheckInlining) {
            return DeoptimizationReason.TypeCheckedInliningViolated;
        } else if (reason == runtime.getConfig().deoptReasonOptimizedTypeCheck) {
            return DeoptimizationReason.OptimizedTypeCheckViolated;
        } else if (reason == runtime.getConfig().deoptReasonNotCompiledExceptionHandler) {
            return DeoptimizationReason.NotCompiledExceptionHandler;
        } else if (reason == runtime.getConfig().deoptReasonUnresolved) {
            return DeoptimizationReason.Unresolved;
        } else if (reason == runtime.getConfig().deoptReasonJsrMismatch) {
            return DeoptimizationReason.JavaSubroutineMismatch;
        } else if (reason == runtime.getConfig().deoptReasonDiv0Check) {
            return DeoptimizationReason.ArithmeticException;
        } else if (reason == runtime.getConfig().deoptReasonConstraint) {
            return DeoptimizationReason.RuntimeConstraint;
        } else if (reason == runtime.getConfig().deoptReasonLoopLimitCheck) {
            return DeoptimizationReason.LoopLimitCheck;
        } else {
            throw GraalInternalError.shouldNotReachHere(Integer.toHexString(reason));
        }
    }
}
