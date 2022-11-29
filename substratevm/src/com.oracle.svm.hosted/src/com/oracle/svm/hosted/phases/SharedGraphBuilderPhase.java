/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.java.BciBlockMapping;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnreachableBeginNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.constraints.TypeInstantiationException;
import com.oracle.graal.pointsto.constraints.UnresolvedElementException;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.graal.nodes.DeoptEntryBeginNode;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.DeoptEntrySupport;
import com.oracle.svm.core.graal.nodes.DeoptProxyAnchorNode;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.LinkAtBuildTimeSupport;
import com.oracle.svm.hosted.nodes.DeoptProxyNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class SharedGraphBuilderPhase extends GraphBuilderPhase.Instance {
    final WordTypes wordTypes;

    public SharedGraphBuilderPhase(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext,
                    WordTypes wordTypes) {
        super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        this.wordTypes = wordTypes;
    }

    @Override
    protected void run(StructuredGraph graph) {
        super.run(graph);
        assert wordTypes == null || wordTypes.ensureGraphContainsNoWordTypeReferences(graph);
    }

    public abstract static class SharedBytecodeParser extends BytecodeParser {

        private int currentDeoptIndex;

        private final boolean explicitExceptionEdges;
        private final boolean linkAtBuildTime;

        protected SharedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, boolean explicitExceptionEdges) {
            this(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, explicitExceptionEdges, LinkAtBuildTimeSupport.singleton().linkAtBuildTime(method.getDeclaringClass()));
        }

        protected SharedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, boolean explicitExceptionEdges, boolean linkAtBuildTime) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
            this.explicitExceptionEdges = explicitExceptionEdges;
            this.linkAtBuildTime = linkAtBuildTime;
        }

        @Override
        protected BciBlockMapping generateBlockMap() {
            if (isDeoptimizationEnabled() && isMethodDeoptTarget()) {
                /*
                 * Need to add blocks representing where deoptimization entrypoint nodes will be
                 * inserted.
                 */
                return DeoptimizationTargetBciBlockMapping.create(stream, code, options, graph.getDebug(), false);
            } else {
                return BciBlockMapping.create(stream, code, options, graph.getDebug(), asyncExceptionLiveness());
            }
        }

        @Override
        protected void build(FixedWithNextNode startInstruction, FrameStateBuilder startFrameState) {
            super.build(startInstruction, startFrameState);

            if (isMethodDeoptTarget()) {
                /*
                 * All DeoptProxyNodes should be valid.
                 */
                for (DeoptProxyNode deoptProxy : graph.getNodes(DeoptProxyNode.TYPE)) {
                    assert deoptProxy.hasProxyPoint();
                }
            }
        }

        @Override
        protected RuntimeException throwParserError(Throwable e) {
            if (e instanceof UserException) {
                throw (UserException) e;
            }
            throw super.throwParserError(e);
        }

        private WordTypes getWordTypes() {
            return ((SharedGraphBuilderPhase) getGraphBuilderInstance()).wordTypes;
        }

        private boolean checkWordTypes() {
            return getWordTypes() != null;
        }

        /**
         * {@link Fold} and {@link NodeIntrinsic} can be deferred during parsing/decoding. Only by
         * the end of {@linkplain SnippetTemplate#instantiate Snippet instantiation} do they need to
         * have been processed.
         *
         * This is how SVM handles snippets. They are parsed with plugins disabled and then encoded
         * and stored in the image. When the snippet is needed at runtime the graph is decoded and
         * the plugins are run during the decoding process. If they aren't handled at this point
         * then they will never be handled.
         */
        @Override
        public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
            return plugin.getSource().equals(Fold.class) || plugin.getSource().equals(NodeIntrinsic.class);
        }

        @Override
        protected JavaMethod lookupMethodInPool(int cpi, int opcode) {
            JavaMethod result = super.lookupMethodInPool(cpi, opcode);
            if (result == null) {
                throw VMError.shouldNotReachHere("Discovered an unresolved callee while parsing " + method.asStackTraceElement(bci()) + '.');
            }
            return result;
        }

        /**
         * Native image can suffer high contention when synchronizing resolution and initialization
         * of a type referenced by a constant pool entry. Such synchronization should be unnecessary
         * for native-image.
         */
        @Override
        protected Object loadReferenceTypeLock() {
            return null;
        }

        @Override
        protected void maybeEagerlyResolve(int cpi, int bytecode) {
            try {
                super.maybeEagerlyResolve(cpi, bytecode);
            } catch (UnresolvedElementException e) {
                if (e.getCause() instanceof LinkageError || e.getCause() instanceof IllegalAccessError) {
                    /*
                     * Ignore LinkageError if thrown from eager resolution attempt. This is usually
                     * followed by a call to ConstantPool.lookupType() which should return an
                     * UnresolvedJavaType which we know how to deal with.
                     */
                } else {
                    throw e;
                }
            }
        }

        @Override
        protected JavaType maybeEagerlyResolve(JavaType type, ResolvedJavaType accessingClass) {
            try {
                return super.maybeEagerlyResolve(type, accessingClass);
            } catch (LinkageError e) {
                /*
                 * Type resolution fails if the type is missing or has an incompatible change. Just
                 * erase the type by returning the Object type. This is the same handling as in
                 * WrappedConstantPool, which is not triggering when parsing is done with the
                 * HotSpot universe instead of the AnalysisUniverse.
                 */
                return getMetaAccess().lookupJavaType(Object.class);
            }
        }

        @Override
        protected void handleIllegalNewInstance(JavaType type) {
            /*
             * If linkAtBuildTime was set for type, report the error during image building,
             * otherwise defer the error reporting to runtime.
             */
            if (linkAtBuildTime) {
                String message = "Cannot instantiate " + type.toJavaName() + ". " +
                                LinkAtBuildTimeSupport.singleton().errorMessageFor(method.getDeclaringClass());
                throw new TypeInstantiationException(message);
            } else {
                ExceptionSynthesizer.throwException(this, InstantiationError.class, type.toJavaName());
            }
        }

        @Override
        protected void handleUnresolvedNewInstance(JavaType type) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedNewObjectArray(JavaType type, ValueNode length) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedNewMultiArray(JavaType type, ValueNode[] dims) {
            handleUnresolvedType(type.getElementalType());
        }

        @Override
        protected void handleUnresolvedInstanceOf(JavaType type, ValueNode object) {
            // The INSTANCEOF byte code refers to a type that could not be resolved.
            // INSTANCEOF must not throw an exception if the object is null.
            BeginNode nullObj = graph.add(new BeginNode());
            BeginNode nonNullObj = graph.add(new BeginNode());
            append(new IfNode(graph.addOrUniqueWithInputs(IsNullNode.create(object)),
                            nullObj, nonNullObj, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            // Case where the object is not null, and type could not be resolved: Throw an
            // exception.
            lastInstr = nonNullObj;
            handleUnresolvedType(type);

            // Case where the object is null: INSTANCEOF does not care about the type.
            // Push zero to the byte code stack, then continue running normally.
            lastInstr = nullObj;
            frameState.push(JavaKind.Int, appendConstant(JavaConstant.INT_0));
        }

        @Override
        protected void handleUnresolvedCheckCast(JavaType type, ValueNode object) {
            // The CHECKCAST byte code refers to a type that could not be resolved.
            // CHECKCAST must throw an exception if, and only if, the object is not null.
            BeginNode nullObj = graph.add(new BeginNode());
            BeginNode nonNullObj = graph.add(new BeginNode());
            append(new IfNode(graph.addOrUniqueWithInputs(IsNullNode.create(object)),
                            nullObj, nonNullObj, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            // Case where the object is not null, and type could not be resolved: Throw an
            // exception.
            lastInstr = nonNullObj;
            handleUnresolvedType(type);

            // Case where the object is null: CHECKCAST does not care about the type.
            // Push "null" to the byte code stack, then continue running normally.
            lastInstr = nullObj;
            frameState.push(JavaKind.Object, appendConstant(JavaConstant.NULL_POINTER));
        }

        @Override
        protected void handleUnresolvedLoadConstant(JavaType type) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedExceptionType(JavaType type) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedStoreField(JavaField field, ValueNode value, ValueNode receiver) {
            handleUnresolvedField(field);
        }

        @Override
        protected void handleUnresolvedLoadField(JavaField field, ValueNode receiver) {
            handleUnresolvedField(field);
        }

        @Override
        protected void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind) {
            handleUnresolvedMethod(javaMethod);
        }

        private void handleUnresolvedType(JavaType type) {
            /*
             * If linkAtBuildTime was set for type, report the error during image building,
             * otherwise defer the error reporting to runtime.
             */
            if (linkAtBuildTime) {
                reportUnresolvedElement("type", type.toJavaName());
            } else {
                ExceptionSynthesizer.throwException(this, NoClassDefFoundError.class, type.toJavaName());
            }
        }

        private void handleUnresolvedField(JavaField field) {
            JavaType declaringClass = field.getDeclaringClass();
            if (!typeIsResolved(declaringClass)) {
                /* The field could not be resolved because its declaring class is missing. */
                handleUnresolvedType(declaringClass);
            } else {
                /*
                 * If linkAtBuildTime was set for type, report the error during image building,
                 * otherwise defer the error reporting to runtime.
                 */
                if (linkAtBuildTime) {
                    reportUnresolvedElement("field", field.format("%H.%n"));
                } else {
                    ExceptionSynthesizer.throwException(this, NoSuchFieldError.class, field.format("%H.%n"));
                }
            }
        }

        private void handleUnresolvedMethod(JavaMethod javaMethod) {
            JavaType declaringClass = javaMethod.getDeclaringClass();
            if (!typeIsResolved(declaringClass)) {
                /* The method could not be resolved because its declaring class is missing. */
                handleUnresolvedType(declaringClass);
            } else {
                /*
                 * If linkAtBuildTime was set for type, report the error during image building,
                 * otherwise defer the error reporting to runtime.
                 */
                if (linkAtBuildTime) {
                    reportUnresolvedElement("method", javaMethod.format("%H.%n(%P)"));
                } else {
                    ExceptionSynthesizer.throwException(this, NoSuchMethodError.class, javaMethod.format("%H.%n(%P)"));
                }
            }
        }

        private void reportUnresolvedElement(String elementKind, String elementAsString) {
            String message = "Discovered unresolved " + elementKind + " during parsing: " + elementAsString + ". " +
                            LinkAtBuildTimeSupport.singleton().errorMessageFor(method.getDeclaringClass());
            throw new UnresolvedElementException(message);
        }

        @Override
        protected void emitCheckForInvokeSuperSpecial(ValueNode[] args) {
            /* Not implemented in SVM (GR-4854) */
        }

        @Override
        protected boolean canInlinePartialIntrinsicExit() {
            return false;
        }

        @Override
        protected void genIf(ValueNode x, Condition cond, ValueNode y) {
            if (checkWordTypes()) {
                if ((x.getStackKind() == JavaKind.Object && y.getStackKind() == getWordTypes().getWordKind()) ||
                                (x.getStackKind() == getWordTypes().getWordKind() && y.getStackKind() == JavaKind.Object)) {
                    throw UserError.abort("Should not compare Word to Object in condition at %s in %s", method, method.asStackTraceElement(bci()));
                }
            }

            super.genIf(x, cond, y);
        }

        @Override
        public MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, JavaTypeProfile profile) {
            boolean isStatic = targetMethod.isStatic();
            if (!isStatic) {
                checkWordType(args[0], targetMethod.getDeclaringClass(), "call receiver");
            }
            for (int i = 0; i < targetMethod.getSignature().getParameterCount(false); i++) {
                checkWordType(args[i + (isStatic ? 0 : 1)], targetMethod.getSignature().getParameterType(i, null), "call argument");
            }

            return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp, profile, null, profile);
        }

        @Override
        protected void genReturn(ValueNode returnVal, JavaKind returnKind) {
            checkWordType(returnVal, method.getSignature().getReturnType(null), "return value");

            super.genReturn(returnVal, returnKind);
        }

        private void checkWordType(ValueNode value, JavaType expectedType, String reason) {
            if (expectedType.getJavaKind() == JavaKind.Object && checkWordTypes()) {
                boolean isWordTypeExpected = getWordTypes().isWord(expectedType);
                boolean isWordValue = value.getStackKind() == getWordTypes().getWordKind();

                if (isWordTypeExpected && !isWordValue) {
                    throw UserError.abort("Expected Word but got Object for %s in %s", reason, method.asStackTraceElement(bci()));
                } else if (!isWordTypeExpected && isWordValue) {
                    throw UserError.abort("Expected Object but got Word for %s in %s", reason, method.asStackTraceElement(bci()));
                }
            }
        }

        @Override
        protected boolean needsExplicitNullCheckException(ValueNode object) {
            return needsExplicitException() && object.getStackKind() == JavaKind.Object;
        }

        @Override
        protected boolean needsExplicitStoreCheckException(ValueNode array, ValueNode value) {
            return needsExplicitException() && value.getStackKind() == JavaKind.Object;
        }

        @Override
        public boolean needsExplicitException() {
            return explicitExceptionEdges && !parsingIntrinsic();
        }

        @Override
        public boolean isPluginEnabled(GraphBuilderPlugin plugin) {
            return true;
        }

        protected static boolean isDeoptimizationEnabled() {
            return DeoptimizationSupport.enabled() && !SubstrateUtil.isBuildingLibgraal();
        }

        protected boolean isMethodDeoptTarget() {
            return method instanceof SharedMethod && ((SharedMethod) method).isDeoptTarget();
        }

        @Override
        protected boolean asyncExceptionLiveness() {
            /*
             * If deoptimization is enabled, then must assume that any method can deoptimize at any
             * point while throwing an exception.
             */
            return isDeoptimizationEnabled();
        }

        @Override
        protected void clearNonLiveLocalsAtTargetCreation(BciBlockMapping.BciBlock block, FrameStateBuilder state) {
            /*
             * In order to match potential DeoptEntryNodes, within runtime compiled code it is not
             * possible to clear non-live locals at the start of a exception dispatch block if
             * deoptimizations can be present, as exception dispatch blocks have the same deopt bci
             * as the exception.
             */
            if ((!(isDeoptimizationEnabled() && block instanceof BciBlockMapping.ExceptionDispatchBlock)) || isMethodDeoptTarget()) {
                super.clearNonLiveLocalsAtTargetCreation(block, state);
            }
        }

        @Override
        protected void clearNonLiveLocalsAtLoopExitCreation(BciBlockMapping.BciBlock block, FrameStateBuilder state) {
            /*
             * In order to match potential DeoptEntryNodes, within runtime compiled code it is not
             * possible to clear non-live locals when deoptimizations can be present.
             */
            if (!isDeoptimizationEnabled() || isMethodDeoptTarget()) {
                super.clearNonLiveLocalsAtLoopExitCreation(block, state);
            }
        }

        @Override
        protected void createExceptionDispatch(BciBlockMapping.ExceptionDispatchBlock block) {
            if (block instanceof DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) {
                /*
                 * If this block is an DeoptEntryInsertionPoint, then a DeoptEntry must be inserted.
                 * Afterwards, this block should jump to either the original ExceptionDispatchBlock
                 * or the UnwindBlock if there is no handler.
                 */
                assert block instanceof DeoptimizationTargetBciBlockMapping.DeoptExceptionDispatchBlock;
                insertDeoptNode((DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) block);
                List<BciBlockMapping.BciBlock> successors = block.getSuccessors();
                assert successors.size() <= 1;
                BciBlockMapping.BciBlock successor = successors.isEmpty() ? blockMap.getUnwindBlock() : successors.get(0);
                appendGoto(successor);
            } else {
                super.createExceptionDispatch(block);
            }
        }

        @Override
        protected void iterateBytecodesForBlock(BciBlockMapping.BciBlock block) {
            if (block instanceof DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) {
                /*
                 * If this block is an DeoptEntryInsertionPoint, then a DeoptEntry must be inserted.
                 * Afterwards, this block should jump to the original BciBlock.
                 */
                assert block instanceof DeoptimizationTargetBciBlockMapping.DeoptBciBlock;
                assert block.getSuccessors().size() == 1 || block.getSuccessors().size() == 2;
                assert block.getSuccessor(0).isInstructionBlock();
                stream.setBCI(block.getStartBci());
                insertDeoptNode((DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) block);
                appendGoto(block.getSuccessor(0));
            } else {
                super.iterateBytecodesForBlock(block);
            }
        }

        /**
         * Inserts either a DeoptEntryNode or DeoptProxyAnchorNode into the graph.
         */
        private void insertDeoptNode(DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint deopt) {
            /*
             * Ensuring current frameState matches the expectations of the DeoptEntryInsertionPoint.
             */
            if (deopt instanceof DeoptimizationTargetBciBlockMapping.DeoptBciBlock) {
                assert !frameState.rethrowException();
            } else {
                assert deopt instanceof DeoptimizationTargetBciBlockMapping.DeoptExceptionDispatchBlock;
                assert frameState.rethrowException();
            }

            DeoptEntrySupport deoptNode = graph.add(deopt.isProxy() ? new DeoptProxyAnchorNode() : new DeoptEntryNode());
            FrameState stateAfter = frameState.create(deopt.frameStateBci(), deoptNode);
            deoptNode.setStateAfter(stateAfter);
            if (lastInstr != null) {
                lastInstr.setNext(deoptNode.asFixedNode());
            }

            if (deopt.isProxy()) {
                lastInstr = (DeoptProxyAnchorNode) deoptNode;
            } else {
                assert !deopt.duringCall() : "Implicit deopt entries from invokes cannot have explicit deopt entries.";
                DeoptEntryNode deoptEntryNode = (DeoptEntryNode) deoptNode;
                deoptEntryNode.setNext(graph.add(new DeoptEntryBeginNode()));

                /*
                 * DeoptEntries for positions not during an exception dispatch (rethrowException)
                 * also must be linked to their exception target.
                 */
                if (!deopt.rethrowException()) {
                    /*
                     * Saving frameState so that different modifications can be made for next() and
                     * exceptionEdge().
                     */
                    FrameStateBuilder originalFrameState = frameState.copy();

                    /* Creating exception object and its state after. */
                    ExceptionObjectNode newExceptionObject = graph.add(new ExceptionObjectNode(getMetaAccess()));
                    frameState.clearStack();
                    frameState.push(JavaKind.Object, newExceptionObject);
                    frameState.setRethrowException(true);
                    int bci = ((DeoptimizationTargetBciBlockMapping.DeoptBciBlock) deopt).getStartBci();
                    newExceptionObject.setStateAfter(frameState.create(bci, newExceptionObject));
                    deoptEntryNode.setExceptionEdge(newExceptionObject);

                    /* Inserting proxies for the exception edge. */
                    insertProxies(newExceptionObject, frameState);

                    /* Linking exception object to exception target. */
                    newExceptionObject.setNext(handleException(newExceptionObject, bci, false));

                    /* Now restoring FrameState so proxies can be inserted for the next() edge. */
                    frameState = originalFrameState;
                } else {
                    /* Otherwise, indicate that the exception edge is not reachable. */
                    AbstractBeginNode newExceptionEdge = graph.add(new UnreachableBeginNode());
                    newExceptionEdge.setNext(graph.add(new LoweredDeadEndNode()));
                    deoptEntryNode.setExceptionEdge(newExceptionEdge);
                }

                /* Correctly setting last instruction. */
                lastInstr = deoptEntryNode.next();
            }

            insertProxies(deoptNode.asFixedNode(), frameState);
        }

        private void insertProxies(FixedNode deoptTarget, FrameStateBuilder state) {
            /*
             * At a deoptimization point we wrap non-constant locals (and java stack elements) with
             * proxy nodes. This is to avoid global value numbering on locals (or derived
             * expressions). The effect is that when a local is accessed after a deoptimization
             * point it is really loaded from its location. This is similar to what happens in the
             * GraphBuilderPhase if entryBCI is set for OSR.
             */
            state.insertProxies(value -> createProxyNode(value, deoptTarget));
            currentDeoptIndex++;
        }

        private ValueNode createProxyNode(ValueNode value, FixedNode deoptTarget) {
            ValueNode v = DeoptProxyNode.create(value, deoptTarget, currentDeoptIndex);
            if (v.graph() != null) {
                return v;
            }
            return graph.addOrUniqueWithInputs(v);
        }

        @Override
        protected boolean forceLoopPhis() {
            return isMethodDeoptTarget() || super.forceLoopPhis();
        }

        @Override
        public boolean allowDeoptInPlugins() {
            return super.allowDeoptInPlugins();
        }

    }
}
