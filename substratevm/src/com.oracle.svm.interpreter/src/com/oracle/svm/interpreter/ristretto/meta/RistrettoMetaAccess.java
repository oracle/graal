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
package com.oracle.svm.interpreter.ristretto.meta;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.metadata.CremaMethodAccess;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

public final class RistrettoMetaAccess implements MetaAccessProvider {
    private final MetaAccessProvider decoratee;

    public RistrettoMetaAccess(MetaAccessProvider decoratee) {
        this.decoratee = decoratee;
    }

    @Override
    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        ResolvedJavaType svmType = decoratee.lookupJavaType(clazz);
        assert svmType instanceof SubstrateType : Assertions.errorMessage("Must be a substrate type if it comes out of a meta access", svmType);
        SubstrateType substrateType = (SubstrateType) svmType;
        DynamicHub hub = substrateType.getHub();
        InterpreterResolvedJavaType iType = (InterpreterResolvedJavaType) hub.getInterpreterType();
        return RistrettoType.create(iType);
    }

    @Override
    public ResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod) {
        // we directly go over crema here
        return RistrettoMethod.create(CremaMethodAccess.toJVMCI(reflectionMethod));
    }

    @Override
    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        ResolvedJavaField substrateField = decoratee.lookupJavaField(reflectionField);
        assert substrateField instanceof SubstrateField : Assertions.errorMessage("Must be a substrate field if it comes out of a meta access", substrateField);
        DynamicHub hub = ((SubstrateType) substrateField.getDeclaringClass()).getHub();
        InterpreterResolvedJavaType iType = (InterpreterResolvedJavaType) hub.getInterpreterType();
        if (substrateField.isStatic()) {
            for (var iField : iType.getStaticFields()) {
                if (iField.getName().equals(substrateField.getName())) {
                    return RistrettoField.create((InterpreterResolvedJavaField) iField);
                }
            }
        } else {
            for (var iField : iType.getStaticFields()) {
                if (iField.getName().equals(substrateField.getName())) {
                    return RistrettoField.create((InterpreterResolvedJavaField) iField);
                }
            }
        }
        throw GraalError.shouldNotReachHere("Should have found iField for svmField " + substrateField);
    }

    @Override
    public ResolvedJavaType lookupJavaType(JavaConstant constant) {
        ResolvedJavaType svmType = decoratee.lookupJavaType(constant);
        if (svmType == null) {
            return null;
        }
        assert svmType instanceof SubstrateType : Assertions.errorMessage("Must be a substrate type if it comes out of a meta access", svmType);
        SubstrateType substrateType = (SubstrateType) svmType;
        DynamicHub hub = substrateType.getHub();
        InterpreterResolvedJavaType iType = (InterpreterResolvedJavaType) hub.getInterpreterType();
        return RistrettoType.create(iType);
    }

    @Override
    public long getMemorySize(JavaConstant constant) {
        return decoratee.getMemorySize(constant);
    }

    @Override
    public Signature parseMethodDescriptor(String methodDescriptor) {
        return decoratee.parseMethodDescriptor(methodDescriptor);
    }

    @Override
    public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int debugId) {
        return decoratee.encodeDeoptActionAndReason(action, reason, debugId);
    }

    @Override
    public JavaConstant encodeSpeculation(SpeculationLog.Speculation speculation) {
        return decoratee.encodeSpeculation(speculation);
    }

    @Override
    public SpeculationLog.Speculation decodeSpeculation(JavaConstant constant, SpeculationLog speculationLog) {
        return decoratee.decodeSpeculation(constant, speculationLog);
    }

    @Override
    public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
        return decoratee.decodeDeoptReason(constant);
    }

    @Override
    public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
        return decoratee.decodeDeoptAction(constant);
    }

    @Override
    public int decodeDebugId(JavaConstant constant) {
        return decoratee.decodeDebugId(constant);
    }

    @Override
    public int getArrayBaseOffset(JavaKind elementKind) {
        return decoratee.getArrayBaseOffset(elementKind);
    }

    @Override
    public int getArrayIndexScale(JavaKind elementKind) {
        return decoratee.getArrayIndexScale(elementKind);
    }
}
