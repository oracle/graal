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

import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

import com.oracle.graal.pointsto.heap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A {@link ConstantReflectionProvider} implementation for standalone use.
 */
public class StandaloneConstantReflectionProvider implements ConstantReflectionProvider {
    /**
     * The analysis universe used by this provider.
     */
    private final AnalysisUniverse universe;
    /**
     * The original {@link ConstantReflectionProvider} used by this provider.
     */
    private final ConstantReflectionProvider original;
    /**
     * The {@link AnalysisField} representing the {@code ForkJoinPool.common} field. If not null,
     * {@link #commonPoolSubstitution} should not be null either and each field access of
     * ForkJoinPool.common in the compiled application will be replaced with this substitution.
     */
    private AnalysisField commonPoolField;
    /**
     * A substitution for the {@code ForkJoinPool.common} field used to prevent the pollution of
     * analysis state with analysis tasks (the analysis itself is using {@link ForkJoinPool}.
     */
    private JavaConstant commonPoolSubstitution;

    /**
     * Creates a new {@link StandaloneConstantReflectionProvider} instance.
     *
     * @param aMetaAccess the analysis meta access
     * @param universe the analysis universe
     * @param original the original {@link ConstantReflectionProvider}
     * @param originalSnippetReflection the original snippet reflection provider
     * @param usingGuestContext true if a guest context is used to achieve isolation between the vm
     *            performing the compilation and the compiled application
     */
    public StandaloneConstantReflectionProvider(AnalysisMetaAccess aMetaAccess, AnalysisUniverse universe, ConstantReflectionProvider original,
                    SnippetReflectionProvider originalSnippetReflection, boolean usingGuestContext) {
        this.universe = universe;
        this.original = original;
        if (!usingGuestContext) {
            commonPoolField = aMetaAccess.lookupJavaField(ReflectionUtil.lookupField(ForkJoinPool.class, "common"));
            commonPoolSubstitution = originalSnippetReflection.forObject(new ForkJoinPool());
        }
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        return original.constantEquals(x, y);
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        if (array instanceof ImageHeapConstant) {
            if (array instanceof ImageHeapArray) {
                return ((ImageHeapArray) array).getLength();
            }
            return null;
        }
        if (array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        return original.readArrayLength(array);
    }

    @Override
    public int identityHashCode(JavaConstant constant) {
        JavaKind kind = Objects.requireNonNull(constant).getJavaKind();
        if (kind != JavaKind.Object) {
            throw new IllegalArgumentException("Constant has unexpected kind " + kind + ": " + constant);
        }
        if (constant.isNull()) {
            /* System.identityHashCode is specified to return 0 when passed null. */
            return 0;
        }
        if (!(constant instanceof ImageHeapConstant imageHeapConstant)) {
            throw new IllegalArgumentException("Constant has unexpected type " + constant.getClass() + ": " + constant);
        }
        if (imageHeapConstant.hasIdentityHashCode()) {
            return imageHeapConstant.getIdentityHashCode();
        }
        Object hostedObject = Objects.requireNonNull(universe.getSnippetReflection().asObject(Object.class, constant));
        return System.identityHashCode(hostedObject);
    }

    @Override
    public int makeIdentityHashCode(JavaConstant constant, int requestedValue) {
        throw GraalError.unimplemented("makeIdentityHashCode");
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        return universe.getHostedValuesProvider().interceptHosted(original.readArrayElement(array, index));
    }

    @Override
    public final JavaConstant readFieldValue(ResolvedJavaField f, JavaConstant receiver) {
        AnalysisField field = (AnalysisField) f;

        field.beforeFieldValueAccess();

        if (field.equals(commonPoolField)) {
            /*
             * Replace the common pool value from the hosted heap with a new object. The common pool
             * is used during analysis, so it can expose analysis metadata to the analysis itself,
             * i.e., the analysis engine analyzing itself.
             */
            assert commonPoolSubstitution != null : "A substitution for the common pool must be provided if commonPoolField is set.";
            return commonPoolSubstitution;
        }
        return universe.getHostedValuesProvider().interceptHosted(original.readFieldValue(field.wrapped, receiver));
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
