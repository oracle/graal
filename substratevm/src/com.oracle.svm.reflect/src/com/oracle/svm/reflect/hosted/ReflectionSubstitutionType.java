/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

// Checkstyle: allow reflection

import static com.oracle.svm.reflect.hosted.ReflectionSubstitution.getStableProxyName;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.PointerEqualsNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.SubstrateIntrinsicGraphBuilder;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.invoke.MethodHandleUtils;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.util.ExceptionHelpers;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.CustomSubstitutionField;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.annotation.CustomSubstitutionType;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.reflect.hosted.ReflectionSubstitutionType.ReflectionSubstitutionMethod;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/** Represents a {@link java.lang.reflect.Method} or {@link java.lang.reflect.Constructor}. */
public class ReflectionSubstitutionType extends CustomSubstitutionType<CustomSubstitutionField, ReflectionSubstitutionMethod> {

    private final String stableName;

    public static class Factory {
        public ReflectionSubstitutionType create(ResolvedJavaType original, Executable member) {
            return new ReflectionSubstitutionType(original, member);
        }

        public void inspectAccessibleField(@SuppressWarnings("unused") Field field) {
        }
    }

    /**
     * Build a substitution for a reflective call.
     *
     * @param original The {@link ResolvedJavaType} of the {@linkplain Member} class (i.e. a
     *            {@link ResolvedJavaType} representing {@link Field}, {@link Constructor} or
     *            {@link Method}).
     * @param member The {@link Member} which we are reflectively accessing.
     */
    protected ReflectionSubstitutionType(ResolvedJavaType original, Executable member) {
        super(original);
        stableName = "L" + getStableProxyName(member).replace(".", "/") + ";";
        for (ResolvedJavaMethod method : original.getDeclaredMethods()) {
            createAndAddSubstitutionMethod(method, member);
        }
    }

    protected void createAndAddSubstitutionMethod(ResolvedJavaMethod method, Member member) {
        switch (method.getName()) {
            case "invoke":
                addSubstitutionMethod(method, new ReflectiveInvokeMethod(method, (Method) member, false));
                break;
            case "invokeSpecial":
                addSubstitutionMethod(method, new ReflectiveInvokeMethod(method, (Method) member, true));
                break;
            case "newInstance":
                Class<?> holder = member.getDeclaringClass();
                if (Modifier.isAbstract(holder.getModifiers()) || holder.isInterface() || holder.isPrimitive() || holder.isArray()) {
                    /*
                     * Invoking the constructor of an abstract class always throws an
                     * InstantiationException. It should not be possible to get a Constructor object
                     * for an interface, array, or primitive type, but we are defensive and throw
                     * the exception in that case too.
                     */
                    addSubstitutionMethod(method, new ThrowingMethod(method, InstantiationException.class, "Cannot instantiate " + holder));
                } else {
                    addSubstitutionMethod(method, new ReflectiveNewInstanceMethod(method, (Constructor<?>) member));
                }
                break;
            case "toString":
                addSubstitutionMethod(method, new ToStringMethod(method, member.getName()));
                break;
            case "hashCode":
                addSubstitutionMethod(method, new HashCodeMethod(method, member.hashCode()));
                break;
            case "equals":
                addSubstitutionMethod(method, new EqualsMethod(method));
                break;
            case "proxyClassLookup":
                addSubstitutionMethod(method, new ProxyClassLookupMethod(method, member));
                break;
            default:
                throw VMError.shouldNotReachHere("unexpected method: " + method.getName());
        }
    }

    @Override
    public String getName() {
        return stableName;
    }

    public abstract static class ReflectionSubstitutionMethod extends CustomSubstitutionMethod {

        public ReflectionSubstitutionMethod(ResolvedJavaMethod original) {
            super(original);
        }

        @Override
        public int getMaxLocals() {
            return original.getMaxLocals();
        }
    }

    private static void throwFailedCast(HostedGraphKit graphKit, ResolvedJavaType expectedType, ValueNode actual) {
        ResolvedJavaMethod throwFailedCast = graphKit.findMethod(ExceptionHelpers.class, "throwFailedCast", true);
        JavaConstant expected = graphKit.getConstantReflection().asJavaClass(expectedType);
        ValueNode expectedNode = graphKit.createConstant(expected, JavaKind.Object);

        graphKit.createJavaCallWithExceptionAndUnwind(InvokeKind.Static, throwFailedCast, expectedNode, actual);
        graphKit.append(new LoweredDeadEndNode());
    }

