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
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public interface InterfaceMethodRefConstant extends MethodRefConstant {

    static InterfaceMethodRefConstant create(int classIndex, int nameAndTypeIndex) {
        return new Indexes(classIndex, nameAndTypeIndex);
    }

    @Override
    default Tag tag() {
        return Tag.INTERFACE_METHOD_REF;
    }

    final class Indexes extends MethodRefConstant.Indexes implements InterfaceMethodRefConstant, Resolvable {
        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }

        /**
         * <h3>5.4.3.4. Interface Method Resolution</h3>
         *
         * To resolve an unresolved symbolic reference from D to an interface method in an interface
         * C, the symbolic reference to C given by the interface method reference is first resolved
         * (&sect;5.4.3.1). Therefore, any exception that can be thrown as a result of failure of
         * resolution of an interface reference can be thrown as a result of failure of interface
         * method resolution. If the reference to C can be successfully resolved, exceptions
         * relating to the resolution of the interface method reference itself can be thrown.
         *
         * When resolving an interface method reference:
         * <ol>
         * <li><b>If C is not an interface, interface method resolution throws an
         * IncompatibleClassChangeError.</b>
         * <li>Otherwise, if C declares a method with the name and descriptor specified by the
         * interface method reference, method lookup succeeds.
         * <li>Otherwise, if the class Object declares a method with the name and descriptor
         * specified by the interface method reference, which has its ACC_PUBLIC flag set and does
         * not have its ACC_STATIC flag set, method lookup succeeds.
         * <li>Otherwise, if the maximally-specific superinterface methods (&sect;5.4.3.3) of C for
         * the name and descriptor specified by the method reference include exactly one method that
         * does not have its ACC_ABSTRACT flag set, then this method is chosen and method lookup
         * succeeds.
         * <li>Otherwise, if any superinterface of C declares a method with the name and descriptor
         * specified by the method reference that has neither its ACC_PRIVATE flag nor its
         * ACC_STATIC flag set, one of these is arbitrarily chosen and method lookup succeeds.
         * <li>Otherwise, method lookup fails.
         * </ol>
         *
         * The result of interface method resolution is determined by whether method lookup succeeds
         * or fails:
         * <ul>
         * <li><b>If method lookup fails, interface method resolution throws a
         * NoSuchMethodError.</b>
         * <li><b>If method lookup succeeds and the referenced method is not accessible
         * (&sect;5.4.4) to D, interface method resolution throws an IllegalAccessError.</b>
         * <li>Otherwise, let < E, L1 > be the class or interface in which the referenced interface
         * method m is actually declared, and let L2 be the defining loader of D.
         * <li>Given that the return type of m is Tr, and that the formal parameter types of m are
         * Tf1, ..., Tfn, then:
         * <li>If Tr is not an array type, let T0 be Tr; otherwise, let T0 be the element type
         * (&sect;2.4) of Tr.
         * <li>For i = 1 to n: If Tfi is not an array type, let Ti be Tfi; otherwise, let Ti be the
         * element type (&sect;2.4) of Tfi.
         * <li>The Java Virtual Machine must impose the loading constraints TiL1 = TiL2 for i = 0 to
         * n (&sect;5.3.4).
         * </ul>
         * The clause about accessibility is necessary because interface method resolution may pick
         * a private method of interface C. (Prior to Java SE 8, the result of interface method
         * resolution could be a non-public method of class Object or a static method of class
         * Object; such results were not consistent with the inheritance model of the Java
         * programming language, and are disallowed in Java SE 8 and above.)
         */
        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, ObjectKlass accessingKlass) {
            METHODREF_RESOLVE_COUNT.inc();
            EspressoContext context = pool.getContext();
            Meta meta = context.getMeta();

            Klass holderInterface = getResolvedHolderKlass(accessingKlass, pool);

            Symbol<Name> name = getName(pool);

            // 1. If C is not an interface, interface method resolution throws an
            // IncompatibleClassChangeError.
            if (!holderInterface.isInterface()) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, meta.toGuestString(name));
            }

            Symbol<Signature> signature = getSignature(pool);

            Method method = ((ObjectKlass) holderInterface).resolveInterfaceMethod(name, signature);

            if (method == null) {
                throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError, meta.toGuestString(name));
            }

            MemberRefConstant.doAccessCheck(accessingKlass, holderInterface, method, meta);

            method.checkLoadingConstraints(accessingKlass.getDefiningClassLoader(), method.getDeclaringKlass().getDefiningClassLoader());

            return new Resolved(method);
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
            // return resolved.getDeclaringKlass().getName();
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
