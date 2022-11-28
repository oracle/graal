/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
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
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.c.CGlobalDataNonConstantRegistry;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticallyRegisteredFeature
public class CGlobalDataFeature implements InternalFeature {

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
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, Plugins plugins, ParsingReason reason) {
        Registration r = new Registration(plugins.getInvocationPlugins(), CGlobalData.class);
        r.register(new RequiredInvocationPlugin("get", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode cGlobalDataNode = receiver.get();
                if (cGlobalDataNode.isConstant()) {
                    CGlobalDataImpl<?> data = (CGlobalDataImpl<?>) SubstrateObjectConstant.asObject(cGlobalDataNode.asConstant());
                    CGlobalDataInfo info = CGlobalDataFeature.this.map.get(data);
                    b.addPush(targetMethod.getSignature().getReturnKind(), new CGlobalDataLoadAddressNode(info));
                } else {
                    ConstantNode registry = ConstantNode.forConstant(nonConstantRegistryJavaConstant, b.getMetaAccess(), b.getGraph());

                    ValueNode info = (ValueNode) b.handleReplacedInvoke(InvokeKind.Virtual, b.getMetaAccess().lookupJavaMethod(getCGlobalDataInfoMethod),
                                    new ValueNode[]{registry, cGlobalDataNode}, false);
                    b.pop(info.getStackKind());
                    info = b.nullCheckedValue(info);

                    ResolvedJavaType infoType = b.getMetaAccess().lookupJavaType(CGlobalDataInfo.class);
                    if (infoType instanceof AnalysisType) {
                        ((AnalysisType) infoType).registerAsReachable("registered by " + CGlobalDataFeature.class.getName());
                    }

                    ValueNode offset = b.add(LoadFieldNode.create(b.getAssumptions(), info, b.getMetaAccess().lookupJavaField(offsetField)));
                    CGlobalDataLoadAddressNode baseAddress = b.add(new CGlobalDataLoadAddressNode(cGlobalDataBaseAddress));

                    /* Both operands should have the same bits size */
                    ValueNode offsetWidened = b.getGraph().addOrUnique(SignExtendNode.create(offset, IntegerStamp.getBits(baseAddress.stamp(NodeView.DEFAULT)), NodeView.DEFAULT));
                    ValueNode address = b.add(new AddNode(baseAddress, offsetWidened));

                    /* Do not dereference the address if CGlobalDataInfo is not a reference */
                    ValueNode isSymbolReference = b.add(LoadFieldNode.create(b.getAssumptions(), info, b.getMetaAccess().lookupJavaField(isSymbolReferenceField)));
                    LogicNode condition = IntegerEqualsNode.create(isSymbolReference, ConstantNode.forBoolean(false, b.getGraph()), NodeView.DEFAULT);
                    ReadNode readValue = b.add(new ReadNode(b.add(OffsetAddressNode.create(address)), NamedLocationIdentity.ANY_LOCATION,
                                    baseAddress.stamp(NodeView.DEFAULT), OnHeapMemoryAccess.BarrierType.NONE, MemoryOrderMode.PLAIN));

                    AbstractBeginNode trueBegin = b.add(new BeginNode());
                    FixedWithNextNode predecessor = (FixedWithNextNode) trueBegin.predecessor();
                    predecessor.setNext(null);
                    AbstractBeginNode falseBegin = b.add(new BeginNode());
                    trueBegin.setNext(null);
                    IfNode ifNode = b.add(new IfNode(condition, trueBegin, falseBegin, BranchProbabilityNode.NOT_LIKELY_PROFILE));
                    falseBegin.setNext(null);
                    predecessor.setNext(ifNode);

                    EndNode thenEnd = b.add(new EndNode());
                    trueBegin.setNext(thenEnd);
                    EndNode elseEnd = b.add(new EndNode());
                    falseBegin.setNext(elseEnd);

                    AbstractMergeNode merge = b.add(new MergeNode());
                    merge.addForwardEnd(thenEnd);
                    merge.addForwardEnd(elseEnd);
                    ValuePhiNode phiNode = new ValuePhiNode(StampFactory.pointer(), merge, new ValueNode[]{address, readValue});
                    phiNode.inferStamp();
                    b.push(targetMethod.getSignature().getReturnKind(), b.getGraph().addOrUnique(phiNode));
                    b.setStateAfter(merge);
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

    /**
     * Makes the provided object available in the binary as a global symbol
     *
     * Warning: Global symbols are affected by linking and loading rules that are OS dependent. So
     * accessing a global symbol content at run time using the symbol name could return a different
     * content than the one provided at build time. This happens for example on Linux when a shared
     * library is loaded and the main executable already defines a symbol with the same name.
     */
    public void registerWithGlobalSymbol(CGlobalData<?> obj) {
        registerWithGlobalSymbolImpl(obj);
    }

    /**
     * Same as {@link #registerWithGlobalSymbol(CGlobalData)} but hides the provided object so that
     * it will not show up in the dynamic symbol table of the final image.
     */
    public void registerWithGlobalHiddenSymbol(CGlobalData<?> obj) {
        registerWithGlobalSymbolImpl(obj).makeHiddenSymbol();
    }

    private CGlobalDataInfo registerWithGlobalSymbolImpl(CGlobalData<?> obj) {
        CGlobalDataInfo info = registerAsAccessedOrGet(obj);
        info.makeGlobalSymbol();
        return info;
    }

    public Set<String> getGlobalHiddenSymbols() {
        return map.entrySet().stream().filter(entry -> entry.getValue().isGlobalSymbol() && entry.getValue().isHiddenSymbol()).map(entry -> entry.getKey().symbolName).collect(Collectors.toSet());
    }

    private Object replaceObject(Object obj) {
        if (obj instanceof CGlobalDataImpl<?>) {
            registerAsAccessedOrGet((CGlobalData<?>) obj);
        }
        return obj;
    }

    private static CGlobalDataInfo assignCGlobalDataSize(Map.Entry<CGlobalDataImpl<?>, CGlobalDataInfo> entry, int wordSize) {
        CGlobalDataImpl<?> data = entry.getKey();
        CGlobalDataInfo info = entry.getValue();

        if (data.bytesSupplier != null) {
            byte[] bytes = data.bytesSupplier.get();
            info.assignSize(bytes.length);
            info.assignBytes(bytes);
        } else {
            if (data.sizeSupplier != null) {
                info.assignSize(data.sizeSupplier.getAsInt());
            } else {
                assert data.symbolName != null : "CGlobalData without bytes, size, or referenced symbol";
                /*
                 * A symbol reference: we support only instruction-pointer-relative addressing with
                 * 32-bit immediates, which might not be sufficient for the target symbol's address.
                 * Therefore, reserve space for a word with the symbol's true address.
                 */
                info.assignSize(wordSize);
            }
        }
        return info;
    }

    private void layout() {
        assert !isLayouted() : "Already layouted";
        final int wordSize = ConfigurationValues.getTarget().wordSize;
        /*
         * Put larger blobs at the end so that offsets are reasonable (<24bit imm) for smaller
         * entries
         */
        totalSize = map.entrySet().stream()
                        .map(entry -> assignCGlobalDataSize(entry, wordSize))
                        .sorted(Comparator.comparing(CGlobalDataInfo::getSize))
                        .reduce(0, (currentOffset, info) -> {
                            info.assignOffset(currentOffset);

                            int nextOffset = currentOffset + info.getSize();
                            return (nextOffset + (wordSize - 1)) & ~(wordSize - 1); // align
                        }, Integer::sum);
        assert isLayouted();
    }

    public int getSize() {
        assert isLayouted() : "Not layouted yet";
        return totalSize;
    }

    public interface SymbolConsumer {
        void apply(int offset, String symbolName, boolean isGlobalSymbol);
    }

    public void writeData(RelocatableBuffer buffer, SymbolConsumer createSymbol, SymbolConsumer createSymbolReference) {
        assert isLayouted() : "Not layouted yet";
        ByteBuffer bufferBytes = buffer.getByteBuffer();
        int start = bufferBytes.position();
        assert IntStream.range(start, start + totalSize).allMatch(i -> bufferBytes.get(i) == 0) : "Buffer must be zero-initialized";
        for (CGlobalDataInfo info : map.values()) {
            byte[] bytes = info.getBytes();
            if (bytes != null) {
                bufferBytes.position(start + info.getOffset());
                bufferBytes.put(bytes, 0, bytes.length);
            }
            CGlobalDataImpl<?> data = info.getData();
            if (data.symbolName != null && !info.isSymbolReference()) {
                createSymbol.apply(info.getOffset(), data.symbolName, info.isGlobalSymbol());
            }
            if (data.nonConstant && data.symbolName != null) {
                createSymbolReference.apply(info.getOffset(), data.symbolName, info.isGlobalSymbol());
            }
        }
    }
}
