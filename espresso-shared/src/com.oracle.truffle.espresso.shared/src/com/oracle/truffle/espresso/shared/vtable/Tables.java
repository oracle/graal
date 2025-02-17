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

import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.ModifiersProvider;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * Contains the result of method table creation from
 * {@link VTable#create(PartialType, boolean, boolean, boolean)}.
 *
 * @param <C> The class providing access to the VM-side java {@link java.lang.Class}.
 * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
 * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
 */
public final class Tables<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
    private final List<PartialMethod<C, M, F>> vtable;
    private final EconomicMap<C, List<PartialMethod<C, M, F>>> itables;
    private final List<PartialMethod<C, M, F>> mirandas;

    Tables(List<PartialMethod<C, M, F>> vtable,
                    EconomicMap<C, List<PartialMethod<C, M, F>>> itables,
                    List<PartialMethod<C, M, F>> mirandas) {
        this.vtable = vtable;
        this.itables = itables;
        this.mirandas = mirandas;
    }

    /**
     * The virtual table associated with the type used for the call to
     * {@link VTable#create(PartialType, boolean, boolean, boolean)}.
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
     */
    public List<PartialMethod<C, M, F>> getVtable() {
        return vtable;
    }

    /**
     * The interface tables associated with the type used for the call to
     * {@link VTable#create(PartialType, boolean, boolean, boolean)}.
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
     * in the type associated with the call to
     * {@link VTable#create(PartialType, boolean, boolean, boolean)} or its superclasses.
     * <p>
     * Such methods are also sometimes referred to as {@code miranda methods}.
     * <p>
     * These are not eagerly added to the {@link #getVtable() vtable}, such that the runtime can
     * decide to add them or not.
     * <p>
     * Each entry in this list is either:
     * <ul>
     * <li>A default interface method (ie: an interface method that is
     * non-{@link ModifiersProvider#isAbstract() abstract}).</li>
     * <li>An {@link ModifiersProvider#isAbstract() abstract} method, in which case the runtime
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
}
