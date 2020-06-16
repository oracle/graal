/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.query;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;

import com.oracle.svm.core.c.enums.EnumArrayLookup;
import com.oracle.svm.core.c.enums.EnumMapLookup;
import com.oracle.svm.core.c.enums.EnumNoLookup;
import com.oracle.svm.core.c.enums.EnumRuntimeData;
import com.oracle.svm.core.c.struct.CInterfaceLocationIdentity;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.ConstantInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumConstantInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.c.info.EnumValueInfo;
import com.oracle.svm.hosted.c.info.NativeCodeInfo;
import com.oracle.svm.hosted.c.info.SizableInfo;
import com.oracle.svm.hosted.c.info.SizableInfo.ElementKind;
import com.oracle.svm.hosted.c.info.StructBitfieldInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class SizeAndSignednessVerifier extends NativeInfoTreeVisitor {

    private SizeAndSignednessVerifier(NativeLibraries nativeLibs) {
        super(nativeLibs);
    }

    public static void verify(NativeLibraries nativeLibs, NativeCodeInfo nativeCodeInfo) {
        SizeAndSignednessVerifier verifier = new SizeAndSignednessVerifier(nativeLibs);
        nativeCodeInfo.accept(verifier);
    }

    @Override
    protected void visitConstantInfo(ConstantInfo constantInfo) {
        if (constantInfo.getKind() != ElementKind.STRING && constantInfo.getKind() != ElementKind.BYTEARRAY) {
            ResolvedJavaMethod method = constantInfo.getAnnotatedElement();
            ResolvedJavaType returnType = AccessorInfo.getReturnType(method);
            checkSizeAndSignedness(constantInfo, returnType, method, true);
        }
    }

    @Override
    protected void visitStructFieldInfo(StructFieldInfo structFieldInfo) {
        checkAccessorLocationIdentity(structFieldInfo.getChildren());
        if (structFieldInfo.getAnyAccessorInfo().hasUniqueLocationIdentity()) {
            structFieldInfo.setLocationIdentity(new CInterfaceLocationIdentity(structFieldInfo.getParent().getName() + "." + structFieldInfo.getName()));
        }
        super.visitStructFieldInfo(structFieldInfo);
    }

    @Override
    protected void visitStructBitfieldInfo(StructBitfieldInfo structBitfieldInfo) {
        checkAccessorLocationIdentity(structBitfieldInfo.getChildren());
        super.visitStructBitfieldInfo(structBitfieldInfo);
    }

    private void checkAccessorLocationIdentity(List<ElementInfo> children) {
        AccessorInfo firstAccessorInfo = null;
        for (ElementInfo child : children) {
            if (child instanceof AccessorInfo) {
                AccessorInfo accessorInfo = (AccessorInfo) child;
                if (firstAccessorInfo == null) {
                    firstAccessorInfo = accessorInfo;
                } else {
                    if (accessorInfo.hasLocationIdentityParameter() != firstAccessorInfo.hasLocationIdentityParameter()) {
                        addError("All accessors for a field must agree on LocationIdentity parameter", firstAccessorInfo, accessorInfo);
                    } else if (accessorInfo.hasUniqueLocationIdentity() != firstAccessorInfo.hasUniqueLocationIdentity()) {
                        addError("All accessors for a field must agree on @" + UniqueLocationIdentity.class.getSimpleName() + " annotation", firstAccessorInfo, accessorInfo);
                    }
                }
            }
        }
    }

    @Override
    protected void visitAccessorInfo(AccessorInfo accessorInfo) {
        ResolvedJavaMethod method = accessorInfo.getAnnotatedElement();
        ResolvedJavaType returnType = accessorInfo.getReturnType();

        if (accessorInfo.getParent() instanceof StructBitfieldInfo) {
            StructBitfieldInfo bitfieldInfo = (StructBitfieldInfo) accessorInfo.getParent();
            switch (accessorInfo.getAccessorKind()) {
                case GETTER:
                case SETTER:
                    checkSignedness(bitfieldInfo.isUnsigned(), returnType, method);
                    break;
                default:
                    assert false;
            }
        } else {
            SizableInfo sizableInfo = (SizableInfo) accessorInfo.getParent();
            switch (accessorInfo.getAccessorKind()) {
                case ADDRESS:
                    assert nativeLibs.isPointerBase(returnType);
                    break;
                case OFFSET:
                    assert returnType.getJavaKind().isNumericInteger() || nativeLibs.isUnsigned(returnType);
                    break;
                case GETTER:
                    checkSizeAndSignedness(sizableInfo, returnType, method, true);
                    break;
                case SETTER:
                    assert returnType.getJavaKind() == JavaKind.Void;
                    ResolvedJavaType valueType = accessorInfo.getValueParameterType();
                    checkSizeAndSignedness(sizableInfo, valueType, method, false);
                    break;
            }
        }
    }

    @Override
    protected void visitEnumInfo(EnumInfo enumInfo) {
        super.visitEnumInfo(enumInfo);

        Map<Enum<?>, Long> javaToC = new HashMap<>();
        Map<Long, Enum<?>> cToJava = new HashMap<>();

        @SuppressWarnings("rawtypes")
        Class<? extends Enum> enumClass = null;
        long minLookupValue = Long.MAX_VALUE;
        long maxLookupValue = Long.MIN_VALUE;

        for (ElementInfo child : enumInfo.getChildren()) {
            if (child instanceof EnumConstantInfo) {
                EnumConstantInfo valueInfo = (EnumConstantInfo) child;
                long cValue = (Long) valueInfo.getValueInfo().getProperty();
                Enum<?> javaValue = valueInfo.getEnumValue();

                assert enumClass == null || enumClass == javaValue.getClass();
                enumClass = javaValue.getClass();

                assert javaToC.get(javaValue) == null;
                javaToC.put(javaValue, Long.valueOf(cValue));

                if (enumInfo.getNeedsLookup() && valueInfo.getIncludeInLookup()) {
                    if (cToJava.get(cValue) != null) {
                        addError("C value is not unique, so reverse lookup from C to Java is not possible: " + cToJava.get(cValue) + " and " + javaValue + " hava same C value " + cValue,
                                        valueInfo.getAnnotatedElement());
                    }
                    cToJava.put(cValue, javaValue);
                    minLookupValue = Math.min(minLookupValue, cValue);
                    maxLookupValue = Math.max(maxLookupValue, cValue);
                }
            }
        }

        long[] javaToCArray = new long[javaToC.size()];
        for (Map.Entry<Enum<?>, Long> entry : javaToC.entrySet()) {
            int idx = entry.getKey().ordinal();
            assert idx >= 0 && idx < javaToCArray.length && javaToCArray[idx] == 0 : "ordinal values are defined as unique and consecutive";
            javaToCArray[idx] = entry.getValue();
        }

        EnumRuntimeData runtimeData;
        if (cToJava.size() > 0) {
            assert minLookupValue <= maxLookupValue;
            long spread = maxLookupValue - minLookupValue;
            assert spread >= cToJava.size() - 1;

            /*
             * We have a choice between an array-based lookup and keeping the HashMap. Since HashMap
             * has a quite high memory footprint, an array is more compact even when most array
             * elements are null.
             */
            if (spread < cToJava.size() * 5L && spread >= 0 && spread < Integer.MAX_VALUE) {
                long offset = minLookupValue;
                Enum<?>[] cToJavaArray = (Enum[]) Array.newInstance(enumClass, (int) spread + 1);

                for (Map.Entry<Long, Enum<?>> entry : cToJava.entrySet()) {
                    long idx = entry.getKey() - offset;
                    assert idx >= 0 && idx < cToJavaArray.length;
                    assert cToJavaArray[(int) idx] == null;
                    cToJavaArray[(int) idx] = entry.getValue();
                }

                runtimeData = new EnumArrayLookup(javaToCArray, offset, cToJavaArray);
            } else {
                runtimeData = new EnumMapLookup(javaToCArray, cToJava);
            }
        } else {
            runtimeData = new EnumNoLookup(javaToCArray);
        }
        enumInfo.setRuntimeData(runtimeData);
    }

    @Override
    protected void visitEnumValueInfo(EnumValueInfo valueInfo) {
        ResolvedJavaMethod method = valueInfo.getAnnotatedElement();
        ResolvedJavaType returnType = AccessorInfo.getReturnType(method);

        EnumInfo enumInfo = (EnumInfo) valueInfo.getParent();
        for (ElementInfo info : enumInfo.getChildren()) {
            if (info instanceof EnumConstantInfo) {
                EnumConstantInfo constantInfo = (EnumConstantInfo) info;
                checkSizeAndSignedness(constantInfo, returnType, method, true);
            }
        }
    }

    private void checkSizeAndSignedness(SizableInfo sizableInfo, ResolvedJavaType type, ResolvedJavaMethod method, boolean isReturn) {
        int declaredSize = getSizeInBytes(type);
        int actualSize = sizableInfo.isObject() ? getSizeInBytes(JavaKind.Object) : sizableInfo.getSizeInfo().getProperty();
        if (declaredSize != actualSize) {
            Class<? extends Annotation> supressionAnnotation = (declaredSize > actualSize) ^ isReturn ? AllowNarrowingCast.class : AllowWideningCast.class;
            if (method.getAnnotation(supressionAnnotation) == null) {
                addError("Type " + type.toJavaName(false) + " has a size of " + declaredSize + " bytes, but accessed C value has a size of " + actualSize +
                                " bytes; to suppress this error, use the annotation @" + supressionAnnotation.getSimpleName(), method);
            }
        }

        checkSignedness(sizableInfo.isUnsigned(), type, method);
    }

    private void checkSignedness(boolean isUnsigned, ResolvedJavaType type, ResolvedJavaMethod method) {
        if (isSigned(type)) {
            if (isUnsigned) {
                addError("Type " + type.toJavaName(false) + " is signed, but accessed C value is unsigned", method);
            }
        } else if (nativeLibs.isWordBase(type)) {
            /* every Word type other than Signed is assumed to be unsigned. */
            if (!isUnsigned) {
                addError("Type " + type.toJavaName(false) + " is unsigned, but accessed C value is signed", method);
            }
        }
    }
}
