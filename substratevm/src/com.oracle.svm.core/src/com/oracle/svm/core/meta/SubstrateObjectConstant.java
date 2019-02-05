/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.VMConstant;

public final class SubstrateObjectConstant implements JavaConstant, CompressibleConstant, VMConstant {

    @Platforms(Platform.HOSTED_ONLY.class) //
    private static final AtomicReferenceFieldUpdater<SubstrateObjectConstant, Object> ROOT_UPDATER = AtomicReferenceFieldUpdater.newUpdater(SubstrateObjectConstant.class, Object.class, "root");

    public static JavaConstant forObject(Object object) {
        return forObject(object, false);
    }

    public static JavaConstant forObject(Object object, boolean compressed) {
        if (object == null) {
            return compressed ? CompressedNullConstant.COMPRESSED_NULL : JavaConstant.NULL_POINTER;
        }
        return new SubstrateObjectConstant(object, compressed);
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
        return ((SubstrateObjectConstant) constant).object;
    }

    public static <T> T asObject(Class<T> type, JavaConstant constant) {
        if (constant.isNonNull()) {
            Object object = ((SubstrateObjectConstant) constant).object;
            if (type.isInstance(object)) {
                return type.cast(object);
            }
        }
        return null;
    }

    public static Object asObject(ResolvedJavaType type, JavaConstant constant) {
        if (constant.isNonNull() && constant instanceof SubstrateObjectConstant) {
            Object object = ((SubstrateObjectConstant) constant).object;
            if (type.isInstance(constant)) {
                return object;
            }
        }
        return null;
    }

    public static boolean isCompressed(JavaConstant constant) {
        return constant instanceof CompressibleConstant && ((CompressibleConstant) constant).isCompressed();
    }

    /** The raw object wrapped by this constant. */
    private final Object object;
    private final boolean compressed;

    /**
     * An object specifying the origin of this constant. This value is used to distinguish between
     * various constants of the same type. Only objects coming from static final fields and
     * from @Fold annotations processing have a root. The static final field originated objects use
     * the field itself as a root while the @Fold originated objects use the folded method as a
     * root. The subtree of a root object shares the same root information as the root object, i.e.,
     * the root information is transiently passed to the statically reachable objects. Other
     * constants, embedded in the code, might not have a root. The root is only used at image build
     * time.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private volatile Object root;

    // As object hsp const
    private SubstrateObjectConstant(Object object, boolean compressed) {
        this.object = object;
        this.compressed = compressed;
        assert object != null;
        if (SubstrateUtil.isInLibgraal()) {
            throw new InternalError();
        }
    }

    public Object getObject() {
        return object;
    }

    @Override
    public boolean isCompressed() {
        return compressed;
    }

    @Override
    public JavaConstant compress() {
        assert !compressed;
        return new SubstrateObjectConstant(object, true);
    }

    @Override
    public JavaConstant uncompress() {
        assert compressed;
        return new SubstrateObjectConstant(object, false);
    }

    public boolean setRoot(Object newRoot) {
        if (root == null && newRoot != null) {
            /*
             * It is possible that the same constant is reached on paths from different roots. We
             * can only register one, we choose the first one.
             */
            return ROOT_UPDATER.compareAndSet(this, null, newRoot);
        }
        return false;
    }

    public Object getRoot() {
        return root;
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
    public int hashCode() {
        return System.identityHashCode(object);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof SubstrateObjectConstant) {
            SubstrateObjectConstant other = (SubstrateObjectConstant) o;
            if (object == other.object && compressed == other.compressed) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toValueString() {
        Object obj = KnownIntrinsics.convertUnknownValue(object, Object.class);
        if (obj instanceof String) {
            return (String) obj;
        }
        return obj.getClass().getName();
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public String toString() {
        return getJavaKind().getJavaName(); // + "[" + toValueString() + "]" + object;
    }
}