    private static ValueNode createCheckcast(HostedGraphKit graphKit, ValueNode value, ResolvedJavaType type, boolean nonNull) {
        TypeReference typeRef = TypeReference.createTrusted(graphKit.getAssumptions(), type);
        LogicNode condition;
        if (nonNull) {
            condition = graphKit.append(InstanceOfNode.create(typeRef, value));
        } else {
            condition = graphKit.append(InstanceOfNode.createAllowNull(typeRef, value, null, null));
        }

        graphKit.startIf(condition, BranchProbabilityNode.FAST_PATH_PROFILE);
        graphKit.thenPart();

        PiNode ret = graphKit.createPiNode(value, StampFactory.object(typeRef, nonNull));

        graphKit.elsePart();

        throwFailedCast(graphKit, type, value);

        graphKit.endIf();

        return ret;
    }

    private static void fillArgsArray(HostedGraphKit graphKit, ValueNode argumentArray, int receiverOffset, ValueNode[] args, Class<?>[] argTypes) {
        /*
         * The length of the args array at run time must be the same as the length of argTypes.
         * Unless the length of argTypes is 0: in that case, null is allowed to be passed in at run
         * time too.
         */
        LogicNode argsNullCondition = graphKit.append(IsNullNode.create(argumentArray));
        graphKit.startIf(argsNullCondition, BranchProbabilityNode.SLOW_PATH_PROFILE);
        graphKit.thenPart();
        if (argTypes.length == 0) {
            /* No arguments, so null is an allowed value. */
        } else {
            throwIllegalArgumentException(graphKit, "wrong number of arguments");
        }
        graphKit.elsePart();
        PiNode argumentArrayNonNull = graphKit.createPiNode(argumentArray, StampFactory.objectNonNull());

        ValueNode argsLength = graphKit.append(ArrayLengthNode.create(argumentArrayNonNull, graphKit.getConstantReflection()));
        LogicNode argsLengthCondition = graphKit.append(IntegerEqualsNode.create(argsLength, ConstantNode.forInt(argTypes.length), NodeView.DEFAULT));
        graphKit.startIf(argsLengthCondition, BranchProbabilityNode.FAST_PATH_PROFILE);
        graphKit.thenPart();

        for (int i = 0; i < argTypes.length; i++) {
            ValueNode arg = graphKit.createLoadIndexed(argumentArrayNonNull, i, JavaKind.Object);
            ResolvedJavaType argType = graphKit.getMetaAccess().lookupJavaType(argTypes[i]);
            JavaKind argKind = graphKit.asKind(argType);
            if (argKind.isPrimitive()) {
                arg = createCheckcast(graphKit, arg, graphKit.getMetaAccess().lookupJavaType(argKind.toBoxedJavaClass()), true);
                arg = graphKit.createUnboxing(arg, argKind, graphKit.getMetaAccess());
            } else {
                arg = createCheckcast(graphKit, arg, argType, false);
            }

            args[i + receiverOffset] = arg;
        }

        graphKit.elsePart();
        throwIllegalArgumentException(graphKit, "wrong number of arguments");
        graphKit.endIf();

        AbstractMergeNode merge = graphKit.endIf();
        if (merge != null) {
            /* When argTypes.length == 0 there is an actual merge that needs a state. */
            merge.setStateAfter(graphKit.getFrameState().create(graphKit.bci(), merge));
        }
    }

    private static void throwIllegalArgumentException(HostedGraphKit graphKit, String message) {
        ResolvedJavaMethod throwIllegalArgumentException = graphKit.findMethod(ExceptionHelpers.class, "throwIllegalArgumentException", true);
        JavaConstant msg = graphKit.getConstantReflection().forString(message);
        ValueNode msgNode = graphKit.createConstant(msg, JavaKind.Object);

        graphKit.createJavaCallWithExceptionAndUnwind(InvokeKind.Static, throwIllegalArgumentException, msgNode);
        graphKit.append(new LoweredDeadEndNode());
    }

    private static class ReflectiveInvokeMethod extends ReflectionSubstitutionMethod {

        private final Method method;
        private final boolean specialInvoke;

        ReflectiveInvokeMethod(ResolvedJavaMethod original, Method method, boolean specialInvoke) {
            super(original);
            this.method = method;
            this.specialInvoke = specialInvoke;
        }

        @Override
        public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod m, HostedProviders providers, Purpose purpose) {
            HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, m);

