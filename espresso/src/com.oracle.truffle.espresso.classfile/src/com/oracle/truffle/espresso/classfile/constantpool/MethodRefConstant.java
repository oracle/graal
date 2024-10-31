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

import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;
import com.oracle.truffle.espresso.classfile.ConstantPool;

public interface MethodRefConstant extends MemberRefConstant {

    /**
     * Gets the signature descriptor of the method represented by this constant.
     *
     * @param pool container of this constant
     */
    @SuppressWarnings("unchecked")
    default Symbol<Signature> getSignature(ConstantPool pool) {
        return (Symbol<Signature>) getDescriptor(pool);
    }

    abstract class Indexes extends MemberRefConstant.Indexes implements MethodRefConstant {
        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }

        @Override
        public void validate(ConstantPool pool) throws ValidationException {
            super.validate(pool);
            // <clinit> method name is allowed here.
            pool.nameAndTypeAt(nameAndTypeIndex).validateMethod(pool, true);
        }
    }
}
