/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;

/**
 * Generate a stub to be called via foreign call. This annotation is valid for methods annotated
 * with {@link NodeIntrinsic} only. To trigger stub generation, a marker class annotated with
 * {@link GeneratedStubsHolder} is required. Processed by {@code IntrinsicStubProcessor}.
 * <p>
 * CPU feature declarations have three distinct roles:
 * <ul>
 * <li><em>Minimum features</em> determine whether the unsuffixed baseline stub can be compiled for
 * the image's build-time target. Multiple sets describe alternative implementations. If no set is
 * declared after resolving defaults, an implicit empty set permits a feature-independent
 * baseline.</li>
 * <li><em>Runtime-compilation features</em> generate additional stub variants for JIT compilation.
 * A variant is selected only when all its features are available to that compilation.</li>
 * <li><em>Guarded features</em> generate run-time feature checks and guarded fast paths within the
 * baseline stub. If a check fails, execution continues in baseline-compatible code.</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Repeatable(GenerateStubs.class)
public @interface GenerateStub {

    /**
     * Name of the stub method. Defaults to the annotated method's name. Must be unique.
     */
    String name() default "";

    /**
     * Optional values for parameters annotated with {@link ConstantNodeParameter}. The string
     * content is pasted as-is into the generated code, with the only exception being enum values -
     * in that case, the enum class name and a dot is prepended to the string.
     */
    String[] parameters() default {};

    /**
     * Optional names of static methods in the current class that return minimum sets of required
     * AMD64 CPU features as {@link java.util.EnumSet}s.
     *
     * Multiple alternative minimum sets may be specified, from most to least preferred. Supersets
     * must precede subsets. Every entry is eligible to determine whether the unsuffixed baseline
     * stub can be compiled. If a strict superset is intended only as a preferred JIT specialization
     * rather than as an alternative baseline requirement, declare it in
     * {@link #runtimeCPUFeaturesAMD64()} instead.
     * <p>
     * If this member is omitted from a {@link GenerateStub} annotation, it inherits any
     * method-level and then class-level {@link GenerateStub.Default} value. Explicitly specifying
     * {@code minimumCPUFeaturesAMD64 = {}} suppresses such an inherited value. After defaults are
     * resolved, the absence of getter names denotes one empty feature set, allowing a baseline stub
     * with no additional CPU feature requirements.
     */
    String[] minimumCPUFeaturesAMD64() default {};

    /**
     * Optional names of static methods in the current class that return minimum sets of required
     * AARCH64 CPU features as {@link java.util.EnumSet}s.
     *
     * Multiple alternative minimum sets may be specified, from most to least preferred. Supersets
     * must precede subsets. Every entry is eligible to determine whether the unsuffixed baseline
     * stub can be compiled. If a strict superset is intended only as a preferred JIT specialization
     * rather than as an alternative baseline requirement, declare it in
     * {@link #runtimeCPUFeaturesAARCH64()} instead.
     * <p>
     * If this member is omitted from a {@link GenerateStub} annotation, it inherits any
     * method-level and then class-level {@link GenerateStub.Default} value. Explicitly specifying
     * {@code minimumCPUFeaturesAARCH64 = {}} suppresses such an inherited value. After defaults are
     * resolved, the absence of getter names denotes one empty feature set, allowing a baseline stub
     * with no additional CPU feature requirements.
     */
    String[] minimumCPUFeaturesAARCH64() default {};

    /**
     * Optional names of static methods in the current class that return AMD64 CPU feature sets for
     * additional runtime-compilation stub variants as {@link java.util.EnumSet}s, from most to
     * least preferred. Supersets must precede subsets.
     * <p>
     * If this member is omitted from a {@link GenerateStub} annotation, it inherits any
     * method-level and then class-level {@link GenerateStub.Default} value. Explicitly specifying
     * {@code runtimeCPUFeaturesAMD64 = {}} suppresses such an inherited value.
     */
    String[] runtimeCPUFeaturesAMD64() default {};

    /**
     * Optional names of static methods in the current class that return AARCH64 CPU feature sets
     * for additional runtime-compilation stub variants as {@link java.util.EnumSet}s, from most to
     * least preferred. Supersets must precede subsets.
     * <p>
     * If this member is omitted from a {@link GenerateStub} annotation, it inherits any
     * method-level and then class-level {@link GenerateStub.Default} value. Explicitly specifying
     * {@code runtimeCPUFeaturesAARCH64 = {}} suppresses such an inherited value.
     */
    String[] runtimeCPUFeaturesAARCH64() default {};

    /**
     * Optional names of static methods in the current class that return AMD64 CPU feature sets used
     * to generate inline fast paths protected by run-time CPU feature checks in the baseline stub,
     * from most to least preferred. Supersets must precede subsets.
     * <p>
     * If this member is omitted from a {@link GenerateStub} annotation, it inherits any
     * method-level and then class-level {@link GenerateStub.Default} value. Explicitly specifying
     * {@code guardedCPUFeaturesAMD64 = {}} suppresses such an inherited value.
     */
    String[] guardedCPUFeaturesAMD64() default {};

    /**
     * Optional names of static methods in the current class that return AARCH64 CPU feature sets
     * used to generate inline fast paths protected by run-time CPU feature checks in the baseline
     * stub, from most to least preferred. Supersets must precede subsets.
     * <p>
     * If this member is omitted from a {@link GenerateStub} annotation, it inherits any
     * method-level and then class-level {@link GenerateStub.Default} value. Explicitly specifying
     * {@code guardedCPUFeaturesAARCH64 = {}} suppresses such an inherited value.
     */
    String[] guardedCPUFeaturesAARCH64() default {};

    /**
     * Default CPU feature configuration for {@link GenerateStub} annotations. On a class, this
     * configuration applies to all stub-generating methods in the class. On a method, it applies to
     * all {@link GenerateStub} annotations on that method and takes precedence over the class-level
     * configuration. Values specified directly in {@link GenerateStub} take precedence over both.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface Default {

        /**
         * Shared default value of {@link GenerateStub#minimumCPUFeaturesAMD64()} for all
         * {@link GenerateStub} annotations on this method or class, unless otherwise specified.
         */
        String[] minimumCPUFeaturesAMD64() default {};

        /**
         * Shared default value of {@link GenerateStub#minimumCPUFeaturesAARCH64()} for all
         * {@link GenerateStub} annotations on this method or class, unless otherwise specified.
         */
        String[] minimumCPUFeaturesAARCH64() default {};

        /**
         * Shared default value of {@link GenerateStub#runtimeCPUFeaturesAMD64()} for all
         * {@link GenerateStub} annotations on this method or class, unless otherwise specified.
         */
        String[] runtimeCPUFeaturesAMD64() default {};

        /**
         * Shared default value of {@link GenerateStub#runtimeCPUFeaturesAARCH64()} for all
         * {@link GenerateStub} annotations on this method or class, unless otherwise specified.
         */
        String[] runtimeCPUFeaturesAARCH64() default {};

        /**
         * Shared default value of {@link GenerateStub#guardedCPUFeaturesAMD64()} for all
         * {@link GenerateStub} annotations on this method or class, unless otherwise specified.
         */
        String[] guardedCPUFeaturesAMD64() default {};

        /**
         * Shared default value of {@link GenerateStub#guardedCPUFeaturesAARCH64()} for all
         * {@link GenerateStub} annotations on this method or class, unless otherwise specified.
         */
        String[] guardedCPUFeaturesAARCH64() default {};
    }
}
