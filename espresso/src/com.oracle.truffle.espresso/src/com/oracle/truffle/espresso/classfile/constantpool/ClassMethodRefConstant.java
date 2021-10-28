/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.constantpool;

import java.util.Objects;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Descriptor;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public interface ClassMethodRefConstant extends MethodRefConstant {

    static ClassMethodRefConstant create(int classIndex, int nameAndTypeIndex) {
        return new Indexes(classIndex, nameAndTypeIndex);
    }

    @Override
    default Tag tag() {
        return Tag.METHOD_REF;
    }

    final class Indexes extends MethodRefConstant.Indexes implements ClassMethodRefConstant, Resolvable {
        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }

        /**
         * <h3>5.4.3.3. Method Resolution</h3>
         *
         * To resolve an unresolved symbolic reference from D to a method in a class C, the symbolic
         * reference to C given by the method reference is first resolved (&sect;5.4.3.1).
         * Therefore, any exception that can be thrown as a result of failure of resolution of a
         * class reference can be thrown as a result of failure of method resolution. If the
         * reference to C can be successfully resolved, exceptions relating to the resolution of the
         * method reference itself can be thrown.
         *
         * When resolving a method reference:
         * <ol>
         *
         * <li>If C is an interface, method resolution throws an IncompatibleClassChangeError.
         *
         * <li>Otherwise, method resolution attempts to locate the referenced method in C and its
         * superclasses:
         * <ul>
         *
         * <li>If C declares exactly one method with the name specified by the method reference, and
         * the declaration is a signature polymorphic method (&sect;2.9), then method lookup
         * succeeds. All the class names mentioned in the descriptor are resolved (&sect;5.4.3.1).
         *
         * <li>The resolved method is the signature polymorphic method declaration. It is not
         * necessary for C to declare a method with the descriptor specified by the method
         * reference.
         *
         * <li>Otherwise, if C declares a method with the name and descriptor specified by the
         * method reference, method lookup succeeds.
         *
         * <li>Otherwise, if C has a superclass, step 2 of method resolution is recursively invoked
         * on the direct superclass of C.
         * </ul>
         *
         * <li>Otherwise, method resolution attempts to locate the referenced method in the
         * superinterfaces of the specified class C:
         * <ul>
         * <li>If the maximally-specific superinterface methods of C for the name and descriptor
         * specified by the method reference include exactly one method that does not have its
         * ACC_ABSTRACT flag set, then this method is chosen and method lookup succeeds.
         *
         * <li>Otherwise, if any superinterface of C declares a method with the name and descriptor
         * specified by the method reference that has neither its ACC_PRIVATE flag nor its
         * ACC_STATIC flag set, one of these is arbitrarily chosen and method lookup succeeds.
         *
         * <li>Otherwise, method lookup fails.
         * </ul>
         * </ol>
         *
         * A maximally-specific superinterface method of a class or interface C for a particular
         * method name and descriptor is any method for which all of the following are true:
         *
         * <ul>
         * <li>The method is declared in a superinterface (direct or indirect) of C.
         *
         * <li>The method is declared with the specified name and descriptor.
         *
         * <li>The method has neither its ACC_PRIVATE flag nor its ACC_STATIC flag set.
         *
         * <li>Where the method is declared in interface I, there exists no other maximally-specific
         * superinterface method of C with the specified name and descriptor that is declared in a
         * subinterface of I.
         * </ul>
         * The result of method resolution is determined by whether method lookup succeeds or fails:
         * <ul>
         * <li>If method lookup fails, method resolution throws a NoSuchMethodError.
         *
         * <li>Otherwise, if method lookup succeeds and the referenced method is not accessible
         * (&sect;5.4.4) to D, method resolution throws an IllegalAccessError.
         *
         * Otherwise, let < E, L1 > be the class or interface in which the referenced method m is
         * actually declared, and let L2 be the defining loader of D.
         *
         * Given that the return type of m is Tr, and that the formal parameter types of m are Tf1,
         * ..., Tfn, then:
         *
         * If Tr is not an array type, let T0 be Tr; otherwise, let T0 be the element type
         * (&sect;2.4) of Tr.
         *
         * For i = 1 to n: If Tfi is not an array type, let Ti be Tfi; otherwise, let Ti be the
         * element type (&sect;2.4) of Tfi.
         *
         * The Java Virtual Machine must impose the loading constraints TiL1 = TiL2 for i = 0 to n
         * (&sect;5.3.4).
         * </ul>
         * When resolution searches for a method in the class's superinterfaces, the best outcome is
         * to identify a maximally-specific non-abstract method. It is possible that this method
         * will be chosen by method selection, so it is desirable to add class loader constraints
         * for it.
         *
         * Otherwise, the result is nondeterministic. This is not new: The Java&reg; Virtual Machine
         * Specification has never identified exactly which method is chosen, and how "ties" should
         * be broken. Prior to Java SE 8, this was mostly an unobservable distinction. However,
         * beginning with Java SE 8, the set of interface methods is more heterogenous, so care must
         * be taken to avoid problems with nondeterministic behavior. Thus:
         *
         * <ul>
         * <li>Superinterface methods that are private and static are ignored by resolution. This is
         * consistent with the Java programming language, where such interface methods are not
         * inherited.
         *
         * <li>Any behavior controlled by the resolved method should not depend on whether the
         * method is abstract or not.
         * </ul>
         * Note that if the result of resolution is an abstract method, the referenced class C may
         * be non-abstract. Requiring C to be abstract would conflict with the nondeterministic
         * choice of superinterface methods. Instead, resolution assumes that the run time class of
         * the invoked object has a concrete implementation of the method.
         */

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            METHODREF_RESOLVE_COUNT.inc();

            EspressoContext context = pool.getContext();
            Klass holderKlass = getResolvedHolderKlass(accessingKlass, pool);

            Meta meta = context.getMeta();
            if (holderKlass.isInterface()) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, meta.toGuestString(getName(pool)));
            }

            Symbol<Name> name = getName(pool);
            Symbol<Signature> signature = getSignature(pool);

            Method method = holderKlass.lookupMethod(name, signature, accessingKlass);
            if (method == null) {
                throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError, meta.toGuestString(holderKlass.getNameAsString() + "." + getName(pool) + signature));
            }

            MemberRefConstant.doAccessCheck(accessingKlass, holderKlass, method, meta);

            if (!method.isPolySignatureIntrinsic()) {
                method.checkLoadingConstraints(accessingKlass.getDefiningClassLoader(), method.getDeclaringKlass().getDefiningClassLoader());
            }

            return new Resolved(method);
        }

        @Override
        public void validate(ConstantPool pool) {
            super.validate(pool);
            // If the name of the method of a CONSTANT_Methodref_info structure begins with a '<'
            // ('\u003c'), then the name must be the special name <init>, representing an instance
            // initialization method (&sect;2.9). The return type of such a method must be void.
            pool.nameAndTypeAt(nameAndTypeIndex).validateMethod(pool, false);
            Symbol<Name> name = pool.nameAndTypeAt(nameAndTypeIndex).getName(pool);
            if (Name._init_.equals(name)) {
                Symbol<? extends Descriptor> descriptor = pool.nameAndTypeAt(nameAndTypeIndex).getDescriptor(pool);
                int len = descriptor.length();
                if (len <= 2 || (descriptor.byteAt(len - 2) != ')' || descriptor.byteAt(len - 1) != 'V')) {
                    throw ConstantPool.classFormatError("<init> method should have ()V signature");
                }
            }
        }
    }

    final class Resolved implements InterfaceMethodRefConstant, Resolvable.ResolvedConstant {
        private final Method resolved;

        Resolved(Method resolved) {
            this.resolved = Objects.requireNonNull(resolved);
        }

        @Override
        public Method value() {
            return resolved;
        }

        @Override
        public Symbol<Name> getHolderKlassName(ConstantPool pool) {
            throw EspressoError.shouldNotReachHere("Method already resolved");
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return resolved.getName();
        }

        @Override
        public Symbol<? extends Descriptor> getDescriptor(ConstantPool pool) {
            return resolved.getRawSignature();
        }
    }
}
