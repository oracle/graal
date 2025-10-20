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
package com.oracle.truffle.espresso.shared.lookup;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;
import com.oracle.truffle.espresso.shared.resolver.CallKind;

/**
 * This class provides implementations of method lookups as defined by the Java Virtual Machine
 * Specifications.
 * <p>
 * Some of these methods may throw a checked {@link LookupSuccessInvocationFailure}. This indicates
 * that, while lookup succeeded, the result of that lookup is not applicable for a
 * {@link CallKind#DIRECT direct} invocation, and should fail with
 * {@link IncompatibleClassChangeError} if ever directly invoked.
 */
public final class MethodLookup {
    // region public interface

    /**
     * Lookup in the declared methods of {@code type} for a method with the specified {@code name}
     * and {@code signature}.
     */
    public static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M lookupDeclaredMethod(C type, Symbol<Name> name, Symbol<Signature> signature, LookupMode lookup) {
        return lookupDeclaredMethodImpl(type, name, signature, lookup);
    }

    /**
     * Lookup in the declared methods of {@code type} for a
     * {@link MethodAccess#isDeclaredSignaturePolymorphic() signature polymorphic declared method}
     * with the specified {@code name}.
     */
    public static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M lookupDeclaredSignaturePolymorphicMethod(C type, Symbol<Name> name, LookupMode lookup) {
        return lookupDeclaredSignaturePolymorphicMethodImpl(type, name, lookup);
    }

    /**
     * Performs {@code method lookup} on this class for the given method name and method signature,
     * according to JVMS-5.4.3.3.
     *
     * @see TypeAccess#lookupMethod(Symbol, Symbol)
     */
    public static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M lookupMethod(C type, Symbol<Name> name, Symbol<Signature> signature, LookupMode lookup)
                    throws LookupSuccessInvocationFailure {
        return lookupMethodImpl(type, name, signature, lookup);
    }

    /**
     * Performs {@code interface method lookup} on this class for the given method name and method
     * signature, according to JVMS-5.4.3.4.
     *
     * @see TypeAccess#lookupInterfaceMethod(Symbol, Symbol)
     */
    public static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M lookupInterfaceMethod(C type, Symbol<Name> name, Symbol<Signature> signature)
                    throws LookupSuccessInvocationFailure {
        return lookupInterfaceMethodImpl(type, name, signature);
    }

    /**
     * Returns the set {@code S} of {@code maximally-specific} methods from the given set.
     * <p>
     * This means that, for every method {@code C.m} in {@code S}, there is no method from the
     * original set {@code C'.m'} such that {@code C} is a super type of {@code C'}.
     */
    public static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> Set<M> resolveMaximallySpecific(Iterable<M> candidates) {
        Set<M> currentMaxs = new HashSet<>();
        for (M candidate : candidates) {
            if (isLocalMax(candidate, currentMaxs)) {
                currentMaxs.add(candidate);
            }
        }
        return currentMaxs;
    }

    // endregion public interface

    // region IMPL

    private MethodLookup() {
    }

    private static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M lookupDeclaredMethodImpl(C type, Symbol<Name> name, Symbol<Signature> signature, LookupMode lookup) {
        M polymorphic = lookupDeclaredSignaturePolymorphicMethodImpl(type, name, lookup);
        if (polymorphic != null) {
            return polymorphic.findSignaturePolymorphicIntrinsic(signature);
        }
        return lookupInList(type.getDeclaredMethodsList(), name, signature, lookup);
    }

    private static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M lookupInDeclaredAndImplicit(C type, Symbol<Name> name, Symbol<Signature> signature, LookupMode lookup) {
        // First, try to locate method in this type's declared methods.
        M declared = lookupDeclaredMethodImpl(type, name, signature, lookup);
        if (declared != null) {
            return declared;
        }

        // If the miranda methods are readily available, try to locate the method in it.
        List<M> mirandas = type.getImplicitInterfaceMethodsList();
        if (mirandas != null) {
            M inMirandas = lookupInList(mirandas, name, signature, lookup);
            if (inMirandas != null) {
                return inMirandas;
            }
        }
        return null;
    }

    private static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M lookupMethodImpl(C type, Symbol<Name> name, Symbol<Signature> signature, LookupMode lookup)
                    throws LookupSuccessInvocationFailure {
        M declared = lookupInDeclaredAndImplicit(type, name, signature, lookup);
        if (declared != null) {
            return declared;
        }

        // Delegate to super type.
        C current = type.getSuperClass();
        while (current != null) {
            M fromSuper = lookupInDeclaredAndImplicit(current, name, signature, lookup);
            if (fromSuper != null) {
                return fromSuper;
            }
            current = current.getSuperClass();
        }

        /*
         * Note: In the case where the implicit interface methods are available for the entire super
         * type hierarchy and the lookup is failing, this call is unnecessary.
         *
         * This should be fine as:
         *
         * - If in the hierarchy, even one type does not have its implicit interface methods
         * available, then this class becomes necessary.
         *
         * - Most lookups usually succeed, and will not reach here.
         */
        return lookupMethodInInterfaces(type, name, signature);
    }

    private static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M lookupDeclaredSignaturePolymorphicMethodImpl(C type, Symbol<Name> name, LookupMode lookup) {
        if (!ParserKlass.isSignaturePolymorphicHolderType(type.getSymbolicType())) {
            return null;
        }
        for (M m : type.getDeclaredMethodsList()) {
            if (lookup.include(m) && m.isDeclaredSignaturePolymorphic()) {
                if (name == m.getSymbolicName()) {
                    return m;
                }
            }
        }
        return null;
    }

