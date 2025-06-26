/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.codegen;

import static jdk.graal.compiler.core.common.calc.CanonicalCondition.BT;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.graal.nodes.FloatingWordCastNode;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.graal.nodes.ReadCallerStackPointerNode;
import com.oracle.svm.core.graal.nodes.ReadReturnAddressNode;
import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.graal.thread.LoadVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.StoreVMThreadLocalNode;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.JSCodeBuffer;
import com.oracle.svm.hosted.webimage.codegen.arithm.BinaryFloatOperationLowerer;
import com.oracle.svm.hosted.webimage.codegen.arithm.BinaryIntOperationLowerer;
import com.oracle.svm.hosted.webimage.codegen.heap.ConstantMap;
import com.oracle.svm.hosted.webimage.codegen.heap.JSBootImageHeapLowerer;
import com.oracle.svm.hosted.webimage.codegen.heap.WebImageObjectInspector;
import com.oracle.svm.hosted.webimage.codegen.long64.Long64Lowerer;
import com.oracle.svm.hosted.webimage.codegen.lowerer.PhiResolveLowerer;
import com.oracle.svm.hosted.webimage.codegen.node.CompoundConditionNode;
import com.oracle.svm.hosted.webimage.codegen.node.ReadIdentityHashCodeNode;
import com.oracle.svm.hosted.webimage.codegen.node.WriteIdentityHashCodeNode;
import com.oracle.svm.hosted.webimage.codegen.oop.ClassLowerer;
import com.oracle.svm.hosted.webimage.codegen.type.ClassMetadataLowerer;
import com.oracle.svm.hosted.webimage.codegen.type.InvokeLoweringUtil;
import com.oracle.svm.hosted.webimage.codegen.value.ResolvedVarLowerer;
import com.oracle.svm.hosted.webimage.codegen.wrappers.JSEmitter;
import com.oracle.svm.hosted.webimage.js.JSBody;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.hosted.webimage.snippets.JSSnippets;
import com.oracle.svm.webimage.functionintrinsics.ImplicitExceptions;
import com.oracle.svm.webimage.functionintrinsics.JSCallNode;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionDefinition;
import com.oracle.svm.webimage.functionintrinsics.JSSystemFunction;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;
import com.oracle.svm.webimage.hightiercodegen.Emitter;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;
import com.oracle.svm.webimage.hightiercodegen.NodeLowerer;
import com.oracle.svm.webimage.hightiercodegen.variables.ResolvedVar;
import com.oracle.svm.webimage.type.TypeControl;

import jdk.graal.compiler.core.amd64.AMD64AddressNode;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.graph.iterators.NodePredicates;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.UnreachableBeginNode;
import jdk.graal.compiler.nodes.UnreachableControlSinkNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.calc.AbsNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.RemNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.RoundNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerDivNode;
import jdk.graal.compiler.nodes.calc.SignedFloatingIntegerRemNode;
import jdk.graal.compiler.nodes.calc.SignumNode;
import jdk.graal.compiler.nodes.calc.SqrtNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnsignedDivNode;
import jdk.graal.compiler.nodes.calc.UnsignedRemNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.nodes.extended.AbstractBoxingNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.ClassIsArrayNode;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.extended.LoadArrayComponentHubNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.extended.ObjectIsArrayNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryLoadNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryStoreNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.AbstractUnsafeCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndAddNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.FinalFieldBarrierNode;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.java.ReachabilityFenceNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndExchangeNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndSwapNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.replacements.nodes.ArrayEqualsNode;
import jdk.graal.compiler.replacements.nodes.ArrayFillNode;
import jdk.graal.compiler.replacements.nodes.BasicArrayCopyNode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.ObjectClone;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.word.WordCastNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Lowering logic for nodes.
 */
public class WebImageJSNodeLowerer extends NodeLowerer {

    /**
     * This field is intentionally hiding the field in {@link NodeLowerer} so that we can use
     * {@link JSCodeGenTool} here.
     */
    protected final JSCodeGenTool codeGenTool;

    public WebImageJSNodeLowerer(JSCodeGenTool codeGenTool) {
        super(codeGenTool);
        this.codeGenTool = codeGenTool;
    }

    // ============================================================================
    // region [Common definitions]

    public static final boolean INT_PALADIN = true;

    /** Denotes nodes that do not need to be lowered. */
    public static final Set<Class<?>> IGNORED_NODE_TYPES = new HashSet<>(Arrays.asList(
                    MergeNode.class,
                    StartNode.class,
                    ValueAnchorNode.class,
                    BeginNode.class,
                    ExceptionObjectNode.LoweredExceptionObjectBegin.class,
                    UnreachableBeginNode.class,
                    LoopExitNode.class,
                    UnreachableControlSinkNode.class,
                    CallTargetNode.class,
                    CFunctionPrologueNode.class,
                    CFunctionEpilogueNode.class,
                    FixedGuardNode.class,
                    MonitorIdNode.class,
                    MembarNode.class,
                    VirtualState.class,
                    VirtualObjectNode.class,
                    FinalFieldBarrierNode.class));

    /** Denotes nodes that should not appear in lowering. */
    public static final Set<Class<?>> FORBIDDEN_NODE_TYPES = new HashSet<>(Arrays.asList(
                    ReadCallerStackPointerNode.class,
                    PhiNode.class,           // PhiNodes are handled specially
                    FormatObjectNode.class,
                    FormatArrayNode.class,
                    StackValueNode.class,
                    ReadReturnAddressNode.class,
                    LoadVMThreadLocalNode.class,
                    StoreVMThreadLocalNode.class,
                    CommitAllocationNode.class, // Removed in MaterializeAllocationsPhase
                    AllocatedObjectNode.class, // Removed in MaterializeAllocationsPhase
                    EnsureClassInitializedNode.class, // lowered in lowering provider
                    ClassIsArrayNode.class // lowered in lowering provider
    ));

    // ============================================================================
    // endregion

    // ============================================================================
    // region [Lowering logic]

    @Override
    protected void lowerVarDeclPrefix(ResolvedVar resolvedVar) {
        codeGenTool.genResolvedVarDeclPrefix(resolvedVar.getName());
        resolvedVar.setDefinitionLowered();
    }

    @Override
    protected void lower(ResolvedVar resolvedVar) {
        ResolvedVarLowerer.lower(resolvedVar, codeGenTool);
    }

    @Override
    protected void dispatch(Node node) {
        if (node instanceof LoweredDeadEndNode) {
            lowerLoweredDeadEndNode();
        } else if (node instanceof ThrowBytecodeExceptionNode throwBytecodeExceptionNode) {
            lower(throwBytecodeExceptionNode);
        } else if (node instanceof DeoptimizeNode) {
            lower((DeoptimizeNode) node);
        } else if (node instanceof JSCallNode) {
            lower((JSCallNode) node);
        } else if (node instanceof CompoundConditionNode) {
            lower((CompoundConditionNode) node);
        } else if (node instanceof JSBody jsBody) {
            lower(jsBody);
        } else if (node instanceof StaticFieldsSupport.StaticFieldResolvedBaseNode resolvedBaseNode) {
            lower(resolvedBaseNode);
        } else if (node instanceof ReadIdentityHashCodeNode readIdentityHashCodeNode) {
            lower(readIdentityHashCodeNode);
        } else if (node instanceof WriteIdentityHashCodeNode writeIdentityHashCodeNode) {
            lower(writeIdentityHashCodeNode);
        } else if (node instanceof FloatingWordCastNode floatingWordCastNode) {
            lower(floatingWordCastNode);
        } else {
            super.dispatch(node);
        }
    }

