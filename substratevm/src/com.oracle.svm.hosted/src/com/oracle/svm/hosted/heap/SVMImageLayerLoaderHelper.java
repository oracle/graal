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

import static com.oracle.graal.pointsto.heap.ImageLayerLoader.get;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FACTORY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.GENERATED_SERIALIZATION_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.HOLDER_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.LAMBDA_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.RAW_DECLARING_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.RAW_TARGET_CONSTRUCTOR_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TARGET_CONSTRUCTOR_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.THROW_ALLOCATED_OBJECT_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.WRAPPED_METHOD_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.WRAPPED_TYPE_TAG;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.heap.ImageLayerLoaderHelper;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.bootstrap.BootstrapMethodConfiguration;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.reflect.serialize.SerializationFeature;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.internal.reflect.ReflectionFactory;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;

public class SVMImageLayerLoaderHelper extends ImageLayerLoaderHelper {
    private static final Class<?> DIRECT_METHOD_HANDLE_STATIC_ACCESSOR_CLASS = ReflectionUtil.lookupClass(false, "java.lang.invoke.DirectMethodHandle$StaticAccessor");
    private static final Class<?> DIRECT_METHOD_HANDLE_CONSTRUCTOR_CLASS = ReflectionUtil.lookupClass(false, "java.lang.invoke.DirectMethodHandle$Constructor");
    private static final String STATIC_BASE_FIELD_NAME = "staticBase";
    private static final String INSTANCE_CLASS_FIELD_NAME = "instanceClass";
    private static final int INVOKE_DYNAMIC_OPCODE = 186;

    public SVMImageLayerLoaderHelper(ImageLayerLoader imageLayerLoader) {
        super(imageLayerLoader);
    }

    @Override
    protected boolean loadType(EconomicMap<String, Object> typeData, int tid) {
        String wrappedType = get(typeData, WRAPPED_TYPE_TAG);
        if (wrappedType == null) {
            return false;
        }
        if (wrappedType.equals(GENERATED_SERIALIZATION_TAG)) {
            String rawDeclaringClassName = get(typeData, RAW_DECLARING_CLASS_TAG);
            String rawTargetConstructorClassName = get(typeData, RAW_TARGET_CONSTRUCTOR_CLASS_TAG);
            Class<?> rawDeclaringClass = imageLayerLoader.lookupClass(false, rawDeclaringClassName);
            Class<?> rawTargetConstructorClass = imageLayerLoader.lookupClass(false, rawTargetConstructorClassName);
            SerializationSupport serializationSupport = SerializationSupport.singleton();
            Constructor<?> rawTargetConstructor = ReflectionUtil.lookupConstructor(rawTargetConstructorClass);
            Constructor<?> constructor = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(rawDeclaringClass, rawTargetConstructor);
            serializationSupport.addConstructorAccessor(rawDeclaringClass, rawTargetConstructorClass, SerializationFeature.getConstructorAccessor(constructor));
            Class<?> constructorAccessor = serializationSupport.getSerializationConstructorAccessor(rawDeclaringClass, rawTargetConstructorClass).getClass();
            imageLayerLoader.getMetaAccess().lookupJavaType(constructorAccessor);
            return true;
        } else if (wrappedType.equals(LAMBDA_TYPE_TAG)) {
            String holderClassName = get(typeData, HOLDER_CLASS_TAG);
            Class<?> holderClass = imageLayerLoader.lookupClass(false, holderClassName);
            loadLambdaTypes(holderClass);
            return true;
        }

        return super.loadType(typeData, tid);
    }

