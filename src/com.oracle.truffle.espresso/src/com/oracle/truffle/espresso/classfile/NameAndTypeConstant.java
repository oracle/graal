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
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Descriptor;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;

public interface NameAndTypeConstant extends PoolConstant {

    /**
     * Gets the name of this name+descriptor pair constant.
     *
     * @param pool the constant pool that maybe be required to convert a constant pool index to a
     *            name
     */
    Symbol<Name> getName(ConstantPool pool);

    /**
     * Gets the descriptor of this name+descriptor pair constant.
     *
     * @param pool the constant pool that maybe be required to convert a constant pool index to a
     *            name
     */
    Symbol<? extends Descriptor> getDescriptor(ConstantPool pool);

    void validateMethod(ConstantPool pool);

    void validateField(ConstantPool pool);

    @Override
    default Tag tag() {
        return Tag.NAME_AND_TYPE;
    }

    @Override
    default String toString(ConstantPool pool) {
        return getName(pool) + ":" + getDescriptor(pool);
    }

    final class Indexes implements NameAndTypeConstant {

        private final char nameIndex;
        private final char typeIndex;

        public Indexes(int nameIndex, int typeIndex) {
            this.nameIndex = PoolConstant.u2(nameIndex);
            this.typeIndex = PoolConstant.u2(typeIndex);
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return pool.symbolAt(nameIndex);
        }

        @Override
        public Symbol<? extends Descriptor> getDescriptor(ConstantPool pool) {
            return pool.symbolAt(typeIndex);
        }

        @Override
        public void validate(ConstantPool pool) {
            Symbol<? extends Descriptor> descriptor = getDescriptor(pool);
            if (descriptor.length() > 0 && descriptor.byteAt(0) == '(') {
                validateMethod(pool);
            } else {
                // Fails with empty name.
                validateField(pool);
            }
        }

        @Override
        public void validateMethod(ConstantPool pool) {
            pool.utf8At(nameIndex).validateMethodName();
            pool.utf8At(typeIndex).validateSignature();
        }

        @Override
        public void validateField(ConstantPool pool) {
            pool.utf8At(nameIndex).validateFieldName();
            pool.utf8At(typeIndex).validateType(false);
        }
    }
}
