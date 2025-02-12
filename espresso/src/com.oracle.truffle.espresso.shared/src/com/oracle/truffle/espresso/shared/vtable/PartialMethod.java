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
     * @return Whether this method appears in a method table.
     */
    default boolean isVirtualEntry() {
        return !isPrivate() && !isStatic() && !isConstructor() && !isClassInitializer();
    }

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
     * Returns whether the current considered {@link PartialMethod method} overrides the method
     * {@code parentMethod} at vtable index {@code vtableIndex}.
     * <p>
     * Typically, this information can be obtained by checking if the declaring class of this method
     * has access to {@code parentMethod}, or any of the methods present in any one of its
     * superclasses' vtables at index {@code vtableIndex}.
     * <p>
     * this method and {@code parentMethod} are guaranteed to have the same
     * {@link #getSymbolicName()} and {@link #getSymbolicSignature()}.
     */
    boolean canOverride(M parentMethod, int vtableIndex);

    /**
     * Returns whether {@link PartialMethod this method} and the given {@code parentMethod} obey the
     * same access checks rules.
     * <p>
     * This method is used to determine whether it is necessary to allocate a new vtable slot for a
     * declared method that overrides a method from the super's vtable.
     * <p>
     * Implementing this method is not necessary if using
     * {@link VTable#create(PartialType, boolean, boolean)} with {@code verbose} set to
     * {@code true}.
     * <p>
     * This method should return {@code true} if:
     * <ul>
     * <li>Both methods are {@link ModifiersProvider#isPublic() public}</li>
     * <li>Both methods are {@link ModifiersProvider#isProtected()} protected}</li>
     * <li>Both methods are {@link ModifiersProvider#isPackagePrivate()} package-private} and the
     * declaring classes of both method are in the same runtime package.</li>
     * </ul>
     * And returns {@code false} otherwise.
     */
    default boolean sameAccess(@SuppressWarnings("unused") M parentMethod) {
        return false;
    }

    /**
     * This method is not used as part of the vtable creation process, and is provided simply for
     * simplifying the translation from {@link PartialMethod} to {@link MethodAccess} once the
     * tables have been obtained and the methods fully created.
     */
    default M asMethodAccess() {
        return null;
    }
}
