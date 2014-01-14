/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.replacements;

import java.lang.annotation.*;

import com.oracle.graal.api.meta.*;

/**
 * Denotes a substitute method. A substitute method can call the original/substituted method by
 * making a recursive call to itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodSubstitution {

    /**
     * Gets the name of the original method.
     * <p>
     * If the default value is specified for this element, then the name of the original method is
     * same as the substitute method.
     */
    String value() default "";

    /**
     * Determines if the original method is static.
     */
    boolean isStatic() default true;

    /**
     * Gets the {@linkplain MetaUtil#signatureToMethodDescriptor signature} of the original method.
     * <p>
     * If the default value is specified for this element, then the signature of the original method
     * is the same as the substitute method.
     */
    String signature() default "";

    /**
     * Determines if this method should be substituted in all cases, even if inlining thinks it is
     * not important.
     * 
     * Note that this is still depending on whether inlining sees the correct call target, so it's
     * only a hard guarantee for static and special invocations.
     */
    boolean forced() default false;

    /**
     * Determines if the substitution is for a method that may not be part of the runtime. For
     * example, a method introduced in a later JDK version. Substitutions for such methods are
     * omitted if the original method cannot be found.
     */
    boolean optional() default false;

    /**
     * Determines if the substitution is globally enabled.
     */

    Class<? extends SubstitutionGuard> guard() default SubstitutionGuard.class;
}
