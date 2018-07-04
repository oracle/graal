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

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.runtime.Klass;
import com.oracle.truffle.espresso.types.TypeDescriptor;

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
     * @param thisIndex index of this constant in {@code pool}
     */
    TypeDescriptor getTypeDescriptor(ConstantPool pool, int thisIndex);

    @Override
    default String toString(ConstantPool pool, int thisIndex) {
        return getTypeDescriptor(pool, thisIndex).toString();
    }

    /**
     * Resolves this entry to a {@link Klass}.
     *
     * @param pool container of this constant
     * @param index index of this constant in {@code pool}
     */
    Klass resolve(ConstantPool pool, int index);

    public static final class Resolved implements ClassConstant {

        final Klass klass;

        public Resolved(Klass klass) {
            this.klass = klass;
        }

        public TypeDescriptor getTypeDescriptor(ConstantPool pool, int index) {
            return klass.getName();
        }

        public Klass resolve(ConstantPool pool, int index) {
            return klass;
        }
    }

    public static class Unresolved implements ClassConstant {

        private final TypeDescriptor typeDescriptor;

        Unresolved(TypeDescriptor typeDescriptor) {
            this.typeDescriptor = typeDescriptor;
        }

        public TypeDescriptor getTypeDescriptor(ConstantPool pool, int index) {
            return typeDescriptor;
        }

        public Klass resolve(ConstantPool pool, int index) {
            try {
                try {
                    Klass klass = typeDescriptor.resolveType(pool.getContext(), pool.classLoader());
                    pool.updateAt(index, new Resolved(klass));
                    return klass;
                } catch (RuntimeException e) {
                    throw (NoClassDefFoundError) new NoClassDefFoundError(typeDescriptor.toString()).initCause(e);
                }
            } catch (VirtualMachineError e) {
                // Comment from Hotspot:
                // Just throw the exception and don't prevent these classes from
                // being loaded for virtual machine errors like StackOverflow
                // and OutOfMemoryError, etc.
                // Needs clarification to section 5.4.3 of the JVM spec (see 6308271)
                throw e;
            }
        }

    }

    public static class Index implements ClassConstant {

        private final char classNameIndex;

        Index(int classNameIndex) {
            this.classNameIndex = PoolConstant.u2(classNameIndex);
        }

        private ClassConstant replace(ConstantPool pool, int index) {
            Utf8Constant className = pool.utf8At(classNameIndex);
            Unresolved replacement = new Unresolved(pool.getContext().getLanguage().getTypeDescriptors().make('L' + className.toString() + ';'));
            return (ClassConstant) pool.updateAt(index, replacement);
        }

        public boolean isResolved() {
            return false;
        }

        public TypeDescriptor getTypeDescriptor(ConstantPool pool, int index) {
            return replace(pool, index).getTypeDescriptor(pool, index);
        }

        public Klass resolve(ConstantPool pool, int index) {
            return replace(pool, index).resolve(pool, index);
        }
    }
}
