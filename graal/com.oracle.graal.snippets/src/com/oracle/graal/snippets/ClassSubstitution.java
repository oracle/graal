/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.snippets;

import java.lang.annotation.*;

/**
 * Denotes a class that substitutes methods of another specified class with snippets.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClassSubstitution {

    /**
     * Specifies the substituted class.
     * <p>
     * If the default value is specified for this element, then a non-default
     * value must be given for the {@link #className()} element.
      */
    Class<?> value() default ClassSubstitution.class;

    /**
     * Specifies the substituted class.
     * <p>
     * This method is provided for cases where the substituted class
     * is not accessible (according to Java language access control rules).
     * <p>
     * If the default value is specified for this element, then a non-default
     * value must be given for the {@link #value()} element.
     */
    String className() default "";

    /**
     * Used to map a substitute method to an original method where the default mapping
     * of name and signature is not possible due to name clashes with final methods in
     * {@link Object} or signature types that are not public.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface MethodSubstitution {
        /**
         * Get the name of the original method.
         */
        String value() default "";

        /**
         * Determine if the substituted method is static.
         */
        boolean isStatic() default true;
    }
}
