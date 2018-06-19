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

public interface DynamicConstant extends BootstrapMethodConstant {

    default Tag tag() {
        return Tag.DYNAMIC;
    }

    static final class Unresolved extends BootstrapMethodConstant.Unresolved implements DynamicConstant {

        public Unresolved(int bootstrapMethodAttrIndex, Utf8Constant name, SignatureDescriptor signature) {
            super(bootstrapMethodAttrIndex, name, signature);
        }
    }

    public static final class Indexes extends BootstrapMethodConstant.Indexes implements DynamicConstant {

        Indexes(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
            super(bootstrapMethodAttrIndex, nameAndTypeIndex);
        }

        @Override
        protected BootstrapMethodConstant createUnresolved(int bsmAttrIndex, Utf8Constant name, SignatureDescriptor signature) {
            return new DynamicConstant.Unresolved(bsmAttrIndex, name, signature);
        }
    }
}