    /**
     * The constant pool index of bootstrap method is not stable in different JVM instances, so the
     * only solution is to load all lambda types of the given holder class.
     */
    private void loadLambdaTypes(Class<?> holderClass) {
        AnalysisUniverse universe = imageLayerLoader.getUniverse();
        AnalysisType type = universe.getBigbang().getMetaAccess().lookupJavaType(holderClass);
        boolean isSubstitution = AnnotationAccess.isAnnotationPresent(holderClass, TargetClass.class);
        ConstantPool constantPool = getConstantPool(type, isSubstitution);
        int index = JavaVersionUtil.JAVA_SPEC > 21 ? 0 : -1;
        ConstantPool.BootstrapMethodInvocation bootstrap;
        while ((bootstrap = getBootstrap(constantPool, index)) != null) {
            if (BootstrapMethodConfiguration.singleton().isMetafactory(OriginalMethodProvider.getJavaMethod(bootstrap.getMethod()))) {
                constantPool.loadReferencedType(index, INVOKE_DYNAMIC_OPCODE);
                JavaConstant test = constantPool.lookupAppendix(index, INVOKE_DYNAMIC_OPCODE);
                Object appendix = universe.getSnippetReflection().asObject(Object.class, test);

                Class<?> potentialLambdaClass;
                if (DIRECT_METHOD_HANDLE_STATIC_ACCESSOR_CLASS.isInstance(appendix)) {
                    potentialLambdaClass = ReflectionUtil.readField(DIRECT_METHOD_HANDLE_STATIC_ACCESSOR_CLASS, STATIC_BASE_FIELD_NAME, appendix);
                } else if (DIRECT_METHOD_HANDLE_CONSTRUCTOR_CLASS.isInstance(appendix)) {
                    potentialLambdaClass = ReflectionUtil.readField(DIRECT_METHOD_HANDLE_CONSTRUCTOR_CLASS, INSTANCE_CLASS_FIELD_NAME, appendix);
                } else {
                    throw VMError.shouldNotReachHere("Unexpected appendix %s", appendix);
                }
                universe.getBigbang().getMetaAccess().lookupJavaType(potentialLambdaClass);
            }
            if (JavaVersionUtil.JAVA_SPEC > 21) {
                index++;
            } else {
                index--;
            }
        }
    }

    /**
     * A default and substitution class have two different constant pools. The constant pool can
     * only be fetched through the methods of the class, so we iterate over the methods and the
     * constructors and take the first constant pool that matches the current class.
     */
    private static ConstantPool getConstantPool(AnalysisType type, boolean isSubstitution) {
        Stream<AnalysisMethod> candidates = Stream.concat(Arrays.stream(type.getDeclaredMethods(false)), Arrays.stream(type.getDeclaredConstructors(false)));
        Optional<ConstantPool> cp = candidates.map(method -> {
            Executable javaMethod = method.getJavaMethod();
            if (((javaMethod != null && AnnotationAccess.isAnnotationPresent(javaMethod.getDeclaringClass(), TargetClass.class)) || (method.wrapped instanceof SubstitutionMethod)) == isSubstitution) {
                return method.getConstantPool();
            }
            return null;
        }).filter(Objects::nonNull).findAny();
        assert cp.isPresent() : String.format("No constant pool was found in the %s class.", isSubstitution ? "substitution" : "default");
        return cp.get();
    }

    private static ConstantPool.BootstrapMethodInvocation getBootstrap(ConstantPool constantPool, int index) {
        try {
            return constantPool.lookupBootstrapMethodInvocation(index, INVOKE_DYNAMIC_OPCODE);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    @Override
    protected boolean loadMethod(EconomicMap<String, Object> methodData, int mid) {
        String wrappedMethod = get(methodData, WRAPPED_METHOD_TAG);
        if (wrappedMethod == null) {
            return false;
        }
        if (wrappedMethod.equals(FACTORY_TAG)) {
            int constructorId = get(methodData, TARGET_CONSTRUCTOR_TAG);
            boolean throwAllocatedObject = get(methodData, THROW_ALLOCATED_OBJECT_TAG);
            FactoryMethodSupport.singleton().lookup(imageLayerLoader.getMetaAccess(), imageLayerLoader.getAnalysisMethod(constructorId), throwAllocatedObject);
            return true;
        }

        return super.loadMethod(methodData, mid);
    }
}
