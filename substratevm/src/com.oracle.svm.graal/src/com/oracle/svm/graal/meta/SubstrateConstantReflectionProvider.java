/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import static com.oracle.svm.shared.util.VMError.shouldNotReachHereAtRuntime;

import java.lang.reflect.Array;
import java.util.Objects;

import org.graalvm.word.SignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.ObjectConstantEquality;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateConstantReflectionProvider extends SharedConstantReflectionProvider {
    private final MetaAccessProvider metaAccess;

    public SubstrateConstantReflectionProvider(SubstrateMetaAccess metaAccess) {
        this.metaAccess = metaAccess;
        assert SubstrateUtil.HOSTED || SubstrateOptions.useRistretto();
    }

    @Override
    public boolean canRepresentAsImageHeapOffset(JavaConstant constant) {
        return getImageHeapOffset(constant) != 0;
    }

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
        return Array.getLength(SubstrateObjectConstant.asObject(array));
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }

        Object a = SubstrateObjectConstant.asObject(array);

        if (!a.getClass().isArray() || index < 0 || index >= Array.getLength(a)) {
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
    public JavaConstant unboxPrimitive(JavaConstant source) {
        if (!source.getJavaKind().isObject()) {
            return null;
        }
        return JavaConstant.forBoxedPrimitive(SubstrateObjectConstant.asObject(source));
    }

    @Override
    public JavaConstant forString(String value) {
        return SubstrateObjectConstant.forObject(value);
    }

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        throw shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int identityHashCode(JavaConstant constant) {
        JavaKind kind = Objects.requireNonNull(constant).getJavaKind();
        if (kind != JavaKind.Object) {
            throw new IllegalArgumentException("Constant has unexpected kind " + kind + ": " + constant);
        }
        if (constant.isNull()) {
            /* System.identityHashCode is specified to return 0 when passed null. */
            return 0;
        }
        if (constant instanceof SubstrateObjectConstant sConstant) {
            return sConstant.getIdentityHashCode();
        }
        throw new IllegalArgumentException("Constant has unexpected type " + constant.getClass() + ": " + constant);
    }

    @Override
    public int makeIdentityHashCode(JavaConstant constant, int requestedValue) {
        throw VMError.unsupportedFeature("Injecting identity hash code not supported at Native Image runtime");
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return SubstrateMemoryAccessProviderImpl.SINGLETON;
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        if (constant instanceof SubstrateObjectConstant) {
            Object obj = SubstrateObjectConstant.asObject(constant);
            if (obj instanceof DynamicHub) {
                return ((SubstrateMetaAccess) metaAccess).lookupJavaTypeFromHub(((DynamicHub) obj));
            }
        }
        return null;
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return SubstrateObjectConstant.forObject(((SubstrateType) type).getHub());
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        return readFieldValue((SubstrateField) field, receiver);
    }

    public static JavaConstant readFieldValue(SubstrateField field, JavaConstant receiver) {
        if (field.constantValue != null) {
            return field.constantValue;
        }
        int location = field.getLocation();
        if (location < 0) {
            return null;
        }
        JavaKind kind = field.getStorageKind();
        Object baseObject;
        if (field.isStatic()) {
            if (kind.isObject()) {
                baseObject = StaticFieldsSupport.getStaticObjectFieldsAtRuntime(field.getInstalledLayerNum());
            } else {
                baseObject = StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(field.getInstalledLayerNum());
            }
        } else {
            if (receiver == null || !field.getDeclaringClass().isInstance(receiver)) {
                return null;
            }
            baseObject = SubstrateObjectConstant.asObject(receiver);
            if (baseObject == null) {
                return null;
            }
        }

        boolean isVolatile = field.isVolatile();
        JavaConstant result;
        /*
         * We know that the memory offset we are reading from is a proper field location: we already
         * checked that the receiver is an instance of an instance field's declaring class; and for
         * static fields the offsets are into the known data arrays that hold the fields. So we can
         * use read methods that do not perform further checks.
         */
        if (kind.isObject()) {
            result = SubstrateMemoryAccessProviderImpl.readObjectUnchecked(baseObject, location, false, isVolatile);
        } else {
            result = SubstrateMemoryAccessProviderImpl.readPrimitiveUnchecked(kind, baseObject, location, kind.getByteCount(), isVolatile);
        }
        return result;
    }

    @Override
    public int getImageHeapOffset(JavaConstant constant) {
        if (constant instanceof SubstrateObjectConstant) {
            return getImageHeapOffsetInternal((SubstrateObjectConstant) constant);
        }

        /* Primitive values, null values. */
        return 0;
    }

    protected static int getImageHeapOffsetInternal(SubstrateObjectConstant constant) {
        Object object = SubstrateObjectConstant.asObject(constant);
        assert object != null;
        /*
         * Provide offsets only for objects in the primary image heap, any optimizations for
         * auxiliary image heaps can lead to trouble when generated code and their objects are built
         * into yet another auxiliary image and the object offsets change.
         */
        if (Heap.getHeap().isInPrimaryImageHeap(object)) {
            SignedWord base = (SignedWord) KnownIntrinsics.heapBase();
            SignedWord offset = Word.objectToUntrackedWord(object).subtract(base);
            return NumUtil.safeToInt(offset.rawValue());
        } else {
            return 0;
        }
    }
}
