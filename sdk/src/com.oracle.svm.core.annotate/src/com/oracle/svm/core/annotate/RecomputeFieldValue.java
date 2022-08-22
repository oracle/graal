/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.svm.core.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

/**
 * Supported API is available to replace this non-API annotation: Use
 * {@link BeforeAnalysisAccess#registerFieldValueTransformer}.
 *
 * Mechanism to change the value of a field. Normally, field values in the native image heap of the
 * Substrate VM are just taken from the host VM. This annotation allows the field value to be
 * intercepted and recomputed.
 * <p>
 * This annotation must be used on a field also annotated with {@link Alias} to specify the field
 * whose value needs to be changed.
 * 
 * @since 22.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Platforms(Platform.HOSTED_ONLY.class)
public @interface RecomputeFieldValue {

    /**
     * @since 22.3
     */
    enum Kind {
        /**
         * The initial field value is not modified. This is the default behavior when no
         * {@link RecomputeFieldValue} annotation is present.
         * 
         * @since 22.3
         */
        None,
        /**
         * The field is reset to the default value (null, 0, false).
         * 
         * @since 22.3
         */
        Reset,
        /**
         * The object field is set to a instance of {@link #declClass} created by calling the
         * default constructor.
         * 
         * @since 22.3
         */
        NewInstance,
        /**
         * The object field is set to a instance of {@link #declClass} created by calling the
         * default constructor when the target field value is not null.
         * 
         * @since 22.3
         */
        NewInstanceWhenNotNull,
        /**
         * The field is set to the value assigned to the {@link Alias} field.
         * 
         * @since 22.3
         */
        FromAlias,
        /**
         * The int or long field is set to the offset of the field named {@link #name()} of the
         * class {@link #declClass}, as it would be computed by
         * {@link sun.misc.Unsafe#objectFieldOffset}.
         * 
         * @since 22.3
         */
        FieldOffset,
        /**
         * The int or long field is set to the offset of the first array element of the array class
         * {@link #declClass}, as it would be computed by
         * {@link sun.misc.Unsafe#arrayBaseOffset(Class)}.
         * 
         * @since 22.3
         */
        ArrayBaseOffset,
        /**
         * The int or long field is set to the element size array class {@link #declClass}, as it
         * would be computed by {@link sun.misc.Unsafe#arrayIndexScale(Class)}.
         * 
         * @since 22.3
         */
        ArrayIndexScale,
        /**
         * The int or long field is set to the log2 of {@link #ArrayIndexScale}.
         * 
         * @since 22.3
         */
        ArrayIndexShift,
        /**
         * Special support for field offsets used by
         * java.util.concurrent.atomic.AtomicXxxFieldUpdater.
         * 
         * @since 22.3
         */
        AtomicFieldUpdaterOffset,
        /**
         * The field offset stored in this int or long field is updated. The original value must be
         * a valid field offset in the hosted universe, and the new value is the field offset of the
         * same field in the substrate universe. The field is looked up in the class
         * {@link #declClass}.
         * 
         * @since 22.3
         */
        TranslateFieldOffset,
        /**
         * Marker value that the field is intercepted by some manual logic.
         * 
         * @since 22.3
         */
        Manual,
        /**
         * Use a {@link FieldValueTransformer}, which is specified as the target class.
         * 
         * @since 22.3
         */
        Custom,
    }

    /**
     * The kind of the recomputation performed.
     * 
     * @since 22.3
     */
    Kind kind();

    /**
     * The class parameter for the recomputation. If this attribute is not specified, then the class
     * specified by {@link #declClassName()} is used. If neither {@link #declClass()} nor
     * {@link #declClassName()} is specified, then the class specified by the {@link TargetClass}
     * annotation is used.
     * 
     * @since 22.3
     */
    Class<?> declClass() default RecomputeFieldValue.class;

    /**
     * The class parameter for the recomputation. If this attribute is not specified, then the class
     * specified by {@link #declClass()} is used. If neither {@link #declClass()} nor
     * {@link #declClassName()} is specified, then the class specified by the {@link TargetClass}
     * annotation is used.
     * 
     * @since 22.3
     */
    String declClassName() default "";

    /**
     * The name parameter for the recomputation.
     * 
     * @since 22.3
     */
    String name() default "";

    /**
     * Treat the value as final, to enforce constant folding already during static analysis.
     * 
     * @since 22.3
     */
    boolean isFinal() default false;

    /**
     * If true, ignores previously computed values and calculates the value for every field read.
     * 
     * @since 22.3
     */
    boolean disableCaching() default false;
}
