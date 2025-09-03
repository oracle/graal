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

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.shared.resolver.LinkResolver;

/**
 * Represents a {@link Class}, and provides access to various lookups and runtime metadata.
 *
 * @param <C> The class providing access to the VM-side java {@link Class}.
 * @param <M> The class providing access to the VM-side java {@link java.lang.reflect.Method}.
 * @param <F> The class providing access to the VM-side java {@link java.lang.reflect.Field}.
 */
public interface TypeAccess<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> extends ModifiersProvider, Named {
    /**
     * Returns the name of this class, as if obtained from {@link Class#getName()}.
     */
    String getJavaName();

    /**
     * Returns the symbolic reference of this class.
     */
    Symbol<Type> getSymbolicType();

    /**
     * Returns whether this class and the other class share the same defining class loader.
     */
    boolean hasSameDefiningClassLoader(C other);

    /**
     * Finds the least common ancestor between this class and the other class.
     */
    C findLeastCommonAncestor(C other);

    /**
     * Returns the superclass of this class, or {@code null} if this class is {@link Object}.
     */
    C getSuperClass();

    /**
     * Returns the host class of this VM-anonymous class, or {@code null} if this class is not a
     * VM-anonymous class.
     *
     * @apiNote A VM-anonymous class is a class defined through
     *          {@code Unsafe.defineAnonymousClass()} and is unrelated to the
     *          {@link Class#isAnonymousClass() Java concept of anonymous classes}.
     * @implNote The concept of VM-anonymous classes was removed from Java 17 onwards, and this
     *           method should therefore always return {@code null} for implementations of Java 17
     *           or later.
     */
    C getHostType();

    /**
     * Returns the name of the runtime package in which this class is defined.
     */
    Symbol<Name> getSymbolicRuntimePackage();

    /**
     * Performs field lookup on this class for the given field name and field type, according to
     * JVMS-5.4.3.2.
     * <p>
     * Field lookup is specified as follows, in order of operations:
     * <ul>
     * <li>If {@link TypeAccess C} declares a field with the name and type specified, field lookup
     * succeeds. The declared field is the result of the field lookup.</li>
     * <li>Otherwise, field lookup is applied recursively to the direct superinterfaces of the
     * specified class or interface {@link TypeAccess C}.</li>
     * <li>Otherwise, if {@link TypeAccess C} has a {@link #getSuperClass() superclass S}, field
     * lookup is applied recursively to {@code S}.</li>
     * <li>Otherwise, field lookup fails.</li>
     * </ul>
     */
    F lookupField(Symbol<Name> name, Symbol<Type> type);

    /**
     * Performs method lookup on this class for the given method name and method signature,
     * according to JVMS-5.4.3.3.
     * <p>
     * <ul>
     * <li>This lookup does not need to throw {@link ErrorType#IncompatibleClassChangeError} if this
     * class is an interface. It is handled in {@link LinkResolver#resolveMethodSymbol}</li>
     * <li>{@link MethodAccess Method} resolution attempts to locate the referenced method in
     * {@link TypeAccess C} and its superclasses:
     * <ul>
     * <li>If {@link TypeAccess C} declares exactly one {@link MethodAccess method} with the name
     * specified, and the declaration is a signature polymorphic method, then method lookup
     * succeeds. All the class names mentioned in the descriptor are resolved. The resolved method
     * is the signature polymorphic method declaration. It is not necessary for {@link TypeAccess C}
     * to declare a method with the signature specified by the method reference.</li>
     * <li>Otherwise, if {@link TypeAccess C} declares a method with the name and signature
     * specified by the method reference, method lookup succeeds.</li>
     * <li>Otherwise, if {@link TypeAccess C} has a {@link #getSuperClass() superclass}, step 2 of
     * method resolution is recursively invoked on the direct superclass of {@link TypeAccess
     * C}.</li>
     * </ul>
     * </li>
     * <li>Otherwise, method resolution attempts to locate the referenced method in the
     * superinterfaces of the specified class {@link TypeAccess C}:
     * <ul>
     * <li>If the maximally-specific superinterface methods of {@link TypeAccess C} for the name and
     * signature specified by the method reference include exactly one method that is not
     * {@link ModifiersProvider#isAbstract() abstract} , then this method is chosen and method
     * lookup succeeds.</li>
     * <li>Otherwise, if any superinterface of {@link TypeAccess C} declares a method with the name
     * and signature specified that is neither {@link ModifiersProvider#isPrivate() private} nor
     * {@link ModifiersProvider#isStatic() static}, one of these is arbitrarily chosen and method
     * lookup succeeds.</li>
     * <li>Otherwise, method lookup fails and returns {@code null}.</li></li>
     * </ul>
     * </ul>
     */
    M lookupMethod(Symbol<Name> name, Symbol<Signature> signature);

