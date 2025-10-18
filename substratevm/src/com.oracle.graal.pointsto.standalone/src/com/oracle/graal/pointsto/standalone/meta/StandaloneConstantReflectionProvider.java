/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2024, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.meta;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class StandaloneConstantReflectionProvider implements ConstantReflectionProvider {
    private final AnalysisUniverse universe;
    private final ConstantReflectionProvider original;

    public StandaloneConstantReflectionProvider(AnalysisUniverse universe, ConstantReflectionProvider original) {
        this.universe = universe;
        this.original = original;
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        return original.constantEquals(x, y);
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        return original.readArrayLength(array);
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        return universe.getHostedValuesProvider().interceptHosted(original.readArrayElement(array, index));
    }

    @Override
    public final JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        ResolvedJavaField wrappedField = ((AnalysisField) field).getWrapped();
        JavaConstant ret = universe.getHostedValuesProvider().interceptHosted(original.readFieldValue(wrappedField, receiver));
        if (ret == null) {
            ret = wrappedField.getConstantValue();
            if (ret == null) {
                ret = JavaConstant.defaultForKind(wrappedField.getJavaKind());
            }
        }
        return ret;
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        return universe.getHostedValuesProvider().interceptHosted(original.boxPrimitive(source));
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        return universe.getHostedValuesProvider().interceptHosted(original.unboxPrimitive(source));
    }

    @Override
    public JavaConstant forString(String value) {
        return universe.getHostedValuesProvider().interceptHosted(original.forString(value));
    }

    /**
     * The correctness of this method is verified by
     * com.oracle.graal.pointsto.test.ClassEqualityTest.
     */
    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return original.asJavaClass(markReachable(type));
    }

    @Override
    public final Constant asObjectHub(ResolvedJavaType type) {
        return original.asObjectHub(markReachable(type));
    }

    private static ResolvedJavaType markReachable(ResolvedJavaType type) {
        if (type instanceof AnalysisType) {
            AnalysisType t = (AnalysisType) type;
            t.registerAsReachable("registered by the StandaloneConstantReflectionProvider");
            return t.getWrapped();
        } else {
            return type;
        }
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        ResolvedJavaType originalJavaType = original.asJavaType(constant);
        if (originalJavaType != null) {
            return universe.lookup(originalJavaType);
        }
        return null;
    }

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        return original.getMethodHandleAccess();
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return original.getMemoryAccessProvider();
    }
}
