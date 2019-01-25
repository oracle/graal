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

public interface InterfaceMethodRefConstant extends MethodRefConstant {

    @Override
    default Tag tag() {
        return Tag.INTERFACE_METHOD_REF;
    }

// static final class Resolved extends MethodRefConstant.Resolved implements
// InterfaceMethodRefConstant {
// public Resolved(MethodInfo method) {
// super(method);
// }
// }

// static final class Unresolved extends MethodRefConstant.Unresolved implements
// InterfaceMethodRefConstant {
//
// public Unresolved(TypeDescriptor declaringClass, String name, SignatureDescriptor signature) {
// super(declaringClass, name, signature);
// }
//
// private MethodInfo lookupMethod(Klass declaringInterface, String name, SignatureDescriptor
// signature) {
// MethodInfo m = declaringInterface.findMethod(name, signature);
// if (m != null) {
// return m;
// }
// for (Klass i : declaringInterface.getInterfaces()) {
// m = lookupMethod(i, name, signature);
// if (m != null) {
// return m;
// }
// }
// return null;
// }
//
// @Override
// public MethodInfo resolve(ConstantPool pool, int index) {
// CompilerDirectives.transferToInterpreterAndInvalidate();
// Klass declaringInterface = pool.getContext().getRegistries().resolve(getDeclaringClass(pool, -1),
// pool.getClassLoader());
// assert declaringInterface.isInterface();
// String name = getName(pool, index);
// SignatureDescriptor signature = getSignature(pool, index);
// MethodInfo m = lookupMethod(declaringInterface, name, signature);
// if (m != null) {
// return m;
// }
// throw EspressoError.shouldNotReachHere(declaringInterface.toString() + "." + name + signature);
// }
// }

    final class Indexes extends MethodRefConstant.Indexes implements InterfaceMethodRefConstant {
        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }
    }
}
