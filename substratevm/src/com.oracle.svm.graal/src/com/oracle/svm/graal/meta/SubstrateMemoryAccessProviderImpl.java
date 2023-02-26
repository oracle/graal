/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.SignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.graal.meta.SubstrateMemoryAccessProvider;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Provides memory access during runtime compilation, so all displacements must be runtime field
 * offset of based on the runtime array base/scale.
 * 
 * Note that the implementation must not assume that the base and displacement constants are valid
 * matching pairs. The compiler performs constant folding as soon as the input nodes are constants.
 * When the folded memory access is in dead code, then the displacement often points outside of the
 * base object or to a field with the wrong type. Careful checking of the arguments is necessary,
 * otherwise the compiler can crash.
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
     * Object constants can only be returned when we are 100% sure that the loaded value is really a
     * valid object. Otherwise the GC will segfault. So we allow the read only when we find an
     * instance field with the matching offset and type; or when accessing an Object[] array at an
     * in-range and correctly aligned position.
     */
    private static JavaConstant readObjectChecked(Constant baseConstant, long displacement, CompressEncoding compressedEncoding) {
        if (compressedEncoding != null) {
            assert ReferenceAccess.singleton().haveCompressedReferences();
            if (!compressedEncoding.equals(ReferenceAccess.singleton().getCompressEncoding())) {
                /* Read with non-default compression not implemented. */
                return null;
            }
        }

        if (!(baseConstant instanceof SubstrateObjectConstant)) {
            return null;
        }
        Object baseObject = SubstrateObjectConstant.asObject(baseConstant);
        if (baseObject == null) {
            /* SubstrateObjectConstant does not wrap null. But we are defensive. */
            return null;
        } else if (displacement <= 0) {
            /* Trying to read before the object, or the hub. No need to look into the object. */
            return null;
        }

        SubstrateType baseObjectType = SubstrateMetaAccess.singleton().lookupJavaType(baseObject.getClass());
        if (baseObjectType.isInstanceClass()) {
            SubstrateField field = baseObjectType.findInstanceFieldWithOffset(displacement, JavaKind.Object);
            if (field == null || field.getStorageKind() != JavaKind.Object) {
                /* Not a valid instance field that has an Object type. */
                return null;
            }
        } else if (baseObject instanceof Object[]) {
            int layoutEncoding = baseObjectType.getHub().getLayoutEncoding();
            assert LayoutEncoding.isObjectArray(layoutEncoding);
            if (displacement < LayoutEncoding.getArrayBaseOffsetAsInt(layoutEncoding)) {
                /* Trying to read before the first array element. */
                return null;
            } else if (WordFactory.unsigned(displacement).aboveOrEqual(LayoutEncoding.getArrayElementOffset(layoutEncoding, ((Object[]) baseObject).length))) {
                /* Trying to read after the last array element. */
                return null;
            } else if ((displacement & (LayoutEncoding.getArrayIndexScale(layoutEncoding) - 1)) != 0) {
                /* Not aligned at the start of an array element. */
                return null;
            }
        } else {
            /* Some other kind of object, for example a primitive array. */
            return null;
        }
        return readObjectUnchecked(baseObject, displacement, compressedEncoding != null, false);
    }

    static JavaConstant readObjectUnchecked(Object baseObject, long displacement, boolean createCompressedConstant, boolean isVolatile) {
        Object rawValue = isVolatile ? BarrieredAccess.readObjectVolatile(baseObject, WordFactory.signed(displacement)) : BarrieredAccess.readObject(baseObject, WordFactory.signed(displacement));
        return SubstrateObjectConstant.forObject(rawValue, createCompressedConstant);
    }

    @Override
    public JavaConstant readPrimitiveConstant(JavaKind kind, Constant baseConstant, long displacement, int bits) {
        return readPrimitiveChecked(kind, baseConstant, displacement, bits);
    }

    /**
     * For primitive constants, we do not need to do a precise type check of the loaded value. The
     * returned constant might be from an unintended field or array element, but that cannot crash
     * the compiler. We need to ensure though that we are only reading within the proper bounds of
     * the object, because a read before/after the object could be in unmapped memory and segfault.
     */
    private static JavaConstant readPrimitiveChecked(JavaKind kind, Constant baseConstant, long displacement, int bits) {
        if (!(baseConstant instanceof SubstrateObjectConstant)) {
            return null;
        }
        Object baseObject = SubstrateObjectConstant.asObject(baseConstant);
        if (baseObject == null) {
            /* SubstrateObjectConstant does not wrap null. But we are defensive. */
            return null;
        } else if (displacement <= 0) {
            /* Trying to read before the object, or the hub. No need to look into the object. */
            return null;
        } else if (WordFactory.unsigned(displacement + bits / 8).aboveThan(LayoutEncoding.getSizeFromObject(baseObject))) {
            /* Trying to read after the end of the object. */
            return null;
        }
        return readPrimitiveUnchecked(kind, baseObject, displacement, bits, false);
    }

    static JavaConstant readPrimitiveUnchecked(JavaKind kind, Object baseObject, long displacement, int bits, boolean isVolatile) {
        SignedWord offset = WordFactory.signed(displacement);
        long rawValue;
        switch (bits) {
            case Byte.SIZE:
                rawValue = isVolatile ? BarrieredAccess.readByteVolatile(baseObject, offset) : BarrieredAccess.readByte(baseObject, offset);
                break;
            case Short.SIZE:
                rawValue = isVolatile ? BarrieredAccess.readShortVolatile(baseObject, offset) : BarrieredAccess.readShort(baseObject, offset);
                break;
            case Integer.SIZE:
                rawValue = isVolatile ? BarrieredAccess.readIntVolatile(baseObject, offset) : BarrieredAccess.readInt(baseObject, offset);
                break;
            case Long.SIZE:
                rawValue = isVolatile ? BarrieredAccess.readLongVolatile(baseObject, offset) : BarrieredAccess.readLong(baseObject, offset);
                break;
            default:
                throw VMError.shouldNotReachHere();
        }
        return toConstant(kind, rawValue);
    }

    public static JavaConstant toConstant(JavaKind kind, long rawValue) {
        switch (kind) {
            case Boolean:
                return JavaConstant.forBoolean(rawValue != 0);
            case Byte:
                return JavaConstant.forByte((byte) rawValue);
            case Char:
                return JavaConstant.forChar((char) rawValue);
            case Short:
                return JavaConstant.forShort((short) rawValue);
            case Int:
                return JavaConstant.forInt((int) rawValue);
            case Long:
                return JavaConstant.forLong(rawValue);
            case Float:
                return JavaConstant.forFloat(Float.intBitsToFloat((int) rawValue));
            case Double:
                return JavaConstant.forDouble(Double.longBitsToDouble(rawValue));
            default:
                throw VMError.shouldNotReachHere();
        }
    }
}
