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
import com.oracle.truffle.espresso.types.SignatureDescriptor;

public interface MethodTypeConstant extends PoolConstant {
    default Tag tag() {
        return Tag.METHODTYPE;
    }

    SignatureDescriptor getSignature(ConstantPool pool, int thisIndex);

    default String toString(ConstantPool pool, int thisIndex) {
        return getSignature(pool, thisIndex).toString();
    }

    public static final class Resolved implements MethodTypeConstant {

        private final SignatureDescriptor signature;

        Resolved(SignatureDescriptor signature) {
            this.signature = signature;
        }

        public SignatureDescriptor getSignature(ConstantPool pool, int thisIndex) {
            return signature;
        }
    }

    static final class Index implements MethodTypeConstant {

        private final char descriptorIndex;

        Index(int descriptorIndex) {
            this.descriptorIndex = PoolConstant.u2(descriptorIndex);
        }

        public SignatureDescriptor getSignature(ConstantPool pool, int thisIndex) {
            String descriptor = pool.utf8At(descriptorIndex).toString();
            Resolved constant = new Resolved(pool.getContext().getLanguage().getSignatureDescriptors().make(descriptor));
            pool.updateAt(thisIndex, constant);
            return constant.signature;
        }
    }
}
