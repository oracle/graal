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
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CAPTURING_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.C_ENTRY_POINT_CALL_STUB_METHOD_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FACTORY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.GENERATED_SERIALIZATION_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INSTANTIATED_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.INTERFACES_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.JNI_JAVA_CALL_VARIANT_WRAPPER_METHOD_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.LAMBDA_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.NOT_AS_PUBLISHED_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.ORIGINAL_METHOD_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.PROXY_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.RAW_DECLARING_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.RAW_TARGET_CONSTRUCTOR_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.REFLECTION_EXPAND_SIGNATURE_METHOD_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TARGET_CONSTRUCTOR_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.THROW_ALLOCATED_OBJECT_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.WRAPPED_MEMBER_ARGUMENTS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.WRAPPED_MEMBER_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.WRAPPED_MEMBER_NAME_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.WRAPPED_METHOD_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.WRAPPED_TYPE_TAG;
import static com.oracle.svm.hosted.lambda.LambdaParser.createMethodGraph;
import static com.oracle.svm.hosted.lambda.LambdaParser.getLambdaClassFromConstantNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.heap.ImageLayerLoaderHelper;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.BaseLayerMethod;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.jni.JNIAccessFeature;
import com.oracle.svm.hosted.lambda.LambdaParser;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import com.oracle.svm.hosted.reflect.serialize.SerializationFeature;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.internal.reflect.ReflectionFactory;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SVMImageLayerLoaderHelper extends ImageLayerLoaderHelper {
    private final Map<Class<?>, Boolean> capturingClasses = new ConcurrentHashMap<>();

    public SVMImageLayerLoaderHelper(ImageLayerLoader imageLayerLoader) {
        super(imageLayerLoader);
    }

    @Override
    @SuppressWarnings("deprecation")
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
            String capturingClassName = get(typeData, CAPTURING_CLASS_TAG);
            Class<?> capturingClass = imageLayerLoader.lookupClass(false, capturingClassName);
            loadLambdaTypes(capturingClass);
        } else if (wrappedType.equals(PROXY_TYPE_TAG)) {
            List<Integer> interfaceIds = get(typeData, INTERFACES_TAG);
            Class<?>[] interfaces = interfaceIds.stream().map(i -> imageLayerLoader.getAnalysisType(i).getJavaClass()).toArray(Class<?>[]::new);
            /* GR-59854: The deprecation warning comes from this call to Proxy.getProxyClass. */
            Class<?> proxy = Proxy.getProxyClass(interfaces[0].getClassLoader(), interfaces);
            imageLayerLoader.getMetaAccess().lookupJavaType(proxy);
            return true;
        }

        return super.loadType(typeData, tid);
    }

    /**
     * Load all lambda types of the given capturing class. Each method of the capturing class is
     * parsed (see {@link LambdaParser#createMethodGraph(ResolvedJavaMethod, OptionValues)}). The
     * lambda types can then be found in the constant nodes of the graphs.
     */
    private void loadLambdaTypes(Class<?> capturingClass) {
        AnalysisUniverse universe = imageLayerLoader.getUniverse();
        capturingClasses.computeIfAbsent(capturingClass, key -> {
            LambdaParser.allExecutablesDeclaredInClass(universe.getOriginalMetaAccess().lookupJavaType(capturingClass))
                            .filter(m -> m.getCode() != null)
                            .forEach(m -> loadLambdaTypes(m, universe.getBigbang()));
            return true;
        });
    }

    private static void loadLambdaTypes(ResolvedJavaMethod m, BigBang bigBang) {
        StructuredGraph graph;
        try {
            graph = createMethodGraph(m, bigBang.getOptions());
        } catch (NoClassDefFoundError | BytecodeParser.BytecodeParserError e) {
            /* Skip the method if it refers to a missing class */
            return;
        }

        NodeIterable<ConstantNode> constantNodes = ConstantNode.getConstantNodes(graph);

        for (ConstantNode cNode : constantNodes) {
            Class<?> lambdaClass = getLambdaClassFromConstantNode(cNode);

            if (lambdaClass != null) {
                bigBang.getMetaAccess().lookupJavaType(lambdaClass);
            }
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
            AnalysisMethod analysisMethod = imageLayerLoader.getAnalysisMethod(constructorId);
            if (analysisMethod.wrapped instanceof BaseLayerMethod) {
                return false;
            }
            int instantiatedTypeId = get(methodData, INSTANTIATED_TYPE_TAG);
            AnalysisType instantiatedType = imageLayerLoader.getAnalysisType(instantiatedTypeId);
            FactoryMethodSupport.singleton().lookup(imageLayerLoader.getMetaAccess(), analysisMethod, instantiatedType, throwAllocatedObject);
            return true;
        } else if (wrappedMethod.equals(C_ENTRY_POINT_CALL_STUB_METHOD_TAG)) {
            int originalMethodId = get(methodData, ORIGINAL_METHOD_ID_TAG);
            boolean asNotPublished = get(methodData, NOT_AS_PUBLISHED_TAG);
            AnalysisMethod originalMethod = imageLayerLoader.getAnalysisMethod(originalMethodId);
            CEntryPointCallStubSupport.singleton().registerStubForMethod(originalMethod, () -> {
                CEntryPointData data = CEntryPointData.create(originalMethod);
                if (asNotPublished) {
                    data = data.copyWithPublishAs(CEntryPoint.Publish.NotPublished);
                }
                return data;
            });
            return true;
        } else if (wrappedMethod.equals(REFLECTION_EXPAND_SIGNATURE_METHOD_TAG)) {
            Executable member = getWrappedMember(methodData);
            if (member == null) {
                return false;
            }
            ImageSingletons.lookup(ReflectionFeature.class).getOrCreateAccessor(member);
            return true;
        } else if (wrappedMethod.equals(JNI_JAVA_CALL_VARIANT_WRAPPER_METHOD_TAG)) {
            Executable member = getWrappedMember(methodData);
            if (member == null) {
                return false;
            }
            JNIAccessFeature.singleton().addMethod(member, (FeatureImpl.DuringAnalysisAccessImpl) imageLayerLoader.getUniverse().getConcurrentAnalysisAccess());
            return true;
        }
        return super.loadMethod(methodData, mid);
    }

    private Executable getWrappedMember(EconomicMap<String, Object> methodData) {
        String className = get(methodData, WRAPPED_MEMBER_CLASS_TAG);
        Class<?> declaringClass = imageLayerLoader.lookupClass(true, className);
        if (declaringClass == null) {
            return null;
        }
        String name = get(methodData, WRAPPED_MEMBER_NAME_TAG);
        List<String> parameterNames = get(methodData, WRAPPED_MEMBER_ARGUMENTS_TAG);
        Class<?>[] parameters = parameterNames.stream().map(c -> imageLayerLoader.lookupClass(false, c)).toArray(Class<?>[]::new);
        return ImageLayerLoader.lookupMethodByReflection(name, declaringClass, parameters);
    }
}
