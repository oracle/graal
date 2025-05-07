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
package com.oracle.svm.hosted.c;

import java.lang.annotation.Annotation;
import java.util.EnumSet;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.impl.CConstantValueSupport;

import com.oracle.svm.core.c.enums.CEnumRuntimeData;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.info.ConstantInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.phases.CInterfaceInvocationPlugin;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@Platforms(Platform.HOSTED_ONLY.class)
public final class CConstantValueSupportImpl implements CConstantValueSupport, LayeredImageSingleton {
    private final NativeLibraries nativeLibraries;
    private final MetaAccessProvider metaAccess;

    public CConstantValueSupportImpl(NativeLibraries nativeLibraries) {
        this.nativeLibraries = nativeLibraries;
        this.metaAccess = nativeLibraries.getMetaAccess();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCConstantValue(Class<?> declaringClass, String methodName, Class<T> returnType) {
        ResolvedJavaMethod method = getAnnotatedMethod(declaringClass, methodName, CConstant.class);
        ConstantInfo constantInfo = (ConstantInfo) nativeLibraries.findElementInfo(method);
        Object value = constantInfo.getValue();

        switch (constantInfo.getKind()) {
            case INTEGER, POINTER -> {
                return (T) CInterfaceInvocationPlugin.convertCIntegerToMethodReturnType(nativeLibraries, returnType, (long) value, constantInfo.getSizeInBytes() * Byte.SIZE,
                                constantInfo.isUnsigned());
            }
            case FLOAT -> {
                if (returnType == Float.class) {
                    return returnType.cast(((Double) value).floatValue());
                }
                return returnType.cast(value);
            }
            case STRING, BYTEARRAY -> {
                return returnType.cast(value);
            }
            default -> throw VMError.shouldNotReachHere("Unexpected returnType: " + returnType.getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCEnumValue(Enum<?> cEnum, String methodName) {
        Class<?> declaringType = cEnum.getDeclaringClass();
        if (declaringType.getAnnotation(CEnum.class) == null) {
            throw VMError.shouldNotReachHere("Type " + declaringType + " is not annotated with @" + ClassUtil.getUnqualifiedName(CEnum.class));
        }

        ResolvedJavaMethod method = getAnnotatedMethod(declaringType, methodName, CEnumValue.class);
        ResolvedJavaType enumType = metaAccess.lookupJavaType(declaringType);
        EnumInfo enumInfo = (EnumInfo) nativeLibraries.findElementInfo(enumType);

        ResolvedJavaType returnType = (ResolvedJavaType) method.getSignature().getReturnType(method.getDeclaringClass());
        return (T) getCEnumValue(cEnum, returnType, enumInfo.getRuntimeData());
    }

    private Object getCEnumValue(Enum<?> cEnum, ResolvedJavaType returnType, CEnumRuntimeData data) {
        if (!nativeLibraries.isIntegerType(returnType)) {
            throw VMError.shouldNotReachHere("Unsupported return type: " + returnType);
        }

        if (nativeLibraries.isWordBase(returnType)) {
            if (nativeLibraries.isSigned(returnType)) {
                return data.enumToSignedWord(cEnum);
            }
            return data.enumToUnsignedWord(cEnum);
        }

        JavaKind returnKind = returnType.getJavaKind();
        return switch (returnKind) {
            case Boolean -> data.enumToBoolean(cEnum);
            case Byte -> data.enumToByte(cEnum);
            case Short -> data.enumToShort(cEnum);
            case Char -> data.enumToChar(cEnum);
            case Int -> data.enumToInt(cEnum);
            case Long -> data.enumToLong(cEnum);
            default -> throw VMError.shouldNotReachHere("Unsupported return type: " + returnType);
        };
    }

    private ResolvedJavaMethod getAnnotatedMethod(Class<?> declaringClass, String methodName, Class<? extends Annotation> annotationClass) {
        ResolvedJavaMethod method;
        try {
            method = metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(declaringClass, methodName));
        } catch (ReflectionUtil.ReflectionUtilError e) {
            throw VMError.shouldNotReachHere("Method not found: " + declaringClass.getName() + "." + methodName);
        }

        if (method.getAnnotation(annotationClass) == null) {
            throw VMError.shouldNotReachHere("Method " + declaringClass.getName() + "." + methodName + " is not annotated with @" + ClassUtil.getUnqualifiedName(annotationClass));
        }
        return method;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        return PersistFlags.NOTHING;
    }
}
