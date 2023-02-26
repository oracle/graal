/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * A class annotated with this annotation denotes a class that modifies methods of fields of another
 * class, called the "original" class. The original class is specified using annotation parameters:
 * {@link #value} or {@link #className} specify the original class either as a class literal or by
 * name, while {@link #classNameProvider} is the most flexible approach where the class name is
 * computed by user code. Optionally, inner classes can be specified using the {@link #innerClass}
 * property.
 *
 * Based on additional annotations, the original class is modified in different ways:
 * <ul>
 * <li>None of {@link Delete} or {@link Substitute}: the annotated class is an alias for the
 * original class. This is the most frequently used case. All methods and fields of the annotated
 * class must be annotated too:
 *
 * <ul>
 * <li>{@link Alias}: The annotated method or field is an alias for the original element, i.e., the
 * annotated element does not exist at run time. All usages of the annotated element reference the
 * original element. For fields, the original field can be further modified using the
 * {@link RecomputeFieldValue} and {@link InjectAccessors} annotations (see the documentation of the
 * annotations for details).</li>
 * <li>{@link Delete}: The annotated method or field, as well as the original element, do not exist.
 * Any usage of them will be reported as an error.</li>
 * <li>{@link Substitute}: The annotated method replaces the original method.</li>
 * <li>{@link AnnotateOriginal}: The original method remains mostly unchanged, but with one
 * exception: all annotations on the annotated method are also present on the original method.</li>
 * </ul>
 * </li>
 *
 * <li>{@link Delete}: The annotated class, as well as the original class, do not exist. Any usage
 * of them will be reported as an error.</li>
 *
 * <li>{@link Substitute}: The annotated class replaces the original class. All fields and methods
 * of the original class are per default treated as deleted, i.e., any usage of them will be
 * reported as an error, with the following exception: A method annotated with {@link Substitute}
 * replaces the original method, i.e., calls to the original method now call the annotated method; a
 * method annotated with {@link KeepOriginal} keeps the original method, i.e., calls to the original
 * method still call the original method.</li>
 * </ul>
 * 
 * @since 22.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Platforms(Platform.HOSTED_ONLY.class)
public @interface TargetClass {

    /**
     * Specifies the substitutee class using a class literal.
     *
     * Either {@link #value()}, {@link #className()} or {@link #classNameProvider()} element can be
     * used to specify the substitutee class.
     * 
     * @since 22.3
     */
    Class<?> value() default TargetClass.class;

    /**
     * Specifies the substitutee class using a class-name string. This method is provided for cases
     * where the substitutee class is not accessible (according to Java language access control
     * rules).
     *
     * Either {@link #value()}, {@link #className()} or {@link #classNameProvider()} element can be
     * used to specify the substitutee class.
     * 
     * @since 22.3
     */
    String className() default "";

    /**
     * Specifies the substitutee class. This is the most flexible version to provide the class name
     * to specify which class should be substituted. The {@link Function#apply} method of the
     * provided class can compute the class name based on system properties (like the JDK version).
     * This annotation is the argument of the function, so the function can, e.g., build a class
     * name that incorporates the {@link #className()} property.
     *
     * Either {@link #value()}, {@link #className()} or {@link #classNameProvider()} element can be
     * used to specify the substitutee class.
     * 
     * @since 22.3
     */
    Class<? extends Function<TargetClass, String>> classNameProvider() default NoClassNameProvider.class;

    /**
     * Specifies the suffix of the substitutee class name when it is an inner class.
     * 
     * @since 22.3
     */
    String[] innerClass() default {};

    /**
     * Substitute only if all provided predicates are true (default: unconditional substitution that
     * is always included).
     *
     * The classes must either implement {@link BooleanSupplier} or {@link Predicate}&lt;String&gt;
     * (the parameter for {@link Predicate#test} is the "original" class name as a {@link String}).
     * 
     * @since 22.3
     */
    Class<?>[] onlyWith() default TargetClass.AlwaysIncluded.class;

    /**
     * The default value for the {@link TargetClass#onlyWith()} attribute.
     * 
     * @since 22.3
     */
    class AlwaysIncluded implements BooleanSupplier {
        AlwaysIncluded() {
        }

        /**
         * @since 22.3
         */
        @Override
        public boolean getAsBoolean() {
            return true;
        }
    }

    /**
     * Marker value for {@link #classNameProvider} that no class name provider should be used.
     * 
     * @since 22.3
     */
    interface NoClassNameProvider extends Function<TargetClass, String> {
    }
}
