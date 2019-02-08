/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;

import static com.oracle.truffle.espresso.nodes.BytecodeNode.resolveKlassCount;

/**
 * Interface denoting a class entry in a constant pool.
 */
public interface ClassConstant extends PoolConstant {

    @Override
    default Tag tag() {
        return Tag.CLASS;
    }

    /**
     * Gets the type descriptor of the class represented by this constant.
     *
     * @param pool container of this constant
     */
    Symbol<Name> getName(ConstantPool pool);

    @Override
    default String toString(ConstantPool pool) {
        return getName(pool).toString();
    }

    final class Index implements ClassConstant, Resolvable {
        private final char classNameIndex;

        Index(int classNameIndex) {
            this.classNameIndex = PoolConstant.u2(classNameIndex);
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return pool.utf8At(classNameIndex);
        }

        /**
         * <h3>5.4.3.1. Class and Interface Resolution</h3>
         *
         * To resolve an unresolved symbolic reference from D to a class or interface C denoted by
         * N, the following steps are performed:
         * <ol>
         * <li>The defining class loader of D is used to create a class or interface denoted by N.
         * This class or interface is C. The details of the process are given in §5.3. <b>Any
         * exception that can be thrown as a result of failure of class or interface creation can
         * thus be thrown as a result of failure of class and interface resolution.</b>
         * <li>If C is an array class and its element type is a reference type, then a symbolic
         * reference to the class or interface representing the element type is resolved by invoking
         * the algorithm in §5.4.3.1 recursively.
         * <li>Finally, access permissions to C are checked.
         * <ul>
         * <li><b>If C is not accessible (§5.4.4) to D, class or interface resolution throws an
         * IllegalAccessError.</b> This condition can occur, for example, if C is a class that was
         * originally declared to be public but was changed to be non-public after D was compiled.
         * </ul>
         * </ol>
         * If steps 1 and 2 succeed but step 3 fails, C is still valid and usable. Nevertheless,
         * resolution fails, and D is prohibited from accessing C.
         */
        private static void pepe() {

        }

        @Override
        public Resolved resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            resolveKlassCount.inc();
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Symbol<Name> name = getName(pool);
            try {
                EspressoContext context = pool.getContext();
                Klass klass = context.getRegistries().loadKlass(
                                context.getTypes().fromName(name), accessingKlass.getDefiningClassLoader());

                if (!checkAccess(klass, accessingKlass)) {
                    Meta meta = context.getMeta();
                    throw meta.throwExWithMessage(meta.IllegalAccessError, meta.toGuestString(name));
                }

                return new Resolved(klass);

            } catch (VirtualMachineError e) {
                // Comment from Hotspot:
                // Just throw the exception and don't prevent these classes from
                // being loaded for virtual machine errors like StackOverflow
                // and OutOfMemoryError, etc.
                // Needs clarification to section 5.4.3 of the JVM spec (see 6308271)
                throw e;
            }
        }

        /**
         * A class or interface C is accessible to a class or interface D if and only if either of
         * the following is true:
         * <ul>
         * <li>C is public.
         * <li>C and D are members of the same run-time package (§5.3).
         * </ul>
         */
        private static boolean checkAccess(Klass klass, Klass accessingKlass) {
            return klass.isPublic() || klass.getRuntimePackage().equals(accessingKlass.getRuntimePackage());
        }
    }

    final class Resolved implements ClassConstant, Resolvable.ResolvedConstant {
        private final Klass resolved;

        Resolved(Klass resolved) {
            this.resolved = Objects.requireNonNull(resolved);
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return resolved.getName();
        }

        @Override
        public Klass value() {
            return resolved;
        }
    }
}
