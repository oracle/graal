/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import java.util.function.Function;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubUtils;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * JVMCI representation of a {@link ResolvedJavaType} used by Ristretto for compilation. Exists once
 * per {@link InterpreterResolvedJavaType}. Allocated during runtime compilation every time a type
 * is accessed.
 * <p>
 * Life cycle: lives until the referencing {@link InterpreterResolvedJavaType} is gc-ed.
 */
public final class RistrettoType extends SubstrateType {
    public static final Function<InterpreterResolvedJavaType, ResolvedJavaType> RISTRETTO_TYPE_FUNCTION = RistrettoType::new;

    private final InterpreterResolvedJavaType interpreterType;

    private RistrettoType(InterpreterResolvedJavaType interpreterType) {
        super(interpreterType.getJavaKind(), interpreterType.getHub());
        this.interpreterType = interpreterType;
    }

    public InterpreterResolvedJavaType getInterpreterType() {
        return interpreterType;
    }

    public static RistrettoType getOrCreate(InterpreterResolvedJavaType interpreterType) {
        return (RistrettoType) interpreterType.getRistrettoType(RISTRETTO_TYPE_FUNCTION);
    }

    @Override
    public String toString() {
        return "RistrettoType{super=" + super.toString() + ", interpreterType=" + interpreterType + "}";
    }

    @Override
    public ResolvedJavaType getComponentType() {
        if (isArray()) {
            InterpreterResolvedJavaType iComponentType = (InterpreterResolvedJavaType) interpreterType.getComponentType();
            GraalError.guarantee(iComponentType != null, "Must find component type if we are dealing with an array, this %s component type %s", this, iComponentType);
            return getOrCreate(iComponentType);
        } else {
            return null;
        }
    }

    @Override
    public boolean isArray() {
        return interpreterType.isArray();
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        return RistrettoUtils.toRFields(interpreterType.getStaticFields());
    }

    @Override
    public SubstrateField[] getInstanceFields(boolean includeSuperclasses) {
        return RistrettoUtils.toRFields(interpreterType.getInstanceFields(includeSuperclasses));
    }

    @Override
    public ResolvedJavaMethod resolveConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        if (method instanceof RistrettoMethod rMethod) {
            InterpreterResolvedJavaMethod resolvedMethod = (InterpreterResolvedJavaMethod) interpreterType.resolveConcreteMethod(rMethod.getInterpreterMethod(), callerType);
            return resolvedMethod == null ? null : RistrettoMethod.getOrCreate(resolvedMethod);
        }
        return super.resolveConcreteMethod(method, callerType);
    }

    /**
     * Normalizes {@code otherType} so the inherited hierarchy walk uses only Ristretto types.
     */
    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        return super.findLeastCommonAncestor(normalizeJVMCIType(otherType));
    }

    /**
     * Normalizes {@code other} so base type hierarchy walks can test it against Ristretto metadata.
     */
    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        ResolvedJavaType normalizedOther = normalizeJVMCIType(other);
        assert normalizedOther instanceof RistrettoType : Assertions.errorMessage("Must be convertible to Ristretto metadata", this, other);
        RistrettoType rTypeOther = (RistrettoType) normalizedOther;
        return this.interpreterType.isAssignableFrom(rTypeOther.interpreterType);
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        InterpreterResolvedJavaType iArrayType = interpreterType.getArrayClass();
        assert iArrayType != null;
        return RistrettoType.getOrCreate(iArrayType);
    }

    @Override
    public AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        AssumptionResult<ResolvedJavaType> result = interpreterType.findLeafConcreteSubtype();
        if (result == null) {
            return null;
        }
        ResolvedJavaType ristrettoResult = normalizeJVMCIType(result.getResult());
        /*
         * An assumption-free result is a closed fact in the current image, so there is no runtime
         * dependency to retain.
         */
        if (result.isAssumptionFree()) {
            return new AssumptionResult<>(ristrettoResult);
        }
        return new AssumptionResult<>(ristrettoResult, retainRuntimeAssumptions(result));
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        ResolvedJavaType result = interpreterType.getSingleImplementor();
        return result == null ? null : normalizeJVMCIType(result);
    }

    @Override
    public AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        if (method instanceof RistrettoMethod rMethod) {
            AssumptionResult<ResolvedJavaMethod> result = interpreterType.findUniqueConcreteMethod(rMethod.getInterpreterMethod());
            if (result == null) {
                return null;
            }
            ResolvedJavaMethod ristrettoResult = toRequiredRistrettoMethod(result.getResult());
            if (result.isAssumptionFree()) {
                return new AssumptionResult<>(ristrettoResult);
            }
            return new AssumptionResult<>(ristrettoResult, retainRuntimeAssumptions(result));
        }
        return super.findUniqueConcreteMethod(method);
    }

    private static ResolvedJavaType normalizeJVMCIType(ResolvedJavaType type) {
        if (type instanceof InterpreterResolvedJavaType iType) {
            return getOrCreate(iType);
        }
        if (type instanceof SubstrateType sType) {
            return RistrettoUtils.toRType(sType);
        }
        return type;
    }

    /**
     * Replays the original JVMCI assumptions into an owned array. Runtime hierarchy queries create
     * these assumptions with canonical interpreter metadata, so only the query result is translated
     * to its compiler-side Ristretto representation.
     */
    private static Assumption[] retainRuntimeAssumptions(AssumptionResult<?> result) {
        Assumptions runtimeAssumptions = new Assumptions();
        result.recordTo(runtimeAssumptions);
        return runtimeAssumptions.toArray();
    }

    private static RistrettoMethod toRequiredRistrettoMethod(ResolvedJavaMethod method) {
        if (method instanceof RistrettoMethod rMethod) {
            return rMethod;
        }
        if (method instanceof InterpreterResolvedJavaMethod iMethod) {
            return RistrettoMethod.getOrCreate(iMethod);
        }
        if (method instanceof SubstrateMethod substrateMethod) {
            RistrettoMethod rMethod = RistrettoUtils.toRMethodOrNull(substrateMethod);
            if (rMethod != null) {
                return rMethod;
            }
        }
        throw GraalError.shouldNotReachHere("Cannot map assumption method to Ristretto metadata: " + method);
    }

    @Override
    public SubstrateType getSuperclass() {
        DynamicHub superHub = getHub().getSuperHub();
        if (superHub == null) {
            return null;
        }
        return RistrettoType.getOrCreate((InterpreterResolvedJavaType) DynamicHubUtils.getInterpreterType(superHub));
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        ResolvedJavaType[] interfaces = super.getInterfaces();
        ResolvedJavaType[] result = new ResolvedJavaType[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            result[i] = RistrettoUtils.toRType((SubstrateType) interfaces[i]);
        }
        return result;
    }

    @Override
    protected SubstrateType getSuperType() {
        if (isArray() || isInterface()) {
            return RistrettoType.getOrCreate((InterpreterResolvedJavaType) DynamicHubUtils.getInterpreterType(DynamicHub.fromClass(Object.class)));
        } else {
            return getSuperclass();
        }
    }
}
