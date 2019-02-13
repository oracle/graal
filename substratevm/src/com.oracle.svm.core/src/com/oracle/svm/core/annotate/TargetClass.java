/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A class annotated with this annotation denotes a class that modifies methods of fields of another
 * the class, called the "original" class. The original class is specified using the properties:
 * {@link #value} or {@link #className} specify the original class either as a class literal or by
 * name. Optionally, inner classes can be specified using the {@link #innerClass} property.
 *
 * Distinguished using additional annotations, the original class is modified in different ways:
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
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TargetClass {

    /**
     * Specifies the substitutee class.
     *
     * If the default value is specified for this element, then a non-default value must be given
     * for the {@link #className()} or {@link #classNameProvider()} element.
     */
    Class<?> value() default TargetClass.class;

    /**
     * Specifies the substitutee class. This method is provided for cases where the substitutee
     * class is not accessible (according to Java language access control rules).
     *
     * If the default value is specified for this element, then a non-default value must be given
     * for the {@link #value()} or {@link #classNameProvider()} element.
     */
    String className() default "";

    /**
     * Specifies the substitutee class. This is the most flexible version to provide the class name.
     * The {@link Function#apply} method of the provided class can compute the class name based on
     * system properties (like the JDK version). This annotation is the argument of the function, so
     * the function can, e.g., build a class name that incorporates the {@link #className()}
     * property.
     *
     * If the default value is specified for this element, then a non-default value must be given
     * for the {@link #value()} or {@link #className()} element.
     */
    Class<? extends Function<TargetClass, String>> classNameProvider() default NoClassNameProvider.class;

    /**
     * Specifies the suffix of the substitutee class name when it is an inner class.
     */
    String[] innerClass() default {};

    /**
     * Substitute only if all provided predicates are true (default: unconditional substitution that
     * is always included).
     *
     * The classes must either implement {@link BooleanSupplier} or {@link Predicate}&lt;String&gt;
     * (the parameter for {@link Predicate#test} is the "original" class name as a {@link String}).
     */
    Class<?>[] onlyWith() default TargetClass.AlwaysIncluded.class;

    /** The default value for the {@link TargetClass#onlyWith()} attribute. */
    class AlwaysIncluded implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return true;
        }
    }

    /** Marker value for {@link #classNameProvider} that no class name provider should be used. */
    interface NoClassNameProvider extends Function<TargetClass, String> {
    }
}
