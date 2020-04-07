/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

//Checkstyle: allow reflection

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.reflect.Array;

import com.oracle.svm.core.meta.ObjectConstantEquality;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class SharedConstantReflectionProvider implements ConstantReflectionProvider {

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        if (x == y) {
            return true;
        } else if (x instanceof SubstrateObjectConstant) {
            return y instanceof SubstrateObjectConstant && ObjectConstantEquality.get().test((SubstrateObjectConstant) x, (SubstrateObjectConstant) y);
        } else {
            return x.equals(y);
        }
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        if (array.getJavaKind() != JavaKind.Object || array.isNull() || !SubstrateObjectConstant.asObject(array).getClass().isArray()) {
            return null;
        }
        return java.lang.reflect.Array.getLength(SubstrateObjectConstant.asObject(array));
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }

        Object a = KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(array), Object.class);

        if (index < 0 || index >= Array.getLength(a)) {
            return null;
        }

        if (a instanceof Object[]) {
            Object element = ((Object[]) a)[index];
            return SubstrateObjectConstant.forObject(element);
        } else {
            return JavaConstant.forBoxedPrimitive(Array.get(a, index));
        }
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        if (!source.getJavaKind().isPrimitive()) {
            return null;
        }
        return SubstrateObjectConstant.forObject(source.asBoxedPrimitive());
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        if (!source.getJavaKind().isObject()) {
            return null;
        }
        return JavaConstant.forBoxedPrimitive(KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(source), Object.class));
    }

    @Override
    public JavaConstant forString(String value) {
        return SubstrateObjectConstant.forObject(value);
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        return ((ReadableJavaField) field).readValue(receiver);
    }

    @Override
    public final MethodHandleAccessProvider getMethodHandleAccess() {
        throw shouldNotReachHere();
    }

    @Override
    public final Constant asObjectHub(ResolvedJavaType type) {
        /*
         * Substrate VM does not distinguish between the hub and the Class, they are both
         * represented by the DynamicHub.
         */
        return asJavaClass(type);
    }
}
