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

import java.nio.ByteBuffer;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.descriptors.Descriptor;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;

/**
 * Interface denoting a field or method entry in a constant pool.
 */
public interface MemberRefConstant extends PoolConstant {

    abstract class Indexes implements MemberRefConstant, ImmutablePoolConstant {

        final char classIndex;

        final char nameAndTypeIndex;

        Indexes(int classIndex, int nameAndTypeIndex) {
            this.classIndex = PoolConstant.u2(classIndex);
            this.nameAndTypeIndex = PoolConstant.u2(nameAndTypeIndex);
        }

        /**
         * Gets the constant pool index of the class in which this method or field is declared.
         */
        public int getHolderIndex() {
            return classIndex;
        }

        /**
         * Gets the class in which this method or field is declared. Note that the actual holder
         * after resolution may be a super class of the class described by the one returned by this
         * method.
         */
        public Symbol<Name> getHolderKlassName(ConstantPool pool) {
            return getHolderClassConstant(pool).getName(pool);
        }

        public ClassConstant.ImmutableClassConstant getHolderClassConstant(ConstantPool pool) {
            return pool.classAt(getHolderIndex());
        }

        /**
         * Gets the name of this field or method.
         *
         * @param pool the constant pool that maybe be required to convert a constant pool index to
         *            a name
         */
        public Symbol<Name> getName(ConstantPool pool) {
            return getNameAndType(pool).getName(pool);
        }

        private NameAndTypeConstant getNameAndType(ConstantPool pool) {
            return pool.nameAndTypeAt(nameAndTypeIndex);
        }

        /**
         * Gets the descriptor (type or signature) of this field or method.
         *
         * @param pool the constant pool that maybe be required to convert a constant pool index to
         *            a name
         */
        public Symbol<? extends Descriptor> getDescriptor(ConstantPool pool) {
            return getNameAndType(pool).getDescriptor(pool);
        }

        @Override
        public void validate(ConstantPool pool) throws ValidationException {
            pool.classAt(classIndex).validate(pool);
            getNameAndType(pool).validate(pool);
        }

        @Override
        public boolean isSame(ImmutablePoolConstant other, ConstantPool thisPool, ConstantPool otherPool) {
            if (!(other instanceof Indexes otherConstant)) {
                return false;
            }
            return getHolderKlassName(thisPool) == otherConstant.getHolderKlassName(otherPool) && getNameAndType(thisPool).isSame(otherConstant.getNameAndType(otherPool), thisPool, otherPool);
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(classIndex);
            buf.putChar(nameAndTypeIndex);
        }

        @Override
        public String toString(ConstantPool pool) {
            return getHolderKlassName(pool) + "." + getName(pool) + getDescriptor(pool);
        }
    }
}