            ResolvedJavaMethod targetMethod;
            ValueNode[] args;
            if (!specialInvoke && method.getDeclaringClass() == MethodHandle.class && (method.getName().equals("invoke") || method.getName().equals("invokeExact"))) {
                targetMethod = MethodHandleUtils.getThrowUnsupportedOperationException(providers.getMetaAccess());
                args = new ValueNode[0];
            } else {
                targetMethod = providers.getMetaAccess().lookupJavaMethod(method);
                Class<?>[] argTypes = method.getParameterTypes();

                int receiverOffset = targetMethod.isStatic() ? 0 : 1;
                args = new ValueNode[argTypes.length + receiverOffset];
                if (targetMethod.isStatic()) {
                    graphKit.emitEnsureInitializedCall(targetMethod.getDeclaringClass());
                } else {
                    ValueNode receiver = graphKit.loadLocal(1, JavaKind.Object);
                    args[0] = createCheckcast(graphKit, receiver, targetMethod.getDeclaringClass(), true);
                }

                ValueNode argumentArray = graphKit.loadLocal(2, JavaKind.Object);
                fillArgsArray(graphKit, argumentArray, receiverOffset, args, argTypes);
            }

            InvokeKind invokeKind;
            if (specialInvoke) {
                invokeKind = InvokeKind.Special;
            } else if (targetMethod.isStatic()) {
                invokeKind = InvokeKind.Static;
            } else if (targetMethod.isInterface()) {
                invokeKind = InvokeKind.Interface;
            } else if (targetMethod.canBeStaticallyBound() || targetMethod.isConstructor()) {
                invokeKind = InvokeKind.Special;
            } else {
                invokeKind = InvokeKind.Virtual;
            }

            InvokeWithExceptionNode invoke = graphKit.createJavaCallWithException(invokeKind, targetMethod, args);
            ValueNode ret = invoke;

            graphKit.noExceptionPart();

            JavaKind retKind = targetMethod.getSignature().getReturnKind();
            if (retKind == JavaKind.Void) {
                ret = graphKit.createObject(null);
            } else if (retKind.isPrimitive()) {
                ResolvedJavaType boxedRetType = providers.getMetaAccess().lookupJavaType(retKind.toBoxedJavaClass());
                ret = graphKit.createBoxing(ret, retKind, boxedRetType);
            }

            graphKit.createReturn(ret, JavaKind.Object);

            graphKit.exceptionPart();
            graphKit.throwInvocationTargetException(graphKit.exceptionObject());

            graphKit.endInvokeWithException();

            if (invokeKind.isDirect()) {
                InvocationPlugin invocationPlugin = providers.getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(targetMethod);
                if (invocationPlugin != null && !invocationPlugin.inlineOnly()) {
                    /*
                     * The BytecodeParser applies invocation plugins directly during bytecode
                     * parsing. We cannot do that because GraphKit is not a GraphBuilderContext. To
                     * get as close as possible to the BytecodeParser behavior, we create a new
                     * graph for the intrinsic and inline it immediately.
                     */
                    Bytecode code = new ResolvedJavaMethodBytecode(targetMethod);
                    StructuredGraph intrinsicGraph = new SubstrateIntrinsicGraphBuilder(graphKit.getOptions(), graphKit.getDebug(), providers, code).buildGraph(invocationPlugin);
                    if (intrinsicGraph != null) {
                        InliningUtil.inline(invoke, intrinsicGraph, false, targetMethod);
                    }
                }
            }

