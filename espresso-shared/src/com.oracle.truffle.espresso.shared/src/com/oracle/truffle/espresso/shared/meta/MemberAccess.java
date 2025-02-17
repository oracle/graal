/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.meta;

import java.util.function.Function;

/**
 * Represents a {@link java.lang.reflect.Member}, and provides access to various runtime
 * capabilities such as {@link #accessChecks(TypeAccess, TypeAccess) access control} and
 * {@link #loadingConstraints(TypeAccess, Function) enforcing loading constraints}.
 *
 * @param <C> The class providing access to the VM-side java {@link Class}.
 * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
 * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
 */
public interface MemberAccess<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> extends ModifiersProvider, Named {
    /**
     * @return The class in which the declaration if this member is present.
     */
    C getDeclaringClass();

    /**
     * Performs access checks for this member.
     *
     * @param accessingClass The class from which resolution is being performed.
     * @param holderClass The class referenced in the symbolic representation of the method, as seen
     *            in the constant pool. May be different from {@link #getDeclaringClass()}.
     * @return whether the access check succeeded or not.
     */
    boolean accessChecks(C accessingClass, C holderClass);

    /**
     * Performs loading constraints checks for this member.
     *
     * @param accessingClass The class from which resolution is being performed.
     * @param errorHandler The function that should be used to throw potential errors.
     */
    void loadingConstraints(C accessingClass, Function<String, RuntimeException> errorHandler);
}
