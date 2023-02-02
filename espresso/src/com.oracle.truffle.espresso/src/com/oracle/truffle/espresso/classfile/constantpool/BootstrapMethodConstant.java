/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;

/**
 * Interface denoting a bootstrap method constant entry in a constant pool.
 */
public interface BootstrapMethodConstant extends PoolConstant {

    int getBootstrapMethodAttrIndex();

    Symbol<Name> getName(ConstantPool pool);

    NameAndTypeConstant getNameAndType(ConstantPool pool);

    @Override
    default String toString(ConstantPool pool) {
        return "bsmIndex:" + getBootstrapMethodAttrIndex() + " " + getNameAndType(pool);
    }

    abstract class Indexes implements BootstrapMethodConstant {

        protected final char bootstrapMethodAttrIndex;
        protected final char nameAndTypeIndex;

        Indexes(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
            this.bootstrapMethodAttrIndex = PoolConstant.u2(bootstrapMethodAttrIndex);
            this.nameAndTypeIndex = PoolConstant.u2(nameAndTypeIndex);
        }

        @Override
        public final int getBootstrapMethodAttrIndex() {
            return bootstrapMethodAttrIndex;
        }

        @Override
        public final Symbol<Name> getName(ConstantPool pool) {
            return getNameAndType(pool).getName(pool);
        }

        @Override
        public NameAndTypeConstant getNameAndType(ConstantPool pool) {
            return pool.nameAndTypeAt(nameAndTypeIndex);
        }
    }
}
