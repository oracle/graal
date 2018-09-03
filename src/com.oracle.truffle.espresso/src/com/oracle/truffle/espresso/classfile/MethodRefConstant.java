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

import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.types.SignatureDescriptor;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public interface MethodRefConstant extends MemberRefConstant {

    SignatureDescriptor getSignature(ConstantPool pool, int thisIndex);

    MethodInfo resolve(ConstantPool pool, int index);

    @Override
    default String toString(ConstantPool pool, int thisIndex) {
        return getDeclaringClass(pool, thisIndex) + "." + getName(pool, thisIndex) + getSignature(pool, thisIndex);
    }

    static abstract class Resolved implements MethodRefConstant {
        private final MethodInfo method;

        public final MethodInfo getMethod() {
            return method;
        }

        public final MethodInfo resolve(ConstantPool pool, int index) {
            return method;
        }

        public Resolved(MethodInfo method) {
            this.method = method;
        }

        public TypeDescriptor getDeclaringClass(ConstantPool pool, int thisIndex) {
            return method.getDeclaringClass().getTypeDescriptor();
        }

        public String getName(ConstantPool pool, int thisIndex) {
            return method.getName();
        }

        public SignatureDescriptor getSignature(ConstantPool pool, int thisIndex) {
            return method.getSignature();
        }
    }

    static abstract class Unresolved extends MemberRefConstant.Unresolved implements MethodRefConstant {

        private final SignatureDescriptor signature;

        public Unresolved(TypeDescriptor declaringClass, String name, SignatureDescriptor signature) {
            super(declaringClass, name);
            this.signature = signature;
        }

        public SignatureDescriptor getSignature(ConstantPool pool, int thisIndex) {
            return signature;
        }
    }

    static abstract class Indexes extends MemberRefConstant.Indexes implements MethodRefConstant {

        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }

        @Override
        protected MemberRefConstant createUnresolved(ConstantPool pool, TypeDescriptor declaringClass, String name, String type) {
            return new ClassMethodRefConstant.Unresolved(declaringClass, name, pool.getContext().getSignatureDescriptors().make(type));
        }

        @Override
        protected MethodRefConstant replace(ConstantPool pool, int thisIndex) {
            return (MethodRefConstant) super.replace(pool, thisIndex);
        }

        public MethodInfo resolve(ConstantPool pool, int index) {
            return replace(pool, index).resolve(pool, index);
        }

        public SignatureDescriptor getSignature(ConstantPool pool, int index) {
            return replace(pool, index).getSignature(pool, index);
        }
    }
}