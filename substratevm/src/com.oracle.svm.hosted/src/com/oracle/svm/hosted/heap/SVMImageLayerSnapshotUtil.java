/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.heap;

import static com.oracle.svm.hosted.methodhandles.InjectedInvokerRenamingSubstitutionProcessor.isInjectedInvokerType;
import static com.oracle.svm.hosted.methodhandles.MethodHandleInvokerRenamingSubstitutionProcessor.isMethodHandleType;
import static com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor.isProxyType;
import static jdk.graal.compiler.java.LambdaUtils.isLambdaType;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.hosted.code.FactoryMethod;
import com.oracle.svm.hosted.code.IncompatibleClassChangeFallbackMethod;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SVMImageLayerSnapshotUtil extends ImageLayerSnapshotUtil {
    private static final String GENERATED_SERIALIZATION = "jdk.internal.reflect.GeneratedSerializationConstructorAccessor";

    public static final Field companion = ReflectionUtil.lookupField(DynamicHub.class, "companion");
    public static final Field classInitializationInfo = ReflectionUtil.lookupField(DynamicHub.class, "classInitializationInfo");
    private static final Field name = ReflectionUtil.lookupField(DynamicHub.class, "name");
    private static final Field superHub = ReflectionUtil.lookupField(DynamicHub.class, "superHub");
    private static final Field componentType = ReflectionUtil.lookupField(DynamicHub.class, "componentType");
    public static final Field arrayHub = ReflectionUtil.lookupField(DynamicHub.class, "arrayHub");
    public static final Field interfacesEncoding = ReflectionUtil.lookupField(DynamicHub.class, "interfacesEncoding");
    public static final Field enumConstantsReference = ReflectionUtil.lookupField(DynamicHub.class, "enumConstantsReference");

    protected static final Set<Field> dynamicHubRelinkedFields = Set.of(companion, classInitializationInfo, name, superHub, componentType, arrayHub);

    protected final Map<AnalysisType, Set<Integer>> fieldsToRelink = new HashMap<>();

    @Override
    public String getTypeIdentifier(AnalysisType type) {
        if (type.toJavaName(true).contains(GENERATED_SERIALIZATION)) {
            getGeneratedSerializationName(type);
        }
        if (isProxyType(type)) {
            return type.toJavaName(true);
        }
        return super.getTypeIdentifier(type);
    }

    private static String generatedSerializationClassName(SerializationSupport.SerializationLookupKey serializationLookupKey) {
        return GENERATED_SERIALIZATION + ":" + serializationLookupKey.getDeclaringClass() + "," + serializationLookupKey.getTargetConstructorClass();
    }

    @Override
    public String getMethodIdentifier(AnalysisMethod method) {
        AnalysisType declaringClass = method.getDeclaringClass();
        String moduleName = declaringClass.getJavaClass().getModule().getName();
        if (declaringClass.toJavaName(true).contains(GENERATED_SERIALIZATION)) {
            return getGeneratedSerializationName(declaringClass) + ":" + method.getName();
        }
        if (method.wrapped instanceof FactoryMethod factoryMethod) {
            ResolvedJavaMethod targetConstructor = factoryMethod.getTargetConstructor();
            return addModuleName(targetConstructor.getDeclaringClass().toJavaName(true) + getQualifiedName(method), moduleName);
        }
        if (method.wrapped instanceof IncompatibleClassChangeFallbackMethod) {
            Executable originalMethod = method.getJavaMethod();
            if (originalMethod != null) {
                return addModuleName(method.getQualifiedName() + " " + method.getJavaMethod().toString(), moduleName);
            }
        }
        if (!(method.wrapped instanceof HotSpotResolvedJavaMethod)) {
            return addModuleName(getQualifiedName(method), moduleName);
        }
        /*
         * Those methods cannot use the name of the wrapped method as it would not use the name of
         * the SubstitutionType
         */
        if (isLambdaType(declaringClass) || isInjectedInvokerType(declaringClass) || isMethodHandleType(declaringClass) || isProxyType(declaringClass)) {
            return getQualifiedName(method);
        }
        return super.getMethodIdentifier(method);
    }

    /*
     * The GeneratedSerializationConstructorAccessor names created in SerializationSupport are not
     * stable in a multi threading context. To ensure the correct one is matched in the extension
     * image, the constructor accessors table from SerializationSupport is accessed.
     */
    private static String getGeneratedSerializationName(AnalysisType type) {
        Class<?> constructorAccessor = type.getJavaClass();
        SerializationSupport serializationRegistry = SerializationSupport.singleton();
        SerializationSupport.SerializationLookupKey serializationLookupKey = serializationRegistry.getKeyFromConstructorAccessorClass(constructorAccessor);
        return generatedSerializationClassName(serializationLookupKey);
    }

    @Override
    public Set<Integer> getRelinkedFields(AnalysisType type, AnalysisMetaAccess metaAccess) {
        Set<Integer> result = fieldsToRelink.computeIfAbsent(type, key -> {
            Class<?> clazz = type.getJavaClass();
            if (clazz == Class.class) {
                type.getInstanceFields(true);
                return dynamicHubRelinkedFields.stream().map(metaAccess::lookupJavaField).map(AnalysisField::getPosition).collect(Collectors.toSet());
            } else {
                return null;
            }
        });
        if (result == null) {
            return Set.of();
        }
        return result;
    }
}
