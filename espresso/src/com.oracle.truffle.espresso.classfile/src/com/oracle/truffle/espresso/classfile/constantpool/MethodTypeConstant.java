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
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;

public interface MethodTypeConstant extends PoolConstant {

    static Index create(int descriptorIndex) {
        return new Index(descriptorIndex);
    }

    @Override
    default Tag tag() {
        return Tag.METHODTYPE;
    }

    final class Index implements MethodTypeConstant, ImmutablePoolConstant, Resolvable {
        private final char descriptorIndex;

        Index(int descriptorIndex) {
            this.descriptorIndex = PoolConstant.u2(descriptorIndex);
        }

        /**
         * Gets the signature of this method type constant.
         *
         * @param pool the constant pool that maybe be required to convert a constant pool index to
         *            a name
         */
        public Symbol<Signature> getSignature(ConstantPool pool) {
            return pool.symbolAtUnsafe(descriptorIndex);
        }

        @Override
        public boolean isSame(ImmutablePoolConstant other, ConstantPool thisPool, ConstantPool otherPool) {
            if (!(other instanceof Index otherConstant)) {
                return false;
            }
            return getSignature(thisPool) == otherConstant.getSignature(otherPool);
        }

        @Override
        public void validate(ConstantPool pool) throws ValidationException {
            pool.utf8At(descriptorIndex).validateSignature();
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(descriptorIndex);
        }

        @Override
        public String toString(ConstantPool pool) {
            return getSignature(pool).toString();
        }
    }
}
