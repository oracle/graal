/*
 * Copyright (c) 2010, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Mechanism to change the value of a field. Normally, field values in the native image heap of the
 * Substrate VM are just taken from the host VM. This annotation allows the field value to be
 * intercepted and recomputed.
 * <p>
 * This annotation must be used on a field also annotated with {@link Alias} to specify the field
 * whose value needs to be changed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RecomputeFieldValue {

    enum Kind {
        /**
         * The initial field value is not modified. This is the default behavior when no
         * {@link RecomputeFieldValue} annotation is present.
         */
        None,
        /**
         * The field is reset to the default value (null, 0, false).
         */
        Reset,
        /**
         * The object field is set to a instance of {@link #declClass} created by calling the
         * default constructor.
         */
        NewInstance,
        /**
         * The field is set to the value assigned to the {@link Alias} field.
         */
        FromAlias,
        /**
         * The int or long field is set to the offset of the field named {@link #name()} of the
         * class {@link #declClass}, as it would be computed by
         * {@link sun.misc.Unsafe#objectFieldOffset}.
         */
        FieldOffset,
        /**
         * The int or long field is set to the offset of the first array element of the array class
         * {@link #declClass}, as it would be computed by
         * {@link sun.misc.Unsafe#arrayBaseOffset(Class)}.
         */
        ArrayBaseOffset,
        /**
         * The int or long field is set to the element size array class {@link #declClass}, as it
         * would be computed by {@link sun.misc.Unsafe#arrayIndexScale(Class)}.
         */
        ArrayIndexScale,
        /**
         * The int or long field is set to the log2 of {@link #ArrayIndexScale}.
         */
        ArrayIndexShift,
        /**
         * Special support for field offsets used by
         * java.util.concurrent.atomic.AtomicXxxFieldUpdater.
         */
        AtomicFieldUpdaterOffset,
        /**
         * The field offset stored in this int or long field is updated. The original value must be
         * a valid field offset in the hosted universe, and the new value is the field offset of the
         * same field in the substrate universe. The field is looked up in the class
         * {@link #declClass}.
         */
        TranslateFieldOffset,
        /**
         * Marker value that the field is intercepted by some manual logic.
         */
        Manual,
        /**
         * Use a {@link CustomFieldValueComputer} or {@link CustomFieldValueTransformer}, which is
         * specified as the target class.
         */
        Custom,
    }

    enum ValueAvailability {
        BeforeAnalysis,
        AfterAnalysis,
        AfterCompilation
    }

    interface CustomFieldValueProvider {

        /**
         * When is the value for this custom computation available? By default, it is assumed that
         * the value is available {@link ValueAvailability#BeforeAnalysis before analysis}, i.e., it
         * doesn't depend on analysis results.
         */
        ValueAvailability valueAvailability();

        /** Return true if value is available during analysis, false otherwise. */
        default boolean isAvailableBeforeAnalysis() {
            return valueAvailability() == ValueAvailability.BeforeAnalysis;
        }

        /**
         * Return true if value is available only after analysis, i.e., it depends on data computed
         * by the analysis (such as field offsets), false otherwise.
         */
        default boolean isAvailableAfterAnalysis() {
            return valueAvailability() == ValueAvailability.AfterAnalysis;
        }

        /**
         * Return true if value is available only after compilation, i.e., it depends on data
         * computed during compilation, false otherwise.
         */
        default boolean isAvailableAfterCompilation() {
            return valueAvailability() == ValueAvailability.AfterCompilation;
        }
    }

    /**
     * Custom recomputation of field values. A class implementing this interface must have a
     * no-argument constructor, which is used to instantiate it before invoking {@link #compute}.
     */
    interface CustomFieldValueComputer extends CustomFieldValueProvider {
        /**
         * Computes the new field value. This method can already be invoked during the analysis,
         * especially when it computes the value of an object field that needs to be visited.
         *
         * @param metaAccess The {@code AnalysisMetaAccess} instance during the analysis or
         *            {@code HostedMetaAccess} instance after the analysis.
         * @param original The original field (if {@link RecomputeFieldValue} is used for an
         *            {@link Alias} field).
         * @param annotated The field annotated with {@link RecomputeFieldValue}.
         * @param receiver The original object for instance fields, or {@code null} for static
         *            fields.
         * @return The new field value.
         */
        Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver);
    }

    /**
     * Custom recomputation of field values. A class implementing this interface must have a
     * no-argument constructor, which is used to instantiate it before invoking {@link #transform}.
     *
     * In contrast to {@link CustomFieldValueComputer}, the {@link #transform} method also has the
     * original field value as a parameter. This is convenient if the new value depends on the
     * original value, but also requires the original field to be present, e.g., it cannot be use
     * for {@link Inject injected fields}.
     */
    interface CustomFieldValueTransformer extends CustomFieldValueProvider {
        /**
         * Computes the new field value. This method can already be invoked during the analysis,
         * especially when it computes the value of an object field that needs to be visited.
         *
         * @param metaAccess The {@code AnalysisMetaAccess} instance during the analysis or
         *            {@code HostedMetaAccess} instance after the analysis.
         * @param original The original field.
         * @param annotated The field annotated with {@link RecomputeFieldValue}.
         * @param receiver The original object for instance fields, or {@code null} for static
         *            fields.
         * @return The new field value.
         */
        Object transform(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver, Object originalValue);
    }

    /**
     * Reset an array field to a new empty array of the same type and length.
     */
    final class NewEmptyArrayTransformer implements CustomFieldValueTransformer {
        @Override
        public ValueAvailability valueAvailability() {
            return ValueAvailability.BeforeAnalysis;
        }

        @Override
        public Object transform(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver, Object originalValue) {
            if (originalValue == null) {
                return null;
            } else {
                int originalLength = Array.getLength(originalValue);
                return Array.newInstance(originalValue.getClass().getComponentType(), originalLength);
            }
        }
    }

    /**
     * The kind of the recomputation performed.
     */
    Kind kind();

    /**
     * The class parameter for the recomputation. If this attribute is not specified, then the class
     * specified by {@link #declClassName()} is used. If neither {@link #declClass()} nor
     * {@link #declClassName()} is specified, then the class specified by the {@link TargetClass}
     * annotation is used.
     */
    Class<?> declClass() default RecomputeFieldValue.class;

    /**
     * The class parameter for the recomputation. If this attribute is not specified, then the class
     * specified by {@link #declClass()} is used. If neither {@link #declClass()} nor
     * {@link #declClassName()} is specified, then the class specified by the {@link TargetClass}
     * annotation is used.
     */
    String declClassName() default "";

    /**
     * The name parameter for the recomputation.
     */
    String name() default "";

    /**
     * Treat the value as final, to enforce constant folding already during static analysis.
     */
    boolean isFinal() default false;

    /**
     * If true, ignores previously computed values and calculates the value for every field read.
     */
    boolean disableCaching() default false;
}
