/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

public final class EspressoMetaAccessProvider implements MetaAccessProvider {
    @Override
    public native EspressoResolvedInstanceType lookupJavaType(Class<?> clazz);

    @Override
    public EspressoResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod) {
        Objects.requireNonNull(reflectionMethod);
        if (reflectionMethod instanceof Method) {
            return lookupMethod((Method) reflectionMethod);
        } else {
            assert reflectionMethod instanceof Constructor : reflectionMethod;
            return lookupConstructor((Constructor<?>) reflectionMethod);
        }
    }

    private native EspressoResolvedJavaMethod lookupMethod(Method reflectionMethod);

    private native EspressoResolvedJavaMethod lookupConstructor(Constructor<?> reflectionMethod);

    @Override
    public native EspressoResolvedJavaField lookupJavaField(Field reflectionField);

    @Override
    public ResolvedJavaType lookupJavaType(JavaConstant constant) {
        if (constant.isNull() || !constant.getJavaKind().isObject()) {
            return null;
        }
        return ((EspressoObjectConstant) constant).getType();
    }

    @Override
    public long getMemorySize(JavaConstant constant) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public Signature parseMethodDescriptor(String methodDescriptor) {
        return new EspressoSignature(methodDescriptor);
    }

    @Override
    public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int debugId) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public JavaConstant encodeSpeculation(SpeculationLog.Speculation speculation) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public SpeculationLog.Speculation decodeSpeculation(JavaConstant constant, SpeculationLog speculationLog) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public int decodeDebugId(JavaConstant constant) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public int getArrayBaseOffset(JavaKind elementKind) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public int getArrayIndexScale(JavaKind elementKind) {
        throw JVMCIError.unimplemented();
    }
}
