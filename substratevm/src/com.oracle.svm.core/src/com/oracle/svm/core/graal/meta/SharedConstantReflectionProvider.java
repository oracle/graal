/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.graal.meta;

import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class SharedConstantReflectionProvider implements ConstantReflectionProvider {

    /**
     * Determines whether {@code constant} refers to an image heap object with an offset that is
     * known or can be patched once known.
     */
    public abstract boolean canRepresentAsImageHeapOffset(JavaConstant constant);

    @Override
    public final JavaConstant boxPrimitive(JavaConstant source) {
        /*
         * This method is likely not going to do what you want: sub-integer constants in Graal IR
         * are usually represented as constants with JavaKind.Integer, which means this method would
         * give you an Integer box instead of a Byte, Short, ... box.
         */
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final Constant asObjectHub(ResolvedJavaType type) {
        /*
         * Substrate VM does not distinguish between the hub and the Class, they are both
         * represented by the DynamicHub.
         */
        return asJavaClass(type);
    }

    public abstract int getImageHeapOffset(JavaConstant constant);
}
