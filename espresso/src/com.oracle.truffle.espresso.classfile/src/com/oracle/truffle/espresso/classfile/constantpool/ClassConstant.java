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

import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Validation;

/**
 * Interface denoting a class entry in a constant pool.
 */
public interface ClassConstant extends PoolConstant {

    static ClassConstant create(int classNameIndex) {
        return new Index(classNameIndex);
    }

    static ClassConstant withString(Symbol<Name> name) {
        return new WithString(name);
    }

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
            return pool.symbolAt(classNameIndex);
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

    final class WithString implements ClassConstant, Resolvable {
        private final Symbol<Name> name;

        WithString(Symbol<Name> name) {
            this.name = name;
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return name;
        }

        @Override
        public void validate(ConstantPool pool) throws ValidationException {
            // No UTF8 entry: cannot cache validation.
            if (!Validation.validModifiedUTF8(name) || !Validation.validClassNameEntry(name)) {
                throw ValidationException.raise("Invalid class name entry: " + name);
            }
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar((char) 0);
        }
    }
}
