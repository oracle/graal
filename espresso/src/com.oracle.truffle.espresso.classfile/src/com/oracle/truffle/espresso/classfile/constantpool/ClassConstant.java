/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;

/**
 * Interface denoting a class entry in a constant pool.
 */
public interface ClassConstant extends PoolConstant {

    static ImmutableClassConstant create(int classNameIndex) {
        return new Index(classNameIndex);
    }

    @Override
    default Tag tag() {
        return Tag.CLASS;
    }

    interface ImmutableClassConstant extends ClassConstant, ImmutablePoolConstant {
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

        @Override
        default boolean isSame(ImmutablePoolConstant other, ConstantPool thisPool, ConstantPool otherPool) {
            if (!(other instanceof ImmutableClassConstant otherConstant)) {
                return false;
            }
            return getName(thisPool) == otherConstant.getName(otherPool);
        }
    }

    final class Index implements ImmutableClassConstant, Resolvable {
        private final char classNameIndex;

        Index(int classNameIndex) {
            this.classNameIndex = PoolConstant.u2(classNameIndex);
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return pool.symbolAtUnsafe(classNameIndex);
        }

        @Override
        public void validate(ConstantPool pool) throws ValidationException {
            pool.utf8At(classNameIndex).validateClassName();
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(classNameIndex);
        }
    }
}
