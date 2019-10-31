/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.meta;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.VMConstant;

public abstract class SubstrateObjectConstant implements JavaConstant, CompressibleConstant, VMConstant {
    public static JavaConstant forObject(Object object) {
        return forObject(object, false);
    }

    public static JavaConstant forObject(Object object, boolean compressed) {
        if (object == null) {
            return compressed ? CompressedNullConstant.COMPRESSED_NULL : JavaConstant.NULL_POINTER;
        }
        return new DirectSubstrateObjectConstant(object, compressed);
    }

    public static JavaConstant forBoxedValue(JavaKind kind, Object value) {
        if (kind == JavaKind.Object) {
            return forObject(value);
        }
        return JavaConstant.forBoxedPrimitive(value);
    }

    public static Object asObject(Constant constant) {
        if (JavaConstant.isNull(constant)) {
            return null;
        }
        return ((DirectSubstrateObjectConstant) constant).getObject();
    }

    public static <T> T asObject(Class<T> type, JavaConstant constant) {
        if (constant.isNonNull()) {
            Object object = ((DirectSubstrateObjectConstant) constant).getObject();
            if (type.isInstance(object)) {
                return type.cast(object);
            }
        }
        return null;
    }

    public static Object asObject(ResolvedJavaType type, JavaConstant constant) {
        if (constant.isNonNull() && constant instanceof DirectSubstrateObjectConstant) {
            Object object = ((DirectSubstrateObjectConstant) constant).getObject();
            if (type.isInstance(constant)) {
                return object;
            }
        }
        return null;
    }

    public static boolean isCompressed(JavaConstant constant) {
        return constant instanceof CompressibleConstant && ((CompressibleConstant) constant).isCompressed();
    }

    protected final boolean compressed;

    protected SubstrateObjectConstant(boolean compressed) {
        this.compressed = compressed;
    }

    @Override
    public boolean isCompressed() {
        return compressed;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public Object asBoxedPrimitive() {
        throw new IllegalArgumentException();
    }

    @Override
    public int asInt() {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean asBoolean() {
        throw new IllegalArgumentException();
    }

    @Override
    public long asLong() {
        throw new IllegalArgumentException();
    }

    @Override
    public float asFloat() {
        throw new IllegalArgumentException();
    }

    @Override
    public double asDouble() {
        throw new IllegalArgumentException();
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj != this && obj instanceof SubstrateObjectConstant) {
            SubstrateObjectConstant other = (SubstrateObjectConstant) obj;
            return isCompressed() == other.isCompressed() && ObjectConstantEquality.get().test(this, other);
        }
        return obj == this;
    }

    @Override
    public final int hashCode() {
        return getIdentityHashCode();
    }

    protected abstract int getIdentityHashCode();

    @Override
    public String toString() {
        return getJavaKind().getJavaName(); // + "[" + toValueString() + "]" + object;
    }

    public abstract ResolvedJavaType getType(MetaAccessProvider provider);

    @Override
    public abstract SubstrateObjectConstant compress();

    @Override
    public abstract SubstrateObjectConstant uncompress();

    public abstract boolean setRoot(Object newRoot);

    public abstract Object getRoot();
}
