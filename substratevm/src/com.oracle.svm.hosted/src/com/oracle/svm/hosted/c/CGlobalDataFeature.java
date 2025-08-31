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

import static jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.c.BoxedRelocatedPointer;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.c.CGlobalDataNonConstantRegistry;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.nodes.CGlobalDataLoadAddressNode;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.hosted.imagelayer.CodeLocation;
import com.oracle.svm.hosted.meta.HostedSnippetReflectionProvider;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class CGlobalDataFeature implements InternalFeature {

    private final Method getCGlobalDataInfoMethod = ReflectionUtil.lookupMethod(CGlobalDataNonConstantRegistry.class, "getCGlobalDataInfo", CGlobalDataImpl.class);
    private final Field offsetField = ReflectionUtil.lookupField(CGlobalDataInfo.class, "offset");
    private final Field isSymbolReferenceField = ReflectionUtil.lookupField(CGlobalDataInfo.class, "isSymbolReference");
    private final Field baseHolderPointerField = ReflectionUtil.lookupField(BoxedRelocatedPointer.class, "pointer");

    private final CGlobalDataNonConstantRegistry nonConstantRegistry = new CGlobalDataNonConstantRegistry();

    private final Map<CGlobalDataImpl<?>, CGlobalDataInfo> map = new ConcurrentHashMap<>();
    private int totalSize = -1;

    private final Set<CodeLocation> seenCodeLocations = ImageLayerBuildingSupport.buildingImageLayer() ? ConcurrentHashMap.newKeySet() : null;

    @SuppressWarnings("this-escape") //
    private final InitialLayerCGlobalTracking initialLayerCGlobalTracking = ImageLayerBuildingSupport.buildingInitialLayer() ? new InitialLayerCGlobalTracking(this) : null;
    @SuppressWarnings("this-escape") //
    private final AppLayerCGlobalTracking appLayerCGlobalTracking = ImageLayerBuildingSupport.buildingApplicationLayer() ? new AppLayerCGlobalTracking(this) : null;

    public static CGlobalDataFeature singleton() {
        return ImageSingletons.lookup(CGlobalDataFeature.class);
    }

    private boolean isLaidOut() {
        return totalSize != -1;
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        a.registerObjectReplacer(this::replaceObject);
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        layout();
    }

    @Override
    public void registerInvocationPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        Registration r = new Registration(plugins.getInvocationPlugins(), CGlobalData.class);
        r.register(new RequiredInvocationPlugin("get", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                assert providers.getSnippetReflection() instanceof HostedSnippetReflectionProvider;
                ValueNode cGlobalDataNode = receiver.get(true);
                CGlobalDataInfo constantInfo = null;
                if (cGlobalDataNode.isConstant()) {
                    CGlobalDataImpl<?> data = providers.getSnippetReflection().asObject(CGlobalDataImpl.class, cGlobalDataNode.asJavaConstant());
                    constantInfo = CGlobalDataFeature.this.map.get(data);
                }

                if (constantInfo != null && reason != ParsingReason.JITCompilation) {
                    /* Use a relocation in code to load the location directly */
                    b.addPush(targetMethod.getSignature().getReturnKind(), new CGlobalDataLoadAddressNode(constantInfo));
                } else {
                    ValueNode info;
                    if (constantInfo != null) {
                        /*
                         * JIT-compiled code must get the CGlobalData base address from the holder
                         * object on the image heap because the code can end up in an auxiliary
                         * image which is loaded in another process with a different base address.
                         */
                        JavaConstant infoConstant = providers.getSnippetReflection().forObject(constantInfo);
                        info = ConstantNode.forConstant(infoConstant, b.getMetaAccess(), b.getGraph());
                    } else {
                        // Non-constant CGlobalData must be resolved at runtime through a map.
                        JavaConstant nonConstantRegistryJavaConstant = providers.getSnippetReflection().forObject(nonConstantRegistry);
                        ConstantNode registry = ConstantNode.forConstant(nonConstantRegistryJavaConstant, b.getMetaAccess(), b.getGraph());

                        info = (ValueNode) b.handleReplacedInvoke(InvokeKind.Virtual, b.getMetaAccess().lookupJavaMethod(getCGlobalDataInfoMethod),
                                        new ValueNode[]{registry, cGlobalDataNode}, false);
                        b.pop(info.getStackKind());
                        info = b.nullCheckedValue(info);

                        ResolvedJavaType infoType = b.getMetaAccess().lookupJavaType(CGlobalDataInfo.class);
                        if (infoType instanceof AnalysisType aInfoType) {
                            aInfoType.registerAsReachable("registered by " + CGlobalDataFeature.class.getName());
                        }
                    }

                    JavaConstant baseHolderConstant = providers.getSnippetReflection().forObject(CGlobalDataInfo.CGLOBALDATA_RUNTIME_BASE_ADDRESS);
                    ConstantNode baseHolder = ConstantNode.forConstant(baseHolderConstant, b.getMetaAccess(), b.getGraph());
                    ResolvedJavaField holderPointerField = providers.getMetaAccess().lookupJavaField(baseHolderPointerField);
                    StampPair pointerStamp = StampPair.createSingle(providers.getWordTypes().getWordStamp((ResolvedJavaType) holderPointerField.getType()));
                    LoadFieldNode baseAddress = b.add(LoadFieldNode.createOverrideStamp(pointerStamp, baseHolder, holderPointerField));

                    /* Both address and offset need to have the same bit width. */
                    ValueNode offset = b.add(LoadFieldNode.create(b.getAssumptions(), info, b.getMetaAccess().lookupJavaField(offsetField)));
                    ValueNode offsetWidened = b.getGraph().addOrUnique(SignExtendNode.create(offset, IntegerStamp.getBits(baseAddress.stamp(NodeView.DEFAULT)), NodeView.DEFAULT));
                    ValueNode address = b.add(new AddNode(baseAddress, offsetWidened));

                    /* Dereference the address if CGlobalDataInfo is a symbol reference. */
                    ValueNode isSymbolReference = b.add(LoadFieldNode.create(b.getAssumptions(), info, b.getMetaAccess().lookupJavaField(isSymbolReferenceField)));
                    LogicNode condition = IntegerEqualsNode.create(isSymbolReference, ConstantNode.forBoolean(false, b.getGraph()), NodeView.DEFAULT);
                    ReadNode readValue = b.add(new ReadNode(b.add(OffsetAddressNode.create(address)), NamedLocationIdentity.ANY_LOCATION,
                                    baseAddress.stamp(NodeView.DEFAULT), BarrierType.NONE, MemoryOrderMode.PLAIN));

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

                    AbstractMergeNode merge = b.append(new MergeNode());
                    merge.addForwardEnd(thenEnd);
                    merge.addForwardEnd(elseEnd);
                    ValuePhiNode phiNode = new ValuePhiNode(StampFactory.pointer(), merge, address, readValue);
                    phiNode.inferStamp();
                    b.push(targetMethod.getSignature().getReturnKind(), b.getGraph().addOrUnique(phiNode));
                    b.setStateAfter(merge);
                }
                return true;
            }
        });
    }

    CGlobalDataInfo getDataInfo(CGlobalDataImpl<?> data) {
        return map.get(data);
    }

    public CGlobalDataInfo registerAsAccessedOrGet(CGlobalData<?> obj) {
        return registerAsAccessedOrGet(obj, true);
    }

    /**
     * {@link #registerAsAccessedOrGet(CGlobalData)} should normally be used instead of this method.
     */
    CGlobalDataInfo registerAsAccessedOrGet(CGlobalData<?> obj, boolean tryCanonicalization) {
        CGlobalDataImpl<?> data = (CGlobalDataImpl<?>) obj;
        if (tryCanonicalization && appLayerCGlobalTracking != null) {
            data = appLayerCGlobalTracking.getCanonicalRepresentation(data);
        }

        if (isLaidOut()) {
            var info = map.get(data);
            VMError.guarantee(info != null, "CGlobalData instance must have been discovered/registered before or during analysis");
            return info;
        } else {
            return map.computeIfAbsent(data, key -> {
                if (appLayerCGlobalTracking != null) {
                    var result = appLayerCGlobalTracking.createCGlobalDataInfo(key);
                    if (result != null) {
                        return result;
                    }
                }
                var result = createCGlobalDataInfo(key, false);
                if (initialLayerCGlobalTracking != null) {
                    initialLayerCGlobalTracking.registerCGlobal(key);
                }
                return result;
            });
        }
    }

    CGlobalDataInfo createCGlobalDataInfo(CGlobalDataImpl<?> data, boolean definedAsGlobalInPriorLayer) {
        if (data.codeLocation != null && seenCodeLocations != null) {
            boolean added = seenCodeLocations.add(CodeLocation.fromStackFrame(data.codeLocation));
            VMError.guarantee(added, "Multiple elements seen at same code location: %s", data.codeLocation);
            VMError.guarantee(!data.codeLocation.getDeclaringClass().isHidden(),
                            "We currently do not allow CGlobalData code locations to be in a hidden class. Please adapt the code accordingly. Location: %s",
                            data.codeLocation);
        }
        CGlobalDataInfo cGlobalDataInfo = new CGlobalDataInfo(data, definedAsGlobalInPriorLayer);
        if (data.nonConstant) {
            nonConstantRegistry.registerNonConstantSymbol(cGlobalDataInfo);
        }
        return cGlobalDataInfo;
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
        if (obj instanceof CGlobalDataImpl<?> cglobal) {
            if (appLayerCGlobalTracking != null) {
                cglobal = appLayerCGlobalTracking.getCanonicalRepresentation(cglobal);
            }
            registerAsAccessedOrGet(cglobal, false);
            return cglobal;
        } else {
            return obj;
        }
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
        assert !isLaidOut() : "Already laid out";
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
                            return NumUtil.roundUp(nextOffset, wordSize); // align
                        }, Integer::sum);
        assert isLaidOut();
    }

    public int getSize() {
        assert isLaidOut() : "Not laid out yet";
        return totalSize;
    }

    public interface SymbolConsumer {
        void apply(int offset, String symbolName, boolean isGlobalSymbol);
    }

    public void writeData(RelocatableBuffer buffer, SymbolConsumer createSymbol, SymbolConsumer createSymbolReference) {
        assert isLaidOut() : "Not laid out yet";
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
        if (initialLayerCGlobalTracking != null) {
            initialLayerCGlobalTracking.writeData(createSymbol, map);
        }
        if (appLayerCGlobalTracking != null) {
            appLayerCGlobalTracking.validateCGlobals(map);
        }
    }

    public InitialLayerCGlobalTracking getInitialLayerCGlobalTracking() {
        return Objects.requireNonNull(initialLayerCGlobalTracking);
    }

    public AppLayerCGlobalTracking getAppLayerCGlobalTracking() {
        return Objects.requireNonNull(appLayerCGlobalTracking);
    }
}
