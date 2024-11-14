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
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FACTORY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.GENERATED_SERIALIZATION_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.LAMBDA_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.RAW_DECLARING_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.RAW_TARGET_CONSTRUCTOR_CLASS_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TARGET_CONSTRUCTOR_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.THROW_ALLOCATED_OBJECT_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.WRAPPED_METHOD_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.WRAPPED_TYPE_TAG;
import static com.oracle.svm.hosted.lambda.LambdaParser.createMethodGraph;
import static com.oracle.svm.hosted.lambda.LambdaParser.getLambdaClassFromConstantNode;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.heap.ImageLayerLoaderHelper;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.lambda.LambdaParser;
import com.oracle.svm.hosted.reflect.serialize.SerializationFeature;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.graph.iterators.NodeIterable;
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
        StructuredGraph graph = createMethodGraph(m, bigBang.getOptions());

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
            FactoryMethodSupport.singleton().lookup(imageLayerLoader.getMetaAccess(), imageLayerLoader.getAnalysisMethod(constructorId), throwAllocatedObject);
            return true;
        }

        return super.loadMethod(methodData, mid);
    }
}
