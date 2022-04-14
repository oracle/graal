/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.nodes.SubstrateNewHybridInstanceNode;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.heap.Pod.RuntimeSupport.PodFactory;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

final class PodFactorySubstitutionProcessor extends SubstitutionProcessor {
    private final ConcurrentMap<ResolvedJavaMethod, PodFactorySubstitutionMethod> substitutions = new ConcurrentHashMap<>();

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        if (method.isSynthetic() && method.getDeclaringClass().isAnnotationPresent(PodFactory.class) && !method.isConstructor()) {
            assert !(method instanceof CustomSubstitutionMethod);
            return substitutions.computeIfAbsent(method, PodFactorySubstitutionMethod::new);
        }
        return method;
    }

    @Override
    public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
        if (method instanceof PodFactorySubstitutionMethod) {
            return ((PodFactorySubstitutionMethod) method).getOriginal();
        }
        return method;
    }
}

final class PodFactorySubstitutionMethod extends CustomSubstitutionMethod {
    PodFactorySubstitutionMethod(ResolvedJavaMethod original) {
        super(original);
    }

    @Override
    public int getModifiers() {
        return super.getModifiers() & ~Modifier.NATIVE;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);

        ValueNode[] originalArgs = kit.loadArguments(method.toParameterTypes()).toArray(ValueNode.EMPTY_ARRAY);

        ResolvedJavaType podType = metaAccess.lookupJavaType(Pod.class);
        ValueNode podObject = kit.maybeCreateExplicitNullCheck(kit.createLoadField(originalArgs[0], findField(method.getDeclaringClass(), "pod")));
        ValueNode sizeWithoutRefMap = kit.createLoadField(podObject, findField(podType, "fieldsSizeWithoutRefMap"));
        ValueNode refMapArray = kit.createLoadField(podObject, findField(podType, "referenceMap"));
        ValueNode refMapLength = kit.append(new ArrayLengthNode(refMapArray));
        ValueNode hybridArrayLength = kit.append(AddNode.add(sizeWithoutRefMap, refMapLength));

        PodFactory annotation = method.getDeclaringClass().getAnnotation(PodFactory.class);
        ResolvedJavaType podConcreteType = metaAccess.lookupJavaType(annotation.podClass());
        ResolvedJavaType elementType = metaAccess.lookupJavaType(byte.class);

        AbstractNewObjectNode instance = kit.append(new SubstrateNewHybridInstanceNode(podConcreteType, elementType, hybridArrayLength));
        kit.createInvokeWithExceptionAndUnwind(Pod.class, "initInstanceRefMap", InvokeKind.Virtual, podObject, instance);

        ResolvedJavaMethod targetCtor = null;
        for (ResolvedJavaMethod ctor : podConcreteType.getSuperclass().getDeclaredConstructors()) {
            if (parameterTypesMatch(method, ctor)) {
                targetCtor = ctor;
                break;
            }
        }
        GraalError.guarantee(targetCtor != null, "Matching constructor not found: %s", getSignature());

        ValueNode[] invokeArgs = Arrays.copyOf(originalArgs, originalArgs.length);
        invokeArgs[0] = instance;
        kit.createInvokeWithExceptionAndUnwind(targetCtor, InvokeKind.Special, kit.getFrameState(), kit.bci(), invokeArgs);

        kit.createReturn(instance, instance.getStackKind());
        return kit.finalizeGraph();
    }

    private static boolean parameterTypesMatch(ResolvedJavaMethod method, ResolvedJavaMethod ctor) {
        int paramsCount = method.getSignature().getParameterCount(false);
        if (paramsCount != ctor.getSignature().getParameterCount(false)) {
            return false;
        }
        for (int i = 0; i < paramsCount; i++) {
            if (!ctor.getSignature().getParameterType(i, ctor.getDeclaringClass())
                            .equals(method.getSignature().getParameterType(i, method.getDeclaringClass()))) {
                return false;
            }
        }
        return true;
    }

    private static ResolvedJavaField findField(ResolvedJavaType type, String name) {
        for (ResolvedJavaField field : type.getInstanceFields(false)) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        throw GraalError.shouldNotReachHere("Required field " + name + " not found in " + type);
    }
}
