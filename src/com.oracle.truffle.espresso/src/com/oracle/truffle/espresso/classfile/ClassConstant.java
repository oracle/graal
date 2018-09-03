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
import com.oracle.truffle.espresso.impl.Klass;
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

    static final class Resolved implements ClassConstant {

        final Klass klass;

        public Resolved(Klass klass) {
            this.klass = klass;
        }

        public TypeDescriptor getTypeDescriptor(ConstantPool pool, int index) {
            return klass.getTypeDescriptor();
        }

        public Klass resolve(ConstantPool pool, int index) {
            return klass;
        }
    }

    static class Unresolved implements ClassConstant {

        private final TypeDescriptor type;

        Unresolved(TypeDescriptor type) {
            this.type = type;
        }

        public TypeDescriptor getTypeDescriptor(ConstantPool pool, int index) {
            return type;
        }

        public Klass resolve(ConstantPool pool, int index) {
            try {
                try {
                    Klass klass = pool.getContext().getRegistries().resolve(type, pool.getClassLoader());
                    pool.updateAt(index, new Resolved(klass));
                    return klass;
                } catch (RuntimeException e) {
                    throw (NoClassDefFoundError) new NoClassDefFoundError(type.toString()).initCause(e);
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

    static class Index implements ClassConstant {
        private final char classNameIndex;

        Index(int classNameIndex) {
            this.classNameIndex = PoolConstant.u2(classNameIndex);
        }

        private ClassConstant replace(ConstantPool pool, int index) {
            // TODO(peterssen): Handle names correctly.
            String typeName = fixName(pool.utf8At(classNameIndex).getValue());
            Unresolved replacement = new Unresolved(pool.getContext().getTypeDescriptors().make(typeName));
            return (ClassConstant) pool.updateAt(index, replacement);
        }

        private static String fixName(String name) {
            if (name.startsWith("[")) {
                return name;
            }
            return "L" + name + ";";
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
