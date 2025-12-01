/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.SignedWord;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.meta.SubstrateMemoryAccessProvider;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.word.BarrieredAccess;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Provides memory access during runtime compilation.
 * 
 * Note that the implementation must not assume that the base and displacement constants are valid
 * matching pairs. The compiler performs constant folding as soon as the input nodes are constants.
 * When the folded memory access is in dead code, then the displacement may point outside the base
 * object or to a field with the wrong type. Careful checking of the arguments is necessary,
 * otherwise the compiler can crash.
 *
 * All reads are treated as volatile for simplicity as those functions can be used both for reading
 * Java fields declared as volatile as well as for constant folding Unsafe.get* methods with
 * volatile semantics.
 */
public final class SubstrateMemoryAccessProviderImpl implements SubstrateMemoryAccessProvider {
    public static final SubstrateMemoryAccessProviderImpl SINGLETON = new SubstrateMemoryAccessProviderImpl();

    @Platforms(Platform.HOSTED_ONLY.class)
    private SubstrateMemoryAccessProviderImpl() {
    }

    @Override
    public JavaConstant readObjectConstant(Constant baseConstant, long displacement) {
        return readObjectChecked(baseConstant, displacement, null);
    }

    @Override
    public JavaConstant readNarrowObjectConstant(Constant baseConstant, long displacement, CompressEncoding encoding) {
        return readObjectChecked(baseConstant, displacement, encoding);
    }

    /**
     * Object references may only be read when we are sure that the loaded value points to a valid
     * object. Otherwise, the GC will segfault. So we allow the read only when we find an instance
     * field with the matching offset and type; or when accessing an Object[] array at an in-range
     * and correctly aligned position.
     */
    private static JavaConstant readObjectChecked(Constant baseConstant, long displacement, CompressEncoding compressedEncoding) {
        if (compressedEncoding != null) {
            assert ReferenceAccess.singleton().haveCompressedReferences();
            if (!compressedEncoding.equals(ReferenceAccess.singleton().getCompressEncoding())) {
                throw new IllegalArgumentException("Reading with non-default compression is not supported.");
            }
        }

        if (!(baseConstant instanceof SubstrateObjectConstant)) {
            throw new IllegalArgumentException("Base " + baseConstant.getClass() + " is not supported.");
        }
        Object baseObject = SubstrateObjectConstant.asObject(baseConstant);
        if (baseObject == null) {
            throw new IllegalArgumentException("Base is null.");
        } else if (displacement < 0 || Word.unsigned(displacement + ConfigurationValues.getObjectLayout().getReferenceSize()).aboveThan(LayoutEncoding.getMomentarySizeFromObject(baseObject))) {
            throw new IllegalArgumentException("Reading outside object bounds.");
        }

        SubstrateType baseObjectType = SubstrateMetaAccess.singleton().lookupJavaType(baseObject.getClass());
        if (baseObjectType.isInstanceClass()) {
            SubstrateField field = baseObjectType.findInstanceFieldWithOffset(displacement, JavaKind.Object);
            if (field == null) {
                throw new IllegalArgumentException("Can't find field at displacement " + displacement + " in object of type " + baseObjectType.toJavaName() + ".");
            } else if (field.getStorageKind() != JavaKind.Object) {
                throw new IllegalArgumentException("Field at displacement " + displacement + " in object of type " + baseObjectType.toJavaName() +
                                " is " + field.getStorageKind() + " but expected Object.");
            }
        } else {
            int layoutEncoding = baseObjectType.getHub().getLayoutEncoding();
            if (baseObject instanceof Object[]) {
                assert LayoutEncoding.isObjectArray(layoutEncoding);
                if (displacement < LayoutEncoding.getArrayBaseOffsetAsInt(layoutEncoding)) {
                    throw new IllegalArgumentException("Reading from array header.");
                } else if (Word.unsigned(displacement).aboveOrEqual(LayoutEncoding.getArrayElementOffset(layoutEncoding, ((Object[]) baseObject).length))) {
                    throw new IllegalArgumentException("Reading after last array element.");
                } else if ((displacement & (LayoutEncoding.getArrayIndexScale(layoutEncoding) - 1)) != 0) {
                    throw new IllegalArgumentException("Misaligned object read from array.");
                }
            } else if (LayoutEncoding.isPrimitiveArray(layoutEncoding)) {
                throw new IllegalArgumentException("Can't read objects from primitive array.");
            } else {
                throw VMError.shouldNotReachHere("Unexpected object type: " + baseObjectType.toJavaName());
            }
        }

        return readObjectUnchecked(baseObject, displacement, compressedEncoding != null, true);
    }

    static JavaConstant readObjectUnchecked(Object baseObject, long displacement, boolean createCompressedConstant, boolean isVolatile) {
        Object rawValue = isVolatile ? BarrieredAccess.readObjectVolatile(baseObject, Word.signed(displacement)) : BarrieredAccess.readObject(baseObject, Word.signed(displacement));
        return SubstrateObjectConstant.forObject(rawValue, createCompressedConstant);
    }

    @Override
    public JavaConstant readPrimitiveConstant(JavaKind kind, Constant baseConstant, long displacement, int accessBits) {
        assert accessBits % Byte.SIZE == 0 && accessBits > 0 && accessBits <= 64 : accessBits;
        int accessBytes = accessBits / Byte.SIZE;
        return readPrimitiveChecked(kind, baseConstant, displacement, accessBytes);
    }

