/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import static org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.BufferUtil;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.c.CGlobalDataNonConstantRegistry;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticFeature
public class CGlobalDataFeature implements GraalFeature {

    private final Method getCGlobalDataInfoMethod = ReflectionUtil.lookupMethod(CGlobalDataNonConstantRegistry.class, "getCGlobalDataInfo", CGlobalDataImpl.class);
    private final Field offsetField = ReflectionUtil.lookupField(CGlobalDataInfo.class, "offset");
    private final Field isSymbolReferenceField = ReflectionUtil.lookupField(CGlobalDataInfo.class, "isSymbolReference");

    private final CGlobalDataNonConstantRegistry nonConstantRegistry = new CGlobalDataNonConstantRegistry();
    private final JavaConstant nonConstantRegistryJavaConstant = SubstrateObjectConstant.forObject(nonConstantRegistry);

    private final Map<CGlobalDataImpl<?>, CGlobalDataInfo> map = new ConcurrentHashMap<>();
    private CGlobalDataInfo cGlobalDataBaseAddress;
    private int totalSize = -1;

    public static CGlobalDataFeature singleton() {
        return ImageSingletons.lookup(CGlobalDataFeature.class);
    }

    private boolean isLayouted() {
        return totalSize != -1;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::replaceObject);
        cGlobalDataBaseAddress = registerAsAccessedOrGet(CGlobalDataInfo.CGLOBALDATA_RUNTIME_BASE_ADDRESS);
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        layout();
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, InvocationPlugins invocationPlugins, boolean analysis, boolean hosted) {
        Registration r = new Registration(invocationPlugins, CGlobalData.class);
        r.register1("get", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext builderContext, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode cGlobalDataNode = receiver.get();
                if (cGlobalDataNode.isConstant()) {
                    CGlobalDataImpl<?> data = (CGlobalDataImpl<?>) SubstrateObjectConstant.asObject(cGlobalDataNode.asConstant());
                    CGlobalDataInfo info = CGlobalDataFeature.this.map.get(data);
                    builderContext.addPush(targetMethod.getSignature().getReturnKind(), new CGlobalDataLoadAddressNode(info));
                } else {
                    ConstantNode nonConstantRegistryNode = ConstantNode.forConstant(nonConstantRegistryJavaConstant, builderContext.getMetaAccess(), builderContext.getGraph());

                    ResolvedJavaMethod getCGlobalDataInfoResolvedMethod = builderContext.getMetaAccess().lookupJavaMethod(getCGlobalDataInfoMethod);
                    ValueNode cGlobalDataInfo = (ValueNode) builderContext.handleReplacedInvoke(InvokeKind.Virtual, getCGlobalDataInfoResolvedMethod,
                                    new ValueNode[]{nonConstantRegistryNode, cGlobalDataNode}, false);
                    builderContext.pop(cGlobalDataInfo.getStackKind());

                    ResolvedJavaField offset = builderContext.getMetaAccess().lookupJavaField(offsetField);
                    ValueNode offsetFieldNode = builderContext.add(LoadFieldNode.create(builderContext.getAssumptions(), cGlobalDataInfo, offset)); // cGlobalDataInfo.offset

                    CGlobalDataLoadAddressNode cGlobalDataBaseAddressNode = builderContext.add(new CGlobalDataLoadAddressNode(cGlobalDataBaseAddress));
                    /* Both operands should have the same bits size */
                    ValueNode cGlobalDataInfoOffsetWidened = builderContext.getGraph()
                                    .addOrUnique(SignExtendNode.create(offsetFieldNode, IntegerStamp.getBits(cGlobalDataBaseAddressNode.stamp(NodeView.DEFAULT)), NodeView.DEFAULT));
                    OffsetAddressNode cGlobalDataAddress = builderContext.add(new OffsetAddressNode(cGlobalDataBaseAddressNode, cGlobalDataInfoOffsetWidened));

                    /* Should not dereference the address if CGlobalDataInfo is not a reference */
                    ResolvedJavaField isSymbolReference = builderContext.getMetaAccess().lookupJavaField(isSymbolReferenceField);
                    ValueNode isSymbolReferenceNode = builderContext.add(LoadFieldNode.create(builderContext.getAssumptions(), cGlobalDataInfo, isSymbolReference)); // cGlobalDataInfo.isSymbolReference
                    LogicNode logicNode = IntegerEqualsNode.create(isSymbolReferenceNode, ConstantNode.forBoolean(false, builderContext.getGraph()), NodeView.DEFAULT);

                    AddNode calculatedAddress = builderContext.add(new AddNode(cGlobalDataAddress.getBase(), cGlobalDataAddress.getOffset()));
                    ReadNode cGlobalDataValue = builderContext.add(new ReadNode(
                                    cGlobalDataAddress,
                                    NamedLocationIdentity.ANY_LOCATION,
                                    cGlobalDataBaseAddressNode.stamp(NodeView.DEFAULT),
                                    OnHeapMemoryAccess.BarrierType.NONE));

                    AbstractBeginNode trueBegin = builderContext.add(new BeginNode());
                    FixedWithNextNode predecessor = (FixedWithNextNode) trueBegin.predecessor();
                    predecessor.setNext(null);
                    AbstractBeginNode falseBegin = builderContext.add(new BeginNode());
                    trueBegin.setNext(null);
                    IfNode ifNode = builderContext.add(new IfNode(logicNode, trueBegin, falseBegin, VERY_SLOW_PATH_PROBABILITY));
                    falseBegin.setNext(null);
                    predecessor.setNext(ifNode);

                    EndNode thenEnd = builderContext.add(new EndNode());
                    trueBegin.setNext(thenEnd);
                    EndNode elseEnd = builderContext.add(new EndNode());
                    falseBegin.setNext(elseEnd);
                    AbstractMergeNode merge = builderContext.add(new MergeNode());
                    merge.addForwardEnd(thenEnd);
                    merge.addForwardEnd(elseEnd);

                    ValuePhiNode phiNode = new ValuePhiNode(cGlobalDataBaseAddressNode.stamp(NodeView.DEFAULT), merge, new ValueNode[]{calculatedAddress, cGlobalDataValue});
                    builderContext.push(targetMethod.getSignature().getReturnKind(), builderContext.getGraph().addOrUnique(phiNode));
                    builderContext.setStateAfter(merge);
                }
                return true;
            }
        });
    }

    public CGlobalDataInfo registerAsAccessedOrGet(CGlobalData<?> obj) {
        CGlobalDataImpl<?> data = (CGlobalDataImpl<?>) obj;
        VMError.guarantee(!isLayouted() || map.containsKey(data), "CGlobalData instance must have been discovered/registered before or during analysis");
        return map.computeIfAbsent((CGlobalDataImpl<?>) obj,
                        o -> {
                            CGlobalDataInfo cGlobalDataInfo = new CGlobalDataInfo(data);
                            if (data.nonConstant) {
                                nonConstantRegistry.registerNonConstantSymbol(cGlobalDataInfo);
                            }
                            return cGlobalDataInfo;
                        });
    }

    private Object replaceObject(Object obj) {
        if (obj instanceof CGlobalDataImpl<?>) {
            registerAsAccessedOrGet((CGlobalData<?>) obj);
        }
        return obj;
    }

    private void layout() {
        assert !isLayouted() : "Already layouted";
        final int wordSize = ConfigurationValues.getTarget().wordSize;
        int offset = 0;
        for (Entry<CGlobalDataImpl<?>, CGlobalDataInfo> entry : map.entrySet()) {
            CGlobalDataImpl<?> data = entry.getKey();
            CGlobalDataInfo info = entry.getValue();
            int size;
            byte[] bytes = null;
            if (data.bytesSupplier != null) {
                bytes = data.bytesSupplier.get();
                size = bytes.length;
            } else {
                if (data.sizeSupplier != null) {
                    size = data.sizeSupplier.getAsInt();
                } else {
                    assert data.symbolName != null : "CGlobalData without bytes, size, or referenced symbol";
                    /*
                     * A symbol reference: we support only instruction-pointer-relative addressing
                     * with 32-bit immediates, which might not be sufficient for the target symbol's
                     * address. Therefore, reserve space for a word with the symbol's true address.
                     */
                    size = wordSize;
                }
            }
            info.assign(offset, bytes);

            offset += size;
            offset = (offset + (wordSize - 1)) & ~(wordSize - 1); // align
        }
        totalSize = offset;
        assert isLayouted();
    }

    public int getSize() {
        assert isLayouted() : "Not layouted yet";
        return totalSize;
    }

    public void writeData(RelocatableBuffer buffer, BiFunction<Integer, String, ?> createSymbol, BiFunction<Integer, String, ?> createSymbolReference) {
        assert isLayouted() : "Not layouted yet";
        ByteBuffer bufferBytes = buffer.getByteBuffer();
        int start = bufferBytes.position();
        assert IntStream.range(start, start + totalSize).allMatch(i -> bufferBytes.get(i) == 0) : "Buffer must be zero-initialized";
        for (CGlobalDataInfo info : map.values()) {
            byte[] bytes = info.getBytes();
            if (bytes != null) {
                BufferUtil.asBaseBuffer(bufferBytes).position(start + info.getOffset());
                bufferBytes.put(bytes, 0, bytes.length);
            }
            CGlobalDataImpl<?> data = info.getData();
            if (data.symbolName != null && !info.isSymbolReference()) {
                createSymbol.apply(info.getOffset(), data.symbolName);
            }
            if (data.nonConstant) {
                createSymbolReference.apply(info.getOffset(), data.symbolName);
            }
        }
    }
}
