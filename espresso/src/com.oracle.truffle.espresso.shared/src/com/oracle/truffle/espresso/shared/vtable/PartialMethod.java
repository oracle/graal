/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.vtable;

import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.ModifiersProvider;
import com.oracle.truffle.espresso.shared.meta.Named;
import com.oracle.truffle.espresso.shared.meta.Signed;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * A representation of a method in the process of being created, providing access to the data
 * necessary for building method tables.
 *
 * @param <C> The class providing access to the VM-side java {@link Class}.
 * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
 * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
 */
public interface PartialMethod<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> extends Named, Signed, ModifiersProvider {
    /**
     * @return {@code true} if this method represents an instance initialization method (its
     *         {@link #getSymbolicName() name} is {@code "<init>"}), {@code false} otherwise.
     */
    boolean isConstructor();

    /**
     * @return {@code true} if this method represents a class initialization method (its
     *         {@link #getSymbolicName() name} is {@code "<clinit>"}, and it is {@link #isStatic()
     *         static}), {@code false} otherwise.
     */
    boolean isClassInitializer();

    /**
     * This method is not used as part of the vtable creation process, and is provided simply for
     * simplifying the translation from {@link PartialMethod} to {@link MethodAccess} once the
     * tables have been obtained and the methods fully created.
     */
    default M asMethodAccess() {
        return null;
    }
}
