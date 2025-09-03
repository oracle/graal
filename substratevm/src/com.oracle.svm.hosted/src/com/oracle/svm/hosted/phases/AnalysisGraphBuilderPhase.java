/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.List;

import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.bootstrap.BootstrapMethodConfiguration;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.util.ModuleSupport;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.meta.ConstantPool.BootstrapMethodInvocation;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

public class AnalysisGraphBuilderPhase extends SharedGraphBuilderPhase {

    protected final SVMHost hostVM;

    public AnalysisGraphBuilderPhase(CoreProviders providers,
                    GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext, SVMHost hostVM) {
        super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        this.hostVM = hostVM;
    }

    @Override
    protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        return new AnalysisBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext, hostVM, true);
    }

    public static class AnalysisBytecodeParser extends SharedBytecodeParser {

        private final SVMHost hostVM;

        @SuppressWarnings("this-escape")
        protected AnalysisBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, SVMHost hostVM, boolean explicitExceptionEdges) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, explicitExceptionEdges);
            this.hostVM = hostVM;
        }

        @Override
        protected boolean tryInvocationPlugin(InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType) {
            boolean result = super.tryInvocationPlugin(invokeKind, args, targetMethod, resultType);
            if (result) {
                ((AnalysisMethod) targetMethod).registerAsIntrinsicMethod(nonNullReason(graph.currentNodeSourcePosition()));
            }
            return result;
        }

        private static Object nonNullReason(Object reason) {
            return reason == null ? "Unknown invocation location." : reason;
        }

        @Override
        protected boolean applyInvocationPlugin(InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType, InvocationPlugin plugin) {
            Class<? extends InvocationPlugin> accessingClass = plugin.getClass();
            /*
             * The annotation-processor creates InvocationPlugins in classes in modules that e.g.
             * use the @Fold annotation. This way InvocationPlugins can be in various classes in
             * various modules. For these InvocationPlugins to do their work they need access to
             * bits of graal. Thus the modules that contain such plugins need to be allowed such
             * access.
             */
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, accessingClass, false, "jdk.internal.vm.ci", "jdk.vm.ci.meta");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, accessingClass, false, "jdk.graal.compiler", "jdk.graal.compiler.nodes");
            return super.applyInvocationPlugin(invokeKind, args, targetMethod, resultType, plugin);
        }

        private boolean tryNodePluginForDynamicInvocation(BootstrapMethodInvocation bootstrap) {
            for (NodePlugin plugin : graphBuilderConfig.getPlugins().getNodePlugins()) {
                var result = plugin.convertInvokeDynamic(this, bootstrap);
                if (result != null) {
                    appendInvoke(InvokeKind.Static, result.getLeft(), result.getRight(), null);
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void genInvokeDynamic(int cpi, int opcode) {
            BootstrapMethodInvocation bootstrap;
            try {
                bootstrap = constantPool.lookupBootstrapMethodInvocation(cpi, opcode);
            } catch (Throwable ex) {
                bootstrapMethodHandler.handleBootstrapException(ex, "invoke dynamic");
                return;
            }
            if (bootstrap != null && tryNodePluginForDynamicInvocation(bootstrap)) {
                return;
            }
            JavaMethod calleeMethod = lookupMethodInPool(cpi, opcode);

            if (bootstrap == null || calleeMethod instanceof ResolvedJavaMethod ||
                            BootstrapMethodConfiguration.singleton().isIndyAllowedAtBuildTime(OriginalMethodProvider.getJavaMethod(bootstrap.getMethod()))) {
                super.genInvokeDynamic(cpi, opcode);
                return;
            }

            int parameterLength = bootstrap.getMethod().getParameters().length;
            List<JavaConstant> staticArgumentsList = bootstrap.getStaticArguments();
            boolean isVarargs = bootstrap.getMethod().isVarArgs();
            int bci = bci();
            MethodType methodType = getSnippetReflection().asObject(MethodType.class, bootstrap.getType());

            for (JavaConstant argument : staticArgumentsList) {
                Object arg = getSnippetReflection().asObject(Object.class, argument);
                if (arg instanceof UnresolvedJavaType unresolvedJavaType) {
                    handleUnresolvedType(unresolvedJavaType);
                    return;
                }
            }

            if (!bootstrapMethodHandler.checkBootstrapParameters(bootstrap.getMethod(), staticArgumentsList, false)) {
                WrongMethodTypeException cause = new WrongMethodTypeException("Cannot convert " + methodType + " to correct MethodType");
                replaceWithThrowingAtRuntime(this, new BootstrapMethodError("Bootstrap method initialization exception", cause));
                return;
            }

            /*
             * Steps 1-4: Fetch the linked call site and execute the bootstrap method if needed (see
             * resolveLinkedObject for details).
             */

            Object initializedCallSite = bootstrapMethodHandler.resolveLinkedObject(bci, cpi, opcode, bootstrap, parameterLength, staticArgumentsList, isVarargs, false);
            if (initializedCallSite instanceof UnresolvedJavaType unresolvedJavaType) {
                handleUnresolvedType(unresolvedJavaType);
                return;
            }
            if (initializedCallSite instanceof Throwable) {
                return;
            }
            ValueNode initializedCallSiteNode = (ValueNode) initializedCallSite;

            /*
             * Check if the CallSite returned is null and throw a BootstrapMethodError if it is.
             */

            LogicNode isInitializedCallSiteNodeNull = graph.unique(IsNullNode.create(initializedCallSiteNode));
            createBytecodeExceptionCheck(bci, isInitializedCallSiteNodeNull, BytecodeExceptionKind.NULL_POINTER, false);

            /*
             * Cast the object stored in the BootstrapMethodInfo to ensure it is a CallSite. Doing
             * so allows to correctly throw a ClassCastException as hotspot does.
             */
            ResolvedJavaType callSiteType = getMetaAccess().lookupJavaType(CallSite.class);
            LogicNode isInstanceOfCallSite = graph.unique(InstanceOfNode.create(TypeReference.create(getAssumptions(), callSiteType), initializedCallSiteNode));
            ValueNode callSiteClass = ConstantNode.forConstant(StampFactory.forKind(JavaKind.Object), getConstantReflection().asJavaClass(callSiteType), getMetaAccess(), getGraph());
            createBytecodeExceptionCheck(bci, isInstanceOfCallSite, BytecodeExceptionKind.CLASS_CAST, true, initializedCallSiteNode, callSiteClass);

            bootstrapMethodHandler.invokeMethodAndAppend(bci, CallSite.class, MethodHandle.class, "dynamicInvoker", InvokeKind.Virtual, new ValueNode[]{initializedCallSiteNode});
            ValueNode methodHandleNode = frameState.pop(JavaKind.Object);

            bootstrapMethodHandler.invokeMethodAndAppend(bci, MethodHandle.class, MethodType.class, "type", InvokeKind.Virtual, new ValueNode[]{methodHandleNode});
            ValueNode callSiteMethodTypeNode = frameState.pop(JavaKind.Object);

            LogicNode checkMethodTypeEqual = graph.unique(
                            ObjectEqualsNode.create(callSiteMethodTypeNode, ConstantNode.forConstant(bootstrap.getType(), getMetaAccess(), getGraph()), getConstantReflection(), NodeView.DEFAULT));

            EndNode checkMethodTypeEqualTrueEnd = graph.add(new EndNode());
            EndNode checkMethodTypeEqualFalseEnd = graph.add(new EndNode());

            JavaConstant wrongMethodTypeException = getSnippetReflection().forObject(new WrongMethodTypeException("CallSite MethodType should be of type " + methodType));
            ConstantNode wrongMethodTypeExceptionNode = ConstantNode.forConstant(StampFactory.forKind(JavaKind.Object), wrongMethodTypeException, getMetaAccess(), getGraph());
            InvokeWithExceptionNode throwWrongMethodTypeNode = bootstrapMethodHandler.throwBootstrapMethodError(bci, wrongMethodTypeExceptionNode);
            throwWrongMethodTypeNode.setNext(checkMethodTypeEqualFalseEnd);

            append(new IfNode(checkMethodTypeEqual, checkMethodTypeEqualTrueEnd, throwWrongMethodTypeNode, BranchProbabilityNode.NOT_LIKELY_PROFILE));

            MergeNode checkMethodTypeEqualMergeNode = append(new MergeNode());
            checkMethodTypeEqualMergeNode.setStateAfter(createFrameState(stream.nextBCI(), checkMethodTypeEqualMergeNode));
            checkMethodTypeEqualMergeNode.addForwardEnd(checkMethodTypeEqualTrueEnd);
            checkMethodTypeEqualMergeNode.addForwardEnd(checkMethodTypeEqualFalseEnd);

            /* Step 5.1: Prepare the arguments for invoking the MethodHandle. */

            int paramLength = calleeMethod.getSignature().getParameterCount(false);
            ValueNode[] invokeExactArguments = popArguments(paramLength);
            NewArrayNode newArrayNode = append(new NewArrayNode(getMetaAccess().lookupJavaType(Object.class), ConstantNode.forInt(paramLength, getGraph()), true));
            for (int i = 0; i < paramLength; ++i) {
                JavaKind stackKind = invokeExactArguments[i].getStackKind();
                if (stackKind.isPrimitive()) {
                    /*
                     * Primitive parameter have to be boxed because invokeExact takes a list of
                     * Objects as argument.
                     */
                    invokeExactArguments[i] = append(BoxNode.create(invokeExactArguments[i], getMetaAccess().lookupJavaType(stackKind.toBoxedJavaClass()), stackKind));
                }
                var storeIndexed = add(new StoreIndexedNode(newArrayNode, ConstantNode.forInt(i, getGraph()), null, null, JavaKind.Object, invokeExactArguments[i]));
                storeIndexed.stateAfter().invalidateForDeoptimization();
            }
            ValueNode[] invokeArguments = new ValueNode[]{methodHandleNode, newArrayNode};

            /* Step 5.2: Invoke the MethodHandle. */

            Class<?> returnType = methodType.returnType();
            JavaKind returnKind = getMetaAccessExtensionProvider().getStorageKind(getMetaAccess().lookupJavaType(returnType));
            bootstrapMethodHandler.invokeMethodAndAppend(bci, MethodHandle.class, Object.class, "invokeExact", InvokeKind.Virtual, invokeArguments, Object.class.arrayType());
            if (returnKind.equals(JavaKind.Void)) {
                frameState.pop(JavaKind.Object);
            } else if (returnKind.isPrimitive()) {
                /* If the return type is a primitive, unbox the result of invokeExact. */
                frameState.push(returnKind, append(UnboxNode.create(getMetaAccess(), getConstantReflection(), frameState.pop(JavaKind.Object), returnKind)));
            }
        }

        private void createBytecodeExceptionCheck(int bci, LogicNode logicNode, BytecodeExceptionKind exception, boolean passingOnTrue, ValueNode... arguments) {
            AbstractBeginNode passingPath = emitBytecodeExceptionCheck(logicNode, passingOnTrue, exception, arguments);
            IfNode bytecodeExceptionIfNode = (IfNode) passingPath.predecessor();
            FixedWithNextNode bytecodeException = (FixedWithNextNode) (passingOnTrue ? bytecodeExceptionIfNode.falseSuccessor() : bytecodeExceptionIfNode.trueSuccessor()).next();
            InvokeWithExceptionNode bootstrapMethodError = bootstrapMethodHandler.throwBootstrapMethodError(bci, bytecodeException);
            FixedNode bytecodeExceptionNext = bytecodeException.next();
            bytecodeException.setNext(bootstrapMethodError);
            bootstrapMethodError.setNext(bytecodeExceptionNext);
        }

        @Override
        protected void genStoreField(ValueNode receiver, ResolvedJavaField field, ValueNode value) {
            hostVM.recordFieldStore(field, method);
            super.genStoreField(receiver, field, value);
        }

        @Override
        protected FrameStateBuilder createFrameStateForExceptionHandling(int bci) {
            var dispatchState = super.createFrameStateForExceptionHandling(bci);
            /*
             * It is beneficial to eagerly clear all non-live locals on the exception object before
             * entering the dispatch target. This helps us prune unneeded values from the graph,
             * which can positively impact our analysis. Since deoptimization is not possible, then
             * there is no risk in clearing the unneeded locals.
             */
            AnalysisMethod aMethod = (AnalysisMethod) method;
            if (aMethod.isOriginalMethod() && !SubstrateCompilationDirectives.singleton().isRegisteredForDeoptTesting(aMethod)) {
                BciBlockMapping.BciBlock dispatchBlock = getDispatchBlock(bci);
                clearNonLiveLocals(dispatchState, dispatchBlock, true);
            }
            return dispatchState;
        }
    }
}