    /**
     * Same as {@link #lookupMethod(Symbol, Symbol)}, but ignores
     * {@link ModifiersProvider#isStatic() static} methods.
     */
    M lookupInstanceMethod(Symbol<Name> name, Symbol<Signature> signature);

    /**
     * Performs interface method lookup on this class for the given method name and method
     * signature, according to JVMS-5.4.3.3.
     * <p>
     * <ul>
     * <li>This lookup does not need to throw {@link ErrorType#IncompatibleClassChangeError} if this
     * class is not an interface. It is handled in {@link LinkResolver#resolveMethodSymbol}.</li>
     * <li>Otherwise, if {@link TypeAccess C} declares a method with the given name and signature,
     * this method is returned.</li>
     * <li>Otherwise, if the class {@link Object} declares a method with the given name and
     * signature, which is {@link ModifiersProvider#isPublic() public} and
     * non-{@link ModifiersProvider#isStatic() static}, this method is returned.</li>
     * <li>Otherwise, if the maximally-specific superinterface methods of {@link TypeAccess C} for
     * the given name and signature include exactly one method that is not
     * {@link ModifiersProvider#isAbstract() abstract}, then this method is returned.</li>
     * <li>Otherwise, if any superinterface of {@link TypeAccess C} declares a method with the name
     * and signature specified that is neither {@link ModifiersProvider#isPrivate() private} nor
     * {@link ModifiersProvider#isStatic() static}, one of these is arbitrarily chosen
     * returned.</li>
     * <li>Otherwise, method lookup fails and returns {@code null}.</li>
     * </ul>
     */
    M lookupInterfaceMethod(Symbol<Name> name, Symbol<Signature> signature);

    /**
     * Returns the {@link MethodAccess method} appearing in this type's virtual table at index
     * {@code vtableIndex}.
     * <p>
     * If {@code vtableIndex} is not within bounds of this type's virtual table length, this method
     * should return {@code null}.
     */
    M lookupVTableEntry(int vtableIndex);

    /**
     * @return {@code true} if {@code other} is a subtype of {@code this}, {@code false} otherwise.
     */
    boolean isAssignableFrom(C other);

    /**
     * @return {@code true} if this class represents the {@link Object} class, {@code false}
     *         otherwise.
     */
    default boolean isJavaLangObject() {
        return getSuperClass() == null;
    }

    /**
     * Whether this class extends the "magic accessor".
     */
    boolean isMagicAccessor();

    /**
     * The {@link ConstantPool} associated with this class.
     */
    ConstantPool getConstantPool();

    /**
     * Resolves a class in the runtime constant pool of this type, then returns it. Further calls to
     * this method with the same cpi should not trigger class loading. Resolution errors should not
     * be saved in the constant pool.
     *
     * @param cpi The constant pool index in which to find the class constant
     * @throws IllegalArgumentException If there is no
     *             {@link com.oracle.truffle.espresso.classfile.ConstantPool.Tag#CLASS} in the
     *             constant pool at index {@code cpi}.
     */
    C resolveClassConstantInPool(int cpi);
}
