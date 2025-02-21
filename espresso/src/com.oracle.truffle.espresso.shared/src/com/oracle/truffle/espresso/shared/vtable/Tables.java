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

import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.ModifiersProvider;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * Contains the result of method table creation from
 * {@link VTable#create(PartialType, boolean, boolean)}.
 *
 * @param <C> The class providing access to the VM-side java {@link java.lang.Class}.
 * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
 * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
 */
public final class Tables<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
    private final List<PartialMethod<C, M, F>> vtable;
    private final EconomicMap<C, List<PartialMethod<C, M, F>>> itables;
    private final List<PartialMethod<C, M, F>> mirandas;
    private final List<Integer> equivalentEntries;

    public Tables(List<PartialMethod<C, M, F>> vtable,
                    EconomicMap<C, List<PartialMethod<C, M, F>>> itables,
                    List<PartialMethod<C, M, F>> mirandas,
                    List<Integer> equivalentEntries) {
        this.vtable = vtable;
        this.itables = itables;
        this.mirandas = mirandas;
        this.equivalentEntries = equivalentEntries;
    }

    /**
     * The virtual table associated with the type used for the call to
     * {@link VTable#create(PartialType, boolean, boolean)}.
     * <p>
     * For a given type {@code type}, a method {@code m} appearing at index {@code i} means that
     * {@code m} is the result of method selection (according to JVMS-5.4.6) with respect to
     * {@code type}, and any method appearing in slot {@code i} of all the vtables of the
     * superclasses of {@code type}.
     * <p>
     * It may happen that {@link PartialMethod#isSelectionFailure() m.isSelectionFailure()} returns
     * {@code true} if the runtime adds the implicit interface methods to the vtable:
     * <p>
     * <ul>
     * <li>One of this type's supertype may have added an interface method that fails
     * selection.</li>
     * <li>One of this type's supertype may have added an interface method that succeeds selection,
     * but this type adds a new interface that makes it fail.</li>
     * </ul>
     *
     */
    public List<PartialMethod<C, M, F>> getVtable() {
        return vtable;
    }

    /**
     * The interface tables associated with the type used for the call to
     * {@link VTable#create(PartialType, boolean, boolean)}.
     * <p>
     * For a given type {@code type}, A method {@code m} appearing at index {@code i} in the table
     * corresponding to interface {@code interface} means that {@code m} is the result of method
     * selection (according to JVMS-5.4.6) with respect to {@code type} and the method that appears
     * at index {@code i} in the method table of {@code interface}.
     * <p>
     * If {@code m} returns {@code true} for {@link PartialMethod#isSelectionFailure()}, this means
     * that more than one maximally-specific non-abstract methods existed for the resolution of that
     * table slot. The runtime must make sure to handle that case accordingly (likely by throwing
     * {@link IncompatibleClassChangeError} at the call-site, according to
     * JVMS-6.5.invokeinterface).
     * <p>
     * Node: When there are zero maximally-specific non-abstract methods for the resolution of that
     * slot, an arbitrary maximally-specific abstract method is used to populate the slot. This is
     * consistent with the requirement to throw {@link AbstractMethodError} in that case.
     */
    public EconomicMap<C, List<PartialMethod<C, M, F>>> getItables() {
        return itables;
    }

    /**
     * The list of methods that are declared in super interfaces, but do not have an implementation
     * in the type associated with the call to {@link VTable#create(PartialType, boolean, boolean)}
     * or its superclasses.
     * <p>
     * Such methods are also sometimes referred to as {@code miranda methods}.
     * <p>
     * The methods in this list are safe to use as-is for the interface table. each entry in this
     * list is either:
     * <ul>
     * <li>A default interface method (ie: an interface method that is
     * non-{@link ModifiersProvider#isAbstract() abstract}).</li>
     * <li>An {@link ModifiersProvider#isAbstract() abstract} methods, in which case the runtime
     * should throw {@link AbstractMethodError}.</li>
     * <li>A {@link PartialMethod method} for which {@link PartialMethod#isSelectionFailure()}
     * returns true, in which case the runtime should throw
     * {@link IncompatibleClassChangeError}.</li>
     * </ul>
     * <p>
     * Note that none of the methods in this list are either {@link ModifiersProvider#isPrivate()
     * private} or {@link ModifiersProvider#isStatic() static}.
     */
    public List<PartialMethod<C, M, F>> getImplicitInterfaceMethods() {
        return mirandas;
    }

    /**
     * A list of indexes in the VTable. Every method in the {@link #getVtable()} with indexes in
     * this list were not appended to the vtable. Instead, they should be using that index as their
     * {@code vtable index}.
     * <p>
     * More precisely, this list of indexes in the VTable is defined as follows:
     * <p>
     * For every index {@code i} in the list:
     * <ul>
     * <li>The {@link PartialMethod method} {@code m} at index {@code i} is a method found in the
     * {@link PartialType#getDeclaredMethodsList() declared method list} of the {@link PartialType
     * type} used for VTable creation.</li>
     * <li>{@code m} overrides the {@link MethodAccess method} {@code m'} found in the
     * {@link PartialType#getParentTable() super class VTable} at index {code i}</li>
     * <li>{@code m} and {@code m'} have the {@code same access control constraints}</li>
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
     * In practice, this means that any method that would override one of the method will also
     * override the other. As such, the declared method can be assigned the same
     * {@code vtable index} as the method it is overriding.
     */
    public List<Integer> getEquivalentEntries() {
        return equivalentEntries;
    }
}
