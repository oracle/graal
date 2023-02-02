/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodPredicate;

/**
 * Marks a substitution that should be bytecode-inlined. If this annotation is specified for the
 * substitution class, then it is applied to every substitution for that class.
 * <p>
 * Does nothing if specified for something that is neither marked as {@link EspressoSubstitutions}
 * or {@link Substitution}.
 * <p>
 * Substitution that are marked as {@link Substitution#isTrivial()} are considered to have an
 * implicit {@link InlineInBytecode} annotation.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface InlineInBytecode {
    /**
     * Specifies a
     * {@link com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodPredicate method
     * guard} for this inlined substitution.
     * <p>
     * <ul>
     * <li>If this returns {@link InlinedMethodPredicate}, no guard is needed.</li>
     * <li>Else, the class returned by this value should have a public static final field named
     * {@code INSTANCE} which returns an instance of this class..</li>
     * </ul>
     */
    Class<? extends InlinedMethodPredicate> guard() default InlinedMethodPredicate.class;
}
