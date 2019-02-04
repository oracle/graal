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

import com.oracle.truffle.espresso.descriptors.ByteString;
import com.oracle.truffle.espresso.descriptors.ByteString.Descriptor;
import com.oracle.truffle.espresso.descriptors.ByteString.Name;
import com.oracle.truffle.espresso.descriptors.ByteString.Type;

/**
 * Interface denoting a field or method entry in a constant pool.
 */
public interface MemberRefConstant extends PoolConstant {

    /**
     * Gets the class in which this method or field is declared. Note that the actual holder after
     * resolution may be a super class of the class described by the one returned by this method.
     */
    ByteString<Type> getDeclaringClass(ConstantPool pool);

    /**
     * Gets the name of this field or method.
     *
     * @param pool the constant pool that maybe be required to convert a constant pool index to a
     *            name
     */
    ByteString<Name> getName(ConstantPool pool);

    /**
     * Gets the descriptor (type or signature) of this field or method.
     *
     * @param pool the constant pool that maybe be required to convert a constant pool index to a
     *            name
     */
    ByteString<? extends Descriptor> getDescriptor(ConstantPool pool);

    @Override
    default String toString(ConstantPool pool) {
        return getDeclaringClass(pool) + "." + getName(pool) + getDescriptor(pool);
    }

    abstract class Indexes implements MemberRefConstant {

        private final char classIndex;
        private final char nameAndTypeIndex;

        Indexes(int classIndex, int nameAndTypeIndex) {
            this.classIndex = PoolConstant.u2(classIndex);
            this.nameAndTypeIndex = PoolConstant.u2(nameAndTypeIndex);
        }

        @Override
        public ByteString<Type> getDeclaringClass(ConstantPool pool) {
            return pool.classAt(classIndex).getType(pool);
        }

        @Override
        public ByteString<Name> getName(ConstantPool pool) {
            return pool.nameAndTypeAt(nameAndTypeIndex).getName(pool);
        }

        @Override
        public ByteString<? extends Descriptor> getDescriptor(ConstantPool pool) {
            return pool.nameAndTypeAt(nameAndTypeIndex).getDescriptor(pool);
        }
    }
}