    @Override
    public boolean isIgnored(Node node) {
        for (Class<?> c : IGNORED_NODE_TYPES) {
            if (c.isInstance(node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isForbiddenNode(Node node) {
        for (Class<?> c : FORBIDDEN_NODE_TYPES) {
            if (c.isInstance(node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected String reportForbiddenNode(Node node) {
        return nodeDebugInfo(node);
    }

    protected void lower(ReadIdentityHashCodeNode readIdentityHashCodeNode) {
        /*
         * Reads the identity hash code field from an object. To handle objects that may not have
         * the field initialized (thus would return undefined), we add `|0` to the read to coerce it
         * to an int (undefined is mapped to 0)
         */
        JSEmitter.intPaladin(JSEmitter.of((t) -> {
            t.genPropertyAccess(Emitter.of(readIdentityHashCodeNode.getObject()), Emitter.of(ClassMetadataLowerer.INSTANCE_TYPE_HASHCODE_FIELD_NAME));
        })).lower(codeGenTool);
    }

    protected void lower(WriteIdentityHashCodeNode writeIdentityHashCodeNode) {
        codeGenTool.genPropertyAccess(Emitter.of(writeIdentityHashCodeNode.getObject()), Emitter.of(ClassMetadataLowerer.INSTANCE_TYPE_HASHCODE_FIELD_NAME));
        codeGenTool.genAssignment();
        lowerValue(writeIdentityHashCodeNode.getHashCode());
    }

    @Override
    protected void lower(NegateNode node) {
        if (node.getValue().getStackKind() == JavaKind.Long) {
            Long64Lowerer.genUnaryArithmeticOperation(node, node.getValue(), codeGenTool);
        } else {
            JSEmitter emitter = JSEmitter.of((t) -> {
                codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
                codeGenTool.getCodeBuffer().emitText("- ");
                lowerValue(node.getValue());
                codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
                if (node.getValue().stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                    codeGenTool.genInlineComment("Stamp bit size:" + ((IntegerStamp) node.stamp(NodeView.DEFAULT)).getBits() + " kind:" + node.getValue().getStackKind().toString());
                    codeGenTool.getCodeBuffer().emitText("&" + CodeUtil.mask(((IntegerStamp) node.stamp(NodeView.DEFAULT)).getBits()));
                }
            });

            if (INT_PALADIN && node.getValue().stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                emitter = JSEmitter.intPaladin(emitter);
            }

            emitter.lower(codeGenTool);
        }
    }

    @Override
    protected void lower(UnwindNode node) {
        codeGenTool.genThrow(node.exception());
    }

    @Override
    protected void lower(Invoke invoke) {
        assert invoke instanceof InvokeNode || invoke instanceof InvokeWithExceptionNode : invoke;
        CallTargetNode callTarget = invoke.callTarget();

        HostedMethod targetMethod = (HostedMethod) callTarget.targetMethod();
        Stamp receiverStamp;
        HostedType receiverType = null;
        if (invoke.getInvokeKind().hasReceiver()) {
            receiverStamp = invoke.getReceiver().stamp(NodeView.DEFAULT);
            assert receiverStamp != null;
            receiverType = (HostedType) receiverStamp.javaType(codeGenTool.getProviders().getMetaAccess());
            assert receiverType != null;
            codeGenTool.getProviders().debug().log("Receiver type: %s", receiverType);
            codeGenTool.getProviders().debug().log("Target method: %s", targetMethod);
            codeGenTool.getProviders().debug().log("Target type  : %s", targetMethod.getDeclaringClass());

            codeGenTool.genComment("Receiver type:" + receiverType);
        }
        if (targetMethod != null) {
            codeGenTool.genComment("Target method:" + targetMethod);
            codeGenTool.genComment("Target type  :" + targetMethod.getDeclaringClass().toString());
        }

        if (callTarget instanceof IndirectCallTargetNode) {
            assert targetMethod == null : "Indirect call target nodes are not expected to have target methods.";
            InvokeLoweringUtil.lowerIndirect(invoke, codeGenTool);
        } else if (invoke.getInvokeKind().hasReceiver()) {
            InvokeLoweringUtil.lowerOrPatchDynamic(targetMethod, receiverType, invoke, codeGenTool);
        } else {
            InvokeLoweringUtil.lowerOrPatchStatic(targetMethod, invoke, codeGenTool);
        }
    }

    @Override
    protected void lower(IsNullNode node) {
        lowerValue(node.getValue());
        // if we are undefined we want not tob e equal to null, as this means we generated
        // an error and we want to crash as soon as possible and not succeed any null check
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.EQQ);
        codeGenTool.genNull();
    }

    @Override
    protected void lower(ExceptionObjectNode node) {
        codeGenTool.getCodeBuffer().emitText(codeGenTool.getExceptionObjectId(node));
    }

    @Override
    protected void lower(ConstantNode node) {
        Constant c = node.asConstant();
        WebImageTypeControl typeControl = codeGenTool.getJSProviders().typeControl();
        ConstantMap constantMap = typeControl.getConstantMap();
        if ((c instanceof PrimitiveConstant && ((PrimitiveConstant) c).getJavaKind() == JavaKind.Long)) {
            Long64Lowerer.lowerFromConstant(c, codeGenTool);
        } else {
            if (c instanceof PrimitiveConstant) {
                lowerConstant((PrimitiveConstant) c, codeGenTool);
            } else {
                codeGenTool.genInlineComment(node.toString());
                codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
                if (JavaConstant.isNull(c)) {
                    codeGenTool.genNull();
                } else {
                    final String varName = constantMap.resolveConstantNode(node);
                    assert varName != null;
                    codeGenTool.getCodeBuffer().emitText(varName);
                }
                codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
            }
        }
    }

    @Override
    protected void lower(LoadIndexedNode node) {
        if (node.elementKind() == JavaKind.Long) {
            Runtime.BigInt64ArrayLoad.emitCall(codeGenTool, Emitter.of(node.array()), Emitter.of(node.index()));
        } else {
            codeGenTool.genArrayLoad(node.index(), node.array());
        }
    }

    @Override
    protected void lower(NewArrayNode node) {
        ResolvedVar resolvedVar = codeGenTool.getAllocatedVariable(node);
        if (resolvedVar == null) {
            // it is safe to skip the allocation if the result is not used
            assert actualUsageCount(node) == 0 : nodeDebugInfo(node);
            return;
        }

        HostedType t = (HostedType) node.elementType();
        // array initialization lowering
        Array.lowerNewArray(t, Emitter.of(node.length()), codeGenTool);
        codeGenTool.genResolvedVarDeclPostfix(node.toString());
    }

    @Override
    protected void lower(StoreIndexedNode node) {
        /*
         * no prefetch or initialization for array store operations, no intermediate val is created
         * for array store
         */
        if (node.elementKind() == JavaKind.Long) {
            Runtime.BigInt64ArrayStore.emitCall(codeGenTool, Emitter.of(node.array()), Emitter.of(node.index()), Emitter.of(node.value()));
        } else {
            codeGenTool.genArrayStore(Emitter.of(node.index()), node.array(), node.value());
        }
    }

    @Override
    protected void lower(ParameterNode node) {
        codeGenTool.genResolvedVarAccess(JSCodeBuffer.getParamName(node.index()));
    }

    @Override
    protected void lower(ConditionalNode node) {
        // condition node can be inlined, thus we need parentheses around the whole expression.
        // TODO: once we have an AST layer, we can avoid such concrete syntactical details.
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        lowerValue(node.condition());
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
        codeGenTool.getCodeBuffer().emitText("?");
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        lowerValue(node.trueValue());
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
        codeGenTool.getCodeBuffer().emitText(":");
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        lowerValue(node.falseValue());
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(BoxNode node) {
        HostedMetaAccess metaAccess = (HostedMetaAccess) codeGenTool.getProviders().getMetaAccess();

        ResolvedVar resolvedVar = codeGenTool.getAllocatedVariable(node);
        if (resolvedVar == null) {
            // If BoxNode has no usages, resolvedVar will be null which would cause a NPE.
            assert actualUsageCount(node) == 0 : nodeDebugInfo(node);
            return;
        }
        HostedType boxing = metaAccess.lookupJavaType(node.getBoxingKind().toBoxedJavaClass());

        codeGenTool.genObjectCreate(Emitter.of(boxing));
        codeGenTool.genResolvedVarDeclPostfix("Boxed");

        HostedField valueField = (HostedField) AbstractBoxingNode.getValueField(boxing);

        codeGenTool.genPropertyAccess(Emitter.of(resolvedVar), Emitter.of(valueField));
        codeGenTool.genAssignment();
        lowerValue(node.getValue());
    }

    @Override
    protected void lower(FloatConvertNode node) {
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        switch (node.getFloatConvert()) {
            case F2I:
            case D2I:
                Runtime.TO_INT.emitCall(codeGenTool, Emitter.of(node.getValue()));
                break;
            case F2L:
            case D2L:
                Runtime.TO_LONG.emitCall(codeGenTool, Emitter.of(node.getValue()));
                break;
            case D2F:
            case I2F:
                /*
                 * Because floats have lower precision, we use Math.fround to convert the given
                 * number to an approximated single precision float
                 */
                codeGenTool.getCodeBuffer().emitText("Math.fround(");
                lowerValue(node.getValue());
                codeGenTool.getCodeBuffer().emitText(")");
                break;
            case I2D:
                /*
                 * Integers in JavaScript are already represented as floating point values, and
                 * double-precision floats can store all possible integers, so no conversion is
                 * needed.
                 */
                lowerValue(node.getValue());
                break;
            case L2D:
                // generate the to number call
                Long64Lowerer.genUnaryArithmeticOperation(node, node.getValue(), codeGenTool);
                break;
            case L2F:
                /*
                 * This is basically L2D followed by D2F l.toNumber() already produces a double
                 * precision float approximation of the Long64, fround then gives us the nearest
                 * single precision 32-bit float
                 */
                codeGenTool.getCodeBuffer().emitText("Math.fround(");
                // generate the to number call
                Long64Lowerer.genUnaryArithmeticOperation(node, node.getValue(), codeGenTool);
                codeGenTool.getCodeBuffer().emitText(")");
                break;
            case F2D:
                // a increase in precision does not require any operation to be applied
                lowerValue(node.getValue());
                break;
            default:
                throw JVMCIError.shouldNotReachHere(node.getFloatConvert().toString());
        }
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(ArrayLengthNode node) {
        codeGenTool.genPropertyAccess(Emitter.of(node.array()), Emitter.of("length"));
    }

    @Override
    protected void lower(BasicArrayCopyNode node) {
        List<ValueNode> v = new ArrayList<>();
        v.add(node.getSource());
        v.add(node.getSourcePosition());
        v.add(node.getDestination());
        v.add(node.getDestinationPosition());
        v.add(node.getLength());
        JSCallNode.ARRAY_COPY.emitCall(codeGenTool, Emitter.of(v));
    }

    @Override
    protected void lower(IntegerTestNode node) {
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        if (node.getX().getStackKind() == JavaKind.Long || node.getY().getStackKind() == JavaKind.Long) {
            JVMCIError.guarantee(node.getX().getStackKind() == JavaKind.Long, "P1 must be long %s", node.getX().toString());
            JVMCIError.guarantee(node.getY().getStackKind() == JavaKind.Long, "P2 must be long %s", node.getY().toString());
            Long64Lowerer.genBinaryLogicNode(node, node.getX(), node.getY(), codeGenTool);
        } else {
            CodeBuffer masm = codeGenTool.getCodeBuffer();

            masm.emitText("(");
            lowerValue(node.getX());
            masm.emitText("&");
            lowerValue(node.getY());
            masm.emitText(") == 0 ");
        }
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(ReturnNode node) {
        if (node.result() == null) {
            codeGenTool.genVoidReturn();
        } else {
            codeGenTool.genReturn(node.result());
        }
    }

    @Override
    protected void lower(ObjectEqualsNode node) {
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        lowerValue(node.getX());
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.EQQ);
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        lowerValue(node.getY());
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(CompareNode node) {
        assert !(node instanceof ObjectEqualsNode) : "this method only handles numeric compare nodes";

        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        ValueNode lOp = node.getX();
        ValueNode rOp = node.getY();
        if (lOp.getStackKind() == JavaKind.Long) {
            assert rOp.getStackKind() == JavaKind.Long : rOp.getStackKind();
            Long64Lowerer.genBinaryLogicNode(node, node.getX(), node.getY(), codeGenTool);
        } else {
            assert lOp.getStackKind() != JavaKind.Long : lOp.getStackKind();
            assert rOp.getStackKind() != JavaKind.Long : rOp.getStackKind();
            if (node.condition() == BT) {
                Runtime.UnsignedIClI32.emitCall(codeGenTool, Emitter.of(lOp), Emitter.of(rOp));
            } else {
                codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
                if (node.unorderedIsTrue()) {
                    codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
                }
                lowerValue(node.getX());
                codeGenTool.genCondition(node.condition());
                lowerValue(node.getY());
                codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
                if (node.unorderedIsTrue()) {
                    codeGenTool.getCodeBuffer().emitText("||");
                    codeGenTool.getCodeBuffer().emitText("isNaN");
                    codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
                    lowerValue(node.getX());
                    codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);

                    codeGenTool.getCodeBuffer().emitText("||");

                    codeGenTool.getCodeBuffer().emitText("isNaN");
                    codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
                    lowerValue(node.getY());
                    codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);

                    codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
                }
            }
        }
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(DeadEndNode node) {
        codeGenTool.genShouldNotReachHere("Dead End"); // ExcludeFromJacocoGeneratedReport
    }

    protected void lowerLoweredDeadEndNode() {
        codeGenTool.genShouldNotReachHere("Lowered Dead End"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    protected void lower(ShiftNode<?> node) {
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        if (node.getStackKind() == JavaKind.Long) {
            Long64Lowerer.genBinaryArithmeticOperation(node, node.getX(), node.getY(), codeGenTool);
        } else {
            if (node instanceof LeftShiftNode) {
                BinaryIntOperationLowerer.binaryOp(node, node.getX(), node.getY(), codeGenTool, JSKeyword.SL, true);
            } else if (node instanceof RightShiftNode) {
                BinaryIntOperationLowerer.binaryOp(node, node.getX(), node.getY(), codeGenTool, JSKeyword.SR, true);
            } else if (node instanceof UnsignedRightShiftNode) {
                BinaryIntOperationLowerer.binaryOp(node, node.getX(), node.getY(), codeGenTool, JSKeyword.USR, true);
            } else {
                throw GraalError.unimplemented(node.toString()); // ExcludeFromJacocoGeneratedReport
            }
        }
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(SignExtendNode node) {
        int inputBits = node.getInputBits();
        int resultBits = node.getResultBits();

        IEmitter emitter;
        if (inputBits < 32) {
            /*
             * All integers with at most 32 bits are stored as 32-bit integers. The lowering of a
             * NarrowNode from 32 to less than 32 bits does not generate any instruction. This means
             * that the higher bits may not be set correctly yet. Therefore, we first need to
             * correctly set the higher bits.
             *
             * We shift the integer to the left to remove the upper bits and then shift to the right
             * again. The right shift copies the MSB and therefore does the sign extension.
             */
            List<Emitter> snippetArgs = new ArrayList<>();
            snippetArgs.add(Emitter.of(node.getValue()));
            snippetArgs.add(Emitter.of(inputBits));
            snippetArgs.add(Emitter.of(inputBits));
            emitter = JSSnippets.instantiateSignExtendSnippet(snippetArgs);
        } else {
            /*
             * If the input bits are 32, all bits are set correctly.
             */
            emitter = Emitter.of(node.getValue());
        }
        if (resultBits == 64) {
            Long64Lowerer.genUnaryArithmeticOperation(node, emitter, codeGenTool);
        } else {
            emitter.lower(codeGenTool);
        }
    }

    @Override
    protected void lower(ZeroExtendNode node) {
        CodeBuffer masm = codeGenTool.getCodeBuffer();
        int inputBits = node.getInputBits();
        int resultBits = node.getResultBits();

        Emitter emitter;
        assert inputBits == 1 || inputBits == 8 || inputBits == 16 || inputBits == 32 : inputBits;
        if (inputBits < 32) {
            /*
             * Set all bits except the inputBits to 0.
             */
            emitter = Emitter.of(
                            Emitter.of(node.getValue()),
                            Emitter.of(" & " + CodeUtil.mask(inputBits)));
        } else {
            emitter = Emitter.of(node.getValue());
        }
        if (resultBits < 64) {
            masm.emitKeyword(JSKeyword.LPAR);
            emitter.lower(codeGenTool);
            masm.emitKeyword(JSKeyword.RPAR);
        } else {
            codeGenTool.genInlineComment(("Zero Extend to 64 bit"));
            Long64Lowerer.genUnaryArithmeticOperation(node, emitter, codeGenTool);
        }
    }

    @Override
    protected void lower(NarrowNode node) {
        int inbits = node.getInputBits();
        int outbits = node.getResultBits();
        codeGenTool.genInlineComment(String.format("Narrow Additional Info inbits:%d outbits:%d", inbits, outbits));
        JVMCIError.guarantee(inbits > outbits, "Narrow node must decrease bit size %s, inbits:%d,outbits:%d", node.toString(), inbits, outbits);

        if (inbits == 64) {
            JVMCIError.guarantee(outbits <= 32, "Narrow from Long must decrease bit size %s", node.toString());
            Long64Lowerer.genUnaryArithmeticOperation(node, node.getValue(), codeGenTool);
        } else {
            lowerValue(node.getValue());
        }
    }

    @Override
    protected void lower(NotNode node) {
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);

        ValueNode input = node.getValue();
        if (input.getStackKind() == JavaKind.Long) {
            Long64Lowerer.genUnaryArithmeticOperation(node, node.getValue(), codeGenTool);
        } else {
            JSEmitter emitter = JSEmitter.of((t) -> t.genUnaryOperation(JSKeyword.NOT, input));
            if (INT_PALADIN) {
                emitter = JSEmitter.intPaladin(emitter);
            }
            emitter.lower(codeGenTool);
        }

        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(SignumNode node) {
        Runtime.MATH_SIGN.emitCall(codeGenTool, Emitter.of(node.getValue()));
    }

    @Override
    protected void lower(NewInstanceNode node) {
        codeGenTool.genNewInstance(node.instanceClass());
    }

    @Override
    protected void lower(DynamicNewInstanceNode node) {
        codeGenTool.genObjectCreate((t) -> codeGenTool.genPropertyBracketAccessWithExpression(Emitter.of(node.getInstanceType()), Emitter.of(RuntimeConstants.JS_CLASS_SYMBOL)));
    }

    @Override
    protected void lower(LoadFieldNode node) {
        Emitter receiver;

        if (node.isStatic()) {
            receiver = Emitter.of(node.field().getDeclaringClass());
        } else {
            receiver = Emitter.of(node.object());
        }

        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        codeGenTool.genPropertyAccess(receiver, Emitter.of(node.field()));
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(UnboxNode node) {
        HostedMetaAccess metaAccess = (HostedMetaAccess) codeGenTool.getProviders().getMetaAccess();
        Class<?> boxing = node.getBoxingKind().toBoxedJavaClass();
        ResolvedJavaField valueField = AbstractBoxingNode.getValueField(metaAccess.lookupJavaType(boxing));

        codeGenTool.genPropertyAccess(Emitter.of(node.getValue()), Emitter.of(valueField));
    }

    @Override
    protected void lower(ReinterpretNode node) {
        MetaAccessProvider metaAcces = codeGenTool.getProviders().getMetaAccess();
        HostedType toType = (HostedType) node.stamp(NodeView.DEFAULT).javaType(metaAcces);
        HostedType fromType = (HostedType) node.getValue().stamp(NodeView.DEFAULT).javaType(metaAcces);
        if (fromType.getJavaKind() == JavaKind.Long && toType.getJavaKind() == JavaKind.Double) {
            // from Long64.js to another kind
            Runtime.LONG_BITS_TO_DOUBLE.emitCall(codeGenTool, Emitter.of(node.getValue()));
        } else if (fromType.getJavaKind() == JavaKind.Double && toType.getJavaKind() == JavaKind.Long) {
            // from another kind to long
            Runtime.DOUBLE_BITS_TO_LONG.emitCall(codeGenTool, Emitter.of(node.getValue()));
        } else if (toType.getJavaKind() == JavaKind.Object || fromType.getJavaKind() == JavaKind.Object) {
            JVMCIError.shouldNotReachHere("Cannot build javascript object from a binary base");
        } else if (fromType.getJavaKind() == JavaKind.Float && toType.getJavaKind() == JavaKind.Int) {
            Runtime.FLOAT_BITS_TO_INT.emitCall(codeGenTool, Emitter.of(node.getValue()));
        } else if (fromType.getJavaKind() == JavaKind.Int && toType.getJavaKind() == JavaKind.Float) {
            Runtime.INT_BITS_TO_FLOAT.emitCall(codeGenTool, Emitter.of(node.getValue()));
        } else {
            throw JVMCIError.unimplemented("General reinterpretation not implemented currently from:" + fromType + " TO:" + toType);
        }
    }

    @Override
    protected void lower(StoreFieldNode node) {
        ResolvedJavaField f = node.field();
        Emitter receiver;
        if (node.isStatic()) {
            receiver = Emitter.of(f.getDeclaringClass());
        } else {
            receiver = Emitter.of(node.object());
        }

        codeGenTool.genPropertyAccess(receiver, Emitter.of(f));
        codeGenTool.genAssignment();
        lowerValue(node.value());
    }

    @Override
    protected void lower(ShortCircuitOrNode node) {
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        if (node.isXNegated()) {
            codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
            codeGenTool.getCodeBuffer().emitText("!");
            codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
            lowerValue(node.getX());
            codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
            codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);

        } else {
            lowerValue(node.getX());
        }

        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.SHORT_CIRCUITE_OR);

        if (node.isYNegated()) {
            codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
            codeGenTool.getCodeBuffer().emitText("!");
            codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
            lowerValue(node.getY());
            codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
            codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
        } else {
            lowerValue(node.getY());
        }

        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(LogicNegationNode node) {
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        codeGenTool.genUnaryOperation(JSKeyword.LOGIC_NOT, node.getValue());
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(WordCastNode node) {
        /*
         * NOTE (ld 04/02/15): thread local map in svm access discoverable reference which itself
         * holds reference ptr case via word cast from an object, we simple stick to it and keep the
         * object, which is needed to proceed certain floating decimal buffer operations
         */
        lowerValue(node.getInput());
    }

    protected void lower(FloatingWordCastNode node) {
        lowerValue(node.getInput());
    }

    @Override
    protected void lower(InstanceOfNode node) {
        CodeBuffer masm = codeGenTool.getCodeBuffer();
        TypeControl typeControl = codeGenTool.getJSProviders().typeControl();
        TypeReference typeReference = node.type();
        boolean isExact = typeReference.isExact();
        HostedType checkType = (HostedType) typeReference.getType();
        GraalError.guarantee(!checkType.isPrimitive(), "instanceof primitive");

        if (isExact || checkType.isInterface() || checkType.isArray()) {
            String targetHubName = typeControl.requestHubName(checkType);
            JSFunctionDefinition typeCheckFunction = isExact ? Runtime.isExact : Runtime.isA;
            typeCheckFunction.emitCall(codeGenTool, Emitter.of(node.allowsNull()), Emitter.of(node.getValue()), Emitter.of(targetHubName));
        } else {
            /*
             * InstanceOfNode can accept null values sometimes (e.g. when it's part of a cast)
             */
            if (node.allowsNull()) {
                masm.emitText("(");
                lowerValue(node.getValue());
                masm.emitText(" === null || ");
            }

            lowerValue(node.getValue());
            masm.emitText(" instanceof ");
            codeGenTool.genTypeName(checkType);

            if (node.allowsNull()) {
                masm.emitText(")");
            }
        }
    }

    @Override
    protected void lower(InstanceOfDynamicNode node) {
        JSFunctionDefinition typeCheckFunction = node.isExact() ? Runtime.isExact : Runtime.isA;
        typeCheckFunction.emitCall(codeGenTool, Emitter.of(node.allowsNull()), Emitter.of(node.getObject()), Emitter.of(node.getMirrorOrHub()));
    }

    @Override
    protected void lower(ArrayEqualsNode node) {
        List<ValueNode> args = new ArrayList<>();
        args.add(node.getArray1());
        args.add(node.getArray2());
        args.add(node.getLength());
        if (node.getStackKind() == JavaKind.Long) {
            Runtime.ARRAY_EQUALS_LONG.emitCall(codeGenTool, Emitter.of(args));
        } else {
            Runtime.ARRAY_EQUALS.emitCall(codeGenTool, Emitter.of(args));
        }
    }

    @Override
    protected void lower(ArrayFillNode node) {
        /* TODO GR-61725 Emit call to appropriate TypedArray.prototype.fill function */
        throw VMError.unimplemented("ArrayFillNode: " + node);
    }

    @Override
    protected void lower(NewMultiArrayNode node) {
        HostedArrayClass arrayClass = (HostedArrayClass) node.type();
        assert arrayClass.getArrayDimension() > 1 : "Dim 1 is no array depth";
        String hubName = codeGenTool.getJSProviders().typeControl().requestHubName(arrayClass);

        List<IEmitter> args = new ArrayList<>(node.dimensionCount() + 1);
        args.add(Emitter.of(hubName));

        for (ValueNode dim : node.dimensions()) {
            args.add(Emitter.of(dim));
        }

        Runtime.NEW_MULTI_ARRAY.emitCall(codeGenTool, args);
    }

    @Override
    protected void lower(ObjectClone node) {
        Runtime.CloneRuntime.emitCall(codeGenTool, Emitter.of(node.getObject()));
    }

    @Override
    protected void lower(LoadHubNode node) {
        codeGenTool.genPropertyAccess(Emitter.of(node.getValue()), Emitter.of(ClassLowerer.HUB_PROP_NAME));
    }

    @Override
    protected void lower(LoadArrayComponentHubNode node) {
        try {
            ResolvedJavaField f = codeGenTool.getProviders().getMetaAccess().lookupJavaField(DynamicHub.class.getDeclaredField("componentType"));
            codeGenTool.genPropertyAccess(Emitter.of(node.getValue()), Emitter.of(f));
        } catch (NoSuchFieldException t) {
            throw GraalError.shouldNotReachHere(t);
        }
    }

    @Override
    protected void lower(EndNode node) {
        new PhiResolveLowerer(node).lower(codeGenTool);
    }

    @Override
    protected void lower(StateSplitProxyNode node) {
        if (node.object() != null) {
            lowerValue(node.object());
        }
    }

    protected void lower(DeoptimizeNode node) {
        codeGenTool.genShouldNotReachHere("Deoptimize node " + node.getReason());
    }

    protected void lower(JSCallNode node) {
        node.getFunctionDefinition().emitCall(codeGenTool, Emitter.of(node.getArguments()));
    }

    @Override
    protected void lower(JavaReadNode node) {
        ArrayList<ValueNode> args = new ArrayList<>();
        if (node.getAddress() instanceof AMD64AddressNode) {
            args.add(node.getAddress().getBase());
            args.add(node.getAddress().getIndex());
        } else if (node.getAddress() instanceof OffsetAddressNode) {
            args.add(node.getAddress().getBase());
            args.add(((OffsetAddressNode) node.getAddress()).getOffset());
        } else {
            JVMCIError.shouldNotReachHere();
        }

        Runtime.UNSAFE_LOAD_RUNTIME.emitCall(codeGenTool, Emitter.of(args));
    }

    @Override
    protected void lower(JavaWriteNode node) {
        ArrayList<ValueNode> args = new ArrayList<>();
        if (node.getAddress() instanceof AMD64AddressNode) {
            args.add(node.getAddress().getBase());
            args.add(node.getAddress().getIndex());
        } else if (node.getAddress() instanceof OffsetAddressNode) {
            args.add(node.getAddress().getBase());
            args.add(((OffsetAddressNode) node.getAddress()).getOffset());
        } else {
            throw JVMCIError.shouldNotReachHere("node: " + node.getAddress());
        }

        args.add(node.value());
        Runtime.UNSAFE_STORE_RUNTIME.emitCall(codeGenTool, Emitter.of(args));
    }

    @Override
    protected void lower(ReadNode node) {
        AddressNode location = node.getAddress();
        CodeBuffer masm = codeGenTool.getCodeBuffer();

        if (location instanceof AMD64AddressNode) {
            ValueNode object = location.getBase();

            HostedType objectType = (HostedType) object.stamp(NodeView.DEFAULT).javaType(codeGenTool.getProviders().getMetaAccess());

            if (objectType.isArray()) {
                codeGenTool.genArrayLoad(location, object);
            } else {
                //
                // We have a read on an object, this read can be modelled as accessing the field of
                // the object at the given index, where the index accesses the field offset table to
                // query the field name for a given type.
                // The table maps offsets to lambdas that perform the field reads.
                codeGenTool.genPropertyAccess(Emitter.of(object), Emitter.of("constructor[CM]." + ClassMetadataLowerer.FIELD_TABLE_NAME));
                masm.emitKeyword(JSKeyword.LBRACK);
                lowerValue(location);
                masm.emitKeyword(JSKeyword.RBRACK);
                masm.emitKeyword(JSKeyword.LPAR);
                lowerValue(object);
                masm.emitKeyword(JSKeyword.RPAR);
            }
        } else if (location instanceof OffsetAddressNode) {
            OffsetAddressNode address = (OffsetAddressNode) node.getAddress();
            Emitter base = Emitter.of(node.getAddress().getBase());
            Emitter offset = Emitter.of(address.getOffset());
            MetaAccessProvider metaAccess = codeGenTool.getProviders().getMetaAccess();
            JavaKind kind = node.getAccessStamp(NodeView.DEFAULT).javaType(metaAccess).getJavaKind();
            Emitter type = Emitter.of(JSBootImageHeapLowerer.getKindNum(kind));
            Runtime.UNSAFE_LOAD_RUNTIME.emitCall(codeGenTool, base, offset, type);
        } else {
            JVMCIError.shouldNotReachHere("Method " + location.graph().method().toString() + " contains node " + location);
        }
    }

    @Override
    protected void lower(RawLoadNode node) {
        Emitter obj = Emitter.of(node.object());
        Emitter offset = Emitter.of(node.offset());
        Emitter type = Emitter.of(JSBootImageHeapLowerer.getKindNum(node.accessKind()));
        Runtime.UNSAFE_LOAD_RUNTIME.emitCall(codeGenTool, obj, offset, type);
    }

    @Override
    protected void lower(RawStoreNode node) {
        Emitter obj = Emitter.of(node.object());
        Emitter offset = Emitter.of(node.offset());
        Emitter value = Emitter.of(node.value());
        Emitter type = Emitter.of(JSBootImageHeapLowerer.getKindNum(node.accessKind()));
        Runtime.UNSAFE_STORE_RUNTIME.emitCall(codeGenTool, obj, offset, value, type);
    }

    @Override
    protected void lower(AbstractUnsafeCompareAndSwapNode node) {
        Emitter obj = Emitter.of(node.object());
        Emitter offset = Emitter.of(node.offset());
        Emitter expected = Emitter.of(node.expected());
        Emitter newValue = Emitter.of(node.newValue());
        Emitter type = Emitter.of(JSBootImageHeapLowerer.getKindNum(node.getValueKind()));
        JSFunctionDefinition func = switch (node) {
            case UnsafeCompareAndSwapNode n -> Runtime.CAS;
            case UnsafeCompareAndExchangeNode n -> Runtime.CAX;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(node);
        };
        func.emitCall(codeGenTool, obj, offset, expected, newValue, type);
    }

    @Override
    protected void lower(AtomicReadAndWriteNode node) {
        ArrayList<ValueNode> args = new ArrayList<>();
        args.add(node.object());
        args.add(node.offset());
        args.add(node.newValue());
        Runtime.ATOMIC_READ_AND_WRITE.emitCall(codeGenTool, Emitter.of(args));
    }

    @Override
    protected void lower(AtomicReadAndAddNode node) {
        ArrayList<ValueNode> args = new ArrayList<>();
        args.add(node.object());
        args.add(node.offset());
        args.add(node.delta());
        Runtime.ATOMIC_READ_AND_ADD.emitCall(codeGenTool, Emitter.of(args));
    }

    @Override
    protected void lower(DynamicNewArrayNode node) {
        Array.lowerNewArray(node.getElementType(), Emitter.of(node.length()), codeGenTool);
    }

    @Override
    protected void lower(ObjectIsArrayNode node) {
        Runtime.IsArray.emitCall(codeGenTool, Emitter.of(node.getValue()));
    }

    protected void lower(CompoundConditionNode node) {
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        lowerValue(node.getX());
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
        if (node.getOp() == CompoundConditionNode.CompoundOp.XaaY) {
            codeGenTool.getCodeBuffer().emitText("&&");
        } else if (node.getOp() == CompoundConditionNode.CompoundOp.XooY) {
            codeGenTool.getCodeBuffer().emitText("||");
        } else {
            JVMCIError.shouldNotReachHere();
        }
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        lowerValue(node.getY());
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(IntegerDivRemNode node) {
        // The generation of these wo should have been prevented as they don't have an equivalent in
        // JavaScript.
        assert !(node instanceof UnsignedDivNode) && !(node instanceof UnsignedRemNode) : node;
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.LPAR);
        if (node.getStackKind() == JavaKind.Long) {
            Long64Lowerer.genBinaryArithmeticOperation(node, node.getX(), node.getY(), codeGenTool);
        } else {
            BinaryIntOperationLowerer.binaryOp(node, node.getX(), node.getY(), codeGenTool, node.getOp() == IntegerDivRemNode.Op.DIV ? JSKeyword.DIV : JSKeyword.MOD, true);
        }
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(BinaryArithmeticNode<?> node) {
        switch (node.getStackKind()) {
            case Int:
                BinaryIntOperationLowerer.binaryOp(node, node.getX(), node.getY(), codeGenTool, getSymbolForBinaryArithmeticOp(node), true);
                break;
            case Long:
                Long64Lowerer.genBinaryArithmeticOperation(node, node.getX(), node.getY(), codeGenTool);
                break;
            case Float:
            case Double:
                BinaryFloatOperationLowerer.doop(codeGenTool, getSymbolForBinaryArithmeticOp(node), node);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(node.getStackKind());
        }
    }

    private static JSKeyword getSymbolForBinaryArithmeticOp(BinaryArithmeticNode<?> node) {
        if (node instanceof AndNode) {
            return JSKeyword.AND;
        } else if (node instanceof OrNode) {
            return JSKeyword.OR;
        } else if (node instanceof XorNode) {
            return JSKeyword.XOR;
        } else if (node instanceof AddNode) {
            return JSKeyword.ADD;
        } else if (node instanceof SubNode) {
            return JSKeyword.SUB;
        } else if (node instanceof MulNode) {
            return JSKeyword.MUL;
        } else if (node instanceof FloatDivNode || node instanceof SignedFloatingIntegerDivNode) {
            return JSKeyword.DIV;
        } else if (node instanceof RemNode || node instanceof SignedFloatingIntegerRemNode) {
            return JSKeyword.MOD;
        } else {
            throw GraalError.shouldNotReachHere("Unexpected binary arithmetic node " + node);
        }
    }

    @Override
    protected void lower(GetClassNode node) {
        codeGenTool.genPropertyAccess(Emitter.of(node.getObject()), Emitter.of(ClassLowerer.HUB_PROP_NAME));
    }

    @Override
    protected void lower(UnaryMathIntrinsicNode node) {
        JSFunctionDefinition fun;
        switch (node.getOperation()) {
            case COS:
                fun = Runtime.MATH_COS;
                break;
            case LOG:
                fun = Runtime.MATH_LOG;
                break;
            case LOG10:
                fun = Runtime.MATH_LOG10;
                break;
            case SIN:
                fun = Runtime.MATH_SIN;
                break;
            case TAN:
                fun = Runtime.MATH_TAN;
                break;
            case EXP:
                fun = Runtime.MATH_EXP;
                break;
            default:
                throw JVMCIError.shouldNotReachHere("Uknown operation " + node.getOperation());
        }

        fun.emitCall(codeGenTool, Emitter.of(node.getValue()));
    }

    @Override
    protected void lower(BinaryMathIntrinsicNode node) {
        switch (node.getOperation()) {
            case POW:
                JSSystemFunction strictMathPow = JSCallNode.STRICT_MATH_POW;
                strictMathPow.emitCall(codeGenTool, Emitter.of(node.getX()), Emitter.of(node.getY()));
                break;
            default:
                throw GraalError.shouldNotReachHere("Uknown operation " + node.getOperation());
        }
    }

    @Override
    protected void lower(AbsNode node) {
        switch (node.getStackKind()) {
            case Long:
                Long64Lowerer.genUnaryArithmeticOperation(node, node.getValue(), codeGenTool);
                break;
            default:
                Runtime.MATH_ABS.emitCall(codeGenTool, Emitter.of(node.getValue()));
                break;
        }
    }

    @Override
    protected void lower(SqrtNode node) {
        Runtime.MATH_SQRT.emitCall(codeGenTool, Emitter.of(node.getValue()));
    }

    @Override
    protected void lower(RoundNode node) {
        JSFunctionDefinition fun;
        switch (node.mode()) {
            case NEAREST:
                /*
                 * We cannot use JavaScript's Math.round() because it rounds x.5 towards +infinity
                 * whereas Java rounds towards the next even integer.
                 */
                throw JVMCIError.shouldNotReachHere("RoundNode mode round to nearest is not supported");
            case TRUNCATE:
                fun = Runtime.MATH_TRUNC;
                break;
            case DOWN:
                fun = Runtime.MATH_FLOOR;
                break;
            case UP:
                fun = Runtime.MATH_CEIL;
                break;
            default:
                throw JVMCIError.shouldNotReachHere("Unknown round mode " + node.mode());
        }

        fun.emitCall(codeGenTool, Emitter.of(node.getValue()));

    }

    @Override
    protected void lower(BytecodeExceptionNode node) {
        lowerBytecodeException(node.getExceptionKind(), node.getArguments());
    }

    protected void lower(ThrowBytecodeExceptionNode node) {
        codeGenTool.getCodeBuffer().emitKeyword(JSKeyword.THROW);
        codeGenTool.getCodeBuffer().emitWhiteSpace();
        lowerBytecodeException(node.getExceptionKind(), node.getArguments());
    }

    /**
     * Generates a call to the appropriate method in {@link ImplicitExceptions} to create a bytecode
     * exception object for the given kind.
     *
     * @see WebImageImplicitExceptionsFeature#getSupportMethodName(BytecodeExceptionNode.BytecodeExceptionKind)
     */
    protected void lowerBytecodeException(BytecodeExceptionNode.BytecodeExceptionKind exceptionKind, List<ValueNode> args) {
        HostedType exceptionsType = (HostedType) codeGenTool.getProviders().getMetaAccess().lookupJavaType(ImplicitExceptions.class);
        HostedMethod meth = WebImageProviders.findMethod(exceptionsType, WebImageImplicitExceptionsFeature.getSupportMethodName(exceptionKind));
        codeGenTool.genStaticCall(meth, Emitter.of(args));
    }

    /**
     * JSBody node gets allocated a variable if its result is used (usually by a ReturnNode).
     */
    protected void lower(JSBody node) {
        CodeBuffer masm = codeGenTool.getCodeBuffer();

        ResolvedJavaType declaring = node.getMethod().getDeclaringClass();

        masm.emitKeyword(JSKeyword.LPAR);
        if (node.declaresJSResources()) {
            masm.emitText("function(){");
            codeGenTool.genInitJsResources(declaring);
            masm.emitText("}(), ");
        }

        JSBody.JSCode jsCode = node.getJsCode();

        String realBody = node.getJSCodeAsString(codeGenTool);

        masm.emitKeyword(JSKeyword.LPAR);
        masm.emitKeyword(JSKeyword.FUNCTION);
        masm.emitKeyword(JSKeyword.LPAR);
        codeGenTool.genCommaList(Arrays.stream(jsCode.getArgs()).map(Emitter::of).collect(Collectors.toList()));
        masm.emitKeyword(JSKeyword.RPAR);
        masm.emitScopeBegin();
        for (String line : realBody.split("\n")) {
            masm.emitText(line);
            masm.emitNewLine();
        }
        masm.emitScopeEnd();
        masm.emitKeyword(JSKeyword.RPAR);
        codeGenTool.genFunctionCall(Emitter.of(""), Emitter.of("call"), Emitter.of(node.getArguments()));
        masm.emitKeyword(JSKeyword.RPAR);
    }

    @Override
    protected void lower(UnsafeMemoryStoreNode node) {
        ArrayList<ValueNode> args = new ArrayList<>();
        args.add(node.getAddress());
        args.add(node.getValue());
        JSFunctionDefinition write = switch (node.getKind()) {
            case Boolean, Byte -> Runtime.WRITE_BYTE;
            case Char -> Runtime.WRITE_CHAR;
            case Short -> Runtime.WRITE_SHORT;
            case Int -> Runtime.WRITE_INT;
            case Float -> Runtime.WRITE_FLOAT;
            case Long -> Runtime.WRITE_LONG;
            case Double -> Runtime.WRITE_DOUBLE;
            default -> throw GraalError.shouldNotReachHere("Unsafe store not possible for kind: " + node.getKind());
        };
        write.emitCall(codeGenTool, Emitter.of(args));
    }

    @Override
    protected void lower(UnsafeMemoryLoadNode node) {
        ArrayList<ValueNode> args = new ArrayList<>();
        args.add(node.getAddress());
        JSFunctionDefinition read = switch (node.getKind()) {
            case Boolean, Byte -> Runtime.READ_BYTE;
            case Char -> Runtime.READ_CHAR;
            case Short -> Runtime.READ_SHORT;
            case Int -> Runtime.READ_INT;
            case Float -> Runtime.READ_FLOAT;
            case Long -> Runtime.READ_LONG;
            case Double -> Runtime.READ_DOUBLE;
            default -> throw GraalError.shouldNotReachHere("Unsafe load not possible for kind: " + node.getKind());
        };
        read.emitCall(codeGenTool, Emitter.of(args));
    }

    /**
     * Simply do nothing as in Graal
     *
     * We can encounter BlackholeNode due to the following substitution in SVM:
     *
     * <pre>
     * static void reachabilityFence(Object ref) {
     *     GraalDirectives.blackhole(ref);
     * }
     * </pre>
     *
     * The constructor {@code PhantomCleanable.<init>} contains call to
     * {@code Reference.reachabilityFence}.
     */
    @Override
    protected void lower(BlackholeNode node) {
    }

    /**
     * Similar to {@link #lower(BlackholeNode)}.
     */
    @Override
    protected void lower(ReachabilityFenceNode node) {
    }

    @Override
    protected void lower(ForeignCall node) {
        ForeignCallDescriptor descriptor = node.getDescriptor();
        if (descriptor == SnippetRuntime.UNSUPPORTED_FEATURE) {
            codeGenTool.genShouldNotReachHere("unsupportedFeature");
        } else {
            SnippetRuntime.SubstrateForeignCallDescriptor substrateDescriptor = (SnippetRuntime.SubstrateForeignCallDescriptor) descriptor;
            ResolvedJavaMethod method = substrateDescriptor.findMethod(codeGenTool.getProviders().getMetaAccess());

            if (method.isStatic()) {
                codeGenTool.genStaticCall(method, Emitter.of(node.getArguments()));
            } else {
                throw GraalError.unimplemented("ForeignCallNode for non-static methods not implemented: " + node);
            }
        }
    }

    @Override
    protected void lower(ClassIsAssignableFromNode node) {
        Runtime.slotTypeCheck.emitCall(codeGenTool, Emitter.of(node.getThisClass()), Emitter.of(node.getOtherClass()));
    }

    private void lower(StaticFieldsSupport.StaticFieldResolvedBaseNode node) {
        boolean primitive = node.primitive;
        WebImageJSProviders providers = codeGenTool.getJSProviders();
        JavaConstant constant = providers.getSnippetReflection()
                        .forObject(primitive ? StaticFieldsSupport.getCurrentLayerStaticPrimitiveFields() : StaticFieldsSupport.getCurrentLayerStaticObjectFields());
        ConstantNode constantNode = ConstantNode.forConstant(constant, providers.getMetaAccess());
        lower(constantNode);
    }

    // ============================================================================
    // endregion

    // ============================================================================
    // region [Helper methods]

    @Override
    public boolean isActiveValueNode(ValueNode node) {
        return actualUsageCount(node) > 0 &&
                        !(node instanceof EndNode) &&
                        !isIgnored(node);
    }

    /**
     * Returns the actual usages of a node.
     *
     * This is supposed to be more precise than {@link Node#usages()}, as it ignores some spurious
     * usage. For example, a usage by a FrameState node is ignored.
     */
    @Override
    public NodeIterable<Node> actualUsages(ValueNode node) {
        return node.usages().filter(NodePredicates.isNotA(FrameState.class));
    }

    @Override
    public boolean isTopLevelStatement(Node node) {
        return !isIgnored(node) &&
                        !(node instanceof OffsetAddressNode) &&
                        !(node instanceof AMD64AddressNode) &&
                        ((actualUsageCount(node) == 0) ||
                                        codeGenTool.declared(node) ||
                                        node instanceof EndNode);
    }

    public static Method getMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    public static void lowerConstant(PrimitiveConstant c, JSCodeGenTool jsLTools) {
        switch (c.getJavaKind()) {
            case Char, Byte, Short, Int, Boolean:
                jsLTools.getCodeBuffer().emitIntLiteral(c.asInt());
                return;
            case Long:
                Long64Lowerer.lowerFromConstant(c, jsLTools);
                return;
            case Float:
                jsLTools.getCodeBuffer().emitText(WebImageObjectInspector.ValueType.floatWithSpecialCases(c.asFloat()));
                return;
            case Double:
                jsLTools.getCodeBuffer().emitText(WebImageObjectInspector.ValueType.doubleWithSpecialCases(c.asDouble()));
                return;
            case Object:
            default:
                JVMCIError.shouldNotReachHere();
        }
    }

    /**
     * Creates a JS string literal for the given string.
     *
     * Any character that could be misinterpreted by the JS runtime has to be escaped:
     * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String#escape_notation
     */
    public static String getStringLiteral(String s) {
        StringBuilder sb = new StringBuilder();

        /*
         * If the string contains single quotes, we use double quotes around the string, otherwise
         * single quotes.
         *
         * If the string contains only one type of quote this ensures that we never have to escape
         * the quotation marks. If it contains both, we always use double quotes.
         */
        boolean useDoubleQuotes = s.contains("'");

        sb.append(useDoubleQuotes ? '"' : '\'');

        for (int i = 0; i < s.length(); i++) {

            char c = s.charAt(i);

            switch (c) {
                case '\0':
                    sb.append("\\0");
                    break;

                case '\b':
                    sb.append("\\b");
                    break;

                case '\t':
                    sb.append("\\t");
                    break;

                case '\n':
                    sb.append("\\n");
                    break;

                case '\f':
                    sb.append("\\f");
                    break;

                case '\r':
                    sb.append("\\r");
                    break;

                case '"':
                    if (useDoubleQuotes) {
                        sb.append("\\\"");
                    } else {
                        sb.append('"');
                    }
                    break;

                case '\'':
                    if (useDoubleQuotes) {
                        sb.append('\'');
                    } else {
                        sb.append("\\'");
                    }
                    break;

                case '\\':
                    sb.append("\\\\");
                    break;

                default:

                    /*
                     * Deal with ASCII control characters that do not have a special escape notation
                     */
                    if (c <= 0x1F || c == 0x7F) {
                        sb.append("\\x");
                        String hex = Integer.toHexString(c);

                        // The \x escape notation requires two characters
                        if (hex.length() == 1) {
                            sb.append("0");
                        }

                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }

        sb.append(useDoubleQuotes ? '"' : '\'');

        return sb.toString();
    }

    @Override
    public String nodeDebugInfo(Node node) {
        boolean isValueNode = node instanceof ValueNode;
        return node +
                        ", inlineable = " + (isValueNode && !codeGenTool.getVariableAllocation().needsVariable((ValueNode) node, codeGenTool)) +
                        (isValueNode ? ", safe inlining = " + codeGenTool.getVariableAllocation().getSafeInliningPolicies((ValueNode) node, codeGenTool) : "") +
                        ", declared = " + (codeGenTool.declared(node)) +
                        ", isValue = " + (isValueNode && isActiveValueNode((ValueNode) node)) +
                        ", ignored = " + isIgnored(node) +
                        ", " + node.usages() +
                        ", actual usage = " + actualUsageCount(node) +
                        (node instanceof FixedWithNextNode ? ", next = " + ((FixedWithNextNode) node).next() : "");
    }

    // ============================================================================
    // endregion

}
