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
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.types.SignatureDescriptor;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public interface ClassMethodRefConstant extends MethodRefConstant {

    default Tag tag() {
        return Tag.METHOD_REF;
    }

    static final class Resolved extends MethodRefConstant.Resolved implements ClassMethodRefConstant {
        public Resolved(MethodInfo method) {
            super(method);
        }
    }

    static final class Unresolved extends MethodRefConstant.Unresolved implements ClassMethodRefConstant {

        public Unresolved(TypeDescriptor declaringClass, String name, SignatureDescriptor signature) {
            super(declaringClass, name, signature);
        }

        public MethodInfo resolve(ConstantPool pool, int index) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Klass declaringClass = pool.getContext().getRegistries().resolve(getDeclaringClass(pool, -1), pool.getClassLoader());
            MethodInfo m = declaringClass.findMethod(getName(pool, -1), getSignature(pool, -1));
            pool.updateAt(index, new ClassMethodRefConstant.Resolved(m));
            return m;
        }
    }

    static final class Indexes extends MethodRefConstant.Indexes implements ClassMethodRefConstant {

        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }
    }
}
