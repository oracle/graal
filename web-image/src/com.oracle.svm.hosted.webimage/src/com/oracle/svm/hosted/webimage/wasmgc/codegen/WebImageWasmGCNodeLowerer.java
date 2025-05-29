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

package com.oracle.svm.hosted.webimage.wasmgc.codegen;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.results.StrengthenGraphs;
import com.oracle.svm.core.graal.nodes.FloatingWordCastNode;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.graal.nodes.ReadExceptionObjectNode;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSNodeLowerer;
import com.oracle.svm.hosted.webimage.codegen.node.ReadIdentityHashCodeNode;
import com.oracle.svm.hosted.webimage.codegen.node.WriteIdentityHashCodeNode;
import com.oracle.svm.hosted.webimage.js.JSBody;
import com.oracle.svm.hosted.webimage.js.JSBodyNode;
import com.oracle.svm.hosted.webimage.js.JSBodyWithExceptionNode;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.wasm.WasmJSCounterparts;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmOptions;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Const;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Unary;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmCodeGenTool;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmIRWalker;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmBackend;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmNodeLowerer;
import com.oracle.svm.hosted.webimage.wasm.debug.WasmDebug;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmTrapNode;
import com.oracle.svm.hosted.webimage.wasm.snippets.WasmImportForeignCallDescriptor;
import com.oracle.svm.hosted.webimage.wasmgc.WasmGCAllocationSupport;
import com.oracle.svm.hosted.webimage.wasmgc.WasmGCArrayCopySupport;
import com.oracle.svm.hosted.webimage.wasmgc.WasmGCConversion;
import com.oracle.svm.hosted.webimage.wasmgc.WasmGCTypeCheckSupport;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.GCKnownIds;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.WebImageWasmGCIds;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmGCUtil;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.webimage.functionintrinsics.JSCallNode;
import com.oracle.svm.webimage.functionintrinsics.JSSystemFunction;
import com.oracle.svm.webimage.wasm.WasmForeignCallDescriptor;
import com.oracle.svm.webimage.wasm.types.WasmUtil.Extension;
import com.oracle.svm.webimage.wasmgc.WasmExtern;
import com.oracle.svm.webimage.wasmgc.WasmGCJSConversion;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.nodes.extended.AbstractBoxingNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.FixedValueAnchorNode;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.LoadArrayComponentHubNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.ObjectIsArrayNode;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.java.AbstractNewArrayNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.java.AccessIndexedNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceNode;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.java.ReachabilityFenceNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.replacements.nodes.AssertionNode;
import jdk.graal.compiler.word.WordCastNode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class WebImageWasmGCNodeLowerer extends WebImageWasmNodeLowerer {

    private final WasmGCUtil gcUtil;
    private final WasmGCBuilder builder;

    protected static final Set<Class<?>> IGNORED_NODE_TYPES = new HashSet<>(WebImageJSNodeLowerer.IGNORED_NODE_TYPES);

    static {
        /*
         * In WasmGC, address nodes usually do not have a direct value (they only make sense with
         * base and offset considered separately) and thus must not be lowered or stored in a
         * variable. Nodes using address nodes (e.g. ReadNode), will directly extract relevant data
         * from the address node.
         *
         * TODO GR-59287 Mark everything except WasmGCFallbackAddressNode as ignored once we have
         * WasmGC specific address nodes.
         */
        IGNORED_NODE_TYPES.add(OffsetAddressNode.class);
    }

    public WebImageWasmGCNodeLowerer(WasmCodeGenTool codeGenTool) {
        super(codeGenTool);

        this.gcUtil = (WasmGCUtil) util;
        this.builder = ((WasmGCCodeGenTool) codeGenTool).getWasmProviders().builder();
    }

    @Override
    protected WasmGCCodeGenTool masm() {
        return (WasmGCCodeGenTool) super.masm();
    }

    @Override
    public Collection<Class<?>> getIgnoredNodeTypes() {
        return IGNORED_NODE_TYPES;
    }

    @Override
    protected Instruction lowerTopLevelStatement(Node n, WasmIRWalker.Requirements reqs) {
        /*
         * We never want to emit a value-producing node at the top level. They always have to be
         * emitted as a parameter of another node.
         */
        assert !util.hasValue(n) : n;

        if (n instanceof BlackholeNode blackhole) {
            return lowerDrop(blackhole.getValue());
        } else if (n instanceof ReturnNode returnNode) {
            return lowerReturn(returnNode);
        } else if (n instanceof ReachabilityFenceNode reachabilityFence) {
            return lowerReachabilityFence(reachabilityFence);
        } else if (n instanceof LoweredDeadEndNode) {
            /*
             * Technically, this node is already unreachable and could not emit any code. But for
             * early detection of bugs for which this node is actually reached, an explicit WASM
             * unreachable instruction is emitted. The overhead is only one byte per instruction.
             */
            return new Instruction.Unreachable();
        } else if (n instanceof WasmTrapNode) {
            return new Instruction.Unreachable();
        } else if (n instanceof MemoryAnchorNode) {
            // Nothing to emit, since this node is used for structural purposes only.
            return null;
        } else if (n instanceof AssertionNode assertion) {
            return lowerAssert(assertion);
        } else {
            /*
             * There are some nodes that can be both top-level statements and values (e.g. invokes
             * with and without return values) and for these we need to delegate to dispatch.
             */
            assert n instanceof ValueNode : n;
            return dispatch((ValueNode) n, reqs);
        }
    }

    @Override
    protected Instruction lowerReturn(ReturnNode returnNode) {
        ValueNode resultNode = returnNode.result();
        Instruction result;

        if (resultNode == null) {
            result = null;
        } else {
            /*
             * TODO GR-49248 workaround for the BoundMethodHandle$Species_L.copyWithExtend* method,
             * which return the wrong stamp
             */
            ResolvedJavaType returnType = masm.compilationResult.getReturnType();
            result = optionalDowncast(lowerExpression(resultNode), resultNode, resultNode.stamp(NodeView.DEFAULT), returnType);
        }

        return new Instruction.Return(result);
    }

    protected Instruction lowerUnwind(UnwindNode n) {
        return new Instruction.Call(masm().getKnownIds().throwTemplate.requestFunctionId(), lowerExpression(n.exception()));
    }

    @Override
    protected Instruction dispatch(ValueNode n, WasmIRWalker.Requirements reqs) {
        return switch (n) {
            case ConstantNode constant -> lowerConstant(constant);
            case ParameterNode param -> lowerParam(param);
            case BinaryNode binary -> lowerBinary(binary);
            case CompressionNode compression -> lowerCompression(compression, reqs);
            case UnwindNode unwind -> lowerUnwind(unwind);
            case UnaryNode unary -> lowerUnary(unary);
            case ReadExceptionObjectNode readExceptionObject -> lowerReadException(readExceptionObject);
            case BoxNode box -> lowerBox(box, reqs);
            case UnboxNode unbox -> lowerUnbox(unbox);
            case LogicNode logic -> lowerLogicNode(logic, reqs);
            case ConditionalNode conditional -> lowerConditional(conditional);
            case FixedAccessNode fixedAccess -> lowerFixedAccess(fixedAccess);
            case PiNode pi -> lowerPi(pi, reqs);
            case IntegerDivRemNode divRem -> lowerDivRem(divRem);
            case FixedValueAnchorNode fixedValueAnchor -> lowerExpression(fixedValueAnchor.object());
            case PublishWritesNode publishWrites -> lowerExpression(publishWrites.getOriginalNode());
            case LoadFieldNode loadField -> lowerLoadField(loadField);
            case StoreFieldNode storeField -> lowerStoreField(storeField);
            case ArrayLengthNode arrayLength -> lowerArrayLength(arrayLength);
            case AccessIndexedNode accessIndexed -> lowerAccessIndexed(accessIndexed);
            case InvokeNode invoke -> lowerInvoke(invoke);
            case InvokeWithExceptionNode invoke -> lowerInvoke(invoke);
            case ForeignCall foreignCall -> lowerForeignCall(foreignCall);
            case NewInstanceNode newInstance -> lowerNewInstance(newInstance);
            case DynamicNewInstanceNode dynamicNewInstance -> lowerDynamicNewInstance(dynamicNewInstance);
            case NewArrayNode newArray -> lowerNewArray(newArray);
            case DynamicNewArrayNode newArray -> lowerDynamicNewArray(newArray);
            case NewMultiArrayNode newMultiArray -> lowerMultiNewArray(newMultiArray);
            case ReadIdentityHashCodeNode readIdentityHashCode -> lowerReadIdentityHashCode(readIdentityHashCode);
            case WriteIdentityHashCodeNode writeIdentityHashCode -> lowerWriteIdentityHashCode(writeIdentityHashCode);
            case LoadArrayComponentHubNode loadArrayComponentHub -> lowerLoadArrayComponentHub(loadArrayComponentHub);
            case LoadHubNode loadHub -> lowerLoadHub(loadHub);
            case JSCallNode jsCall -> lowerJSCall(jsCall);
            case JSBodyNode jsBody -> lowerJSBody(jsBody);
            case JSBodyWithExceptionNode jsBody -> lowerJSBody(jsBody);
            case WordCastNode wordCast -> lowerWordCast(wordCast);
            case FloatingWordCastNode wordCast -> lowerFloatingWordCast(wordCast);
            default -> {
                assert !isForbiddenNode(n) : reportForbiddenNode(n);
                if (WebImageOptions.DebugOptions.VerificationPhases.getValue()) {
                    throw GraalError.shouldNotReachHere("Tried to lower unknown node: " + n);
                }
                // TODO GR-47009 Stop generating stub code.
                yield getStub(n);
            }
        };
    }

    /**
     * Optional downcast to the node's stamp.
     *
     * @see #optionalDowncast(Instruction, ValueNode, ResolvedJavaType, ResolvedJavaType)
     */
    private Instruction optionalDowncast(Instruction original, ValueNode node, ResolvedJavaType fromType) {
        return optionalDowncast(original, node, fromType, node.stamp(NodeView.DEFAULT));
    }

    private Instruction optionalDowncast(Instruction original, ValueNode node, Stamp fromStamp, ResolvedJavaType toType) {
        return optionalDowncast(original, node, fromStamp.javaType(masm.getProviders().getMetaAccess()), toType);
    }

    private Instruction optionalDowncast(Instruction original, ValueNode node, ResolvedJavaType fromType, Stamp toStamp) {
        return optionalDowncast(original, node, fromType, toStamp.javaType(masm.getProviders().getMetaAccess()));
    }

    /**
     * Inserts an optional downcast to ensure the produced value (of type {@code fromType}) matches
     * {@code toType}.
     * <p>
     * This method addresses situations where the stamps in the IR do not match declared types in
     * the hosted universe:
     * <ul>
     * <li>Field types (mismatch with field loads)</li>
     * <li>Parameter types (mismatch with method signature)</li>
     * <li>Return types (mismatch with method signature)</li>
     * </ul>
     * This can happen if stamps in the IR are strengthened (see {@link StrengthenGraphs}) and now
     * refer to a subtype of the expected type. In that case, an explicit cast is necessary (the
     * analysis guarantees that the cast succeeds).
     * <p>
     * The analysis results used to strengthen stamps do not seem directly applicable for using the
     * stronger types in the Wasm field or function declarations.
     *
     * @param original The instruction produced from the node
     * @param node Node being lowered
     * @param fromType The corresponding Java type produced by the instruction.
     * @param toType The type that should pre produced
     * @return The original instruction, optionally wrapped in a cast.
     */
    private Instruction optionalDowncast(Instruction original, ValueNode node, ResolvedJavaType fromType, ResolvedJavaType toType) {
        ResolvedJavaType wasmFromType = util.canonicalizeJavaType(fromType);
        ResolvedJavaType wasmToType = util.canonicalizeJavaType(toType);

        if (wasmFromType.getJavaKind() != JavaKind.Object || wasmToType.getJavaKind() != JavaKind.Object) {
            return original;
        }

        boolean toIsSubtype = wasmFromType.isAssignableFrom(wasmToType);

        /*
         * toType has to be either a subtype (-> downcast) or a supertype (-> nothing to do) of
         * fromType
         */
        assert toIsSubtype || wasmToType.isAssignableFrom(wasmFromType) : wasmToType + " is not compatible with " + wasmFromType + ", at " + node;

        // Insert a downcast if toType is a strict sub-type of fromType
        if (toIsSubtype && !wasmFromType.equals(wasmToType)) {
            original.setComment(masm.getNodeComment(node));
            Instruction cast = new Instruction.RefCast(original, (WasmRefType) util.typeForJavaType(wasmToType));
            cast.setComment("Explicit downcast due to type mismatch, expected " + wasmToType.getName() + ", got " + wasmFromType.getName());
            return cast;
        }

        return original;
    }

    private Instruction lowerNewInstance(NewInstanceNode newInstance) {
        return new Instruction.Call(masm().getKnownIds().instanceCreateTemplate.requestFunctionId(OriginalClassProvider.getJavaClass(newInstance.instanceClass())));
    }

    private Instruction lowerDynamicNewInstance(DynamicNewInstanceNode newInstance) {
        return masm().getWasmProviders().builder().createUninitialized(lowerExpression(newInstance.getInstanceType()));
    }

    private Instruction allocateArray(AbstractNewArrayNode newArray, JavaKind elementKind, Instruction hub) {
        Instruction length = lowerExpression(newArray.length());
        Instruction innerArray = new Instruction.ArrayNew(masm().getKnownIds().innerArrayTypes.get(elementKind), length);

        return allocateArrayWithInnerArray(elementKind, hub, innerArray);
    }

    private Instruction allocateArrayWithInnerArray(JavaKind elementKind, Instruction hub, Instruction innerArray) {
        return new Instruction.StructNew(masm().getKnownIds().arrayStructTypes.get(elementKind), hub, Const.forInt(0), innerArray);
    }

    private Instruction lowerNewArray(NewArrayNode newArray) {
        JavaKind elementKind = newArray.elementType().getJavaKind();
        JavaConstant hubConstant = masm.getProviders().getConstantReflection().asJavaClass(newArray.elementType().getArrayClass());
        assert !hubConstant.isNull() : hubConstant;
        return allocateArray(newArray, elementKind, lowerConstant(hubConstant));
    }

    private Instruction lowerDynamicNewArray(DynamicNewArrayNode newArray) {
        // All other variants of this node have been replaced
        assert newArray.getKnownElementKind() == JavaKind.Object : newArray.getKnownElementKind();

        WasmId.StructType hubTypeId = masm().getWasmProviders().util().getHubObjectId();
        ResolvedJavaField hubCompanionField = masm.getProviders().getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "companion"));
        WasmId.Field companionFieldId = masm.idFactory.newJavaField(hubCompanionField);

        WasmId.StructType companionTypeId = masm.idFactory.newJavaStruct(masm.getWasmProviders().getMetaAccess().lookupJavaType(DynamicHubCompanion.class));
        ResolvedJavaField companionArrayHubField = masm.getProviders().getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(DynamicHubCompanion.class, "arrayHub"));
        WasmId.Field arrayHubFieldId = masm.idFactory.newJavaField(companionArrayHubField);

        Instruction.StructGet companion = new Instruction.StructGet(hubTypeId, companionFieldId, Extension.None, lowerExpression(newArray.getElementType()));
        Instruction.StructGet arrayHub = new Instruction.StructGet(companionTypeId, arrayHubFieldId, Extension.None, companion);
        return allocateArray(newArray, JavaKind.Object, arrayHub);
    }

    /**
     * Multidimensional arrays are allocated through a call to
     * {@link WasmGCAllocationSupport#newMultiArray(Class, int[])}.
     * <p>
     * For this, a Java int array has to be created which stores the array dimensions.
     */
    private Instruction lowerMultiNewArray(NewMultiArrayNode newMultiArray) {
        MetaAccessProvider metaAccess = masm.getProviders().getMetaAccess();

        Instruction[] dimensions = new Instruction[newMultiArray.dimensionCount()];
        for (int i = 0; i < dimensions.length; i++) {
            dimensions[i] = lowerExpression(newMultiArray.dimension(i));
        }

        // Allocate a Java int array with the dimensions
        Instruction innerArray = new Instruction.ArrayNewFixed(masm().getKnownIds().innerArrayTypes.get(JavaKind.Int), dimensions);
        JavaConstant dimsArrayHubConstant = masm.getProviders().getConstantReflection().asJavaClass(metaAccess.lookupJavaType(int[].class));
        Instruction dimensionsArrayStruct = allocateArrayWithInnerArray(JavaKind.Int, lowerConstant(dimsArrayHubConstant), innerArray);

        JavaConstant hubConstant = masm.getProviders().getConstantReflection().asJavaClass(newMultiArray.type());

        Instruction multiArray = lowerSubstrateForeignCall(WasmGCAllocationSupport.NEW_MULTI_ARRAY, lowerConstant(hubConstant), dimensionsArrayStruct);
        return optionalDowncast(multiArray, newMultiArray, metaAccess.lookupJavaType(Object.class));
    }

    private Instruction lowerReadIdentityHashCode(ReadIdentityHashCodeNode readIdentityHashCode) {
        return new Instruction.StructGet(gcUtil.getJavaLangObjectId(), masm().getKnownIds().identityHashCodeField, Extension.None,
                        lowerExpression(readIdentityHashCode.getObject()));
    }

    private Instruction lowerWriteIdentityHashCode(WriteIdentityHashCodeNode writeIdentityHashCode) {
        return new Instruction.StructSet(gcUtil.getJavaLangObjectId(), masm().getKnownIds().identityHashCodeField,
                        lowerExpression(writeIdentityHashCode.getObject()), lowerExpression(writeIdentityHashCode.getHashCode()));
    }

    private Instruction lowerLoadHub(LoadHubNode loadHub) {
        return masm().getWasmProviders().builder().getHub(lowerExpression(loadHub.getValue()));
    }

    private Instruction lowerLoadArrayComponentHub(LoadArrayComponentHubNode loadArrayComponentHub) {
        ResolvedJavaField componentTypeField = masm.getProviders().getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(DynamicHub.class, "componentType"));

        WasmId.StructType hubTypeId = masm().getWasmProviders().util().getHubObjectId();
        WasmId.Field componentTypeFieldId = masm.idFactory.newJavaField(componentTypeField);

        return new Instruction.StructGet(hubTypeId, componentTypeFieldId, Extension.None, lowerExpression(loadArrayComponentHub.getValue()));
    }

    /**
     * Raw memory accesses generate calls to {@link WasmGCUnsafeTemplates}, which dispatch to the
     * appropriate accessor function for the access kind.
     */
    private Instruction lowerFixedAccess(FixedAccessNode n) {
        if (!n.getAddress().getBase().stamp(NodeView.DEFAULT).isObjectStamp()) {
            // TODO GR-60261 support off-heap accesses
            logError("Detected off-heap memory access. This is currently not supported");
            return super.getStub(n);
        }

        if (!(n.getAddress() instanceof OffsetAddressNode address)) {
            throw GraalError.shouldNotReachHere("Unexpected address node in " + n + " : " + n.getAddress());
        }

        if (n instanceof ReadNode || n instanceof WriteNode) {
            WebImageWasmGCProviders providers = masm().getWasmProviders();

            JavaKind accessKind = providers.util().memoryKind(((LIRLowerableAccess) n).getAccessStamp(NodeView.DEFAULT));
            ResolvedJavaType returnedType = providers.getMetaAccess().lookupJavaType(accessKind == JavaKind.Object ? Object.class : accessKind.toJavaClass());

            WasmGCUnsafeTemplates.DispatchAccess dispatchAccessTemplate = providers.knownIds().dispatchAccessTemplate;
            Instruction base = lowerExpression(address.getBase());
            Instruction offset = lowerExpression(address.getOffset());

            Instruction dispatch;
            if (n instanceof WriteNode write) {
                dispatch = new Instruction.Call(dispatchAccessTemplate.requestWriteFunctionId(accessKind), base, offset, lowerExpression(write.value()));
            } else {
                dispatch = new Instruction.Call(dispatchAccessTemplate.requestReadFunctionId(accessKind), base, offset);
            }
            return optionalDowncast(dispatch, n, returnedType);
        } else {
            throw GraalError.shouldNotReachHere(n.toString());
        }
    }

    @Override
    protected Instruction lowerParam(ParameterNode param) {
        /*
         * The param stamp may differ from the method signature for reference types (e.g. if it is
         * determined that an argument is actually always a specific subtype).
         */

        Instruction paramGet = super.lowerParam(param);

        ResolvedJavaType fromType = masm.compilationResult.getParamTypes()[param.index()];
        return optionalDowncast(paramGet, param, fromType);
    }

    private WebImageWasmGCIds.StaticField getStaticFieldId(ResolvedJavaField field) {
        assert field.isStatic();
        return masm.idFactory.forStaticField(masm.wasmProviders.util().typeForJavaType(field.getType()), field);
    }

    /**
     * Lowers the object on which a field is accessed.
     * <p>
     * Sometimes requires an explicit downcast because the object node can have an imprecise stamp.
     * This can happen when the field access is reconstructed from an unsafe access.
     */
    private Instruction fieldAccessObject(AccessFieldNode node) {
        return optionalDowncast(lowerExpression(node.object()), node, node.object().stamp(NodeView.DEFAULT), node.field().getDeclaringClass());
    }

    private Instruction lowerLoadField(LoadFieldNode node) {
        ResolvedJavaField field = node.field();
        Instruction getter;
        if (field.isStatic()) {
            getter = getStaticFieldId(field).getter();
        } else {
            getter = new Instruction.StructGet(masm.idFactory.newJavaStruct(field.getDeclaringClass()),
                            masm.idFactory.newJavaField(field), Extension.forKind(field.getJavaKind()), fieldAccessObject(node));
        }

        /*
         * Due to strengthened stamps, the LoadFieldNode's stamp may be stronger than the declared
         * field type. Thus, we sometimes require an explicit downcast after field reads.
         */
        return optionalDowncast(getter, node, (ResolvedJavaType) field.getType());
    }

    private Instruction lowerStoreField(StoreFieldNode node) {
        ResolvedJavaField field = node.field();
        if (field.isStatic()) {
            return getStaticFieldId(field).setter(lowerExpression(node.value()));
        }

        return new Instruction.StructSet(masm.idFactory.newJavaStruct(field.getDeclaringClass()), masm.idFactory.newJavaField(field), fieldAccessObject(node), lowerExpression(node.value()));
    }

    private Instruction lowerArrayLength(ArrayLengthNode node) {
        /*
         * TODO GR-47441 either outline array length or also store the length in the struct and read
         * it from there.
         */

        ValueNode arrayNode = node.array();
        Instruction array = lowerExpression(arrayNode);
        Stamp arrayNodeStamp = arrayNode.stamp(NodeView.DEFAULT);

        /*
         * node.array() may not be an array, because there is no common supertype exclusively for
         * arrays (neither in Java nor with stamps). In that case, an extra cast is necessary to our
         * common Wasm supertype for arrays.
         */
        if (!arrayNodeStamp.javaType(masm().getProviders().getMetaAccess()).isArray()) {
            array = new Instruction.RefCast(array, masm().getKnownIds().baseArrayType.asNonNull());
        }

        return builder.getArrayLength(array);
    }

    private Instruction lowerAccessIndexed(AccessIndexedNode node) {
        JavaKind componentKind = node.elementKind();
        ValueNode array = node.array();
        Instruction arrayStruct = lowerExpression(array);
        Instruction index = lowerExpression(node.index());

        if (node instanceof LoadIndexedNode loadIndexed) {
            Instruction getter = builder.getArrayElement(arrayStruct, index, componentKind);

            if (loadIndexed.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp objectStamp) {
                ResolvedJavaType componentType = objectStamp.javaType(masm.getProviders().getMetaAccess());

                /*
                 * The read value needs to have this type, but the Wasm arrays just store
                 * j.l.Object. If it isn't the j.l.Object type, a downcast is needed.
                 */
                WasmRefType componentWasmType = (WasmRefType) util.typeForJavaType(componentType);
                if (!gcUtil.isJavaLangObject(componentWasmType)) {
                    getter = new Instruction.RefCast(getter, componentWasmType);
                }
            }

            return getter;
        } else if (node instanceof StoreIndexedNode storeIndexed) {
            return builder.setArrayElement(arrayStruct, index, lowerExpression(storeIndexed.value()), componentKind);
        } else {
            throw GraalError.shouldNotReachHere(node.toString());
        }
    }

    @Override
    protected Instruction lowerLogicNode(LogicNode n, WasmIRWalker.Requirements reqs) {
        if (n instanceof ObjectIsArrayNode objectIsArray) {
            return lowerObjectIsArray(objectIsArray);
        } else if (n instanceof InstanceOfNode instanceOf) {
            return lowerInstanceOf(instanceOf);
        }

        if (n instanceof InstanceOfDynamicNode instanceOfDynamic) {
            return lowerInstanceOfDynamic(instanceOfDynamic);
        } else if (n instanceof ClassIsAssignableFromNode classIsAssignableFrom) {
            return lowerClassIsAssignableFrom(classIsAssignableFrom);
        }

        return super.lowerLogicNode(n, reqs);
    }

    protected Instruction lowerInstanceOfDynamic(InstanceOfDynamicNode node) {
        boolean allowsNull = node.allowsNull();
        if (node.isExact()) {
            SnippetRuntime.SubstrateForeignCallDescriptor descriptor = allowsNull ? WasmGCTypeCheckSupport.IS_EXACT_OR_NULL : WasmGCTypeCheckSupport.IS_EXACT_NON_NULL;
            return lowerSubstrateForeignCall(descriptor, lowerExpression(node.getObject()), lowerExpression(node.getMirrorOrHub()));
        } else {
            SnippetRuntime.SubstrateForeignCallDescriptor descriptor = WasmGCTypeCheckSupport.INSTANCEOF_DYNAMIC;
            return lowerSubstrateForeignCall(descriptor, lowerExpression(node.getMirrorOrHub()), lowerExpression(node.getObject()), Const.forBoolean(allowsNull));
        }
    }

    protected Instruction lowerClassIsAssignableFrom(ClassIsAssignableFromNode node) {
        SnippetRuntime.SubstrateForeignCallDescriptor descriptor = WasmGCTypeCheckSupport.CLASS_IS_ASSIGNABLE_FROM;
        return lowerSubstrateForeignCall(descriptor, lowerExpression(node.getThisClass()), lowerExpression(node.getOtherClass()));
    }

    protected Instruction lowerObjectIsArray(ObjectIsArrayNode node) {
        return masm().getWasmProviders().builder.isArrayStruct(lowerExpression(node.getValue()), null);
    }

    /**
     * {@link InstanceOfNode}s can usually be serviced by calls to {@link WasmGCTypeCheckSupport}.
     * <p>
     * However, for certain cases, we can directly emit an equivalent {@code ref.test} instructions.
     * This is faster and requires less space. {@code ref.test} works if it is a subtype check and
     * the type that is checked against is either a primitive array, {@code Object[]} (and not any
     * of its subtypes), or an instance class. For all of these types there exists an equivalent
     * Wasm struct that {@code ref.test} can check against.
     */
    protected Instruction lowerInstanceOf(InstanceOfNode node) {
        TypeReference typeReference = node.type();
        HostedType checkType = (HostedType) node.type().getType();
        assert !checkType.isPrimitive() : "instanceof primitive";
        DynamicHub hub = checkType.getHub();
        boolean allowsNull = node.allowsNull();

        Instruction object = lowerExpression(node.getValue());

        if (typeReference.isExact()) {
            /*
             * Exact type checks always have to go through the Java implementation. WasmGC has no
             * instruction to check for type equality.
             */
            SnippetRuntime.SubstrateForeignCallDescriptor descriptor = allowsNull ? WasmGCTypeCheckSupport.IS_EXACT_OR_NULL : WasmGCTypeCheckSupport.IS_EXACT_NON_NULL;
            return lowerSubstrateForeignCall(descriptor, object, lowerConstant(masm().getProviders().getConstantReflection().asJavaClass(checkType)));
        } else {
            if (checkType.isInstanceClass()) {
                return lowerRefTestCheck(masm.idFactory.newJavaStruct(checkType), allowsNull, object);
            }
            ResolvedJavaType componentType = checkType.getComponentType();
            if (checkType.isArray() && (componentType.isPrimitive() || componentType.isJavaLangObject())) {
                /*
                 * Instanceof checks against primitive or j.l.Object arrays can be done using Wasm's
                 * type checks
                 */
                return lowerRefTestCheck(masm().getKnownIds().arrayStructTypes.get(componentType.getJavaKind()), allowsNull, object);
            }

            // Fall back to Java implementation that goes through the hub
            return lowerSubstrateForeignCall(WasmGCTypeCheckSupport.INSTANCEOF, object, Const.forBoolean(allowsNull), Const.forInt(hub.getTypeCheckStart()), Const.forInt(hub.getTypeCheckRange()),
                            Const.forInt(hub.getTypeCheckSlot()));
        }
    }

    private static Instruction lowerRefTestCheck(WasmId.Type testWasmId, boolean allowsNull, Instruction objectToCheck) {
        WasmRefType testWasmType = allowsNull ? testWasmId.asNullable() : testWasmId.asNonNull();
        return new Instruction.RefTest(objectToCheck, testWasmType);
    }

    @Override
    protected Instruction lowerIsNull(IsNullNode n) {
        return Unary.Op.RefIsNull.create(lowerExpression(n.getValue()));
    }

    /**
     * Boxing booleans is a simple {@code value? Boolean.TRUE : Boolean.FALSE}. For all other types,
     * the {@link GCKnownIds#allocatingBoxTemplate} is called to allocate a new boxed instance.
     * <p>
     * TODO GR-54782 Remove once we are using BoxingSnippets
     */
    protected Instruction lowerBox(BoxNode node, WasmIRWalker.Requirements reqs) {
        CoreProviders providers = masm.getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        JavaKind boxedKind = node.getBoxingKind();
        ResolvedJavaType boxing = metaAccess.lookupJavaType(boxedKind.toBoxedJavaClass());
        WebImageWasmGCIds.JavaStruct boxedStruct = masm().idFactory.newJavaStruct(boxing);

        if (boxedKind == JavaKind.Boolean) {
            Instruction.Relocation trueValue = masm().getConstantRelocation(providers.getSnippetReflection().forObject(Boolean.TRUE));
            Instruction.Relocation falseValue = masm().getConstantRelocation(providers.getSnippetReflection().forObject(Boolean.FALSE));
            /*
             * The select instruction interprets any non-zero value as true, so the condition can be
             * lowered without strict logic.
             */
            return new Instruction.Select(trueValue, falseValue, lowerExpression(node.getValue(), reqs.setStrictLogic(false)), boxedStruct.asNullable());
        } else {
            return new Instruction.Call(masm().getKnownIds().allocatingBoxTemplate.requestFunctionId(boxedKind), lowerExpression(node.getValue(), reqs));
        }
    }

    /**
     * Unboxing loads the {@code value} field of the boxed class. For example for {@link Integer}:
     *
     * <pre>{@code
     * (struct.get $Integer $value (...))
     * }</pre>
     * <p>
     * TODO GR-54782 Remove once we are using BoxingSnippets
     */
    protected Instruction lowerUnbox(UnboxNode node) {
        MetaAccessProvider metaAccess = codeGenTool.getProviders().getMetaAccess();
        JavaKind boxedKind = node.getBoxingKind();
        ResolvedJavaType boxing = metaAccess.lookupJavaType(boxedKind.toBoxedJavaClass());
        ResolvedJavaField valueField = AbstractBoxingNode.getValueField(boxing);
        WebImageWasmGCIds.JavaStruct boxedStruct = masm().idFactory.newJavaStruct(boxing);

        return new Instruction.StructGet(boxedStruct, masm().idFactory.newJavaField(valueField), Extension.forKind(boxedKind), lowerExpression(node.getValue()));
    }

    protected Instruction lowerPi(PiNode n, WasmIRWalker.Requirements reqs) {
        Stamp oldStamp = n.getOriginalNode().stamp(NodeView.DEFAULT);
        Stamp newStamp = n.stamp(NodeView.DEFAULT);

        Instruction inner = lowerExpression(n.getOriginalNode(), reqs);

        if (newStamp instanceof PrimitiveStamp) {
            /*
             * Primitive types don't need any explicit cast since Wasm doesn't have any kind of more
             * restricted primitive types.
             */
            return inner;
        } else if (newStamp.isObjectStamp() && oldStamp.isObjectStamp()) {
            /*
             * References to Wasm's struct types have a very strict type system without implicit
             * casts. If an instruction expects a more narrow type (narrowed by a pi node), an
             * explicit downcast must be inserted.
             *
             * Types are canonicalized first so a downcast is only inserted if it is necessary in
             * the WasmGC type system (not just in the Java type system). For example, if both types
             * are interfaces, and one is a subtype of the other, the Java type system treats those
             * as subtypes, but in WasmGC, both are represented as java.lang.Object and no downcast
             * is necessary.
             */
            ResolvedJavaType oldType = util.canonicalizeJavaType(oldStamp.javaType(masm.wasmProviders.getMetaAccess()));
            ResolvedJavaType newType = util.canonicalizeJavaType(newStamp.javaType(masm.wasmProviders.getMetaAccess()));

            // Whether the pi node changes the stamp to a strict subtype
            boolean changedToSubtype = !newType.equals(oldType) && oldType.isAssignableFrom(newType);

            /*
             * If the new stamp is more precise (cast to subtype), we need to emit an explicit cast.
             * Otherwise, the pi node is not necessary.
             */
            if (changedToSubtype) {
                return new Instruction.RefCast(inner, (WasmRefType) util.typeForNode(n));
            } else {
                return inner;
            }
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(newStamp);
        }
    }

    protected <T extends FixedNode & Invoke> Instruction lowerInvoke(T node) {
        CallTargetNode callTarget = node.callTarget();

        Instructions params = new Instructions();
        callTarget.arguments().forEach(param -> params.add(lowerExpression(param)));

        HostedMethod targetMethod = (HostedMethod) callTarget.targetMethod();

        if (targetMethod == null) {
            /*
             * If there is no target method, this must be a method pointer call. The method pointer
             * is stored in the computedAddress of the call target and is a pointer into the global
             * function table.
             */
            IndirectCallTargetNode indirectCallTarget = (IndirectCallTargetNode) callTarget;

            assert !indirectCallTarget.invokeKind().hasReceiver() : "Calls to an address cannot have receivers: " + indirectCallTarget;
            MetaAccessProvider metaAccess = masm().getProviders().getMetaAccess();
            ResolvedJavaType returnType = indirectCallTarget.returnStamp().getTrustedStamp().javaType(metaAccess);
            /*
             * Since there is no target method, the TypeUse has to be reconstructed from the call
             * target node signature.
             */
            TypeUse typeUse = WebImageWasmBackend.signatureToTypeUse(masm().wasmProviders, indirectCallTarget.signature(), returnType);
            WasmId.FuncType targetFuncType = masm().getWasmProviders().util().functionIdForMethod(typeUse);
            Instruction index = Unary.Op.I32Wrap64.create(lowerExpression(indirectCallTarget.computedAddress()));

            return masm().getCall(null, false, new Instruction.CallIndirect(masm().getKnownIds().functionTable, index, targetFuncType, typeUse, params));
        } else {
            CallTargetNode.InvokeKind invokeKind = callTarget.invokeKind();
            HostedMethod[] implementations = targetMethod.getImplementations();

            HostedMethod singleTarget = implementations.length == 1 ? implementations[0] : targetMethod;
            Instruction.AbstractCall call;
            if (invokeKind.isDirect() || implementations.length == 1) {
                WasmId.Func targetMethodId = masm.idFactory.forMethod(singleTarget);
                call = masm.getCall(singleTarget, true, new Instruction.Call(targetMethodId, params));
            } else {
                call = masm.getCall(singleTarget, false, new Instruction.Call(masm().getKnownIds().indirectCallBridgeTemplate.requestFunctionId(singleTarget), params));
            }
            ResolvedJavaType returnType = WebImageWasmBackend.constructReturnType(singleTarget);
            /*
             * The analysis may determine that this invoke returns a subtype of the declared return
             * type, making an explicit cast necessary.
             */
            return optionalDowncast(call, node, returnType);
        }
    }

    /**
     * Inserts a call to {@link WasmDebug#logError(String)} to abort with an error message.
     */
    protected void logError(String msg) {
        /*
         * Create image heap constant for error message. Interned, so it's not duplicated in the
         * image heap.
         */
        Instruction string = masm().getConstantRelocation(codeGenTool.getProviders().getConstantReflection().forString(msg.intern()));
        /*
         * Emit as top-level instruction in the current block. The call does not produce the correct
         * value for the node and can't be used as the lowering.
         */
        masm.genInst(lowerSubstrateForeignCall(WasmDebug.LOG_ERROR, string), msg);
    }

    /**
     * Inserts a call to {@link WasmDebug#logError(String)} to abort with an error message if this
     * node is reached and calls implementation in superclass to generate the actual stub
     * instruction.
     * <p>
     * TODO GR-47009 Stop generating stub code once all nodes are supported.
     */
    @Override
    protected Instruction getStub(Node n) {
        if (WebImageWasmOptions.fatalUnsupportedNodes()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("Node of type ").append(n.getClass()).append(" is not supported: ");

            NodeSourcePosition nsp = n.getNodeSourcePosition();
            if (nsp == null) {
                errorMessage.append(((StructuredGraph) n.graph()).method().format("%H.%n(%P)"));
            } else {
                errorMessage.append(System.lineSeparator());
                errorMessage.append(nsp);
            }

            logError(errorMessage.toString());
        }

        return super.getStub(n);
    }

    /**
     * Creates an instruction for the given node that produces the right type of value.
     *
     * TODO GR-47009 Stop generating stub code.
     */
    @Override
    protected Instruction getValueStub(ValueNode node) {
        JavaKind wasmKind = util.kindForNode(node);

        if (wasmKind != JavaKind.Object) {
            return super.getValueStub(node);
        }

        WasmRefType.TypeIndex type = (WasmRefType.TypeIndex) util.typeForNode(node);

        if (type.nullable) {
            return new Instruction.RefNull(type);
        } else {
            AbstractObjectStamp stamp = (AbstractObjectStamp) node.stamp(NodeView.DEFAULT);
            ResolvedJavaType javaType = stamp.javaType(masm.wasmProviders.getMetaAccess());
            // For non-null ref types, we need to actually produce an instance of that type.
            if (javaType.isArray()) {
                // The stub just allocates an empty array
                return new Instruction.StructNew((WasmId.StructType) type.id, Const.forInt(0),
                                new Instruction.ArrayNew(masm().getKnownIds().innerArrayTypes.get(javaType.getComponentType().getJavaKind()), Const.forInt(0)));
            } else {
                // This will only work as long as all fields are defaultable (primitive or
                // nullable).
                return new Instruction.StructNew((WasmId.StructType) type.id);
            }
        }
    }

    @Override
    protected Instruction lowerConstant(ConstantNode n) {
        if (n.isNullConstant()) {
            return new Instruction.RefNull((WasmRefType.TypeIndex) util.typeForNode(n));
        }

        return super.lowerConstant(n);
    }

    /**
     * @param wasmExtern An instruction holding a {@link WasmExtern} instance.
     */
    private Instruction callToExtern(Instruction wasmExtern) {
        return new Instruction.Call(masm().getKnownIds().toExternTemplate.requestFunctionId(), wasmExtern);
    }

    /**
     * @param externref An instruction holding a {@code externref} value.
     */
    private Instruction callWrapExtern(Instruction externref) {
        return new Instruction.Call(masm().getKnownIds().wrapExternTemplate.requestFunctionId(), externref);
    }

    @Override
    protected Instruction lowerWasmImportForeignCall(WasmImportForeignCallDescriptor descriptor, Instructions args) {
        Class<?>[] argTypes = descriptor.getArgumentTypes();
        for (int i = 0; i < argTypes.length; i++) {
            if (argTypes[i] == WasmExtern.class) {
                // Unwrap the WasmExtern so that the imported host function sees a host object
                args.get().set(i, callToExtern(args.get().get(i)));
            }
        }

        WasmId.Func target = masm.idFactory.forFunctionImport(descriptor.getImportDescriptor(masm.wasmProviders));
        Instruction result = new Instruction.Call(target, args);

        if (descriptor.getResultType() == WasmExtern.class) {
            /*
             * The returned value is not a Java object, but a host object. It needs to be wrapped in
             * WasmExtern so that it can be used in Java code.
             */
            result = new Instruction.RefCast(callWrapExtern(result), masm().getWasmProviders().util().typeForJavaClass(WasmExtern.class));
        }

        return result;
    }

    @Override
    protected Instruction lowerWasmForeignCall(WasmForeignCallDescriptor descriptor, Instructions args) {
        if (WasmGCArrayCopySupport.COPY_FOREIGN_CALLS.containsKey(descriptor)) {
            JavaKind componentKind = WasmGCArrayCopySupport.COPY_FOREIGN_CALLS.get(descriptor);
            return new Instruction.Call(masm().getKnownIds().arrayCopyTemplate.requestFunctionId(componentKind), args);
        } else if (descriptor == WasmGCTypeCheckSupport.SLOT_TYPE_CHECK) {
            return lowerSlotTypeCheck(args);
        } else if (descriptor == WasmGCCloneSupport.CLONE_TEMPLATE) {
            return new Instruction.Call(masm().getKnownIds().genericCloneTemplate.requestFunctionId(), args);
        } else if (descriptor == WasmGCJSConversion.EXTRACT_JS_NATIVE) {
            return new Instruction.Call(masm().getKnownIds().extractJSValueTemplate.requestGetterFunctionId(), args);
        } else if (descriptor == WasmGCJSConversion.SET_JS_NATIVE) {
            return new Instruction.Call(masm().getKnownIds().extractJSValueTemplate.requestSetterFunctionId(), args);
        } else {
            return super.lowerWasmForeignCall(descriptor, args);
        }
    }

    @Override
    protected Instruction lowerForeignCall(ForeignCall n) {
        ResolvedJavaType returnedType = masm().wasmProviders.getMetaAccess().lookupJavaType(n.getDescriptor().getResultType());
        /*
         * The foreign call may have a stronger stamp than the return type of the method it refers
         * to, in which case, a downcast is necessary
         */
        return optionalDowncast(super.lowerForeignCall(n), n.asNode(), returnedType);
    }

    /**
     * Implements {@code TypeSnippets#slotTypeCheck} in Wasm. This is the code generated for foreign
     * calls to {@link WasmGCTypeCheckSupport#SLOT_TYPE_CHECK}.
     * <p>
     * Produces an instruction to compute:
     *
     * <pre> {@code
     * (checkedHub.closedTypeWorldTypeCheckSlots[slot] - start) < range
     * }</pre>
     */
    protected Instruction lowerSlotTypeCheck(Instructions args) {
        List<Instruction> instrs = args.get();
        assert instrs.size() == 4 : "Unexpected number of arguments for slot type check: " + instrs;

        WasmId.StructType hubType = masm().idFactory.newJavaStruct(masm().getProviders().getMetaAccess().lookupJavaType(DynamicHub.class));
        WasmId.Field typeCheckSlotsField = masm().getKnownIds().typeCheckSlotsField;
        WasmId.ArrayType typeCheckSlotsFieldType = masm().getKnownIds().typeCheckSlotsFieldType;

        Instruction start = instrs.get(0);
        Instruction range = instrs.get(1);
        Instruction slot = instrs.get(2);
        Instruction checkedHub = instrs.get(3);
        Instruction typeCheckSlots = new Instruction.StructGet(hubType, typeCheckSlotsField, Extension.None, checkedHub);
        Instruction checkedTypeId = new Instruction.ArrayGet(typeCheckSlotsFieldType, Extension.forKind(JavaKind.Short), typeCheckSlots, slot);
        return Instruction.Binary.Op.I32LtU.create(Instruction.Binary.Op.I32Sub.create(checkedTypeId, start), range);
    }

    /**
     * Generates a call to an imported JS function.
     * <p>
     * The assumption for these calls are that the JS code expects only JS values and it can't deal
     * with references to Wasm structs or arrays. Because of that any object that is passed as an
     * argument is first proxied (see {@link WasmGCConversion#proxyObject(Object)}) and converted to
     * an {@code externref} (see {@code WasmGCFunctionTemplates.ToExtern}).
     * <p>
     * For object return values, the JS code has to call the appropriate conversion function.
     *
     * @see WasmJSCounterparts
     */
    private Instruction lowerJSCall(JSCallNode n) {
        JSSystemFunction func = n.getFunctionDefinition();

        Instructions params = new Instructions();
        for (ValueNode param : n.getArguments()) {
            Instruction paramInstruction = lowerExpression(param);

            if (param.stamp(NodeView.DEFAULT).isObjectStamp()) {
                // Object arguments are proxied, which produces a WasmExtern instance
                paramInstruction = lowerSubstrateForeignCall(WasmGCConversion.PROXY_OBJECT, Instructions.asInstructions(paramInstruction));
                // That WasmExtern instance then has to be unwrapped to an externref value
                paramInstruction = new Instruction.Call(masm().getWasmProviders().knownIds().toExternTemplate.requestFunctionId(), paramInstruction);
            }

            params.add(paramInstruction);
        }
        Instruction callResult = new Instruction.Call(masm.wasmProviders.getJSCounterparts().idForJSFunction(masm.wasmProviders, func), params);

        if (func.stamp().isObjectStamp()) {
            /*
             * Functions returning an object, receive an externref from the JS code, which has to be
             * converted/wrapped to/in a Java object.
             */
            callResult = callWrapExtern(callResult);
        }

        return callResult;
    }

    /**
     * Generates a call to an imported JS function that contains the code of the given
     * {@link JSBody} node.
     * <p>
     * Any conversions and coercions are not done here, but are already present in the IR as
     * separate method calls.
     * <p>
     * The only "conversion" done here is to ensure all object arguments are converted to
     * {@code externref} (see {@link GCKnownIds#toExternTemplate}) and any object return value is
     * turned from an {@code externref} to a Java object (see
     * {@link GCKnownIds#wrapExternTemplate}).
     *
     */
    private <T extends FixedNode & JSBody> Instruction lowerJSBody(T jsBody) {
        WebImageWasmGCProviders wasmProviders = masm().getWasmProviders();

        Instructions params = new Instructions();
        for (ValueNode param : jsBody.getArguments()) {
            Instruction paramInstruction = lowerExpression(param);

            if (param.stamp(NodeView.DEFAULT).isObjectStamp()) {
                paramInstruction = new Instruction.Call(wasmProviders.knownIds().toExternTemplate.requestFunctionId(), paramInstruction);
            }

            params.add(paramInstruction);
        }

        Instruction returnValue = new Instruction.Call(wasmProviders.getJSCounterparts().idForJSBody(wasmProviders, jsBody), params);

        if (jsBody.stamp(NodeView.DEFAULT).isObjectStamp()) {
            returnValue = new Instruction.Call(wasmProviders.knownIds().wrapExternTemplate.requestFunctionId(), returnValue);
        }

        return returnValue;
    }

    @Override
    protected Instruction lowerWordCast(WordCastNode n) {
        // TODO GR-60168 Eliminate WordCastNodes completely. They are fundamentally not supportable
        // under WasmGC
        logError("This method should never be reached and cannot be supported.");
        return super.getStub(n);
    }
}
