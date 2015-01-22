/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.internal;

import java.lang.annotation.*;

/**
 * Internal DSL options to tune the generated code. These are expert options and not intended to be
 * changed used for guest language implementations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface DSLOptions {

    /** Flag has no effect anymore. Is going to be removed soon. */
    @Deprecated
    boolean useNewLayout() default true;

    /**
     * Lazy class loading ensures that all generated specialization classes are loaded lazily.
     * Disabling this feature will eagerly load all classes but will also reduce the generated code
     * size.
     */
    boolean useLazyClassLoading() default true;

    /**
     * Sets the optimization strategy for implicit casts.
     */
    ImplicitCastOptimization implicitCastOptimization() default ImplicitCastOptimization.DUPLICATE_TAIL;

    /** Not yet implemented. */
    boolean useDisjunctiveMethodGuardOptimization() default true;

    public enum ImplicitCastOptimization {

        /** Perform no informed optimization for implicit casts. */
        NONE,

        /** Duplicate specializations for each used implicit cast combination. */
        DUPLICATE_TAIL,

        /**
         * Use the same specialization for multiple combinations of implicit casts and specialize
         * them independently. Not yet fully implemented.
         */
        MERGE_CASTS;

        public boolean isNone() {
            return this == NONE;
        }

        public boolean isDuplicateTail() {
            return this == DUPLICATE_TAIL;
        }

        public boolean isMergeCasts() {
            return this == MERGE_CASTS;
        }
    }

    public enum TypeBoxingOptimization {
        /** Perform the optimization for all types. */
        ALWAYS,
        /** Perform the optimization just for primitive types. */
        PRIMITIVE,
        /** Perform the optimization for no types. */
        NONE;
    }

    /**
     * Defines the range of the generation of type specialized execute methods for return types and
     * for specialized parameter types. A type specialized execute method is generated as soon as
     * one declared type is either returned or used a specialized parameter.
     */
    TypeBoxingOptimization monomorphicTypeBoxingOptimization() default TypeBoxingOptimization.PRIMITIVE;

    /**
     * Defines the range of types for which type specialized execute methods should be used for
     * polymorphic operations.
     */
    TypeBoxingOptimization polymorphicTypeBoxingElimination() default TypeBoxingOptimization.PRIMITIVE;

    /**
     * Defines the range of types for which type specialized execute methods for implicit cast
     * optimizations are used. This option only has an effect if
     * {@link ImplicitCastOptimization#DUPLICATE_TAIL} or
     * {@link ImplicitCastOptimization#MERGE_CASTS} is set in {@link #implicitCastOptimization()}.
     */
    TypeBoxingOptimization implicitTypeBoxingOptimization() default TypeBoxingOptimization.PRIMITIVE;

    /**
     * Defines range of specialization return types in which the void boxing optimization is used.
     * Void boxing generates an extra execute method with {@link Void} return type in order to avoid
     * boxing and type checking of the return type in case the return type is not needed. For this
     * to work the operation class needs to provide an overridable execute method returning
     * {@link Void}.
     */
    TypeBoxingOptimization voidBoxingOptimization() default TypeBoxingOptimization.PRIMITIVE;

    public enum FallbackOptimization {
        /** Always generate an optimized fallback specialization. */
        ALWAYS,

        /**
         * Only generate an optimized fallback specialization if a method annotated with @Fallback
         * is used in the operation.
         */
        DECLARED,

        /**
         * Never generate an optimized fallback specialization. Please be aware that triggering a @Fallback
         * case without optimization will also invalidate your compiled code.
         */
        NEVER;
    }

    /** Defines the optimization strategy that is used to optimize @Fallback annotated methods. */
    FallbackOptimization optimizeFallback() default FallbackOptimization.DECLARED;

}
