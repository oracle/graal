/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a class to be a target for substitution annotation processing.
 * <p>
 * By default, a class whose name is {@code Target_a_b_c} will create substitutions for the guest
 * class {@code a/b/c}.
 * <p>
 * Changing {@link #value()} will result in a substitution for the class targetted by the new value.
 * <p>
 * When it is not possible to represent the class name of a guest class in the form of
 * {@code Target_a_b_c} (for example, when it is an inner class, with the symbol {@code $}, or if it
 * cannot be used in {@link #value()} (for example if the class is a private jdk class) one can use
 * the {@link #type()} method to provide the internal name of the class (in the form
 * {@code "La/b/c;"}.
 * <p>
 * Finally, if a single substitution can target multiple class names (/ex:
 * {@code sun.misc.Unsafe and jdk.internal.misc.Unsafe}, all the names can be provided through a
 * {@link #nameProvider()}. The class of the given provider must declare a
 * {@code public static final SubstitutionNameProvide INSTANCE} field.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface EspressoSubstitutions {
    /**
     * The class to substitute. If it is not accessible (eg: private jdk class), use either
     * {@link #type()} or {@link #nameProvider()}.
     */
    Class<?> value() default EspressoSubstitutions.class;

    /**
     * Refers to an internal type name (Of the {@code La/b/C;} form.
     * <p>
     * If specified, {@link #value()} will be ignored.
     * 
     */
    String type() default "";

    /**
     * Points to a {@link SubstitutionNamesProvider}.
     * <p>
     * If specified, both {@link #value()} and {@link #type()} will be ignored.
     */
    Class<? extends SubstitutionNamesProvider> nameProvider() default SubstitutionNamesProvider.NoProvider.class;

    /**
     * The target class for the generated {@link Collect} annotation.
     */
    Class<?> group() default Substitution.class;
}
