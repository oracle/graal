/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.meta.SubstrateMemoryAccessProvider;
import com.oracle.svm.core.meta.CompressedNullConstant;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.CompressibleConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class HostedMemoryAccessProvider implements SubstrateMemoryAccessProvider {

    private final HostedUniverse hUniverse;
    private final HostedMetaAccess hMetaAccess;
    private final HostedConstantReflectionProvider hConstantReflection;

    public HostedMemoryAccessProvider(HostedUniverse hUniverse, HostedMetaAccess hMetaAccess, HostedConstantReflectionProvider hConstantReflection) {
        this.hUniverse = hUniverse;
        this.hMetaAccess = hMetaAccess;
        this.hConstantReflection = hConstantReflection;
    }

    @Override
    public JavaConstant readPrimitiveConstant(JavaKind stackKind, Constant base, long displacement, int accessBits) {
        assert accessBits % Byte.SIZE == 0 && accessBits > 0 && accessBits <= 64 : accessBits;

        int accessBytes = accessBits / Byte.SIZE;
        JavaConstant result = doRead(stackKind, (JavaConstant) base, displacement, accessBytes);
        /* Wrap the result in the expected stack kind. */
        return switch (stackKind) {
            case Int -> JavaConstant.forInt(result.asInt());
            case Long, Float, Double -> {
                assert result.getJavaKind() == stackKind;
                yield result;
            }
            default -> throw VMError.shouldNotReachHereUnexpectedInput(stackKind); // ExcludeFromJacocoGeneratedReport
        };
    }

    @Override
    public JavaConstant readObjectConstant(Constant base, long displacement) {
        ObjectLayout layout = ImageSingletons.lookup(ObjectLayout.class);
        JavaConstant result = doRead(JavaKind.Object, (JavaConstant) base, displacement, layout.getReferenceSize());
        if (result instanceof ImageHeapRelocatableConstant) {
            throw new IllegalArgumentException("References to later layers cannot be constant-folded.");
        }
        return result;
    }

    @Override
    public JavaConstant readNarrowObjectConstant(Constant base, long displacement, CompressEncoding encoding) {
        assert SubstrateOptions.SpawnIsolates.getValue();
        // NOTE: the encoding parameter only applies at image runtime, not for hosted execution
        JavaConstant result = readObjectConstant(base, displacement);
        if (JavaConstant.NULL_POINTER.equals(result)) {
            return CompressedNullConstant.COMPRESSED_NULL;
        }
        return ((CompressibleConstant) result).compress();
    }

    private JavaConstant doRead(JavaKind stackKind, JavaConstant base, long displacement, int accessBytes) {
        if (base.isNull()) {
            throw new IllegalArgumentException("Base is null.");
        } else if (base.getJavaKind() != JavaKind.Object) {
            throw new IllegalArgumentException("Base " + base.getClass() + " is not supported.");
        }

        HostedType type = hMetaAccess.lookupJavaType(base);
        if (type.isInstanceClass()) {
            /*
             * Hybrid objects end up in this branch as well. For them, we only constant-fold field
             * reads at the moment (reads from the array part are not constant-folded).
             */
            HostedField field = (HostedField) type.findInstanceFieldWithOffset(displacement, null);
            if (field == null) {
                throw new IllegalArgumentException("Can't find field at displacement " + displacement + " in object of type " + type.toJavaName() + ".");
            } else if (field.getStorageKind().getStackKind() != stackKind) {
                throw new IllegalArgumentException("Field at displacement " + displacement + " in object of type " + type.toJavaName() +
                                " is " + field.getStorageKind().getStackKind() + " but expected " + stackKind + ".");
            }
            return readField(stackKind, base, field);
        } else if (type.isArray()) {
            if (isUsedForCurrentLayersStaticFields(base)) {
                throw new IllegalArgumentException("Static fields in the current layer may not be available yet.");
            }
            return readArrayElement(base, displacement, accessBytes, type);
        } else {
            throw VMError.shouldNotReachHere("Unexpected object type: " + type.toJavaName());
        }
    }

    /**
     * Read nodes for static final fields have an immutable location identity, so we will try to
     * constant-fold such reads. However, there is no guarantee that the field value is already
     * available at this point in time. So, we need to skip constant folding if we detect that the
     * read happens on an array that is used for the static fields of the current layer.
     */
    private boolean isUsedForCurrentLayersStaticFields(JavaConstant base) {
        if (base instanceof ImageHeapConstant imageHeapConstant) {
            JavaConstant hostedObject = imageHeapConstant.getHostedObject();
            if (hostedObject != null) {
                Object object = hUniverse.getSnippetReflection().asObject(Object.class, hostedObject);
                if (object != null) {
                    return object == StaticFieldsSupport.getCurrentLayerStaticPrimitiveFields() || object == StaticFieldsSupport.getCurrentLayerStaticObjectFields();
                }
            }
        }
        return false;
    }

    private JavaConstant readField(JavaKind stackKind, JavaConstant base, HostedField field) {
        assert field.getStorageKind().getStackKind() == stackKind;

        JavaConstant result = hConstantReflection.readFieldValue(field, base);
        if (result == null) {
            throw new IllegalArgumentException("Could not read field " + field.format("%H.%n"));
        }

        JavaKind resultStackKind = result.getJavaKind().getStackKind();
        if (stackKind != resultStackKind) {
            /* Primarily happens because Word types have a primitive type at run-time. */
            throw new IllegalArgumentException("Stack kind mismatch for field " + field.format("%H.%n") + ".");
        }

        return result;
    }

    private JavaConstant readArrayElement(JavaConstant base, long runtimeOffset, int accessBytes, HostedType type) {
        if (!(base instanceof ImageHeapArray array)) {
            throw VMError.shouldNotReachHere("May only be called for arrays.");
        }

        JavaKind arrayKind = JavaKind.fromJavaClass(type.getComponentType().getJavaClass());
        int runtimeBaseOffset = hMetaAccess.getArrayBaseOffset(arrayKind);
        int runtimeIndexScale = hMetaAccess.getArrayIndexScale(arrayKind);

        long accessedDataOffset = runtimeOffset - runtimeBaseOffset;
        if (accessedDataOffset < 0) {
            throw new IllegalArgumentException("Reading outside array bounds.");
        }

        if (accessedDataOffset % runtimeIndexScale != 0 || accessBytes != runtimeIndexScale) {
            /* The read does not access a single array element. */
            return hConstantReflection.readArrayUnaligned(array, accessBytes, accessedDataOffset, runtimeIndexScale);
        } else {
            /* The read accesses a single array element. */
            long index = accessedDataOffset / runtimeIndexScale;
            if (index >= array.getLength()) {
                throw new IllegalArgumentException("Reading after last array element.");
            }

            assert index >= 0;
            JavaConstant result = hConstantReflection.readArrayElement(base, NumUtil.safeToInt(index));
            if (result == null) {
                throw new IllegalArgumentException("Could not read array element.");
            }
            return result;
        }
    }
}
