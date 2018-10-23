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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public interface FieldRefConstant extends MemberRefConstant {

    @Override
    default Tag tag() {
        return Tag.FIELD_REF;
    }

    TypeDescriptor getTypeDescriptor(ConstantPool pool, int thisIndex);

    FieldInfo resolve(ConstantPool pool, int thisIndex);

    @Override
    default String toString(ConstantPool pool, int thisIndex) {
        return getDeclaringClass(pool, thisIndex) + "." + getName(pool, thisIndex) + getTypeDescriptor(pool, thisIndex);
    }

    public static final class Resolved implements FieldRefConstant {

        private final FieldInfo field;

        public FieldInfo field() {
            return field;
        }

        public Resolved(FieldInfo field) {
            this.field = field;
        }

        @Override
        public FieldInfo resolve(ConstantPool pool, int index) {
            return field;
        }

        @Override
        public TypeDescriptor getDeclaringClass(ConstantPool pool, int thisIndex) {
            return field.getDeclaringClass().getTypeDescriptor();
        }

        public String getName(ConstantPool pool, int thisIndex) {
            return field.getName();
        }

        public TypeDescriptor getTypeDescriptor(ConstantPool pool, int thisIndex) {
            return field.getTypeDescriptor();
        }
    }

    static final class Unresolved extends MemberRefConstant.Unresolved implements FieldRefConstant {

        private final TypeDescriptor type;

        public Unresolved(TypeDescriptor declaringClass, String name, TypeDescriptor type) {
            super(declaringClass, name);
            this.type = type;
        }

        public TypeDescriptor getTypeDescriptor(ConstantPool pool, int thisIndex) {
            return type;
        }

        @Override
        public FieldInfo resolve(ConstantPool pool, int thisIndex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Klass declaringClass = pool.getContext().getRegistries().resolve(getDeclaringClass(pool, -1), pool.getClassLoader());
            while (declaringClass != null) {
                for (FieldInfo fi : declaringClass.getDeclaredFields()) {
                    if (fi.getName().equals(getName(pool, -1)) && type.equals(fi.getTypeDescriptor())) {
                        pool.updateAt(thisIndex, new FieldRefConstant.Resolved(fi));
                        return fi;
                    }
                }
                declaringClass = declaringClass.getSuperclass();
            }
            throw EspressoError.shouldNotReachHere();
        }
    }

    static final class Indexes extends MemberRefConstant.Indexes implements FieldRefConstant {

        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }

        @Override
        protected MemberRefConstant createUnresolved(ConstantPool pool, TypeDescriptor declaringClass, String name, String type) {
            return new FieldRefConstant.Unresolved(declaringClass, name, pool.getContext().getTypeDescriptors().make(type));
        }

        @Override
        protected FieldRefConstant replace(ConstantPool pool, int thisIndex) {
            return (FieldRefConstant) super.replace(pool, thisIndex);
        }

        public FieldInfo resolve(ConstantPool pool, int thisIndex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return replace(pool, thisIndex).resolve(pool, thisIndex);
        }

        public TypeDescriptor getTypeDescriptor(ConstantPool pool, int thisIndex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return replace(pool, thisIndex).getTypeDescriptor(pool, thisIndex);
        }
    }
}
