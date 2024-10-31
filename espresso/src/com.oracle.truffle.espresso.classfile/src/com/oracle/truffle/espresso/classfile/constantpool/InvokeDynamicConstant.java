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

import com.oracle.truffle.espresso.classfile.descriptors.Signatures;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;

public interface InvokeDynamicConstant extends BootstrapMethodConstant {

    static InvokeDynamicConstant create(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
        return new Indexes(bootstrapMethodAttrIndex, nameAndTypeIndex);
    }

    @Override
    default Tag tag() {
        return Tag.INVOKEDYNAMIC;
    }

    Symbol<Signature> getSignature(ConstantPool pool);

    default Symbol<Type>[] getParsedSignature() {
        throw new IllegalStateException("Not resolved yet");
    }

    default boolean isResolved() {
        return false;
    }

    final class Indexes extends BootstrapMethodConstant.Indexes implements InvokeDynamicConstant, Resolvable {
        Indexes(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
            super(bootstrapMethodAttrIndex, nameAndTypeIndex);
        }

        @Override
        public void dump(ByteBuffer buf) {
            buf.putChar(bootstrapMethodAttrIndex);
            buf.putChar(nameAndTypeIndex);
        }

        @Override
        public void validate(ConstantPool pool) throws ValidationException {
            pool.nameAndTypeAt(nameAndTypeIndex).validateMethod(pool, false);
        }

        @Override
        public Symbol<Signature> getSignature(ConstantPool pool) {
            return Signatures.check(pool.nameAndTypeAt(nameAndTypeIndex).getDescriptor(pool));
        }

        @Override
        public String toString(ConstantPool pool) {
            return "bsmIndex:" + getBootstrapMethodAttrIndex() + " " + getSignature(pool);
        }
    }
}