    private static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M lookupInterfaceMethodImpl(C type, Symbol<Name> name, Symbol<Signature> signature)
                    throws LookupSuccessInvocationFailure {
        assert type.isInterface();
        // Simple case: This class declares the requested method
        M declaredMethod = lookupDeclaredMethod(type, name, signature, LookupMode.ALL);
        if (declaredMethod != null) {
            return declaredMethod;
        }

        // Look in the methods of java.lang.Object
        C objectType = type.getSuperClass();
        assert objectType.getSymbolicType() == ParserSymbols.ParserTypes.java_lang_Object : "An interface should always declare j.l.Object as its super class.";
        M objectMethod = lookupDeclaredMethod(objectType, name, signature, LookupMode.PUBLIC_NON_STATIC);
        if (objectMethod != null) {
            return objectMethod;
        }

        // If miranda methods are immediately available, do a linear search.
        List<M> implicitMethods = type.getImplicitInterfaceMethodsList();
        if (implicitMethods != null) {
            return lookupInList(implicitMethods, name, signature, LookupMode.ALL);
        } else {
            return lookupMethodInInterfaces(type, name, signature);
        }
    }

    private static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M lookupMethodInInterfaces(C type, Symbol<Name> name, Symbol<Signature> signature)
                    throws LookupSuccessInvocationFailure {
        // Do lookup in super interfaces
        Set<M> candidates = new HashSet<>();
        for (C superInterface : getTransitiveSuperInterfaces(type)) {
            M candidate = lookupDeclaredMethodImpl(superInterface, name, signature, LookupMode.NON_STATIC_NON_PRIVATE);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        // Search for the maximally-specific methods
        Set<M> maximallySpecific = resolveMaximallySpecific(candidates);

        if (maximallySpecific.size() == 1) {
            return maximallySpecific.iterator().next();
        }

        // Search for a unique non-abstract maximally-specific method.
        M uniqueNonAbstract = null;
        for (M candidate : maximallySpecific) {
            if (!candidate.isAbstract()) {
                if (uniqueNonAbstract != null) {
                    // Not unique: chose one arbitrarily, and notify that direct invocation should
                    // fail.
                    throw new LookupSuccessInvocationFailure(candidate);
                }
                uniqueNonAbstract = candidate;
            }
        }
        if (uniqueNonAbstract != null) {
            return uniqueNonAbstract;
        }
        // Only abstract maximally-specific methods. Choose one arbitrarily.
        M result = maximallySpecific.iterator().next();
        assert result.isAbstract();
        return result;
    }

    /**
     * Compares the declaring classes of both given methods, and returns the method whose declaring
     * class is a sub-type of the other's, or {@code null} if the declaring classes are from
     * unrelated hierarchies.
     */
    private static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> M mostSpecific(M m1, M m2) {
        if (m2.getDeclaringClass().isAssignableFrom(m1.getDeclaringClass())) {
            return m1;
        }
        if (m1.getDeclaringClass().isAssignableFrom(m2.getDeclaringClass())) {
            return m2;
        }
        return null;
    }

    /**
     * This method compares the given {@code candidate} method with each method in the
     * {@code currentMaxs} set to determine if it is a local maximum with respect to the set of
     * current local maximums ({@code currentMaxs}):
     * <ul>
     * <li>If it is more specific than a method in {@code currentMaxs}: that method is no longer
     * considered a local maximum and is removed from the set.</li>
     * <li>If a method in {@code currentMaxs} is more specific than it: We can be certain the
     * {@code candidate} will not be a local maximum, and we can early return {@code false}</li>
     * <li>Otherwise, it is a local maximum, and this method returns {@code true}</li>
     * </ul>
     */
    private static <C extends TypeAccess<C, M, ?>, M extends MethodAccess<C, M, ?>> boolean isLocalMax(M candidate, Set<M> currentMaxs) {
        Iterator<M> maxIterator = currentMaxs.iterator();
        while (maxIterator.hasNext()) {
            M currentMax = maxIterator.next();
            M mostSpecific = mostSpecific(candidate, currentMax);
            if (mostSpecific == candidate) {
                /* candidate's is a subtype of currentMax's: it is more specific. */
                maxIterator.remove();
            } else if (mostSpecific == currentMax) {
                /*
                 * candidate's is a super-type of currentMax's: it is less specific and can be
                 * discarded.
                 */
                return false;
            } else {

                /*
                 * If candidate and currentMax are unrelated, there is nothing to do but compare the
                 * candidate to the next current maximally-specific method.
                 */
                assert mostSpecific == null;
            }
        }
        /*
         * No method in the currentMaxs was more specific: Our candidate is therefore a new
         * maximally-specific method (wrt the current position in the candidates set).
         */
        return true;
    }

    private static <C extends TypeAccess<C, ?, ?>> Set<C> getTransitiveSuperInterfaces(C type) {
        HashSet<C> result = new HashSet<>();
        C current = type;
        while (current != null) {
            for (C interfaceClass : current.getSuperInterfacesList()) {
                collectInterfaces(interfaceClass, result);
            }
            current = current.getSuperClass();
        }
        return result;
    }

    private static <C extends TypeAccess<C, ?, ?>> void collectInterfaces(C interfaceClass, HashSet<C> result) {
        if (result.add(interfaceClass)) {
            for (C superInterface : interfaceClass.getSuperInterfacesList()) {
                collectInterfaces(superInterface, result);
            }
        }
    }

    private static <M extends MethodAccess<?, M, ?>> M lookupInList(List<M> methods, Symbol<Name> name, Symbol<Signature> signature, LookupMode lookup) {
        for (M m : methods) {
            if (lookup.include(m)) {
                if (name == m.getSymbolicName() && signature == m.getSymbolicSignature()) {
                    return m;
                }
            }
        }
        return null;
    }
    // endregion IMPL
}