            return graphKit.finalizeGraph();
        }
    }

    protected static class ReflectiveNewInstanceMethod extends ReflectionSubstitutionMethod {

        private final Constructor<?> constructor;

        protected ReflectiveNewInstanceMethod(ResolvedJavaMethod original, Constructor<?> constructor) {
            super(original);
            this.constructor = constructor;
        }

        @Override
        public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
            HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, method);

            ResolvedJavaType type = providers.getMetaAccess().lookupJavaType(constructor.getDeclaringClass());

            graphKit.emitEnsureInitializedCall(type);

            ResolvedJavaMethod cons = providers.getMetaAccess().lookupJavaMethod(constructor);
            Class<?>[] argTypes = constructor.getParameterTypes();

            ValueNode ret = graphKit.append(createNewInstanceNode(type));

            ValueNode[] args = new ValueNode[argTypes.length + 1];
            args[0] = ret;

            ValueNode argumentArray = graphKit.loadLocal(1, JavaKind.Object);
            fillArgsArray(graphKit, argumentArray, 1, args, argTypes);

            createJavaCall(graphKit, cons, ret, args);

            return graphKit.finalizeGraph();
        }

        protected void createJavaCall(HostedGraphKit graphKit, ResolvedJavaMethod cons, ValueNode ret, ValueNode[] args) {
            graphKit.createJavaCallWithException(InvokeKind.Special, cons, args);

            graphKit.noExceptionPart();
            graphKit.createReturn(ret, JavaKind.Object);

            graphKit.exceptionPart();
            graphKit.throwInvocationTargetException(graphKit.exceptionObject());

            graphKit.endInvokeWithException();
        }

        protected ValueNode createNewInstanceNode(ResolvedJavaType type) {
            return new NewInstanceNode(type, true);
        }
    }

    private static class ToStringMethod extends ReflectionSubstitutionMethod {

        private final String name;

        ToStringMethod(ResolvedJavaMethod original, String name) {
            super(original);
            this.name = name;
        }

        @Override
        public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
            HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, method);

            ValueNode nameNode = graphKit.createObject(name);
            graphKit.createReturn(nameNode, JavaKind.Object);

            return graphKit.finalizeGraph();
        }
    }

    private static class HashCodeMethod extends ReflectionSubstitutionMethod {

        private final int hashCode;

        HashCodeMethod(ResolvedJavaMethod original, int hashCode) {
            super(original);
            this.hashCode = hashCode;
        }

        @Override
        public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
            HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, method);

            ValueNode nameNode = graphKit.createInt(hashCode);
            graphKit.createReturn(nameNode, JavaKind.Int);

            return graphKit.finalizeGraph();
        }
    }

    private static class EqualsMethod extends ReflectionSubstitutionMethod {

        EqualsMethod(ResolvedJavaMethod original) {
            super(original);
        }

        @Override
        public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
            HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, method);

            ValueNode self = graphKit.loadLocal(0, JavaKind.Object);
            ValueNode other = graphKit.loadLocal(1, JavaKind.Object);

            ValueNode trueValue = graphKit.createInt(1);
            ValueNode falseValue = graphKit.createInt(0);

            LogicNode otherIsNull = graphKit.append(IsNullNode.create(other));

            graphKit.startIf(otherIsNull, BranchProbabilityNode.NOT_LIKELY_PROFILE);

            graphKit.thenPart();

            graphKit.createReturn(falseValue, JavaKind.Boolean);

            graphKit.elsePart();

            ValueNode otherNonNull = graphKit.createPiNode(other, StampFactory.objectNonNull());

            ValueNode selfHub = graphKit.unique(new LoadHubNode(providers.getStampProvider(), self));
            ValueNode otherHub = graphKit.unique(new LoadHubNode(providers.getStampProvider(), otherNonNull));

            LogicNode equals = graphKit.unique(PointerEqualsNode.create(selfHub, otherHub, NodeView.DEFAULT));

            graphKit.startIf(equals, BranchProbabilityNode.NOT_LIKELY_PROFILE);
            graphKit.thenPart();

            graphKit.createReturn(trueValue, JavaKind.Boolean);

            graphKit.elsePart();

            graphKit.createReturn(falseValue, JavaKind.Boolean);

            graphKit.endIf();

            graphKit.endIf();

            return graphKit.finalizeGraph();
        }
    }

    private static final class ThrowingMethod extends ReflectionSubstitutionMethod {

        private final Class<? extends Throwable> exceptionClass;
        private final String message;

        private ThrowingMethod(ResolvedJavaMethod original, Class<? extends Throwable> exceptionClass, String message) {
            super(original);
            this.exceptionClass = exceptionClass;
            this.message = message;
        }

        @Override
        public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
            HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, method);
            ResolvedJavaType exceptionType = graphKit.getMetaAccess().lookupJavaType(exceptionClass);
            ValueNode instance = graphKit.append(new NewInstanceNode(exceptionType, true));
            ResolvedJavaMethod cons = null;
            for (ResolvedJavaMethod c : exceptionType.getDeclaredConstructors()) {
                if (c.getSignature().getParameterCount(false) == 1) {
                    ResolvedJavaType stringType = providers.getMetaAccess().lookupJavaType(String.class);
                    if (c.getSignature().getParameterType(0, null).equals(stringType)) {
                        cons = c;
                    }
                }
            }
            JavaConstant msg = graphKit.getConstantReflection().forString(message);
            ValueNode msgNode = graphKit.createConstant(msg, JavaKind.Object);
            graphKit.createJavaCallWithExceptionAndUnwind(InvokeKind.Special, cons, instance, msgNode);
            graphKit.append(new UnwindNode(instance));

            return graphKit.finalizeGraph();
        }
    }

    private static final class ProxyClassLookupMethod extends ReflectionSubstitutionMethod {

        @SuppressWarnings("unused")//
        private final Member member;

        ProxyClassLookupMethod(ResolvedJavaMethod original, Member member) {
            super(original);
            this.member = member;
        }

        @Override
        public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
            // GR-28942: handle new proxyClassLookup method added to Proxy in JDK 16
            throw VMError.unimplemented();
        }
    }

    @Override
    public Annotation[] getAnnotations() {
        return InternalVMMethod.Holder.ARRAY;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return annotationClass == InternalVMMethod.class;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (annotationClass == InternalVMMethod.class) {
            return annotationClass.cast(InternalVMMethod.Holder.INSTANCE);
        }
        return null;
    }
}
