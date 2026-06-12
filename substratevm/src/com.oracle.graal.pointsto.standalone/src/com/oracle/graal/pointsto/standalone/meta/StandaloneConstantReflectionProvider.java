/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.standalone.heap.StandaloneFieldValueAvailabilitySupport;
import com.oracle.svm.shared.util.ReflectionUtil;

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
     * Standalone-owned field-value availability layer used to decide when static reads may switch
     * from original-provider semantics to shadow-heap-backed values.
     */
    private final StandaloneFieldValueAvailabilitySupport fieldValueAvailabilitySupport;

    /**
     * Creates a new {@link StandaloneConstantReflectionProvider} instance.
     *
     * @param aMetaAccess the analysis meta access
     * @param universe the analysis universe
     * @param original the original {@link ConstantReflectionProvider}
     * @param originalSnippetReflection the original snippet reflection provider
     * @param fullyIsolated true if the analysis is using a fully isolated
     *            {@link jdk.graal.compiler.vmaccess.VMAccess} between the vm performing the
     *            compilation and the compiled application
     */
    public StandaloneConstantReflectionProvider(AnalysisMetaAccess aMetaAccess, AnalysisUniverse universe, ConstantReflectionProvider original,
                    SnippetReflectionProvider originalSnippetReflection, boolean fullyIsolated,
                    StandaloneFieldValueAvailabilitySupport fieldValueAvailabilitySupport) {
        this.universe = universe;
        this.original = original;
        this.fieldValueAvailabilitySupport = fieldValueAvailabilitySupport;
        if (!fullyIsolated) {
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
        if (array instanceof ImageHeapArray imageHeapArray) {
            if (index < 0 || index >= imageHeapArray.getLength()) {
                return null;
            }
            imageHeapArray.ensureReaderInstalled();
            return imageHeapArray.readElementValue(index);
        }
        return original.readArrayElement(array, index);
    }

    /**
     * Reads a hosted field value while unwrapping image-heap receiver constants before delegating
     * to the original reflection provider.
     */
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
        if (field.isStatic()) {
            switch (fieldValueAvailabilitySupport.getStaticFieldReadPolicy(field)) {
                case SHADOW_HEAP:
                    /*
                     * Runtime-initialized classes keep their original provider semantics. Only classes
                     * whose build-time initialization completed may use shadow-heap snapshots as the
                     * source of truth for static field reads.
                     */
                    return field.getDeclaringClass().getOrComputeData().readFieldValue(field);
                case ORIGINAL_PROVIDER:
                    break;
            }
        }
        if (receiver instanceof ImageHeapInstance imageHeapInstance) {
            imageHeapInstance.ensureReaderInstalled();
            return imageHeapInstance.readFieldValue(field);
        }
        JavaConstant fieldReceiver = receiver;
        if (receiver instanceof ImageHeapConstant imageHeapConstant && imageHeapConstant.getHostedObject() != null) {
            /*
             * Unwrap before passing into the original ConstantReflectionProvider.
             */
            fieldReceiver = imageHeapConstant.getHostedObject();
        }
        return original.readFieldValue(field.wrapped, fieldReceiver);
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        return original.boxPrimitive(source);
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        return original.unboxPrimitive(source);
    }

    @Override
    public JavaConstant forString(String value) {
        return original.forString(value);
    }

    /**
     * The correctness of this method is verified by
     * com.oracle.graal.pointsto.test.ClassEqualityTest.
     *
     * Standalone does not create a native-image-style {@code DynamicHub} representation for class
     * constants. It still allows raw guest {@link Class} constants to be scanned as ordinary
     * image-heap objects, and {@link #asJavaType(Constant)} keeps the represented type reachable when
     * those constants are observed.
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
        /*
         * The native-image-style class-constant path is intentionally skipped in standalone for
         * now. See asJavaClass() above for the Terminus-specific rationale.
         */
        ResolvedJavaType originalJavaType = original.asJavaType(constant);
        if (originalJavaType != null) {
            AnalysisType type = universe.lookup(originalJavaType);
            /*
             * Standalone still exposes raw guest Class constants instead of hosted DynamicHub
             * objects, so scanning a class constant must also make the represented type reachable.
             * Keep this temporary symmetry with asJavaClass() until standalone grows a dedicated
             * class-constant model.
             */
            type.registerAsReachable("registered by the StandaloneConstantReflectionProvider.asJavaType");
            return type;
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
