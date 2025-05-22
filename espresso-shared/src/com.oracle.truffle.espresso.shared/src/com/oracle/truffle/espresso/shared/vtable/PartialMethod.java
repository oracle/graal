/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
     * When the {@link VTable} builder adds a method to the vtable, this method will be called to
     * provide a hint to the runtime that the given {@code index} should be used as a vtable index
     * for that method.
     * <p>
     * It may be called on interface methods, if the implicit interface methods are added to the
     * virtual tables. The runtime decides how to interpret that hint.
     * <p>
     * When the {@link VTable} builder finds an {@code equivalent slot} for this method in the
     * parent's table, this method will be used to make that information known to the runtime.
     * <p>
     * An {@code equivalent slot} at index {@code i} for a method {@code m} is defined as follows:
     * <ul>
     * <li>There exists a method {@code m'} in the parent's table at index {@code i}.</li>
     * <li>Methods {@code m} and {@code m'} have the {@code same access control constraints}</li>
     * </ul>
     * <p>
     * Two methods are said to have the {@code same access control constraints} if either:
     * <ul>
     * <li>Both methods are either {@link ModifiersProvider#isPublic() public} or
     * {@link ModifiersProvider#isProtected() protected}.</li>
     * <li>Both methods are package-private, and are declared in the
     * {@link PartialType#sameRuntimePackage(TypeAccess) same runtime package}.</li>
     * </ul>
     * <p>
     * In practice, this means that any method that would override one of the methods will also
     * override the other. As such, the declared method can be assigned the same
     * {@code vtable index} as the method it is overriding.
     * <p>
     * Note: The {@link VTable} builder ensures that this method is only called once per declared
     * method, but it may be called multiple times on interface methods.
     */
    PartialMethod<C, M, F> withVTableIndex(int index);

    /**
     * Whether this {@link PartialMethod method} is a failing entry in a {@link Tables method
     * table}. Any call-site that selects this method should throw
     * {@link IncompatibleClassChangeError}.
     * <p>
     * This method should not be overridden, except by {@link FailingPartialMethod}.
     */
    default boolean isSelectionFailure() {
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
