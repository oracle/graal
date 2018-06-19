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

import com.oracle.truffle.espresso.types.SignatureDescriptor;

public interface BootstrapMethodConstant extends PoolConstant {

    int getBootstrapMethodAttrIndex();

    Utf8Constant getName(ConstantPool pool, int thisIndex);

    SignatureDescriptor getSignature(ConstantPool pool, int thisIndex);

    @Override
    default String toString(ConstantPool pool, int thisIndex) {
        return "bsmIndex:" + getBootstrapMethodAttrIndex() + " " + getSignature(pool, thisIndex);
    }

    public static abstract class Unresolved implements BootstrapMethodConstant {
        private final char bootstrapMethodAttrIndex;
        private final Utf8Constant name;
        private final SignatureDescriptor signature;

        public Unresolved(int bootstrapMethodAttrIndex, Utf8Constant name, SignatureDescriptor signature) {
            this.bootstrapMethodAttrIndex = PoolConstant.u2(bootstrapMethodAttrIndex);
            this.name = name;
            this.signature = signature;
        }

        public final Utf8Constant getName(ConstantPool pool, int thisIndex) {
            return name;
        }

        public int getBootstrapMethodAttrIndex() {
            return bootstrapMethodAttrIndex;
        }

        public SignatureDescriptor getSignature(ConstantPool pool, int thisIndex) {
            return signature;
        }
    }

    public static abstract class Indexes implements BootstrapMethodConstant {

        private final char bootstrapMethodAttrIndex;
        private final char nameAndTypeIndex;

        Indexes(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
            this.bootstrapMethodAttrIndex = PoolConstant.u2(bootstrapMethodAttrIndex);
            this.nameAndTypeIndex = PoolConstant.u2(nameAndTypeIndex);
        }

        protected abstract BootstrapMethodConstant createUnresolved(int bsmAttrIndex, Utf8Constant name, SignatureDescriptor signature);

        protected BootstrapMethodConstant replace(ConstantPool pool, int thisIndex) {
            NameAndTypeConstant nat = pool.nameAndTypeAt(nameAndTypeIndex);
            Utf8Constant name = nat.getName(pool, nameAndTypeIndex);
            Utf8Constant type = nat.getType(pool, nameAndTypeIndex);
            SignatureDescriptor signature = pool.getContext().getLanguage().getSignatureDescriptors().make(type.toString());
            return (BootstrapMethodConstant) pool.updateAt(thisIndex, createUnresolved(bootstrapMethodAttrIndex, name, signature));
        }

        public Utf8Constant getName(ConstantPool pool, int thisIndex) {
            return replace(pool, thisIndex).getName(pool, thisIndex);
        }

        public SignatureDescriptor getSignature(ConstantPool pool, int thisIndex) {
            return replace(pool, thisIndex).getSignature(pool, thisIndex);
        }

        public int getBootstrapMethodAttrIndex() {
            return bootstrapMethodAttrIndex;
        }
    }
}
