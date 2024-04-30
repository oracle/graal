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
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumConstant;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;

import com.oracle.svm.core.c.enums.CEnumArrayLookup;
import com.oracle.svm.core.c.enums.CEnumMapLookup;
import com.oracle.svm.core.c.enums.CEnumNoLookup;
import com.oracle.svm.core.c.enums.CEnumRuntimeData;
import com.oracle.svm.core.c.struct.CInterfaceLocationIdentity;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.ConstantInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumConstantInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.c.info.NativeCodeInfo;
import com.oracle.svm.hosted.c.info.SizableInfo;
import com.oracle.svm.hosted.c.info.SizableInfo.ElementKind;
import com.oracle.svm.hosted.c.info.StructBitfieldInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.vm.ci.code.CodeUtil;
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
            verifySize(constantInfo, returnType, method, true);
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
            if (child instanceof AccessorInfo accessorInfo) {
                if (accessorInfo.getAccessorKind() != AccessorInfo.AccessorKind.OFFSET) {
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
    }

    @Override
    protected void visitAccessorInfo(AccessorInfo accessorInfo) {
        ResolvedJavaMethod method = accessorInfo.getAnnotatedElement();
        ResolvedJavaType returnType = accessorInfo.getReturnType();

        if (accessorInfo.getParent() instanceof StructBitfieldInfo) {
            assert accessorInfo.getAccessorKind() == AccessorInfo.AccessorKind.GETTER || accessorInfo.getAccessorKind() == AccessorInfo.AccessorKind.SETTER;
        } else {
            SizableInfo sizableInfo = (SizableInfo) accessorInfo.getParent();
            switch (accessorInfo.getAccessorKind()) {
                case ADDRESS -> {
                    assert nativeLibs.isPointerBase(returnType);
                }
                case OFFSET -> {
                    assert nativeLibs.isIntegerType(returnType);
                }
                case GETTER -> {
                    verifySize(sizableInfo, returnType, method, true);
                    verifySignedness(sizableInfo, returnType, method);
                }
                case SETTER -> {
                    assert returnType.getJavaKind() == JavaKind.Void;
                    ResolvedJavaType valueType = accessorInfo.getValueParameterType();
                    verifySize(sizableInfo, valueType, method, false);
                    verifySignedness(sizableInfo, valueType, method);
                }
            }
        }
    }

    @Override
    protected void visitEnumInfo(EnumInfo enumInfo) {
        verifyCEnumValueMethodReturnTypes(enumInfo);
        verifyCEnumLookupMethodArguments(enumInfo);

        CEnumRuntimeData runtimeData = createCEnumRuntimeData(enumInfo);
        enumInfo.setRuntimeData(runtimeData);

        super.visitEnumInfo(enumInfo);
    }

    private CEnumRuntimeData createCEnumRuntimeData(EnumInfo enumInfo) {
        Map<Enum<?>, Long> javaToC = new HashMap<>();
        Map<Long, Enum<?>> cToJava = new HashMap<>();

        long minLookupValue = Long.MAX_VALUE;
        long maxLookupValue = Long.MIN_VALUE;

        /* Collect all the CEnumConstants. */
        for (ElementInfo child : enumInfo.getChildren()) {
            if (child instanceof EnumConstantInfo valueInfo) {
                long cValue = limitValueToEnumBytes(enumInfo, valueInfo);
                Enum<?> javaValue = valueInfo.getEnumValue();
                assert !javaToC.containsKey(javaValue);
                javaToC.put(javaValue, cValue);

                if (enumInfo.hasCEnumLookupMethods() && valueInfo.getIncludeInLookup()) {
                    if (cToJava.containsKey(cValue)) {
                        addError("C value is not unique, so reverse lookup from C to Java is not possible: " + cToJava.get(cValue) + " and " + javaValue + " have the same C value " + cValue + ". " +
                                        "Please exclude one of the values from the lookup (see @" + CEnumConstant.class.getSimpleName() + " for more details).", valueInfo.getAnnotatedElement());
                    }
                    cToJava.put(cValue, javaValue);
                    minLookupValue = Math.min(minLookupValue, cValue);
                    maxLookupValue = Math.max(maxLookupValue, cValue);
                }
            }
        }

        /* Create a long[] that contains the values for the C enum constants. */
        long[] javaToCArray = new long[javaToC.size()];
        for (Map.Entry<Enum<?>, Long> entry : javaToC.entrySet()) {
            int idx = entry.getKey().ordinal();
            assert idx >= 0 && idx < javaToCArray.length && javaToCArray[idx] == 0 : "ordinal values are defined as unique and consecutive";
            javaToCArray[idx] = entry.getValue();
        }

        int enumBytesInC = enumInfo.getSizeInBytes();
        boolean isCEnumUnsigned = enumInfo.isUnsigned();
        if (cToJava.isEmpty()) {
            return new CEnumNoLookup(javaToCArray, enumBytesInC, isCEnumUnsigned);
        }

        /* Compute how spread out the C enum values are. */
        assert minLookupValue <= maxLookupValue;
        BigInteger bigIntSpread = BigInteger.valueOf(maxLookupValue).subtract(BigInteger.valueOf(minLookupValue));
        assert bigIntSpread.compareTo(BigInteger.valueOf(cToJava.size() - 1)) >= 0;
        long spread = bigIntSpread.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : bigIntSpread.longValue();

        /*
         * Decide between array- and hash-map-based lookup. A hash map has a high memory footprint,
         * so an array is more compact even if most elements are null.
         */
        if (spread < cToJava.size() * 5L && spread < Integer.MAX_VALUE - 10) {
            int arraySize = NumUtil.safeToInt(spread + 1);
            Enum<?>[] cToJavaArray = new Enum<?>[arraySize];
            for (Map.Entry<Long, Enum<?>> entry : cToJava.entrySet()) {
                int idx = NumUtil.safeToInt(entry.getKey() - minLookupValue);
                assert idx >= 0 && idx < cToJavaArray.length;
                assert cToJavaArray[idx] == null;
                cToJavaArray[idx] = entry.getValue();
            }
            return new CEnumArrayLookup(javaToCArray, enumBytesInC, isCEnumUnsigned, minLookupValue, cToJavaArray);
        }
        return new CEnumMapLookup(javaToCArray, enumBytesInC, isCEnumUnsigned, Map.copyOf(cToJava));
    }

    private void verifyCEnumValueMethodReturnTypes(EnumInfo enumInfo) {
        for (ResolvedJavaMethod method : enumInfo.getCEnumValueMethods()) {
            ResolvedJavaType returnType = AccessorInfo.getReturnType(method);
            verifySize(enumInfo, returnType, method, true);
        }
    }

    private void verifyCEnumLookupMethodArguments(EnumInfo enumInfo) {
        for (ResolvedJavaMethod method : enumInfo.getCEnumLookupMethods()) {
            ResolvedJavaType arg0 = AccessorInfo.getParameterType(method, 0);
            verifySize(enumInfo, arg0, method, false);
        }
    }

    private void verifySize(SizableInfo sizableInfo, ResolvedJavaType type, ResolvedJavaMethod method, boolean isReturn) {
        int declaredSize = getSizeInBytes(type);

        boolean allowNarrowingCast = AnnotationAccess.isAnnotationPresent(method, AllowNarrowingCast.class);
        if (allowNarrowingCast) {
            if (sizableInfo.isObject()) {
                addError(ClassUtil.getUnqualifiedName(AllowNarrowingCast.class) + " cannot be used on fields that have an object type.", method);
            } else if (sizableInfo.getKind() == ElementKind.FLOAT) {
                addError(ClassUtil.getUnqualifiedName(AllowNarrowingCast.class) + " cannot be used on fields of type float or double.", method);
            }
        }

        boolean allowWideningCast = AnnotationAccess.isAnnotationPresent(method, AllowWideningCast.class);
        if (allowWideningCast) {
            if (sizableInfo.isObject()) {
                addError(ClassUtil.getUnqualifiedName(AllowWideningCast.class) + " cannot be used on fields that have an object type.", method);
            } else if (sizableInfo.getKind() == ElementKind.FLOAT) {
                addError(ClassUtil.getUnqualifiedName(AllowWideningCast.class) + " cannot be used on fields of type float or double.", method);
            }
        }

        int actualSize = sizableInfo.isObject() ? getSizeInBytes(JavaKind.Object) : sizableInfo.getSizeInBytes();
        if (declaredSize != actualSize) {
            boolean narrow = declaredSize > actualSize;
            if (isReturn) {
                narrow = !narrow;
            }

            Class<? extends Annotation> suppressionAnnotation = narrow ? AllowNarrowingCast.class : AllowWideningCast.class;
            if (method.getAnnotation(suppressionAnnotation) == null) {
                addError("Type " + type.toJavaName(false) + " has a size of " + declaredSize + " bytes, but accessed C value has a size of " + actualSize +
                                " bytes; to suppress this error, use the annotation @" + ClassUtil.getUnqualifiedName(suppressionAnnotation), method);
            }
        }
    }

    private void verifySignedness(SizableInfo sizableInfo, ResolvedJavaType type, ResolvedJavaMethod method) {
        if (Options.VerifyCInterfaceSignedness.getValue() && sizableInfo.getKind() == ElementKind.INTEGER) {
            boolean isJavaTypeSigned = nativeLibs.isSigned(type);
            boolean isCTypeSigned = sizableInfo.getSignednessInfo().getProperty() == SizableInfo.SignednessValue.SIGNED;
            if (isJavaTypeSigned != isCTypeSigned) {
                addError("Java type " + type.toJavaName(false) + " is " + (isJavaTypeSigned ? "signed" : "unsigned") +
                                ", while the accessed C value is " + (isCTypeSigned ? "signed" : "unsigned"), method);
            }
        }
    }

    /**
     * The value of the constant was already sign- or zero-extended to 64-bit when it was parsed
     * (based on the size and signedness of the constant itself). However, it can happen that the
     * data type of the C enum is smaller than the data type of the constant. Therefore, we
     * explicitly limit the bits of the constant to the bits that fit into the type of the C enum.
     */
    private long limitValueToEnumBytes(EnumInfo enumInfo, EnumConstantInfo constantInfo) {
        int enumBits = enumInfo.getSizeInBytes() * Byte.SIZE;

        long result = constantInfo.getValue();
        if (constantInfo.isUnsigned()) {
            result = CodeUtil.zeroExtend(result, enumBits);
        } else {
            result = CodeUtil.signExtend(result, enumBits);
        }

        if (result != constantInfo.getValue()) {
            addError("The value of a C constant does not fit into the C data type of the @" + CEnum.class.getSimpleName() + " ('" + enumInfo.getName() + "'). " +
                            "Please specify a larger C data type in @" + CEnum.class.getSimpleName() + "(value = \"...\").", enumInfo, constantInfo);
        }
        return result;
    }

    public static class Options {
        /**
         * Only verifies the signedness of accessors at the moment. The signedness of constants and
         * enums in C is often just up to the compiler and therefore not particularly useful.
         */
        @Option(help = "Verify the signedness of Java and C types that are used in the C interface.", type = OptionType.Debug)//
        public static final HostedOptionKey<Boolean> VerifyCInterfaceSignedness = new HostedOptionKey<>(false);
    }
}
