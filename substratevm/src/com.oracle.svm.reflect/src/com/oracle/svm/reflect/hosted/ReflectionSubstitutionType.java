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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.PointerEqualsNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.graal.nodes.DeadEndNode;
import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.ExceptionHelpers;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.CustomSubstitutionField;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.annotation.CustomSubstitutionType;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.DeletedMethod;
import com.oracle.svm.reflect.hosted.ReflectionSubstitutionType.ReflectionSubstitutionMethod;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class ReflectionSubstitutionType extends CustomSubstitutionType<CustomSubstitutionField, ReflectionSubstitutionMethod> {

    private String stableName;

    public ReflectionSubstitutionType(ResolvedJavaType original, Member member) {
        super(original);
        stableName = "L" + getStableProxyName(member).replace(".", "/") + ";";
        for (ResolvedJavaMethod method : original.getDeclaredMethods()) {
            switch (method.getName()) {
                case "invoke":
                    addSubstitutionMethod(method, new ReflectiveInvokeMethod(method, (Method) member));
                    break;
                case "get":
                    addSubstitutionMethod(method, new ReflectiveReadMethod(method, (Field) member, JavaKind.Object));
                    break;
                case "getBoolean":
                    addSubstitutionMethod(method, new ReflectiveReadMethod(method, (Field) member, JavaKind.Boolean));
                    break;
                case "getByte":
                    addSubstitutionMethod(method, new ReflectiveReadMethod(method, (Field) member, JavaKind.Byte));
                    break;
                case "getShort":
                    addSubstitutionMethod(method, new ReflectiveReadMethod(method, (Field) member, JavaKind.Short));
                    break;
                case "getChar":
                    addSubstitutionMethod(method, new ReflectiveReadMethod(method, (Field) member, JavaKind.Char));
                    break;
                case "getInt":
                    addSubstitutionMethod(method, new ReflectiveReadMethod(method, (Field) member, JavaKind.Int));
                    break;
                case "getLong":
                    addSubstitutionMethod(method, new ReflectiveReadMethod(method, (Field) member, JavaKind.Long));
                    break;
                case "getFloat":
                    addSubstitutionMethod(method, new ReflectiveReadMethod(method, (Field) member, JavaKind.Float));
                    break;
                case "getDouble":
                    addSubstitutionMethod(method, new ReflectiveReadMethod(method, (Field) member, JavaKind.Double));
                    break;
                case "set":
                    addSubstitutionMethod(method, createWriteMethod(method, (Field) member, JavaKind.Object));
                    break;
                case "setBoolean":
                    addSubstitutionMethod(method, createWriteMethod(method, (Field) member, JavaKind.Boolean));
                    break;
                case "setByte":
                    addSubstitutionMethod(method, createWriteMethod(method, (Field) member, JavaKind.Byte));
                    break;
                case "setShort":
                    addSubstitutionMethod(method, createWriteMethod(method, (Field) member, JavaKind.Short));
                    break;
                case "setChar":
                    addSubstitutionMethod(method, createWriteMethod(method, (Field) member, JavaKind.Char));
                    break;
                case "setInt":
                    addSubstitutionMethod(method, createWriteMethod(method, (Field) member, JavaKind.Int));
                    break;
                case "setLong":
                    addSubstitutionMethod(method, createWriteMethod(method, (Field) member, JavaKind.Long));
                    break;
                case "setFloat":
                    addSubstitutionMethod(method, createWriteMethod(method, (Field) member, JavaKind.Float));
                    break;
                case "setDouble":
                    addSubstitutionMethod(method, createWriteMethod(method, (Field) member, JavaKind.Double));
                    break;
                case "newInstance":
                    Class<?> holder = member.getDeclaringClass();
                    if (Modifier.isAbstract(holder.getModifiers()) || holder.isInterface() || holder.isPrimitive() || holder.isArray()) {
                        /*
                         * Invoking the constructor of an abstract class always throws an
                         * InstantiationException. It should not be possible to get a Constructor
                         * object for an interface, array, or primitive type, but we are defensive
                         * and throw the exception in that case too.
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
                default:
                    throw VMError.shouldNotReachHere("unexpected method: " + method.getName());
            }
        }
    }

    private static ReflectionSubstitutionMethod createWriteMethod(ResolvedJavaMethod method, Field field, JavaKind kind) {
        ReflectionDataBuilder reflectionDataBuilder = (ReflectionDataBuilder) ImageSingletons.lookup(RuntimeReflectionSupport.class);
        if (Modifier.isFinal(field.getModifiers()) && !reflectionDataBuilder.inspectFinalFieldWritableForAnalysis(field)) {
            return new ThrowingMethod(method, IllegalAccessException.class, "Cannot set final field: " + field.getDeclaringClass().getName() +
                            "." + field.getName() + ". " + "Enable by specifying \"allowWrite\" for this field in the reflection configuration.");
        }
        return new ReflectiveWriteMethod(method, field, kind);
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
        graphKit.append(new DeadEndNode());
    }

    private static ValueNode createCheckcast(HostedGraphKit graphKit, ValueNode value, ResolvedJavaType type, boolean nonNull) {
        TypeReference typeRef = TypeReference.createTrusted(graphKit.getAssumptions(), type);
        LogicNode condition;
        if (nonNull) {
            condition = graphKit.append(InstanceOfNode.create(typeRef, value));
        } else {
            condition = graphKit.append(InstanceOfNode.createAllowNull(typeRef, value, null, null));
        }

        graphKit.startIf(condition, BranchProbabilityNode.FAST_PATH_PROBABILITY);
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
        graphKit.startIf(argsNullCondition, BranchProbabilityNode.SLOW_PATH_PROBABILITY);
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
        graphKit.startIf(argsLengthCondition, BranchProbabilityNode.FAST_PATH_PROBABILITY);
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
        graphKit.append(new DeadEndNode());
    }

    private static boolean canImplicitCast(JavaKind from, JavaKind to) {
        if (from == to) {
            return true;
        }

        switch (to) {
            case Object:
                // boxing can be possible
                return true;
            case Boolean:
            case Char:
                return false;
        }

        switch (from) {
            case Byte:
                return true;
            case Short:
                return to != JavaKind.Byte;
            case Char:
                return to != JavaKind.Byte && to != JavaKind.Short;
            case Int:
                return to == JavaKind.Long || to.isNumericFloat();
            case Long:
            case Float:
                return to.isNumericFloat();
            default:
                return false;
        }
    }

    private static ValueNode doImplicitCast(HostedGraphKit graphKit, JavaKind from, JavaKind to, ValueNode value) {
        assert canImplicitCast(from, to);
        if (from == to) {
            return value;
        }

        switch (to) {
            case Object:
                ResolvedJavaType boxedRetType = graphKit.getMetaAccess().lookupJavaType(from.toBoxedJavaClass());
                return graphKit.createBoxing(value, from, boxedRetType);
            case Float:
                switch (from) {
                    case Int:
                        return graphKit.append(new FloatConvertNode(FloatConvert.I2F, value));
                    case Long:
                        return graphKit.append(new FloatConvertNode(FloatConvert.L2F, value));
                }
                break;
            case Double:
                switch (from) {
                    case Float:
                        return graphKit.append(new FloatConvertNode(FloatConvert.F2D, value));
                    case Int:
                        return graphKit.append(new FloatConvertNode(FloatConvert.I2D, value));
                    case Long:
                        return graphKit.append(new FloatConvertNode(FloatConvert.L2D, value));
                }
                break;
            case Short:
            case Int:
                assert from.isNumericInteger() && from.getBitCount() < to.getBitCount();
                /* All values smaller than 32 bit always have a 32-bit stamp. */
                return value;
            case Long:
                assert from.isNumericInteger() && from.getBitCount() < to.getBitCount();
                if (from.isUnsigned()) {
                    return graphKit.append(ZeroExtendNode.create(value, to.getBitCount(), NodeView.DEFAULT));
                } else {
                    return graphKit.append(SignExtendNode.create(value, to.getBitCount(), NodeView.DEFAULT));
                }
            default:
                throw VMError.shouldNotReachHere();
        }

        assert from.isNumericInteger() && from.getByteCount() < 4;
        ValueNode intermediate = doImplicitCast(graphKit, from, JavaKind.Int, value);
        return doImplicitCast(graphKit, JavaKind.Int, to, intermediate);
    }

    private static boolean isDeletedField(ResolvedJavaField field) {
        return GuardedAnnotationAccess.isAnnotationPresent(field, Delete.class);
    }

    private static void handleDeletedField(HostedGraphKit graphKit, HostedProviders providers, ResolvedJavaField field, JavaKind returnKind) {
        Delete deleteAnnotation = GuardedAnnotationAccess.getAnnotation(field, Delete.class);
        String msg = AnnotationSubstitutionProcessor.deleteErrorMessage(field, deleteAnnotation.value(), false);
        ValueNode msgNode = ConstantNode.forConstant(SubstrateObjectConstant.forObject(msg), providers.getMetaAccess(), graphKit.getGraph());
        ResolvedJavaMethod reportErrorMethod = providers.getMetaAccess().lookupJavaMethod(DeletedMethod.reportErrorMethod);
        graphKit.createInvokeWithExceptionAndUnwind(reportErrorMethod, InvokeKind.Static, graphKit.getFrameState(), graphKit.bci(), msgNode);
        ConstantNode returnValue = null;
        if (returnKind != JavaKind.Void) {
            returnValue = graphKit.unique(ConstantNode.defaultForKind(returnKind));
        }
        graphKit.createReturn(returnValue, returnKind);
    }

    private static class ReflectiveReadMethod extends ReflectionSubstitutionMethod {

        private final Field field;
        private final JavaKind kind;

        ReflectiveReadMethod(ResolvedJavaMethod original, Field field, JavaKind kind) {
            super(original);
            this.field = field;
            this.kind = kind;
        }

        @Override
        public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
            HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, method);
            ResolvedJavaField targetField = providers.getMetaAccess().lookupJavaField(field);

            if (isDeletedField(targetField)) {
                handleDeletedField(graphKit, providers, targetField, kind);

            } else if (canImplicitCast(graphKit.asKind(targetField.getType()), kind)) {

                ValueNode receiver;
                if (targetField.isStatic()) {
                    receiver = null;
                    graphKit.emitEnsureInitializedCall(targetField.getDeclaringClass());
                } else {
                    receiver = graphKit.loadLocal(1, JavaKind.Object);
                    receiver = createCheckcast(graphKit, receiver, targetField.getDeclaringClass(), true);
                }

                ValueNode ret = graphKit.append(LoadFieldNode.create(graphKit.getAssumptions(), receiver, targetField));
                ret = doImplicitCast(graphKit, graphKit.asKind(targetField.getType()), kind, ret);

                graphKit.createReturn(ret, kind);

            } else {
                throwIllegalArgumentException(graphKit, "cannot read field of type " + graphKit.asKind(targetField.getType()) + " with " + method.getName());
            }

            return graphKit.finalizeGraph();
        }
    }

    private static class ReflectiveWriteMethod extends ReflectionSubstitutionMethod {

        private final Field field;
        private final JavaKind kind;

        ReflectiveWriteMethod(ResolvedJavaMethod original, Field field, JavaKind kind) {
            super(original);
            this.field = field;
            this.kind = kind;
        }

        @Override
        public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
            HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, method);
            ResolvedJavaField targetField = providers.getMetaAccess().lookupJavaField(field);

            JavaKind fieldKind = graphKit.asKind(targetField.getType());
            if (isDeletedField(targetField)) {
                handleDeletedField(graphKit, providers, targetField, JavaKind.Void);

            } else if (kind == JavaKind.Object || canImplicitCast(kind, fieldKind)) {

                ValueNode receiver;
                if (targetField.isStatic()) {
                    receiver = null;
                    graphKit.emitEnsureInitializedCall(targetField.getDeclaringClass());
                } else {
                    receiver = graphKit.loadLocal(1, JavaKind.Object);
                    receiver = createCheckcast(graphKit, receiver, targetField.getDeclaringClass(), true);
                }

                ValueNode value = graphKit.loadLocal(2, kind);

                if (kind == JavaKind.Object) {
                    if (fieldKind.isPrimitive()) {
                        for (JavaKind valueKind : JavaKind.values()) {
                            // if cascade for every input kind we accept
                            if (canImplicitCast(valueKind, fieldKind)) {
                                ResolvedJavaType type = providers.getMetaAccess().lookupJavaType(valueKind.toBoxedJavaClass());
                                TypeReference typeRef = TypeReference.createTrusted(graphKit.getAssumptions(), type);
                                LogicNode condition = graphKit.append(InstanceOfNode.create(typeRef, value));

                                graphKit.startIf(condition, 0.5);

                                graphKit.thenPart();
                                PiNode boxed = graphKit.createPiNode(value, StampFactory.object(typeRef, true));
                                ValueNode unboxed = graphKit.createUnboxing(boxed, valueKind, providers.getMetaAccess());
                                ValueNode converted = doImplicitCast(graphKit, valueKind, fieldKind, unboxed);

                                graphKit.append(new StoreFieldNode(receiver, targetField, converted));
                                graphKit.createReturn(null, JavaKind.Void);

                                graphKit.elsePart();
                            }
                        }

                        // else: error
                        ResolvedJavaType expectedType = providers.getMetaAccess().lookupJavaType(fieldKind.toBoxedJavaClass());
                        throwFailedCast(graphKit, expectedType, value);
                    } else {
                        // kind == JavaKind.Object && fieldKind == JavaKind.Object
                        ResolvedJavaType type = providers.getMetaAccess().lookupJavaType(field.getType());
                        value = createCheckcast(graphKit, value, type, false);
                        graphKit.append(new StoreFieldNode(receiver, targetField, value));
                        graphKit.createReturn(null, JavaKind.Void);
                    }
                } else {
                    // kind == PrimitiveKind
                    if (fieldKind == JavaKind.Object && !field.getType().equals(kind.toBoxedJavaClass())) {
                        throwIllegalArgumentException(graphKit, "cannot write field of type " + targetField.getJavaKind() + " with Field." + method.getName());
                    } else {
                        value = doImplicitCast(graphKit, kind, fieldKind, value);
                        graphKit.append(new StoreFieldNode(receiver, targetField, value));
                        graphKit.createReturn(null, JavaKind.Void);
                    }
                }

            } else {
                throwIllegalArgumentException(graphKit, "cannot write field of type " + targetField.getJavaKind() + " with Field." + method.getName());
            }

            return graphKit.finalizeGraph();
        }
    }

    private static class ReflectiveInvokeMethod extends ReflectionSubstitutionMethod {

        private final Method method;

        ReflectiveInvokeMethod(ResolvedJavaMethod original, Method method) {
            super(original);
            this.method = method;
        }

        @Override
        public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod m, HostedProviders providers, Purpose purpose) {
            HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, m);

            ResolvedJavaMethod targetMethod = providers.getMetaAccess().lookupJavaMethod(method);
            Class<?>[] argTypes = method.getParameterTypes();

            int receiverOffset = targetMethod.isStatic() ? 0 : 1;
            ValueNode[] args = new ValueNode[argTypes.length + receiverOffset];
            if (targetMethod.isStatic()) {
                graphKit.emitEnsureInitializedCall(targetMethod.getDeclaringClass());
            } else {
                ValueNode receiver = graphKit.loadLocal(1, JavaKind.Object);
                args[0] = createCheckcast(graphKit, receiver, targetMethod.getDeclaringClass(), true);
            }

            ValueNode argumentArray = graphKit.loadLocal(2, JavaKind.Object);
            fillArgsArray(graphKit, argumentArray, receiverOffset, args, argTypes);

            InvokeKind invokeKind;
            if (targetMethod.isStatic()) {
                invokeKind = InvokeKind.Static;
            } else if (targetMethod.isInterface()) {
                invokeKind = InvokeKind.Interface;
            } else if (targetMethod.canBeStaticallyBound() || targetMethod.isConstructor()) {
                invokeKind = InvokeKind.Special;
            } else {
                invokeKind = InvokeKind.Virtual;
            }
            ValueNode ret = graphKit.createJavaCallWithException(invokeKind, targetMethod, args);

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

            return graphKit.finalizeGraph();
        }
    }

    private static class ReflectiveNewInstanceMethod extends ReflectionSubstitutionMethod {

        private final Constructor<?> constructor;

        ReflectiveNewInstanceMethod(ResolvedJavaMethod original, Constructor<?> constructor) {
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

            ValueNode ret = graphKit.append(new NewInstanceNode(type, true));

            ValueNode[] args = new ValueNode[argTypes.length + 1];
            args[0] = ret;

            ValueNode argumentArray = graphKit.loadLocal(1, JavaKind.Object);
            fillArgsArray(graphKit, argumentArray, 1, args, argTypes);

            graphKit.createJavaCallWithException(InvokeKind.Special, cons, args);

            graphKit.noExceptionPart();
            graphKit.createReturn(ret, JavaKind.Object);

            graphKit.exceptionPart();
            graphKit.throwInvocationTargetException(graphKit.exceptionObject());

            graphKit.endInvokeWithException();

            return graphKit.finalizeGraph();
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

            graphKit.startIf(otherIsNull, BranchProbabilityNode.NOT_LIKELY_PROBABILITY);

            graphKit.thenPart();

            graphKit.createReturn(falseValue, JavaKind.Boolean);

            graphKit.elsePart();

            ValueNode otherNonNull = graphKit.createPiNode(other, StampFactory.objectNonNull());

            ValueNode selfHub = graphKit.unique(new LoadHubNode(providers.getStampProvider(), self));
            ValueNode otherHub = graphKit.unique(new LoadHubNode(providers.getStampProvider(), otherNonNull));

            LogicNode equals = graphKit.unique(PointerEqualsNode.create(selfHub, otherHub, NodeView.DEFAULT));

            graphKit.startIf(equals, BranchProbabilityNode.NOT_LIKELY_PROBABILITY);
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
