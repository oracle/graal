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

import com.oracle.graal.api.meta.*;

/**
 * Denotes a class that substitutes methods of another specified class.
 * The substitute methods are exactly those annotated by {@link MethodSubstitution}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClassSubstitution {

    /**
     * Specifies the original class.
     * <p>
     * If the default value is specified for this element, then a non-default
     * value must be given for the {@link #className()} element.
      */
    Class<?> value() default ClassSubstitution.class;

    /**
     * Specifies the original class.
     * <p>
     * This method is provided for cases where the original class
     * is not accessible (according to Java language access control rules).
     * <p>
     * If the default value is specified for this element, then a non-default
     * value must be given for the {@link #value()} element.
     */
    String className() default "";

    /**
     * Denotes a substitute method. A substitute method can call the original/substituted
     * method by making a recursive call to itself.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface MethodSubstitution {
        /**
         * Gets the name of the original method.
         * <p>
         * If the default value is specified for this element, then the
         * name of the original method is same as the substitute method.
         */
        String value() default "";

        /**
         * Determines if the original method is static.
         */
        boolean isStatic() default true;

        /**
         * Gets the {@linkplain Signature#getMethodDescriptor() signature} of the original method.
         * <p>
         * If the default value is specified for this element, then the
         * signature of the original method is the same as the substitute method.
         */
        String signature() default "";
    }
}
