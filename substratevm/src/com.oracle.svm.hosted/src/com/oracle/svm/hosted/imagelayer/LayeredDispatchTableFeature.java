/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.InvalidMethodPointerHandler;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.snippets.OpenTypeWorldDispatchTableSnippets;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.VTableBuilder;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Tracks the state of type dispatch tables across layers. Across layers, this information is used
 * to validate the consistency of dispatch table computations and track the resolution status of
 * dispatch table slots.
 *
 * The dispatch logic for virtual calls can be found in {@link OpenTypeWorldDispatchTableSnippets}.
 *
 * The contents of the dispatch tables are computed within {@link VTableBuilder}.
 *
 * We also track across layers virtual calls which need to be registered as roots in subsequent
 * layers. We must register these call as roots since new types can be instantiated in subsequent
 * layers which then may cause new call targets to be reachable from prior layers' virtual calls.
 *
 * We call {@link #recordVirtualCallTarget} to register a virtual call that must be added as a root
 * in a subsequent layer. The logic for installing roots in subsequent layers is performed in
 * {@link #beforeAnalysis}.
 */
@AutomaticallyRegisteredFeature
public class LayeredDispatchTableFeature implements FeatureSingleton, InternalFeature {
    public static final class Options {
        @Option(help = "Log discrepancies between layered open world type information. This is an experimental option which will be removed.")//
        public static final HostedOptionKey<Boolean> LogLayeredDispatchTableDiscrepancies = new HostedOptionKey<>(false);

        @Option(help = "Throw an error when there are discrepancies between layered open world type information. This is an experimental option which will be removed.")//
        public static final HostedOptionKey<Boolean> ErrorOnLayeredDispatchTableDiscrepancies = new HostedOptionKey<>(false);
    }

    private final boolean buildingSharedLayer = ImageLayerBuildingSupport.buildingSharedLayer();
    private final boolean buildingInitialLayer = buildingSharedLayer && ImageLayerBuildingSupport.buildingInitialLayer();
    private final boolean buildingExtensionLayer = ImageLayerBuildingSupport.buildingExtensionLayer();

    private int wordSize;
    private HostedUniverse hUniverse;

    final Map<HostedType, PriorDispatchTable> priorDispatchTableCache = buildingExtensionLayer ? new ConcurrentHashMap<>() : null;
    final Map<Integer, PriorDispatchMethod> priorDispatchMethodCache = buildingExtensionLayer ? new ConcurrentHashMap<>() : null;

    final Set<HostedMethod> virtualCallTargets = buildingSharedLayer ? ConcurrentHashMap.newKeySet() : null;
    final boolean generateUnresolvedSymbolNames = buildingSharedLayer;
    Map<HostedMethod, Integer> persistedHostedMethodIndexMap = buildingSharedLayer ? new ConcurrentHashMap<>() : null;

    final Map<MethodRef, HostedDispatchSlot> vtableWordToDispatchSlot = new IdentityHashMap<>();
    final Map<HostedType, HostedDispatchTable> typeToDispatchTable = new HashMap<>();
    /**
     * Bitmap relative to the start of the current layer's image heap relocatables partition where
     * each 1-bit indicates one word that needs to be patched. Each such word initially contains an
     * offset relative to the current layer's code section that will be patched so it becomes
     * relative to the global code base, taking into account the displacement between the code base
     * and the current layer's code section at runtime.
     */
    final BitSet offsetsToPatchInHeapRelocs = new BitSet();
    /**
     * Bitmap like {@link #offsetsToPatchInHeapRelocs}, but each 1-bit indicates one word that
     * initially contains an absolute address in memory (e.g., for a symbol resolved by the runtime
     * linker) that will be patched so it becomes an offset relative to the global code base.
     */
    final BitSet addressesToPatchInHeapRelocs = new BitSet();

    /**
     * Cache of builderModules. Set in {@link #beforeCompilation}.
     */
    private Set<Module> builderModules;

    static final int INVALID_HOSTED_METHOD_INDEX = -1;

    private static final String UNRESOLVED_VTABLE_ENTRY_SYMBOL = "__svm_vtableSym_";

    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        wordSize = ConfigurationValues.getTarget().wordSize;
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            var config = (FeatureImpl.BeforeAnalysisAccessImpl) access;
            getPriorVirtualCallTargets().forEach(aMethod -> {
                config.registerAsRoot(aMethod, false, "in prior layer dispatch table");
            });
        }
        LayeredImageHooks.singleton().registerDynamicHubWrittenCallback(this::onDynamicHubWritten);
        LayeredImageHooks.singleton().registerPatchedWordWrittenCallback(this::onPatchedWordWritten);
    }

    @Override
    public void beforeCompilation(Feature.BeforeCompilationAccess a) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;
        hUniverse = access.getUniverse();
        installBuilderModules(access.getImageClassLoader().getBuilderModules());
    }

    private PriorDispatchMethod createPriorDispatchMethodInfo(int index) {
        return priorDispatchMethodCache.computeIfAbsent(index, i -> {
            var loader = HostedImageLayerBuildingSupport.singleton().getLoader();
            var reader = loader.getHostedMethodData(i);
            return new PriorDispatchMethod(reader.getMethodId(), reader.getSymbolName().toString(), reader.getVTableIndex(), reader.getIsVirtualCallTarget());
        });
    }

    private PriorDispatchTable createPriorDispatchTable(HostedType hType) {
        var loader = HostedImageLayerBuildingSupport.singleton().getLoader();
        var hubInfo = loader.getDynamicHubInfo(hType.getWrapped());
        var localSlotIds = hubInfo.getLocallyDeclaredSlotsHostedMethodIndexes();
        PriorDispatchMethod[] locallyDeclaredSlots = new PriorDispatchMethod[hubInfo.getLocallyDeclaredSlotsHostedMethodIndexes().size()];
        for (int i = 0; i < locallyDeclaredSlots.length; i++) {
            locallyDeclaredSlots[i] = createPriorDispatchMethodInfo(localSlotIds.get(i));
        }

        PriorDispatchSlot[] dispatchSlots;
        if (hubInfo.hasDispatchTableSlotValues()) {
            var dispatchSlotsReader = hubInfo.getDispatchTableSlotValues();
            dispatchSlots = new PriorDispatchSlot[dispatchSlotsReader.size()];
            for (int i = 0; i < dispatchSlots.length; i++) {
                var slotInfo = dispatchSlotsReader.get(i);
                PriorDispatchMethod declaredMethod = createPriorDispatchMethodInfo(slotInfo.getDeclaredHostedMethodIndex());
                PriorDispatchMethod resolvedMethod = null;
                if (slotInfo.getResolvedHostedMethodIndex() != INVALID_HOSTED_METHOD_INDEX) {
                    resolvedMethod = createPriorDispatchMethodInfo(slotInfo.getResolvedHostedMethodIndex());
                }
                SlotResolutionStatus status = SlotResolutionStatus.values()[slotInfo.getResolutionStatus()];
                String slotSymbolName = slotInfo.getSlotSymbolName().toString();
                var dispatchSlot = new PriorDispatchSlot(declaredMethod, resolvedMethod, slotInfo.getSlotIndex(), status, slotSymbolName);
                dispatchSlots[i] = dispatchSlot;
            }
        } else {
            dispatchSlots = PriorDispatchSlot.EMPTY_ARRAY;
        }
        return new PriorDispatchTable(hubInfo.getTypeId(), hubInfo.getInstalled(), locallyDeclaredSlots, dispatchSlots);
    }

    private PriorDispatchTable getPriorDispatchTable(HostedType hType) {
        if (hType.getWrapped().isInBaseLayer()) {
            return priorDispatchTableCache.computeIfAbsent(hType, this::createPriorDispatchTable);
        } else {
            return null;
        }
    }

    private static Set<String> getPriorUnresolvedSymbols() {
        Set<String> unresolvedSymbols = new HashSet<>();
        var hubInfos = HostedImageLayerBuildingSupport.singleton().getLoader().getDynamicHubInfos();
        for (var hubInfo : hubInfos) {
            if (hubInfo.getInstalled()) {
                assert hubInfo.hasDispatchTableSlotValues();
                var dispatchSlots = hubInfo.getDispatchTableSlotValues();
                for (var slotInfo : dispatchSlots) {
                    SlotResolutionStatus status = SlotResolutionStatus.values()[slotInfo.getResolutionStatus()];
                    String slotSymbolName = slotInfo.getSlotSymbolName().toString();
                    if (status == SlotResolutionStatus.UNRESOLVED || status == SlotResolutionStatus.NOT_COMPILED) {
                        assert !slotSymbolName.equals(PriorDispatchSlot.INVALID_SYMBOL_NAME);
                        unresolvedSymbols.add(slotSymbolName);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(unresolvedSymbols);
    }

    static Stream<AnalysisMethod> getPriorVirtualCallTargets() {
        var loader = HostedImageLayerBuildingSupport.singleton().getLoader();
        var methods = loader.getHostedMethods();
        return StreamSupport.stream(methods.spliterator(), false).filter(SharedLayerSnapshotCapnProtoSchemaHolder.PersistedHostedMethod.Reader::getIsVirtualCallTarget).map(data -> {
            assert data.getMethodId() != PriorDispatchMethod.UNPERSISTED_METHOD_ID;
            return loader.getAnalysisMethodForBaseLayerId(data.getMethodId());
        });
    }

    public static LayeredDispatchTableFeature singleton() {
        return ImageSingletons.lookup(LayeredDispatchTableFeature.class);
    }

    void installBuilderModules(Set<Module> newCoreTypes) {
        assert builderModules == null : builderModules;
        builderModules = newCoreTypes;
    }

    /**
     * Registers a virtual call target which will be added as a root in subsequent layers. Currently
     * we filter our all calls either originating from or targeting a {@link #builderModules}.
     */
    public void recordVirtualCallTarget(HostedMethod caller, HostedMethod callee) {
        Module callerModule = caller.getDeclaringClass().getJavaClass().getModule();
        Module calleeModule = callee.getDeclaringClass().getJavaClass().getModule();
        if (!(builderModules.contains(callerModule) || builderModules.contains(calleeModule))) {
            virtualCallTargets.add(callee);
        }
    }

    public void registerDeclaredDispatchInfo(HostedType type, List<HostedMethod> declaredMethods) {
        var dispatchTable = new HostedDispatchTable();
        dispatchTable.type = type;
        dispatchTable.locallyDeclaredSlots = declaredMethods.toArray(HostedMethod[]::new);
        var prev = typeToDispatchTable.put(type, dispatchTable);
        assert prev == null : prev;
    }

    /**
     * During the initial stages of vtable building we do not explicitly calculate dispatch table
     * info for arrays. Instead, we know they share the same dispatch table internals as the Object
     * type.
     */
    public void registerArrayDispatchTable(HostedType arrayType, HostedType objectType) {
        assert !typeToDispatchTable.containsKey(arrayType);
        var objDispatchTable = typeToDispatchTable.get(objectType);
        var arrayDispatchTable = new HostedDispatchTable();
        arrayDispatchTable.type = arrayType;
        arrayDispatchTable.locallyDeclaredSlots = objDispatchTable.locallyDeclaredSlots;
        arrayDispatchTable.status = HubStatus.DISPATCH_INFO_CALCULATED;

        arrayDispatchTable.slots = Arrays.stream(objDispatchTable.slots).map(objSlotInfo -> {
            HostedDispatchSlot arraySlotInfo = new HostedDispatchSlot();
            arraySlotInfo.dispatchTable = arrayDispatchTable;
            arraySlotInfo.declaredMethod = objSlotInfo.declaredMethod;
            arraySlotInfo.resolvedMethod = objSlotInfo.resolvedMethod;
            arraySlotInfo.slotIndex = objSlotInfo.slotIndex;
            arraySlotInfo.status = objSlotInfo.status;

            return arraySlotInfo;
        }).toArray(HostedDispatchSlot[]::new);

        injectPriorLayerInfo(arrayType, arrayDispatchTable);

        var prev = typeToDispatchTable.put(arrayType, arrayDispatchTable);
        assert prev == null : prev;
    }

    public void registerNonArrayDispatchTable(HostedType type, boolean[] validTarget) {
        var dispatchTable = typeToDispatchTable.get(type);
        dispatchTable.status = HubStatus.DISPATCH_INFO_CALCULATED;

        assert dispatchTable.slots == null;

        var resolvedMethods = type.getOpenTypeWorldDispatchTables();
        var targetMethods = type.getOpenTypeWorldDispatchTableSlotTargets();
        int length = validTarget.length;
        assert resolvedMethods.length == length && targetMethods.length == length : Assertions.errorMessage(resolvedMethods, targetMethods, validTarget);

        HostedDispatchSlot[] slotInfos = new HostedDispatchSlot[length];
        for (int i = 0; i < length; i++) {
            HostedDispatchSlot slot = new HostedDispatchSlot();
            slot.dispatchTable = dispatchTable;
            slot.slotIndex = i;
            slot.resolvedMethod = validTarget[i] ? resolvedMethods[i] : null;
            slot.declaredMethod = targetMethods[i];
            slot.status = validTarget[i] ? SlotResolutionStatus.COMPUTED : SlotResolutionStatus.UNRESOLVED;
            slotInfos[i] = slot;
        }

        dispatchTable.slots = slotInfos;

        injectPriorLayerInfo(type, dispatchTable);
    }

    private void injectPriorLayerInfo(HostedType type, HostedDispatchTable dispatchTable) {
        if (type.getWrapped().isInBaseLayer()) {
            var priorInfo = getPriorDispatchTable(type);
            if (priorInfo != null) {
                compareTypeInfo(dispatchTable, priorInfo);
                dispatchTable.status = priorInfo.installed ? HubStatus.INSTALLED_PRIOR_LAYER : HubStatus.COMPUTED_PRIOR_LAYER;
                if (priorInfo.installed) {
                    // record symbol info for installed hubs
                    for (int i = 0; i < dispatchTable.slots.length; i++) {
                        HostedDispatchSlot slot = dispatchTable.slots[i];
                        PriorDispatchSlot priorSlot = priorInfo.slots[i];
                        if (priorSlot.status.isCompiled()) {
                            slot.status = SlotResolutionStatus.PRIOR_LAYER;
                            assert !priorSlot.slotSymbolName.equals(PriorDispatchSlot.INVALID_SYMBOL_NAME);
                            slot.symbol = priorSlot.slotSymbolName;
                        }
                    }
                }
            }
        }
    }

    private static String compareMethod(HostedMethod curMethod, PriorDispatchMethod priorMethod) {
        String errorMessage = "";
        int priorId = priorMethod.methodId();
        int curId = curMethod.getWrapped().getId();
        if (priorId != PriorDispatchMethod.UNPERSISTED_METHOD_ID) {
            if (curId != priorId) {
                errorMessage += String.format("mismatch in id %s %s%n", curId, priorId);
            }
        }

        String curSymbol = NativeImage.localSymbolNameForMethod(curMethod);
        String priorSymbol = priorMethod.symbolName;
        if (!curSymbol.equals(priorSymbol)) {
            errorMessage += String.format("mismatch in symbol name %s %s%n", curSymbol, priorSymbol);
        }

        int priorVTableIdx = priorMethod.vtableIndex;
        int curVTableIdx = curMethod.hasVTableIndex() ? curMethod.getVTableIndex() : HostedMethod.MISSING_VTABLE_IDX;
        if (priorVTableIdx != HostedMethod.MISSING_VTABLE_IDX && curVTableIdx != HostedMethod.MISSING_VTABLE_IDX && priorVTableIdx != curVTableIdx) {
            errorMessage += String.format("mismatch in vtable index %s %s%n", curVTableIdx, priorVTableIdx);
        }
        if (!errorMessage.isEmpty()) {
            errorMessage = String.format("Issue while comparing method %s %s%n", curMethod.getQualifiedName(), priorMethod) + errorMessage;
        }

        return errorMessage;
    }

    private static void compareTypeInfo(HostedDispatchTable curInfo, PriorDispatchTable priorInfo) {
        if (!(Options.LogLayeredDispatchTableDiscrepancies.getValue() || Options.ErrorOnLayeredDispatchTableDiscrepancies.getValue())) {
            // it is not necessary to compare type info
            return;
        }

        String errorMessage = "";
        if (curInfo.locallyDeclaredSlots.length == priorInfo.locallyDeclaredSlots.length) {
            for (int i = 0; i < curInfo.locallyDeclaredSlots.length; i++) {
                errorMessage += compareMethod(curInfo.locallyDeclaredSlots[i], priorInfo.locallyDeclaredSlots[i]);
            }
        } else {
            errorMessage += String.format("Mismatch in locally declared slot length %s %s%n", curInfo.locallyDeclaredSlots.length, priorInfo.locallyDeclaredSlots.length);
        }

        if (curInfo.slots.length == priorInfo.slots.length) {
            for (int i = 0; i < curInfo.slots.length; i++) {
                var curSlotInfo = curInfo.slots[i];
                var priorSlotInfo = priorInfo.slots[i];
                compareMethod(curSlotInfo.declaredMethod, priorSlotInfo.declaredMethod);
                if (curSlotInfo.resolvedMethod != null && priorSlotInfo.status.isResolved()) {
                    compareMethod(curSlotInfo.resolvedMethod, priorSlotInfo.resolvedMethod);
                }
            }
        } else {
            errorMessage += String.format("Mismatch in dispatch table slot length %s %s%n", curInfo.slots.length, priorInfo.slots.length);
        }

        if (!errorMessage.isEmpty()) {
            String message = String.format("Issue while comparing dispatch table info: %s and %s%n%s", curInfo, priorInfo, errorMessage);
            if (Options.ErrorOnLayeredDispatchTableDiscrepancies.getValue()) {
                throw VMError.shouldNotReachHere(message);
            }
            if (Options.LogLayeredDispatchTableDiscrepancies.getValue()) {
                System.out.println(message);
            }
        }
    }

    private void onDynamicHubWritten(DynamicHub hub, MethodRef[] vtable) {
        AnalysisType aType = hUniverse.hostVM().lookupType(hub);
        HostedType hType = hUniverse.lookup(aType);

        assert hType.getWrapped().isReachable() : "All installed hubs should be reachable " + hType;

        if (VTableBuilder.hasEmptyDispatchTable(hType)) {
            assert vtable.length == 0 : hType;
            return;
        }

        var dispatchTable = typeToDispatchTable.get(hType);

        // upgrade status to being installed in current layer
        assert dispatchTable.status == HubStatus.DISPATCH_INFO_CALCULATED || dispatchTable.status == HubStatus.COMPUTED_PRIOR_LAYER : dispatchTable;
        dispatchTable.status = HubStatus.INSTALLED_CURRENT_LAYER;

        assert dispatchTable.slots.length == vtable.length : Assertions.errorMessage(vtable, dispatchTable.slots);

        for (int i = 0; i < vtable.length; i++) {
            var slot = dispatchTable.slots[i];
            var prev = vtableWordToDispatchSlot.put(vtable[i], slot);
            assert prev == null : prev;
        }
    }

    private void onPatchedWordWritten(WordBase word, int offsetInHeap, ImageHeapLayoutInfo heapLayout) {
        if (word instanceof MethodOffset methodOffset) {
            ResolvedJavaMethod method = methodOffset.getMethod();
            HostedMethod target = (method instanceof HostedMethod hm) ? hm : hUniverse.lookup(method);
            if (target.isCompiledInPriorLayer()) {
                /*
                 * Method compiled in the initial layer: we can use its offset without patching
                 * because it is relative to the initial layer's text section, which becomes the
                 * global code base.
                 */
                assert DynamicImageLayerInfo.getCurrentLayerNumber() == 1 : "Currently cannot patch references to code in a middle layer";
            } else if (target.isCompiled()) {
                if (!buildingInitialLayer) {
                    /*
                     * Method compiled in the current (non-base) layer: the offset is relative to
                     * the current layer's text section and must be patched to account for its
                     * displacement from the global code base at runtime.
                     */
                    markRelocsWordInBitmap(offsetsToPatchInHeapRelocs, offsetInHeap, heapLayout);
                }
            } else {
                /*
                 * Method compiled in a future layer, so the target will be resolved to an address
                 * via a symbol reference by the runtime linker and we must subsequently patch the
                 * word to turn the address into an offset relative to the code base.
                 */
                markRelocsWordInBitmap(addressesToPatchInHeapRelocs, offsetInHeap, heapLayout);
            }
        }
    }

    private void markRelocsWordInBitmap(BitSet bitmap, int offsetInHeap, ImageHeapLayoutInfo heapLayout) {
        assert heapLayout.isReadOnlyRelocatable(offsetInHeap) : offsetInHeap;
        int offsetInHeapRelocs = NumUtil.safeToInt(offsetInHeap - heapLayout.getReadOnlyRelocatableOffset());
        assert offsetInHeapRelocs % wordSize == 0 : offsetInHeap;
        bitmap.set(offsetInHeapRelocs / wordSize);
    }

    private static String computeUnresolvedMethodSymbol(HostedDispatchSlot slotInfo, Map<ResolvedJavaMethod, String> methodToSymbolMap, Supplier<String> symbolNameSupplier) {
        /*
         * First try to determine the resolved method. This is useful for deduplication.
         */
        ResolvedJavaMethod resolvedMethod = null;
        if (slotInfo.status == SlotResolutionStatus.NOT_COMPILED) {
            resolvedMethod = OriginalMethodProvider.getOriginalMethod(slotInfo.resolvedMethod);
        } else {
            assert slotInfo.status == SlotResolutionStatus.UNRESOLVED : slotInfo;
            ResolvedJavaType originalType = OriginalClassProvider.getOriginalType(slotInfo.dispatchTable.type);
            ResolvedJavaMethod originalMethod = OriginalMethodProvider.getOriginalMethod(slotInfo.declaredMethod);
            if (originalMethod != null) {
                resolvedMethod = originalType.resolveMethod(originalMethod, originalMethod.getDeclaringClass());
            }
        }

        String unresolvedTableSymbol;
        if (resolvedMethod != null) {
            unresolvedTableSymbol = methodToSymbolMap.computeIfAbsent(resolvedMethod, k -> symbolNameSupplier.get());
        } else {
            unresolvedTableSymbol = symbolNameSupplier.get();
        }
        return unresolvedTableSymbol;
    }

    /*
     * Defines symbols for slots which either have yet to have their method compiled or for slots
     * which were defined in a prior layer, but have only had their method target resolved in this
     * layer.
     */
    public void defineDispatchTableSlotSymbols(ObjectFile objectFile, ObjectFile.Section textSection, NativeImageCodeCache codeCache, HostedMetaAccess metaAccess) {
        HostedMethod invalidMethod = metaAccess.lookupJavaMethod(InvalidMethodPointerHandler.INVALID_VTABLE_ENTRY_HANDLER_METHOD);

        Map<String, HostedMethod> resolvedPriorVTableMap = new HashMap<>();
        Set<String> unresolvedVTableSymbolNames = generateUnresolvedSymbolNames ? new HashSet<>() : null;

        Map<ResolvedJavaMethod, String> deduplicatedMethodMap = new HashMap<>();
        final var unresolvedSlotCount = IntStream.iterate(0, i -> i + 1).iterator();
        Supplier<String> symbolNameSupplier = () -> {
            int count = unresolvedSlotCount.nextInt();
            return String.format("%s_%s", UNRESOLVED_VTABLE_ENTRY_SYMBOL, count);
        };

        /*
         * First calculate symbols for all slots
         */
        for (var slotInfo : vtableWordToDispatchSlot.values()) {
            assert slotInfo.dispatchTable.status == HubStatus.INSTALLED_CURRENT_LAYER;

            if (slotInfo.status == SlotResolutionStatus.COMPUTED) {
                // check if this method is compiled
                slotInfo.status = slotInfo.resolvedMethod.isCompiled() ? SlotResolutionStatus.CURRENT_LAYER : SlotResolutionStatus.NOT_COMPILED;
            }

            String symbol = null;
            if (!slotInfo.status.isCompiled()) {
                assert slotInfo.status == SlotResolutionStatus.UNRESOLVED || slotInfo.status == SlotResolutionStatus.NOT_COMPILED : slotInfo;
                if (generateUnresolvedSymbolNames) {
                    /*
                     * We need to make a symbol for this method which can be resolved in a later
                     * build if the target is compiled.
                     */
                    symbol = computeUnresolvedMethodSymbol(slotInfo, deduplicatedMethodMap, symbolNameSupplier);
                    if (unresolvedVTableSymbolNames.add(symbol)) {
                        objectFile.createUndefinedSymbol(symbol, true);
                    }
                }
            } else {
                /*
                 * The resolved method has been compiled so we can link to it.
                 */
                symbol = NativeImage.localSymbolNameForMethod(slotInfo.resolvedMethod);
            }
            slotInfo.symbol = symbol;
        }

        /*
         * Next determine if any vtable symbols defined in prior layers now have a resolved target.
         */
        for (HostedDispatchTable typeInfo : typeToDispatchTable.values()) {
            if (typeInfo.status == HubStatus.INSTALLED_PRIOR_LAYER) {
                for (HostedDispatchSlot slotInfo : typeInfo.slots) {
                    if (slotInfo.status == SlotResolutionStatus.COMPUTED && slotInfo.resolvedMethod.isCompiled()) {
                        /*
                         * Now that this method has been compiled we must define it.
                         */

                        var priorInfo = getPriorDispatchTable(slotInfo.dispatchTable.type);
                        var symName = priorInfo.slots[slotInfo.slotIndex].slotSymbolName;
                        var prev = resolvedPriorVTableMap.put(symName, slotInfo.resolvedMethod);
                        /*
                         * All slots with the same symbol name should have the same resolved method.
                         */
                        assert prev == null || prev.equals(slotInfo.resolvedMethod);
                    }
                }
            }
        }

        for (var entry : resolvedPriorVTableMap.entrySet()) {
            String symbol = entry.getKey();
            HostedMethod method = entry.getValue();
            CompilationResult result = codeCache.compilationResultFor(method);

            final int size = result == null ? 0 : result.getTargetCodeSize();
            objectFile.createDefinedSymbol(symbol, textSection, method.getCodeAddressOffset(), size, true, true);
        }

        /*
         * Finally, in the application layer we need to make sure all remaining undefined symbols
         * are defined to be linked to the invalid method handler.
         */
        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            getPriorUnresolvedSymbols().forEach(symbol -> {
                if (!resolvedPriorVTableMap.containsKey(symbol)) {
                    CompilationResult result = codeCache.compilationResultFor(invalidMethod);

                    final int size = result == null ? 0 : result.getTargetCodeSize();
                    objectFile.createDefinedSymbol(symbol, textSection, invalidMethod.getCodeAddressOffset(), size, true, true);
                }
            });
        }
    }

    // GR-58588 use injectedNotCompiled to track status of all MethodPointers
    public String getSymbolName(MethodRef methodRef, HostedMethod target, @SuppressWarnings("unused") boolean injectedNotCompiled) {
        var slotInfo = vtableWordToDispatchSlot.get(methodRef);
        String symbol = NativeImage.localSymbolNameForMethod(target);
        if (slotInfo != null) {
            if (!slotInfo.status.isCompiled()) {
                assert slotInfo.status == SlotResolutionStatus.UNRESOLVED || slotInfo.status == SlotResolutionStatus.NOT_COMPILED : slotInfo;
                if (generateUnresolvedSymbolNames) {
                    assert slotInfo.symbol != null : slotInfo;
                    /*
                     * We have injected a new symbol which can be resolved in a later layer.
                     */
                    symbol = slotInfo.symbol;
                }
            } else {
                assert slotInfo.symbol.equals(symbol);
            }
        }
        return symbol;
    }

    public HostedMethod[] acquireHostedMethodArray() {
        return persistedHostedMethodIndexMap.entrySet().stream()
                        .sorted(Comparator.comparingInt(Map.Entry::getValue))
                        .map(Map.Entry::getKey)
                        .toArray(HostedMethod[]::new);
    }

    public void releaseHostedMethodArray() {
        persistedHostedMethodIndexMap = null;
    }

    public void persistHostedMethod(HostedMethod hMethod, Supplier<SharedLayerSnapshotCapnProtoSchemaHolder.PersistedHostedMethod.Builder> methodInfoBuilderSupplier) {
        assert persistedHostedMethodIndexMap.containsKey(hMethod);

        boolean persistedMethod = hMethod.getWrapped().isTrackedAcrossLayers();
        var builder = methodInfoBuilderSupplier.get();
        builder.setIndex(persistedHostedMethodIndexMap.get(hMethod));
        builder.setMethodId(persistedMethod ? hMethod.getWrapped().getId() : PriorDispatchMethod.UNPERSISTED_METHOD_ID);
        builder.setSymbolName(NativeImage.localSymbolNameForMethod(hMethod));
        builder.setVTableIndex(hMethod.hasVTableIndex() ? hMethod.getVTableIndex() : HostedMethod.MISSING_VTABLE_IDX);
        builder.setIsVirtualCallTarget(virtualCallTargets.contains(hMethod));
        builder.setInstalledOffset(hMethod.isCodeAddressOffsetValid() ? hMethod.getCodeAddressOffset() : HostedMethod.INVALID_CODE_ADDRESS_OFFSET);
        if (persistedMethod) {
            builder.setHostedMethodName(hMethod.getName());
            builder.setHostedMethodUniqueName(hMethod.getUniqueShortName());
        }
    }

    public int getPersistedHostedMethodIndex(HostedMethod hMethod) {
        int nextIdx = persistedHostedMethodIndexMap.size();
        persistedHostedMethodIndexMap.putIfAbsent(hMethod, nextIdx);
        return persistedHostedMethodIndexMap.get(hMethod);
    }

    public void persistDynamicHubInfo(HostedType hType, Supplier<SharedLayerSnapshotCapnProtoSchemaHolder.DynamicHubInfo.Builder> typeInfoBuilderSupplier) {
        var typeInfoBuilder = typeInfoBuilderSupplier.get();
        typeInfoBuilder.setTypeId(hType.getWrapped().getId());

        // typecheck info
        typeInfoBuilder.setTypecheckId(hType.getTypeID());
        typeInfoBuilder.setNumClassTypes(hType.getNumClassTypes());
        typeInfoBuilder.setNumInterfaceTypes(hType.getNumInterfaceTypes());
        SVMImageLayerWriter.initInts(typeInfoBuilder::initTypecheckSlotValues, Arrays.stream(hType.getOpenTypeWorldTypeCheckSlots()));

        // dispatch table info
        HostedDispatchTable hDispatchTable = typeToDispatchTable.get(hType);
        if (hDispatchTable != null) {
            boolean hubInstalled = hDispatchTable.status == HubStatus.INSTALLED_CURRENT_LAYER;
            typeInfoBuilder.setInstalled(hubInstalled);

            SVMImageLayerWriter.initInts(typeInfoBuilder::initLocallyDeclaredSlotsHostedMethodIndexes,
                            Arrays.stream(hDispatchTable.locallyDeclaredSlots).mapToInt(this::getPersistedHostedMethodIndex));

            assert !(hDispatchTable.status == HubStatus.UNINITIALIZED && hDispatchTable.slots != null) : hType;
            if (hDispatchTable.slots != null) {
                SVMImageLayerWriter.initSortedArray(typeInfoBuilder::initDispatchTableSlotValues, hDispatchTable.slots, (dispatchSlot, dispatchSlotInfoSupplier) -> {
                    persistDynamicSlot(dispatchSlot, dispatchSlotInfoSupplier, hubInstalled);
                });
            }
        } else {
            assert hType.isPrimitive() : hType;
        }
    }

    private void persistDynamicSlot(HostedDispatchSlot dispatchSlot, Supplier<SharedLayerSnapshotCapnProtoSchemaHolder.DispatchSlotInfo.Builder> dispatchSlotInfoSupplier, boolean hubInstalled) {
        var dispatchSlotBuilder = dispatchSlotInfoSupplier.get();

        dispatchSlotBuilder.setSlotIndex(dispatchSlot.slotIndex);
        dispatchSlotBuilder.setResolutionStatus(dispatchSlot.status.ordinal());

        dispatchSlotBuilder.setDeclaredHostedMethodIndex(getPersistedHostedMethodIndex(dispatchSlot.declaredMethod));
        dispatchSlotBuilder.setResolvedHostedMethodIndex(dispatchSlot.status.isResolved() ? getPersistedHostedMethodIndex(dispatchSlot.resolvedMethod) : INVALID_HOSTED_METHOD_INDEX);
        dispatchSlotBuilder.setSlotSymbolName(hubInstalled ? dispatchSlot.symbol : PriorDispatchSlot.INVALID_SYMBOL_NAME);
    }

    enum HubStatus {
        UNINITIALIZED,
        DISPATCH_INFO_CALCULATED,
        COMPUTED_PRIOR_LAYER,
        INSTALLED_PRIOR_LAYER,
        INSTALLED_CURRENT_LAYER,
    }

    static class HostedDispatchTable {
        HostedType type;
        HostedMethod[] locallyDeclaredSlots;
        HostedDispatchSlot[] slots;

        HubStatus status = HubStatus.UNINITIALIZED;
    }

    enum SlotResolutionStatus {
        UNRESOLVED,
        COMPUTED, // the target is known, but it hasn't been compiled yet
        NOT_COMPILED,
        PRIOR_LAYER,
        CURRENT_LAYER;

        public boolean isResolved() {
            return this != UNRESOLVED;
        }

        public boolean isCompiled() {
            return this == PRIOR_LAYER || this == CURRENT_LAYER;
        }
    }

    static class HostedDispatchSlot {
        HostedDispatchTable dispatchTable;
        HostedMethod declaredMethod;
        HostedMethod resolvedMethod;
        int slotIndex;
        SlotResolutionStatus status;

        /**
         * Stores the symbol installed for this slot. We must track this when the symbol is resolved
         * in a later layer.
         */
        String symbol;
    }

    /*
     * We track the dispatch table information recorded in prior layered builds so that we can
     * validate it is consistent across layers and also to ensure we resolve all placeholder
     * symbols.
     */

    record PriorDispatchTable(int typeID, boolean installed,
                    PriorDispatchMethod[] locallyDeclaredSlots, PriorDispatchSlot[] slots) {
    }

    record PriorDispatchSlot(
                    PriorDispatchMethod declaredMethod, PriorDispatchMethod resolvedMethod,
                    int slotIndex, SlotResolutionStatus status, String slotSymbolName) {
        static final String INVALID_SYMBOL_NAME = "invalid";

        static final PriorDispatchSlot[] EMPTY_ARRAY = new PriorDispatchSlot[0];
    }

    /**
     * Because methods are currently only persisted when a method is implementation invoked, it is
     * not always possible to match on method id. When it is not possible, we store
     * {@link PriorDispatchMethod#UNPERSISTED_METHOD_ID} as the value.
     */
    record PriorDispatchMethod(int methodId, String symbolName, int vtableIndex, boolean isVirtualCallTarget) {
        static final int UNPERSISTED_METHOD_ID = -1;
    }
}
