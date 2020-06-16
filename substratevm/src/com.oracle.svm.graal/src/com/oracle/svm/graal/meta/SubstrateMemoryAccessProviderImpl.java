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
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.graal.meta.SubstrateMemoryAccessProvider;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

public final class SubstrateMemoryAccessProviderImpl implements SubstrateMemoryAccessProvider {

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();
    public static final SubstrateMemoryAccessProviderImpl SINGLETON = new SubstrateMemoryAccessProviderImpl();

    @Platforms(Platform.HOSTED_ONLY.class)
    private SubstrateMemoryAccessProviderImpl() {
    }

    static JavaConstant readUnsafeConstant(JavaKind kind, JavaConstant base, long displacement, boolean isVolatile) {
        if (kind == JavaKind.Object) {
            return readObjectConstant(base, displacement, null, isVolatile);
        }
        return readPrimitiveConstant(kind, base, displacement, kind.getByteCount() * Byte.SIZE, isVolatile);
    }

    @Override
    public JavaConstant readObjectConstant(Constant baseConstant, long displacement) {
        return readObjectConstant(baseConstant, displacement, null, false);
    }

    @Override
    public JavaConstant readNarrowObjectConstant(Constant baseConstant, long displacement, CompressEncoding encoding) {
        return readObjectConstant(baseConstant, displacement, encoding, false);
    }

    private static JavaConstant readObjectConstant(Constant baseConstant, long displacement, CompressEncoding compressedEncoding, boolean isVolatile) {
        SignedWord offset = WordFactory.signed(displacement);

        if (baseConstant instanceof SubstrateObjectConstant) { // always compressed (if enabled)
            if (compressedEncoding != null) {
                assert ReferenceAccess.singleton().haveCompressedReferences();
                if (!compressedEncoding.equals(ReferenceAccess.singleton().getCompressEncoding())) {
                    return null; // read with non-default compression not implemented
                }
            }
            Object baseObject = SubstrateObjectConstant.asObject(baseConstant);
            assert baseObject != null : "SubstrateObjectConstant does not wrap null value";
            SubstrateMetaAccess metaAccess = SubstrateMetaAccess.singleton();
            ResolvedJavaType baseObjectType = metaAccess.lookupJavaType(baseObject.getClass());
            checkRead(JavaKind.Object, displacement, baseObjectType, baseObject);
            Object rawValue = BarrieredAccess.readObject(baseObject, offset);
            if (isVolatile) {
                UNSAFE.loadFence();
            }
            return SubstrateObjectConstant.forObject(rawValue, (compressedEncoding != null));
        }
        if (baseConstant instanceof PrimitiveConstant) { // never compressed
            assert compressedEncoding == null;

            PrimitiveConstant prim = (PrimitiveConstant) baseConstant;
            if (!prim.getJavaKind().isNumericInteger()) {
                return null;
            }
            Word baseAddress = WordFactory.unsigned(prim.asLong());
            if (baseAddress.equal(0)) {
                return null;
            }
            Word address = baseAddress.add(offset);
            Object rawValue = ReferenceAccess.singleton().readObjectAt(address, false);
            if (isVolatile) {
                UNSAFE.loadFence();
            }
            return SubstrateObjectConstant.forObject(rawValue, false);
        }
        return null;
    }

    private static void checkRead(JavaKind kind, long displacement, ResolvedJavaType type, Object object) {
        if (kind != JavaKind.Object) {
            throw VMError.unimplemented();
        }

        if (type.isArray()) {
            int length = KnownIntrinsics.readArrayLength(object);
            if (length < 1) {
                throw new IllegalArgumentException("Unsafe array access: reading element of kind " + kind +
                                " at offset " + displacement + " from zero-sized array " +
                                type.toJavaName());
            }
            int encoding = KnownIntrinsics.readHub(object).getLayoutEncoding();
            UnsignedWord unsignedDisplacement = WordFactory.unsigned(displacement);
            UnsignedWord maxDisplacement = LayoutEncoding.getArrayElementOffset(encoding, length - 1);
            if (displacement < 0 || maxDisplacement.belowThan(unsignedDisplacement)) {
                int elementSize = LayoutEncoding.getArrayIndexScale(encoding);
                UnsignedWord index = unsignedDisplacement.subtract(LayoutEncoding.getArrayBaseOffset(encoding)).unsignedDivide(elementSize);
                throw new IllegalArgumentException("Unsafe array access: reading element of kind " + kind +
                                " at offset " + displacement + " (index ~ " + index.rawValue() + ") in " +
                                type.toJavaName() + " object of length " + length);
            }
        } else {
            ResolvedJavaField field = type.findInstanceFieldWithOffset(displacement, JavaKind.Object);
            if (field == null) {
                throw new IllegalArgumentException("Unsafe object access: field not found for read of kind Object" +
                                " at offset " + displacement + " in " + type.toJavaName() + " object");
            }
            if (field.getJavaKind() != JavaKind.Object) {
                throw new IllegalArgumentException("Unsafe object access: field " + field.format("%H.%n:%T") + " not of expected kind Object" +
                                " at offset " + displacement + " in " + type.toJavaName() + " object");
            }
        }
    }

    @Override
    public JavaConstant readPrimitiveConstant(JavaKind kind, Constant baseConstant, long displacement, int bits) {
        return readPrimitiveConstant(kind, baseConstant, displacement, bits, false);
    }

    private static JavaConstant readPrimitiveConstant(JavaKind kind, Constant baseConstant, long displacement, int bits, boolean isVolatile) {
        SignedWord offset = WordFactory.signed(displacement);
        long rawValue;

        if (baseConstant instanceof SubstrateObjectConstant) {
            Object baseObject = SubstrateObjectConstant.asObject(baseConstant);
            assert baseObject != null : "SubstrateObjectConstant does not wrap null value";

            switch (bits) {
                case Byte.SIZE:
                    rawValue = BarrieredAccess.readByte(baseObject, offset);
                    break;
                case Short.SIZE:
                    rawValue = BarrieredAccess.readShort(baseObject, offset);
                    break;
                case Integer.SIZE:
                    rawValue = BarrieredAccess.readInt(baseObject, offset);
                    break;
                case Long.SIZE:
                    rawValue = BarrieredAccess.readLong(baseObject, offset);
                    break;
                default:
                    throw VMError.shouldNotReachHere();
            }

        } else if (baseConstant instanceof PrimitiveConstant) {
            PrimitiveConstant prim = (PrimitiveConstant) baseConstant;
            if (!prim.getJavaKind().isNumericInteger()) {
                return null;
            }
            Pointer basePointer = WordFactory.unsigned(prim.asLong());
            if (basePointer.equal(0)) {
                return null;
            }

            switch (bits) {
                case Byte.SIZE:
                    rawValue = basePointer.readByte(offset);
                    break;
                case Short.SIZE:
                    rawValue = basePointer.readShort(offset);
                    break;
                case Integer.SIZE:
                    rawValue = basePointer.readInt(offset);
                    break;
                case Long.SIZE:
                    rawValue = basePointer.readLong(offset);
                    break;
                default:
                    throw VMError.shouldNotReachHere();
            }

        } else {
            return null;
        }
        if (isVolatile) {
            UNSAFE.loadFence();
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
