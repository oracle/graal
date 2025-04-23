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

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.descriptors.Descriptor;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;

public interface ClassMethodRefConstant extends MethodRefConstant {

    static Indexes create(int classIndex, int nameAndTypeIndex) {
        return new Indexes(classIndex, nameAndTypeIndex);
    }

    @Override
    default Tag tag() {
        return Tag.METHOD_REF;
    }

    final class Indexes extends MethodRefConstant.Indexes implements ClassMethodRefConstant, Resolvable {
        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }

        @Override
        public void validate(ConstantPool pool) throws ValidationException {
            super.validate(pool);
            // If the name of the method of a CONSTANT_Methodref_info structure begins with a '<'
            // ('\u003c'), then the name must be the special name <init>, representing an instance
            // initialization method (&sect;2.9). The return type of such a method must be void.
            pool.nameAndTypeAt(nameAndTypeIndex).validateMethod(pool, false, true);
            Symbol<Name> name = pool.nameAndTypeAt(nameAndTypeIndex).getName(pool);
            if (ParserNames._init_.equals(name)) {
                Symbol<? extends Descriptor> descriptor = pool.nameAndTypeAt(nameAndTypeIndex).getDescriptor(pool);
                int len = descriptor.length();
                if (len <= 2 || (descriptor.byteAt(len - 2) != ')' || descriptor.byteAt(len - 1) != 'V')) {
                    throw ValidationException.raise("<init> method should have ()V signature");
                }
            }
        }
    }

}
