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

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.ModifiersProvider;
import com.oracle.truffle.espresso.shared.meta.Named;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * A representation of a type in the process of being created, providing access to the data
 * necessary for building method tables. There should be no {@code null} entries in any of the
 * {@link List} or {@link EconomicMap} returned by the methods of this interface.
 *
 * @param <C> The class providing access to the VM-side java {@link Class}.
 * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
 * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
 */
public interface PartialType<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> extends Named {
    /**
     * The vtable of the declared superclass of this type, as would be constructed by a previous
     * call to {@link VTable#create(PartialType, boolean, boolean, boolean)}.
     * <p>
     * If this type does not have a superclass, this method should return an empty list, and not
     * {@code null}.
     */
    List<M> getParentTable();

    /**
     * A mapping from all interfaces (not just the declared ones!) this type implements to their
     * corresponding {@code itable}.
     * <p>
     * Though not enforced, an interface's {@code itable} should not contain any
     * {@link ModifiersProvider#isStatic() static} or {@link ModifiersProvider#isPrivate() private}
     * method.
     */
    EconomicMap<C, List<M>> getInterfacesData();

    /**
     * The list of methods this type declares.
     */
    List<? extends PartialMethod<C, M, F>> getDeclaredMethodsList();

    /**
     * Whether {@link PartialType this type} will share the same runtime package as the given
     * {@link TypeAccess otherType}.
     */
    boolean sameRuntimePackage(C otherType);

    /**
     * If the runtime allows selecting private methods for interface invokes (For java versions <=
     * 8), this method should be implemented.
     * <p>
     * It can be left as unimplemented for runtimes that do not support this behavior.
     * <p>
     * <p>
     * This method searches for a declaration of an instance method with the given {@code name} and
     * {@code signature} starting with this type, and continuing with the direct superclass of that
     * class, and so forth, until a method is found or no further superclasses exist.
     * <p>
     * If a method is found, it is the selected method. This method may be
     * {@link ModifiersProvider#isPrivate() private}.
     */
    default PartialMethod<C, M, F> lookupOverrideWithPrivate(@SuppressWarnings("unused") Symbol<Name> name, @SuppressWarnings("unused") Symbol<Signature> signature) {
        return null;
    }
}
