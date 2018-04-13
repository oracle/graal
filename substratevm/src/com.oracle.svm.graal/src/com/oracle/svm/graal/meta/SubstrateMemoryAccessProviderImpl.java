/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;

import com.oracle.svm.core.config.ConfigurationValues;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.graal.meta.SubstrateMemoryAccessProvider;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

public final class SubstrateMemoryAccessProviderImpl implements SubstrateMemoryAccessProvider {

    public static final SubstrateMemoryAccessProviderImpl SINGLETON = new SubstrateMemoryAccessProviderImpl();

    @Platforms(Platform.HOSTED_ONLY.class)
    private SubstrateMemoryAccessProviderImpl() {
    }

    public JavaConstant readUnsafeConstant(JavaKind kind, JavaConstant base, long displacement) {
        if (kind == JavaKind.Object) {
            return readObjectConstant(base, displacement);
        }
        return readPrimitiveConstant(kind, base, displacement, kind.getByteCount() * Byte.SIZE);
    }

    @Override
    public JavaConstant readObjectConstant(Constant baseConstant, long displacement) {
        return readObjectConstant(baseConstant, displacement, false);
    }

    @Override
    public JavaConstant readNarrowObjectConstant(Constant baseConstant, long displacement) {
        return readObjectConstant(baseConstant, displacement, true);
    }

    private static JavaConstant readObjectConstant(Constant baseConstant, long displacement, boolean requireCompressed) {
        SignedWord offset = WordFactory.signed(displacement);

        if (baseConstant instanceof SubstrateObjectConstant) { // always compressed (if enabled)
            assert !requireCompressed || ReferenceAccess.singleton().haveCompressedReferences();
            Object baseObject = SubstrateObjectConstant.asObject(baseConstant);
            assert baseObject != null : "SubstrateObjectConstant does not wrap null value";
            SubstrateMetaAccess metaAccess = SubstrateMetaAccess.singleton();
            ResolvedJavaType baseObjectType = metaAccess.lookupJavaType(baseObject.getClass());
            checkRead(JavaKind.Object, displacement, baseObjectType, baseObject);
            Object rawValue = BarrieredAccess.readObject(baseObject, offset);
            return SubstrateObjectConstant.forObject(rawValue, requireCompressed);
        }
        if (baseConstant instanceof PrimitiveConstant) { // never compressed
            assert !requireCompressed;

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
            return SubstrateObjectConstant.forObject(rawValue, false);
        }
        return null;
    }

    private static void checkRead(JavaKind kind, long displacement, ResolvedJavaType type, Object object) {
        if (kind != JavaKind.Object) {
            throw unimplemented();
        }

        if (type.isArray()) {
            ResolvedJavaType componentType = type.getComponentType();
            JavaKind componentKind = componentType.getJavaKind();
            final int headerSize = ConfigurationValues.getObjectLayout().getArrayBaseOffset(componentKind);
            int sizeOfElement = ConfigurationValues.getObjectLayout().getArrayIndexScale(componentKind);
            int length = BarrieredAccess.readInt(object, ConfigurationValues.getObjectLayout().getArrayLengthOffset());
            long arrayEnd = ConfigurationValues.getObjectLayout().getArraySize(componentKind, length);
            if (displacement < 0 || displacement > (arrayEnd - sizeOfElement)) {
                int index = (int) ((displacement - headerSize) / sizeOfElement);
                throw new IllegalArgumentException("Unsafe array access: reading element of kind " + kind +
                                " at offset " + displacement + " (index ~ " + index + ") in " +
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
        SignedWord offset = WordFactory.signed(displacement);
        long rawValue;

        if (baseConstant instanceof SubstrateObjectConstant) {
            Object baseObject = ((SubstrateObjectConstant) baseConstant).getObject();
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
                    throw shouldNotReachHere();
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
                    throw shouldNotReachHere();
            }

        } else {
            return null;
        }
        return toConstant(kind, rawValue);
    }

    private static JavaConstant toConstant(JavaKind kind, long rawValue) {
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
                throw shouldNotReachHere();
        }
    }
}
