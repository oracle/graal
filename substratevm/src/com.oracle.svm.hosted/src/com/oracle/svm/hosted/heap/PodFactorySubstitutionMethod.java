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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnreachableBeginNode;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import com.oracle.svm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.annotate.DeoptTest;
import com.oracle.svm.core.graal.nodes.DeoptEntryBeginNode;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.graal.nodes.NewPodInstanceNode;
import com.oracle.svm.core.graal.nodes.TestDeoptimizeNode;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.heap.Pod.RuntimeSupport.PodFactory;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.nodes.DeoptProxyNode;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

final class PodFactorySubstitutionProcessor extends SubstitutionProcessor {
    private final ConcurrentMap<ResolvedJavaMethod, PodFactorySubstitutionMethod> substitutions = new ConcurrentHashMap<>();

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        if (method.isSynthetic() && GuardedAnnotationAccess.isAnnotationPresent(method.getDeclaringClass(), PodFactory.class) && !method.isConstructor()) {
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
    public boolean allowRuntimeCompilation() {
        return true;
    }

    @Override
    public int getModifiers() {
        return super.getModifiers() & ~Modifier.NATIVE;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        // Needed to match type flows to invokes so invoked methods can be inlined in runtime
        // compilations, see GraalFeature.processMethod() and MethodTypeFlowBuilder.uniqueKey()
        boolean trackNodeSourcePosition = (purpose == Purpose.ANALYSIS);

        HostedGraphKit kit = new HostedGraphKit(debug, providers, method, trackNodeSourcePosition);
        boolean isDeoptTarget = (method instanceof SharedMethod) && ((SharedMethod) method).isDeoptTarget();

        ResolvedJavaType factoryType = method.getDeclaringClass();
        PodFactory annotation = factoryType.getAnnotation(PodFactory.class);
        ResolvedJavaType podConcreteType = kit.getMetaAccess().lookupJavaType(annotation.podClass());
        ResolvedJavaMethod targetCtor = null;
        for (ResolvedJavaMethod ctor : podConcreteType.getSuperclass().getDeclaredConstructors()) {
            if (parameterTypesMatch(method, ctor)) {
                targetCtor = ctor;
                break;
            }
        }
        GraalError.guarantee(targetCtor != null, "Matching constructor not found: %s", getSignature());

        /*
         * The graph must be safe for runtime compilation and so for compilation as a deoptimization
         * target. We must be careful to use values only from the frame state, so we keep the
         * allocated instance in a local and load it after each step during which a deopt can occur.
         */
        int instanceLocal = kit.getFrameState().localsSize() - 1; // reserved when generating class
        int nextDeoptIndex = startMethod(kit, isDeoptTarget, 0);
        instantiatePod(kit, providers, factoryType, podConcreteType, instanceLocal);
        if (isAnnotationPresent(DeoptTest.class)) {
            kit.append(new TestDeoptimizeNode());
        }
        nextDeoptIndex = invokeConstructor(kit, method, isDeoptTarget, nextDeoptIndex, targetCtor, instanceLocal);

        kit.createReturn(kit.loadLocal(instanceLocal, JavaKind.Object), JavaKind.Object);
        return kit.finalizeGraph();
    }

    private static int startMethod(HostedGraphKit kit, boolean isDeoptTarget, int nextDeoptIndex) {
        if (!isDeoptTarget) {
            return nextDeoptIndex;
        }
        FrameState initialState = kit.getGraph().start().stateAfter();
        return appendDeoptWithExceptionUnwind(kit, initialState, initialState.bci, nextDeoptIndex);
    }

    private static void instantiatePod(HostedGraphKit kit, HostedProviders providers, ResolvedJavaType factoryType, ResolvedJavaType podConcreteType, int instanceLocal) {
        ResolvedJavaType podType = kit.getMetaAccess().lookupJavaType(Pod.class);
        ValueNode receiver = kit.loadLocal(0, JavaKind.Object);
        ValueNode pod = loadNonNullField(kit, receiver, findField(factoryType, "pod"));
        ValueNode arrayLength = kit.createLoadField(pod, findField(podType, "arrayLength"));
        ValueNode refMap = loadNonNullField(kit, pod, findField(podType, "referenceMap"));
        ConstantNode hub = kit.createConstant(providers.getConstantReflection().asObjectHub(podConcreteType), JavaKind.Object);
        ValueNode instance = kit.append(new NewPodInstanceNode(podConcreteType, hub, arrayLength, refMap));
        kit.storeLocal(instanceLocal, JavaKind.Object, instance);
    }

    private static ValueNode loadNonNullField(HostedGraphKit kit, ValueNode object, ResolvedJavaField field) {
        return kit.append(PiNode.create(kit.createLoadField(object, field), StampFactory.objectNonNull()));
    }

    private static int invokeConstructor(HostedGraphKit kit, ResolvedJavaMethod method, boolean isDeoptTarget, int nextDeoptIndex, ResolvedJavaMethod targetCtor, int instanceLocal) {
        ValueNode instance = kit.loadLocal(instanceLocal, JavaKind.Object);
        ValueNode[] originalArgs = kit.loadArguments(method.toParameterTypes()).toArray(ValueNode.EMPTY_ARRAY);
        ValueNode[] invokeArgs = Arrays.copyOf(originalArgs, originalArgs.length);
        invokeArgs[0] = instance;
        return invokeWithDeoptAndExceptionUnwind(kit, isDeoptTarget, nextDeoptIndex, targetCtor, InvokeKind.Special, invokeArgs);
    }

    /** @see com.oracle.svm.hosted.phases.HostedGraphBuilderPhase */
    private static int invokeWithDeoptAndExceptionUnwind(HostedGraphKit kit, boolean isDeoptTarget, int initialNextDeoptIndex, ResolvedJavaMethod target, InvokeKind invokeKind, ValueNode... args) {
        int bci = kit.bci();
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(target, invokeKind, kit.getFrameState(), bci, args);
        invoke.setNodeSourcePosition(NodeSourcePosition.placeholder(kit.getGraph().method(), bci));
        kit.exceptionPart();
        ExceptionObjectNode exception = kit.exceptionObject();

        if (!isDeoptTarget) {
            kit.append(new UnwindNode(exception));
            kit.endInvokeWithException();
            return initialNextDeoptIndex;
        }

        int nextDeoptIndex = initialNextDeoptIndex;

        // Exception during invoke

        var exceptionDeopt = kit.add(new DeoptEntryNode());
        exceptionDeopt.setStateAfter(exception.stateAfter().duplicate());
        var exceptionDeoptBegin = kit.add(new DeoptEntryBeginNode());
        int exceptionDeoptIndex = nextDeoptIndex++;
        ValueNode exceptionProxy = createDeoptProxy(kit, exceptionDeoptIndex, exceptionDeopt, exception);
        var unwind = kit.append(new UnwindNode(exceptionProxy));
        exception.setNext(exceptionDeopt);
        exceptionDeopt.setNext(exceptionDeoptBegin);
        exceptionDeoptBegin.setNext(unwind);

        var exceptionDeoptExceptionEdge = kit.add(new UnreachableBeginNode());
        exceptionDeoptExceptionEdge.setNext(kit.add(new LoweredDeadEndNode()));
        exceptionDeopt.setExceptionEdge(exceptionDeoptExceptionEdge);

        // Deopt entry after invoke without exception

        kit.noExceptionPart();
        nextDeoptIndex = appendDeoptWithExceptionUnwind(kit, invoke.stateAfter(), invoke.stateAfter().bci, nextDeoptIndex);
        kit.endInvokeWithException();

        return nextDeoptIndex;
    }

    /** @see com.oracle.svm.hosted.phases.HostedGraphBuilderPhase */
    private static int appendDeoptWithExceptionUnwind(HostedGraphKit kit, FrameState state, int exceptionBci, int nextDeoptIndex) {
        var entry = kit.add(new DeoptEntryNode());
        entry.setStateAfter(state.duplicate());
        var begin = kit.append(new DeoptEntryBeginNode());
        ((FixedWithNextNode) begin.predecessor()).setNext(entry);
        entry.setNext(begin);

        ExceptionObjectNode exception = kit.add(new ExceptionObjectNode(kit.getMetaAccess()));
        entry.setExceptionEdge(exception);
        var exState = kit.getFrameState().copy();
        exState.clearStack();
        exState.push(JavaKind.Object, exception);
        exState.setRethrowException(true);
        exception.setStateAfter(exState.create(exceptionBci, exception));
        exception.setNext(kit.add(new UnwindNode(exception)));

        // Ensure later nodes see values from potential deoptimization
        kit.getFrameState().insertProxies(value -> createDeoptProxy(kit, nextDeoptIndex, entry, value));
        return nextDeoptIndex + 1;
    }

    private static ValueNode createDeoptProxy(HostedGraphKit kit, int nextDeoptIndex, FixedNode deoptTarget, ValueNode value) {
        return kit.getGraph().addOrUniqueWithInputs(DeoptProxyNode.create(value, deoptTarget, nextDeoptIndex));
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
