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

import static com.oracle.svm.hosted.lambda.LambdaParser.createMethodGraph;
import static com.oracle.svm.hosted.lambda.LambdaParser.getLambdaClassFromConstantNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.capnproto.Text;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.heap.ImageLayerLoaderHelper;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod.WrappedMethod;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisMethod.WrappedMethod.WrappedMember;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType.WrappedType;
import com.oracle.graal.pointsto.heap.SharedLayerSnapshotCapnProtoSchemaHolder.PersistedAnalysisType.WrappedType.SerializationGenerated;
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
    protected boolean loadType(PersistedAnalysisType.Reader typeData, int tid) {
        WrappedType.Reader wrappedType = typeData.getWrappedType();
        if (wrappedType.isNone()) {
            return false;
        }
        if (wrappedType.isSerializationGenerated()) {
            SerializationGenerated.Reader sg = wrappedType.getSerializationGenerated();
            String rawDeclaringClassName = sg.getRawDeclaringClass().toString();
            String rawTargetConstructorClassName = sg.getRawTargetConstructor().toString();
            Class<?> rawDeclaringClass = imageLayerLoader.lookupClass(false, rawDeclaringClassName);
            Class<?> rawTargetConstructorClass = imageLayerLoader.lookupClass(false, rawTargetConstructorClassName);
            SerializationSupport serializationSupport = SerializationSupport.singleton();
            Constructor<?> rawTargetConstructor = ReflectionUtil.lookupConstructor(rawTargetConstructorClass);
            Constructor<?> constructor = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(rawDeclaringClass, rawTargetConstructor);
            serializationSupport.addConstructorAccessor(rawDeclaringClass, rawTargetConstructorClass, SerializationFeature.getConstructorAccessor(constructor));
            Class<?> constructorAccessor = serializationSupport.getSerializationConstructorAccessor(rawDeclaringClass, rawTargetConstructorClass).getClass();
            imageLayerLoader.getMetaAccess().lookupJavaType(constructorAccessor);
            return true;
        } else if (wrappedType.isLambda()) {
            String capturingClassName = wrappedType.getLambda().getCapturingClass().toString();
            Class<?> capturingClass = imageLayerLoader.lookupClass(false, capturingClassName);
            loadLambdaTypes(capturingClass);
        } else if (wrappedType.isProxyType()) {
            Class<?>[] interfaces = Stream.of(typeData.getInterfaces()).flatMapToInt(r -> IntStream.range(0, r.size()).map(r::get))
                            .mapToObj(i -> imageLayerLoader.getAnalysisTypeForBaseLayerId(i).getJavaClass()).toArray(Class<?>[]::new);
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
    protected boolean loadMethod(PersistedAnalysisMethod.Reader methodData, int mid) {
        WrappedMethod.Reader wrappedMethod = methodData.getWrappedMethod();
        if (wrappedMethod.isNone()) {
            return false;
        }
        if (wrappedMethod.isFactoryMethod()) {
            WrappedMethod.FactoryMethod.Reader fm = wrappedMethod.getFactoryMethod();
            AnalysisMethod analysisMethod = imageLayerLoader.getAnalysisMethodForBaseLayerId(fm.getTargetConstructorId());
            if (analysisMethod.wrapped instanceof BaseLayerMethod) {
                return false;
            }
            AnalysisType instantiatedType = imageLayerLoader.getAnalysisTypeForBaseLayerId(fm.getInstantiatedTypeId());
            FactoryMethodSupport.singleton().lookup(imageLayerLoader.getMetaAccess(), analysisMethod, instantiatedType, fm.getThrowAllocatedObject());
            return true;
        } else if (wrappedMethod.isCEntryPointCallStub()) {
            WrappedMethod.CEntryPointCallStub.Reader stub = wrappedMethod.getCEntryPointCallStub();
            boolean asNotPublished = stub.getNotPublished();
            AnalysisMethod originalMethod = imageLayerLoader.getAnalysisMethodForBaseLayerId(stub.getOriginalMethodId());
            CEntryPointCallStubSupport.singleton().registerStubForMethod(originalMethod, () -> {
                CEntryPointData data = CEntryPointData.create(originalMethod);
                if (asNotPublished) {
                    data = data.copyWithPublishAs(CEntryPoint.Publish.NotPublished);
                }
                return data;
            });
            return true;
        } else if (wrappedMethod.isWrappedMember()) {
            WrappedMember.Reader wm = wrappedMethod.getWrappedMember();
            Executable member = getWrappedMember(wm);
            if (member == null) {
                return false;
            }
            if (wm.isReflectionExpandSignature()) {
                ImageSingletons.lookup(ReflectionFeature.class).getOrCreateAccessor(member);
            } else if (wm.isJavaCallVariantWrapper()) {
                JNIAccessFeature.singleton().addMethod(member, (FeatureImpl.DuringAnalysisAccessImpl) imageLayerLoader.getUniverse().getConcurrentAnalysisAccess());
            }
            return true;
        }
        return super.loadMethod(methodData, mid);
    }

    private Executable getWrappedMember(WrappedMember.Reader memberData) {
        String className = memberData.getDeclaringClassName().toString();
        Class<?> declaringClass = imageLayerLoader.lookupClass(true, className);
        if (declaringClass == null) {
            return null;
        }
        String name = memberData.getName().toString();
        Class<?>[] parameters = StreamSupport.stream(memberData.getArgumentTypeNames().spliterator(), false).map(Text.Reader::toString)
                        .map(c -> imageLayerLoader.lookupClass(false, c)).toArray(Class<?>[]::new);
        return ImageLayerLoader.lookupMethodByReflection(name, declaringClass, parameters);
    }
}
