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
     * @return Whether this method appears in a method table.
     */
    default boolean isVirtualEntry() {
        return !isPrivate() && !isStatic() && !isConstructor() && !isClassInitializer();
    }

    /**
     * Returns whether the current considered {@link PartialMethod method} overrides the method
     * {@code parentMethod}.
     * <p>
     * This check should be done in accordance to the first two cases of the definition of method
     * overriding of the JVM Specification (jvms-5.4.5).
     * <p>
     * In practice, this method simply needs to check whether the declaring class of {@code this}
     * can access {@code parentMethod}.
     * <p>
     * In particular, implementations does not need to check whether there exists a method
     * in-between the two methods considered, for which {@code this} can override.
     * <p>
     * The callers must ensure both {@code this} method and {@code parentMethod}:
     * <ul>
     * <li>{@link #isVirtualEntry() Appears in method tables}</li>
     * <li>Have the same {@link #getSymbolicName() name} and {@link #getSymbolicSignature()
     * signature}.</li>
     * </ul>
     *
     * @param declaredType The {@link PartialType} provided to the virtual table builder. Should
     *            represent the declaring type of this {@link PartialMethod}.
     *
     * @implNote the {@link ModifiersProvider#isFinalFlagSet() final flag} is irrelevant to the
     *           definition, and will be checked separately to appropriately throw
     *           {@link MethodTableException} with the
     *           {@link MethodTableException.Kind#IllegalClassChangeError} kind set.
     */
    default boolean canOverride(PartialType<C, M, F> declaredType, M parentMethod) {
        assert isVirtualEntry() && parentMethod.isVirtualEntry();
        assert getSymbolicName() == parentMethod.getSymbolicName() && getSymbolicSignature() == parentMethod.getSymbolicSignature();
        if (parentMethod.isPublic() || parentMethod.isProtected()) {
            return true;
        }
        return declaredType.sameRuntimePackage(parentMethod.getDeclaringClass());
    }

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
     * <li>Both methods are either {@link ModifiersProvider#isPublic() public} or
     * {@link ModifiersProvider#isProtected()} protected}</li>
     * <li>Both methods are {@link ModifiersProvider#isPackagePrivate()} package-private} and the
     * declaring classes of both method are in the same runtime package.</li>
     * </ul>
     * And returns {@code false} otherwise.
     * <p>
     * Note that the callers must ensure both methods are not private.
     *
     * @param declaredType The {@link PartialType} provided to the virtual table builder. Should
     *            represent the declaring type of this {@link PartialMethod}.
     */
    default boolean sameOverrideAccess(PartialType<C, M, F> declaredType, M parentMethod) {
        assert isVirtualEntry() && parentMethod.isVirtualEntry();
        assert getSymbolicName() == parentMethod.getSymbolicName() && getSymbolicSignature() == parentMethod.getSymbolicSignature();
        if (isPublic() || isProtected()) {
            return parentMethod.isPublic() || parentMethod.isProtected();
        }
        assert isPackagePrivate();
        return parentMethod.isPackagePrivate() && declaredType.sameRuntimePackage(parentMethod.getDeclaringClass());
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
