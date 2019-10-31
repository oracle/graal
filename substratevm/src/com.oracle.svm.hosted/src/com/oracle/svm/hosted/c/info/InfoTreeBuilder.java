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
package com.oracle.svm.hosted.c.info;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.graalvm.compiler.bytecode.BridgeMethodUtils;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumConstant;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.struct.CBitfield;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CFieldOffset;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawFieldAddress;
import org.graalvm.nativeimage.c.struct.RawFieldOffset;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;
import org.graalvm.word.PointerBase;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.c.CTypedef;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.hosted.c.BuiltinDirectives;
import com.oracle.svm.hosted.c.NativeCodeContext;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo.AccessorKind;
import com.oracle.svm.hosted.c.info.SizableInfo.ElementKind;
import com.oracle.svm.hosted.cenum.CEnumCallWrapperMethod;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InfoTreeBuilder {

    private final NativeLibraries nativeLibs;
    private final NativeCodeContext codeCtx;
    private final NativeCodeInfo nativeCodeInfo;

    public InfoTreeBuilder(NativeLibraries nativeLibs, NativeCodeContext codeCtx) {
        this.nativeLibs = nativeLibs;
        this.codeCtx = codeCtx;
        boolean isBuiltin = codeCtx.getDirectives() instanceof BuiltinDirectives;

        String name;
        if (codeCtx.getDirectives() != null) {
            name = codeCtx.getDirectives().getClass().getSimpleName();
        } else {
            StringBuilder nameBuilder = new StringBuilder();
            String sep = "";
            for (String headerFile : codeCtx.getDirectives().getHeaderFiles()) {
                nameBuilder.append(sep).append(headerFile);
                sep = "_";
            }
            name = nameBuilder.toString();
        }
        this.nativeCodeInfo = new NativeCodeInfo(name, codeCtx.getDirectives(), isBuiltin);
    }

    public NativeCodeInfo construct() {
        for (ResolvedJavaMethod method : codeCtx.getConstantAccessors()) {
            createConstantInfo(method);
        }
        for (ResolvedJavaType type : codeCtx.getStructTypes()) {
            createStructInfo(type);
        }
        for (ResolvedJavaType type : codeCtx.getRawStructTypes()) {
            createRawStructInfo(type);
        }
        for (ResolvedJavaType type : codeCtx.getPointerToTypes()) {
            createPointerToInfo(type);
        }
        for (ResolvedJavaType type : codeCtx.getEnumTypes()) {
            createEnumInfo(type);
        }
        return nativeCodeInfo;
    }

    private MetaAccessProvider getMetaAccess() {
        return nativeLibs.getMetaAccess();
    }

    protected void createConstantInfo(ResolvedJavaMethod method) {
        int actualParamCount = getParameterCount(method);
        if (actualParamCount != 0) {
            nativeLibs.addError("Wrong number of parameters: expected 0; found " + actualParamCount, method);
            return;
        }
        ResolvedJavaType returnType = AccessorInfo.getReturnType(method);
        if (returnType.getJavaKind() == JavaKind.Void ||
                        (returnType.getJavaKind() == JavaKind.Object && !nativeLibs.isString(returnType) && !nativeLibs.isByteArray(returnType) && !nativeLibs.isWordBase(returnType))) {
            nativeLibs.addError("Wrong return type: expected a primitive type, String, or a Word type; found " + returnType.toJavaName(true), method);
            return;
        }

        String constantName = getConstantName(method);
        ElementKind elementKind = elementKind(returnType, false);
        ConstantInfo constantInfo = new ConstantInfo(constantName, elementKind, method);
        nativeCodeInfo.adoptChild(constantInfo);
        nativeLibs.registerElementInfo(method, constantInfo);
    }

    private void createPointerToInfo(ResolvedJavaType type) {
        if (!validInterfaceDefinition(type, CPointerTo.class)) {
            return;
        }
        List<AccessorInfo> accessorInfos = new ArrayList<>();

        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
            AccessorKind accessorKind = returnsDeclaringClass(method) ? AccessorKind.ADDRESS : getAccessorKind(method);
            boolean isIndexed = getParameterCount(method) > (accessorKind == AccessorKind.SETTER ? 1 : 0);
            AccessorInfo accessorInfo = new AccessorInfo(method, accessorKind, isIndexed, false, false);
            if (accessorValid(accessorInfo)) {
                accessorInfos.add(accessorInfo);
                nativeLibs.registerElementInfo(method, accessorInfo);
            }
        }

        String typeName = getPointerToTypeName(type);
        String typedefName = getTypedefName(type);
        PointerToInfo pointerToInfo = new PointerToInfo(typeName, typedefName, elementKind(accessorInfos), type);
        pointerToInfo.adoptChildren(accessorInfos);
        nativeCodeInfo.adoptChild(pointerToInfo);
        nativeLibs.registerElementInfo(type, pointerToInfo);
    }

    private static int getParameterCount(ResolvedJavaMethod method) {
        return method.getSignature().getParameterCount(false);
    }

    private static boolean returnsDeclaringClass(ResolvedJavaMethod accessor) {
        return AccessorInfo.getReturnType(accessor).equals(accessor.getDeclaringClass());
    }

    private static AccessorKind getAccessorKind(ResolvedJavaMethod accessor) {
        return accessor.getSignature().getReturnKind() == JavaKind.Void ? AccessorKind.SETTER : AccessorKind.GETTER;
    }

    public static String getTypedefName(ResolvedJavaType type) {
        CTypedef typedefAnnotation = type.getAnnotation(CTypedef.class);
        return typedefAnnotation != null ? typedefAnnotation.name() : null;
    }

    private void createStructInfo(ResolvedJavaType type) {
        if (!validInterfaceDefinition(type, CStruct.class)) {
            return;
        }

        Map<String, List<AccessorInfo>> fieldAccessorInfos = new TreeMap<>();
        Map<String, List<AccessorInfo>> bitfieldAccessorInfos = new TreeMap<>();
        List<AccessorInfo> structAccessorInfos = new ArrayList<>();

        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
            final AccessorInfo accessorInfo;
            final String fieldName;

            CField fieldAnnotation = getMethodAnnotation(method, CField.class);
            CFieldAddress fieldAddressAnnotation = getMethodAnnotation(method, CFieldAddress.class);
            CFieldOffset fieldOffsetAnnotation = getMethodAnnotation(method, CFieldOffset.class);
            CBitfield bitfieldAnnotation = getMethodAnnotation(method, CBitfield.class);
            if (fieldAnnotation != null) {
                accessorInfo = new AccessorInfo(method, getAccessorKind(method), false, hasLocationIdentityParameter(method), hasUniqueLocationIdentity(method));
                fieldName = getStructFieldName(accessorInfo, fieldAnnotation.value());
            } else if (bitfieldAnnotation != null) {
                accessorInfo = new AccessorInfo(method, getAccessorKind(method), false, hasLocationIdentityParameter(method), false);
                fieldName = getStructFieldName(accessorInfo, bitfieldAnnotation.value());
            } else if (fieldAddressAnnotation != null) {
                accessorInfo = new AccessorInfo(method, AccessorKind.ADDRESS, false, false, false);
                fieldName = getStructFieldName(accessorInfo, fieldAddressAnnotation.value());
            } else if (fieldOffsetAnnotation != null) {
                accessorInfo = new AccessorInfo(method, AccessorKind.OFFSET, false, false, false);
                fieldName = getStructFieldName(accessorInfo, fieldOffsetAnnotation.value());
            } else if (returnsDeclaringClass(method)) {
                accessorInfo = new AccessorInfo(method, AccessorKind.ADDRESS, getParameterCount(method) > 0, false, false);
                fieldName = null;
            } else {
                nativeLibs.addError("Unexpected method without annotation", method);
                continue;
            }

            if (accessorValid(accessorInfo)) {
                if (fieldName == null) {
                    structAccessorInfos.add(accessorInfo);
                } else {
                    Map<String, List<AccessorInfo>> map = bitfieldAnnotation != null ? bitfieldAccessorInfos : fieldAccessorInfos;
                    List<AccessorInfo> accessorInfos = map.get(fieldName);
                    if (accessorInfos == null) {
                        accessorInfos = new ArrayList<>();
                        map.put(fieldName, accessorInfos);
                    }
                    accessorInfos.add(accessorInfo);
                }

                nativeLibs.registerElementInfo(method, accessorInfo);
            }
        }

        StructInfo structInfo = StructInfo.create(getStructName(type), type);
        structInfo.adoptChildren(structAccessorInfos);

        for (Map.Entry<String, List<AccessorInfo>> entry : fieldAccessorInfos.entrySet()) {
            StructFieldInfo fieldInfo = new StructFieldInfo(entry.getKey(), elementKind(entry.getValue()));
            fieldInfo.adoptChildren(entry.getValue());
            structInfo.adoptChild(fieldInfo);
        }
        for (Map.Entry<String, List<AccessorInfo>> entry : bitfieldAccessorInfos.entrySet()) {
            if (fieldAccessorInfos.containsKey(entry.getKey())) {
                nativeLibs.addError("Bitfield and regular field accessor methods cannot be mixed", entry.getValue(), fieldAccessorInfos.get(entry.getKey()));
            } else if (elementKind(entry.getValue()) != ElementKind.INTEGER) {
                nativeLibs.addError("Bitfield accessor method must have integer kind", entry.getValue());
            }
            StructBitfieldInfo bitfieldInfo = new StructBitfieldInfo(entry.getKey());
            bitfieldInfo.adoptChildren(entry.getValue());
            structInfo.adoptChild(bitfieldInfo);
        }
        nativeCodeInfo.adoptChild(structInfo);
        nativeLibs.registerElementInfo(type, structInfo);
    }

    private void createRawStructInfo(ResolvedJavaType type) {
        if (!validInterfaceDefinition(type, RawStructure.class)) {
            return;
        }

        Map<String, List<AccessorInfo>> fieldAccessorInfos = new TreeMap<>();
        List<AccessorInfo> structAccessorInfos = new ArrayList<>();

        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
            final AccessorInfo accessorInfo;
            final String fieldName;

            RawField fieldAnnotation = getMethodAnnotation(method, RawField.class);
            RawFieldAddress fieldAddressAnnotation = getMethodAnnotation(method, RawFieldAddress.class);
            RawFieldOffset fieldOffsetAnnotation = getMethodAnnotation(method, RawFieldOffset.class);
            if (fieldAnnotation != null) {
                accessorInfo = new AccessorInfo(method, getAccessorKind(method), false, hasLocationIdentityParameter(method), hasUniqueLocationIdentity(method));
                fieldName = getStructFieldName(accessorInfo, "");
            } else if (fieldAddressAnnotation != null) {
                accessorInfo = new AccessorInfo(method, AccessorKind.ADDRESS, false, false, false);
                fieldName = getStructFieldName(accessorInfo, "");
            } else if (fieldOffsetAnnotation != null) {
                accessorInfo = new AccessorInfo(method, AccessorKind.OFFSET, false, false, false);
                fieldName = getStructFieldName(accessorInfo, "");
            } else if (returnsDeclaringClass(method)) {
                accessorInfo = new AccessorInfo(method, AccessorKind.ADDRESS, getParameterCount(method) > 0, false, false);
                fieldName = null;
            } else {
                nativeLibs.addError("Unexpected method without annotation", method);
                continue;
            }

            if (accessorValid(accessorInfo)) {
                if (fieldName == null) {
                    structAccessorInfos.add(accessorInfo);
                } else {
                    Map<String, List<AccessorInfo>> map = fieldAccessorInfos;
                    List<AccessorInfo> accessorInfos = map.get(fieldName);
                    if (accessorInfos == null) {
                        accessorInfos = new ArrayList<>();
                        map.put(fieldName, accessorInfos);
                    }
                    accessorInfos.add(accessorInfo);
                }

                nativeLibs.registerElementInfo(method, accessorInfo);
            }
        }

        StructInfo structInfo = StructInfo.create(getStructName(type), type);
        structInfo.adoptChildren(structAccessorInfos);

        for (Map.Entry<String, List<AccessorInfo>> entry : fieldAccessorInfos.entrySet()) {
            StructFieldInfo fieldInfo = new StructFieldInfo(entry.getKey(), elementKind(entry.getValue()));
            fieldInfo.adoptChildren(entry.getValue());
            structInfo.adoptChild(fieldInfo);
        }

        nativeCodeInfo.adoptChild(structInfo);
        nativeLibs.registerElementInfo(type, structInfo);
    }

    private boolean hasLocationIdentityParameter(ResolvedJavaMethod method) {
        int parameterCount = getParameterCount(method);
        if (parameterCount == 0) {
            return false;
        }
        JavaType lastParam = AccessorInfo.getParameterType(method, parameterCount - 1);
        return nativeLibs.getLocationIdentityType().equals(lastParam);
    }

    private static boolean hasUniqueLocationIdentity(ResolvedJavaMethod method) {
        return getMethodAnnotation(method, UniqueLocationIdentity.class) != null;
    }

    private ElementKind elementKind(Collection<AccessorInfo> accessorInfos) {
        ElementKind overallKind = ElementKind.UNKNOWN;
        AccessorInfo overallKindAccessor = null;

        for (AccessorInfo accessorInfo : accessorInfos) {
            final ResolvedJavaType type;
            switch (accessorInfo.getAccessorKind()) {
                case GETTER:
                    type = accessorInfo.getReturnType();
                    break;
                case SETTER:
                    type = accessorInfo.getValueParameterType();
                    break;
                default:
                    continue;
            }

            ResolvedJavaMethod method = accessorInfo.getAnnotatedElement();
            ElementKind newKind = elementKind(type, isPinnedObjectFieldAccessor(method));
            if (overallKind == ElementKind.UNKNOWN) {
                overallKind = newKind;
                overallKindAccessor = accessorInfo;
            } else if (overallKind != newKind) {
                nativeLibs.addError("Accessor methods mix integer, floating point, and pointer kinds", overallKindAccessor.getAnnotatedElement(), method);
            }
        }
        return overallKind;
    }

    private ElementKind elementKind(ResolvedJavaType type, boolean isPinnedObject) {
        switch (type.getJavaKind()) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
                return ElementKind.INTEGER;
            case Float:
            case Double:
                return ElementKind.FLOAT;
            case Object:
                if (nativeLibs.isSigned(type) || nativeLibs.isUnsigned(type)) {
                    return ElementKind.INTEGER;
                } else if (isPinnedObject) {
                    return ElementKind.OBJECT;
                } else if (nativeLibs.isString(type)) {
                    return ElementKind.STRING;
                } else if (nativeLibs.isByteArray(type)) {
                    return ElementKind.BYTEARRAY;
                } else {
                    return ElementKind.POINTER;
                }
            default:
                return ElementKind.UNKNOWN;
        }
    }

    private static boolean isPinnedObjectFieldAccessor(ResolvedJavaMethod method) {
        return getMethodAnnotation(method, PinnedObjectField.class) != null;
    }

    private boolean accessorValid(AccessorInfo accessorInfo) {
        ResolvedJavaMethod method = accessorInfo.getAnnotatedElement();

        int expectedParamCount = accessorInfo.parameterCount(false);
        int actualParamCount = getParameterCount(method);
        if (actualParamCount != expectedParamCount) {
            nativeLibs.addError("Wrong number of parameters: expected " + expectedParamCount + "; found " + actualParamCount, method);
            return false;
        }

        if (accessorInfo.isIndexed()) {
            ResolvedJavaType paramType = accessorInfo.getParameterType(accessorInfo.indexParameterNumber(false));
            if (paramType.getJavaKind() != JavaKind.Int && paramType.getJavaKind() != JavaKind.Long && !nativeLibs.isSigned(paramType)) {
                nativeLibs.addError("Wrong type of index parameter 0: expected int, long, or Signed; found " + paramType.toJavaName(true), method);
                return false;
            }
        }
        if (accessorInfo.hasLocationIdentityParameter() && accessorInfo.hasUniqueLocationIdentity()) {
            nativeLibs.addError("Method cannot have annotation @" + UniqueLocationIdentity.class.getSimpleName() + " and a LocationIdentity parameter", method);
            return false;
        }
        if (accessorInfo.hasLocationIdentityParameter()) {
            ResolvedJavaType paramType = accessorInfo.getParameterType(accessorInfo.locationIdentityParameterNumber(false));
            if (!nativeLibs.getLocationIdentityType().equals(paramType)) {
                nativeLibs.addError("Wrong type of locationIdentity parameter: expected " + nativeLibs.getLocationIdentityType().toJavaName(true) + "; found " + paramType.toJavaName(true), method);
                return false;
            }
        }

        ResolvedJavaType returnType = AccessorInfo.getReturnType(method);
        if (!checkObjectType(returnType, method)) {
            return false;
        }
        switch (accessorInfo.getAccessorKind()) {
            case ADDRESS:
                if (!nativeLibs.isPointerBase(returnType) || nativeLibs.isSigned(returnType) || nativeLibs.isUnsigned(returnType)) {
                    nativeLibs.addError("Wrong return type: expected a pointer type; found " + returnType.toJavaName(true), method);
                    return false;
                }
                break;
            case OFFSET:
                if (!(returnType.getJavaKind().isNumericInteger() || nativeLibs.isUnsigned(returnType))) {
                    nativeLibs.addError("Wrong return type: expected an integer numeric type or Unsigned; found " + returnType.toJavaName(true), method);
                    return false;
                }
                break;
            case SETTER:
                if (!checkObjectType(accessorInfo.getValueParameterType(), method)) {
                    return false;
                }
                break;
        }
        return true;
    }

    private boolean checkObjectType(ResolvedJavaType returnType, ResolvedJavaMethod method) {
        if (returnType.getJavaKind() == JavaKind.Object && !nativeLibs.isWordBase(returnType) && !isPinnedObjectFieldAccessor(method)) {
            nativeLibs.addError("Wrong type: expected a primitive type or a Word type; found " + returnType.toJavaName(true) + ". Use the annotation @" + PinnedObjectField.class.getSimpleName() +
                            " if you know what you are doing.", method);
            return false;
        }
        return true;
    }

    private boolean validInterfaceDefinition(ResolvedJavaType type, Class<? extends Annotation> annotationClass) {
        assert type.getAnnotation(annotationClass) != null;

        if (!type.isInterface() || !nativeLibs.isPointerBase(type)) {
            nativeLibs.addError("Annotation @" + annotationClass.getSimpleName() + " can only be used on an interface that extends " + PointerBase.class.getSimpleName(), type);
            return false;
        }
        return true;
    }

    private static String removePrefix(String name, String prefix) {
        assert prefix.length() > 0;
        String result = name;
        if (result.startsWith(prefix)) {
            result = result.substring(prefix.length());
            if (result.startsWith("_")) {
                result = result.substring("_".length());
            }
        }
        return result;
    }

    private static String getConstantName(ResolvedJavaMethod method) {
        CConstant constantAnnotation = getMethodAnnotation(method, CConstant.class);
        String name = constantAnnotation.value();
        if (name.length() == 0) {
            name = method.getName();
            /* Remove "get" prefix for automatically inferred names. */
            name = removePrefix(name, "get");
        }
        return name;
    }

    private String getPointerToTypeName(ResolvedJavaType type) {
        CPointerTo pointerToAnnotation = type.getAnnotation(CPointerTo.class);
        String nameOfCType = pointerToAnnotation.nameOfCType();

        Class<?> pointerToType = pointerToAnnotation.value();
        CStruct pointerToStructAnnotation;
        CPointerTo pointerToPointerAnnotation;
        do {
            pointerToStructAnnotation = pointerToType.getAnnotation(CStruct.class);
            pointerToPointerAnnotation = pointerToType.getAnnotation(CPointerTo.class);
            if (pointerToStructAnnotation != null || pointerToPointerAnnotation != null) {
                break;
            }
            pointerToType = pointerToType.getInterfaces().length == 1 ? pointerToType.getInterfaces()[0] : null;
        } while (pointerToType != null);

        int n = (nameOfCType.length() > 0 ? 1 : 0) + (pointerToStructAnnotation != null ? 1 : 0) + (pointerToPointerAnnotation != null ? 1 : 0);
        if (n != 1) {
            nativeLibs.addError("Exactly one of " +  //
                            "1) literal C type name, " +  //
                            "2) class annotated with @" + CStruct.class.getSimpleName() + ", or " +  //
                            "3) class annotated with @" + CPointerTo.class.getSimpleName() + " must be specified in @" + CPointerTo.class.getSimpleName() + " annotation", type);
            return "__error";
        }

        if (pointerToStructAnnotation != null) {
            return getStructName(getMetaAccess().lookupJavaType(pointerToType)) + "*";
        } else if (pointerToPointerAnnotation != null) {
            return getPointerToTypeName(getMetaAccess().lookupJavaType(pointerToType)) + "*";
        } else {
            return nameOfCType;
        }
    }

    private static String getStructName(ResolvedJavaType type) {
        CStruct structAnnotation = type.getAnnotation(CStruct.class);

        if (structAnnotation == null) {
            RawStructure rsanno = type.getAnnotation(RawStructure.class);
            assert rsanno != null : "Unexpected struct type " + type;
            return getSimpleJavaName(type);
        }

        String name = structAnnotation.value();

        if (name.length() == 0) {
            name = getSimpleJavaName(type);
        }
        if (structAnnotation.addStructKeyword()) {
            name = "struct " + name;
        }
        return name;
    }

    private static String getSimpleJavaName(ResolvedJavaType type) {
        String name = type.toJavaName(false);
        int innerClassSeparator = name.lastIndexOf('$');

        if (innerClassSeparator >= 0) {
            name = name.substring(innerClassSeparator + 1);
        }

        return name;
    }

    private static String getStructFieldName(AccessorInfo info, String annotationValue) {
        if (annotationValue.length() != 0) {
            return annotationValue;
        } else {
            return removePrefix(info.getAnnotatedElement().getName(), info.getAccessorPrefix());
        }
    }

    private void createEnumInfo(ResolvedJavaType type) {
        if (!nativeLibs.isEnum(type)) {
            nativeLibs.addError("Annotation @" + CEnum.class.getSimpleName() + " can only be used on an Java enumeration", type);
            return;
        }

        CEnum annotation = type.getAnnotation(CEnum.class);
        String name = annotation.value();
        if (!name.isEmpty()) {
            if (annotation.addEnumKeyword()) {
                name = "enum " + name;
            }
        } else {
            name = "int";
        }
        EnumInfo enumInfo = new EnumInfo(name, type);

        for (ResolvedJavaField field : type.getStaticFields()) {
            assert Modifier.isStatic(field.getModifiers());
            if (Modifier.isFinal(field.getModifiers()) && field.getType().equals(type)) {
                createEnumConstantInfo(enumInfo, field);
            }
        }
        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
            if (getMethodAnnotation(method, CEnumValue.class) != null) {
                createEnumValueInfo(enumInfo, method);
            }
            if (getMethodAnnotation(method, CEnumLookup.class) != null) {
                createEnumLookupInfo(enumInfo, method);
            }
        }
        nativeCodeInfo.adoptChild(enumInfo);
        nativeLibs.registerElementInfo(type, enumInfo);
    }

    private void createEnumConstantInfo(EnumInfo enumInfo, ResolvedJavaField field) {
        JavaConstant enumValue = nativeLibs.getConstantReflection().readFieldValue(field, null);
        assert enumValue.isNonNull() && nativeLibs.getMetaAccess().lookupJavaType(enumValue).equals(enumInfo.getAnnotatedElement());

        CEnumConstant fieldAnnotation = field.getAnnotation(CEnumConstant.class);
        String name = "";
        boolean includeInLookup = true;
        if (fieldAnnotation != null) {
            name = fieldAnnotation.value();
            includeInLookup = fieldAnnotation.includeInLookup();
        }
        if (name.length() == 0) {
            name = field.getName();
        }

        EnumConstantInfo constantInfo = new EnumConstantInfo(name, field, includeInLookup, nativeLibs.getSnippetReflection().asObject(Enum.class, enumValue));
        enumInfo.adoptChild(constantInfo);
    }

    private static ResolvedJavaMethod originalMethod(ResolvedJavaMethod method) {
        assert method instanceof AnalysisMethod;
        AnalysisMethod analysisMethod = (AnalysisMethod) method;
        assert analysisMethod.getWrapped() instanceof CEnumCallWrapperMethod;
        CEnumCallWrapperMethod wrapperMethod = (CEnumCallWrapperMethod) analysisMethod.getWrapped();
        return wrapperMethod.getOriginal();
    }

    private void createEnumValueInfo(EnumInfo enumInfo, ResolvedJavaMethod method) {

        /* Check the modifiers of the original method. The synthetic method is not native. */
        ResolvedJavaMethod originalMethod = originalMethod(method);
        if (!Modifier.isNative(originalMethod.getModifiers()) || Modifier.isStatic(originalMethod.getModifiers())) {
            nativeLibs.addError("Method annotated with @" + CEnumValue.class.getSimpleName() + " must be a non-static native method", method);
            return;
        }
        if (getParameterCount(method) != 0) {
            nativeLibs.addError("Method annotated with @" + CEnumValue.class.getSimpleName() + " cannot have parameters", method);
            return;
        }
        ElementKind elementKind = elementKind(AccessorInfo.getReturnType(method), false);
        if (elementKind != ElementKind.INTEGER) {
            nativeLibs.addError("Method annotated with @" + CEnumValue.class.getSimpleName() + " must have an integer return type", method);
            return;
        }

        EnumValueInfo valueInfo = new EnumValueInfo(method);
        enumInfo.adoptChild(valueInfo);
        nativeLibs.registerElementInfo(method, valueInfo);
    }

    private void createEnumLookupInfo(EnumInfo enumInfo, ResolvedJavaMethod method) {

        /* Check the modifiers of the original method. The synthetic method is not native. */
        ResolvedJavaMethod originalMethod = originalMethod(method);
        if (!Modifier.isNative(originalMethod.getModifiers()) || !Modifier.isStatic(originalMethod.getModifiers())) {
            nativeLibs.addError("Method annotated with @" + CEnumLookup.class.getSimpleName() + " must be a static native method", method);
            return;
        }
        if (getParameterCount(method) != 1 || elementKind(AccessorInfo.getParameterType(method, 0), false) != ElementKind.INTEGER) {
            nativeLibs.addError("Method annotated with @" + CEnumLookup.class.getSimpleName() + " must have exactly one integer parameter", method);
            return;
        }
        if (!returnsDeclaringClass(method)) {
            nativeLibs.addError("Return type of method annotated with @" + CEnumLookup.class.getSimpleName() + " must be the annotation type", method);
            return;
        }

        enumInfo.needsLookup = true;
        EnumLookupInfo lookupInfo = new EnumLookupInfo(method);
        enumInfo.adoptChild(lookupInfo);
        nativeLibs.registerElementInfo(method, lookupInfo);
    }

    private static <T extends Annotation> T getMethodAnnotation(ResolvedJavaMethod method, Class<T> annotationClass) {
        /*
         * The Eclipse Java compiler does not emit annotations for bridge methods that are emitted
         * when overwriting a method with covariant return types. As a workaround, we look up the
         * original method and use the annotations of the original method.
         */
        return BridgeMethodUtils.getAnnotation(annotationClass, method);
    }
}
