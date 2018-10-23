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

public interface NameAndTypeConstant extends PoolConstant {

    Utf8Constant getName(ConstantPool pool, int thisIndex);

    Utf8Constant getType(ConstantPool pool, int thisIndex);

    @Override
    default Tag tag() {
        return Tag.NAME_AND_TYPE;
    }

    @Override
    default String toString(ConstantPool pool, int thisIndex) {
        return getName(pool, thisIndex) + ":" + getType(pool, thisIndex);
    }

    public static final class Resolved implements NameAndTypeConstant {

        private final Utf8Constant name;
        private final Utf8Constant type;

        Resolved(Utf8Constant name, Utf8Constant type) {
            this.name = name;
            this.type = type;
        }

        public Utf8Constant getName(ConstantPool pool, int thisIndex) {
            return name;
        }

        public Utf8Constant getType(ConstantPool pool, int thisIndex) {
            return type;
        }

    }

    public static final class Indexes implements NameAndTypeConstant {
        private final char nameIndex;
        private final char typeIndex;

        public Indexes(int nameIndex, int typeIndex) {
            this.nameIndex = PoolConstant.u2(nameIndex);
            this.typeIndex = PoolConstant.u2(typeIndex);
        }

        private NameAndTypeConstant replace(ConstantPool pool, int thisIndex) {
            Utf8Constant name = pool.utf8At(nameIndex);
            Utf8Constant type = pool.utf8At(typeIndex);
            return (NameAndTypeConstant) pool.updateAt(thisIndex, new Resolved(name, type));
        }

        public Utf8Constant getName(ConstantPool pool, int thisIndex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return replace(pool, thisIndex).getName(pool, thisIndex);
        }

        public Utf8Constant getType(ConstantPool pool, int thisIndex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return replace(pool, thisIndex).getType(pool, thisIndex);
        }
    }
}