    /**
     * For primitive constants, we do not need to do a precise type check of the loaded value. The
     * returned constant might be from an unintended field or array element, but that cannot crash
     * the compiler. We need to ensure though that we are only reading within the proper bounds of
     * the object, because a read before/after the object could be in unmapped memory and segfault.
     */
    private static JavaConstant readPrimitiveChecked(JavaKind kind, Constant baseConstant, long displacement, int accessBytes) {
        ObjectLayout ol = ConfigurationValues.getObjectLayout();

        if (!(baseConstant instanceof SubstrateObjectConstant)) {
            throw new IllegalArgumentException("Base " + baseConstant.getClass() + " is not supported.");
        }
        Object baseObject = SubstrateObjectConstant.asObject(baseConstant);
        if (baseObject == null) {
            throw new IllegalArgumentException("Base is null.");
        } else if (displacement < 0 || Word.unsigned(displacement + accessBytes).aboveThan(LayoutEncoding.getMomentarySizeFromObject(baseObject))) {
            throw new IllegalArgumentException("Reading outside object bounds.");
        } else if (Platform.includedIn(Platform.AARCH64.class) && displacement % accessBytes != 0) {
            /* Unaligned volatile accesses may cause segfaults on AArch64. */
            throw new IllegalArgumentException("Read is unaligned.");
        }

        SubstrateType baseObjectType = SubstrateMetaAccess.singleton().lookupJavaType(baseObject.getClass());
        int layoutEncoding = baseObjectType.getHub().getLayoutEncoding();
        if (LayoutEncoding.isPureInstance(layoutEncoding)) {
            /* Field information may be missing, so allow most accesses to instance objects. */
            if (displacement < ol.getFirstFieldOffset()) {
                throw new IllegalArgumentException("Reading from object header.");
            }
        } else if (LayoutEncoding.isHybrid(layoutEncoding)) {
            /* Field information may be missing, so allow most accesses to hybrid objects. */
            if (displacement < ol.getFirstFieldOffset()) {
                throw new IllegalArgumentException("Reading from object header.");
            } else if (Word.unsigned(displacement).aboveOrEqual(LayoutEncoding.getArrayElementOffset(layoutEncoding, ArrayLengthNode.arrayLength(baseObject)))) {
                throw new IllegalArgumentException("Reading after last array element.");
            }
        } else {
            if (LayoutEncoding.isObjectArray(layoutEncoding)) {
                throw new IllegalArgumentException("Reading primitive from object array.");
            } else if (displacement < LayoutEncoding.getArrayBaseOffsetAsInt(layoutEncoding)) {
                throw new IllegalArgumentException("Reading from array header.");
            } else if (Word.unsigned(displacement).aboveOrEqual(LayoutEncoding.getArrayElementOffset(layoutEncoding, ArrayLengthNode.arrayLength(baseObject)))) {
                throw new IllegalArgumentException("Reading after last array element.");
            }
        }

        return readPrimitiveUnchecked(kind, baseObject, displacement, accessBytes, true);
    }

    static JavaConstant readPrimitiveUnchecked(JavaKind kind, Object baseObject, long displacement, int accessBytes, boolean isVolatile) {
        long rawValue = readPrimitiveUnchecked0(baseObject, displacement, accessBytes, isVolatile);
        return toConstant(kind, rawValue);
    }

    private static long readPrimitiveUnchecked0(Object baseObject, long displacement, int accessBytes, boolean isVolatile) {
        SignedWord offset = Word.signed(displacement);
        return switch (accessBytes) {
            case Byte.BYTES ->
                isVolatile ? BarrieredAccess.readByteVolatile(baseObject, offset) : BarrieredAccess.readByte(baseObject, offset);
            case Short.BYTES ->
                isVolatile ? BarrieredAccess.readShortVolatile(baseObject, offset) : BarrieredAccess.readShort(baseObject, offset);
            case Integer.BYTES ->
                isVolatile ? BarrieredAccess.readIntVolatile(baseObject, offset) : BarrieredAccess.readInt(baseObject, offset);
            case Long.BYTES ->
                isVolatile ? BarrieredAccess.readLongVolatile(baseObject, offset) : BarrieredAccess.readLong(baseObject, offset);
            default -> throw VMError.shouldNotReachHereUnexpectedInput(accessBytes); // ExcludeFromJacocoGeneratedReport
        };
    }

    public static JavaConstant toConstant(JavaKind kind, long rawValue) {
        return switch (kind) {
            case Boolean -> JavaConstant.forBoolean(rawValue != 0);
            case Byte -> JavaConstant.forByte((byte) rawValue);
            case Char -> JavaConstant.forChar((char) rawValue);
            case Short -> JavaConstant.forShort((short) rawValue);
            case Int -> JavaConstant.forInt((int) rawValue);
            case Long -> JavaConstant.forLong(rawValue);
            case Float -> JavaConstant.forFloat(Float.intBitsToFloat((int) rawValue));
            case Double -> JavaConstant.forDouble(Double.longBitsToDouble(rawValue));
            default -> throw VMError.shouldNotReachHereUnexpectedInput(kind); // ExcludeFromJacocoGeneratedReport
        };
    }
}
