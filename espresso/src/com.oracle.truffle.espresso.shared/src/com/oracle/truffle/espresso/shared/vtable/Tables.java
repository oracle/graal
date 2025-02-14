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
    private final List<MethodWrapper<C, M, F>> mirandas;

    public Tables(List<PartialMethod<C, M, F>> vtable, EconomicMap<C, List<PartialMethod<C, M, F>>> itables, List<MethodWrapper<C, M, F>> mirandas) {
        this.vtable = vtable;
        this.itables = itables;
        this.mirandas = mirandas;
    }

    /**
     * The virtual table associated with the type used for the call to
     * {@link VTable#create(PartialType, boolean, boolean)}.
     * <p>
     * For a given type {@code type}, a method {@code m} appearing at index {@code i} means that
     * {@code m} is the result of method selection (according to JVMS-5.4.6) with respect to
     * {@code type}, and any method appearing in slot {@code i} of all the vtables of the
     * superclasses of {@code type}.
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
     * If {@code m} is {@code null}, this means that more than one maximally-specific non-abstract
     * methods existed for the resolution of that table slot. The runtime must make sure to handle
     * that case accordingly (likely by throwing {@link IncompatibleClassChangeError} at the
     * call-site, according to JVMS-6.5.invokeinterface).
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
     * if {@link MethodWrapper#isSelectionFailure()}, this means that more than one
     * maximally-specific non-abstract methods existed for the resolution of that implicit interface
     * method.
     */
    public List<MethodWrapper<C, M, F>> getImplicitInterfaceMethods() {
        return mirandas;
    }

    public record MethodWrapper<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>>(M method, boolean isSelectionFailure) {
    }
}
