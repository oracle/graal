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
import com.oracle.truffle.espresso.types.TypeDescriptor;

/**
 * Interface denoting a field or method entry in a constant pool.
 */
public interface MemberRefConstant extends PoolConstant {

    /**
     * Gets the class in which this method or field is declared. Note that the actual holder after
     * resolution may be a super class of the class described by the one returned by this method.
     */
    TypeDescriptor getDeclaringClass(ConstantPool pool, int thisIndex);

    /**
     * Gets the name of this field or method.
     *
     * @param pool the constant pool that maybe be required to convert a constant pool index to a
     *            name
     */
    String getName(ConstantPool pool, int thisIndex);

    static abstract class Unresolved implements MemberRefConstant {
        private final TypeDescriptor declaringClass;
        private final String name;

        public Unresolved(TypeDescriptor declaringClass, String name) {
            this.declaringClass = declaringClass;
            this.name = name;
        }

        public final TypeDescriptor getDeclaringClass(ConstantPool pool, int thisIndex) {
            return declaringClass;
        }

        public final String getName(ConstantPool pool, int thisIndex) {
            return name;
        }
    }

    static abstract class Indexes implements MemberRefConstant {

        private final char classIndex;
        private final char nameAndTypeIndex;

        Indexes(int classIndex, int nameAndTypeIndex) {
            this.classIndex = PoolConstant.u2(classIndex);
            this.nameAndTypeIndex = PoolConstant.u2(nameAndTypeIndex);
        }

        protected abstract MemberRefConstant createUnresolved(ConstantPool pool, TypeDescriptor declaringClass, String name, String type);

        protected MemberRefConstant replace(ConstantPool pool, int thisIndex) {
            TypeDescriptor declaringClass = pool.classAt(classIndex).getTypeDescriptor(pool, classIndex);
            NameAndTypeConstant nat = pool.nameAndTypeAt(nameAndTypeIndex);
            Utf8Constant name = nat.getName(pool, nameAndTypeIndex);
            Utf8Constant type = nat.getType(pool, nameAndTypeIndex);
            return (MemberRefConstant) pool.updateAt(thisIndex, createUnresolved(pool, declaringClass, name.getValue(), type.getValue()));
        }

        public final TypeDescriptor getDeclaringClass(ConstantPool pool, int thisIndex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return replace(pool, thisIndex).getDeclaringClass(pool, thisIndex);
        }

        public String getName(ConstantPool pool, int thisIndex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return replace(pool, thisIndex).getName(pool, thisIndex);
        }
    }
}
